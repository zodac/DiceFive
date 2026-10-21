package net.zodac.dicefive.ui.statistics

import kotlin.test.Test
import kotlin.test.assertEquals

class StatisticsSortOrderTest {

    @Test
    fun `every sort and direction is stored and read back as it was`() {
        for (sort in StatisticsSort.entries) {
            for (ascending in listOf(true, false)) {
                val order = StatisticsSortOrder(sort, ascending)
                assertEquals(order, StatisticsSortOrder.parse(order.stored))
            }
        }
        assertEquals("highest_score:desc", StatisticsSortOrder(StatisticsSort.HIGHEST_SCORE, ascending = false).stored)
        assertEquals("name:asc", StatisticsSortOrder().stored)
    }

    @Test
    fun `nothing stored or a value this build doesn't know gives Name from A to Z`() {
        assertEquals(StatisticsSortOrder(), StatisticsSortOrder.parse(null))
        assertEquals(StatisticsSortOrder(), StatisticsSortOrder.parse("newest:desc"))
        assertEquals(StatisticsSortOrder(), StatisticsSortOrder.parse(""))
        // A known sort with an unknown direction starts in its own first one.
        assertEquals(StatisticsSortOrder(StatisticsSort.MOST_GAMES, ascending = false), StatisticsSortOrder.parse("most_games"))
        assertEquals(StatisticsSortOrder(StatisticsSort.FIRST_PLAYED, ascending = true), StatisticsSortOrder.parse("first_played:sideways"))
    }

    @Test
    fun `picking the current sort reverses it and another starts in its own first direction`() {
        val name = StatisticsSortOrder()
        assertEquals(StatisticsSortOrder(StatisticsSort.NAME, ascending = false), name.pick(StatisticsSort.NAME))
        assertEquals(StatisticsSortOrder(StatisticsSort.MOST_GAMES, ascending = false), name.pick(StatisticsSort.MOST_GAMES))
        assertEquals(StatisticsSortOrder(StatisticsSort.FIRST_PLAYED, ascending = true), name.copy(ascending = false).pick(StatisticsSort.FIRST_PLAYED))
    }
}
