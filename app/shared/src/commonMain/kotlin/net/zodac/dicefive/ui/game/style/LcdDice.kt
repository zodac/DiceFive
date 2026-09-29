package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** The seven segments of an LCD digit: top, upper right, lower right, bottom, lower left, upper left, middle. */
private enum class Segment { A, B, C, D, E, F, G }

/** Which segments light up for each face, 1..6. */
private val LIT_SEGMENTS: Map<Int, Set<Segment>> = mapOf(
    1 to setOf(Segment.B, Segment.C),
    2 to setOf(Segment.A, Segment.B, Segment.G, Segment.E, Segment.D),
    3 to setOf(Segment.A, Segment.B, Segment.G, Segment.C, Segment.D),
    4 to setOf(Segment.F, Segment.G, Segment.B, Segment.C),
    5 to setOf(Segment.A, Segment.F, Segment.G, Segment.C, Segment.D),
    6 to setOf(Segment.A, Segment.F, Segment.G, Segment.E, Segment.C, Segment.D),
)

// How rounded a Lcd die's corners are, as a percentage of its size - for drawing it and its shadow alike.
private const val LCD_CORNER_PERCENT = 14

/**
 * A die with a little LCD screen for a face, showing its value as a 7-segment digit. The unlit
 * segments stay faintly visible, the way they do on a real display, and [glow] adds a soft halo
 * round the lit ones for a backlit neon look.
 */
class LcdDiceStyle(
    override val id: String,
    private val body: Color,
    private val screen: Color,
    private val lit: Color,
    private val glow: Boolean,
) : DiceStyle, Swatched {
    override val swatch: Color = if (glow) lit else body

    override fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = RoundedCornerShape(LCD_CORNER_PERCENT)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) = StyledDie(
        value = value,
        held = held,
        modifier = modifier,
        face = Brush.linearGradient(listOf(body, body)),
        edge = screen,
        pipColor = lit,
        cornerPercent = LCD_CORNER_PERCENT,
        pipShape = PipShape.CUSTOM,
        customPips = { face -> drawDigit(face) },
    ) {
        // The screen: a slightly different panel inset into the body.
        val inset = size.minDimension * 0.1f
        drawRoundRect(
            screen,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(size.minDimension * 0.06f),
        )
    }

    private fun DrawScope.drawDigit(face: Int) {
        val on = LIT_SEGMENTS[face].orEmpty()
        val thickness = size.minDimension * 0.11f
        val width = size.width * 0.5f
        val height = size.height * 0.82f
        val left = (size.width - width) / 2
        val top = (size.height - height) / 2
        for (segment in Segment.entries) {
            val shape = segmentPath(segment, left, top, width, height, thickness)
            if (segment in on) {
                if (glow) drawPath(shape, lit.copy(alpha = 0.25f), style = Stroke(width = thickness * 0.9f))
                drawPath(shape, lit)
            } else {
                drawPath(shape, lit.copy(alpha = 0.07f))
            }
        }
    }
}

/**
 * One [segment] of a digit filling the box at ([left], [top]) of [width] x [height]: a bar
 * [thickness] thick with pointed ends, stopping short of its neighbours so each segment reads on
 * its own.
 */
private fun segmentPath(segment: Segment, left: Float, top: Float, width: Float, height: Float, thickness: Float): Path {
    val gap = thickness * 0.25f
    val right = left + width
    val bottom = top + height
    val middle = top + height / 2
    return when (segment) {
        Segment.A -> horizontalBar(left + gap, right - gap, top + thickness / 2, thickness)
        Segment.G -> horizontalBar(left + gap, right - gap, middle, thickness)
        Segment.D -> horizontalBar(left + gap, right - gap, bottom - thickness / 2, thickness)
        Segment.F -> verticalBar(left + thickness / 2, top + gap, middle - gap, thickness)
        Segment.B -> verticalBar(right - thickness / 2, top + gap, middle - gap, thickness)
        Segment.E -> verticalBar(left + thickness / 2, middle + gap, bottom - gap, thickness)
        Segment.C -> verticalBar(right - thickness / 2, middle + gap, bottom - gap, thickness)
    }
}

private fun horizontalBar(x1: Float, x2: Float, y: Float, thickness: Float) = Path().apply {
    val half = thickness / 2
    moveTo(x1, y)
    lineTo(x1 + half, y - half)
    lineTo(x2 - half, y - half)
    lineTo(x2, y)
    lineTo(x2 - half, y + half)
    lineTo(x1 + half, y + half)
    close()
}

private fun verticalBar(x: Float, y1: Float, y2: Float, thickness: Float) = Path().apply {
    val half = thickness / 2
    moveTo(x, y1)
    lineTo(x + half, y1 + half)
    lineTo(x + half, y2 - half)
    lineTo(x, y2)
    lineTo(x - half, y2 - half)
    lineTo(x - half, y1 + half)
    close()
}
