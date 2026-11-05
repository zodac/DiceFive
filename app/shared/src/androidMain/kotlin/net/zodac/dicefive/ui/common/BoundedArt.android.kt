package net.zodac.dicefive.ui.common

import android.graphics.RenderNode
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil

@Composable
internal actual fun BoundedArt(modifier: Modifier, overflow: Dp, art: DrawScope.() -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        BoundedArtInNode(modifier, overflow, art)
    } else {
        BoundedArtInLayer(modifier, art)
    }
}

/** [BoundedArt] in a [RenderNode] clipped to its bounds. A canvas that can't hold a node (a software one) has the art drawn on it. */
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
private fun BoundedArtInNode(modifier: Modifier, overflow: Dp, art: DrawScope.() -> Unit) {
    val view = LocalView.current
    val artNode = remember(view) { ArtNode(view) }
    DisposableEffect(artNode) { onDispose { artNode.release() } }
    Spacer(
        modifier = modifier.drawBehind {
            val canvas = drawContext.canvas.nativeCanvas
            if (canvas.isHardwareAccelerated) {
                artNode.show(this, overflow.toPx(), art)
                canvas.drawRenderNode(artNode.node)
            } else {
                art()
            }
        },
    )
}

/**
 * Watches what every [ArtNode]'s art reads: one observer for them all, as an observer looks through what it watches on
 * every change to any state, and a page can hold many. Only used on the main thread.
 */
private val artReads = SnapshotStateObserver { change ->
    if (Looper.myLooper() == Looper.getMainLooper()) change() else Handler(Looper.getMainLooper()).post(change)
}.apply { start() }

/**
 * The node behind a [BoundedArtInNode], in [view], recorded again whenever what its art read changes, without [view]
 * recording anything: a node's change is bounded by its clip, and [View.invalidate] would redraw all of [view].
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class ArtNode(private val view: View) {
    val node = RenderNode("BoundedArt").apply { setClipToBounds(true) }

    private var art: DrawScope.() -> Unit = {}
    private var density: Density = Density(1f)
    private var layoutDirection = LayoutDirection.Ltr
    private var size = Size.Zero
    private var margin = 0f
    private var released = false

    /** Takes on [art] at the size of [scope], [margin] past it each way. Reads nothing that would redraw what draws this. */
    fun show(scope: DrawScope, margin: Float, art: DrawScope.() -> Unit) {
        this.art = art
        density = scope
        layoutDirection = scope.layoutDirection
        size = scope.size
        this.margin = margin
        Snapshot.withoutReadObservation { record() }
    }

    private fun record() {
        if (released) return
        artReads.observeReads(this, onArtChanged) {
            val reach = ceil(margin).toInt()
            val width = ceil(size.width).toInt()
            val height = ceil(size.height).toInt()
            node.setPosition(-reach, -reach, width + reach, height + reach)
            val canvas = node.beginRecording(width + reach * 2, height + reach * 2)
            try {
                canvas.translate(reach.toFloat(), reach.toFloat())
                CanvasDrawScope().draw(density, layoutDirection, Canvas(canvas), size, art)
            } finally {
                node.endRecording()
            }
        }
    }

    private fun redraw() {
        record()
        view.parent?.onDescendantInvalidated(view, view)
    }

    fun release() {
        released = true
        artReads.clear(this)
        node.discardDisplayList()
    }

    private companion object {
        val onArtChanged: (ArtNode) -> Unit = ArtNode::redraw
    }
}
