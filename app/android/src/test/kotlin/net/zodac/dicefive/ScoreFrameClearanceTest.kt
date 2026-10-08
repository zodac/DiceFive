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
import net.zodac.dicefive.ui.theme.SurfaceContainer
import net.zodac.dicefive.ui.theme.color
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Every frame keeps clear of the active tab's name, score, place and turn dot, on the narrowest phone the
 * name caps were sized for (360dp) at four players, where tabs are tightest. Measured from real renders:
 * the tab is drawn once with no frame (its text is then whatever the player's colour pulled away from the
 * panel) and once with the frame (its pixels are what changed by more than a faint wash), and the
 * closest frame pixel to any text pixel must be [MIN_GAP_DP] away. A frame that grows into the name
 * fails here before anyone sees it on a phone.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ScoreFrameClearanceTest {
    @get:Rule val compose = createComposeRule()

    private object NoFrame : ScoreFrame {
        override val id = "none"
        override fun DrawScope.drawFrame(color: Color) = Unit
    }

    private fun player(name: String, ai: Boolean, colour: PlayerColour, score: Int): PlayerState {
        val p = PlayerState(name = name, type = if (ai) PlayerType.AI else PlayerType.HUMAN, colour = colour)
        return p.copy(scorecard = p.scorecard + (ScoreCategory.CHANCE to listOf(score)))
    }

    // The active tab first: the widest ordinary name at the four-player cap, and a CPU one beside its chip.
    // Tied three-digit scores, so the place row is there too ("=1st").
    private val tables = listOf(
        listOf(player("Wolfgang", false, PlayerColour.CYAN, 388), player("Player 2", false, PlayerColour.PURPLE, 388)),
        listOf(player("Payout", true, PlayerColour.CYAN, 388), player("Player 2", false, PlayerColour.PURPLE, 388)),
    ).map { it + player("Cubes", true, PlayerColour.GREEN, 120) + player("Wildly", true, PlayerColour.AMBER, 120) }

    @Test
    fun everyFrameClearsTheActiveTabsText() {
        var frame: ScoreFrame by mutableStateOf(NoFrame)
        var players by mutableStateOf(tables[0])
        compose.setContent {
            DiceFiveTheme {
                CompositionLocalProvider(LocalGameVisualTheme provides GameVisualTheme(frame = frame)) {
                    // The game screen's width for the tabs: 16dp padding each side and the back arrow's 36dp.
                    Box(
                        Modifier.testTag("header").background(MaterialTheme.colorScheme.background)
                            .padding(start = LEFT_MARGIN_DP.dp, top = 6.dp, bottom = 6.dp).width((SCREEN_DP - 68).dp),
                    ) {
                        PlayerHeaderBar(players, currentPlayerIndex = 0, viewedPlayerIndex = null, enabled = true, onPlayerTap = {})
                    }
                }
            }
        }
        val density = compose.density.density
        val failures = mutableListOf<String>()
        for (table in tables) {
            players = table
            frame = NoFrame
            compose.waitForIdle()
            val plain = compose.onNodeWithTag("header").captureToImage().asAndroidBitmap()
            // The active (first) tab, and the few dp a frame may spill past it.
            val width = (((SCREEN_DP - 68) / table.size + LEFT_MARGIN_DP + 4) * density).toInt()
            for (f in ScoreFrames.all) {
                frame = f
                compose.waitForIdle()
                val framed = compose.onNodeWithTag("header").captureToImage().asAndroidBitmap()
                val gap = closestGapPx(plain, framed, width, table[0].colour.color) / density
                if (gap < MIN_GAP_DP) failures += "${f.id} with ${table[0].name}: %.1fdp".format(gap)
            }
        }
        assertTrue("Frames too close to the active tab's text (need ${MIN_GAP_DP}dp): $failures", failures.isEmpty())
    }

    /** The distance, in pixels, from the frame's nearest pixel to the tab's nearest text pixel - 0 or less if they touch. */
    private fun closestGapPx(plain: Bitmap, framed: Bitmap, w: Int, colour: Color): Float {
        val h = plain.height
        val panel = SurfaceContainer
        val dr = colour.red - panel.red
        val dg = colour.green - panel.green
        val db = colour.blue - panel.blue
        val length2 = dr * dr + dg * dg + db * db
        // How far a pixel is pulled from the panel towards the player's colour: 0 on the panel, 1 at full colour.
        fun pull(c: Color) = ((c.red - panel.red) * dr + (c.green - panel.green) * dg + (c.blue - panel.blue) * db) / length2
        val text = BooleanArray(w * h)
        val frame = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val before = Color(plain.getPixel(x, y))
            val after = Color(framed.getPixel(x, y))
            val i = y * w + x
            text[i] = pull(before) > 0.3f
            val moved = (after.red - before.red).let { it * it } + (after.green - before.green).let { it * it } + (after.blue - before.blue).let { it * it }
            // More than a wash: Art Deco tints the whole tab by 16%, which is meant to sit behind the text.
            frame[i] = pull(after) - pull(before) > 0.3f || moved > 0.12f
        }
        // Each pixel's distance (in steps) to the nearest frame pixel, spreading out from all of them at once.
        val distance = IntArray(w * h) { Int.MAX_VALUE }
        val queue = ArrayDeque<Int>()
        for (i in frame.indices) if (frame[i]) { distance[i] = 0; queue.add(i) }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % w
            val y = i / w
            for ((nx, ny) in listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                if (nx !in 0 until w || ny !in 0 until h) continue
                val j = ny * w + nx
                if (distance[j] == Int.MAX_VALUE) { distance[j] = distance[i] + 1; queue.add(j) }
            }
        }
        val closest = text.indices.filter { text[it] }.minOfOrNull { distance[it] } ?: Int.MAX_VALUE
        return closest - 1f
    }

    private companion object {
        const val SCREEN_DP = 360
        const val LEFT_MARGIN_DP = 8
        const val MIN_GAP_DP = 1.5f
    }
}
