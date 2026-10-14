package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A background's colours: [top] and [bottom] of its gradient (the centre and edge, for a radial
 * one) and [detail] for any pattern over it. Every pattern is kept faint - the scorecard sits on top
 * and has to stay the thing that reads.
 */
data class BackgroundPalette(val top: Color, val bottom: Color, val detail: Color = Color.White)

/** A light pooled in the middle of the table, falling off to dark edges. */
class SpotlightBackground(override val id: String, palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.radialGradient(listOf(palette.top, palette.bottom))
}

/** Fine vertical pinstripes, like a suit. */
class PinstripeBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() {
        val gap = 14.dp.toPx()
        val stripe = palette.detail.copy(alpha = 0.07f)
        var x = gap / 2
        while (x < size.width) {
            drawLine(stripe, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            x += gap
        }
    }
}

/** Horizontal wooden planks with a faint wavy grain. */
class PlanksBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() {
        val plank = 30.dp.toPx()
        val random = Random(3)
        val grain = palette.detail.copy(alpha = 0.06f)
        var top = 0f
        while (top < size.height) {
            repeat(2) {
                val y = top + plank * (0.3f + random.nextFloat() * 0.4f)
                val phase = random.nextFloat() * 2f * PI.toFloat()
                val line = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    while (x <= size.width) {
                        lineTo(x, y + sin(x / 40.dp.toPx() + phase) * 2.dp.toPx())
                        x += 6.dp.toPx()
                    }
                }
                drawPath(line, grain, style = Stroke(width = 1.dp.toPx()))
            }
            top += plank
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(0f, top), Offset(size.width, top), strokeWidth = 1.dp.toPx())
        }
    }
}

/**
 * Leather - to go with the Leather dice, cup and mat: a faint pebbled hide over the gradient, stitched
 * round just inside the edge in [BackgroundPalette.detail]. Painted once.
 */
class LeatherBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() {
        drawCachedSurface("background:$id") {
            val pebble = 1.4.dp.toPx()
            paintPebbledHide(palette.top, palette.bottom, Random(9), pebbles = (size.width * size.height / (pebble * pebble * 6f)).toInt(), pebbleSize = pebble, alpha = 0.7f)
            val inset = 6.dp.toPx()
            val stitch = Stroke(width = 1.3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            val box = Size(size.width - inset * 2, size.height - inset * 2)
            drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(inset, inset + 1.dp.toPx()), box, CornerRadius(10.dp.toPx()), style = stitch)
            insetOutline(6.dp, 10.dp, palette.detail.copy(alpha = 0.55f), stitch)
        }
    }
}

/** A gingham tablecloth, to go with the Gingham mat. */
class GinghamBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() {
        drawGingham(palette.detail, band = 14.dp.toPx())
    }
}

/** A night sky full of stars. */
class StarryBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    private var seconds by mutableFloatStateOf(0f)

    @Composable
    override fun Animate() {
        TwinkleClock { seconds = it }
    }

    override fun DrawScope.drawScoreAreaDecoration() {
        drawStars(seed = 9, color = palette.detail, seconds = seconds)
    }
}

/** A faint honeycomb of hexagon outlines. */
class HoneycombBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() {
        val radius = 16.dp.toPx()
        val rowHeight = radius * sqrt(3f)
        val outline = Stroke(width = 1.dp.toPx())
        val color = palette.detail.copy(alpha = 0.07f)
        var column = 0
        var cx = 0f
        while (cx < size.width + radius) {
            var cy = if (column % 2 == 0) 0f else rowHeight / 2
            while (cy < size.height + rowHeight) {
                val hexagon = polygonPath(hexagonPoints(Offset(cx, cy), radius))
                drawPath(hexagon, color, style = outline)
                cy += rowHeight
            }
            cx += radius * 1.5f
            column++
        }
    }
}

/** Alternating rays fanning out from above the top edge. */
class SunburstBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() {
        val centre = Offset(size.width / 2, -size.height * 0.1f)
        val reach = hypot(size.width, size.height) * 1.2f
        val rays = 28
        val step = 2f * PI.toFloat() / rays
        val color = palette.detail.copy(alpha = 0.05f)
        for (ray in 0 until rays step 2) {
            val from = step * ray
            val to = from + step
            val wedge = Path().apply {
                moveTo(centre.x, centre.y)
                lineTo(centre.x + reach * cos(from), centre.y + reach * sin(from))
                lineTo(centre.x + reach * cos(to), centre.y + reach * sin(to))
                close()
            }
            drawPath(wedge, color)
        }
    }
}
