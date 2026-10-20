package net.zodac.dicefive.ui.game

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.ScoreCategory

class GameLayoutTest {

    private val standard = GameMode.STANDARD.categoriesWith(false)

    @Test
    fun `the landscape board puts each section on a row - and the featured box with the buttons last`() {
        val layout = landscapeLayout(standard)
        assertEquals(
            listOf(
                listOf(ScoreCategory.ONES, ScoreCategory.TWOS, ScoreCategory.THREES, ScoreCategory.FOURS, ScoreCategory.FIVES, ScoreCategory.SIXES),
                listOf(
                    ScoreCategory.THREE_OF_A_KIND, ScoreCategory.FOUR_OF_A_KIND, ScoreCategory.FULL_HOUSE,
                    ScoreCategory.SMALL_STRAIGHT, ScoreCategory.LARGE_STRAIGHT, ScoreCategory.CHANCE,
                ),
            ),
            layout.rows,
        )
        assertEquals(ScoreCategory.FIVE_OF_A_KIND, layout.featured)
        assertEquals(listOf(2), layout.buttonRows)
        assertEquals(3, layout.rowCount)
        assertFalse(layout.featuredNarrow)
    }

    @Test
    fun `Tricolour with Extended Scores puts Evens and Odds beside the colours - and Two Pair before 5x`() {
        val layout = landscapeLayout(GameMode.TRICOLOUR.categoriesWith(true))
        assertEquals(listOf(ScoreCategory.EVENS, ScoreCategory.ODDS), layout.rows[2].takeLast(2))
        assertEquals(listOf(ScoreCategory.TWO_PAIR), layout.beforeFeatured)
        assertEquals(4, layout.rowCount)
        assertFalse(layout.featuredNarrow)
    }

    @Test
    fun `Standard with Extended Scores shares the last row - 5x a single tile after three boxes`() {
        val layout = landscapeLayout(GameMode.STANDARD.categoriesWith(true))
        assertEquals(listOf(ScoreCategory.EVENS, ScoreCategory.ODDS, ScoreCategory.TWO_PAIR), layout.beforeFeatured)
        assertEquals(3, layout.rowCount)
        assertTrue(layout.featuredNarrow)
    }

    @Test
    fun `D20 keeps each half to its rows - Totals and Undo in the spare cells of the second and fourth`() {
        val layout = landscapeLayout(GameMode.D20.categoriesWith(false))
        assertEquals(4, layout.rows.size)
        assertEquals(listOf(1, 3), layout.buttonRows)
        assertEquals(4, layout.rowCount)
    }

    @Test
    fun `a wider than tall screen is laid out for landscape - a taller one is not`() {
        assertTrue(gameLayout(1280.dp, 800.dp, standard).landscape)
        assertFalse(gameLayout(800.dp, 1280.dp, standard).landscape)
    }

    @Test
    fun `a taller header draws the game smaller - and leaves it room on a screen that has it`() {
        val coach = 170.dp
        val tabs = gameLayout(800.dp, 1280.dp, standard)
        val tutorial = gameLayout(800.dp, 1280.dp, standard, headerHeight = coach)
        assertTrue(tutorial.scale < tabs.scale)
        // Fitted at its scale, so the whole header fits.
        assertTrue(tutorial.headerRoom >= coach)
    }

    @Test
    fun `on a phone short of room for the coach card the mat gives up as much as it takes - and the game keeps its size`() {
        val coach = 170.dp
        val layout = gameLayout(360.dp, 772.dp, standard, headerHeight = coach)
        assertEquals(1f, layout.scale)
        assertTrue(layout.scatterHeight < SCATTER_AREA_HEIGHT)
        assertTrue(layout.scatterHeight > MIN_SCATTER_HEIGHT)
        assertEquals(coach.value, layout.headerRoom.value, 0.01f)
    }

    @Test
    fun `a card too tall even then has the shallowest mat - and is told the room left`() {
        val coach = 300.dp
        val layout = gameLayout(360.dp, 772.dp, standard, headerHeight = coach)
        assertEquals(MIN_SCATTER_HEIGHT, layout.scatterHeight)
        assertTrue(layout.headerRoom < coach)
    }

    @Test
    fun `the player tabs need no shallower mat on a phone`() {
        assertEquals(SCATTER_AREA_HEIGHT, gameLayout(360.dp, 772.dp, standard).scatterHeight)
    }
}
