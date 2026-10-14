package net.zodac.dicefive.ui.game

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.model.GameMode

class BonusFitTest {

    private val standard = boardLayout(GameMode.STANDARD.categories)
    private val tricolour = boardLayout(GameMode.TRICOLOUR.categories)

    @Test
    fun `a roomy board writes its bonus at the full size - on every card`() {
        for (layout in listOf(standard, tricolour)) {
            val fit = fitBonus(layout, pane = 182.dp, bonusWidth = 27.dp, reserve = 0.dp)
            assertEquals(BONUS_MAX_SIZE, fit.fontSize)
        }
    }

    @Test
    fun `a tighter board writes every bonus smaller - at one size - but not below the floor`() {
        val fit = fitBonus(tricolour, pane = 156.dp, bonusWidth = 31.dp, reserve = 0.dp)
        assertTrue(fit.fontSize < BONUS_MAX_SIZE && fit.fontSize >= BONUS_MIN_SIZE, "was ${fit.fontSize}")
        // Narrower still and there's no size that fits: a bare "+" for all of them.
        assertEquals(TextUnit.Unspecified, fitBonus(tricolour, pane = 110.dp, bonusWidth = 31.dp, reserve = 0.dp).fontSize)
    }

    @Test
    fun `a bonus after a score has less room - Third Wind's - so is smaller than one under it`() {
        val under = fitBonus(standard, pane = 170.dp, bonusWidth = 30.dp, reserve = 0.dp).fontSize
        val after = fitBonus(standard, pane = 170.dp, bonusWidth = 30.dp, reserve = 17.dp).fontSize
        assertTrue(after < under, "after $after, under $under")
    }

    @Test
    fun `the cup panel gives the grid what is spare beside the 5x tile - and boxes under it even the two out`() {
        // Nothing under 5x: the spare room goes to the grid, never more than 16dp, never less than none.
        assertEquals(16.dp, fitBonus(standard, pane = 182.dp, bonusWidth = 27.dp, reserve = 0.dp).paneShift)
        assertEquals(0.dp, fitBonus(standard, pane = 140.dp, bonusWidth = 27.dp, reserve = 0.dp).paneShift)
        // Boxes under it: the shift is within 12dp either way, and no card without a 5x moves.
        assertTrue(fitBonus(tricolour, pane = 156.dp, bonusWidth = 31.dp, reserve = 0.dp).paneShift in (-12).dp..12.dp)
        assertEquals(0.dp, fitBonus(boardLayout(GameMode.D20.categories), pane = 182.dp, bonusWidth = 27.dp, reserve = 0.dp).paneShift)
    }
}
