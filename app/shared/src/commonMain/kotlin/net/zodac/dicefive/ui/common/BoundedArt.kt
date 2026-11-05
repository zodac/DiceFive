package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Art that redraws on its own clock: drawn like a `Canvas`, except that where the platform can, each redraw has the GPU
 * redraw no more than its area, rather than the whole window as a change to a Compose layer does. Any state [art] reads
 * redraws it, and nothing recomposes.
 *
 * It's cut off [overflow] past [modifier]'s bounds, which is also how far that area reaches: give art that reaches out of
 * its box just the room it needs. Something moving that isn't in one (a Compose layer turning) still redraws the window.
 */
@Composable
internal expect fun BoundedArt(modifier: Modifier, overflow: Dp = 0.dp, art: DrawScope.() -> Unit)

/** [BoundedArt] in a Compose layer, for a platform with nothing narrower: each redraw redraws the whole window. */
@Composable
internal fun BoundedArtInLayer(modifier: Modifier, art: DrawScope.() -> Unit) {
    Spacer(modifier = modifier.graphicsLayer { }.drawBehind(art))
}
