package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.rules.RulesScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Rules screen's words: its title, its groups and every page's tab, what a page and its examples say and draw, and its page count. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RulesTextTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showRules() {
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { RulesScreen(onBack = {}) }
            }
        }
    }

    @Test
    fun `the screen is titled Rules - and the first page is how to play`() {
        showRules()

        compose.onNodeWithText("Rules").assertExists()
        compose.onNodeWithText("How to Play DiceFive").assertExists()
        compose.onNodeWithText("Score as many points as possible by rolling five dice, with three rolls per round.").assertExists()
    }

    @Test
    fun `every page has a tab in its group`() {
        showRules()

        listOf("Gameplay", "Modes", "Modifiers").forEach { compose.onNodeWithText(it).assertExists() }
        mapOf(
            "Gameplay" to listOf("How to Play", "Upper Section", "Lower Section", "5x & Joker", "Tie Breaks"),
            "Modes" to listOf("Overview", "Tricolour", "Quickfire", "7 Dice Stud", "Third Wind", "Hit List"),
            "Modifiers" to listOf("Overview", "Turn Timer", "Number of Rolls", "Stored Rolls", "Extended Scores", "Unlucky Dice"),
        ).forEach { (group, pages) ->
            compose.onNodeWithText(group).performClick()
            compose.waitForIdle()
            pages.forEach { compose.onNodeWithText(it).assertExists() }
        }
    }

    @Test
    fun `the modes and the modifiers each open on an overview`() {
        showRules()

        compose.onNodeWithText("Modes").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Game Modes").assertExists()

        compose.onNodeWithText("Modifiers").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Modifiers are just for fun", substring = true).assertExists()
    }

    @Test
    fun `the lower section's boxes are headed by their names and marks - and each example says what it scores`() {
        showRules()
        compose.onNodeWithText("Lower Section").performScrollTo().performClick()
        compose.waitForIdle()

        listOf("3x", "4x", "Full House", "Small Straight", "Large Straight", "5x", "Chance").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("= 25pts", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Example: 3, 3, 3, 6, 6. Scores 25 points.")).assertExists()
    }

    @Test
    fun `a list's steps are numbered - and the joker's two amounts are added`() {
        showRules()
        compose.onNodeWithText("5x & Joker").performScrollTo().performClick()
        compose.waitForIdle()

        listOf("1.", "2.", "3.").forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }
        compose.onNodeWithText("= 20pts + 100pts", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the footer counts the pages of the group showing`() {
        showRules()

        compose.onNodeWithText("1 of 5").assertExists()
        compose.onNode(hasContentDescription("Gameplay, page 1 of 5")).assertExists()

        compose.onNodeWithText("Modifiers").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("1 of 6").assertExists()
        compose.onNode(hasContentDescription("Modifiers, page 1 of 6")).assertExists()
    }
}
