package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.setup.GameSetupScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(411, 891)

/** Until a first game is finished the New Game form's Game Mode and Modifiers pickers are replaced by a note, and the tutorial is offered. */
class SetupLockTest : UiTest() {

    private val showcase = Showcase { compose }

    private var tutorialOpened = 0

    private fun show(viewModel: GameViewModel) = showcase.show {
        CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
            DiceFiveTheme { GameSetupScreen(viewModel = viewModel, onStartGame = {}, onTutorial = { tutorialOpened++ }, onBack = {}) }
        }
    }

    @Test
    fun `locked - each card is its title over the note - read as one - with no picker to open - and the tutorial is offered`() = uiTest(SCREEN) {
        show(gameViewModel(modesUnlocked = false))

        val notes = mapOf("Game Mode" to "Finish a game to unlock game modes", "Modifiers" to "Finish a game to unlock modifiers")
        for ((title, note) in notes) {
            val config = compose.onNode(hasText(title) and hasText(note)).fetchSemanticsNode().config
            assertEquals(listOf(title, note), config[SemanticsProperties.Text].map { it.text })
            assertFalse(SemanticsActions.OnClick in config, title)
        }
        // Neither picker is on the form.
        compose.onAllNodesWithText("Standard").assertCountEquals(0)
        compose.onAllNodesWithText("None").assertCountEquals(0)

        compose.onNodeWithText("New to the game? Play the tutorial").performClick()
        assertEquals(1, tutorialOpened)
    }

    @Test
    fun `unlocked - the pickers are back and open as before - and the tutorial isn't offered`() = uiTest(SCREEN) {
        show(gameViewModel())

        compose.onAllNodesWithText("Finish a game", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("New to the game? Play the tutorial").assertCountEquals(0)

        compose.onNodeWithContentDescription("Game Mode").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Cancel").assertExists()
    }
}
