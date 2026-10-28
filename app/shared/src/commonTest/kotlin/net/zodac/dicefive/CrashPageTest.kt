package net.zodac.dicefive

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.ui.common.CrashPage
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(360, 800)

/** An error nothing caught is said so in place of the app, with a way back into it, and the error to report or, once shown, copy. */
class CrashPageTest : UiTest() {

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val isPoliteLiveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test
    fun `the page names what happened under a heading - what's kept - with the details hidden until asked for`() = uiTest(SCREEN) {
        val details = "DiceFive 1.3.0 (github debug)\nThread: main\njava.lang.IllegalStateException: Boom\n\tat Game.roll(Game.kt:1)"
        compose.setContent { DiceFiveTheme { CrashPage(details = details, onRestart = {}, onCopy = {}, onReport = {}) } }
        compose.onNode(isHeading and hasText("Something went wrong")).assertIsDisplayed()
        listOf(
            "DiceFive ran into a problem it couldn't recover from, so it has stopped here.",
            "Your scores, achievements, and settings are kept, but a game in progress may have lost its last move.",
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        listOf("If it keeps happening, these details will help when you report it:", "Copy details", details)
            .forEach { compose.onNodeWithText(it).assertDoesNotExist() }

        compose.onNode(isButton and hasText("Show details")).performScrollTo().performClick()
        compose.onNodeWithText("If it keeps happening, these details will help when you report it:").performScrollTo().assertIsDisplayed()
        compose.onNode(isButton and hasText("Copy details")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(details).performScrollTo().assertIsDisplayed()

        compose.onNode(isButton and hasText("Hide details")).performScrollTo().performClick()
        compose.onNodeWithText(details).assertDoesNotExist()
    }

    @Test
    fun `opening the details never moves the page - they open below Show details, and scroll in the room left`() = uiTest(SCREEN) {
        val details = (1..60).joinToString("\n") { "\tat net.zodac.dicefive.Game.frame$it(Game.kt:$it)" }
        compose.mainClock.autoAdvance = false
        compose.setContent { DiceFiveTheme { CrashPage(details = details, onRestart = {}, onCopy = {}, onReport = {}) } }
        compose.mainClock.advanceTimeBy(1_000)
        val heading = compose.onNode(isHeading and hasText("Something went wrong"))
        val toggle = compose.onNode(isButton and hasText("Show details"))
        val headingTop = heading.getUnclippedBoundsInRoot().top
        val toggleBottom = toggle.getUnclippedBoundsInRoot().bottom

        toggle.performClick()
        // Every frame of the opening, not just its end: the page never shifts on the way.
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            assertEquals(headingTop, heading.getUnclippedBoundsInRoot().top)
        }
        assertEquals(toggleBottom, compose.onNode(isButton and hasText("Hide details")).getUnclippedBoundsInRoot().bottom)
        val explanation = compose.onNodeWithText("If it keeps happening, these details will help when you report it:")
        assertTrue(explanation.getUnclippedBoundsInRoot().top >= toggleBottom)

        // The panel scrolls, the page doesn't: the trace's end is reached, and the heading is still where it was.
        compose.onNodeWithText(details).performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(headingTop, heading.getUnclippedBoundsInRoot().top)
        assertTrue(explanation.getUnclippedBoundsInRoot().top < toggleBottom, "the panel didn't scroll its explanation away")
    }

    @Test
    fun `Restart and Report on GitHub are buttons side by side that restart and report`() = uiTest(SCREEN) {
        var restarts = 0
        var reports = 0
        compose.setContent { DiceFiveTheme { CrashPage(details = "Boom", onRestart = { restarts++ }, onCopy = {}, onReport = { reports++ }) } }
        val restart = compose.onNode(isButton and hasClickAction() and hasText("Restart")).assertIsDisplayed()
        val report = compose.onNode(isButton and hasClickAction() and hasText("Report on GitHub")).assertIsDisplayed()
        assertEquals(restart.getUnclippedBoundsInRoot().top, report.getUnclippedBoundsInRoot().top)
        restart.performClick()
        report.performClick()
        assertEquals(1, restarts)
        assertEquals(1, reports)
    }

    @Test
    fun `Copy details copies them - then says Copied - as a polite live region TalkBack announces`() = uiTest(SCREEN) {
        var copies = 0
        compose.setContent { DiceFiveTheme { CrashPage(details = "Boom", onRestart = {}, onCopy = { copies++ }, onReport = {}) } }
        compose.onNode(isButton and hasText("Show details")).performScrollTo().performClick()
        compose.onNode(isButton and hasText("Copy details")).performScrollTo().performClick()
        assertEquals(1, copies)
        compose.onNode(isButton and hasText("Copied") and isPoliteLiveRegion).performScrollTo().assertIsDisplayed()
    }
}
