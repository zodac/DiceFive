package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.resetAchievementsScrollMemory
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.robolectric.annotation.Config

private const val THUMB_MARGIN_PX = 50

/** The achievements page's previous/next category arrows glide to the header rather than snapping. */
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AchievementCategoryScrollTest : UiTest() {

    @Test
    fun `the next category arrow is still scrolling after a quarter of a second and has settled by a second`() = uiTest {
        resetAchievementsScrollMemory()
        val viewModel = achievementsViewModel()
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalAppContainer provides inMemoryAppContainer(), LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        mainClock.advanceTimeBy(3000)
        // The scrollbar thumb's top edge: the first gold pixel in the page's right-hand margin, where nothing else is gold-coloured.
        fun thumbTop(): Int {
            val bitmap = onRoot().captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
            return (0 until bitmap.height).first { y -> (bitmap.width - THUMB_MARGIN_PX until bitmap.width).any { x -> pixels[y * bitmap.width + x].let { c -> (c shr 16) and 255 > 180 && (c shr 8) and 255 > 130 && c and 255 < 90 } } }
        }
        onAllNodes(hasContentDescription("Next category")).fetchSemanticsNodes().let { check(it.isNotEmpty()) }
        onAllNodes(hasContentDescription("Next category"))[0].performClick()
        val start = thumbTop()
        mainClock.advanceTimeBy(250)
        val quarterSecond = thumbTop()
        mainClock.advanceTimeBy(750)
        val oneSecond = thumbTop()
        mainClock.advanceTimeBy(1000)
        val settled = thumbTop()
        assertTrue(quarterSecond in (start + 1) until settled, "the thumb was at $start, then $quarterSecond after 250ms, and ends at $settled")
        assertEquals(settled, oneSecond, "the scroll was still moving after a second")
    }
}
