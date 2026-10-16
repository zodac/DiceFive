package net.zodac.dicefive.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.ScoreCategory

class BoardLayoutTest {

    private fun layout(mode: GameMode, extended: Boolean) = boardLayout(mode.categoriesWith(extended))

    @Test
    fun `each card's layout - its rows - the boxes under its featured one and how many rows that takes`() {
        // Standard is six rows with nothing under its large 5x, which takes two rows.
        val standard = layout(GameMode.STANDARD, extended = false)
        assertEquals(6, standard.rowCount)
        assertTrue(standard.sideRows.isEmpty())
        assertEquals(listOf(ScoreCategory.ONES, ScoreCategory.THREE_OF_A_KIND), standard.leftRows.first())
        assertEquals(listOf(ScoreCategory.SIXES, ScoreCategory.CHANCE), standard.leftRows.last())
        assertEquals(ScoreCategory.FIVE_OF_A_KIND, standard.featured)
        assertEquals(2, standard.featuredRows)

        // Extended Scores puts Evens and Odds then Two Pair under 5x without adding rows - 5x one wide row over them.
        val evensAndOdds = listOf(listOf(ScoreCategory.EVENS, ScoreCategory.ODDS), listOf(ScoreCategory.TWO_PAIR))
        val extended = layout(GameMode.STANDARD, extended = true)
        assertEquals(evensAndOdds, extended.sideRows)
        assertEquals(6, extended.rowCount)
        assertEquals(1, extended.featuredRows)

        // Tricolour puts its four colour boxes under 5x as two rows of two.
        val colours = listOf(listOf(ScoreCategory.REDS, ScoreCategory.YELLOWS), listOf(ScoreCategory.BLUES, ScoreCategory.COLOURED_HOUSE))
        val tricolour = layout(GameMode.TRICOLOUR, extended = false)
        assertEquals(colours, tricolour.sideRows)
        assertEquals(6, tricolour.rowCount)
        assertEquals(1, tricolour.featuredRows)

        // Tricolour with Extended Scores gives 5x its three and moves the colours to the grid - and, with the room, 5x takes
        // two rows with the boxes and cup a row lower.
        val tall = layout(GameMode.TRICOLOUR, extended = true)
        assertEquals(evensAndOdds, tall.sideRows)
        assertEquals(8, tall.rowCount)
        assertEquals(colours, tall.leftRows.takeLast(2))
        assertEquals(2, tall.featuredRows)
        assertEquals(4, tall.cupFirstRow)
        assertEquals(3, tall.rowCount - 1 - tall.cupFirstRow)

        // Hit List's targets fill the grid two to a row in card order, with the Alibi as the large square.
        val hitList = layout(GameMode.HIT_LIST, extended = false)
        assertEquals(ScoreCategory.ALIBI, hitList.featured)
        assertEquals(ScoreCategory.TARGETS.chunked(2), hitList.leftRows)
        assertEquals(emptyList(), hitList.sideRows)
        assertEquals(6, hitList.rowCount)
        assertEquals(2, hitList.featuredRows)

        // D20 has no featured box: its twenty-two boxes fill four columns of seven rows, a half to each side, each half's
        // wildcard at its top - the inner two columns four boxes deep, over a cup of two rows and the buttons.
        val d20 = layout(GameMode.D20, extended = false)
        assertNull(d20.featured)
        assertEquals(7, d20.rowCount)
        assertEquals(0, d20.featuredRows)
        assertTrue(d20.leftRows.isEmpty())
        assertTrue(d20.sideRows.isEmpty())
        fun numbers(range: IntRange) = range.map { ScoreCategory.D20_NUMBERS[it - 1] }
        val empty = List(3) { null }
        assertEquals(
            listOf(
                listOf(ScoreCategory.D20_WILD_LOW) + numbers(1..6),
                numbers(7..10) + empty,
                listOf(ScoreCategory.D20_WILD_HIGH) + numbers(11..13) + empty,
                numbers(14..20),
            ),
            d20.columns,
        )
        assertEquals(4, d20.cupFirstRow)
        assertEquals(2, d20.rowCount - 1 - d20.cupFirstRow)
    }

    @Test
    fun `a board laid out by columns is tall enough for the whole cup over the rows above its buttons`() {
        val d20 = boardLayout(GameMode.D20.categories)
        val height = scoreBoardHeight(GameMode.D20.categories, BOARD_PADDING)
        val rowHeight = (height - BOARD_PADDING * 2 - GRID_ROW_SPACING * (d20.rowCount - 1)) / d20.rowCount
        val cupRows = d20.rowCount - 1 - d20.cupFirstRow
        assertTrue(rowHeight * cupRows + GRID_ROW_SPACING * (cupRows - 1) >= CUP_SIZE, "$height")
    }

    @Test
    fun `every card puts each box on the board exactly once - its featured box apart - and leaves the cup two rows or more`() {
        for (mode in GameMode.entries) for (extended in listOf(false, true)) {
            val categories = mode.categoriesWith(extended)
            val layout = boardLayout(categories)
            assertEquals(categories.filter { it != layout.featured }.sorted(), layout.placed.sorted(), "${mode.id} $extended")
            assertTrue(layout.rowCount - 1 - layout.cupFirstRow >= 2, "$mode extended=$extended")
        }
    }
}
