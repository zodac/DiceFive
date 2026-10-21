package net.zodac.dicefive.online

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.scores.UnpostedScore
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.ProgressStyle

/**
 * Mirrors this device's achievements with the online service, both ways, by comparing the whole of each rather than
 * queueing changes: whatever failed last time is simply sent again next time ([reconcile]).
 *
 * - **Here to there**: every unlock the service lacks, and how far along each climbing total is.
 * - **There to here**: every unlock the service has and this device lacks is recorded quietly (no banner), as of now -
 *   a reinstall gets its achievements back.
 *
 * With whichever account is signed in, keeping each account's apart:
 * - the first account to sync (or the same one again) is merged with the device - progress made signed out goes up;
 * - a **different** account from the last one synced ([OnlineSettingsRepository.syncedAccount]) first has the device's
 *   achievements and their progress wiped, as Reset achievements does, then its own restored. The last account's are
 *   already on the service, and come back when it signs in again.
 *
 * Then each mode's leaderboard is sent player 1's best score from the games played under its own rules since the last
 * one it took from this device ([OnlineSettingsRepository.scoresPostedThrough], per account): an account's first sync
 * sends this device's best so far; after that, each newer game's score, or the best of several played while signed out.
 * One the service didn't take is sent again next time.
 *
 * With a [cloudSave], the whole of this device's progress is also merged with the account's cloud save ([CloudSave]):
 * read and merged in first, then written back if this device added anything, and "Last saved" recorded once the two
 * match ([OnlineSettingsRepository.cloudSavedAt]). A different account's switch wipes all of it here first, and waits
 * for the service to answer about the save too. A save written by a newer app is merged in but never written over.
 * Another device's totals can carry a counter, score band or the career points past a target neither reached alone:
 * those achievements are unlocked quietly here, and so reach the service, whose own pop-up announces them.
 *
 * Never once superuser mode has been used here ([OnlineSettingsRepository.mirroringBlocked]): its dice can be set by
 * hand, so neither its unlocks nor its scores are fair - and neither goes in a cloud save.
 */
class OnlineSync(
    private val services: GameServices,
    private val store: AchievementStore,
    private val settings: OnlineSettingsRepository,
    private val leaderboardTotals: suspend () -> LeaderboardTotals,
    /** Player 1's best score in a mode from the games played after a time, as `ScoreRepository.bestUnpostedScore`. */
    private val unpostedScore: suspend (GameMode, afterEpochMillis: Long) -> UnpostedScore?,
    /** This device's side of the cloud save; null syncs achievements and scores alone. */
    private val cloudSave: CloudSaveStore? = null,
    private val now: () -> Long = ::nowEpochMillis,
) {

    private val lock = Mutex()

    private val mutableMirroring = MutableStateFlow(false)

    /**
     * True while unlocks reach the service, as of the last [reconcile]: then it announces them itself, and the app's
     * own unlock banner stands aside for any achievement it [GameServices.mirrors].
     */
    val mirroring: StateFlow<Boolean> = mutableMirroring.asStateFlow()

    private val mutableUnreachable = MutableStateFlow(false)

    /**
     * True while signed in if the last [reconcile] couldn't reach the service - for achievements, scores or the cloud
     * save - so the Settings card can say it will retry. False again once one gets through, or on signing out.
     */
    val unreachable: StateFlow<Boolean> = mutableUnreachable.asStateFlow()

    private val mutableProgress = MutableStateFlow<SyncProgress?>(null)

    /** Which step a [reconcile] for a signed-in account is on, or null when none is under way: the Settings card shows it. */
    val progress: StateFlow<SyncProgress?> = mutableProgress.asStateFlow()

    private val mutableSignInSynced = MutableStateFlow<String?>(null)

    /**
     * The account ([OnlineAccount.accountKey]) whose sign-in sync has finished - got through or not - or null until it
     * has, and again once signed out ([forgetSignInSync]). The Settings card offers Sign out only from then.
     */
    val signInSynced: StateFlow<String?> = mutableSignInSynced.asStateFlow()

    /** Signed out: the next sign-in waits for its own sync again. */
    fun forgetSignInSync() {
        mutableSignInSynced.value = null
    }

    /** [progress], [unreachable] and [signInSynced], for the Settings card. */
    val status = OnlineSyncStatus(progress, unreachable, signInSynced)

    /** The steps a [reconcile] takes, in order: the cloud save's own only where there is one. */
    private val steps = if (cloudSave == null) SyncStep.entries.filter { !it.cloudSaveOnly } else SyncStep.entries

    /** Whether the [reconcile] under way shows its [progress]. */
    private var showingProgress = false

    private fun step(step: SyncStep) {
        val number = steps.indexOf(step) + 1
        if (showingProgress && number > 0) mutableProgress.value = SyncProgress(step, number, steps.size)
    }

    /** What the service had unlocked, as of the last [reconcile], with what that sent it. */
    private var remoteUnlocked: Set<String> = emptySet()

    /**
     * Whether the service announces [achievement]'s unlock, so the app shouldn't: only while still signed in (signing
     * out runs no [reconcile] to clear [mirroring]), and only for its first unlock there - one it already has gets no
     * pop-up from it.
     */
    fun announcesUnlock(achievement: Achievement): Boolean =
        mirroring.value && services.state.value is GameServicesState.SignedIn && services.mirrors(achievement.id) &&
            achievement.id !in remoteUnlocked

    /**
     * Compares this device's achievements with the service's and sends or records the difference, then sends the
     * leaderboards their new scores. Returns how many achievements were restored here. Its [progress] is shown only if
     * [showProgress]: for the sync a sign-in starts, not the quiet ones after it.
     */
    suspend fun reconcile(showProgress: Boolean = false): Int = lock.withLock {
        val account = (services.state.value as? GameServicesState.SignedIn)?.account
        try {
            if (account == null || settings.mirroringBlocked.first()) {
                mutableMirroring.value = false
                mutableUnreachable.value = false
                return 0
            }
            showingProgress = showProgress
            reconcile(account)
        } finally {
            mutableProgress.value = null
            // A sign-in's sync is done however it ended, so the card never waits on one that failed.
            if (showProgress && account != null) mutableSignInSynced.value = account.accountKey
        }
    }

    private suspend fun reconcile(account: OnlineAccount): Int {
        step(SyncStep.DOWNLOADING)
        // Nothing is wiped, or sent, until the service has answered - about the cloud save too.
        val remote = services.unlockedAchievementIds() ?: return 0.also { mutableUnreachable.value = true }
        val cloud = cloudSave?.let { services.readCloudSave(::mergeSaves) }
        val switched = settings.syncedAccount.first().let { it != null && it != account.accountKey }
        if (switched && cloud == CloudSaveRead.Failed) return 0.also { mutableUnreachable.value = true }

        if (switched) {
            store.locked { store.resetAll() }
            cloudSave?.wipe()
        }
        // A save read means there is a store to merge it into: the compiler knows cloudSave isn't null wherever it is.
        val remoteSave = (cloud as? CloudSaveRead.Found)?.let { withContext(Dispatchers.Default) { CloudSaveCodec.decode(it.bytes) } }

        step(SyncStep.SETTINGS)
        remoteSave?.let { cloudSave.mergeSettings(it) }
        step(SyncStep.GAME)
        remoteSave?.let { cloudSave.mergeGame(it) }
        step(SyncStep.ACHIEVEMENTS)
        remoteSave?.let { cloudSave.mergeAchievements(it) }
        val restored = store.locked {
            val state = store.current()
            val missing = Achievement.entries.filter { it.id in remote && !state.isUnlocked(it) }
            store.record(missing.associateWith { now() }, emptyMap())
            missing
        }
        step(SyncStep.SCORES)
        if (remoteSave != null) {
            cloudSave.mergeScores(remoteSave)
            // Last, as the score bands and career points count the merged scores.
            unlockReachedTargets()
        }
        settings.setSyncedAccount(account.accountKey)

        step(SyncStep.UPLOADING)
        val state = store.current()
        remoteUnlocked = remote + state.unlockedAt.keys.map { it.id }
        mutableMirroring.value = true
        val totals = leaderboardTotals()
        for (achievement in Achievement.entries) {
            when {
                state.isUnlocked(achievement) -> if (achievement.id !in remote) services.unlock(achievement.id)
                achievement.progressStyle == ProgressStyle.CUMULATIVE -> {
                    val progress = AchievementEngine.progressOf(achievement, state.counters, totals)
                    if (progress > 0) services.setProgress(achievement.id, progress)
                }
            }
        }
        // The save before the scores: going to the background is often the last chance before the app is closed.
        val saved = when {
            cloudSave == null -> true
            cloud == CloudSaveRead.Failed -> false
            else -> saveToCloud(cloudSave, account.accountKey, remoteSave)
        }
        step(SyncStep.LEADERBOARDS)
        postScores(account.accountKey)
        mutableUnreachable.value = !saved
        return restored.size
    }

    private suspend fun unlockReachedTargets() = store.unlockReachedTargets(leaderboardTotals(), now())

    /**
     * Writes this device's progress, merged with [remoteSave] (the save as read, if any), unless the two already match.
     * False if the service didn't take it; a newer app's save, never written over, counts as reached.
     */
    private suspend fun saveToCloud(cloudSave: CloudSaveStore, accountKey: String, remoteSave: CloudSave?): Boolean {
        if (remoteSave != null && !remoteSave.writableHere) return true
        val local = cloudSave.snapshot()
        val merged = withContext(Dispatchers.Default) { CloudSaveMerge.merge(local, remoteSave ?: CloudSave()) }
        if (merged != remoteSave) {
            val data = withContext(Dispatchers.Default) { CloudSaveCodec.encode(merged) }
            val written = services.writeCloudSave(data, ::mergeSaves) ?: return false
            // Another device's save, written since the read, came back merged in: take its part here too.
            if (!written.contentEquals(data)) {
                withContext(Dispatchers.Default) { CloudSaveCodec.decode(written) }?.let {
                    cloudSave.merge(it)
                    unlockReachedTargets()
                }
            }
        }
        settings.setCloudSavedAt(accountKey, now())
        return true
    }

    /**
     * Two copies of the save as one, for the service to store when it finds them in conflict. Neither is lost: one that
     * can't be read gives way to the other, and a newer app's is kept as it is.
     */
    private fun mergeSaves(local: ByteArray, remote: ByteArray): ByteArray {
        val theirs = CloudSaveCodec.decode(remote) ?: return local
        if (!theirs.writableHere) return remote
        val ours = CloudSaveCodec.decode(local) ?: return remote
        if (!ours.writableHere) return local
        return CloudSaveCodec.encode(CloudSaveMerge.merge(ours, theirs))
    }

    private suspend fun postScores(accountKey: String) {
        for (mode in GameMode.entries) {
            val key = mode.onlineLeaderboardKey
            val best = unpostedScore(mode, settings.scoresPostedThrough(accountKey, key) ?: Long.MIN_VALUE) ?: continue
            if (services.submitScore(key, best.score.toLong())) settings.setScoresPostedThrough(accountKey, key, best.playedEpochMillis)
        }
    }
}

/**
 * Where a change asks for a [OnlineSync.reconcile]: every recorded achievement update and every finished game raises
 * one, and `DiceFiveApp` reconciles in turn. Requests made while one runs fold into a single next run.
 */
object OnlineSyncRequests {

    private val mutableRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val requests: SharedFlow<Unit> = mutableRequests.asSharedFlow()

    fun request() {
        mutableRequests.tryEmit(Unit)
    }
}

/**
 * The parts of an [OnlineSync.reconcile], in the order it takes them, as the Settings card names them; the
 * [cloudSaveOnly] ones are skipped with no cloud save. A merge with nothing to merge (no save yet) still has its step.
 * The cloud save's [merge] steps are a progress file's import's too ([ImportStep.merge]), named the same.
 */
enum class SyncStep(val cloudSaveOnly: Boolean = false, val merge: MergeStep? = null) {
    /** Asking the service what it has - unlocked achievements, and the cloud save - and another account's wipe. */
    DOWNLOADING,

    SETTINGS(cloudSaveOnly = true, merge = MergeStep.SETTINGS),
    GAME(cloudSaveOnly = true, merge = MergeStep.GAME),

    /** The save's achievements merged in, and the service's unlocks restored. */
    ACHIEVEMENTS(merge = MergeStep.ACHIEVEMENTS),

    /** The save's scores merged in, then any achievement the merged totals reach. */
    SCORES(cloudSaveOnly = true, merge = MergeStep.SCORES),

    /** The achievements sent, then the cloud save written. */
    UPLOADING,

    LEADERBOARDS,
}

/** A sync on its [number]th step of [total] (from 1). */
data class SyncProgress(val step: SyncStep, val number: Int, val total: Int)

/**
 * What the Settings card shows of [OnlineSync]: the step a sync is on (null: none under way), whether the last one got
 * through, and whose sign-in sync has finished. With nothing providing one, every sign-in counts as synced.
 */
class OnlineSyncStatus(
    val progress: StateFlow<SyncProgress?> = MutableStateFlow(null),
    val unreachable: StateFlow<Boolean> = MutableStateFlow(false),
    val signInSynced: StateFlow<String?>? = null,
)

/** [OnlineSync.status]; never syncing or unreachable where nothing provides it (previews, tests). */
val LocalOnlineSyncStatus = staticCompositionLocalOf { OnlineSyncStatus() }
