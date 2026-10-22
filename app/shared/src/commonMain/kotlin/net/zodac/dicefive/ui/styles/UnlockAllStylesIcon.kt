package net.zodac.dicefive.ui.styles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp

/**
 * The "Unlock all styles" purchase's mark ([net.zodac.dicefive.purchases.ProductKey.UNLOCK_ALL_STYLES]): a palette,
 * centred in the [size] square, with an open padlock on a disc at its lower left, all in [tint]. The ring round the disc
 * and the padlock are cut out, so whatever is behind shows through them: a button's container, or Play Console's gold
 * tile.
 *
 * The padlock stays at the lower left in a right-to-left language too, clear of the palette's thumb hole. Decorative:
 * whatever shows it names the purchase itself.
 */
@Composable
fun UnlockAllStylesIcon(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    val palette = rememberVectorPainter(Icons.Filled.Palette)
    val padlock = rememberVectorPainter(Icons.Filled.LockOpen)
    // Drawn offscreen, so the cut-outs clear only the mark's own pixels, not what's behind it.
    Canvas(modifier.size(size).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val unit = this.size.minDimension / UNITS
        drawCentred(palette, PALETTE * unit, center, ColorFilter.tint(tint))
        val disc = center + Offset(DISC_X * unit, DISC_Y * unit)
        drawCircle(Color.Black, radius = (DISC / 2 + RING) * unit, center = disc, blendMode = BlendMode.Clear)
        drawCircle(tint, radius = DISC / 2 * unit, center = disc)
        val lock = DISC * PADLOCK * unit
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(disc, lock / 2), Paint().apply { blendMode = BlendMode.DstOut })
            drawCentred(padlock, lock, disc, ColorFilter.tint(Color.Black))
            canvas.restore()
        }
    }
}

/** [painter] at [side] square, centred on [centre]. */
private fun DrawScope.drawCentred(painter: Painter, side: Float, centre: Offset, colorFilter: ColorFilter) {
    translate(left = centre.x - side / 2, top = centre.y - side / 2) {
        with(painter) { draw(Size(side, side), colorFilter = colorFilter) }
    }
}

// In 512ths of the square, so the Play icon's pixels.
private const val UNITS = 512f
private const val PALETTE = 320f
private const val DISC = 150f
private const val RING = 22f

/** The disc's centre, from the square's. */
private const val DISC_X = -100f
private const val DISC_Y = 112f

/** Of the disc. */
private const val PADLOCK = 0.62f
