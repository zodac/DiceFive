package net.zodac.dicefive

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.ui.common.ArtNode
import net.zodac.dicefive.ui.common.BoundedArt
import org.robolectric.shadows.ShadowLooper

/**
 * [BoundedArt] redraws when what its art reads changes, without recomposing. In a node, a redraw asks the view's parent for
 * the frame and leaves the view's own recording alone, as invalidating the view would have the whole window redrawn.
 */
class BoundedArtTest : UiTest() {

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

    private fun applyChanges() {
        Snapshot.sendApplyNotifications()
        ShadowLooper.idleMainLooper()
    }

    /** Shows [art] in [node], as a draw of the view does, at 20 x 10 with [margin] past it. */
    private fun show(node: ArtNode, margin: Float = 0f, art: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(1, 1)), Size(20f, 10f)) {
            node.show(this, margin, art)
        }
    }

    @Test
    fun whatTheArtReadsChangingRecordsItAgainAndAsksTheParentForAFrame() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = WatchingParent(context)
        val view = WatchedView(context)
        parent.addView(view)
        view.invalidations = 0
        val node = ArtNode(view)
        val seconds = mutableFloatStateOf(0f)
        // State made this frame isn't yet "changed" by a write: an app's has been around longer.
        Snapshot.notifyObjectsInitialized()
        var draws = 0
        show(node) {
            seconds.floatValue
            draws++
            drawRect(Color.Red)
        }
        assertEquals(1, draws)
        assertEquals(0, parent.framesAsked)

        seconds.floatValue = 1f
        applyChanges()

        assertEquals(2, draws)
        assertEquals(1, parent.framesAsked)
        assertEquals(0, view.invalidations)
        node.release()
    }

    @Test
    fun onceReleasedNothingRecordsAgain() {
        val parent = WatchingParent(ApplicationProvider.getApplicationContext())
        val view = View(parent.context)
        parent.addView(view)
        val node = ArtNode(view)
        val seconds = mutableFloatStateOf(0f)
        // State made this frame isn't yet "changed" by a write: an app's has been around longer.
        Snapshot.notifyObjectsInitialized()
        var draws = 0
        show(node) {
            seconds.floatValue
            draws++
        }

        node.release()
        seconds.floatValue = 1f
        applyChanges()

        assertEquals(1, draws)
        assertEquals(0, parent.framesAsked)
        assertTrue(!node.node.hasDisplayList())
    }

    @Test
    fun theNodeIsClippedAndReachesItsMarginPastTheArtEachWay() {
        val node = ArtNode(View(ApplicationProvider.getApplicationContext()))
        assertTrue(node.node.clipToBounds)

        show(node, margin = 4f) { drawRect(Color.Red) }

        assertEquals(-4, node.node.left)
        assertEquals(-4, node.node.top)
        assertEquals(28, node.node.width)
        assertEquals(18, node.node.height)
        node.release()
    }

    @Test
    fun aChangeRedrawsTheArtWithoutRecomposing() = uiTest {
        val seconds = mutableFloatStateOf(0f)
        // State made this frame isn't yet "changed" by a write: an app's has been around longer.
        Snapshot.notifyObjectsInitialized()
        var compositions = 0
        var draws = 0
        setContent {
            SideEffect { compositions++ }
            BoundedArt(modifier = Modifier.size(40.dp)) {
                seconds.floatValue
                draws++
            }
        }
        waitForIdle()
        // A UI test draws only when captured.
        onRoot().captureToImage()
        val settled = compositions
        val drawn = draws

        seconds.floatValue = 1f
        applyChanges()
        waitForIdle()
        onRoot().captureToImage()

        assertEquals(settled, compositions)
        assertTrue(draws > drawn, "Not redrawn")
    }
}
