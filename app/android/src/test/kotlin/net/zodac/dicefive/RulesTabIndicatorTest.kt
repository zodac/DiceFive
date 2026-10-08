package net.zodac.dicefive

import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.rules.RulesScreen
import net.zodac.dicefive.ui.rules.pagerIndicatorLayout
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Rules page row's sliding indicator sits under the selected tab in either layout direction, and a swipe to a page
 * whose tab is off the row's end brings that tab into view.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp")
class RulesTabIndicatorTest {

    @get:Rule
    val compose = createComposeRule()

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private val showcase = Showcase(compose)

    private fun showRules() = showcase.show {
        CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) { DiceFiveTheme { RulesScreen(onBack = {}) } }
    }

    private fun checkIndicatorUnderEachTab() {
        showRules()
        // The page row's tabs, after the top row's three groups.
        val groups = 3
        for (index in 0..2) {
            compose.tapTab(compose.onAllNodes(isTab)[groups + index])
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitForIdle()
            val tab = compose.onAllNodes(isTab)[groups + index].fetchSemanticsNode().boundsInRoot
            val indicator = compose.onNodeWithTag("rulesTabIndicator").fetchSemanticsNode().boundsInRoot
            assertEquals("indicator $indicator vs tab $index $tab", tab.center.x, indicator.center.x, 2f)
        }
    }

    private fun checkSwipedToTabComesIntoView(rtl: Boolean) {
        showRules()
        // The page row's last tab in the first group, off its end at 360dp, then swiped to page by page.
        val pages = compose.onAllNodes(isTab).fetchSemanticsNodes().size - 3
        val lastTab = compose.onAllNodes(isTab)[3 + pages - 1]
        val row = compose.onNode(hasAnyChild(isTab and hasText(compose.onAllNodes(isTab)[3].fetchSemanticsNode().config[SemanticsProperties.Text].joinToString())))
        repeat(pages - 1) {
            // The page showing - its neighbours may be composed too, off screen.
            val shownPages = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyChild(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)))
            val showing = shownPages.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.left >= 0f && it.boundsInRoot.right <= compose.onRoot().fetchSemanticsNode().size.width }
            shownPages[showing].performTouchInput { if (rtl) swipeRight() else swipeLeft() }
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitForIdle()
        }
        lastTab.assertIsSelected()
        val tab = lastTab.fetchSemanticsNode().boundsInRoot
        val bounds = row.fetchSemanticsNode().boundsInRoot
        assertTrue("tab $tab not wholly inside the row $bounds", tab.left >= bounds.left && tab.right <= bounds.right)
    }

    /** A tap snaps the pages but slides the indicator through the tabs in between, ending under the tapped one. */
    private fun checkTapSlidesTheIndicator() {
        showRules()
        val groups = 3
        val start = compose.onNodeWithTag("rulesTabIndicator").fetchSemanticsNode().boundsInRoot.center.x
        compose.mainClock.autoAdvance = false
        compose.tapTab(compose.onAllNodes(isTab)[groups + 2])
        // The page is already there - only the indicator is still on its way.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodes(isTab)[groups + 2].assertIsSelected()
        compose.mainClock.advanceTimeBy(60)
        val partway = compose.onNodeWithTag("rulesTabIndicator").fetchSemanticsNode().boundsInRoot.center.x
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val end = compose.onNodeWithTag("rulesTabIndicator").fetchSemanticsNode().boundsInRoot.center.x
        val tab = compose.onAllNodes(isTab)[groups + 2].fetchSemanticsNode().boundsInRoot.center.x
        assertEquals("ends under the tapped tab", tab, end, 2f)
        assertTrue("part way ($partway) between where it was ($start) and where it ends ($end)", partway > start + 2f && partway < end - 2f)
    }

    @Test
    fun `the indicator is under the selected tab - a tap slides it there - and a swipe brings an off-screen tab into view left to right`() {
        checkIndicatorUnderEachTab()
        checkSwipedToTabComesIntoView(rtl = false)
        checkTapSlidesTheIndicator()
    }

    @Test
    @Config(qualifiers = "ar-w360dp-h800dp")
    fun `the indicator is under the selected tab - and a swipe brings an off-screen tab into view right to left`() {
        checkIndicatorUnderEachTab()
        checkSwipedToTabComesIntoView(rtl = true)
    }

    /**
     * The indicator is under its tab when the tabs differ in width, as they do on a device (the labels' own widths;
     * the test fonts make every label alike, so the Rules screen's own tabs can't show it). Material places the
     * indicator itself, relative to the selected tab - which the Rules rows keep at tab 0 - so a layout that ignores that
     * is out by half the difference between tab 0's width and the shown tab's.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    private fun checkIndicatorUnderUnevenTabs(position: Float) {
        val widths = listOf(90.dp, 200.dp, 130.dp, 160.dp)
        showcase.show {
            val pagePosition = remember { mutableFloatStateOf(position) }
            SecondaryScrollableTabRow(
                selectedTabIndex = 0,
                edgePadding = 0.dp,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
                            pagerIndicatorLayout(measurable, constraints, tabPositions, pagePosition.floatValue)
                        }.testTag("rulesTabIndicator"),
                    )
                },
            ) {
                widths.forEachIndexed { index, width ->
                    Tab(selected = index == 0, onClick = {}, text = { Text("Tab $index") }, modifier = Modifier.width(width))
                }
            }
        }
        val tabs = compose.onAllNodes(isTab).fetchSemanticsNodes().map { it.boundsInRoot }
        val indicator = compose.onNodeWithTag("rulesTabIndicator").fetchSemanticsNode().boundsInRoot
        val whole = position.toInt()
        val expected = tabs[whole].center.x + (tabs[(whole + 1).coerceAtMost(tabs.size - 1)].center.x - tabs[whole].center.x) * (position - whole)
        assertEquals("indicator $indicator vs tabs $tabs at $position", expected, indicator.center.x, 2f)
    }

    @Test
    @Config(qualifiers = "w600dp-h800dp")
    fun `the indicator is under a tab of a different width than the first - and between two part way through a swipe`() {
        checkIndicatorUnderUnevenTabs(1f)
        checkIndicatorUnderUnevenTabs(2f)
        checkIndicatorUnderUnevenTabs(2.5f)
    }

    @Test
    @Config(qualifiers = "ar-w600dp-h800dp")
    fun `the indicator is under a tab of a different width than the first right to left`() = checkIndicatorUnderUnevenTabs(3f)
}
