package net.zodac.dicefive.ui.game.style

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RabbitRailTest {

    @Test
    fun aRabbitSitsMidHatWhenStillGoesToTheLowEndWhenTippedAndNeverLeavesItsRail() {
        assertEquals(0f, rabbitRailX(rotation = 0f, slosh = 0f), 0.001f)
        assertEquals(0f, rabbitLean(slosh = 0f), 0.001f)

        // Resting tilt is anticlockwise (negative): the left side is the low one.
        assertEquals(-RABBIT_RAIL_HALF_LENGTH, rabbitRailX(RESTING_TILT_DEGREES, 0f), 0.001f)
        assertEquals(RABBIT_RAIL_HALF_LENGTH, rabbitRailX(-RESTING_TILT_DEGREES, 0f), 0.001f)

        // However hard it's thrown about, it stays on the rail and leans only a little.
        for (rotation in -90..90 step 5) for (slosh in -60..60 step 5) {
            assertTrue(abs(rabbitRailX(rotation.toFloat(), slosh.toFloat())) <= RABBIT_RAIL_HALF_LENGTH, "off the rail at $rotation, $slosh")
            assertTrue(abs(rabbitLean(slosh.toFloat())) <= 6f, "leans too far at $slosh")
        }

        // A shake's swing (about 7 degrees) moves it less than half the rail.
        assertTrue(abs(rabbitRailX(7f, 0f)) < RABBIT_RAIL_HALF_LENGTH / 2f)
    }
}
