package net.zodac.dicefive.ui.common

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The drift is slow, so a fast display doesn't redraw it on every refresh - but never below 60 times a second where the
 * display can - and, run as the backdrop runs it, only wakes for about as many frames.
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
    fun `run moves the dice at least 60 times a second on any display that fast - and wakes for no frame it would skip`() {
        val expectedMoves = mapOf(60 to 59..61, 90 to 88..91, 120 to 59..61, 144 to 70..73)
        for ((hz, expected) in expectedMoves) {
            val (wakes, moves) = runForOneSecond(1_000_000_000L / hz)
            assertTrue(moves in expected, "${hz}Hz moved the dice $moves times")
            assertEquals(moves, wakes, "${hz}Hz")
        }
    }

    @Test
    fun `the drift redraws on every other refresh at 120Hz - and every refresh at 30 - 60 or 90Hz`() {
        assertTrue(redrawsInOneSecond(8_333_333L) in 59..61)
        assertTrue(redrawsInOneSecond(16_666_667L) in 59..61)
        assertTrue(redrawsInOneSecond(11_111_111L) in 89..91)
        assertTrue(redrawsInOneSecond(33_333_333L) in 29..31)
    }

    private companion object {
        /** How long after its refresh a frame's callback runs. */
        const val CALLBACK_LATENCY_NANOS = 2_000_000L
    }
}
