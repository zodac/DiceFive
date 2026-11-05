package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Art that only ever fades in and out, by [alpha], staying within [modifier]'s bounds. A Compose layer that changes redraws
 * the whole window on the GPU, however small it is, so where the platform can, this redraws no more than the art's own
 * area. Nothing recomposes as [alpha] changes.
 *
 * [art] draws at the alpha it's given, and mustn't overlap itself, as nothing composites it first. It's recorded once and
 * then only faded, so it can't depend on any state that changes.
 */
@Composable
internal expect fun PulsingArt(alpha: State<Float>, modifier: Modifier, art: DrawScope.(alpha: Float) -> Unit)

/** [PulsingArt] in a Compose layer, for a platform with nothing narrower: each change redraws the whole window. */
@Composable
internal fun PulsingArtInLayer(alpha: State<Float>, modifier: Modifier, art: DrawScope.(alpha: Float) -> Unit) {
    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawBehind { art(1f) },
    )
}
