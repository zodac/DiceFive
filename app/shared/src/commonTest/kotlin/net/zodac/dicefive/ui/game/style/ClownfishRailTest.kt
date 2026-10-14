package net.zodac.dicefive.ui.game.style

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClownfishRailTest {

    private val samples = (0..2000).map { it / 2000f }

    @Test
    fun theFishStaysOnItsRailSwimsSideOnAndTurnsFaceOnWithoutJumping() {
        // Side-on at each end, heading right for the first half of the lap and left for the second.
        assertEquals(-CLOWNFISH_RAIL_HALF_LENGTH to 0f, clownfishOnRail(0f))
        assertEquals(CLOWNFISH_RAIL_HALF_LENGTH to 180f, clownfishOnRail(0.5f))
        assertEquals(0f, clownfishOnRail(0.23f).second)
        assertEquals(180f, clownfishOnRail(0.73f).second)

        for (lap in samples) {
            val (x, yaw) = clownfishOnRail(lap)
            assertTrue(abs(x) <= CLOWNFISH_RAIL_HALF_LENGTH + 0.001f, "off the rail at $lap")
            // Every turn goes through 90 - face towards the viewer - never round the back.
            assertTrue(yaw in 0f..180f, "turned round the back at $lap: $yaw")
        }
        // Each turn passes face-on.
        assertTrue(samples.filter { it < 0.5f }.any { abs(clownfishOnRail(it).second - 90f) < 5f })
        assertTrue(samples.filter { it >= 0.5f }.any { abs(clownfishOnRail(it).second - 90f) < 5f })

        // No jumps, across the whole lap and round from its end to its start.
        for ((from, to) in (samples + samples.first()).zipWithNext()) {
            val (x0, yaw0) = clownfishOnRail(from)
            val (x1, yaw1) = clownfishOnRail(to)
            assertTrue(abs(x1 - x0) < 0.1f, "jumped along the rail at $from")
            assertTrue(abs(yaw1 - yaw0) < 5f, "snapped round at $from")
        }
    }

    @Test
    fun aSpentBowlHoldsTheFishWhereItWasAndCarriesOnFromThereNextTurn() {
        val held = HeldLap()
        // Never run (reduced motion from the start): no lap, so the resting pose.
        assertNull(held.shown(null))

        assertEquals(0.3f, held.shown(0.3f)!!, 0.0001f)
        assertEquals(0.4f, held.shown(0.4f)!!, 0.0001f)
        // Spent: the clock stops, and the fish stays where it was.
        assertEquals(0.4f, held.shown(null)!!, 0.0001f)
        assertEquals(0.4f, held.shown(null)!!, 0.0001f)
        // The next turn's clock starts again from 0, and the fish carries on from 0.4.
        assertEquals(0.4f, held.shown(0f)!!, 0.0001f)
        assertEquals(0.5f, held.shown(0.1f)!!, 0.0001f)
        // Round past the end of the lap.
        assertEquals(0.1f, held.shown(0.7f)!!, 0.0001f)
    }
}
