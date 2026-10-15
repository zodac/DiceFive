package net.zodac.dicefive.online

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.images.ImageManager
import com.google.android.gms.games.GamesClientStatusCodes
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.achievement.Achievement as PlayAchievement
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Task
import java.lang.ref.WeakReference
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.zodac.dicefive.BuildConfig
import net.zodac.dicefive.model.Achievement

/**
 * [GameServices] on Google Play Games Services v2 - the only code that imports the Play Games SDK. One per
 * process, so its state survives a configuration change; each Activity attaches itself, as the SDK's clients
 * need one. Present only where [isEnabled]; everywhere else the app gets [NoGameServices].
 *
 * The SDK signs in by itself whenever it starts, and has no sign-out, so it isn't started until the player signs in
 * from Settings ([signIn]). From then on it starts with the app, in `Application.onCreate` as Google requires, and
 * signs in again quietly, until [signOut]. That choice is kept in SharedPreferences ([START_AT_LAUNCH_KEY]), read
 * there and then: DataStore's read would start the SDK only after the first Activity, too late for its sign-in. None of the calls throws: a failure leaves the player signed out, and is logged
 * under [LOG_TAG] with Google's status code. Every call has a timeout, bar the two that wait for the player to pick
 * an account.
 *
 * Achievements are mirrored and scores posted ([OnlineSync] decides what to send), by [playAchievementIds] and
 * [playLeaderboardIds]. The cloud save is one Saved Games snapshot ([CloudSaveCodec.SNAPSHOT_NAME]), opened with
 * manual conflict resolution: two copies in conflict are merged, never one picked ([openSave]).
 */
class PlayGamesServices private constructor(private val application: Application) : GameServices {

    override val serviceName: String = SERVICE_NAME

    private val mutableState = MutableStateFlow<GameServicesState>(GameServicesState.SigningIn)
    override val state: StateFlow<GameServicesState> = mutableState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activity: WeakReference<ComponentActivity>? = null
    private val preferences = application.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    private var sdkStarted = false
    private var refreshWhenAttached = false

    init {
        if (preferences.getBoolean(START_AT_LAUNCH_KEY, false)) {
            startSdk()
            // Who is signed in is asked once there's an Activity to ask with.
            refreshWhenAttached = true
        } else {
            mutableState.value = GameServicesState.SignedOut
        }
    }

    private fun startSdk() {
        if (sdkStarted) return
        sdkStarted = true
        PlayGamesSdk.initialize(application)
    }

    private fun attach(activity: ComponentActivity) {
        this.activity = WeakReference(activity)
        activity.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY && this.activity?.get() === activity) this.activity = null
            },
        )
        if (refreshWhenAttached) {
            refreshWhenAttached = false
            scope.launch { refresh() }
        }
    }

    override suspend fun signIn() {
        val activity = activity?.get() ?: return
        mutableState.value = GameServicesState.SigningIn
        startSdk()
        val result = PlayGames.getGamesSignInClient(activity).signIn().awaitOrNull("signIn", timeoutMillis = null)
        if (result?.isAuthenticated == false) failed("signIn: not authenticated")
        refresh()
        setStartAtLaunch(mutableState.value is GameServicesState.SignedIn)
    }

    /** The SDK stays signed in until the app next starts, but nothing here uses it, and it isn't started again. */
    override suspend fun signOut() {
        setStartAtLaunch(false)
        mutableState.value = GameServicesState.SignedOut
    }

    private fun setStartAtLaunch(start: Boolean) {
        preferences.edit { putBoolean(START_AT_LAUNCH_KEY, start) }
    }

    /** Asks whether the player is signed in and, if so, who as. A player who was already shown stays shown meanwhile. */
    private suspend fun refresh() {
        val activity = activity?.get() ?: run {
            refreshWhenAttached = true
            return
        }
        // No timeout: at launch this waits for the SDK's own sign-in, which may be waiting for the player to pick an account.
        val authenticated = PlayGames.getGamesSignInClient(activity).isAuthenticated()
            .awaitOrNull("isAuthenticated", timeoutMillis = null)?.isAuthenticated == true
        if (!authenticated) failed("isAuthenticated: not authenticated")
        val player = if (authenticated) PlayGames.getPlayersClient(activity).currentPlayer.awaitOrNull("getCurrentPlayer") else null
        if (player == null) {
            mutableState.value = GameServicesState.SignedOut
            return
        }
        val account = OnlineAccount(displayName = player.displayName, avatar = null, accountKey = accountKey(player.playerId))
        val shown = (mutableState.value as? GameServicesState.SignedIn)?.account
        // The same account again keeps the avatar it already has, rather than blinking back to the icon.
        mutableState.value = GameServicesState.SignedIn(if (shown?.accountKey == account.accountKey) shown else account)
        player.iconImageUri?.let { loadAvatar(it, account.accountKey) }
    }

    private fun loadAvatar(uri: Uri, accountKey: String) {
        ImageManager.create(application).loadImage({ _, drawable, isRequestedDrawable ->
            val signedIn = mutableState.value as? GameServicesState.SignedIn
            if (drawable != null && isRequestedDrawable && signedIn?.account?.accountKey == accountKey) {
                val avatar = drawable.toBitmap(AVATAR_SIZE_PX, AVATAR_SIZE_PX).asImageBitmap()
                mutableState.value = GameServicesState.SignedIn(signedIn.account.copy(avatar = avatar))
            }
        }, uri)
    }

    /** The task's result, or null if it failed or took longer than [timeoutMillis] (null: no limit). */
    private suspend fun <T> Task<T>.awaitOrNull(call: String, timeoutMillis: Long? = TIMEOUT_MILLIS): T? =
        awaitResult(call, timeoutMillis).getOrNull()

    /** The task's result, or why it failed: its exception, or a timeout after [timeoutMillis] (null: no limit). */
    private suspend fun <T> Task<T>.awaitResult(call: String, timeoutMillis: Long? = TIMEOUT_MILLIS): Result<T> {
        val await: suspend () -> Result<T> = {
            suspendCancellableCoroutine { continuation ->
                addOnCompleteListener { task ->
                    if (!task.isSuccessful) failed("$call failed: ${describe(task.exception)}", task.exception)
                    continuation.resume(
                        if (task.isSuccessful) Result.success(task.result) else Result.failure(task.exception ?: IllegalStateException(call)),
                    )
                }
            }
        }
        if (timeoutMillis == null) return await()
        return withTimeoutOrNull(timeoutMillis) { await() } ?: Result.failure<T>(IllegalStateException("$call timed out")).also { failed("$call timed out") }
    }

    private fun failed(message: String, cause: Throwable? = null) {
        Log.w(LOG_TAG, message, cause)
    }

    override fun mirrors(achievementId: String): Boolean = achievementId in playIdsById

    override suspend fun unlockedAchievementIds(): Set<String>? {
        val activity = signedInActivity() ?: return null
        val loaded = PlayGames.getAchievementsClient(activity).load(false).awaitOrNull("loadAchievements") ?: return null
        val buffer = loaded.get() ?: return null
        return try {
            buffer.filter { it.state == PlayAchievement.STATE_UNLOCKED }.mapNotNull { idsByPlayId[it.achievementId] }.toSet()
        } finally {
            buffer.release()
        }
    }

    /** An incremental achievement is unlocked by filling its steps; Play takes it (offline too) and shows its own pop-up. */
    override suspend fun unlock(achievementId: String) {
        val activity = signedInActivity() ?: return
        val achievement = achievementsById[achievementId] ?: return
        val playId = playAchievementIds[achievement] ?: return
        val client = PlayGames.getAchievementsClient(activity)
        when (val type = achievement.playType) {
            is PlayAchievementType.Incremental -> client.setSteps(playId, type.steps)
            PlayAchievementType.Standard -> client.unlock(playId)
        }
    }

    /** [steps] is the achievement's own progress, in Play's steps for an incremental one; a standard one has none. */
    override suspend fun setProgress(achievementId: String, steps: Int) {
        val activity = signedInActivity() ?: return
        val achievement = achievementsById[achievementId] ?: return
        val playId = playAchievementIds[achievement] ?: return
        val type = achievement.playType as? PlayAchievementType.Incremental ?: return
        val playSteps = (steps / type.perStep).coerceAtMost(type.steps)
        if (playSteps > 0) PlayGames.getAchievementsClient(activity).setSteps(playId, playSteps)
    }

    /** The attached Activity, while signed in: nothing is sent or asked for otherwise. */
    private fun signedInActivity(): ComponentActivity? =
        activity?.get()?.takeIf { mutableState.value is GameServicesState.SignedIn }

    /** Waits for Play to take the score, so one that didn't reach it is sent again by the next [OnlineSync.reconcile]. */
    override suspend fun submitScore(leaderboardKey: String, score: Long): Boolean {
        val activity = signedInActivity() ?: return false
        val playId = playLeaderboardIdsByKey[leaderboardKey] ?: return false
        return PlayGames.getLeaderboardsClient(activity).submitScoreImmediate(playId, score).awaitOrNull("submitScore") != null
    }

    /** The snapshot's bytes, opened only to read: [CloudSaveRead.Empty] if the account has none yet. */
    override suspend fun readCloudSave(merge: (local: ByteArray, remote: ByteArray) -> ByteArray): CloudSaveRead =
        orOnThrow("readCloudSave", CloudSaveRead.Failed) { readSave(merge) }

    private suspend fun readSave(merge: (local: ByteArray, remote: ByteArray) -> ByteArray): CloudSaveRead {
        val activity = signedInActivity() ?: return CloudSaveRead.Failed
        val client = PlayGames.getSnapshotsClient(activity)
        val snapshot = openSave(client, createIfNotFound = false, merge).getOrElse { error ->
            val notFound = (error as? ApiException)?.statusCode == GamesClientStatusCodes.SNAPSHOT_NOT_FOUND
            return if (notFound) CloudSaveRead.Empty else CloudSaveRead.Failed
        }
        val bytes = snapshot.bytes()
        client.discardAndClose(snapshot).awaitOrNull("discardSnapshot")
        return when {
            bytes == null -> CloudSaveRead.Failed
            bytes.isEmpty() -> CloudSaveRead.Empty
            else -> CloudSaveRead.Found(bytes)
        }
    }

    /** Commits [data], merged with what the snapshot holds by now if that differs; nothing is committed if it already holds it all. */
    override suspend fun writeCloudSave(data: ByteArray, merge: (local: ByteArray, remote: ByteArray) -> ByteArray): ByteArray? =
        orOnThrow("writeCloudSave", null) { writeSave(data, merge) }

    private suspend fun writeSave(data: ByteArray, merge: (local: ByteArray, remote: ByteArray) -> ByteArray): ByteArray? {
        val activity = signedInActivity() ?: return null
        val client = PlayGames.getSnapshotsClient(activity)
        val snapshot = openSave(client, createIfNotFound = true, merge).getOrNull() ?: return null
        val current = snapshot.bytes()
        val final = when {
            current == null -> null
            current.isEmpty() || current.contentEquals(data) -> data
            else -> withContext(Dispatchers.Default) { merge(data, current) }
        }
        return when {
            final == null || final.contentEquals(current) -> final.also { client.discardAndClose(snapshot).awaitOrNull("discardSnapshot") }
            !withContext(Dispatchers.IO) { snapshot.snapshotContents.writeBytes(final) } ->
                null.also { client.discardAndClose(snapshot).awaitOrNull("discardSnapshot") }
            else -> final.takeIf { client.commitAndClose(snapshot, SnapshotMetadataChange.EMPTY_CHANGE).awaitOrNull("commitSnapshot") != null }
        }
    }

    /**
     * Opens the save, settling any conflict Play reports (an offline commit against a newer one, say) by merging the
     * two with [merge] - up to [MAX_CONFLICT_RESOLUTIONS] times, as each settling can meet another device's newer
     * commit. The caller closes the snapshot.
     */
    private suspend fun openSave(
        client: SnapshotsClient,
        createIfNotFound: Boolean,
        merge: (local: ByteArray, remote: ByteArray) -> ByteArray,
    ): Result<Snapshot> {
        var opened = client.open(CloudSaveCodec.SNAPSHOT_NAME, createIfNotFound, SnapshotsClient.RESOLUTION_POLICY_MANUAL)
            .awaitResult("openSnapshot").getOrElse { return Result.failure(it) }
        repeat(MAX_CONFLICT_RESOLUTIONS) {
            val conflict = opened.conflictOrNull()
                ?: return opened.data?.let { Result.success(it) } ?: Result.failure(IllegalStateException("openSnapshot: no snapshot"))
            val remote = conflict.snapshot.bytes() ?: return Result.failure(IllegalStateException("conflict: unreadable"))
            val local = conflict.conflictingSnapshot.bytes() ?: return Result.failure(IllegalStateException("conflict: unreadable"))
            val resolved = withContext(Dispatchers.Default) { merge(local, remote) }
            val contents = conflict.resolutionSnapshotContents
            if (!withContext(Dispatchers.IO) { contents.writeBytes(resolved) }) return Result.failure(IllegalStateException("conflict: unwritable"))
            opened = client.resolveConflict(conflict.conflictId, conflict.snapshot.metadata.snapshotId, SnapshotMetadataChange.EMPTY_CHANGE, contents)
                .awaitResult("resolveConflict").getOrElse { return Result.failure(it) }
        }
        failed("openSnapshot: still in conflict after $MAX_CONFLICT_RESOLUTIONS resolutions")
        return Result.failure(IllegalStateException("openSnapshot: unresolved conflict"))
    }

    /**
     * [block], or [fallback] if Play's SDK throws instead of failing its task - as some of its getters do - so a
     * surprise from it is a sync that didn't happen, retried next time, never a crash. Cancellation still cancels.
     */
    private suspend fun <T> orOnThrow(call: String, fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        failed("$call threw", error)
        fallback
    }

    /** The snapshot's contents, or null if they can't be read. */
    private suspend fun Snapshot.bytes(): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { snapshotContents.readFully() }.onFailure { failed("readSnapshot failed", it) }.getOrNull()
    }

    override val canShowAchievementsUi: Boolean
        get() = signedInActivity() != null

    /** Play's own achievements screen. */
    override fun showAchievementsUi() {
        val activity = signedInActivity() ?: return
        scope.launch {
            val intent = PlayGames.getAchievementsClient(activity).achievementsIntent.awaitOrNull("getAchievementsIntent") ?: return@launch
            launchPlayScreen(activity, ACHIEVEMENTS_UI_KEY, intent)
        }
    }

    override val canShowLeaderboardUi: Boolean
        get() = signedInActivity() != null

    /** Play's own screen for one mode's leaderboard, or its list of them all. */
    override fun showLeaderboardUi(leaderboardKey: String?) {
        val activity = signedInActivity() ?: return
        val client = PlayGames.getLeaderboardsClient(activity)
        val task = if (leaderboardKey == null) {
            client.allLeaderboardsIntent
        } else {
            client.getLeaderboardIntent(playLeaderboardIdsByKey[leaderboardKey] ?: return)
        }
        scope.launch {
            val intent = task.awaitOrNull("getLeaderboardIntent") ?: return@launch
            launchPlayScreen(activity, LEADERBOARD_UI_KEY, intent)
        }
    }

    /** Opens one of Play's own screens, which Play needs started for a result (it reads the caller), though none comes back. */
    private fun launchPlayScreen(activity: ComponentActivity, key: String, intent: Intent) {
        lateinit var launcher: ActivityResultLauncher<Intent>
        launcher = activity.activityResultRegistry.register(key, ActivityResultContracts.StartActivityForResult()) {
            launcher.unregister()
        }
        launcher.launch(intent)
    }

    companion object {

        /** The service's own name, which Google doesn't translate. */
        const val SERVICE_NAME = "Google Play Games"

        /** Why a call failed, in logcat: `adb logcat -s DiceFivePlayGames`. */
        const val LOG_TAG = "DiceFivePlayGames"

        private val achievementsById = Achievement.entries.associateBy { it.id }
        private val playIdsById = playAchievementIds.mapKeys { it.key.id }
        private val idsByPlayId = playAchievementIds.entries.associate { (achievement, playId) -> playId to achievement.id }

        private val playLeaderboardIdsByKey = playLeaderboardIds.mapKeys { it.key.onlineLeaderboardKey }

        private const val ACHIEVEMENTS_UI_KEY = "play_games_achievements"
        private const val LEADERBOARD_UI_KEY = "play_games_leaderboard"
        private const val PREFERENCES_FILE = "play_games"

        /** Whether the player signed in from Settings on this device, and hasn't signed out since. */
        private const val START_AT_LAUNCH_KEY = "start_at_launch"

        private const val PLAY_STORE_PACKAGE = "com.android.vending"
        private const val TIMEOUT_MILLIS = 15_000L
        private const val MAX_CONFLICT_RESOLUTIONS = 10
        private const val AVATAR_SIZE_PX = 96

        /** Hashed into [OnlineAccount.accountKey], so this device never stores the raw player id. */
        private const val ACCOUNT_KEY_SALT = "net.zodac.dicefive.online:"

        @Volatile
        private var instance: PlayGamesServices? = null

        /**
         * Sets up the service where Play Games is used: a device with Google Play services, running a debug build or one
         * installed from Google Play. A sideloaded release build gets no service at all.
         */
        fun initialize(application: Application) {
            if (!isEnabled(application)) return
            instance = PlayGamesServices(application)
        }

        /** The service for [activity] to provide, attached to it; [NoGameServices] where [initialize] started none. */
        fun attach(activity: ComponentActivity): GameServices = instance?.also { it.attach(activity) } ?: NoGameServices

        private fun isEnabled(context: Context): Boolean = usesPlayGames(
            playServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS,
            isDebugBuild = BuildConfig.DEBUG,
            installer = installerPackage(context),
        )

        /** Play Games needs Google Play services, and is only for debug builds and installs from Google Play. */
        internal fun usesPlayGames(playServicesAvailable: Boolean, isDebugBuild: Boolean, installer: String?): Boolean =
            playServicesAvailable && (isDebugBuild || installer == PLAY_STORE_PACKAGE)

        private fun installerPackage(context: Context): String? = runCatching {
            val packageManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                // The only way to ask before Android 11, which deprecated it for getInstallSourceInfo above.
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()

        /** Google's status code and its name for an [ApiException] (10 is DEVELOPER_ERROR, a credential mismatch). */
        private fun describe(error: Exception?): String = when (error) {
            is ApiException -> "status ${error.statusCode} (${CommonStatusCodes.getStatusCodeString(error.statusCode)})"
            null -> "no reason given"
            else -> error.toString()
        }

        /** The conflict, or null if there is none - asked first, as Play's own getConflict throws when there isn't one. */
        internal fun <T> SnapshotsClient.DataOrConflict<T>.conflictOrNull(): SnapshotsClient.SnapshotConflict? = if (isConflict) conflict else null

        internal fun accountKey(playerId: String): String =
            MessageDigest.getInstance("SHA-256").digest((ACCOUNT_KEY_SALT + playerId).toByteArray())
                .joinToString("") { "%02x".format(it) }

    }
}
