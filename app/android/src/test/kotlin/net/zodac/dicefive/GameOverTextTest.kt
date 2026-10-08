package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameOverScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The results page's words: its title and buttons, the winner's card, and why a tie went the way it did. */
@RunWith(AndroidJUnit4::class)
class GameOverTextTest {

    @get:Rule
    val compose = createComposeRule()

    private val showcase = Showcase(compose)

    private fun show(scores: List<Map<ScoreCategory, Int>>) {
        val configs = scores.indices.map { PlayerConfig(slot = it + 1, type = PlayerType.HUMAN, name = "Player${it + 1}") }
        val started = GameEngine.newGame(configs, GameMode.STANDARD)
        val state = started.copy(
            players = started.players.mapIndexed { index, player ->
                player.copy(scorecard = player.scorecard + scores[index].mapValues { listOf(it.value) })
            },
        )
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { GameOverScreen(state = state, onBackToMenu = {}, onPlayAgain = {}, onReviewScorecards = {}, soundEnabled = false) }
            }
        }
    }

    @Test
    fun `the page is titled Game Over with its three buttons - the winner's card says Winner - and a tie on score says what decided it`() {
        show(listOf(mapOf(ScoreCategory.CHANCE to 25), mapOf(ScoreCategory.CHANCE to 10)))
        listOf("Game Over", "Review Scorecards", "Main Menu", "Play Again").forEach { compose.onNodeWithText(it).assertExists() }
        // On the trophy as well.
        compose.onNodeWithText("Winner").assertExists()
        compose.onNodeWithContentDescription("Winner", useUnmergedTree = true).assertExists()

        // Same total, but the first player zeroed a box the second left empty: more zeroed categories wins the tie.
        show(listOf(mapOf(ScoreCategory.CHANCE to 20, ScoreCategory.ONES to 0), mapOf(ScoreCategory.CHANCE to 20)))
        compose.onNodeWithText("Won on more zeroed categories").assertExists()
    }
}
