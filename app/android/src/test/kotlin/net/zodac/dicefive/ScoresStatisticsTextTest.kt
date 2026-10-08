package net.zodac.dicefive

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.statistics.StatisticsScreen
import net.zodac.dicefive.ui.statistics.StatisticsViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Leaderboard's and Statistics' words: titles, empty states, column headings, paging, and the delete question. */
@RunWith(AndroidJUnit4::class)
class ScoresStatisticsTextTest {

    @get:Rule
    val compose = createComposeRule()

    private val showcase = Showcase(compose)

    private fun showScores(rows: List<net.zodac.dicefive.data.scores.ScoreEntry>) {
        val viewModel = ScoresViewModel(ScoreRepository(RowsDao(rows)))
        showcase.show { DiceFiveTheme { ScoresScreen(viewModel = viewModel, onBack = {}) } }
    }

    @Test
    fun `the Leaderboard and Statistics are titled and say when they're empty - and the table has its column headings`() {
        showScores(emptyList())
        compose.onNodeWithText("Leaderboard").assertExists()
        compose.onNodeWithText("No scores yet - play a game!").assertExists()
        compose.onNodeWithText("Combined").assertExists()
        compose.onNodeWithText("Game Mode").assertExists()

        showScores(listOf(rowOf(1, 300, GameMode.STANDARD)))
        listOf("#", "Player", "5x", "Score").forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }

        val viewModel = StatisticsViewModel()
        showcase.show { DiceFiveTheme { StatisticsScreen(viewModel = viewModel, onBack = {}) } }
        compose.onNodeWithText("Statistics").assertExists()
        compose.onNodeWithText("No stats yet - play a game!").assertExists()
    }
}
