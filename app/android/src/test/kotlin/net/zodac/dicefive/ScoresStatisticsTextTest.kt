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
import org.robolectric.annotation.Config

/** The Leaderboard's and Statistics' words: titles, empty states, column headings, paging, and the delete question. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScoresStatisticsTextTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showScores(rows: List<net.zodac.dicefive.data.scores.ScoreEntry>) {
        val viewModel = ScoresViewModel(ScoreRepository(RowsDao(rows)))
        compose.setContent { DiceFiveTheme { ScoresScreen(viewModel = viewModel, onBack = {}) } }
        compose.waitForIdle()
    }

    @Test
    fun `an empty Leaderboard is titled and says no scores yet`() {
        showScores(emptyList())

        compose.onNodeWithText("Leaderboard").assertExists()
        compose.onNodeWithText("No scores yet - play a game!").assertExists()
        compose.onNodeWithText("Combined").assertExists()
        compose.onNodeWithText("Game Mode").assertExists()
    }

    @Test
    fun `the table has its column headings`() {
        showScores(listOf(rowOf(1, 300, GameMode.STANDARD)))

        listOf("#", "Player", "5x", "Score").forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }
    }

    @Test
    fun `empty Statistics is titled and says no stats yet`() {
        val viewModel = StatisticsViewModel()
        compose.setContent { DiceFiveTheme { StatisticsScreen(viewModel = viewModel, onBack = {}) } }

        compose.onNodeWithText("Statistics").assertExists()
        compose.onNodeWithText("No stats yet - play a game!").assertExists()
    }
}
