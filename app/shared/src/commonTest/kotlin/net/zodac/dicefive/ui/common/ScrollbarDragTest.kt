package net.zodac.dicefive.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import net.zodac.dicefive.TestScreen
import net.zodac.dicefive.UiTest
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.ui.scores.LeaderboardPage
import net.zodac.dicefive.ui.scores.LeaderboardTable
import net.zodac.dicefive.ui.theme.DiceFiveTheme

private val SCREEN = TestScreen(360, 800)
private val LIST_WIDTH = 300.dp
private val LIST_HEIGHT = 600.dp

/** Holding and pulling a scrollbar's thumb: what it scrolls follows it, and only the thumb takes the touch. */
class ScrollbarDragTest : UiTest() {

    private lateinit var list: LazyListState

    private fun ComposeUiTest.show(direction: LayoutDirection = LayoutDirection.Ltr, barOffset: Dp = 0.dp) {
        setContent {
            DiceFiveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    list = rememberLazyListState()
                    Box(Modifier.size(LIST_WIDTH, LIST_HEIGHT)) {
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                            items(1_000) { Text("Row $it", Modifier.height(30.dp)) }
                        }
                        LazyListScrollbar(list, modifier = Modifier.offset(x = barOffset))
                    }
                }
            }
        }
        waitForIdle()
    }

    /** A press at ([xDp], [yDp]), pulled by [pullDp], then let go. */
    private fun ComposeUiTest.pull(xDp: Float, yDp: Float, pullDp: Float) {
        // The thumb's place comes from its drawing, which Robolectric does only for a capture.
        onRoot().captureToImage()
        onRoot().performTouchInput {
            down(Offset(xDp.dp.toPx(), yDp.dp.toPx()))
            moveBy(Offset(0f, pullDp.dp.toPx()))
            up()
        }
        waitForIdle()
    }

    private val atBarX = LIST_WIDTH.value - 2f

    @Test
    fun `pulling the thumb the length of the track reaches the list's end, and pulling it back its start`() = uiTest(SCREEN) {
        show()
        pull(atBarX, 5f, LIST_HEIGHT.value)
        assertFalse(list.canScrollForward)

        pull(atBarX, LIST_HEIGHT.value - 5f, -LIST_HEIGHT.value)
        assertEquals(0, list.firstVisibleItemIndex)
    }

    @Test
    fun `the list follows the thumb in proportion, far faster than the finger`() = uiTest(SCREEN) {
        show()
        // A thumb at its 8% minimum travels 552dp, so half that is half of the list's 980 rows' worth of scrolling.
        pull(atBarX, 5f, 276f)
        assertTrue(list.firstVisibleItemIndex in 480..500, "${list.firstVisibleItemIndex}")
    }

    @Test
    fun `the handle reaches past the thin bar, for a finger`() = uiTest(SCREEN) {
        show()
        pull(LIST_WIDTH.value - 20f, 5f, 276f)
        assertTrue(list.firstVisibleItemIndex > 400, "${list.firstVisibleItemIndex}")
    }

    @Test
    fun `a bar placed out past the list's edge, as Achievements' is in the page margin, is held there`() = uiTest(SCREEN) {
        show(barOffset = 12.dp)
        pull(LIST_WIDTH.value + 10f, 5f, LIST_HEIGHT.value)
        assertFalse(list.canScrollForward)
    }

    @Test
    fun `off the thumb, a touch on the track is the list's own scroll`() = uiTest(SCREEN) {
        show()
        pull(atBarX, 400f, -300f)
        // The list's own drag moves it about the finger's distance, rows of 30dp: not a jump along the track.
        assertTrue(list.firstVisibleItemIndex in 5..10, "${list.firstVisibleItemIndex}")
    }

    @Test
    fun `right to left, the thumb is held at the bar's own side`() = uiTest(SCREEN) {
        show(direction = LayoutDirection.Rtl)
        pull(2f, 5f, LIST_HEIGHT.value)
        assertFalse(list.canScrollForward)
    }

    @Test
    fun `the position for a scroll offset is the item it lands in and how far in`() {
        val sizes = listOf(10f, 20f, 30f)
        assertEquals(0 to 5, scrollPositionAt(5f, 3, 0f) { sizes[it] })
        assertEquals(1 to 0, scrollPositionAt(10f, 3, 0f) { sizes[it] })
        assertEquals(2 to 5, scrollPositionAt(35f, 3, 0f) { sizes[it] })
        // The gap between items counts as the item before it's.
        assertEquals(1 to 4, scrollPositionAt(16f, 3, 2f) { sizes[it] })
        // Past the end: the last item, for the list to stop at its own end.
        assertEquals(2 to 70, scrollPositionAt(100f, 3, 0f) { sizes[it] })
    }

    private fun rows(count: Int) = List(count) { index ->
        ScoreEntry(
            uuid = Uuid.fromLongs(0L, index.toLong()),
            playerName = "Player$index",
            score = 100_000 - index,
            timestampEpochMillis = 1L,
            won = null,
            isPrimaryPlayer = true,
            fiveOfAKindCount = 0,
            zeroedCategoryCount = 0,
            upperSectionTotal = 0,
            chanceScore = 0,
            threeOfAKindScore = 0,
            fourOfAKindScore = 0,
        )
    }

    private fun ComposeUiTest.pullLeaderboardThumb(count: Int) {
        setContent {
            DiceFiveTheme {
                Column(Modifier.size(360.dp, 700.dp)) {
                    LeaderboardTable(LeaderboardPage(rows(count), 0, count), "", combined = false, onPrevious = {}, onNext = {}, landscape = false) {
                        Text("Board")
                    }
                }
            }
        }
        waitForIdle()
        val top = onNodeWithText("Player0").fetchSemanticsNode().boundsInRoot.top
        onRoot().captureToImage()
        onRoot().performTouchInput {
            down(Offset(width - 12.dp.toPx(), top + 4.dp.toPx()))
            moveBy(Offset(0f, height.toFloat()))
            up()
        }
        waitForIdle()
    }

    @Test
    fun `a Leaderboard page's thumb can be pulled however few rows it has, as long as it scrolls`() = uiTest(SCREEN) {
        pullLeaderboardThumb(40)
        onNodeWithText("Player39").assertExists()
    }

    private lateinit var page: ScrollState

    /** A long page in the [LIST_WIDTH] box, with its bar in a narrow margin past the box's end, as Styles has it. */
    private fun ComposeUiTest.showPage() {
        setContent {
            DiceFiveTheme {
                page = rememberScrollState()
                Box(Modifier.size(LIST_WIDTH, LIST_HEIGHT)) {
                    Box(Modifier.fillMaxSize().verticalScroll(page)) { Text("Page", Modifier.height(5_000.dp)) }
                    VerticalScrollbar(page, width = 20.dp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 20.dp))
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage()
    }

    @Test
    fun `a page bar's thumb is held from past its narrow margin, and is pulled to the end of the page`() = uiTest(SCREEN) {
        showPage()
        // The margin runs from the box's end for 20dp; this is 10dp in from there, over the page.
        pull(LIST_WIDTH.value - 10f, 5f, LIST_HEIGHT.value)
        assertEquals(page.maxValue, page.value)
    }

    @Test
    fun `over the page off a page bar's thumb, a touch is the page's own`() = uiTest(SCREEN) {
        showPage()
        pull(LIST_WIDTH.value - 10f, 400f, -100f)
        // The page's own drag moves it about the finger's distance: nothing like the thumb's pace.
        assertTrue(page.value in 1..with(density) { 110.dp.toPx() }.toInt(), "${page.value}")
    }

    private lateinit var row: ScrollState

    private fun ComposeUiTest.showRow(direction: LayoutDirection = LayoutDirection.Ltr) {
        setContent {
            DiceFiveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    row = rememberScrollState()
                    Column(Modifier.size(LIST_WIDTH, 100.dp)) {
                        Row(Modifier.fillMaxWidth().height(40.dp).horizontalScroll(row)) { Text("Tiles", Modifier.width(3_000.dp)) }
                        HorizontalScrollbar(row, modifier = Modifier.testTag("bar"))
                        Text("Under", Modifier.testTag("under"))
                    }
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage()
    }

    /** A press at ([xDp], the bar's height plus [yOffDp]), pulled along by [pullDp], then let go. */
    private fun ComposeUiTest.pullAlong(xDp: Float, yOffDp: Float, pullDp: Float) {
        onRoot().performTouchInput {
            down(Offset(xDp.dp.toPx(), (40f + 2f + yOffDp).dp.toPx()))
            moveBy(Offset(pullDp.dp.toPx(), 0f))
            up()
        }
        waitForIdle()
    }

    @Test
    fun `a horizontal bar's thumb pulled the length of its track reaches the row's end`() = uiTest(SCREEN) {
        showRow()
        pullAlong(5f, 0f, LIST_WIDTH.value)
        assertEquals(row.maxValue, row.value)
    }

    @Test
    fun `a horizontal bar's thumb is held from over what's around the thin bar`() = uiTest(SCREEN) {
        showRow()
        pullAlong(5f, 20f, LIST_WIDTH.value)
        assertEquals(row.maxValue, row.value)
    }

    @Test
    fun `off a horizontal bar's thumb, nothing is pulled`() = uiTest(SCREEN) {
        showRow()
        pullAlong(LIST_WIDTH.value - 5f, 0f, -100f)
        assertEquals(0, row.value)
    }

    @Test
    fun `a horizontal bar takes only its own thin height, for all its thumb's reach`() = uiTest(SCREEN) {
        showRow()
        assertEquals(4.dp, onNodeWithTag("bar").getBoundsInRoot().let { it.bottom - it.top })
        assertEquals(44.dp, onNodeWithTag("under").getBoundsInRoot().top)
    }
}
