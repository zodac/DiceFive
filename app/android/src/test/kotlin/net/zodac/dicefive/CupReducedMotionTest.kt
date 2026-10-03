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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Under reduced motion a cup never shakes - it's never told it's rolling (see DiceCupPanel) - but it still has to
 * show that a roll has landed, by tipping or opening (the Shipping container's doors, the Picnic Basket's lids,
 * the Treasure Chest's lid, the Volcano's eruption). Every cup style is checked, including when first drawn
 * already poured (a continued game, or back from another player's tab), where the Treasure Chest used to stay
 * shut for good, waiting for a shake it would never see.
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

    private fun settledShot(): Bitmap {
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        return compose.onNodeWithTag("cup").captureToImage().asAndroidBitmap()
    }

    @Test
    fun `every cup tips or opens when its roll lands, even one first drawn already poured`() {
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                key(redraw) {
                    Box(Modifier.size(104.dp).testTag("cup")) {
                        style.Cup(rolling = false, tilted = tilted, modifier = Modifier.size(style.shape.gridWidth.dp, style.shape.gridHeight.dp))
                    }
                }
            }
        }
        for (cup in DiceCupStyles.all) {
            style = cup
            tilted = false
            redraw++
            val standing = settledShot()
            tilted = true
            val landed = settledShot()
            assertTrue("${style.id} doesn't change when its roll lands", differingPixels(standing, landed) > CHANGED_PIXELS)

            // First drawn already poured; then stood up for the next roll (a cup isn't told it's shaking), and poured.
            redraw++
            settledShot()
            tilted = false
            settledShot()
            tilted = true
            val landedAgain = settledShot()
            assertTrue("${style.id} first drawn poured doesn't change when its next roll lands", differingPixels(standing, landedAgain) > CHANGED_PIXELS)
        }
    }

    private companion object {
        const val CHANGED_PIXELS = 100
    }
}
