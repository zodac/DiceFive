package net.zodac.dicefive

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.game.style.DiceCupStyle
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.LocalCupActivity
import net.zodac.dicefive.ui.game.style.TreasureChestDiceCupStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Under reduced motion a cup never shakes - it's never told it's rolling (see DiceCupPanel) - but it still has to
 * show that a roll has landed, by snapping (never animating) to tipped or open: the Shipping container's doors, the
 * Picnic Basket's lids, the Treasure Chest's lid, the Volcano's eruption. Every cup style is checked, including when
 * first drawn already poured (a continued game, or back from another player's tab), where the Treasure Chest used
 * to stay shut for good, waiting for a shake it would never see.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CupReducedMotionTest {

    @get:Rule
    val compose = createComposeRule()

    private var style by mutableStateOf<DiceCupStyle>(DiceCupStyles.default)
    private var tilted by mutableStateOf(false)
    private var redraw by mutableIntStateOf(0)

    /** How many sampled pixels differ: a cup that tips or opens changes far more than a few. */
    private fun differingPixels(a: Bitmap, b: Bitmap): Int {
        var count = 0
        for (x in 0 until a.width step 2) for (y in 0 until a.height step 2) if (a.getPixel(x, y) != b.getPixel(x, y)) count++
        return count
    }

    /**
     * Changes the cup's state as one applied snapshot, so the composition sees it on the next frame - written
     * straight from the test thread with the clock paused, a change could go unnoticed.
     */
    private fun set(change: () -> Unit) = Snapshot.withMutableSnapshot(change)

    private fun settledShot(): Bitmap = shotAfter(3_000)

    // Only ever the time asked for: the test clock left to advance on its own runs any animation to its end first.
    private fun shotAfter(millis: Long): Bitmap {
        compose.mainClock.advanceTimeBy(millis)
        return compose.onNodeWithTag("cup").captureToImage().asAndroidBitmap()
    }

    /**
     * Makes [change] and asserts the cup snaps: every frame for a while after shows it either as it was before or as it
     * ends up, never anything in between. Returns how it ends up.
     */
    private fun assertSnaps(what: String, change: () -> Unit): Bitmap {
        val before = shotAfter(0)
        change()
        val frames = List(FRAMES_WATCHED) { shotAfter(FRAME_MILLIS) }
        val after = frames.last()
        frames.forEachIndexed { index, frame ->
            assertTrue(
                "${style.id} is caught part-way $what on frame $index",
                differingPixels(frame, before) == 0 || differingPixels(frame, after) == 0,
            )
        }
        return after
    }

    @Test
    fun `every cup tips or opens when its roll lands, even one first drawn already poured`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            // On a table being played at, as in the game (its dice are what LocalCupActivity carries there).
            CompositionLocalProvider(LocalReduceMotion provides true, LocalCupActivity provides Unit) {
                key(redraw) {
                    Box(Modifier.size(104.dp).testTag("cup")) {
                        style.Cup(rolling = false, tilted = tilted, modifier = Modifier.size(style.shape.gridWidth.dp, style.shape.gridHeight.dp))
                    }
                }
            }
        }
        for (cup in DiceCupStyles.all) {
            set {
                style = cup
                tilted = false
                redraw++
            }
            val standing = settledShot()
            // A player has real time at the table before a roll lands, in which the Treasure Chest paints its hoard
            // on a background thread (see its Cup). The test clock's time isn't real, so give it some.
            if (cup is TreasureChestDiceCupStyle) {
                Thread.sleep(PAINTING_MILLIS)
                settledShot()
            }
            val landed = assertSnaps("tipping or opening") { set { tilted = true } }
            // A cup that only plays something as it pours (the Chicken's squawk) ends as it began.
            if (cup.changesWhenPoured) {
                assertTrue("${style.id} doesn't change when its roll lands", differingPixels(standing, landed) > CHANGED_PIXELS)
            }
            assertSnaps("standing back up or closing") { set { tilted = false } }
            set { tilted = true }
            settledShot()

            // First drawn already poured; then stood up for the next roll (a cup isn't told it's shaking), and poured.
            set { redraw++ }
            settledShot()
            set { tilted = false }
            settledShot()
            set { tilted = true }
            val landedAgain = settledShot()
            if (cup.changesWhenPoured) {
                assertTrue("${style.id} first drawn poured doesn't change when its next roll lands", differingPixels(standing, landedAgain) > CHANGED_PIXELS)
            }
        }
    }

    private companion object {
        const val CHANGED_PIXELS = 100

        const val FRAME_MILLIS = 16L

        /** Real time for a cup's background painting before its first roll lands. */
        const val PAINTING_MILLIS = 300L

        /** About half a second of frames: longer than any of the cups' swings, and short of the Top Hat's first rabbit peek. */
        const val FRAMES_WATCHED = 30
    }
}
