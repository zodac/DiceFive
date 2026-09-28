package net.zodac.dicefive.data.achievements

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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

    private val _requests = MutableSharedFlow<String>(replay = 1)
    val requests: SharedFlow<String> = _requests.asSharedFlow()

    fun request(achievementId: String) {
        _requests.tryEmit(achievementId)
    }

    /** Whether a request is currently waiting to be picked up - checked by the screen's own
     * saved-scroll-position restore, which should sit this one out rather than fight over the
     * list's position with whatever the request is about to do. */
    fun hasPending(): Boolean = _requests.replayCache.isNotEmpty()

    /** Drops the replayed value once it's been acted on. */
    fun consumePending() {
        _requests.resetReplayCache()
    }
}
