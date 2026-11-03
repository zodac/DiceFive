package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameOverScreen
import net.zodac.dicefive.ui.game.PlayerHeaderBar
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** A score of a thousand or more has its thousands grouped wherever a score is shown: the game's tabs, Game Over, and the Leaderboard. */
class ScoreGroupingTest : UiTest() {

    private val showcase = Showcase { compose }

    @Test
    fun `a player's tab shows a score of a thousand or more grouped`() = uiTest {
        val players = listOf(
            PlayerState(name = "Sam", type = PlayerType.HUMAN, colour = PlayerColour.CYAN),
            PlayerState(name = "Ada", type = PlayerType.HUMAN, colour = PlayerColour.GREEN),
        ).mapIndexed { i, p -> p.copy(scorecard = p.scorecard + (ScoreCategory.CHANCE to listOf(1039 - i * 850))) }
        showcase.show {
            DiceFiveTheme { PlayerHeaderBar(players, currentPlayerIndex = 0, viewedPlayerIndex = null, enabled = true, onPlayerTap = {}) }
        }
        compose.onNodeWithText("1,039").assertExists()
        compose.onNodeWithText("189").assertExists()
    }

    @Test
    fun `Game Over shows the winner's and the other players' totals grouped`() = uiTest {
        val configs = (1..2).map { PlayerConfig(slot = it, type = PlayerType.HUMAN, name = "Player$it") }
        val started = GameEngine.newGame(configs, GameMode.STANDARD)
        val totals = listOf(1088, 1001)
        val state = started.copy(
            players = started.players.mapIndexed { i, p -> p.copy(scorecard = p.scorecard + (ScoreCategory.CHANCE to listOf(totals[i]))) },
        )
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { GameOverScreen(state = state, onBackToMenu = {}, onPlayAgain = {}, onReviewScorecards = {}) }
            }
        }
        compose.onNodeWithText("1,088").assertExists()
        compose.onNodeWithText("1,001").assertExists()
    }

    @Test
    fun `the Leaderboard shows a score of a thousand or more grouped`() = uiTest {
        val viewModel = ScoresViewModel(ScoreRepository(InMemoryScoreDao(listOf(rowOf(1, 1039, GameMode.STANDARD), rowOf(2, 189, GameMode.STANDARD)))))
        showcase.show { DiceFiveTheme { ScoresScreen(viewModel = viewModel, onBack = {}) } }
        compose.onNodeWithText("1,039").assertExists()
        // Padded to the width of the longest score there can be ("4,725"), so the column's digits line up.
        compose.onNodeWithText("  189").assertExists()
    }
}
