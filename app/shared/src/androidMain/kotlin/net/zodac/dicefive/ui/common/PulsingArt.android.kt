package net.zodac.dicefive.ui.common

import android.graphics.RenderNode
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import kotlin.math.ceil

@Composable
internal actual fun PulsingArt(alpha: State<Float>, modifier: Modifier, art: DrawScope.(alpha: Float) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PulsingArtInNode(alpha, modifier, art)
    } else {
        PulsingArtInLayer(alpha, modifier, art)
    }
}

/**
 * [PulsingArt] in a [RenderNode] of its own, clipped to its bounds: the renderer then redraws only that area for each
 * change. A Compose layer never gets that clip. A canvas that can't hold a node (a software one) has the art drawn on it.
 */
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
private fun PulsingArtInNode(alpha: State<Float>, modifier: Modifier, art: DrawScope.(alpha: Float) -> Unit) {
    val view = LocalView.current
    val pulse = remember(view) { NodePulse(view) }
    DisposableEffect(pulse) { onDispose { pulse.release() } }
    // The infinite-animation frame, as the alpha's own clock has: a UI test isn't kept waiting for it.
    LaunchedEffect(pulse, alpha) {
        while (true) {
            withInfiniteAnimationFrameNanos { pulse.fadeTo(alpha.value) }
        }
    }
    Box(
        modifier = modifier.drawBehind {
            val canvas = drawContext.canvas.nativeCanvas
            if (canvas.isHardwareAccelerated) {
                pulse.record(this) { art(1f) }
                pulse.fadeTo(Snapshot.withoutReadObservation { alpha.value }, askForFrame = false)
                canvas.drawRenderNode(pulse.node)
            } else {
                art(alpha.value)
            }
        },
    )
}

/** The node behind a [PulsingArtInNode], in [view]: faded without [view] recording anything again. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class NodePulse(private val view: View) {
    val node = RenderNode("PulsingArt").apply {
        setClipToBounds(true)
        setHasOverlappingRendering(false)
    }

    /** Records [art] at the size of [scope]. */
    fun record(scope: DrawScope, art: DrawScope.() -> Unit) {
        val width = ceil(scope.size.width).toInt()
        val height = ceil(scope.size.height).toInt()
        node.setPosition(0, 0, width, height)
        val canvas = node.beginRecording(width, height)
        try {
            CanvasDrawScope().draw(scope, scope.layoutDirection, Canvas(canvas), scope.size, art)
        } finally {
            node.endRecording()
        }
    }

    /**
     * Fades the node to [alpha]. Asking the parent for the frame, as a Compose layer does, leaves [view]'s own recording
     * alone: [View.invalidate] would redraw all of it.
     */
    fun fadeTo(alpha: Float, askForFrame: Boolean = true) {
        node.setAlpha(alpha)
        if (askForFrame) view.parent?.onDescendantInvalidated(view, view)
    }

    fun release() = node.discardDisplayList()
}
