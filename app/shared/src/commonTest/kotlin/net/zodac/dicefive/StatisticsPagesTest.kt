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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.PlayerModeTotals
import net.zodac.dicefive.data.scores.PlayerRecord
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.scores.WinStreaks
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.ModePage
import net.zodac.dicefive.ui.statistics.StatisticsScreen
import net.zodac.dicefive.ui.statistics.StatisticsSort
import net.zodac.dicefive.ui.statistics.StatisticsSortOrder
import net.zodac.dicefive.ui.statistics.StatisticsUiState
import net.zodac.dicefive.ui.statistics.StatisticsViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** Statistics' pages: the drop-down that picks one, what each page holds, and each page's own sort. */
class StatisticsPagesTest : UiTest() {

    private val showcase = Showcase { compose }

    @Test
    fun `a stored page names itself by its mode's id or combined - and nothing stored or a mode no longer known is Standard's`() {
        for (page in ModePage.all) assertEquals(page, ModePage.parse(page.stored))
        assertEquals(GameMode.entries.map { ModePage.Mode(it) } + ModePage.Combined, ModePage.all)
        assertEquals(ModePage.STANDARD, ModePage.parse(null))
        assertEquals(ModePage.STANDARD, ModePage.parse("retired_mode"))
        assertEquals("combined", ModePage.Combined.stored)
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
        compose.onNodeWithText("No statistics in Tricolour yet").assertExists()
        assertEquals("tricolour", settings.statisticsChoice.first())

        // Combined has both players.
        compose.onNodeWithContentDescription("Statistics, Tricolour").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Combined").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Player1.", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("Player2.", substring = true)).assertCountEquals(1)
        compose.onAllNodesWithText("No statistics", substring = true).assertCountEquals(0)
    }

    /** Sam's record, or a failed read while [failing]. */
    private class FlakyScoreDao : ScoreDao by InMemoryScoreDao() {
        var failing = true

        override suspend fun playerRecords(): List<PlayerRecord> {
            check(!failing) { "The database can't be read" }
            return listOf(PlayerRecord("Sam", listOf(PlayerModeTotals("sam", GameMode.STANDARD.id, 1, 0, 0, 1, 100, 100, 0, 0L)), WinStreaks(), emptyMap()))
        }
    }

    @Test
    fun `stats that can't be read open empty rather than taking the app down - and later keep the cards on view`() = uiTest {
        val dao = FlakyScoreDao()
        val viewModel = StatisticsViewModel(ScoreRepository(dao), SettingsRepository(InMemoryPreferences()))
        compose.waitForIdle()
        assertEquals(StatisticsUiState(players = emptyList(), isLoaded = true), viewModel.uiState.value)

        dao.failing = false
        viewModel.dismissPlayer("Ada")
        compose.waitForIdle()
        assertEquals(listOf("Sam"), viewModel.uiState.value.players.map { it.playerName })

        dao.failing = true
        viewModel.dismissPlayer("Ada")
        compose.waitForIdle()
        assertEquals(listOf("Sam"), viewModel.uiState.value.players.map { it.playerName })
    }

    @Test
    fun `deleting a player's stats says it is for every game mode`() = uiTest(TestScreen(400, 3000)) {
        val dao = FlakyScoreDao().apply { failing = false }
        val viewModel = StatisticsViewModel(ScoreRepository(dao), SettingsRepository(InMemoryPreferences()))
        showcase.show { DiceFiveTheme { StatisticsScreen(viewModel = viewModel, onBack = {}) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Delete Sam's statistics").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Would you like to delete Sam's statistics in every game mode? Their leaderboard scores will not be affected.").assertExists()
    }

    @Test
    fun `a picked page and sort are on view even when the write that keeps them can't be made`() = uiTest {
        val full = object : DataStore<Preferences> by InMemoryPreferences() {
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = data.first()
        }
        val settings = SettingsRepository(full)
        val viewModel = StatisticsViewModel(ScoreRepository(InMemoryScoreDao()), settings)
        compose.waitForIdle()
        assertEquals(ModePage.STANDARD, viewModel.page.value)

        val byScore = StatisticsSortOrder(StatisticsSort.HIGHEST_SCORE)
        viewModel.selectPage(ModePage.Combined)
        viewModel.setSortOrder(ModePage.Combined, byScore)
        compose.waitForIdle()

        assertEquals(ModePage.Combined, viewModel.page.value)
        assertEquals(byScore, viewModel.sortOrders.value!!.getValue(ModePage.Combined))
        assertEquals(StatisticsSortOrder(), viewModel.sortOrders.value!!.getValue(ModePage.STANDARD))
        assertEquals(null, settings.statisticsChoice.first())
    }

    @Test
    fun `each page keeps its own sort`() = uiTest {
        val settings = SettingsRepository(InMemoryPreferences())
        val viewModel = StatisticsViewModel(ScoreRepository(InMemoryScoreDao()), settings)
        val byScore = StatisticsSortOrder(StatisticsSort.HIGHEST_SCORE)
        viewModel.setSortOrder(ModePage.Mode(GameMode.STUD), byScore)
        compose.waitForIdle()
        val sorts = MutableStateFlow(viewModel.sortOrders.first { it?.get(ModePage.Mode(GameMode.STUD)) == byScore })
        assertEquals(StatisticsSortOrder(), sorts.value!!.getValue(ModePage.STANDARD))
        assertEquals(StatisticsSortOrder(), sorts.value!!.getValue(ModePage.Combined))
        assertEquals(mapOf("stud" to byScore.stored), settings.statisticsSorts.first())
    }
}
