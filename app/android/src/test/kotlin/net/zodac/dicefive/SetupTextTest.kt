package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.setup.GameSetupScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The New Game form's words - and that an empty name field shows its seat's default as a hint, not as text that was typed. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SetupTextTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(viewModel: GameViewModel = GameViewModel()) {
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { GameSetupScreen(viewModel = viewModel, onStartGame = {}, onBack = {}) }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the form is titled and its sections are named`() {
        show()

        listOf("New Game", "Players", "Game Mode", "Modifiers", "Start Game").forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }
    }

    @Test
    fun `an empty name field shows its seat's default as a hint - and a name typed over it replaces it`() {
        val viewModel = GameViewModel()
        show(viewModel)

        compose.onNodeWithText("Player 1").assertExists()
        viewModel.setPlayerName(1, "Wolfgang")
        compose.waitForIdle()

        compose.onNodeWithText("Wolfgang").assertExists()
        compose.onAllNodesWithText("Player 1").assertCountEquals(0)
        compose.onNodeWithContentDescription("Player 1 name").assertExists()
    }

    @Test
    fun `a seat's default clashes with another seat that typed the same words`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(3)
        viewModel.setPlayerType(2, PlayerType.HUMAN)
        viewModel.setPlayerType(3, PlayerType.HUMAN)
        viewModel.setPlayerName(2, "player 3")
        show(viewModel)

        compose.onNodeWithText("Names must be unique").assertExists()
    }

    @Test
    fun `the seat chips and a computer player's difficulties are named`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerType(2, PlayerType.AI)
        show(viewModel)

        compose.onNodeWithText("CPU").assertExists()
        listOf("Easy", "Medium", "Hard").forEach { compose.onNodeWithText(it).assertExists() }
    }
}
