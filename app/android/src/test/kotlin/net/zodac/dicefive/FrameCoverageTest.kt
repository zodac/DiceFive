package net.zodac.dicefive

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.game.PlayerHeaderBar
import net.zodac.dicefive.ui.game.style.GameVisualTheme
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.game.style.ScoreFrame
import net.zodac.dicefive.ui.game.style.ScoreFrames
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The frames that go all the way round a tab - the wreaths, the starry, nautical and marquee ones - fill the band along every
 * edge, in each third of it, not just the corners or one side (the wreath once stopped short of the top). Measured from real
 * renders: the active tab drawn plain and framed, counting the pixels the frame changed 3 to 6dp in from each edge. A plain ring
 * and Greek Key are held to the same strips and must fail them, so the check can't pass for everything.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class FrameCoverageTest {
    @get:Rule val compose = createComposeRule()

    private object NoFrame : ScoreFrame {
        override val id = "none"
        override fun DrawScope.drawFrame(color: Color) = Unit
    }

    private fun player(name: String, colour: PlayerColour): PlayerState {
        val p = PlayerState(name = name, type = PlayerType.HUMAN, colour = colour)
        return p.copy(scorecard = p.scorecard + (ScoreCategory.CHANCE to listOf(388)))
    }

    /**
     * How many pixels [frame] changes in the band 3 to 6dp in from each edge of the tab, in each third of it (away from the
     * corners): "top 0" to "top 2" left to right, "left 0" to "left 2" top to bottom, and so on. An outline hugging the edge
     * changes none of them; a border of leaves, stars or links changes them all.
     */
    private fun depthCounts(frame: ScoreFrame, plain: Bitmap, density: Float): Map<String, Int> {
        this.frame = frame
        compose.waitForIdle()
        val framed = compose.onNodeWithTag("header").captureToImage().asAndroidBitmap()
        fun px(dp: Float) = (dp * density).toInt()
        val left = px(TAB_LEFT_DP.toFloat())
        val right = px((TAB_LEFT_DP + TAB_WIDTH_DP).toFloat())
        val top = px(TAB_TOP_DP.toFloat())
        val bottom = plain.height - px(TAB_TOP_DP.toFloat())
        val corner = px(CORNER_DP)
        fun changed(x0: Int, x1: Int, y0: Int, y1: Int): Int {
            var count = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                val a = Color(plain.getPixel(x, y))
                val b = Color(framed.getPixel(x, y))
                val moved = (a.red - b.red).let { it * it } + (a.green - b.green).let { it * it } + (a.blue - b.blue).let { it * it }
                if (moved > MOVED) count++
            }
            return count
        }
        val near = px(DEPTH_FROM_DP)
        val far = px(DEPTH_TO_DP)
        val counts = linkedMapOf<String, Int>()
        for (third in 0 until 3) {
            val x0 = left + corner + (right - left - 2 * corner) * third / 3
            val x1 = left + corner + (right - left - 2 * corner) * (third + 1) / 3
            val y0 = top + corner + (bottom - top - 2 * corner) * third / 3
            val y1 = top + corner + (bottom - top - 2 * corner) * (third + 1) / 3
            counts["top $third"] = changed(x0, x1, top + near, top + far)
            counts["bottom $third"] = changed(x0, x1, bottom - far, bottom - near)
            counts["left $third"] = changed(left + near, left + far, y0, y1)
            counts["right $third"] = changed(right - far, right - near, y0, y1)
        }
        return counts
    }

    private var frame: ScoreFrame by mutableStateOf(NoFrame)

    @Test
    fun framesThatWrapTheTabCoverEveryEdge() {
        compose.setContent {
            DiceFiveTheme {
                CompositionLocalProvider(LocalGameVisualTheme provides GameVisualTheme(frame = frame)) {
                    Box(
                        Modifier.testTag("header").background(MaterialTheme.colorScheme.background)
                            .padding(start = HEADER_LEFT_DP.dp, top = 6.dp, bottom = 6.dp).width((360 - 68).dp),
                    ) {
                        PlayerHeaderBar(
                            listOf(player("Wolfgang", PlayerColour.CYAN), player("Player 2", PlayerColour.PURPLE)),
                            currentPlayerIndex = 0, viewedPlayerIndex = null, enabled = true, onPlayerTap = {},
                        )
                    }
                }
            }
        }
        val density = compose.density.density
        frame = NoFrame
        compose.waitForIdle()
        val plain = compose.onNodeWithTag("header").captureToImage().asAndroidBitmap()

        val wrapping = listOf(
            "wreath_laurel", "wreath_olive", "starry_stars", "starry_sparkles", "starry_constellation",
            "nautical_rope", "nautical_chain", "marquee_bulbs", "marquee_diamonds",
        )
        fun frame(id: String) = ScoreFrames.all.single { it.id == id }
        val thin = wrapping.associateWith { id -> depthCounts(frame(id), plain, density).filterValues { it < MIN_PIXELS } }.filterValues { it.isNotEmpty() }
        assertEquals("Frames with too little art along part of the tab's edge, by strip and pixels changed", emptyMap<String, Map<String, Int>>(), thin)

        // The same check fails a plain ring, which hugs the edge, and Greek Key, which fills the top and bottom but not the sides.
        val ring = depthCounts(frame("classic_ring"), plain, density).filterValues { it < MIN_PIXELS }.keys
        assertEquals(12, ring.size)
        val greek = depthCounts(frame("greek_key"), plain, density).filterValues { it < MIN_PIXELS }.keys
        assertEquals(setOf("left 0", "left 1", "left 2", "right 0", "right 1", "right 2"), greek)
    }

    private companion object {
        /** The header's own left padding, and the tab inside it: 3dp in, [TAB_WIDTH_DP] wide for two players on a 360dp phone. */
        const val HEADER_LEFT_DP = 8
        const val TAB_TOP_DP = 6
        const val CORNER_DP = 12f
        const val DEPTH_FROM_DP = 3f
        const val DEPTH_TO_DP = 6f
        const val TAB_LEFT_DP = HEADER_LEFT_DP + 3
        const val TAB_WIDTH_DP = (360 - 68) / 2 - 6

        /** How many changed pixels a strip needs to count as covered: the sparsest frame, Stars on its sides, has about 55. */
        const val MIN_PIXELS = 30

        /** Squared distance between two colours, from 0 to 3, that counts a pixel as changed - a faint wash doesn't. */
        const val MOVED = 0.02f
    }
}
