package net.zodac.dicefive.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.ScoreCategory

class BoardLayoutTest {

    private fun layout(mode: GameMode, extended: Boolean) = boardLayout(mode.categoriesWith(extended))

    @Test
    fun `Standard is six rows with nothing under 5x`() {
        val layout = layout(GameMode.STANDARD, extended = false)

        assertEquals(6, layout.rowCount)
        assertTrue(layout.sideRows.isEmpty())
        assertEquals(listOf(ScoreCategory.ONES, ScoreCategory.THREE_OF_A_KIND), layout.leftRows.first())
        assertEquals(listOf(ScoreCategory.SIXES, ScoreCategory.CHANCE), layout.leftRows.last())
    }

    @Test
    fun `Extended Scores puts Evens and Odds then Two Pair under 5x without adding rows`() {
        val layout = layout(GameMode.STANDARD, extended = true)

        assertEquals(listOf(listOf(ScoreCategory.EVENS, ScoreCategory.ODDS), listOf(ScoreCategory.TWO_PAIR)), layout.sideRows)
        assertEquals(6, layout.rowCount)
    }

    @Test
    fun `Tricolour puts its four colour boxes under 5x as two rows of two`() {
        val layout = layout(GameMode.TRICOLOUR, extended = false)

        assertEquals(
            listOf(
                listOf(ScoreCategory.REDS, ScoreCategory.YELLOWS),
                listOf(ScoreCategory.BLUES, ScoreCategory.COLOURED_HOUSE),
            ),
            layout.sideRows,
        )
        assertEquals(6, layout.rowCount)
    }

    @Test
    fun `Tricolour with Extended Scores gives 5x its three and moves the colours to the grid`() {
        val layout = layout(GameMode.TRICOLOUR, extended = true)

        assertEquals(listOf(listOf(ScoreCategory.EVENS, ScoreCategory.ODDS), listOf(ScoreCategory.TWO_PAIR)), layout.sideRows)
        assertEquals(8, layout.rowCount)
        assertEquals(listOf(listOf(ScoreCategory.REDS, ScoreCategory.YELLOWS), listOf(ScoreCategory.BLUES, ScoreCategory.COLOURED_HOUSE)), layout.leftRows.takeLast(2))
    }

    @Test
    fun `every box of every card is on the board exactly once - its featured box apart`() {
        for (mode in GameMode.entries) for (extended in listOf(false, true)) {
            val categories = mode.categoriesWith(extended)
            val layout = boardLayout(categories)
            val placed = layout.leftRows.flatten() + layout.sideRows.flatten()

            assertEquals(categories.filter { it != layout.featured }.sorted(), placed.sorted(), "${mode.id} $extended")
        }
    }

    @Test
    fun `Hit List's targets fill the grid two to a row in card order - with the Alibi as the large square`() {
        val layout = layout(GameMode.HIT_LIST, extended = false)

        assertEquals(ScoreCategory.ALIBI, layout.featured)
        assertEquals(ScoreCategory.TARGETS.chunked(2), layout.leftRows)
        assertEquals(emptyList(), layout.sideRows)
        assertEquals(6, layout.rowCount)
        assertEquals(ScoreCategory.FIVE_OF_A_KIND, layout(GameMode.STANDARD, extended = false).featured)
    }
}
