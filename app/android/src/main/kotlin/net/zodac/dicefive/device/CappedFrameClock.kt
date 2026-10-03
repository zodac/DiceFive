package net.zodac.dicefive.device

import androidx.compose.runtime.MonotonicFrameClock

/** The shortest gap between two frames handed on while capped: 30 a second, with some slack for vsync jitter. */
private const val MIN_CAPPED_FRAME_GAP_NANOS = 1_000_000_000L / 30 * 9 / 10

/**
 * Compose's frame clock for the whole window, capped at 30 frames a second while [capped] is true - the player's
 * "Remove animations" (see `AndroidPlatformServices.capFrameRate`). Everything that animates in Compose, the
 * recomposer included, waits on this clock, so what still moves with the decoration stopped (a banner sliding in,
 * a cup's lid opening) is drawn at most 30 times a second. Installed by `MainActivity` as its window's recomposer's
 * clock; when not capped it hands every frame straight on.
 *
 * A frame is handed on if it's at least [MIN_CAPPED_FRAME_GAP_NANOS] after the last one handed on - or is that same
 * frame, so every caller waiting on one frame gets it, not just the first. Only touched on the main thread, which
 * is where [base]'s frames are delivered.
 */
internal class CappedFrameClock(private val base: MonotonicFrameClock) : MonotonicFrameClock {

    private var lastFrameNanos = 0L

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        while (true) {
            var handedOn = false
            var result: R? = null
            base.withFrameNanos { frameTimeNanos ->
                if (!capped || frameTimeNanos == lastFrameNanos || frameTimeNanos - lastFrameNanos >= MIN_CAPPED_FRAME_GAP_NANOS) {
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
        /** Set from the player's "Remove animations"; read on every frame. */
        @Volatile
        var capped: Boolean = false
    }
}
