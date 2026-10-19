package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.setup.GameSetupScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Until a first game is finished the New Game form's Game Mode and Modifiers pickers are replaced by a note, and the tutorial is offered. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class SetupLockTest {

    @get:Rule
    val compose = createComposeRule()

    private val showcase = Showcase(compose)

    private var tutorialOpened = 0

    private fun show(viewModel: GameViewModel) = showcase.show {
        CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
            DiceFiveTheme { GameSetupScreen(viewModel = viewModel, onStartGame = {}, onTutorial = { tutorialOpened++ }, onBack = {}) }
        }
    }

    @Test
    fun `locked - each card is its title over the note - read as one - with no picker to open - and the tutorial is offered`() {
        show(gameViewModel(modesUnlocked = false))

        val notes = mapOf("Game Mode" to "Finish a game to unlock game modes", "Modifiers" to "Finish a game to unlock modifiers")
        for ((title, note) in notes) {
            val config = compose.onNode(hasText(title) and hasText(note)).fetchSemanticsNode().config
            assertEquals(listOf(title, note), config[SemanticsProperties.Text].map { it.text })
            assertFalse(title, SemanticsActions.OnClick in config)
        }
        // Neither picker is on the form.
        compose.onAllNodesWithText("Standard").assertCountEquals(0)
        compose.onAllNodesWithText("None").assertCountEquals(0)

        compose.onNodeWithText("New to the game? Play the tutorial").performClick()
        assertEquals(1, tutorialOpened)
    }

    @Test
    fun `unlocked - the pickers are back and open as before - and the tutorial isn't offered`() {
        show(gameViewModel())

        compose.onAllNodesWithText("Finish a game", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("New to the game? Play the tutorial").assertCountEquals(0)

        compose.onNodeWithContentDescription("Game Mode").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Cancel").assertExists()
    }
}
