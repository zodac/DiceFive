package net.zodac.dicefive.ui.common

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The drift is slow, so it's redrawn about 30 times a second however fast the display refreshes - and, run as the
 * backdrop runs it, only wakes for about as many frames.
 */
class DriftFrameRateTest {

    private fun redrawsInOneSecond(frameNanos: Long): Int {
        val drift = DriftState().apply { size = Size(1080f, 2000f) }
        var redraws = 0
        var seen = drift.frame.longValue
        var now = 1_000_000_000L
        val end = now + 1_000_000_000L
        while (now < end) {
            drift.onFrame(now)
            if (drift.frame.longValue != seen) {
                redraws++
                seen = drift.frame.longValue
            }
            now += frameNanos
        }
        return redraws
    }

    /** One second of [DriftState.run] on a display refreshing every [frameNanos]: the frames it woke for, and the moves. */
    private fun runForOneSecond(frameNanos: Long): Pair<Int, Int> {
        val drift = DriftState().apply { size = Size(1080f, 2000f) }
        val start = 1_000_000_000L
        var clock = start
        var wakes = 0
        var moves = 0
        runTest {
            try {
                drift.run(
                    sleep = { millis -> clock += millis * 1_000_000L },
                    awaitFrame = { onFrame ->
                        // The next refresh after now: its time is what the frame is handed, and its callback runs a little later.
                        val vsync = (clock / frameNanos + 1) * frameNanos
                        if (vsync >= start + 1_000_000_000L) throw OneSecondUp()
                        clock = vsync + CALLBACK_LATENCY_NANOS
                        wakes++
                        onFrame(vsync).also { if (it) moves++ }
                    },
                )
            } catch (_: OneSecondUp) {
                // Done.
            }
        }
        return wakes to moves
    }

    private class OneSecondUp : RuntimeException()

    @Test
    fun `run wakes for about 30 frames a second however fast the display - and moves the dice on nearly every one`() {
        for (hz in listOf(60, 90, 120, 144)) {
            val (wakes, moves) = runForOneSecond(1_000_000_000L / hz)
            assertTrue(moves in 27..31, "${hz}Hz moved the dice $moves times")
            assertTrue(wakes <= moves * 2, "${hz}Hz woke $wakes times for $moves moves")
        }
        // On a 60, 90 or 120Hz display, every frame woken for moves the dice.
        for (hz in listOf(60, 90, 120)) {
            val (wakes, moves) = runForOneSecond(1_000_000_000L / hz)
            assertEquals(moves, wakes, "${hz}Hz")
        }
    }

    @Test
    fun `the drift redraws about 30 times a second on a 30 - 60 or 120Hz display`() {
        // A 60Hz display redraws the drift about 30 times a second.
        assertTrue(redrawsInOneSecond(16_666_667L) in 29..31)

        // A 120Hz display redraws it no more often than that.
        assertTrue(redrawsInOneSecond(8_333_333L) in 29..34)

        // A 30Hz display is not slowed further.
        assertTrue(redrawsInOneSecond(33_333_333L) in 29..31)
    }

    private companion object {
        /** How long after its refresh a frame's callback runs. */
        const val CALLBACK_LATENCY_NANOS = 2_000_000L
    }
}
