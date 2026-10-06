package net.zodac.dicefive.data.achievements

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The styles a long-pressed styles banner names, for the Styles screen to scroll its rows to and flash.
 * [animate] is false when the screen wasn't already open - see [AchievementScrollRequest]. Not a data class: two
 * requests for the same styles are still two requests, each to be scrolled to again.
 */
class StyleScrollRequest(val styles: List<UnlockedStyle>, val animate: Boolean)

/**
 * Where a long-pressed styles banner asks the Styles screen to scroll each category's row to the newly
 * unlocked style and flash it - the twin of [AchievementScrollRequests], and replayed and consumed the
 * same way, for the same reasons.
 */
object StyleScrollRequests {

    private val _requests = MutableSharedFlow<StyleScrollRequest>(replay = 1)
    val requests: SharedFlow<StyleScrollRequest> = _requests.asSharedFlow()

    fun request(styles: List<UnlockedStyle>, animate: Boolean) {
        _requests.tryEmit(StyleScrollRequest(styles, animate))
    }

    /** The request waiting to be picked up, if any - read as the Styles screen first composes, so it opens already in place. */
    fun pending(): StyleScrollRequest? = _requests.replayCache.firstOrNull()

    /** Drops the replayed value once it's been acted on. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumePending() {
        _requests.resetReplayCache()
    }
}
