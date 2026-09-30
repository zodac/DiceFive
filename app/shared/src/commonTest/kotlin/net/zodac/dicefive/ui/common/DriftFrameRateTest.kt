package net.zodac.dicefive.ui.common

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertTrue

/** The drift is slow, so it's redrawn about 30 times a second however fast the display refreshes. */
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

    @Test
    fun `a 60Hz display redraws the drift about 30 times a second`() {
        assertTrue(redrawsInOneSecond(16_666_667L) in 29..31)
    }

    @Test
    fun `a 120Hz display redraws it no more often than that`() {
        assertTrue(redrawsInOneSecond(8_333_333L) in 29..34)
    }

    @Test
    fun `a 30Hz display is not slowed further`() {
        assertTrue(redrawsInOneSecond(33_333_333L) in 29..31)
    }
}
