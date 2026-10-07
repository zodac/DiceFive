package net.zodac.dicefive.device

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Compose's frame clock for the whole window, handing on at most [maxFramesPerSecond] frames a second - the player's
 * "Animations" level (see `AndroidPlatformServices.capFrameRate`). Everything that animates in Compose, the recomposer
 * included, waits on this clock, so it caps whatever moves: the app's own animations and what's left at "Off"
 * (Material's touch ripples and switch thumbs, a scroll's fling). Installed by `MainActivity` as its window's
 * recomposer's clock; uncapped, it hands every frame straight on.
 *
 * A frame is handed on if it's at least 90% of a capped frame's length after the last one handed on (slack for vsync
 * jitter) - or is that same frame, so every caller waiting on one frame gets it, not just the first. So a cap only
 * ever skips whole refreshes: at 60 a 120Hz screen gives 60 frames a second, a 90Hz one 45 and a 144Hz one 48 (where
 * the screen can't drop to 60Hz itself - `MainActivity` asks it to); at 30, anything from 60Hz up gives 29-30. Only
 * touched on the main thread, which is where [base]'s frames are delivered.
 */
internal class CappedFrameClock(private val base: MonotonicFrameClock) : MonotonicFrameClock {

    private var lastFrameNanos = 0L

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        while (true) {
            var handedOn = false
            var result: R? = null
            base.withFrameNanos { frameTimeNanos ->
                val minGap = minFrameGapNanos
                if (minGap == 0L || frameTimeNanos == lastFrameNanos || frameTimeNanos - lastFrameNanos >= minGap) {
                    lastFrameNanos = frameTimeNanos
                    result = onFrame(frameTimeNanos)
                    handedOn = true
                }
            }
            // R may itself be nullable, so whether the frame was handed on is tracked separately from the result.
            @Suppress("UNCHECKED_CAST")
            if (handedOn) return result as R
        }
    }

    companion object {
        private val limit = MutableStateFlow<Int?>(null)

        /** The shortest gap between two frames handed on, from [maxFramesPerSecond]: 0 when uncapped. Read on every frame. */
        @Volatile
        private var minFrameGapNanos = 0L

        /** The most frames a second handed on, or null for every frame; set from the player's "Animations" level. */
        var maxFramesPerSecond: Int?
            get() = limit.value
            set(value) {
                minFrameGapNanos = value?.let { 1_000_000_000L / it * 9 / 10 } ?: 0L
                limit.value = value
            }

        /** [maxFramesPerSecond] and every change to it, for `MainActivity` to ask the screen for a matching refresh rate. */
        val maxFramesPerSecondChanges: StateFlow<Int?> = limit.asStateFlow()
    }
}
