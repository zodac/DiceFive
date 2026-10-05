package net.zodac.dicefive.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** What a screen reader hears for a box with several slots (Third Wind) - see [stackedSpokenState]. */
class StackedSpokenStateTest {

    @Test
    fun `an untouched box says how many slots are open`() {
        assertEquals("3 open", stackedSpokenState(emptyList(), slotCount = 3, previewScore = null, bonusAmount = 0, lastScored = false))
    }

    @Test
    fun `a preview fills the next slot - and the rest are more open`() {
        assertEquals("Scored 50, would score 50, 1 more open", stackedSpokenState(listOf(50), 3, previewScore = 50, bonusAmount = 0, lastScored = false))
        assertEquals("Scored 12, 9, would score 0", stackedSpokenState(listOf(12, 9), 3, previewScore = 0, bonusAmount = 0, lastScored = false))
    }

    @Test
    fun `the 5x box adds its bonus - and the last turn's score comes last`() {
        assertEquals(
            "Scored 50, 0, 50, plus 200 bonus, last turn's score 50",
            stackedSpokenState(listOf(50, 0, 50), 3, previewScore = null, bonusAmount = 200, lastScored = true),
        )
    }
}
