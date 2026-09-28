package net.zodac.dicefive.data.achievements

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * [animate] is false when the screen wasn't already open - the caller is about to navigate to it
 * fresh, and the player has nothing to see a scroll happen *from* yet, so the row should just
 * already be there the moment the screen appears rather than visibly sliding into place. It's true
 * only when the player was already looking at the list when the request was made, where a jump
 * with no animation would instead read as the screen glitching under them.
 */
data class AchievementScrollRequest(val achievementId: String, val animate: Boolean)

/**
 * Where a long-pressed achievement banner asks the Achievements screen to scroll to and flash a
 * specific row - see `ui/achievements/AchievementBannerHost`'s long press and
 * `ui/achievements/AchievementsScreen`'s collector.
 *
 * Replay is 1, not a growing buffer like [AchievementEvents]: only the most recent request
 * matters, and it has to survive the gap between the request being made (before the screen even
 * exists, if it's not already open) and the screen's own collector starting up once it does.
 * [consumePending] clears it after use so it isn't replayed again on a later, unrelated visit to
 * the screen.
 */
object AchievementScrollRequests {

    private val _requests = MutableSharedFlow<AchievementScrollRequest>(replay = 1)
    val requests: SharedFlow<AchievementScrollRequest> = _requests.asSharedFlow()

    fun request(achievementId: String, animate: Boolean) {
        _requests.tryEmit(AchievementScrollRequest(achievementId, animate))
    }

    /** Whether a request is currently waiting to be picked up - checked by the screen's own
     * saved-scroll-position restore, which should sit this one out rather than fight over the
     * list's position with whatever the request is about to do. */
    fun hasPending(): Boolean = _requests.replayCache.isNotEmpty()

    /** Drops the replayed value once it's been acted on. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumePending() {
        _requests.resetReplayCache()
    }
}
