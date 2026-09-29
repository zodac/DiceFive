package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

// How deep the cube's top and side faces are drawn, as a fraction of its size.
private const val CUBE_DEPTH = 0.2f

/**
 * A die drawn as a solid cube seen from above and to the right: its value on the front face, and
 * two neighbouring faces - never the opposite one, which always adds up to 7 - on the lit top and
 * shaded side.
 */
class CubeDiceStyle(
    override val id: String,
    private val front: Color,
    private val frontShade: Color,
    private val top: Color,
    private val side: Color,
    private val pip: Color,
) : DiceStyle, Swatched {
    override val swatch: Color = front

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val silhouette = GenericShape { size, _ -> addPath(cubeOutline(size)) }
        Canvas(modifier = modifier.dieShadow(silhouette)) {
            val s = size.minDimension
            val d = s * CUBE_DEPTH
            // Front, top and right faces as corner points; see cubeOutline for the whole shape.
            val frontLeftTop = Offset(0f, d)
            val frontRightTop = Offset(s - d, d)
            val backLeftTop = Offset(d, 0f)
            val backRightTop = Offset(s, 0f)
            val frontRightBottom = Offset(s - d, s)
            val backRightBottom = Offset(s, s - d)

            drawRect(Brush.linearGradient(listOf(front, frontShade)), topLeft = frontLeftTop, size = Size(s - d, s - d))
            drawPath(quad(frontLeftTop, backLeftTop, backRightTop, frontRightTop), top)
            drawPath(quad(frontRightTop, backRightTop, backRightBottom, frontRightBottom), side)

            val (topValue, sideValue) = neighbouringFaces(value)
            val pipRadius = s * 0.075f
            // Front: an ordinary face, inset from its edges.
            for (p in pipLayout(value)) {
                val inset = (s - d) * 0.12f
                val span = (s - d) - inset * 2
                drawCircle(pip, pipRadius, Offset(inset + p.x * span, d + inset + p.y * span))
            }
            // Top and side: the same layout mapped onto each slanted face, each pip squashed to match.
            for (p in pipLayout(topValue)) {
                val u = 0.15f + p.x * 0.7f
                val v = 0.15f + p.y * 0.7f
                val centre = backLeftTop + (backRightTop - backLeftTop) * u + (frontLeftTop - backLeftTop) * v
                drawOval(pip.copy(alpha = 0.8f), topLeft = centre - Offset(pipRadius * 0.9f, pipRadius * 0.35f), size = Size(pipRadius * 1.8f, pipRadius * 0.7f))
            }
            for (p in pipLayout(sideValue)) {
                val u = 0.15f + p.x * 0.7f
                val v = 0.15f + p.y * 0.7f
                val centre = frontRightTop + (backRightTop - frontRightTop) * u + (frontRightBottom - frontRightTop) * v
                drawOval(pip.copy(alpha = 0.7f), topLeft = centre - Offset(pipRadius * 0.35f, pipRadius * 0.9f), size = Size(pipRadius * 0.7f, pipRadius * 1.8f))
            }

            drawPath(
                cubeOutline(size),
                color = if (held) GoldAccent else frontShade,
                style = Stroke(width = if (held) 2.dp.toPx() else 1.dp.toPx()),
            )
        }
    }
}

/** A top and a side face that can both sit next to [front] on a real die: neither is it or its opposite. */
private fun neighbouringFaces(front: Int): Pair<Int, Int> {
    val top = (1..6).first { it != front && it != 7 - front }
    val side = (1..6).first { it != front && it != 7 - front && it != top && it != 7 - top }
    return top to side
}

private fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    lineTo(d.x, d.y)
    close()
}

/** The cube's six-sided silhouette in a [size] box. */
private fun cubeOutline(size: Size): Path {
    val s = size.minDimension
    val d = s * CUBE_DEPTH
    return Path().apply {
        moveTo(0f, d)
        lineTo(d, 0f)
        lineTo(s, 0f)
        lineTo(s, s - d)
        lineTo(s - d, s)
        lineTo(0f, s)
        close()
    }
}

/**
 * A misprinted die: a wobbly, doubled outline in [ink] on [paper], and every pip printed well off
 * its proper spot - at least a whole pip's width away, in its own random direction - though still
 * roughly where it belongs, and never overlapping another, so the count still reads. Seeded by
 * face, so each face is always misprinted the same way.
 */
class MisprintDiceStyle(override val id: String, private val paper: Color, private val ink: Color) : DiceStyle, Swatched {
    override val swatch: Color = paper

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) = StyledDie(
        value = value,
        held = held,
        modifier = modifier,
        face = Brush.linearGradient(listOf(paper, paper)),
        edge = Color.Transparent,
        pipColor = ink,
        cornerPercent = 16,
        pipShape = PipShape.CUSTOM,
        customPips = { face -> drawMisprintedPips(face) },
    ) {
        val line = Stroke(width = 1.4.dp.toPx())
        val inset = 2.dp.toPx()
        val corner = CornerRadius(size.minDimension * 0.16f)
        drawRoundRect(ink.copy(alpha = 0.85f), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), corner, style = line)
        drawRoundRect(
            ink.copy(alpha = 0.5f),
            Offset(inset + 0.8.dp.toPx(), inset - 0.6.dp.toPx()),
            Size(size.width - inset * 2 - 1.dp.toPx(), size.height - inset * 2 + 0.8.dp.toPx()),
            corner,
            style = Stroke(width = 1.dp.toPx()),
        )
    }

    private fun DrawScope.drawMisprintedPips(face: Int) {
        val radius = size.minDimension * 0.1f
        for (centre in misprintedPips(face, size.minDimension, radius)) {
            drawCircle(ink.copy(alpha = 0.9f), radius, centre)
        }
    }
}

/**
 * Where [face]'s pips land on a misprinted die [side] across: each one moved between one and one
 * and a half pip-spacings from its proper spot, in a random direction, staying on the face and
 * clear of the pips already placed. If no such spot turns up for a pip, it takes the one that came
 * closest to fitting.
 */
private fun misprintedPips(face: Int, side: Float, radius: Float): List<Offset> {
    val random = Random(face * 31)
    val spacing = side * 0.24f
    val low = radius * 1.1f
    val high = side - radius * 1.1f
    val placed = mutableListOf<Offset>()
    for (proper in pipLayout(face)) {
        val home = Offset(proper.x * side, proper.y * side)
        var best = home
        var bestClearance = Float.NEGATIVE_INFINITY
        for (attempt in 0 until 80) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val distance = spacing * (1f + random.nextFloat() * 0.5f)
            val candidate = home + Offset(cos(angle) * distance, sin(angle) * distance)
            if (candidate.x !in low..high || candidate.y !in low..high) continue
            val clearance = placed.minOfOrNull { (it - candidate).getDistance() } ?: Float.MAX_VALUE
            if (clearance > bestClearance) {
                best = candidate
                bestClearance = clearance
            }
            if (clearance >= radius * 2.3f) break
        }
        placed += best
    }
    return placed
}
