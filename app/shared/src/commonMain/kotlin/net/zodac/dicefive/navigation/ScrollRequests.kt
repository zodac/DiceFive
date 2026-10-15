package net.zodac.dicefive.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.zodac.dicefive.data.achievements.UnlockedStyle

/**
 * Where a long-pressed banner asks a list screen to scroll to and flash what it named - see
 * `ui/achievements/AchievementBannerHost`'s long press, and each screen's collector.
 *
 * Replay is 1, not a growing buffer like [net.zodac.dicefive.data.achievements.AchievementEvents]: only the most recent
 * request matters, and it has to survive the gap between the request being made (before the screen even exists, if
 * it's not already open) and the screen's own collector starting up once it does. [consumePending] clears it after
 * use so it isn't replayed again on a later, unrelated visit to the screen.
 */
open class ScrollRequests<T : Any> {

    private val _requests = MutableSharedFlow<T>(replay = 1)
    val requests: SharedFlow<T> = _requests.asSharedFlow()

    fun request(request: T) {
        _requests.tryEmit(request)
    }

    /** The request waiting to be picked up, if any - read as a screen first composes, so it opens already in place. */
    fun pending(): T? = _requests.replayCache.firstOrNull()

    /** Drops the replayed value once it's been acted on. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumePending() {
        _requests.resetReplayCache()
    }
}

/**
 * [animate] is false when the screen wasn't already open - the caller is about to navigate to it
 * fresh, and the player has nothing to see a scroll happen *from* yet, so the row should just
 * already be there the moment the screen appears rather than visibly sliding into place. It's true
 * only when the player was already looking at the list when the request was made, where a jump
 * with no animation would instead read as the screen glitching under them.
 */
data class AchievementScrollRequest(val achievementId: String, val animate: Boolean)

/**
 * The styles a long-pressed styles banner names, for the Styles screen to scroll its rows to and flash.
 * [animate] is false when the screen wasn't already open - see [AchievementScrollRequest]. Not a data class: two
 * requests for the same styles are still two requests, each to be scrolled to again.
 */
class StyleScrollRequest(val styles: List<UnlockedStyle>, val animate: Boolean)

/** The Achievements screen's: scroll to one row and flash it. */
object AchievementScrollRequests : ScrollRequests<AchievementScrollRequest>()

/** The Styles screen's: scroll each category's row to its newly unlocked style and flash it. */
object StyleScrollRequests : ScrollRequests<StyleScrollRequest>()
