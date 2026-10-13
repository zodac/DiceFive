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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

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

/**
 * Wooden floorboards - more colours of the Wood mat, to go with the Wood dice: four planks across the
 * tray, each a slightly different shade, with a wavy [MatPalette.detail] grain, the odd knot, and
 * butt joints staggered from plank to plank. Painted once.
 */
class WoodPlankDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() = drawCachedSurface("mat:$id") {
        val random = Random(id.hashCode())
        val plank = size.height / 4f
        val seam = 1.dp.toPx()
        for (row in 0 until 4) {
            val top = row * plank
            // Each plank a shade lighter or darker than the next.
            drawRect(lerp(palette.top, palette.bottom, random.nextFloat()).copy(alpha = 0.35f), Offset(0f, top), Size(size.width, plank))
            repeat(5) {
                val y = top + plank * (0.12f + random.nextFloat() * 0.76f)
                val phase = random.nextFloat() * 6.28f
                val wave = (40 + random.nextInt(50)).dp.toPx()
                val grain = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    while (x <= size.width) {
                        x += 5.dp.toPx()
                        lineTo(x, y + sin(x / wave + phase) * 1.6.dp.toPx())
                    }
                }
                drawPath(grain, palette.detail.copy(alpha = 0.18f + random.nextFloat() * 0.18f), style = Stroke(width = (0.6f + random.nextFloat()).dp.toPx()))
            }
            if (random.nextFloat() < 0.6f) {
                val knot = Offset(size.width * random.nextFloat(), top + plank * (0.3f + random.nextFloat() * 0.4f))
                val r = (2.5f + random.nextFloat() * 2f).dp.toPx()
                drawOval(palette.detail.copy(alpha = 0.45f), knot - Offset(r * 1.6f, r), Size(r * 3.2f, r * 2f))
                drawOval(palette.detail.copy(alpha = 0.3f), knot - Offset(r * 2.6f, r * 1.6f), Size(r * 5.2f, r * 3.2f), style = Stroke(width = 0.8.dp.toPx()))
            }
            // A butt joint somewhere along it, staggered from the plank above.
            val joint = size.width * (0.2f + (row * 0.37f + random.nextFloat() * 0.2f) % 0.6f)
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(joint, top), Offset(joint, top + plank), strokeWidth = seam)
            if (row > 0) {
                drawLine(Color.Black.copy(alpha = 0.35f), Offset(0f, top), Offset(size.width, top), strokeWidth = seam)
                drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, top + seam), Offset(size.width, top + seam), strokeWidth = seam)
            }
        }
    }
}
