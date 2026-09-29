package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    /** Whether the pattern moves, so it's redrawn on every frame with the time in seconds. */
    protected open val animated: Boolean = false

    protected abstract fun DrawScope.drawPattern()

    protected open fun DrawScope.drawPattern(seconds: Float) = drawPattern()

    @Composable
    final override fun DiceTrayDecoration(modifier: Modifier) {
        var seconds by remember { mutableFloatStateOf(0f) }
        if (animated) TwinkleClock { seconds = it }
        Canvas(modifier = modifier) { drawPattern(seconds) }
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

/** A gingham tablecloth: see-through bands both ways, darker where they cross. */
class GinghamDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() {
        drawGingham(palette.detail, band = 12.dp.toPx())
    }
}

/** A night sky full of stars. */
class StarryDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override val animated: Boolean = true

    override fun DrawScope.drawPattern() = drawPattern(seconds = 0f)

    override fun DrawScope.drawPattern(seconds: Float) {
        drawStars(seed = 5, color = palette.detail, seconds = seconds)
    }
}
