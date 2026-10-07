package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
 * The Rules page row scrolls, and nothing on screen says how far - so it's the semantics that tell a
 * TalkBack user there are more tabs than the ones in view, as "Tab, 1 of 6" rather than just "Tab".
 * The group row above it says the same of its own three.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RulesScreenAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    /** A page's title - which now reads the same as its tab. */
    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun showRules() {
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { RulesScreen(onBack = {}) }
            }
        }
    }

    /** Each tab row (the tab's nearest collection - not the pager below them, which is one of its own) and its tabs, in order. */
    private fun tabRows(): Map<SemanticsNode, List<SemanticsNode>> {
        val rowOf = compose.onAllNodes(isTab).fetchSemanticsNodes().associateWith { tab ->
            generateSequence(tab.parent) { it.parent }.first { SemanticsProperties.CollectionInfo in it.config }
        }
        // By id: a node fetched twice is two objects.
        val rows = rowOf.values.associateBy { it.id }
        return rowOf.keys.groupBy { rowOf.getValue(it).id }.mapKeys { (id, _) -> rows.getValue(id) }
    }

    private fun openGroup(name: String) {
        compose.onNodeWithText(name).performClick()
        compose.waitForIdle()
    }

    @Test
    fun eachTabRowAnnouncesHowManyTabsItHas() {
        showRules()
        openGroup("Modifiers")

        val rows = tabRows()
        assertEquals(2, rows.size)
        rows.forEach { (row, tabs) -> assertEquals(tabs.size, row.config[SemanticsProperties.CollectionInfo].columnCount) }
        assertEquals(listOf(3, 6), rows.values.map { it.size })
    }

    @Test
    fun eachTabAnnouncesItsOwnPositionInItsRow() {
        showRules()
        openGroup("Modes")

        tabRows().values.forEach { tabs ->
            val positions = tabs.map { it.config[SemanticsProperties.CollectionItemInfo].columnIndex }
            assertEquals(positions.indices.toList(), positions)
        }
    }

    @Test
    fun aGroupTabOpensItsFirstPage() {
        showRules()

        openGroup("Modes")

        compose.onNodeWithText("Modes").assertIsSelected()
        compose.onNodeWithText("Overview").assertIsSelected()
        compose.onAllNodesWithText("Game Modes")[0].assertIsDisplayed()
    }

    @Test
    fun swipingPastAGroupsLastPageCarriesOnIntoTheNextGroup() {
        showRules()
        compose.onNodeWithText("Tie Breaks").performScrollTo().performClick()
        compose.waitForIdle()

        // The page itself, not the page row, which holds a "Tie Breaks" too.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyChild(hasText("Tie Breaks")))
            .performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("Modes").assertIsSelected()
        compose.onNodeWithText("Overview").assertIsSelected()
        compose.onNode(hasContentDescription("Modes, page 1 of 6")).assertExists()
    }

    @Test
    fun anOffscreenTabCanBeReachedAndOpensItsPage() {
        showRules()
        openGroup("Modes")

        compose.onNode(isTab and hasText("Quickfire")).performScrollTo().performClick()
        compose.waitForIdle()

        // The tab, not the page's title, which reads the same.
        compose.onNode(isTab and hasText("Quickfire")).assertIsSelected()
        compose.onNode(isHeading and hasText("Quickfire")).assertIsDisplayed()
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

        openGroup("Modes")
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
    fun theExampleTurnTimerIsDescribedNotAnnouncedAsALiveCountdown() {
        showRules()

        openGroup("Modifiers")
        compose.onNodeWithText("Turn Timer").performScrollTo().performClick()
        compose.waitForIdle()

        val timer = compose.onNodeWithContentDescription("Example: the turn timer, turning red with 4 seconds left").fetchSemanticsNode()
        // The game's badge is a live region in its last seconds; here no turn is running, so nothing is announced.
        assertTrue(SemanticsProperties.LiveRegion !in timer.config)
        assertTrue(compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).fetchSemanticsNodes()
            .none { it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.any { d -> "running out" in d } })
    }

    @Test
    fun theEdgeChevronsAreNotExtraTalkBackStops() {
        showRules()

        // Every clickable node is a tab or the app bar's back arrow - the chevrons only repeat what
        // the tabs (and their "1 of 6") already give TalkBack, so they're kept out of it.
        val tabs = compose.onAllNodes(isTab).fetchSemanticsNodes().size
        val clickables = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes().size

        assertEquals(tabs + 1, clickables)
    }

    @Test
    fun tappingTheEndChevronScrollsTheTabs() {
        showRules()
        openGroup("Modifiers")
        compose.onNodeWithText("Unlucky Dice").assertIsNotDisplayed()

        // The chevron has no semantics node of its own, so it's tapped where it sits: over the page row's end.
        compose.onNode(hasAnyChild(isTab and hasText("Overview"))).performTouchInput { click(centerRight - Offset(24.dp.toPx(), 0f)) }
        compose.waitForIdle()

        compose.onNodeWithText("Overview").assertIsNotDisplayed()
        // Still on the group's first page: the chevron took the tap and scrolled the tabs, rather than the
        // tap falling through to a tab under it (which would also have scrolled the row, by selecting it).
        compose.onNodeWithText("Overview").assertIsSelected()
    }

    @Test
    fun theFooterSaysWhichPageAndIsAnnouncedWhenItChanges() {
        showRules()
        compose.onNodeWithText("1 of 5").assertIsDisplayed()

        compose.onNodeWithText("Tie Breaks").performScrollTo().performClick()
        compose.waitForIdle()

        val footer = compose.onNodeWithContentDescription("Gameplay, page 5 of 5").fetchSemanticsNode()
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
        val footerTop = compose.onNode(hasContentDescription(", page ", substring = true)).fetchSemanticsNode().boundsInRoot.top
        assertTrue("last line ends at $lastLineBottom, footer starts at $footerTop", lastLineBottom <= footerTop)
    }

    @Test
    fun hitListExamplesSayWhatTheTargetsTileShows() {
        showRules()
        openGroup("Modes")
        compose.onNodeWithText("Hit List").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(3000)

        compose.onNode(hasContentDescription("Example target: 4, 1, 3, 2, any. Worth 20 points.")).assertExists()
        compose.onNode(hasContentDescription("Its tile shows hit, 0 of 4 in place.", substring = true)).assertExists()
        compose.onNode(hasContentDescription("Example roll: 4, 1, 5, 6, 2. Held: 4, 1 and 2. Its tile shows partial hit, 3 of 4 rolled, 2 in place.")).assertExists()
        compose.onNode(hasContentDescription("Example: 4, 1, 6, 2, 5. The 6 and 5 don't count. Scores 10 points. Its tile shows partial hit, 3 of 4 rolled, 3 in place.")).assertExists()
        compose.onAllNodes(hasContentDescription("Its tile shows exact hit.", substring = true)).fetchSemanticsNodes().let { assertEquals(2, it.size) }
        compose.onNode(hasContentDescription("The Alibi lights up.", substring = true)).assertExists()
    }

    /**
     * A tab row that scrolls itself on every selection (as Material's re-centring did) takes a tap while it's scrolling as
     * "stop", not as a tap on the tab - so a second tap soon after the first went nowhere. Real touches, 50ms apart.
     */
    private fun tapTwice(first: SemanticsMatcher, second: SemanticsMatcher) {
        compose.mainClock.autoAdvance = false
        showRules()
        compose.mainClock.advanceTimeBy(500)
        compose.onNode(first).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(50)
        compose.onNode(second).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(2_000)
    }

    @Test
    fun aSecondGroupTapSoonAfterTheFirstStillLands() {
        tapTwice(isTab and hasText("Modes"), isTab and hasText("Modifiers"))

        compose.onNode(isTab and hasText("Modifiers")).assertIsSelected()
    }

    @Test
    fun aSecondPageTapSoonAfterTheFirstStillLands() {
        tapTwice(isTab and hasText("Lower Section"), isTab and hasText("Upper Section"))

        compose.onNode(isTab and hasText("Upper Section")).assertIsSelected()
    }

    @Test
    fun aTabSnapsToItsPageWithoutPassingThroughTheOnesBetween() {
        compose.mainClock.autoAdvance = false
        showRules()
        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithText("Modes").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(50)
        // Off the row's end, so tapped through TalkBack's action; with the clock held, nothing may wait for idle.
        compose.onNodeWithText("Hit List").performSemanticsAction(SemanticsActions.OnClick)
        // A frame or two: no slide through Quickfire, 7 Dice Stud and Third Wind to get there.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        compose.onNode(isHeading and hasText("Hit List")).assertIsDisplayed()
        listOf("Quickfire", "7 Dice Stud", "Third Wind").forEach { compose.onAllNodes(isHeading and hasText(it)).assertCountEquals(0) }
    }
}
