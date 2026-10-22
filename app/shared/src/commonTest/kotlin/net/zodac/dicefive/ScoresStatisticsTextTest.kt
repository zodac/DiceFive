package net.zodac.dicefive

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.statistics.StatisticsScreen
import net.zodac.dicefive.ui.statistics.StatisticsViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The Leaderboard's and Statistics' words: titles, empty states, column headings, paging, and the delete question. */
class ScoresStatisticsTextTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun showScores(rows: List<ScoreEntry>) {
        val viewModel = ScoresViewModel(ScoreRepository(InMemoryScoreDao(rows)))
        showcase.show { DiceFiveTheme { ScoresScreen(viewModel = viewModel, onBack = {}) } }
    }

    @Test
    fun `the Leaderboard and Statistics are titled and say when they're empty - and the table has its column headings`() = uiTest {
        showScores(emptyList())
        compose.onNodeWithText("Leaderboard").assertExists()
        compose.onNodeWithText("No scores yet - play a game!").assertExists()
        compose.onNodeWithText("Combined").assertExists()
        compose.onNodeWithText("Game Mode").assertExists()

        showScores(listOf(rowOf(1, 300, GameMode.STANDARD)))
        listOf("#", "Player", "5x", "Score").forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }

        val viewModel = StatisticsViewModel(ScoreRepository(InMemoryScoreDao(emptyList())), SettingsRepository(InMemoryPreferences()))
        showcase.show { DiceFiveTheme { StatisticsScreen(viewModel = viewModel, onBack = {}) } }
        compose.onNodeWithText("Statistics").assertExists()
        compose.onNodeWithText("No stats yet - play a game!").assertExists()
    }
}
