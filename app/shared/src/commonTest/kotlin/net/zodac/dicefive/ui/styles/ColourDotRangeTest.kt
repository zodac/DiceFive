package net.zodac.dicefive.ui.styles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColourDotRangeTest {

    @Test
    fun aStyleWithFewColoursShowsEveryDot() {
        assertEquals(0..0, colourDotRange(1, 0))
        assertEquals(0..2, colourDotRange(3, 2))
    }

    @Test
    fun aLongerStyleShowsAWindowAroundTheShownColour() {
        assertEquals(0..2, colourDotRange(7, 0))
        assertEquals(0..2, colourDotRange(7, 1))
        assertEquals(2..4, colourDotRange(7, 3))
        assertEquals(4..6, colourDotRange(7, 6))
    }

    @Test
    fun theShownColourIsAlwaysInTheWindow() {
        for (count in 1..12) {
            for (shown in 0 until count) {
                val range = colourDotRange(count, shown)
                assertTrue(shown in range, "$shown of $count isn't in $range")
                assertTrue(range.count() <= MAX_COLOUR_DOTS)
            }
        }
    }
}
