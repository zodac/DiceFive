package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.resetAchievementsScrollMemory
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** Half the header's height (its 48dp arrows), at density 3. */
private const val HALF_HEADER_PX = 72

/** A scroll's step, small enough to catch a row at every stage of passing under the header. */
private const val SCROLL_STEP_PX = 20f
private const val SCROLL_STEPS = 16

/** How far in from the card's edge to look in a corner: inside the header's curve, one row down from its flat edge. */
private const val CORNER_INSET_PX = 3

/** A row scrolling away shows past the pinned category header's rounded bottom corners, and never its top ones. */
class AchievementsPinnedHeaderTest : UiTest() {

    private fun ComposeUiTest.showPage() {
        resetAchievementsScrollMemory()
        val viewModel = achievementsViewModel()
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalPlatformServices provides SilentPlatformServices,
                LocalReduceMotion provides true,
            ) {
                DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        mainClock.advanceTimeBy(3000)
    }

    private fun ComposeUiTest.scrollOneStep() {
        onNode(hasScrollToIndexAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, SCROLL_STEP_PX) }
        mainClock.advanceTimeBy(100)
    }

    @Test
    fun `a row shows past the pinned header's bottom corners but not its top ones`() = uiTest(TestScreen(360, 800, density = 3f)) {
        showPage()
        scrollOneStep()
        // A locked row's colour and its card's start edge, read from a row still clear of the header.
        val title = onAllNodes(hasText("Let's Finish This")).fetchSemanticsNodes().first().boundsInRoot
        val restingPixels = onRoot().captureToImage().toPixelMap()
        val rowColour = restingPixels[title.left.toInt() - 2, title.center.y.toInt()]
        val cardStart = (0 until title.left.toInt()).first { restingPixels[it, title.center.y.toInt()] == rowColour }
        val corner = cardStart + CORNER_INSET_PX

        var bottomCornersFilled = 0
        repeat(SCROLL_STEPS) {
            scrollOneStep()
            val pixels = onRoot().captureToImage().toPixelMap()
            val headerMiddle = onAllNodes(hasContentDescription("Next category")).fetchSemanticsNodes().minOf { it.boundsInRoot.center.y }.toInt()
            assertNotEquals(rowColour, pixels[corner, headerMiddle - HALF_HEADER_PX + 1], "A row showed past the header's top corner")
            if (pixels[corner, headerMiddle + HALF_HEADER_PX - 1] == rowColour) bottomCornersFilled++
        }
        assertTrue(bottomCornersFilled > 0, "No row ever showed past the header's bottom corner")
    }
}
