package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import net.zodac.dicefive.ui.common.LocalReduceMotion

// The container, in grid units (SQUAT, 76 x 66), as a box in its own space: x along its length (its
// door end negative, on the left), y up from the table, z forward. It's long and low, so it lies along
// the squat grid's width, turned CONTAINER_YAW (negative, so its left end shows) so its doors show as
// well as its long side. It doesn't tip: it opens its doors instead.
private const val CONTAINER_HALF_LENGTH = 27f
private const val CONTAINER_HALF_DEPTH = 11f
private const val CONTAINER_HEIGHT = 23f
private const val CONTAINER_CENTRE_X = 38f
private const val CONTAINER_BASE_Y = 50f
private const val CONTAINER_YAW_DEGREES = -40f
private const val CONTAINER_ELEVATION_DEGREES = 22f
// The steel frame round every edge, and the corrugation's pitch along the side.
private const val RAIL = 1.3f
private const val RIB_PITCH = 1.7f
// The door end, how far each door swings out on its hinges once the dice are poured, and how
// quickly they shut for the next shake.
private const val DOOR_END_X = -CONTAINER_HALF_LENGTH
private const val DOOR_OPEN_DEGREES = 115f
private const val DOOR_CLOSE_MILLIS = 110

private val ContainerYawSin = sin(CONTAINER_YAW_DEGREES * PI.toFloat() / 180f)
private val ContainerYawCos = cos(CONTAINER_YAW_DEGREES * PI.toFloat() / 180f)
private val ContainerElevationSin = sin(CONTAINER_ELEVATION_DEGREES * PI.toFloat() / 180f)
private val ContainerElevationCos = cos(CONTAINER_ELEVATION_DEGREES * PI.toFloat() / 180f)

/**
 * A steel shipping container, lying long and low: corrugated sides, a frame of rails and corner
 * posts with a cast block at every corner, and at its left end a pair of doors with their locking
 * bars, handles and hinges - with a few scuffs and rust streaks of honest wear. It shakes like any
 * cup but doesn't tip: poured, it stays standing and its doors swing open on their hinges, bouncing
 * to a stop, showing its dark inside; they shut again as the next shake starts. A container that first
 * appears already poured (a game being continued) shows them open; under reduced motion they open
 * without bouncing.
 *
 * [CupPalette.mid] is its paint, [CupPalette.light] and [CupPalette.dark] its lit and shaded faces,
 * [CupPalette.accent] its bare-metal fittings, [CupPalette.interior] its inside. The box (inside
 * included) is painted once and stamped; only the two doors are drawn live.
 */
class ShippingContainerDiceCupStyle(override val id: String, private val palette: CupPalette) : DiceCupStyle, Swatched {
    override val shape: CupShape = CupShape.SQUAT
    override val swatch: Color = palette.mid

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val reduceMotion = LocalReduceMotion.current
        val open = tilted && !rolling
        // A continued game's container starts with its doors open; anything else starts shut.
        val doors = remember { Animatable(if (open) 1f else 0f) }
        LaunchedEffect(open) {
            // Under reduced motion the doors are simply shut or open, with no swing between.
            when {
                reduceMotion -> doors.snapTo(if (open) 1f else 0f)
                !open -> doors.animateTo(0f, tween(DOOR_CLOSE_MILLIS))
                // Underdamped, so the doors fly open and bounce on their hinges.
                else -> doors.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
            }
        }
        // It shakes like any cup, but never tips: its doors open instead.
        CupCanvas(rolling, tilted, modifier, shape, tips = false) {
            // Painted once per size: the shake only turns the canvas.
            drawCachedSurface(ContainerBody(id)) { CupDrawScope(this, CupShape.SQUAT, CupPose(0f, 0f)).paintContainer() }
            // Read here, inside the draw, so the doors' swing only repaints them. The far door first.
            val degrees = doors.value * DOOR_OPEN_DEGREES
            drawDoor(front = false, degrees)
            drawDoor(front = true, degrees)
        }
    }

    private data class ContainerBody(val id: String)

    private fun CupDrawScope.point(x: Float, y: Float, z: Float): Offset = Offset(
        gx(CONTAINER_CENTRE_X + x * ContainerYawCos - z * ContainerYawSin),
        gy(CONTAINER_BASE_Y - y + (x * ContainerYawSin + z * ContainerYawCos) * CUP_VIEW_SQUASH),
    )

    /** How squarely a surface facing ([nx], [ny], [nz]) faces the viewer: above 0 it's seen. */
    private fun facing(nx: Float, ny: Float, nz: Float): Float {
        val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        return (nx * ContainerYawSin * ContainerElevationCos + ny * ContainerElevationSin + nz * ContainerYawCos * ContainerElevationCos) / length
    }

    private fun CupDrawScope.quad(vararg corners: Triple<Float, Float, Float>): Path = Path().apply {
        corners.forEachIndexed { i, (x, y, z) ->
            val p = point(x, y, z)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }

    /** A rectangle on the long front side (z = depth / 2), from [x0] to [x1] and [y0] up to [y1]. */
    private fun CupDrawScope.side(x0: Float, x1: Float, y0: Float, y1: Float, z: Float = CONTAINER_HALF_DEPTH): Path =
        quad(Triple(x0, y0, z), Triple(x1, y0, z), Triple(x1, y1, z), Triple(x0, y1, z))

    /** A rectangle on the door end (x = [DOOR_END_X]), from [z0] to [z1] and [y0] up to [y1]. */
    private fun CupDrawScope.end(z0: Float, z1: Float, y0: Float, y1: Float, x: Float = DOOR_END_X): Path =
        quad(Triple(x, y0, z0), Triple(x, y0, z1), Triple(x, y1, z1), Triple(x, y1, z0))

    private fun CupDrawScope.paintContainer() {
        val l = CONTAINER_HALF_LENGTH
        val d = CONTAINER_HALF_DEPTH
        // Its shadow: its own footprint on the table, cast right and back, softened in layers.
        for ((spread, alpha) in listOf(3f to 0.07f, 1.8f to 0.1f, 0.6f to 0.14f)) {
            drawPath(quad(Triple(-l - spread + 2f, 0f, d + spread - 1.5f), Triple(l + spread + 2f, 0f, d + spread - 1.5f), Triple(l + spread + 2f, 0f, -d - spread - 1.5f), Triple(-l - spread + 2f, 0f, -d - spread - 1.5f)), Color.Black.copy(alpha = alpha))
        }
        drawSide()
        drawInside()
        drawRoof()
        drawFrame()
    }

    /**
     * The long side: corrugated - ribs standing out and recesses set back in turn, each catching the
     * light on its own slant - between the top and bottom rails, with a little wear on it.
     */
    private fun CupDrawScope.drawSide() {
        val l = CONTAINER_HALF_LENGTH
        val h = CONTAINER_HEIGHT
        drawPath(side(-l, l, 0f, h), palette.mid)
        var x = -l + RAIL
        var k = 0
        while (x < l - RAIL) {
            val next = (x + RIB_PITCH / 2f).coerceAtMost(l - RAIL)
            val slant = when (k % 4) {
                0 -> palette.light
                1 -> palette.mid
                2 -> lerp(palette.mid, palette.dark, 0.6f)
                else -> palette.mid
            }
            drawPath(side(x, next, RAIL, h - RAIL), slant)
            x = next
            k++
        }
        // Wear: a few rust streaks running down from the top rail, and the odd scuff.
        val random = Random(id.hashCode())
        val rust = Color(0xFF7A3E1A)
        repeat(5) {
            val at = -l + 3f + random.nextFloat() * (2f * l - 6f)
            val from = point(at, h - RAIL, CONTAINER_HALF_DEPTH + 0.05f)
            val to = point(at + (random.nextFloat() - 0.5f) * 0.6f, h - RAIL - 3f - random.nextFloat() * 7f, CONTAINER_HALF_DEPTH + 0.05f)
            drawLine(Brush.linearGradient(listOf(rust.copy(alpha = 0.55f), Color.Transparent), start = from, end = to), from, to, strokeWidth = gx(0.5f + random.nextFloat() * 0.5f), cap = StrokeCap.Round)
        }
        repeat(3) {
            val at = point(-l + 4f + random.nextFloat() * (2f * l - 8f), 3f + random.nextFloat() * (h - 8f), CONTAINER_HALF_DEPTH + 0.05f)
            val r = gx(0.8f + random.nextFloat() * 1.2f)
            drawOval(Color.Black.copy(alpha = 0.12f), topLeft = at - Offset(r, r * 0.6f), size = Size(r * 2f, r * 1.2f))
        }
    }

    /**
     * The inside, seen through the door end once the doors swing back: dark walls running back into
     * the gloom, and the plank floor, lit just inside the doorway. Painted with the box - the shut
     * doors simply cover it.
     */
    private fun CupDrawScope.drawInside() {
        val d = CONTAINER_HALF_DEPTH - RAIL
        val h = CONTAINER_HEIGHT - RAIL
        val depth = 14f
        // Worked out here: the grid isn't reachable inside clipPath.
        val doorway = end(-d, d, RAIL, h)
        val back = end(-d, d, RAIL, h, x = DOOR_END_X + depth)
        val floor = quad(Triple(DOOR_END_X, RAIL, d), Triple(DOOR_END_X + depth, RAIL, d), Triple(DOOR_END_X + depth, RAIL, -d), Triple(DOOR_END_X, RAIL, -d))
        val farWall = quad(Triple(DOOR_END_X, RAIL, -d), Triple(DOOR_END_X + depth, RAIL, -d), Triple(DOOR_END_X + depth, h, -d), Triple(DOOR_END_X, h, -d))
        val ceiling = quad(Triple(DOOR_END_X, h, d), Triple(DOOR_END_X + depth, h, d), Triple(DOOR_END_X + depth, h, -d), Triple(DOOR_END_X, h, -d))
        val planks = List(5) { k ->
            val z = -d + 2f * d * (k + 1) / 6f
            point(DOOR_END_X, RAIL, z) to point(DOOR_END_X + depth, RAIL, z)
        }
        val doorwayTop = point(DOOR_END_X, h, 0f).y
        val doorwayBottom = point(DOOR_END_X, RAIL, 0f).y
        val plankWidth = gx(0.25f)
        clipPath(doorway) {
            drawPath(doorway, palette.interior)
            drawPath(ceiling, lerp(palette.interior, palette.dark, 0.3f))
            drawPath(farWall, lerp(palette.interior, palette.dark, 0.5f))
            drawPath(back, lerp(palette.interior, Color.Black, 0.4f))
            drawPath(floor, Brush.verticalGradient(listOf(Color(0xFF3A2A1A), Color(0xFF8A6A42)), startY = doorwayTop, endY = doorwayBottom))
            for ((from, to) in planks) drawLine(Color(0xFF2A1C10).copy(alpha = 0.7f), from, to, strokeWidth = plankWidth)
        }
    }

    /** The roof: flat, lit from above, with its shallow cross-corrugation. */
    private fun CupDrawScope.drawRoof() {
        val l = CONTAINER_HALF_LENGTH
        val d = CONTAINER_HALF_DEPTH
        val h = CONTAINER_HEIGHT
        drawPath(quad(Triple(-l, h, d), Triple(l, h, d), Triple(l, h, -d), Triple(-l, h, -d)), lerp(palette.mid, palette.light, 0.45f))
        var x = -l + RAIL + 1.2f
        while (x < l - RAIL) {
            drawLine(lerp(palette.mid, palette.dark, 0.3f).copy(alpha = 0.5f), point(x, h, d - RAIL), point(x, h, -d + RAIL), strokeWidth = gx(0.3f))
            x += 2.4f
        }
    }

    /**
     * The frame: the top and bottom rails along the side, the corner posts, the door end's header and
     * sill and its posts - which the doors hang on - and the cast corner blocks with their oval slots.
     */
    private fun CupDrawScope.drawFrame() {
        val l = CONTAINER_HALF_LENGTH
        val d = CONTAINER_HALF_DEPTH
        val h = CONTAINER_HEIGHT
        val rail = lerp(palette.mid, palette.dark, 0.25f)
        val endRail = lerp(palette.mid, palette.dark, 0.45f)
        drawPath(side(-l, l, h - RAIL, h), lerp(palette.mid, palette.light, 0.2f))
        drawPath(side(-l, l, 0f, RAIL), rail)
        drawPath(side(-l, -l + RAIL, 0f, h), rail)
        drawPath(side(l - RAIL, l, 0f, h), rail)
        drawPath(end(-d, d, h - RAIL, h), endRail)
        drawPath(end(-d, d, 0f, RAIL), endRail)
        drawPath(end(d - RAIL, d, 0f, h), endRail)
        drawPath(end(-d, -d + RAIL, 0f, h), endRail)
        val casting = palette.accent
        for ((x, y) in listOf(-l to 0f, -l to h, l to 0f, l to h)) {
            val cy = if (y == 0f) 0f else h - RAIL
            drawPath(side(x - if (x > 0) RAIL else 0f, x + if (x > 0) 0f else RAIL, cy, cy + RAIL), casting)
            val slot = point(x + if (x > 0) -RAIL / 2f else RAIL / 2f, cy + RAIL / 2f, d + 0.05f)
            drawOval(Color.Black, topLeft = slot - Offset(gx(0.4f), gy(0.25f)), size = Size(gx(0.8f), gy(0.5f)))
        }
        for ((z, y) in listOf(-d to 0f, -d to h, d to 0f, d to h)) {
            val cy = if (y == 0f) 0f else h - RAIL
            drawPath(end(z - if (z > 0) RAIL else 0f, z + if (z > 0) 0f else RAIL, cy, cy + RAIL), lerp(casting, Color.Black, 0.2f))
        }
        // The edges, inked lightly so the box reads crisply at a small size.
        val edge = palette.dark.copy(alpha = 0.6f)
        val width = gx(0.3f)
        drawPath(side(-l, l, 0f, h), edge, style = Stroke(width))
        drawPath(end(-d, d, 0f, h), edge, style = Stroke(width))
        drawPath(quad(Triple(-l, h, d), Triple(l, h, d), Triple(l, h, -d), Triple(-l, h, -d)), edge, style = Stroke(width))
    }

    /**
     * One door - the [front] one or the back - swung out [degrees] on its hinges at its corner post:
     * its outside (corrugated, with two locking bars in their keepers, a handle on each, and hinge
     * knuckles down its outer edge) or its plain inside, whichever faces the viewer.
     */
    private fun CupDrawScope.drawDoor(front: Boolean, degrees: Float) {
        val s = if (front) 1f else -1f
        val a = degrees * PI.toFloat() / 180f
        val hingeZ = s * (CONTAINER_HALF_DEPTH - RAIL)
        val width = CONTAINER_HALF_DEPTH - RAIL
        val y0 = RAIL
        val y1 = CONTAINER_HEIGHT - RAIL
        // A point on the door: [u] in from its hinge edge, [y] up, [out] off its outside face. Shut, it
        // lies across the doorway; opening, it swings out away from the box.
        val dirX = -sin(a)
        val dirZ = -s * cos(a)
        fun at(u: Float, y: Float, out: Float = 0f) = Triple(DOOR_END_X + u * dirX - out * cos(a), y, hingeZ + u * dirZ + out * s * sin(a))
        fun panel(u0: Float, u1: Float, ya: Float, yb: Float, out: Float = 0f) = quad(at(u0, ya, out), at(u1, ya, out), at(u1, yb, out), at(u0, yb, out))
        fun line(from: Triple<Float, Float, Float>, to: Triple<Float, Float, Float>) = point(from.first, from.second, from.third) to point(to.first, to.second, to.third)
        val outside = facing(-cos(a), 0f, s * sin(a))
        if (outside > 0f) {
            val paint = lerp(lerp(palette.mid, palette.dark, 0.45f), palette.light, (outside * 0.6f).coerceIn(0f, 0.5f))
            drawPath(panel(0f, width, y0, y1), paint)
            // Its corrugation: a few broad ribs.
            for (k in 0 until 4) {
                drawPath(panel(width * (k + 0.2f) / 4f, width * (k + 0.6f) / 4f, y0 + 0.5f, y1 - 0.5f, out = 0.05f), lerp(paint, palette.light, 0.25f))
            }
            // Two locking bars, held in keepers top and bottom, each with its handle swung down.
            val metal = palette.accent
            for (u in listOf(width * 0.3f, width * 0.72f)) {
                val (barTop, barBottom) = line(at(u, y0 + 0.2f, 0.25f), at(u, y1 - 0.2f, 0.25f))
                drawLine(metal, barTop, barBottom, strokeWidth = gx(0.5f), cap = StrokeCap.Round)
                for (y in listOf(y0 + 0.9f, y1 - 0.9f)) drawPath(panel(u - 0.6f, u + 0.6f, y - 0.5f, y + 0.5f, out = 0.3f), lerp(metal, Color.Black, 0.3f))
                val (pivot, grip) = line(at(u, CONTAINER_HEIGHT * 0.45f, 0.35f), at(u - 2f, CONTAINER_HEIGHT * 0.45f - 0.3f, 0.35f))
                drawLine(lerp(metal, Color.White, 0.25f), pivot, grip, strokeWidth = gx(0.45f), cap = StrokeCap.Round)
            }
            // Hinge knuckles down its outer edge, on the corner post.
            for (y in listOf(4f, CONTAINER_HEIGHT / 2f, CONTAINER_HEIGHT - 4f)) {
                drawPath(panel(0f, 0.9f, y - 0.6f, y + 0.6f, out = 0.15f), lerp(metal, Color.Black, 0.2f))
            }
        } else {
            // The plain inside of the door, in shadow, with its stiffening ribs.
            drawPath(panel(0f, width, y0, y1), lerp(palette.dark, palette.interior, 0.3f))
            for (k in 1 until 4) {
                val (top, bottom) = line(at(width * k / 4f, y0 + 0.5f), at(width * k / 4f, y1 - 0.5f))
                drawLine(palette.interior.copy(alpha = 0.6f), top, bottom, strokeWidth = gx(0.3f))
            }
        }
        drawPath(panel(0f, width, y0, y1), palette.dark.copy(alpha = 0.6f), style = Stroke(gx(0.3f)))
    }
}
