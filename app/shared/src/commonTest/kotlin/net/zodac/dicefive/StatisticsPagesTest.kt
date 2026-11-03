package net.zodac.dicefive

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.statistics.StatisticsPage
import net.zodac.dicefive.ui.statistics.StatisticsScreen
import net.zodac.dicefive.ui.statistics.StatisticsSort
import net.zodac.dicefive.ui.statistics.StatisticsSortOrder
import net.zodac.dicefive.ui.statistics.StatisticsViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** Statistics' pages: the drop-down that picks one, what each page holds, and each page's own sort. */
class StatisticsPagesTest : UiTest() {

    private val showcase = Showcase { compose }

    @Test
    fun `a stored page names itself by its mode's id or combined - and nothing stored or a mode no longer known is Standard's`() {
        for (page in StatisticsPage.all) assertEquals(page, StatisticsPage.parse(page.stored))
        assertEquals(GameMode.entries.map { StatisticsPage.Mode(it) } + StatisticsPage.Combined, StatisticsPage.all)
        assertEquals(StatisticsPage.STANDARD, StatisticsPage.parse(null))
        assertEquals(StatisticsPage.STANDARD, StatisticsPage.parse("retired_mode"))
        assertEquals("combined", StatisticsPage.Combined.stored)
    }

    @Test
    fun `the page drop-down is one control - lists each mode then Combined - and a page with no games says so`() = uiTest(TestScreen(400, 3000)) {
        val dao = InMemoryScoreDao(listOf(rowOf(1, 200, GameMode.STANDARD), rowOf(2, 900, GameMode.STUD)))
        val settings = SettingsRepository(InMemoryPreferences())
        val viewModel = StatisticsViewModel(ScoreRepository(dao), settings)
        showcase.show { DiceFiveTheme { StatisticsScreen(viewModel = viewModel, onBack = {}) } }
        compose.waitForIdle()

        val field = compose.onNodeWithContentDescription("Statistics, Standard")
        assertEquals(Role.DropdownList, field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        // Only Standard's player has a card on its page.
        compose.onAllNodes(hasContentDescription("Player2.", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Player1.", substring = true)).assertCountEquals(1)

        field.performClick()
        compose.waitForIdle()
        val names = listOf("Standard", "Tricolour", "Third Wind", "7 Dice Stud", "D20", "Hit List", "Quickfire", "Combined")
        names.forEachIndexed { index, name ->
            val item = compose.onNodeWithContentDescription(name).fetchSemanticsNode().config
            assertEquals(index, item.getOrNull(SemanticsProperties.CollectionItemInfo)!!.rowIndex, name)
        }
        compose.onNodeWithContentDescription("Tricolour").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No stats in Tricolour yet").assertExists()
        assertEquals("tricolour", settings.statisticsChoice.first())

        // Combined has both players.
        compose.onNodeWithContentDescription("Statistics, Tricolour").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Combined").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Player1.", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("Player2.", substring = true)).assertCountEquals(1)
        compose.onAllNodesWithText("No stats", substring = true).assertCountEquals(0)
    }

    @Test
    fun `each page keeps its own sort`() = uiTest {
        val settings = SettingsRepository(InMemoryPreferences())
        val viewModel = StatisticsViewModel(ScoreRepository(InMemoryScoreDao()), settings)
        val byScore = StatisticsSortOrder(StatisticsSort.HIGHEST_SCORE)
        viewModel.setSortOrder(StatisticsPage.Mode(GameMode.STUD), byScore)
        compose.waitForIdle()
        val sorts = MutableStateFlow(viewModel.sortOrders.first { it?.get(StatisticsPage.Mode(GameMode.STUD)) == byScore })
        assertEquals(StatisticsSortOrder(), sorts.value!!.getValue(StatisticsPage.STANDARD))
        assertEquals(StatisticsSortOrder(), sorts.value!!.getValue(StatisticsPage.Combined))
        assertEquals(mapOf("stud" to byScore.stored), settings.statisticsSorts.first())
    }
}
