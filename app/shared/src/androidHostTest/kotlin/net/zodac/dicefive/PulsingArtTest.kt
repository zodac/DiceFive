package net.zodac.dicefive

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.ui.common.NodePulse
import net.zodac.dicefive.ui.common.PulsingArt

/**
 * [PulsingArt] fades its art without recomposing. In a node, which is what stops each fade redrawing the whole window, a
 * fade only asks the view's parent for a frame: invalidating the view itself would have it record everything again.
 */
class PulsingArtTest : UiTest() {

    private class WatchingParent(context: Context) : FrameLayout(context) {
        var framesAsked = 0

        override fun onDescendantInvalidated(child: View, target: View) {
            framesAsked++
        }
    }

    private class WatchedView(context: Context) : View(context) {
        var invalidations = 0

        override fun invalidate() {
            invalidations++
        }
    }

    @Test
    fun aFadeAsksTheParentForAFrameAndLeavesTheViewsRecordingAlone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = WatchingParent(context)
        val view = WatchedView(context)
        parent.addView(view)
        view.invalidations = 0
        val pulse = NodePulse(view)

        pulse.fadeTo(0.7f)

        assertEquals(0.7f, pulse.node.alpha)
        assertEquals(1, parent.framesAsked)
        assertEquals(0, view.invalidations)

        // Set while drawing, a frame is already under way.
        pulse.fadeTo(0.6f, askForFrame = false)
        assertEquals(0.6f, pulse.node.alpha)
        assertEquals(1, parent.framesAsked)
        pulse.release()
    }

    @Test
    fun theNodeIsClippedToItsBoundsWithNothingToCompositeAndHoldsWhatWasRecorded() {
        val pulse = NodePulse(View(ApplicationProvider.getApplicationContext()))
        assertTrue(pulse.node.clipToBounds)
        assertTrue(!pulse.node.hasOverlappingRendering())
        assertTrue(!pulse.node.hasDisplayList())

        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(1, 1)), Size(20f, 10f)) {
            pulse.record(this) { drawRect(Color.Red) }
        }

        assertEquals(20, pulse.node.width)
        assertEquals(10, pulse.node.height)
        assertTrue(pulse.node.hasDisplayList())
        pulse.release()
        assertTrue(!pulse.node.hasDisplayList())
    }

    @Test
    fun theArtShowsAtItsAlphaAndAChangeRecomposesNothing() = uiTest {
        val alpha = mutableFloatStateOf(0.5f)
        var compositions = 0
        setContent {
            SideEffect { compositions++ }
            Box(Modifier.background(Color.Black)) {
                PulsingArt(alpha = alpha, modifier = Modifier.size(ART_SIZE)) { faded -> drawRect(Color.White, alpha = faded) }
            }
        }
        waitForIdle()

        val image: Bitmap = onRoot().captureToImage().asAndroidBitmap()
        val red = android.graphics.Color.red(image.getPixel(image.width / 2, image.height / 2))
        assertTrue(red in 126..129, "Not half faded: $red")
        val settled = compositions

        alpha.floatValue = 1f
        waitForIdle()

        assertEquals(settled, compositions)
    }

    private companion object {
        val ART_SIZE = 40.dp
    }
}
