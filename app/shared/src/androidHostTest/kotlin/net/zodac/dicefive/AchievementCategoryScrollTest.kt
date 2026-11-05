package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.resetAchievementsScrollMemory
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.robolectric.annotation.Config

private const val THUMB_MARGIN_PX = 50

private const val FRAMES_FOR_A_JUMP_MILLIS = 48L

/** How often an arrow's scroll is looked at, and for how many looks: past its longest, 800ms. */
private const val SAMPLE_MILLIS = 32L
private const val SCROLL_SAMPLES = 36

/** Lower on the screen than the pinned header, at density 3, so only rows that scroll are measured. */
private const val BELOW_PINNED_HEADER_PX = 500f

private const val NOTHING_STAYED_ON_SCREEN = 99_999

/** How much further than the frame before one can move while slowing: a better estimate changes the pace a little. */
private const val SLOWING_SLACK = 1.15f
private const val SLOWING_SLACK_PX = 12

/** The achievements page's previous/next category arrows glide to the header rather than snapping. */
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AchievementCategoryScrollTest : UiTest() {

    /** The page, opened and settled on its clock, with [reduceMotion] as the app's Off or the system would set it. */
    private fun androidx.compose.ui.test.ComposeUiTest.showPage(reduceMotion: Boolean) {
        resetAchievementsScrollMemory()
        val viewModel = achievementsViewModel()
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalPlatformServices provides SilentPlatformServices,
                LocalReduceMotion provides reduceMotion,
            ) {
                DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        mainClock.advanceTimeBy(3000)
        onAllNodes(hasContentDescription("Next category")).fetchSemanticsNodes().let { check(it.isNotEmpty()) }
    }

    /** The scrollbar thumb's top edge: the first gold pixel in the page's right-hand margin, where nothing else is gold-coloured. */
    private fun androidx.compose.ui.test.ComposeUiTest.thumbTop(): Int {
        val bitmap = onRoot().captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        return (0 until bitmap.height).first { y -> (bitmap.width - THUMB_MARGIN_PX until bitmap.width).any { x -> pixels[y * bitmap.width + x].let { c -> (c shr 16) and 255 > 180 && (c shr 8) and 255 > 130 && c and 255 < 90 } } }
    }

    @Test
    fun `under reduced motion the next category arrow jumps to the header rather than gliding`() = uiTest {
        showPage(reduceMotion = true)
        val start = thumbTop()
        onAllNodes(hasContentDescription("Next category"))[0].performClick()
        // Two frames: the tap's, and the jump's.
        mainClock.advanceTimeBy(FRAMES_FOR_A_JUMP_MILLIS)
        val jumped = thumbTop()
        mainClock.advanceTimeBy(2000)
        assertTrue(jumped > start, "the thumb stayed at $start")
        assertEquals(thumbTop(), jumped, "the list was still moving after the tap's frames")
    }

    @Test
    fun `the next category arrow is still scrolling after a quarter of a second and has settled by a second`() = uiTest {
        showPage(reduceMotion = false)
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

    /** Taps [arrow] on the header pinned at the top, and lets its scroll finish. */
    private fun androidx.compose.ui.test.ComposeUiTest.tapAndSettle(arrow: String) {
        tapOnPinnedHeader(arrow)
        mainClock.advanceTimeBy(2000)
    }

    /** Taps [arrow] on the topmost header: the one pinned there. */
    private fun androidx.compose.ui.test.ComposeUiTest.tapOnPinnedHeader(arrow: String) {
        val all = onAllNodes(hasContentDescription(arrow))
        val pinned = all.fetchSemanticsNodes().withIndex().minBy { it.value.boundsInRoot.top }.index
        all[pinned].performClick()
    }

    /** Every text on the page below the pinned header, with how far down it is. */
    private fun androidx.compose.ui.test.ComposeUiTest.rowTops(): Map<String, Float> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top > BELOW_PINNED_HEADER_PX }
            .associate { it.config[SemanticsProperties.Text].joinToString() to it.boundsInRoot.top }

    /** How far the rows moved on each frame of the [arrow] tap's scroll on the pinned header, until it has settled. */
    private fun androidx.compose.ui.test.ComposeUiTest.rowMoves(arrow: String): List<Int> {
        tapOnPinnedHeader(arrow)
        var last = rowTops()
        return List(SCROLL_SAMPLES) {
            mainClock.advanceTimeBy(SAMPLE_MILLIS)
            val now = rowTops()
            val moved = now.keys.intersect(last.keys).map { (now.getValue(it) - last.getValue(it)).toInt() }.sorted()
            last = now
            // No row in both frames: a jump further than the screen.
            moved.getOrElse(moved.size / 2) { NOTHING_STAYED_ON_SCREEN }
        }
    }

    /** That the rows sped up and slowed down one way, with no jump: once past their fastest, no frame moved them much further than the last. */
    private fun assertGlided(moves: List<Int>) {
        val sizes = moves.map { abs(it) }
        assertTrue(moves.map { it.sign }.filter { it != 0 }.distinct().size == 1, "the rows turned back on the way: $moves")
        val slowing = sizes.drop(sizes.indexOf(sizes.max()))
        assertTrue(slowing.zipWithNext().all { (from, to) -> to <= from * SLOWING_SLACK + SLOWING_SLACK_PX }, "the rows jumped as they slowed: $moves")
        assertEquals(0, sizes.last(), "the rows were still moving at the end: $moves")
    }

    @Test
    fun `the previous category arrow glides up to its header, and lands on it, as the next one glides down`() = uiTest {
        showPage(reduceMotion = false)
        val first = rowTops()
        tapAndSettle("Next category")
        val second = rowTops()
        tapAndSettle("Next category")
        val third = rowTops()

        assertGlided(rowMoves("Previous category"))
        assertEquals(second, rowTops(), "it should land on the second category's header")

        // The longest way: every row of the first category crossed, its header pinned from the start.
        assertGlided(rowMoves("Previous category"))
        assertEquals(first, rowTops(), "it should land on the first category's header")

        assertGlided(rowMoves("Next category"))
        assertGlided(rowMoves("Next category"))
        assertEquals(third, rowTops(), "it should land on the third category's header")
    }
}
