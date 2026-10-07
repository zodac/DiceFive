package net.zodac.dicefive

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.common.ChoicePicker
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.menu.MenuScreen
import net.zodac.dicefive.ui.settings.AboutDialog
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The words of the shared chrome - the title bar's back arrow, a picker field, the main menu and the About dialog - as a player and TalkBack get them. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChromeTextTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the title bar's back arrow is called Back`() {
        compose.setContent { DiceFiveTheme { ScreenScaffold(title = "Page", onBack = {}) {} } }

        compose.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun `a picker field says its choice after its title - and its tap is called Choose`() {
        compose.setContent {
            DiceFiveTheme {
                ChoicePicker(title = "Thing", options = listOf("A", "B"), selected = "A", onSelect = {}, label = { "Pick $it" }, description = { "About $it" })
            }
        }

        val field = compose.onNodeWithContentDescription("Thing").fetchSemanticsNode().config
        assertEquals("Pick A. About A", field[SemanticsProperties.StateDescription])
        assertEquals("Choose Thing", field[SemanticsActions.OnClick].label)
    }

    private fun showMenu(hasInProgressGame: Boolean) {
        compose.setContent {
            DiceFiveTheme {
                MenuScreen(
                    hasInProgressGame = hasInProgressGame,
                    onContinue = {}, onNewGame = {}, onScores = {}, onStatistics = {}, onAchievements = {}, onStyles = {}, onRules = {}, onSettings = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the main menu names every destination - and splits Play when a game is saved`() {
        showMenu(hasInProgressGame = true)

        listOf("Achievements", "Styles", "Leaderboard", "Statistics", "Rules", "Settings", "New Game", "Continue").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onAllNodesWithText("Play").assertCountEquals(0)
    }

    @Test
    fun `the main menu has a single Play button with no saved game`() {
        showMenu(hasInProgressGame = false)

        compose.onNodeWithText("Play").assertExists()
        compose.onAllNodesWithText("Continue").assertCountEquals(0)
    }

    @Test
    fun `the About dialog says who made the app - and its close button is named`() {
        compose.setContent { DiceFiveTheme { AboutDialog(onDismissRequest = {}) } }

        compose.onNodeWithContentDescription("Close about").assertExists()
        compose.onAllNodesWithText("About").assertCountEquals(1)
        listOf("Author", "Inspiration", "Privacy", "Source code on GitHub", "Dice Me Online on Google Play", "Full privacy policy").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("DiceFive is created by zodac.").assertExists()
        compose.onNodeWithText("DiceFive was inspired by Dice Me Online, created by Arturo Gutierrez.").assertExists()
        compose.onNodeWithText("DiceFive holds no user data. Your games, scores and settings stay on this device, and there are no ads, analytics or tracking.")
            .assertExists()
    }
}
