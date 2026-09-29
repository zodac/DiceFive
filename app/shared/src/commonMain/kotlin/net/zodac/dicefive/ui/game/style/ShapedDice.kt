package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

// How deep the cube's top and side faces are drawn, as a fraction of its size.
private const val CUBE_DEPTH = 0.2f

/**
 * A die drawn as a solid cube seen from above and to the right: its value on the front face, and
 * two neighbouring faces - never the opposite one, which always adds up to 7 - on the lit top and
 * shaded side. Mid-toss it tumbles as that same solid ([TossedDie]), not as a set of flat faces.
 */
class CubeDiceStyle(
    override val id: String,
    private val front: Color,
    private val frontShade: Color,
    private val top: Color,
    private val side: Color,
    private val pip: Color,
    private val rounded: Boolean = false,
) : DiceStyle, Swatched {
    override val swatch: Color = front

    // The same outline the die is drawn in, rounded or not, so its shadow matches the silhouette.
    private val silhouette = GenericShape { size, _ -> addPath(cubeOutline(size, rounded)) }

    override fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = silhouette

    override fun topFace(value: Int): Int = neighbouringFaces(value).first

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val (topValue, sideValue) = neighbouringFaces(value)
        Canvas(modifier = modifier.dieShadow(silhouette)) {
            drawCube(front = value, top = topValue, side = sideValue, tipDegrees = 0f, held = held)
        }
    }

    /**
     * The cube rolling up the tray and back, turning about its left-right axis: the front face tips
     * up and over into the top as the one beneath comes round to the front, while the side face
     * stays put. [ring] and [finalTurns] are the faces round that axis and where it stops - see
     * [TossPath] - and the side is whichever face is left over, the one it shows at rest if it can.
     */
    @Composable
    override fun TossedDie(roll: Float, finalTurns: Int, ring: List<Int>, modifier: Modifier) {
        if (ring.size < 4) {
            // Not rolling round any axis - slid off the mat face-up, say.
            Die(value = ring.first(), held = false, modifier = modifier)
            return
        }
        fun faceAt(turn: Int) = ring[(turn - finalTurns).mod(ring.size)]
        val base = floor(roll).toInt()
        val sideValue = neighbouringFaces(ring.first()).second.takeIf { it !in ring } ?: (1..6).first { it !in ring }
        Canvas(modifier = modifier) {
            drawCube(
                front = faceAt(base),
                top = faceAt(base - 1),
                side = sideValue,
                tipDegrees = (roll - base) * 90f,
                held = false,
                bottom = faceAt(base + 1),
            )
        }
    }

    /**
     * The cube in 3D, tipped [tipDegrees] about its left-right axis from lying with [front] facing
     * you, [top] above it and [side] to its right, and projected the way it's always drawn: every
     * face turned towards you, filled, shaded between the front's colours and the top's as it tips
     * over, and pipped with its pips laid flat on it; then outlined, in gold when [held].
     */
    private fun DrawScope.drawCube(front: Int, top: Int, side: Int, tipDegrees: Float, held: Boolean, bottom: Int = 7 - top) {
        val s = size.minDimension
        val d = s * CUBE_DEPTH
        val span = s - d
        val radians = tipDegrees * PI.toFloat() / 180f
        fun project(p: CubePoint) = Offset(p.x * span + p.z * d, d + p.y * span - p.z * d)
        // Seen along this, so a face whose normal points against it is turned towards you.
        val sight = CubePoint(-d, d, span)

        val faces = listOf(
            CubeFace(front, corner = CubePoint(0f, 0f, 0f), across = CubePoint(1f, 0f, 0f), down = CubePoint(0f, 1f, 0f), normal = CubePoint(0f, 0f, -1f)),
            CubeFace(top, corner = CubePoint(0f, 0f, 1f), across = CubePoint(1f, 0f, 0f), down = CubePoint(0f, 0f, -1f), normal = CubePoint(0f, -1f, 0f)),
            CubeFace(bottom, corner = CubePoint(0f, 1f, 0f), across = CubePoint(1f, 0f, 0f), down = CubePoint(0f, 0f, 1f), normal = CubePoint(0f, 1f, 0f)),
            CubeFace(7 - front, corner = CubePoint(0f, 1f, 1f), across = CubePoint(1f, 0f, 0f), down = CubePoint(0f, -1f, 0f), normal = CubePoint(0f, 0f, 1f)),
            CubeFace(side, corner = CubePoint(1f, 0f, 0f), across = CubePoint(0f, 0f, 1f), down = CubePoint(0f, 1f, 0f), normal = CubePoint(1f, 0f, 0f)),
        ).map { it.tipped(radians) }

        val pipRadius = CUBE_PIP_RADIUS / (1f - CUBE_DEPTH)
        fun drawFaces() {
            for (face in faces) {
                if (face.normal dot sight > -0.001f * s) continue
                fun at(u: Float, v: Float) = project(face.corner + face.across * u + face.down * v)
                val isSide = face.normal.x > 0.5f
                // How far over towards being the top a front-to-top face has tipped, for its shading.
                val up = (-face.normal.y).coerceAtLeast(0f)
                val towards = (-face.normal.z).coerceAtLeast(0f)
                val topness = if (up + towards > 0f) up / (up + towards) else 0f
                val fill = if (isSide) {
                    SolidColor(this@CubeDiceStyle.side)
                } else {
                    Brush.linearGradient(listOf(lerp(this@CubeDiceStyle.front, this@CubeDiceStyle.top, topness), lerp(frontShade, this@CubeDiceStyle.top, topness)))
                }
                drawPath(quad(at(0f, 0f), at(1f, 0f), at(1f, 1f), at(0f, 1f)), fill)
                val pipColour = pip.copy(alpha = if (isSide) 0.7f else 1f - topness * 0.2f)
                for (p in pipLayout(face.value)) {
                    val u = CUBE_PIP_INSET + p.x * (1f - CUBE_PIP_INSET * 2)
                    val v = CUBE_PIP_INSET + p.y * (1f - CUBE_PIP_INSET * 2)
                    val dot = Path().apply {
                        for (step in 0 until 20) {
                            val angle = step * 2f * PI.toFloat() / 20f
                            val point = at(u + cos(angle) * pipRadius, v + sin(angle) * pipRadius)
                            if (step == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                        }
                        close()
                    }
                    drawPath(dot, pipColour)
                }
            }
        }

        val corners = listOf(0f, 1f).flatMap { x -> listOf(0f, 1f).flatMap { y -> listOf(0f, 1f).map { z -> CubePoint(x, y, z) } } }
        val outline = convexHull(corners.map { project(it.tipped(radians, about = 0.5f)) })
        val outlinePath = polygonPath(outline, if (rounded) s * CUBE_ROUNDING else 0f)
        // Rounded: the faces are cut to the softened silhouette, so its corners are round however the cube is tipped.
        if (rounded) clipPath(outlinePath) { drawFaces() } else drawFaces()
        drawPath(
            outlinePath,
            color = if (held) GoldAccent else frontShade,
            style = Stroke(width = if (held) 2.dp.toPx() else 1.dp.toPx(), join = StrokeJoin.Round),
        )
    }
}

// How round a rounded cube's corners are, as a fraction of its size.
private const val CUBE_ROUNDING = 0.14f

// Each pip's radius, as a fraction of the whole die, and how far in from a face's edges its pips sit,
// as a fraction of the face.
private const val CUBE_PIP_RADIUS = 0.075f
private const val CUBE_PIP_INSET = 0.12f

/** A point or direction in the cube's own space: x to the right, y down and z away from you, the cube spanning 0..1 each way. */
private data class CubePoint(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: CubePoint) = CubePoint(x + o.x, y + o.y, z + o.z)
    operator fun times(k: Float) = CubePoint(x * k, y * k, z * k)
    infix fun dot(o: CubePoint) = x * o.x + y * o.y + z * o.z

    /**
     * Turned [radians] about the left-right axis through [about] (the cube's centre for a point, 0
     * for a direction), the front tipping up and away.
     */
    fun tipped(radians: Float, about: Float = 0f): CubePoint {
        val c = cos(radians)
        val s = sin(radians)
        val y0 = y - about
        val z0 = z - about
        return CubePoint(x, about + y0 * c + z0 * s, about - y0 * s + z0 * c)
    }
}

/** One face of the cube showing [value]: a [corner], the edges its pips are laid out [across] and [down], and which way it faces. */
private class CubeFace(val value: Int, val corner: CubePoint, val across: CubePoint, val down: CubePoint, val normal: CubePoint) {
    fun tipped(radians: Float) = CubeFace(value, corner.tipped(radians, about = 0.5f), across.tipped(radians), down.tipped(radians), normal.tipped(radians))
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

/** The cube's six-sided silhouette in a [size] box, with its corners [rounded] off if asked. */
private fun cubeOutline(size: Size, rounded: Boolean = false): Path {
    val s = size.minDimension
    val d = s * CUBE_DEPTH
    val corners = listOf(Offset(0f, d), Offset(d, 0f), Offset(s, 0f), Offset(s, s - d), Offset(s - d, s), Offset(0f, s))
    return polygonPath(corners, if (rounded) s * CUBE_ROUNDING else 0f)
}

/** The closed polygon through [points], each corner cut back and curved by up to [rounding] (never more than half an edge). */
private fun polygonPath(points: List<Offset>, rounding: Float) = Path().apply {
    if (rounding <= 0f) {
        points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
        return@apply
    }
    val n = points.size
    fun towards(from: Offset, to: Offset): Offset {
        val gap = to - from
        val length = gap.getDistance()
        return if (length == 0f) from else from + gap * (minOf(rounding, length / 2f) / length)
    }
    for (i in 0 until n) {
        val corner = points[i]
        val entry = towards(corner, points[(i + n - 1) % n])
        val exit = towards(corner, points[(i + 1) % n])
        if (i == 0) moveTo(entry.x, entry.y) else lineTo(entry.x, entry.y)
        quadraticTo(corner.x, corner.y, exit.x, exit.y)
    }
    close()
}

// How rounded a Misprint die's corners are, as a percentage of its size - for drawing it and its shadow alike.
private const val MISPRINT_CORNER_PERCENT = 16

/**
 * A misprinted die: a wobbly, doubled outline in [ink] on [paper], and every pip printed well off
 * its proper spot - at least a whole pip's width away, in its own random direction - though still
 * roughly where it belongs, and never overlapping another, so the count still reads. Seeded by
 * face, so each face is always misprinted the same way - and differently for each colour ([seed]).
 */
class MisprintDiceStyle(override val id: String, private val paper: Color, private val ink: Color, private val seed: Int) : DiceStyle, Swatched {
    override val swatch: Color = paper

    override fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = RoundedCornerShape(MISPRINT_CORNER_PERCENT)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) = StyledDie(
        value = value,
        held = held,
        modifier = modifier,
        face = Brush.linearGradient(listOf(paper, paper)),
        edge = Color.Transparent,
        pipColor = ink,
        cornerPercent = MISPRINT_CORNER_PERCENT,
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
        for (centre in misprintedPips(face, size.minDimension, radius, seed)) {
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
private fun misprintedPips(face: Int, side: Float, radius: Float, styleSeed: Int): List<Offset> {
    val random = Random(face * 31 + styleSeed * 9973)
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
