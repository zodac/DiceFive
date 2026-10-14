package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.game.DICE_TOSS_MILLIS
import net.zodac.dicefive.ui.theme.GoldAccent

// Light falls from the top left, in screen space - so a die's lit edges stay lit whichever way it lies.
private val POLYGON_LIGHT = Offset(-0.6f, -0.8f)

// How many whole turns a flat die spins through in a toss, slowing to a stop on its resting twist.
private const val POLYGON_TOSS_TURNS = 1.6f

/**
 * A flat die of [sides] equal sides, [radius] (centre to corner, as a fraction of the die's square)
 * across, its centre [drop] below the square's (so a triangle, heavier at its base, sits in the middle
 * of its square), its first corner at [firstCorner] degrees. [restTwist] is how far either way it lies
 * turned at rest, different for every die and roll.
 */
internal class PolygonShape(
    val sides: Int,
    val radius: Float,
    val drop: Float,
    val firstCorner: Float,
    val restTwist: Float,
) {
    /** How it lies, in degrees: spinning through a toss [tumbleMillis] in, or at its resting twist (null). */
    fun angle(value: Int, dieIndex: Int, tumbleMillis: Float?): Float {
        val rest = firstCorner + (Random(dieIndex * 31 + value).nextFloat() * 2f - 1f) * restTwist
        if (tumbleMillis == null) return rest
        val remaining = (1f - (tumbleMillis / DICE_TOSS_MILLIS).coerceIn(0f, 1f)).let { it * it }
        val direction = if (Random(dieIndex * 17 + 5).nextBoolean()) 1f else -1f
        return rest + direction * POLYGON_TOSS_TURNS * 360f * remaining
    }

    fun centre(size: Size): Offset = Offset(size.width / 2f, size.height / 2f + drop * size.minDimension)

    /** Its corners at [size], turned to [degrees]. */
    fun corners(size: Size, degrees: Float, scale: Float = 1f): List<Offset> {
        val centre = centre(size)
        val r = radius * size.minDimension * scale
        return (0 until sides).map { i ->
            val a = (degrees + 360f * i / sides) * PI.toFloat() / 180f
            centre + Offset(cos(a) * r, sin(a) * r)
        }
    }

    /** [local] (a point in the die's own frame, in units of its radius) where it lands at [size], turned to [degrees]. */
    fun place(size: Size, degrees: Float, local: Offset): Offset {
        // The die's own +x runs out through its first corner, wherever that's turned to.
        val a = degrees * PI.toFloat() / 180f
        val r = radius * size.minDimension
        return centre(size) + Offset((local.x * cos(a) - local.y * sin(a)) * r, (local.x * sin(a) + local.y * cos(a)) * r)
    }

    fun outline(degrees: Float): Shape = GenericShape { size, _ ->
        val points = corners(size, degrees)
        moveTo(points[0].x, points[0].y)
        for (p in points.drop(1)) lineTo(p.x, p.y)
        close()
    }
}

/** How lit a surface facing [outwards] (screen space) is, 0 (away from the light) to 1 (straight at it). */
private fun litness(outwards: Offset): Float {
    val n = outwards / outwards.getDistance()
    return ((n.x * POLYGON_LIGHT.x + n.y * POLYGON_LIGHT.y) * 0.5f + 0.5f).coerceIn(0f, 1f)
}

// The six pip spots of a hexagon, pointing out of its corners, and how far out they sit (in units of its radius).
private const val HEX_PIP_RING = 0.52f
private const val HEX_PIP_RADIUS = 0.135f

/** A hexagon's pips for [value], in its own frame: a ring that follows its corners, filled in as the value grows. */
private fun hexPips(value: Int): List<Offset> {
    fun ring(vararg corners: Int) = corners.map { c ->
        val a = c * PI.toFloat() / 3f
        Offset(cos(a) * HEX_PIP_RING, sin(a) * HEX_PIP_RING)
    }
    return when (value) {
        1 -> listOf(Offset.Zero)
        2 -> ring(2, 5)
        3 -> ring(1, 3, 5)
        4 -> ring(1, 2, 4, 5)
        5 -> ring(1, 2, 4, 5) + Offset.Zero
        else -> ring(0, 1, 2, 3, 4, 5)
    }
}

/**
 * Bestagon dice: flat hexagonal tiles with a bevelled edge, each of its six edge facets catching the
 * light by which way it faces, and pips laid out to suit the shape - a 6 is a ring of pips, one
 * pointing into each corner, 3 a triangle of them, 4 a rectangle, 5 the rectangle round a centre pip.
 * A toss spins it flat across the mat, slowing onto a resting twist of its own.
 */
class BestagonDiceStyle(
    override val id: String,
    private val light: Color,
    private val dark: Color,
    private val pip: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = light
    override val bodyColor: Color = dark
    override val tumblesItself: Boolean = true
    override val lockedChainReach: Float = 0.8f

    private val hexagon = PolygonShape(sides = 6, radius = 0.49f, drop = 0f, firstCorner = 0f, restTwist = 14f)

    override fun recoloured(palette: DieColourPalette): DiceStyle = BestagonDiceStyle(id, palette.diceTop, palette.diceBottom, palette.pip, palette.heldRing)

    override fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = hexagon.outline(hexagon.angle(value, dieIndex, tumbleMillis))

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val dieIndex = LocalDieIndex.current
        val tumbleMillis = LocalDieTumbleMillis.current
        val angle = hexagon.angle(value, dieIndex, tumbleMillis)
        Canvas(modifier = modifier.dieShadow(hexagon.outline(angle))) {
            drawBevelledPolygon(hexagon, angle, light, dark, bevel = 0.8f)
            val r = hexagon.radius * size.minDimension
            for (spot in hexPips(value)) {
                val at = hexagon.place(size, angle, spot)
                drawSunkenPip(at, HEX_PIP_RADIUS * r, pip)
            }
            drawPolygonEdge(hexagon, angle, held)
        }
    }

    private fun DrawScope.drawPolygonEdge(shape: PolygonShape, angle: Float, held: Boolean) {
        drawPath(
            polygonPath(shape.corners(size, angle)),
            if (held) heldRing else lerp(dark, Color.Black, 0.35f),
            style = Stroke(width = if (held) 2.dp.toPx() else 0.8.dp.toPx(), join = StrokeJoin.Round),
        )
    }
}

/**
 * A polygon tile with a bevelled edge: its flat top inset to [bevel] of its size, lit across from
 * [light] to [dark], and a band of facets round it, each shaded by which way it faces the light.
 */
private fun DrawScope.drawBevelledPolygon(shape: PolygonShape, angle: Float, light: Color, dark: Color, bevel: Float) {
    val outer = shape.corners(size, angle)
    val inner = shape.corners(size, angle, scale = bevel)
    val centre = shape.centre(size)
    for (i in outer.indices) {
        val j = (i + 1) % outer.size
        val facet = polygonPath(listOf(outer[i], outer[j], inner[j], inner[i]))
        val outwards = (outer[i] + outer[j]) / 2f - centre
        drawPath(facet, lerp(lerp(dark, Color.Black, 0.25f), lerp(light, Color.White, 0.35f), litness(outwards)))
    }
    val r = shape.radius * size.minDimension
    drawPath(
        polygonPath(inner),
        Brush.linearGradient(listOf(lerp(light, Color.White, 0.12f), light, dark), start = centre + Offset(-r, -r), end = centre + Offset(r, r)),
    )
}

/** A pip pressed into the face: [colour], with a dark lip along its top where it's in shadow. */
private fun DrawScope.drawSunkenPip(at: Offset, radius: Float, colour: Color) {
    drawCircle(colour, radius, at)
    drawCircle(Color.Black.copy(alpha = 0.3f), radius, at, style = Stroke(width = radius * 0.22f))
    drawCircle(Color.White.copy(alpha = 0.18f), radius * 0.35f, at + Offset(-radius * 0.3f, -radius * 0.3f))
}
