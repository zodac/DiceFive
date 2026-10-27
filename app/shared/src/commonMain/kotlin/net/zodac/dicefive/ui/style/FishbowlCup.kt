package net.zodac.dicefive.ui.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// The fishbowl: a sphere of BOWL_RADIUS round (centreX, BOWL_CENTRE_Y), cut flat across the top for
// its opening (of BOWL_MOUTH_RADIUS) and flat across the bottom (at BOWL_BASE_Y), which it stands on. Its water
// rests at BOWL_WATER_Y, and the clownfish takes CLOWNFISH_LAP_MILLIS to swim to one end of its rail and back.
private const val BOWL_RADIUS = 29f
private const val BOWL_CENTRE_Y = 34f
private const val BOWL_MOUTH_RADIUS = 16f
private const val BOWL_BASE_Y = 61f
private const val BOWL_WATER_Y = 30f
private const val CLOWNFISH_LAP_MILLIS = 9_000

// The clownfish: how many grid units one of its own is (it's about 17 of them long), how far either side
// of the centre line it swims, how far below the water's surface, how far it bobs, and how much of
// each lap it spends turning round at either end.
private const val CLOWNFISH_SCALE = 1.52f
internal const val CLOWNFISH_RAIL_HALF_LENGTH = 9f
private const val CLOWNFISH_DEPTH = 13f
private const val CLOWNFISH_BOB = 1.2f
private const val CLOWNFISH_TURN = 0.06f

// The clownfish's body, in its own units: half its length, height and thickness - an egg-like solid,
// so turned head-on it's still a fat oval, never a sliver.
private const val FISH_HALF_LENGTH = 6.5f
private const val FISH_HALF_HEIGHT = 3.2f
private const val FISH_HALF_THICKNESS = 2.3f
// How many pieces round its body each band is drawn in.
private const val FISH_BAND_STEPS = 16

/**
 * One of the clownfish's bands, round its body: centred [at] along it (head forwards), [half] either
 * side, its middle down the flank bowed [bow] towards the head.
 */
private class FishBand(val at: Float, val half: Float, val bow: Float)

private val FishBands = listOf(FishBand(2.8f, 0.85f, 0.5f), FishBand(-1f, 0.95f, 0.45f), FishBand(-5.1f, 0.5f, 0.25f))

/** How far across the fishbowl's sphere is at [y]: half its width there. */
private fun bowlHalfWidth(y: Float): Float = sqrt(maxOf(0f, BOWL_RADIUS * BOWL_RADIUS - (y - BOWL_CENTRE_Y) * (y - BOWL_CENTRE_Y)))

// Where the opening cuts the sphere.
private val BowlRimY = BOWL_CENTRE_Y - sqrt(BOWL_RADIUS * BOWL_RADIUS - BOWL_MOUTH_RADIUS * BOWL_MOUTH_RADIUS)

// The fishbowl's fronds: how much of the bowl's tilt they lean back against (floating up towards the
// surface), how far the water's slosh swings them past that, how far they sway on their own, and the
// most their tips ever bend either way.
private const val FROND_BUOYANCY = 0.6f
private const val FROND_SLOSH = 1.2f
private const val FROND_SWAY = 6f
private const val FROND_MAX_BEND = 70f
private const val FROND_STEPS = 9

private val FrondDark = Color(0xFF2E7D3A)
private val FrondLight = Color(0xFF55B04E)

/**
 * One frond of weed in the fishbowl, rooted at grid ([rootX], [rootY]): [length] long and [width] wide
 * at its widest, leaving its root at [lean] degrees from upright (clockwise) and curling [curl] more
 * by its tip, swaying a [phase] of a sway behind the others.
 */
private class Frond(val rootX: Float, val rootY: Float, val length: Float, val width: Float, val lean: Float, val curl: Float, val phase: Float, val colour: Color)

private val Fronds = listOf(
    Frond(-9f, 60.5f, 20f, 3.4f, -12f, 14f, 0f, FrondDark),
    Frond(-10f, 60.5f, 14f, 3f, -34f, 22f, 0.3f, FrondLight),
    Frond(-8f, 60.5f, 11f, 2.6f, 10f, -12f, 0.6f, FrondLight),
    Frond(10f, 60.5f, 17f, 3.2f, 10f, -16f, 0.2f, FrondDark),
    Frond(11f, 60.5f, 11f, 2.8f, 32f, -22f, 0.5f, FrondLight),
)

/**
 * One of the fishbowl's rocks: resting [home] grid units from the centre line on the bottom, [radius]
 * across (a little flatter than it is wide), in [colour], with its lumpy outline from [seed].
 */
private class BowlRock(val home: Float, val radius: Float, val colour: Color, val seed: Int)

/**
 * The fishbowl's lap as drawn: [shown] turns the ambient cycle (null while it's stopped) into where
 * the fish is on its lap, picking up from wherever it was last held whenever the cycle starts again
 * from 0. Null until the cycle has run at all, for the resting pose. Plain fields, not state: it's
 * read and written only while drawing.
 */
internal class HeldLap {
    private var last: Float? = null
    private var offset = 0f
    private var running = false

    fun shown(cycle: Float?): Float? {
        if (cycle == null) {
            running = false
            return last
        }
        if (!running) {
            offset = last?.let { it - cycle } ?: 0f
            running = true
        }
        val lap = ((cycle + offset) % 1f + 1f) % 1f
        last = lap
        return lap
    }
}

/** A [BowlRock] ready to draw, in pixels. */
private class RockShape(val outline: Path, val fill: Brush, val edge: Color, val edgeWidth: Float, val shadow: Rect, val glint: Offset, val glintRadius: Float)

// Back to front: the middle one sits behind the other two.
private val BowlRocks = listOf(
    BowlRock(2f, 4.2f, Color(0xFFC9B48E), 3),
    BowlRock(-7f, 5.6f, Color(0xFF8E9AA3), 1),
    BowlRock(9f, 4.8f, Color(0xFF6E8299), 2),
)

// How far the rocks roll per unit of the sine of the water's tilt, and the furthest they roll either
// way: tipped right over they gather at the low side, and a shake rocks them back and forth.
private const val ROCK_ROLL = 40f
private const val ROCK_ROLL_MAX = 12f

private val ClownfishOrange = Color(0xFFFF7A1A)
private val ClownfishOrangeShade = Color(0xFFD9520A)
private val ClownfishBand = Color(0xFFFFFBF5)
private val ClownfishInk = Color(0xFF1A1210)

/**
 * Where the clownfish is on its rail ([CLOWNFISH_RAIL_HALF_LENGTH] grid units either side of the
 * centre line at most) at [lap] (0 to 1, once there and back), and which way it's turned: its yaw in
 * degrees, 0 side-on facing right and 180 side-on facing left. It eases along the rail side-on, then
 * at each end turns round in [CLOWNFISH_TURN] of the lap - always through 90, face towards the
 * viewer, so every turn shows its face.
 */
internal fun clownfishOnRail(lap: Float): Pair<Float, Float> {
    val half = 0.5f
    val leg = (lap % 1f + 1f) % 1f
    // The first half of the lap heads right, the second left; each ends in its turn.
    val headingRight = leg < half
    val along = if (headingRight) leg else leg - half
    val swim = half - CLOWNFISH_TURN
    val from = if (headingRight) -CLOWNFISH_RAIL_HALF_LENGTH else CLOWNFISH_RAIL_HALF_LENGTH
    val direction = if (headingRight) 1f else -1f
    if (along < swim) {
        val t = along / swim
        val eased = t * t * (3f - 2f * t)
        return from + direction * 2f * CLOWNFISH_RAIL_HALF_LENGTH * eased to if (headingRight) 0f else 180f
    }
    val t = (along - swim) / CLOWNFISH_TURN
    val eased = t * t * (3f - 2f * t)
    return -from to if (headingRight) 180f * eased else 180f - 180f * eased
}

/**
 * A round goldfish bowl - a glass sphere, cut flat for its opening and its base - of see-through
 * [tint] glass, with a pale [water] that stays level and sloshes as it's shaken and poured (see
 * [liquidIn]). Three loose rocks lie on the bottom and roll with the water's tilt, beside fronds of
 * weed that sway with it. A clownfish swims back and forth on a rail through the water
 * ([clownfishOnRail]), riding the slosh, and bubbles rise from the bottom. Once the cup is spent
 * the fish and fronds hold still where they were (see [HeldLap]) and no bubbles rise, until the
 * next turn sets them going again from there; under reduced motion, where they've never moved, the
 * fish rests in the middle, facing left. Either way the fronds still lean with the bowl and the
 * slosh. Drawn [CupShape.SQUAT].
 */
class FishbowlDiceCupStyle(override val id: String, private val tint: Color, private val water: Color) : DiceCupStyle, Swatched {
    override val swatch: Color = ClownfishOrange
    override val shape: CupShape = CupShape.SQUAT

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val swim = rememberAmbientCycle(CLOWNFISH_LAP_MILLIS)
        val held = remember { HeldLap() }
        CupCanvas(rolling, tilted, modifier, shape) {
            val running = swim?.value
            // Where the fish, its tail and the fronds are drawn: the running lap, carried on from
            // wherever they were held - so they stop where they are when the cup is spent, rather
            // than jumping to rest, and set off again from there.
            val lap = held.shown(running)
            val baseRadius = bowlHalfWidth(BOWL_BASE_Y)
            drawContactShadow(baseRadius, BOWL_BASE_Y, alpha = 0.22f)
            val sphere = Rect(Offset(gx(centreX - BOWL_RADIUS), gy(BOWL_CENTRE_Y - BOWL_RADIUS)), Offset(gx(centreX + BOWL_RADIUS), gy(BOWL_CENTRE_Y + BOWL_RADIUS)))
            // The angles (degrees, clockwise from the right) where the sphere meets the opening and the base, on the right.
            val toRad = 180f / PI.toFloat()
            val rimAngle = atan2(BowlRimY - BOWL_CENTRE_Y, BOWL_MOUTH_RADIUS) * toRad
            val baseAngle = atan2(BOWL_BASE_Y - BOWL_CENTRE_Y, baseRadius) * toRad
            // Open across the top, for the glass's edge; the bowl itself is that closed.
            val glassEdge = Path().apply {
                moveTo(gx(centreX - BOWL_MOUTH_RADIUS), gy(BowlRimY))
                arcTo(sphere, 180f - rimAngle, rimAngle - baseAngle, false)
                lineTo(gx(centreX + baseRadius), gy(BOWL_BASE_Y))
                arcTo(sphere, baseAngle, rimAngle - baseAngle, false)
            }
            val bowl = Path().apply {
                addPath(glassEdge)
                close()
            }
            val mouth = mouthBounds(BOWL_MOUTH_RADIUS, BowlRimY)
            drawPath(Path.combine(PathOperation.Union, bowl, Path().apply { addOval(mouth) }), tint.copy(alpha = 0.16f))

            val liquid = liquidIn(BowlRimY, BOWL_MOUTH_RADIUS, BOWL_WATER_Y)
            val surfaceRadius = bowlHalfWidth(BOWL_WATER_Y)
            // Everything in pixels up front: the cup grid can't be reached from inside clipPath.
            val volume = liquidVolume(liquid, surfaceRadius)
            val surface = liquidRing(liquid, surfaceRadius)
            val fronds = frondPaths(lap)
            val (railX, yaw) = if (lap == null) 0f to 180f else clownfishOnRail(lap)
            val bob = if (lap == null) 0f else sin(lap * 2f * PI.toFloat() * 3f) * CLOWNFISH_BOB
            val fishAt = onLiquid(liquid, centreX + railX, liquid.level + CLOWNFISH_DEPTH + bob)
            val unit = gx(CLOWNFISH_SCALE)
            val level = pose.rotation
            val wag = if (lap == null) 0f else sin(lap * 2f * PI.toFloat() * 14f)
            val bubbles = if (running == null || lap == null) {
                emptyList()
            } else {
                (0 until 3).map { i ->
                    val t = (lap * 4f + i / 3f) % 1f
                    val top = liquid.level + liquid.slope * 6f + 2f
                    Offset(gx(centreX + 11f + sin(t * 9f + i) * 1.2f), gy(56f - (56f - top) * t)) to gy(0.7f + t * 0.5f)
                }
            }

            val rocks = rockShapes()
            clipPath(bowl) {
                drawPath(volume, water.copy(alpha = 0.45f))
                for ((path, colour) in fronds) drawPath(path, colour)
                for (rock in rocks) {
                    drawOval(Color.Black.copy(alpha = 0.18f), topLeft = rock.shadow.topLeft, size = rock.shadow.size)
                    drawPath(rock.outline, rock.fill)
                    drawPath(rock.outline, rock.edge, style = Stroke(width = rock.edgeWidth))
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = rock.glintRadius, center = rock.glint)
                }
                drawPath(surface, lerp(water, Color.White, 0.4f).copy(alpha = 0.4f))
            }
            // The water inside the bowl, as two nested clips rather than one combined path: building
            // that every frame is far dearer.
            clipPath(bowl) {
                clipPath(volume) {
                    // Level on screen, however the bowl is tipped.
                    translate(fishAt.x, fishAt.y) {
                        rotate(-level, pivot = Offset.Zero) {
                            scale(unit, pivot = Offset.Zero) { drawClownfish(yaw, wag) }
                        }
                    }
                    for ((at, radius) in bubbles) {
                        drawCircle(Color.White.copy(alpha = 0.6f), radius = radius, center = at, style = Stroke(width = radius * 0.4f))
                    }
                }
            }

            drawPath(glassEdge, Color.White.copy(alpha = 0.35f), style = Stroke(width = gy(0.9f)))
            // Highlights following the sphere: a bright one up on the left, a faint one low on the right.
            val inset = gx(4f)
            val inner = Rect(sphere.left + inset, sphere.top + inset, sphere.right - inset, sphere.bottom - inset)
            drawArc(Color.White.copy(alpha = 0.5f), 150f, 60f, useCenter = false, topLeft = inner.topLeft, size = inner.size, style = Stroke(width = gy(1.8f), cap = StrokeCap.Round))
            drawArc(Color.White.copy(alpha = 0.22f), -20f, 45f, useCenter = false, topLeft = inner.topLeft, size = inner.size, style = Stroke(width = gy(1.1f), cap = StrokeCap.Round))
            drawOval(Color.White.copy(alpha = 0.3f), topLeft = mouth.topLeft, size = mouth.size, style = Stroke(width = gy(2.4f)))
            drawOval(Color.White.copy(alpha = 0.8f), topLeft = mouth.topLeft, size = mouth.size, style = Stroke(width = gy(0.9f)))
        }
    }

    /**
     * Every frond as a filled outline, in pixels. Each leans back against the bowl's tilt (see
     * [FROND_BUOYANCY]), swings with the water's slosh, and - while [lap] runs - sways gently on its
     * own; the bend grows along the frond, so its root stays put and its tip moves most.
     */
    private fun CupDrawScope.frondPaths(lap: Float?): List<Pair<Path, Color>> = Fronds.map { frond ->
        val ambient = if (lap == null) 0f else sin(2f * PI.toFloat() * (lap * 3f + frond.phase)) * FROND_SWAY
        val bend = (-pose.rotation * FROND_BUOYANCY - pose.slosh * FROND_SLOSH + ambient).coerceIn(-FROND_MAX_BEND, FROND_MAX_BEND)
        val toRadians = PI.toFloat() / 180f
        var x = centreX + frond.rootX
        var y = frond.rootY
        val left = ArrayList<Offset>(FROND_STEPS + 1)
        val right = ArrayList<Offset>(FROND_STEPS + 1)
        for (step in 0..FROND_STEPS) {
            val t = step / FROND_STEPS.toFloat()
            val heading = (frond.lean + frond.curl * t + bend * t) * toRadians
            if (step > 0) {
                x += sin(heading) * frond.length / FROND_STEPS
                y -= cos(heading) * frond.length / FROND_STEPS
            }
            // Narrow at the root, widest a third of the way up, to a point at the tip.
            val half = frond.width / 2f * (1f - t) * (0.6f + 1.6f * t).coerceAtMost(1.2f)
            left += Offset(gx(x - cos(heading) * half), gy(y - sin(heading) * half))
            right += Offset(gx(x + cos(heading) * half), gy(y + sin(heading) * half))
        }
        smoothPath(left + right.asReversed(), closed = true) to frond.colour
    }

    /**
     * The rocks on the bottom, in pixels, as they are now. They're loose: they roll towards whichever
     * side of the bowl is lower, following the water's lagging tilt ([CupPose.liquidRotation]), so a
     * shake rocks them to and fro, overshooting a little, and a pour sends them to the low side, where
     * they ride up the curve of the bowl. Each turns as it rolls, and keeps a shadow under it.
     */
    private fun CupDrawScope.rockShapes(): List<RockShape> {
        val roll = (sin(pose.liquidRotation * PI.toFloat() / 180f) * ROCK_ROLL).coerceIn(-ROCK_ROLL_MAX, ROCK_ROLL_MAX)
        return BowlRocks.map { rock ->
            val reach = bowlHalfWidth(BOWL_BASE_Y - rock.radius) - rock.radius
            val x = (rock.home + roll).coerceIn(-reach, reach)
            // The bottom under it: the flat base, then the sphere curving up.
            val floor = if (abs(x) <= bowlHalfWidth(BOWL_BASE_Y)) BOWL_BASE_Y else BOWL_CENTRE_Y + sqrt(maxOf(0f, BOWL_RADIUS * BOWL_RADIUS - x * x))
            val height = rock.radius * 0.75f
            val centreY = floor - height
            val turn = roll / rock.radius
            val random = Random(rock.seed)
            val points = (0 until 8).map { i ->
                val angle = 2f * PI.toFloat() * i / 8 + turn
                val reachOut = 0.82f + random.nextFloat() * 0.22f
                Offset(gx(centreX + x + cos(angle) * rock.radius * reachOut), gy(centreY + sin(angle) * height * reachOut))
            }
            val outline = smoothPath(points, closed = true)
            val centre = Offset(gx(centreX + x), gy(centreY))
            RockShape(
                outline = outline,
                fill = Brush.radialGradient(
                    listOf(lerp(rock.colour, Color.White, 0.25f), rock.colour, lerp(rock.colour, Color.Black, 0.35f)),
                    center = centre - Offset(gx(rock.radius * 0.35f), gy(height * 0.45f)),
                    radius = gx(rock.radius * 1.4f),
                ),
                edge = lerp(rock.colour, Color.Black, 0.5f),
                edgeWidth = gy(0.35f),
                shadow = Rect(Offset(gx(centreX + x - rock.radius), gy(floor - 0.9f)), Size(gx(rock.radius * 2f), gy(1.8f))),
                glint = centre - Offset(gx(rock.radius * 0.4f), gy(height * 0.45f)),
                glintRadius = gx(rock.radius * 0.18f),
            )
        }
    }
}

/**
 * A clownfish in its own units (about 17 long with its tail, centred on the origin), turned [yaw]
 * degrees about its upright axis: 0 is side-on facing right, 90 head-on, 180 side-on facing left.
 *
 * Its body is a solid ([FISH_HALF_LENGTH], [FISH_HALF_HEIGHT], [FISH_HALF_THICKNESS]), mostly orange,
 * shaded darker towards its edges so it reads as round. The three black-edged white [FishBands] are
 * rings round it - only the part on the side towards the viewer is drawn, so mid-turn they curve
 * round its face rather than squashing flat - and each eye shows only while its side faces the
 * viewer. The tail, which [wag]s (-1 to 1), and the fin on top are thin, so they do turn edge-on.
 */
private fun DrawScope.drawClownfish(yaw: Float, wag: Float) {
    val radians = yaw * PI.toFloat() / 180f
    val c = cos(radians)
    val s = sin(radians)
    // A point on the fish ([u] along it, head forwards; [v] across it, towards the viewer at yaw 0;
    // [w] up) as it lands on screen, and how far it is towards the viewer.
    fun screenX(u: Float, v: Float) = u * c - v * s
    fun towards(u: Float, v: Float) = u * s + v * c

    val sway = wag * 0.9f
    val tail = Path().apply {
        moveTo(screenX(-5.8f, 0f), 0f)
        quadraticTo(screenX(-8f, 0f), -2.8f + sway * 0.3f, screenX(-9.8f, 0f), -3.2f + sway)
        quadraticTo(screenX(-8.7f, 0f), sway, screenX(-9.8f, 0f), 3.2f + sway)
        quadraticTo(screenX(-8f, 0f), 2.8f + sway * 0.3f, screenX(-5.8f, 0f), 0f)
        close()
    }
    val dorsal = Path().apply {
        moveTo(screenX(3f, 0f), -3.1f)
        cubicTo(screenX(2f, 0f), -5.2f, screenX(-2.5f, 0f), -5.4f, screenX(-4.4f, 0f), -2.4f)
        close()
    }
    val fin = Stroke(width = 0.35f)
    // Every turn swings the face towards the viewer, so the fins are always behind the body.
    for (part in listOf(tail, dorsal)) {
        drawPath(part, ClownfishOrange)
        drawPath(part, ClownfishInk, style = fin)
    }

    val halfWidth = sqrt((FISH_HALF_LENGTH * c) * (FISH_HALF_LENGTH * c) + (FISH_HALF_THICKNESS * s) * (FISH_HALF_THICKNESS * s))
    val outline = Rect(-halfWidth, -FISH_HALF_HEIGHT, halfWidth, FISH_HALF_HEIGHT)
    val body = Path().apply { addOval(outline) }
    drawPath(body, Brush.verticalGradient(listOf(ClownfishOrange, ClownfishOrangeShade), startY = -FISH_HALF_HEIGHT, endY = FISH_HALF_HEIGHT))
    clipPath(body) {
        for (band in FishBands) {
            drawPath(fishBand(band, band.half + 0.25f, c, s), ClownfishInk)
            drawPath(fishBand(band, band.half, c, s), ClownfishBand)
        }
        // Rounding: darker towards the edges, lit from up and to the left.
        drawOval(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.28f),
                center = Offset(-halfWidth * 0.25f, -FISH_HALF_HEIGHT * 0.35f),
                radius = maxOf(halfWidth, FISH_HALF_HEIGHT) * 1.25f,
            ),
            topLeft = outline.topLeft,
            size = outline.size,
        )
    }
    drawPath(body, ClownfishInk, style = Stroke(width = 0.3f))

    // The eyes, one each side of the head, looking a little forwards.
    for (side in listOf(1f, -1f)) {
        val facing = 0.45f * s + side * 0.9f * c
        if (facing <= 0.05f) continue
        val across = side * 1.55f
        val at = Offset(screenX(4.7f, across), -0.75f)
        val squash = facing.coerceAtMost(1f)
        drawOval(Color.White, topLeft = at - Offset(0.85f * squash, 0.85f), size = Size(1.7f * squash, 1.7f))
        drawOval(ClownfishInk, topLeft = at - Offset(0.55f * squash, 0.55f), size = Size(1.1f * squash, 1.1f))
    }
}

/**
 * [band] as it shows on the turned fish ([c] and [s] the cosine and sine of its yaw), [half] either
 * side of its centre: the pieces of the ring round the body that face the viewer.
 */
private fun fishBand(band: FishBand, half: Float, c: Float, s: Float) = Path().apply {
    fun point(u: Float, angle: Float): Offset {
        // The body's cross-section where the band is: an oval, smaller towards either end.
        val taper = sqrt(maxOf(0f, 1f - (u / FISH_HALF_LENGTH) * (u / FISH_HALF_LENGTH)))
        val v = FISH_HALF_THICKNESS * taper * cos(angle)
        return Offset(u * c - v * s, -FISH_HALF_HEIGHT * taper * sin(angle))
    }
    val twoPi = 2f * PI.toFloat()
    for (step in 0 until FISH_BAND_STEPS) {
        val from = twoPi * step / FISH_BAND_STEPS
        val to = twoPi * (step + 1) / FISH_BAND_STEPS
        val middle = (from + to) / 2f
        // Facing the viewer: the body's surface normal there points towards them.
        val normalU = band.at / (FISH_HALF_LENGTH * FISH_HALF_LENGTH)
        val normalV = cos(middle) / FISH_HALF_THICKNESS
        if (normalU * s + normalV * c <= 0f) continue
        // Bowed towards the head down the flank, straight at the top and bottom.
        fun edge(angle: Float, side: Float) = band.at + side * half + band.bow * cos(angle) * cos(angle)
        val a = point(edge(from, -1f), from)
        val b = point(edge(from, 1f), from)
        val d = point(edge(to, 1f), to)
        val e = point(edge(to, -1f), to)
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(d.x, d.y)
        lineTo(e.x, e.y)
        close()
    }
}
