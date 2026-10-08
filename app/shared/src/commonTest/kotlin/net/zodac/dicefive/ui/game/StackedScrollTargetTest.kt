package net.zodac.dicefive.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** Where an overflowing stack of scores scrolls to keep its next slot in view - see [stackedScrollTarget]. */
class StackedScrollTargetTest {

    // Three 40px lines in a 60px tile: up to 60px of scroll.
    private fun target(line: Int) = stackedScrollTarget(contentHeight = 120, viewportHeight = 60, lineCount = 3, targetLine = line, maxScroll = 60)

    @Test
    fun `a stacked box scrolls its first slot to the top - a middle one centred - the last to the bottom - and nothing when there's no room`() {
        // The first slot stays at the top.
        assertEquals(0, target(0))

        // A middle slot is centred.
        // Its top at 40, less half the room left around it: 40 - (60 - 40) / 2.
        assertEquals(30, target(1))

        // The last slot scrolls to the bottom - never past it.
        assertEquals(60, target(2))

        // Nothing to scroll means no scroll.
        assertEquals(0, stackedScrollTarget(contentHeight = 48, viewportHeight = 48, lineCount = 3, targetLine = 2, maxScroll = 0))
    }
}
