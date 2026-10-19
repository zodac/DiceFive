package net.zodac.dicefive.ui.game

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TotalsButtonTest {
    @Test
    fun `a bonus gained pops the button`() {
        assertTrue(bonusJustEarned(previous = 0, now = 1))
    }

    @Test
    fun `D20's second half pops it again`() {
        assertTrue(bonusJustEarned(previous = 1, now = 2))
    }

    @Test
    fun `no change or an undone bonus does not pop it`() {
        assertFalse(bonusJustEarned(previous = 1, now = 1))
        assertFalse(bonusJustEarned(previous = 2, now = 1))
    }
}
