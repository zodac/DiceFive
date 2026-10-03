package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.rules.RulesScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Rules tab row scrolls, and nothing on screen says how far - so it's the semantics that tell a
 * TalkBack user there are more tabs than the ones in view, as "Tab, 1 of 7" rather than just "Tab".
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RulesScreenAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun showRules() {
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { RulesScreen(onBack = {}) }
            }
        }
    }

    @Test
    fun tabRowAnnouncesHowManyTabsItHas() {
        showRules()

        val tabs = compose.onAllNodes(isTab).fetchSemanticsNodes()
        // The tab row, not the pager below it - which is a collection of its own.
        val row = generateSequence(tabs.first().parent) { it.parent }
            .first { SemanticsProperties.CollectionInfo in it.config }

        assertEquals(tabs.size, row.config[SemanticsProperties.CollectionInfo].columnCount)
    }

    @Test
    fun eachTabAnnouncesItsOwnPosition() {
        showRules()

        val positions = compose.onAllNodes(isTab).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.CollectionItemInfo].columnIndex }

        assertEquals(positions.indices.toList(), positions)
    }

    @Test
    fun anOffscreenTabCanBeReachedAndOpensItsPage() {
        showRules()

        compose.onNodeWithText("Quickfire").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Quickfire").assertIsSelected()
        compose.onAllNodesWithText("Mode: Quickfire")[0].assertIsDisplayed()
    }

    @Test
    fun anExampleDiceRowIsSpokenWithWhichDiceCountAndWhatItScores() {
        showRules()

        compose.onNodeWithText("Lower Section").performScrollTo().performClick()
        compose.waitForIdle()

        // One stop for the category - its name, what it takes and the example - not five bare dice.
        compose.onNodeWithText("Total of all five dice, if at least three dice are the same", substring = true)
            .assertContentDescriptionEquals(
                "Total of all five dice, if at least three dice are the same",
                "Example: 5, 5, 5, 2, 6. The 2 and 6 don't count. Scores 23 points.",
            )
    }

    @Test
    fun aColouredExampleDieIsSpokenWithItsColour() {
        showRules()

        compose.onNodeWithText("Tricolour").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Three of one colour and two of another", substring = true)
            .assertContentDescriptionEquals(
                "Three of one colour and two of another",
                "Example: red 1, red 4, red 6, blue 2, blue 5. Scores 25 points.",
            )
    }

    @Test
    fun pointsAreSpokenInFullNotAsPts() {
        showRules()

        compose.onNodeWithText("Upper Section").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Score 63pts or more", substring = true)
            .assertContentDescriptionEquals(
                "Score 63 points or more across the whole section and you earn a bonus 35 points! That's an average of three of each number.",
            )
    }

    @Test
    fun theEdgeChevronsAreNotExtraTalkBackStops() {
        showRules()

        // Every clickable node is a tab or the app bar's back arrow - the chevrons only repeat what
        // the tabs (and their "1 of 7") already give TalkBack, so they're kept out of it.
        val tabs = compose.onAllNodes(isTab).fetchSemanticsNodes().size
        val clickables = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes().size

        assertEquals(tabs + 1, clickables)
    }

    @Test
    fun tappingTheEndChevronScrollsTheTabs() {
        showRules()
        compose.onNodeWithText("Quickfire").assertIsNotDisplayed()

        // The chevron has no semantics node of its own, so it's tapped where it sits: over the row's end.
        compose.onNode(hasAnyChild(isTab)).performTouchInput { click(centerRight - Offset(24.dp.toPx(), 0f)) }
        compose.waitForIdle()

        compose.onNodeWithText("How to Play").assertIsNotDisplayed()
        // Still on the first page: the chevron took the tap and scrolled the tabs, rather than the tap
        // falling through to a tab under it (which would also have scrolled the row, by selecting it).
        compose.onNodeWithText("How to Play").assertIsSelected()
    }

    @Test
    fun theFooterSaysWhichPageAndIsAnnouncedWhenItChanges() {
        showRules()
        val tabs = compose.onAllNodes(isTab).fetchSemanticsNodes().size
        compose.onNodeWithText("1 of $tabs").assertIsDisplayed()

        compose.onNodeWithText("Tie Breaks").performScrollTo().performClick()
        compose.waitForIdle()

        val footer = compose.onNodeWithContentDescription("Page 5 of $tabs").fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, footer.config[SemanticsProperties.LiveRegion])
    }

    @Test
    fun aLongPageScrollsItsLastLineClearOfTheFooter() {
        showRules()
        compose.onNodeWithText("5x & Joker").performScrollTo().performClick()
        compose.waitForIdle()

        // The joker rule's last paragraph ends its page - scrolled right to the end, as a player
        // would, it must finish above the footer that floats over the pages, not stay behind it.
        // (Not performScrollTo, which stops as soon as the line is inside the scroll area - and that
        // area deliberately runs on under the footer.)
        val page = compose.onNode(hasScrollAction() and hasAnyChild(hasText("5x and the Joker Rule")))
        repeat(5) { page.performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val lastLine = compose.onNodeWithText("you still get the", substring = true)

        val lastLineBottom = lastLine.fetchSemanticsNode().boundsInRoot.bottom
        val footerTop = compose.onNode(hasContentDescription("Page ", substring = true)).fetchSemanticsNode().boundsInRoot.top
        assertTrue("last line ends at $lastLineBottom, footer starts at $footerTop", lastLineBottom <= footerTop)
    }
}
