package net.zodac.dicefive.online

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
 * Never once superuser mode has been used here ([OnlineSettingsRepository.mirroringBlocked]): its dice can be set by
 * hand, so neither its unlocks nor its scores are fair.
 */
class OnlineSync(
    private val services: GameServices,
    private val store: AchievementStore,
    private val settings: OnlineSettingsRepository,
    private val leaderboardTotals: suspend () -> LeaderboardTotals,
    /** Player 1's best score in a mode from the games played after a time, as `ScoreRepository.bestUnpostedScore`. */
    private val unpostedScore: suspend (GameMode, afterEpochMillis: Long) -> UnpostedScore?,
    private val now: () -> Long = ::nowEpochMillis,
) {

    private val lock = Mutex()

    private val mutableMirroring = MutableStateFlow(false)

    /**
     * True while unlocks reach the service, as of the last [reconcile]: then it announces them itself, and the app's
     * own unlock banner stands aside for any achievement it [GameServices.mirrors].
     */
    val mirroring: StateFlow<Boolean> = mutableMirroring.asStateFlow()

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
     * leaderboards their new scores. Returns how many achievements were restored here.
     */
    suspend fun reconcile(): Int = lock.withLock {
        val account = (services.state.value as? GameServicesState.SignedIn)?.account
        if (account == null || settings.mirroringBlocked.first()) {
            mutableMirroring.value = false
            return 0
        }
        // Nothing is wiped, or sent, until the service has answered.
        val remote = services.unlockedAchievementIds() ?: return 0
        val switched = settings.syncedAccount.first().let { it != null && it != account.accountKey }

        val restored = store.locked {
            if (switched) store.resetAll()
            val state = store.current()
            val missing = Achievement.entries.filter { it.id in remote && !state.isUnlocked(it) }
            store.record(missing.associateWith { now() }, emptyMap())
            missing
        }
        settings.setSyncedAccount(account.accountKey)

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
        postScores(account.accountKey)
        restored.size
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
