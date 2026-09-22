package net.zodac.dicefive.data.achievements

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.zodac.dicefive.model.Achievement

/** Something worth putting on screen for a moment - see `ui/achievements/AchievementBannerHost`. */
sealed interface AchievementEvent {

    val achievement: Achievement

    /** Earned just now. */
    data class Unlocked(override val achievement: Achievement) : AchievementEvent

    /** Moved closer to being earned. [current] is the new progress, out of the target. */
    data class Progressed(override val achievement: Achievement, val current: Int) : AchievementEvent
}

/**
 * Where unlock banners are announced, and where the banner host listens.
 *
 * A process-wide object rather than state on a ViewModel, because the two ends have no scope in
 * common: achievements are raised by the "play"-graph-scoped `GameViewModel`, while the host sits
 * above the whole `NavHost` in `MainActivity` so an end-of-game burst keeps showing as the player
 * moves from the board to the results screen (and on to the menu). Achievements are per device,
 * and there is exactly one of these per process, which is the same scope.
 *
 * The buffer is deep enough for a whole game's worth at once - the end of a game can unlock a
 * dozen - and drops the *oldest* if it somehow overflows, since the newest banner is the one the
 * player is waiting to see. Emission never suspends, so a slow collector (the host deliberately
 * staggers what it takes) can't stall the game.
 */
object AchievementEvents {

    private const val BUFFER_CAPACITY = 64

    private val _events = MutableSharedFlow<AchievementEvent>(
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<AchievementEvent> = _events.asSharedFlow()

    fun emit(event: AchievementEvent) {
        _events.tryEmit(event)
    }
}
