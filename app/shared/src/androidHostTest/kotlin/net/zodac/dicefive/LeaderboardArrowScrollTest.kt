package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.ModePage
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme

private const val ROWS = 60

/** Frames to look at: past an arrow's longest scroll, 800ms. */
private const val FRAMES = 60
private const val NOTHING_STAYED_ON_SCREEN = 99_999
private const val SLOWING_SLACK = 1.15f
private const val SLOWING_SLACK_PX = 12

/** A Leaderboard page's arrows glide to its top and bottom rather than snapping, unless motion is reduced. */
class LeaderboardArrowScrollTest : UiTest() {

    private fun ComposeUiTest.showBoard(reduceMotion: Boolean) {
        val rows = (1..ROWS).map { rowOf(it.toLong(), 1_000 - it, GameMode.STANDARD) }
        val viewModel = ScoresViewModel(ScoreRepository(InMemoryScoreDao(rows)), MutableStateFlow(ModePage.Mode(GameMode.STANDARD)))
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                DiceFiveTheme { ScoresScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
    }

    private fun ComposeUiTest.rowTops(): Map<String, Float> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.Text].joinToString() to it.boundsInRoot.top }
            .filter { (text, _) -> text.startsWith("Player") && text != "Player" }
            .toMap()

    /** How far the rows moved on each frame of a tap on the arrow [described], until it has settled. */
    private fun ComposeUiTest.rowMoves(described: String): List<Int> {
        onNodeWithContentDescription(described).performClick()
        var last = rowTops()
        return List(FRAMES) {
            mainClock.advanceTimeByFrame()
            val now = rowTops()
            val moved = now.keys.intersect(last.keys).map { (now.getValue(it) - last.getValue(it)).toInt() }.sorted()
            last = now
            moved.getOrElse(moved.size / 2) { NOTHING_STAYED_ON_SCREEN }
        }
    }

    private fun assertGlided(moves: List<Int>) {
        val sizes = moves.map { abs(it) }
        assertTrue(moves.map { it.sign }.filter { it != 0 }.distinct().size == 1, "the rows turned back on the way: $moves")
        assertTrue(sizes.count { it > 0 } >= 5, "the rows got there in a few frames: $moves")
        val slowing = sizes.drop(sizes.indexOf(sizes.max()))
        assertTrue(slowing.zipWithNext().all { (from, to) -> to <= from * SLOWING_SLACK + SLOWING_SLACK_PX }, "the rows jumped as they slowed: $moves")
        assertEquals(0, sizes.last(), "the rows were still moving at the end: $moves")
    }

    @Test
    fun `the arrows glide to the bottom row and back to the top one`() = uiTest {
        showBoard(reduceMotion = false)
        assertGlided(rowMoves("Go to the bottom of the page"))
        onNodeWithText("Player$ROWS").assertExists()

        assertGlided(rowMoves("Go to the top of the page"))
        onNodeWithText("Player1").assertExists()
    }

    @Test
    fun `under reduced motion the arrows jump`() = uiTest {
        showBoard(reduceMotion = true)
        onAllNodesWithText("Player$ROWS").assertCountEquals(0)
        onNodeWithContentDescription("Go to the bottom of the page").performClick()
        // Two frames: the tap's, and the jump's.
        mainClock.advanceTimeBy(48)
        onNodeWithText("Player$ROWS").assertExists()
    }
}
