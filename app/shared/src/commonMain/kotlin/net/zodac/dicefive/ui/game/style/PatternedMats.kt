package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A mat's colours: the tray's [top]-to-[bottom] gradient, its held-dice slots ([slotTop] to
 * [slotBottom], darker than the tray, ringed in [slotBorder], lighter than both), and [detail] for
 * whatever pattern it draws on top.
 */
data class MatPalette(
    val top: Color,
    val bottom: Color,
    val slotTop: Color,
    val slotBottom: Color,
    val slotBorder: Color,
    val detail: Color,
)

/** A [DiceMat] from a [MatPalette], with [drawPattern] painted over the tray between its brush and the dice. */
abstract class PatternedDiceMat(final override val id: String, protected val palette: MatPalette) : DiceMat {
    final override val diceTrayBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))
    final override val slotSocketBrush: Brush = Brush.verticalGradient(listOf(palette.slotTop, palette.slotBottom))
    final override val slotSocketBorder: Color = palette.slotBorder

    protected abstract fun DrawScope.drawPattern()

    @Composable
    final override fun DiceTrayDecoration(modifier: Modifier) {
        Canvas(modifier = modifier) { drawPattern() }
    }
}

/** A rounded rectangle [inset] from the edges, stroked in [color]. */
private fun DrawScope.insetOutline(inset: Dp, corner: Dp, color: Color, stroke: Stroke) {
    val px = inset.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(px, px),
        size = Size(size.width - px * 2, size.height - px * 2),
        cornerRadius = CornerRadius(corner.toPx()),
        style = stroke,
    )
}

/** Tooled leather, stitched all the way round just inside its edge. */
class LeatherDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() {
        val stitching = Stroke(width = 1.3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.5.dp.toPx())))
        insetOutline(7.dp, 10.dp, palette.detail, stitching)
    }
}

/** Casino baize with a double gold pinstripe round the edge. */
class CasinoDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() {
        insetOutline(6.dp, 12.dp, palette.detail.copy(alpha = 0.9f), Stroke(width = 1.5.dp.toPx()))
        insetOutline(10.dp, 8.dp, palette.detail.copy(alpha = 0.55f), Stroke(width = 0.8.dp.toPx()))
    }
}

/** Polished dark marble, veined like a kitchen countertop. */
class MarbleDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() {
        drawMarble(seed = 11, vein = palette.detail)
    }
}

/** A chalkboard in a wooden frame, with a few faint smudges of old chalk. */
class ChalkboardDiceMat(id: String, palette: MatPalette, private val frame: Color) : PatternedDiceMat(id, palette), Swatched {
    override val swatch: Color = palette.top

    override fun DrawScope.drawPattern() {
        val smudge = Color.White.copy(alpha = 0.04f)
        val width = 18.dp.toPx()
        drawLine(smudge, Offset(size.width * 0.1f, size.height * 0.8f), Offset(size.width * 0.45f, size.height * 0.55f), strokeWidth = width)
        drawLine(smudge, Offset(size.width * 0.55f, size.height * 0.9f), Offset(size.width * 0.9f, size.height * 0.6f), strokeWidth = width)
        drawLine(smudge, Offset(size.width * 0.3f, size.height * 0.45f), Offset(size.width * 0.7f, size.height * 0.4f), strokeWidth = width * 0.7f)
        insetOutline(2.5.dp, 15.dp, frame, Stroke(width = 5.dp.toPx()))
    }
}

/** A gingham tablecloth: see-through bands both ways, darker where they cross. */
class GinghamDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() {
        drawGingham(palette.detail, band = 12.dp.toPx())
    }
}

/** A night sky full of stars. */
class StarryDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() {
        drawStars(seed = 5, color = palette.detail)
    }
}
