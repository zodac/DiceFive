package net.zodac.dicefive

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.ModePage
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** Frames to look for the new page in, and to let it settle over. */
private const val FRAMES = 10

/** Rows on the last page, few enough to fit without a scrollbar. */
private const val LAST_PAGE_ROWS = 3

/** Going from a page that scrolls to one that doesn't, the old page's scrollbar and arrows aren't drawn over the new one. */
class LeaderboardPageFlashTest : UiTest() {

    private fun ComposeUiTest.frame(): Bitmap = onRoot().captureToImage().asAndroidBitmap()

    /** The pixels of [actual] that aren't [expected]'s, as the box around them, for the failure message. */
    private fun differences(expected: Bitmap, actual: Bitmap): String {
        val xs = mutableListOf<Int>()
        val ys = mutableListOf<Int>()
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                if (expected.getPixel(x, y) != actual.getPixel(x, y)) {
                    xs += x
                    ys += y
                }
            }
        }
        return if (xs.isEmpty()) "" else "x ${xs.min()}..${xs.max()}, y ${ys.min()}..${ys.max()} of ${expected.width}x${expected.height}"
    }

    @Test
    fun `a page that fits looks settled from its first frame`() = uiTest {
        val rows = (1..SCORES_PAGE_SIZE + LAST_PAGE_ROWS).map { rowOf(it.toLong(), SCORES_PAGE_SIZE * 2 - it, GameMode.STANDARD) }
        val viewModel = ScoresViewModel(ScoreRepository(InMemoryScoreDao(rows)), MutableStateFlow(ModePage.Mode(GameMode.STANDARD)))
        setContent { DiceFiveTheme { ScoresScreen(viewModel = viewModel, onBack = {}, driftingDice = false) } }
        waitForIdle()
        mainClock.autoAdvance = false

        viewModel.nextPage()
        val firstFrame = (1..FRAMES).firstNotNullOfOrNull {
            mainClock.advanceTimeByFrame()
            frame().takeIf { onAllNodesWithText("Player${SCORES_PAGE_SIZE + 1}").fetchSemanticsNodes().isNotEmpty() }
        }
        assertNotNull(firstFrame, "the last page never came up")
        mainClock.advanceTimeBy(FRAMES * 16L)

        assertEquals("", differences(frame(), firstFrame))
    }
}
