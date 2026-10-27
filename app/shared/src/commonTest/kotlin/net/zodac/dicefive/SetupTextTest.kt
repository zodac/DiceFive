package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.setup.GameSetupScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(411, 891)

/**
 * The New Game form's words - and that an empty name field shows its seat's default as a hint, not as text that was typed.
 * On a wide phone: at 360dp a computer player's difficulties are their initials (see DifficultySelector).
 */
class SetupTextTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun show(viewModel: GameViewModel = gameViewModel()) = showcase.show {
        CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
            DiceFiveTheme { GameSetupScreen(viewModel = viewModel, onStartGame = {}, onTutorial = {}, onBack = {}) }
        }
    }

    @Test
    fun `the form's words - its title and sections - a seat's default as a hint - a clash with it - and the seat chips and difficulties`() = uiTest(SCREEN) {
        val viewModel = gameViewModel()
        show(viewModel)
        listOf("New Game", "Players", "Game Mode", "Modifiers", "Start Game").forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }

        // An empty name field shows its seat's default as a hint - and a name typed over it replaces it.
        compose.onNodeWithText("Player 1").assertExists()
        viewModel.setupForm.setPlayerName(1, "Wolfgang")
        compose.waitForIdle()
        compose.onNodeWithText("Wolfgang").assertExists()
        compose.onAllNodesWithText("Player 1").assertCountEquals(0)
        compose.onNodeWithContentDescription("Player 1 name").assertExists()

        // A seat's default clashes with another seat that typed the same words.
        val clash = gameViewModel()
        clash.setupForm.setPlayerCount(3)
        clash.setupForm.setPlayerType(2, PlayerType.HUMAN)
        clash.setupForm.setPlayerType(3, PlayerType.HUMAN)
        clash.setupForm.setPlayerName(2, "player 3")
        show(clash)
        compose.onNodeWithText("Names must be unique").assertExists()

        // The seat chips and a computer player's difficulties are named.
        val cpu = gameViewModel()
        cpu.setupForm.setPlayerType(2, PlayerType.AI)
        show(cpu)
        compose.onNodeWithText("CPU").assertExists()
        listOf("Easy", "Medium", "Hard").forEach { compose.onNodeWithText(it).assertExists() }
    }
}
