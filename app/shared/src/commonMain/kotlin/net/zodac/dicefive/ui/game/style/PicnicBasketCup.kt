package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.launch
import net.zodac.dicefive.ui.common.LocalReduceMotion

/**
 * A picnic basket's colours: its wicker, light to dark, with the dark of the gaps in the weave; the
 * gingham its lids are lined with and its cloth is cut from ([check] over [cloth]); and the leather
 * of its hinges and latch.
 */
class BasketPalette(
    val wickerLight: Color,
    val wicker: Color,
    val wickerDark: Color,
    val gap: Color,
    val cloth: Color,
    val check: Color,
    val leather: Color,
)

// The basket, in grid units (SQUAT, 76 x 66), as a solid in its own space: x across (its right end
// positive), y up from the table, z forward from its middle. Turned BASKET_YAW so its right end
// shows, seen from the cups' usual raised angle. Its sides taper in a little towards the bottom.
private const val BASKET_WIDTH = 40f
private const val BASKET_DEPTH = 25f
private const val BASKET_HEIGHT = 17f
private const val BASKET_TAPER = 1.4f
private const val BASKET_WALL = 1.2f
private const val BASKET_CENTRE_X = 38f
private const val BASKET_BASE_Y = 52f
private const val BASKET_YAW_DEGREES = 26f
private const val BASKET_ELEVATION_DEGREES = 22f
// The whole basket drawn this much larger than its grid, about the middle of its footprint.
private const val BASKET_SCALE = 1.2f

// The lids: the top split in two across the middle, each half hinged along its own short end and
// reaching a little past the walls, this thick, with this gap between them. Opened, each lifts at
// the split and swings out over its end, past upright, to rest leaning out the opposite way to the other.
private const val FLAP_OVERHANG = 0.8f
private const val FLAP_THICKNESS = 1.1f
private const val FLAP_GAP = 0.3f
private const val FLAP_OPEN_DEGREES = 105f
// How far below the rim the cloth lies inside, under the food.
private const val CLOTH_Y = BASKET_HEIGHT - 4.5f
// The handle: fixed to the middle of the front and back walls, standing this far out past the lids'
// edges, its legs rising this far above the rim before it arches over, this much higher still.
private const val HANDLE_STANDOFF = 1.4f
private const val HANDLE_LEG = 5f
private const val HANDLE_RISE = 13f

// The shake: one loop carries it all, every part a whole number of smooth waves per loop - it
// rocks from side to side four times a second, lifting at each end of the rock, its lids rattling up
// in time.
private const val BASKET_SHAKE_LOOP_MILLIS = 1000
private const val BASKET_ROCK_DEGREES = 6f
private const val BASKET_SKITTER = 0.8f
private const val BASKET_HOP = 1.2f
private const val FLAP_RATTLE_DEGREES = 9f
private const val BASKET_SHAKE_FADE_MILLIS = 120
private const val FLAP_CLOSE_MILLIS = 110
// How long the opening's spill takes, from the lids flying up to the last apple coming to rest.
private const val SPILL_MILLIS = 900

/**
 * A wicker picnic basket, instead of a cup, its top split across the middle into two lids, each
 * hinged at its own end, under a handle arching front to back over the split: shaken, it rocks from
 * side to side, its lids rattling on their hinges; poured, it doesn't tip but flaps its lids open in
 * opposite directions - each lifting at the split and swinging out over its end, bouncing to rest
 * leaning out past upright - and a few apples tumble out onto the table. Open, it shows a picnic packed on its gingham cloth, until the next
 * shake shuts it.
 *
 * A basket that first appears with the roll already poured (a game being continued) appears open,
 * at rest - its contents are drawn live and cheaply, so there's nothing to wait for. Under reduced
 * motion it doesn't rock, and the lids open without bouncing.
 */
class PicnicBasketDiceCupStyle(override val id: String, private val palette: BasketPalette) : DiceCupStyle, Swatched {
    override val shape: CupShape = CupShape.SQUAT
    override val swatch: Color = palette.wicker

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val reduceMotion = LocalReduceMotion.current
        val open = tilted && !rolling
        // A continued game's basket starts open and at rest; anything else starts shut.
        val flaps = remember { Animatable(if (open) 1f else 0f) }
        val spill = remember { Animatable(1f) }
        var spills by remember { mutableStateOf(if (open) basketSpill(Random.nextInt()) else emptyList()) }
        LaunchedEffect(open) {
            if (!open) {
                flaps.animateTo(0f, tween(FLAP_CLOSE_MILLIS))
            } else if (flaps.value < 1f) {
                spills = basketSpill(Random.nextInt())
                if (reduceMotion) {
                    flaps.animateTo(1f, tween(200))
                } else {
                    launch { spill.snapTo(0f); spill.animateTo(1f, tween(SPILL_MILLIS, easing = LinearEasing)) }
                    // Underdamped, so the lids fly up and bounce on their hinges.
                    flaps.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
        val shakeWeight by animateFloatAsState(if (rolling) 1f else 0f, tween(BASKET_SHAKE_FADE_MILLIS), label = "basketShakeFade")
        // The shake's clock only exists while it counts, so a still basket asks for no frames.
        val shake: State<Float>? = if (rolling || shakeWeight > 0f) rememberBasketShakeLoop() else null

        Canvas(
            modifier = modifier.graphicsLayer {
                // Read here, so the shake only moves the layer.
                val t = (shake?.value ?: 0f) * 2f * PI.toFloat()
                val weight = shakeWeight
                rotationZ = weight * BASKET_ROCK_DEGREES * sin(4f * t)
                translationX = weight * size.width * BASKET_SKITTER * sin(2f * t + 1f) / CupShape.SQUAT.gridWidth
                translationY = -weight * size.height * BASKET_HOP * basketLift(t) / CupShape.SQUAT.gridHeight
                transformOrigin = TransformOrigin(0.5f, 0.9f)
            },
        ) {
            val scope = CupDrawScope(this, shape, CupPose(0f, 0f))
            val t = (shake?.value ?: 0f) * 2f * PI.toFloat()
            // The two lids rattle a little out of step with each other.
            val rattleLeft = shakeWeight * FLAP_RATTLE_DEGREES * basketLift(t)
            val rattleRight = shakeWeight * FLAP_RATTLE_DEGREES * 0.7f * basketLift(t + 0.4f)
            val opened = flaps.value * FLAP_OPEN_DEGREES
            scaledAbout(BASKET_SCALE, basketPivot(size)) {
                scope.drawBasket(
                    leftDegrees = opened + rattleLeft,
                    rightDegrees = opened + rattleRight,
                    openness = flaps.value.coerceIn(0f, 1f),
                    spill = spill.value,
                    spills = if (open) spills else emptyList(),
                )
            }
        }
    }

    /**
     * The basket with its left lid swung up [leftDegrees] and its right one [rightDegrees], its
     * contents showing once it's [openness] open, and the apples it [spills] [spill] of the way
     * through tumbling out.
     */
    private fun CupDrawScope.drawBasket(leftDegrees: Float, rightDegrees: Float, openness: Float, spill: Float, spills: List<BasketSpill>) {
        if (leftDegrees < 0.5f && rightDegrees < 0.5f) {
            drawBodyImage()
            drawDrape()
            drawFlap(right = false, leftDegrees)
            drawFlap(right = true, rightDegrees)
            drawHandle()
            return
        }
        // Lifted: far to near - the left lid once it's swung out past upright over the far end,
        // everything inside, the walls in front of it, the handle over the middle, then the right
        // lid, above its wall - and a left lid still lying low over the inside, rattling in the
        // shake, over the lot.
        val leftOut = leftDegrees > 90f
        if (leftOut) drawFlap(right = false, leftDegrees)
        drawOpening()
        if (openness > 0.05f) drawContents()
        drawBodyImage()
        drawDrape()
        if (!leftOut) drawFlap(right = false, leftDegrees)
        drawHandle()
        drawFlap(right = true, rightDegrees)
        // Only while it's open - gone the moment it starts to shut, and never there when the lids
        // just rattle in the shake.
        if (spills.isNotEmpty()) drawSpill(spills, spill)
    }

    private data class BasketBody(val id: String)

    /** The body, painted once at the basket's scale - so stamped with that scale undone. */
    private fun CupDrawScope.drawBodyImage() {
        val pivot = basketPivot(size)
        unscaledAbout(BASKET_SCALE, pivot) {
            drawCachedSurface(BasketBody(id)) { scaledAbout(BASKET_SCALE, pivot) { CupDrawScope(this, CupShape.SQUAT, CupPose(0f, 0f)).paintBody() } }
        }
    }

    // --- The view ---------------------------------------------------------------------------------

    /** How far [x], [z] lies towards the viewer, once the basket is turned. */
    private fun towardViewer(x: Float, z: Float): Float = x * BasketYawSin + z * BasketYawCos

    private fun CupDrawScope.point(x: Float, y: Float, z: Float): Offset =
        Offset(gx(BASKET_CENTRE_X + x * BasketYawCos - z * BasketYawSin), gy(BASKET_BASE_Y - y + towardViewer(x, z) * CUP_VIEW_SQUASH))

    /** How squarely a surface facing ([nx], [ny], [nz]) faces the viewer: above 0 it's seen. */
    private fun facing(nx: Float, ny: Float, nz: Float): Float {
        val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        return (nx * BasketYawSin * BasketElevationCos + ny * BasketElevationSin + nz * BasketYawCos * BasketElevationCos) / length
    }

    private fun CupDrawScope.poly(vararg corners: Triple<Float, Float, Float>): Path = Path().apply {
        corners.forEachIndexed { i, (x, y, z) ->
            val p = point(x, y, z)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }

    /** The half-width and half-depth of the basket's walls at height [y] - narrower towards the bottom. */
    private fun halfAt(y: Float): Pair<Float, Float> {
        val inset = BASKET_TAPER * (1f - y / BASKET_HEIGHT)
        return (BASKET_WIDTH / 2f - inset) to (BASKET_DEPTH / 2f - inset)
    }

    /** A point on the front wall: [u] from its left end (0) to its right (1), at height [y]. */
    private fun CupDrawScope.onFront(u: Float, y: Float): Offset {
        val (hw, hd) = halfAt(y)
        return point(-hw + 2f * hw * u, y, hd)
    }

    /** A point on the right end wall: [u] from its front (0) to its back (1), at height [y]. */
    private fun CupDrawScope.onEnd(u: Float, y: Float): Offset {
        val (hw, hd) = halfAt(y)
        return point(hw, y, hd - 2f * hd * u)
    }

    // --- The body, painted once -------------------------------------------------------------------

    /**
     * The body: its shadow (its own footprint on the table, cast right and back), the front and
     * right end woven in rows of wicker round upright stakes, a braided rim along the top and a
     * thicker one round the foot, and the peg on the front the latch loops over.
     */
    private fun CupDrawScope.paintBody() {
        val bottom = halfAt(0f)
        for ((spread, alpha) in listOf(4f to 0.06f, 2.6f to 0.08f, 1.3f to 0.1f, 0f to 0.14f)) {
            val w = bottom.first + spread
            val d = bottom.second + spread
            drawPath(poly(Triple(-w + 3f, 0f, d - 2f), Triple(w + 3f, 0f, d - 2f), Triple(w + 3f, 0f, -d - 2f), Triple(-w + 3f, 0f, -d - 2f)), Color.Black.copy(alpha = alpha))
        }
        val front = Path().apply {
            val corners = listOf(onFront(0f, 0f), onFront(1f, 0f), onFront(1f, BASKET_HEIGHT), onFront(0f, BASKET_HEIGHT))
            moveTo(corners[0].x, corners[0].y)
            for (c in corners.drop(1)) lineTo(c.x, c.y)
            close()
        }
        val end = Path().apply {
            val corners = listOf(onEnd(0f, 0f), onEnd(1f, 0f), onEnd(1f, BASKET_HEIGHT), onEnd(0f, BASKET_HEIGHT))
            moveTo(corners[0].x, corners[0].y)
            for (c in corners.drop(1)) lineTo(c.x, c.y)
            close()
        }
        drawPath(front, palette.gap)
        drawPath(end, lerp(palette.gap, Color.Black, 0.3f))
        weave(onFrontWall = true, rows = 8, bricks = 9, shade = 0f, wall = front)
        weave(onFrontWall = false, rows = 8, bricks = 5, shade = 0.35f, wall = end)
        // Light falls from the top left: the end wall in shadow, both darker towards the foot.
        drawPath(end, Color.Black.copy(alpha = 0.18f))
        drawPath(front, Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)), startY = onFront(0f, BASKET_HEIGHT).y, endY = onFront(0f, 0f).y))
        drawPath(end, Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)), startY = onEnd(0f, BASKET_HEIGHT).y, endY = onEnd(0f, 0f).y))
        // The braided rims, round the foot and along the top.
        braid(BASKET_HEIGHT - 0.6f, gy(2.2f))
        braid(0.8f, gy(1.8f))
    }

    /**
     * Wicker woven across a wall: [rows] courses of strands, each passing in front of and behind
     * the upright stakes - so the strands in neighbouring courses are offset by half - shown as
     * rounded lengths of cane, a little different in tone, across the front wall or (not
     * [onFrontWall]) the right end.
     */
    private fun CupDrawScope.weave(onFrontWall: Boolean, rows: Int, bricks: Int, shade: Float, wall: Path) {
        fun at(u: Float, y: Float) = if (onFrontWall) onFront(u, y) else onEnd(u, y)
        val random = Random(rows * 31 + bricks)
        val rowHeight = (BASKET_HEIGHT - 1.6f) / rows
        val strands = buildList {
            for (row in 0 until rows) {
                val y0 = 1.6f + row * rowHeight
                val y1 = y0 + rowHeight * 0.86f
                val offset = if (row % 2 == 0) 0f else 0.5f
                for (k in -1..bricks) {
                    val u0 = ((k + offset) / bricks + 0.008f).coerceIn(0f, 1f)
                    val u1 = ((k + offset + 1f) / bricks - 0.008f).coerceIn(0f, 1f)
                    if (u1 <= u0) continue
                    val tone = random.nextFloat()
                    add(listOf(at(u0, y0), at(u1, y0), at(u1, y1), at(u0, y1)) to tone)
                }
            }
        }
        clipPath(wall) {
            for ((corners, tone) in strands) {
                val colour = lerp(lerp(palette.wicker, palette.wickerLight, tone * 0.7f), palette.wickerDark, shade)
                val strand = Path().apply {
                    // Rounded at its ends where it turns behind a stake.
                    val (a, b, c, d) = corners
                    moveTo(a.x, a.y)
                    lineTo(b.x, b.y)
                    quadraticTo(b.x + (b.x - a.x) * 0.06f, (b.y + c.y) / 2f, c.x, c.y)
                    lineTo(d.x, d.y)
                    quadraticTo(a.x - (b.x - a.x) * 0.06f, (a.y + d.y) / 2f, a.x, a.y)
                    close()
                }
                drawPath(strand, colour)
                // A lit edge along its top.
                drawLine(lerp(colour, Color.White, 0.25f), corners[3], corners[2], strokeWidth = (corners[0] - corners[3]).getDistance() * 0.18f)
            }
        }
    }

    /** A braided rim round the front and right end at height [y], [width] thick. */
    private fun CupDrawScope.braid(y: Float, width: Float) {
        val rim = Path().apply {
            val start = onFront(0f, y)
            moveTo(start.x, start.y)
            val corner = onFront(1f, y)
            lineTo(corner.x, corner.y)
            val back = onEnd(1f, y)
            lineTo(back.x, back.y)
        }
        drawPath(rim, palette.wickerDark, style = Stroke(width * 1.15f, cap = StrokeCap.Round))
        drawPath(rim, palette.wicker, style = Stroke(width * 0.85f, cap = StrokeCap.Round))
        // The twist of the braid: short slanted strokes all along it.
        drawPath(rim, palette.wickerLight, style = Stroke(width * 0.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 0.35f, width * 0.55f))))
    }

    // --- Lids, bar and handle -----------------------------------------------------------------------

    /**
     * One lid - the [right] one or the left - swung up [degrees] on its hinge along its end wall's
     * top: a woven top, a gingham lining underneath, and its thickness along its edges, each shown
     * only where it faces the viewer, with leather hinges wrapping from the lid down over the end
     * wall it's hinged to.
     */
    private fun CupDrawScope.drawFlap(right: Boolean, degrees: Float) {
        val a = degrees * PI.toFloat() / 180f
        val side = if (right) 1f else -1f
        val hingeX = side * BASKET_WIDTH / 2f
        val hingeY = BASKET_HEIGHT + 0.3f
        // From just past the end wall (the overhang) in to the split in the middle.
        val reach = BASKET_WIDTH / 2f - FLAP_GAP
        val half = BASKET_DEPTH / 2f + FLAP_OVERHANG
        // A point on the lid: [c] across it, front positive; [s] in from the hinge; [t] up through its thickness.
        fun at(c: Float, s: Float, t: Float) = Triple(hingeX - side * (s * cos(a) - t * sin(a)), hingeY + s * sin(a) + t * cos(a), c)
        val up = Triple(side * sin(a), cos(a), 0f)
        val inward = Triple(-side * cos(a), sin(a), 0f)
        val topFacing = facing(up.first, up.second, up.third)
        if (topFacing > 0f) {
            drawFlapTop(::at, reach, half, topFacing)
        } else {
            // The lining: gingham, its checks laid along the lid.
            drawGingham({ u, v -> at(-half + 2f * half * u, -FLAP_OVERHANG + (reach + FLAP_OVERHANG) * v, 0f) }, across = 2f * half, along = reach + FLAP_OVERHANG, light = -topFacing)
        }
        // Its edges where they show: along the split, and along its front.
        if (facing(inward.first, inward.second, inward.third) > 0f) {
            drawPath(poly(at(-half, reach, 0f), at(half, reach, 0f), at(half, reach, FLAP_THICKNESS), at(-half, reach, FLAP_THICKNESS)), palette.wickerDark)
        }
        drawPath(poly(at(half, -FLAP_OVERHANG, 0f), at(half, reach, 0f), at(half, reach, FLAP_THICKNESS), at(half, -FLAP_OVERHANG, FLAP_THICKNESS)), lerp(palette.wickerDark, palette.gap, 0.4f))
        // The leather hinges: each a strap riveted to the lid, wrapped round the hinge line and down
        // onto the end wall, which stays put.
        for (c in listOf(-BASKET_DEPTH / 4f, BASKET_DEPTH / 4f)) {
            val onLid = poly(at(c - 1.5f, 0f, FLAP_THICKNESS + 0.05f), at(c + 1.5f, 0f, FLAP_THICKNESS + 0.05f), at(c + 1.5f, 3.2f, FLAP_THICKNESS + 0.05f), at(c - 1.5f, 3.2f, FLAP_THICKNESS + 0.05f))
            if (topFacing > 0f) drawPath(onLid, palette.leather)
            if (right) {
                val wallX = BASKET_WIDTH / 2f + 0.15f
                val onWall = poly(Triple(wallX, BASKET_HEIGHT + 0.4f, c - 1.5f), Triple(wallX, BASKET_HEIGHT + 0.4f, c + 1.5f), Triple(wallX, BASKET_HEIGHT - 3f, c + 1.5f), Triple(wallX, BASKET_HEIGHT - 3f, c - 1.5f))
                drawPath(onWall, lerp(palette.leather, Color.Black, 0.2f))
                drawCircle(palette.wickerLight, gx(0.35f), point(wallX, BASKET_HEIGHT - 1.8f, c))
            }
            // The knuckle, on the hinge line.
            drawCircle(lerp(palette.leather, Color.Black, 0.35f), gx(0.8f), point(hingeX + side * 0.2f, hingeY, c))
        }
    }

    /** A lid's woven top: courses of cane running across it, a braided edge, lit by how squarely it faces the viewer. */
    private fun CupDrawScope.drawFlapTop(at: (Float, Float, Float) -> Triple<Float, Float, Float>, reach: Float, half: Float, light: Float) {
        val t = FLAP_THICKNESS
        drawPath(poly(at(-half, -FLAP_OVERHANG, t), at(half, -FLAP_OVERHANG, t), at(half, reach, t), at(-half, reach, t)), palette.gap)
        val courses = 6
        for (k in 0 until courses) {
            val s0 = reach * (k + 0.08f) / courses
            val s1 = reach * (k + 0.92f) / courses
            val tone = lerp(palette.wicker, palette.wickerLight, (0.35f + 0.5f * light) * if (k % 2 == 0) 1f else 0.75f)
            drawPath(poly(at(-half + 0.6f, s0, t), at(half - 0.6f, s0, t), at(half - 0.6f, s1, t), at(-half + 0.6f, s1, t)), tone)
        }
        // The stakes the courses are woven round, showing between them.
        for (k in 1 until 8) {
            val x = -half + 2f * half * k / 8f
            val from = at(x, 0f, t)
            val to = at(x, reach, t)
            drawLine(palette.wickerDark.copy(alpha = 0.55f), point(from.first, from.second, from.third), point(to.first, to.second, to.third), strokeWidth = gx(0.45f))
        }
        // The braided border round its outer edge and end.
        val edge = Path().apply {
            val p0 = at(-half, reach, t)
            val p1 = at(half, reach, t)
            val p2 = at(half, 0f, t)
            point(p0.first, p0.second, p0.third).let { moveTo(it.x, it.y) }
            point(p1.first, p1.second, p1.third).let { lineTo(it.x, it.y) }
            point(p2.first, p2.second, p2.third).let { lineTo(it.x, it.y) }
        }
        drawPath(edge, palette.wickerDark, style = Stroke(gy(1.5f), cap = StrokeCap.Round))
        drawPath(edge, palette.wickerLight, style = Stroke(gy(0.6f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(gy(0.5f), gy(0.6f)))))
    }

    /** Gingham over the patch of a surface [at] maps (u, v from 0 to 1) to, [across] by [along] units, [light] lit. */
    private fun CupDrawScope.drawGingham(at: (Float, Float) -> Triple<Float, Float, Float>, across: Float, along: Float, light: Float) {
        fun quad(u0: Float, v0: Float, u1: Float, v1: Float) = poly(at(u0, v0), at(u1, v0), at(u1, v1), at(u0, v1))
        val shade = 0.25f + 0.75f * light.coerceIn(0f, 1f)
        drawPath(quad(0f, 0f, 1f, 1f), lerp(lerp(palette.cloth, Color.Black, 0.4f), palette.cloth, shade))
        val band = 2.4f
        val check = lerp(lerp(palette.check, Color.Black, 0.4f), palette.check, shade).copy(alpha = 0.5f)
        var u = 0f
        while (u < across) {
            drawPath(quad(u / across, 0f, ((u + band) / across).coerceAtMost(1f), 1f), check)
            u += band * 2f
        }
        var v = 0f
        while (v < along) {
            drawPath(quad(0f, v / along, 1f, ((v + band) / along).coerceAtMost(1f)), check)
            v += band * 2f
        }
    }

    /**
     * The handle: cane wrapped in a spiral of thinner cane, an upright U arching front to back over
     * the split. Its legs stand off the front and back walls on leather brackets, wider apart than
     * the lids reach, and run straight up past the lids before it arches over - so the lids open and
     * shut under it. Its back leg is hidden below the rim, behind the basket.
     */
    private fun CupDrawScope.drawHandle() {
        val (_, hd) = halfAt(BASKET_HEIGHT)
        val pivotY = BASKET_HEIGHT - 3f
        val legZ = hd + FLAP_OVERHANG + HANDLE_STANDOFF
        val legTop = BASKET_HEIGHT + HANDLE_LEG
        val handle = Path()
        val points = buildList {
            // Up the front leg...
            for (i in 0..4) add(Triple(0f, pivotY + (legTop - pivotY) * i / 4f, legZ))
            // ...over the top, as half an ellipse...
            for (i in 1 until 24) {
                val angle = PI.toFloat() * i / 24f
                add(Triple(0f, legTop + HANDLE_RISE * sin(angle), legZ * cos(angle)))
            }
            // ...and down the back leg to the rim, where the basket hides the rest.
            for (i in 0..3) add(Triple(0f, legTop - (legTop - BASKET_HEIGHT - 1.4f) * i / 3f, -legZ))
        }
        points.forEachIndexed { i, (x, y, z) ->
            val p = point(x, y, z)
            if (i == 0) handle.moveTo(p.x, p.y) else handle.lineTo(p.x, p.y)
        }
        drawPath(handle, palette.wickerDark, style = Stroke(gy(3.2f), cap = StrokeCap.Round))
        drawPath(handle, palette.wicker, style = Stroke(gy(2.4f), cap = StrokeCap.Round))
        drawPath(handle, palette.wickerDark, style = Stroke(gy(2.4f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(gy(0.4f), gy(1f)))))
        drawPath(handle, palette.wickerLight.copy(alpha = 0.7f), style = Stroke(gy(0.6f)))
        // The front bracket: a leather strap from the wall out to the leg, and the boss pinning the leg to it.
        val wall = point(0f, pivotY, hd + 0.2f)
        val boss = point(0f, pivotY, legZ)
        drawLine(lerp(palette.leather, Color.Black, 0.25f), wall, boss, strokeWidth = gy(2.6f), cap = StrokeCap.Round)
        drawCircle(lerp(palette.leather, Color.Black, 0.3f), gx(2f), boss)
        drawCircle(palette.leather, gx(1.6f), boss)
        drawCircle(palette.wickerLight, gx(0.5f), boss)
    }

    // --- Inside -------------------------------------------------------------------------------------

    /**
     * The open top: the far walls' tops, the inside of the back and left walls down to the cloth,
     * and the cloth itself lying in folds well below the rim - mostly hidden under the food.
     */
    private fun CupDrawScope.drawOpening() {
        val (hw, hd) = halfAt(BASKET_HEIGHT)
        val y = BASKET_HEIGHT
        drawPath(poly(Triple(-hw, y, hd), Triple(hw, y, hd), Triple(hw, y, -hd), Triple(-hw, y, -hd)), palette.wicker)
        val iw = hw - BASKET_WALL
        val id = hd - BASKET_WALL
        // The inside of the walls that face the viewer: the back's lit, the left's in shadow, woven like the outside.
        val back = poly(Triple(-iw, CLOTH_Y, -id), Triple(iw, CLOTH_Y, -id), Triple(iw, y, -id), Triple(-iw, y, -id))
        val left = poly(Triple(-iw, CLOTH_Y, id), Triple(-iw, CLOTH_Y, -id), Triple(-iw, y, -id), Triple(-iw, y, id))
        drawPath(back, lerp(palette.wicker, palette.gap, 0.35f))
        drawPath(left, lerp(palette.wicker, palette.gap, 0.6f))
        for (k in 1..3) {
            val wy = CLOTH_Y + (y - CLOTH_Y) * k / 4f
            drawLine(palette.gap.copy(alpha = 0.7f), point(-iw, wy, -id), point(iw, wy, -id), strokeWidth = gy(0.35f))
            drawLine(palette.gap.copy(alpha = 0.7f), point(-iw, wy, id), point(-iw, wy, -id), strokeWidth = gy(0.35f))
        }
        // The cloth, in soft folds, its checks across the whole inside.
        drawGingham({ u, v -> Triple(-iw + 2f * iw * u, CLOTH_Y + 0.7f * sin(u * 9f) * sin(v * 3f), -id + 2f * id * v) }, across = 2f * iw, along = 2f * id, light = 0.85f)
    }

    /**
     * The picnic, packed in on the cloth and drawn back to front so each piece rests against the
     * ones behind it: a bottle of water lying along the back, grapes, an orange, a sandwich, a wedge of
     * cheese, apples, and a baguette along the front. All of it lies low enough for the lids to shut
     * over it.
     */
    private fun CupDrawScope.drawContents() {
        val pieces = listOf<Pair<Pair<Float, Float>, CupDrawScope.() -> Unit>>(
            (-10f to -7f) to { drawWaterBottle(from = Triple(-17f, CLOTH_Y + 2.6f, -8f), to = Triple(0f, CLOTH_Y + 2.6f, -6.5f)) },
            (5f to -6.5f) to { drawGrapes(5f, -6.5f) },
            (12.5f to -6f) to { drawOrange(point(12.5f, CLOTH_Y + 3.3f, -6f), 3.3f) },
            (-10f to 0f) to { drawSandwich(-10f, 0f) },
            (2.5f to 0.5f) to { drawCheese(2.5f, 0.5f) },
            (10.5f to 1.5f) to { drawApple(point(10.5f, CLOTH_Y + 3.4f, 1.5f), 3.4f, red = true) },
            (14f to 7.5f) to { drawApple(point(14f, CLOTH_Y + 3.2f, 7.5f), 3.2f, red = false) },
            (-5f to 8f) to { drawBaguette(Triple(-17f, CLOTH_Y + 2.6f, 7.5f), Triple(8f, CLOTH_Y + 2.8f, 9f)) },
        )
        for ((_, draw) in pieces.sortedBy { (at, _) -> towardViewer(at.first, at.second) }) draw()
    }

    /**
     * A bottle of water lying on the cloth, from its base at [from] to its cap at [to]: clear blue
     * plastic with ridges round it, the water inside lying level along its lower side, a paper
     * label, and a blue screw cap.
     */
    private fun CupDrawScope.drawWaterBottle(from: Triple<Float, Float, Float>, to: Triple<Float, Float, Float>) {
        val plastic = Color(0xFFB8DDF2)
        val water = Color(0xFF5FAEE0)
        val base = point(from.first, from.second, from.third)
        val end = point(to.first, to.second, to.third)
        val shoulder = base + (end - base) * 0.72f
        val neck = base + (end - base) * 0.86f
        val body = gy(5f)
        drawLine(Color.Black.copy(alpha = 0.22f), base + Offset(0f, gy(1.6f)), neck + Offset(0f, gy(1.6f)), strokeWidth = body, cap = StrokeCap.Round)
        drawLine(lerp(plastic, Color(0xFF3A6E90), 0.35f), base, shoulder, strokeWidth = body, cap = StrokeCap.Round)
        drawLine(plastic, base, shoulder, strokeWidth = body * 0.84f, cap = StrokeCap.Round)
        // The water, lying along its lower side.
        drawLine(water.copy(alpha = 0.75f), base + Offset(0f, body * 0.16f), shoulder + Offset(0f, body * 0.16f), strokeWidth = body * 0.5f, cap = StrokeCap.Round)
        // The tapering shoulder and neck.
        drawLine(plastic, shoulder, neck, strokeWidth = body * 0.55f, cap = StrokeCap.Round)
        // Ridges moulded round it.
        val along = (end - base) / (end - base).getDistance()
        val across = Offset(-along.y, along.x)
        for (k in 1..4) {
            val at = base + (shoulder - base) * (0.08f * k)
            drawLine(Color.White.copy(alpha = 0.55f), at - across * body * 0.42f, at + across * body * 0.42f, strokeWidth = gy(0.35f))
        }
        // The label.
        val labelFrom = base + (shoulder - base) * 0.42f
        val labelTo = base + (shoulder - base) * 0.82f
        drawLine(Color.White, labelFrom, labelTo, strokeWidth = body * 0.86f)
        drawLine(Color(0xFF2E7BC4), labelFrom + (labelTo - labelFrom) * 0.15f, labelTo - (labelTo - labelFrom) * 0.15f, strokeWidth = body * 0.3f)
        // The cap.
        drawLine(Color(0xFF1F5FB0), neck, end, strokeWidth = body * 0.42f, cap = StrokeCap.Butt)
        drawLine(Color.White.copy(alpha = 0.6f), base + (shoulder - base) * 0.05f - across * body * 0.25f, labelFrom - across * body * 0.25f, strokeWidth = gy(0.5f), cap = StrokeCap.Round)
    }

    /** A bunch of black grapes lying on the cloth at ([x], [z]), its stalk at the top. */
    private fun CupDrawScope.drawGrapes(x: Float, z: Float) {
        val top = point(x, CLOTH_Y + 4f, z)
        val r = gx(1.5f)
        val grape = Color(0xFF4A2350)
        // Rows of grapes narrowing to the bunch's tip, every grape touching its neighbours.
        val rows = listOf(4, 4, 3, 2, 1)
        drawOval(Color.Black.copy(alpha = 0.25f), topLeft = top + Offset(-r * 3.2f, r * 3.8f), size = Size(r * 7f, r * 2.2f))
        for ((row, count) in rows.withIndex()) {
            for (k in 0 until count) {
                val at = top + Offset((k - (count - 1) / 2f) * r * 1.7f + row * r * 0.6f, row * r * 1.35f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF9A6AA6), grape, lerp(grape, Color.Black, 0.5f)), at - Offset(r * 0.35f, r * 0.35f), r * 1.4f), r, at)
                drawCircle(Color.White.copy(alpha = 0.45f), r * 0.22f, at - Offset(r * 0.35f, r * 0.4f))
            }
        }
        drawLine(Color(0xFF5D4022), top - Offset(r * 0.5f, r * 0.8f), top + Offset(-r * 1.6f, -r * 2f), strokeWidth = gy(0.5f), cap = StrokeCap.Round)
    }

    /** An orange centred at [at], [radius] across: dimpled peel and a little green stalk scar. */
    private fun CupDrawScope.drawOrange(at: Offset, radius: Float) {
        val r = gx(radius)
        val peel = Color(0xFFF08A1C)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC27A), peel, lerp(peel, Color.Black, 0.35f)), at - Offset(r * 0.35f, r * 0.35f), r * 1.5f), r, at)
        val random = Random(5)
        repeat(14) {
            val a = random.nextFloat() * 2f * PI.toFloat()
            val d = random.nextFloat() * r * 0.8f
            drawCircle(lerp(peel, Color.Black, 0.2f).copy(alpha = 0.5f), r * 0.05f, at + Offset(cos(a) * d, sin(a) * d))
        }
        drawCircle(Color(0xFF5E8A2A), r * 0.12f, at - Offset(r * 0.1f, r * 0.7f))
    }

    /** A sandwich cut corner to corner, lying on the cloth at ([x], [z]): its top slice, and its cut face showing the filling. */
    private fun CupDrawScope.drawSandwich(x: Float, z: Float) {
        val y = CLOTH_Y
        val h = 4.2f
        val a = Triple(x - 5.5f, y, z + 4f)
        val b = Triple(x + 5f, y, z + 4.5f)
        val c = Triple(x - 3f, y, z - 5f)
        fun up(p: Triple<Float, Float, Float>, by: Float) = p.copy(second = p.second + by)
        val bread = Color(0xFFF3DDB0)
        val crust = Color(0xFFB9783A)
        // The cut face along the front: bread, lettuce, tomato, ham, bread.
        val layers = listOf(0f to bread, 1.2f to Color(0xFF6DB33F), 1.8f to Color(0xFFE0443A), 2.4f to Color(0xFFE8A0A0), 3f to bread, h to bread)
        for (i in 0 until layers.size - 1) {
            val (from, colour) = layers[i]
            val to = layers[i + 1].first
            drawPath(poly(up(a, from), up(b, from), up(b, to), up(a, to)), colour)
        }
        drawPath(poly(up(b, 0f), up(c, 0f), up(c, h), up(b, h)), lerp(crust, Color.Black, 0.15f))
        drawPath(poly(up(a, h), up(b, h), up(c, h)), bread)
        drawPath(poly(up(a, h), up(b, h), up(c, h)), crust, style = Stroke(gy(0.6f)))
    }

    /** A wedge of cheese lying on the cloth at ([x], [z]): its pale rind on top, its cut faces to the front, with holes. */
    private fun CupDrawScope.drawCheese(x: Float, z: Float) {
        val y = CLOTH_Y
        val h = 4.4f
        val cheese = Color(0xFFF5D06A)
        val a = Triple(x - 4.5f, y, z + 3f)
        val b = Triple(x + 4.5f, y, z + 3.2f)
        val c = Triple(x + 0.5f, y, z - 4f)
        drawPath(poly(a.copy(second = y + h), b.copy(second = y + h), c.copy(second = y + h)), lerp(cheese, Color.White, 0.25f))
        drawPath(poly(a, b, b.copy(second = y + h), a.copy(second = y + h)), cheese)
        drawPath(poly(b, c, c.copy(second = y + h), b.copy(second = y + h)), lerp(cheese, Color.Black, 0.2f))
        val hole = lerp(cheese, Color(0xFFB08A2A), 0.6f)
        drawCircle(hole, gx(0.7f), point(x - 1.5f, y + 1.3f, z + 3.1f))
        drawCircle(hole, gx(0.45f), point(x + 1.8f, y + 2.2f, z + 3.15f))
        drawCircle(hole, gx(0.35f), point(x + 0.2f, y + 0.7f, z + 3.1f))
    }

    /** A baguette lying on the cloth from [from3] to [to3], scored along its top. */
    private fun CupDrawScope.drawBaguette(from3: Triple<Float, Float, Float>, to3: Triple<Float, Float, Float>) {
        val from = point(from3.first, from3.second, from3.third)
        val to = point(to3.first, to3.second, to3.third)
        val crust = Color(0xFFC8862E)
        val thick = gy(5f)
        drawLine(Color.Black.copy(alpha = 0.25f), from + Offset(gx(0.4f), gy(1.8f)), to + Offset(gx(0.4f), gy(1.8f)), strokeWidth = thick, cap = StrokeCap.Round)
        drawLine(lerp(crust, Color.Black, 0.25f), from, to, strokeWidth = thick, cap = StrokeCap.Round)
        drawLine(crust, from - Offset(0f, gy(0.4f)), to - Offset(0f, gy(0.4f)), strokeWidth = thick * 0.78f, cap = StrokeCap.Round)
        drawLine(lerp(crust, Color.White, 0.3f), from - Offset(0f, gy(1.1f)), to - Offset(0f, gy(1.1f)), strokeWidth = thick * 0.18f, cap = StrokeCap.Round)
        // The slashes across its top.
        val along = (to - from) / (to - from).getDistance()
        val across = Offset(-along.y, along.x)
        for (k in 1..5) {
            val at = from + (to - from) * (k / 6f) - Offset(0f, gy(0.5f))
            drawLine(Color(0xFFF2D49A), at - along * gx(1.3f) - across * gy(0.7f), at + along * gx(1.3f) + across * gy(0.3f), strokeWidth = gy(0.7f), cap = StrokeCap.Round)
        }
    }

    /** The cloth's corner hanging out over the front wall, in folds. */
    private fun CupDrawScope.drawDrape() {
        val (hw, hd) = halfAt(BASKET_HEIGHT)
        val y = BASKET_HEIGHT + 0.6f
        val z = hd + 0.35f
        drawGingham({ u, v ->
            val x = -hw * 0.7f + hw * 0.55f * u
            // Longer in the middle of the fall, in a soft point, rippled.
            val fall = 6.5f * (1f - abs(u - 0.45f) * 1.2f) + 0.5f * sin(u * 12f)
            Triple(x, y - fall * v, z + 0.4f * v)
        }, across = hw * 0.55f, along = 6f, light = 0.75f)
    }

    // --- The spill ----------------------------------------------------------------------------------

    /** The [spills] [spill] of the way through tumbling out of the basket onto the table. */
    private fun CupDrawScope.drawSpill(spills: List<BasketSpill>, spill: Float) {
        for (s in spills) {
            val t = ((spill - s.start) / BASKET_FLIGHT).coerceIn(0f, 1f)
            if (spill < s.start) continue
            // Thrown up out of the basket, over the front rim, and down onto the table.
            val x = s.fromX + (s.toX - s.fromX) * t
            val z = s.fromZ + (s.toZ - s.fromZ) * t
            val fromY = BASKET_HEIGHT + 2f
            val y = fromY + (s.radius - fromY) * t + 9f * sin(PI.toFloat() * t)
            val ground = point(x, 0f, z)
            val shadow = gx(s.radius) * (0.6f + 0.4f * t)
            drawOval(Color.Black.copy(alpha = 0.3f * t), topLeft = ground - Offset(shadow, shadow * 0.35f), size = Size(shadow * 2f, shadow * 0.7f))
            drawApple(point(x, y, z), s.radius, s.red)
        }
    }

    /** An apple centred at [at], [radius] across: red or green, with a dimple, a stalk and a leaf. */
    private fun CupDrawScope.drawApple(at: Offset, radius: Float, red: Boolean) {
        val r = gx(radius)
        val skin = if (red) Color(0xFFC62828) else Color(0xFF7CB342)
        drawCircle(Brush.radialGradient(listOf(lerp(skin, Color.White, 0.45f), skin, lerp(skin, Color.Black, 0.45f)), at - Offset(r * 0.35f, r * 0.35f), r * 1.5f), r, at)
        drawLine(Color(0xFF5D4022), at - Offset(0f, r * 0.75f), at - Offset(-r * 0.15f, r * 1.25f), strokeWidth = r * 0.16f, cap = StrokeCap.Round)
        val leaf = Path().apply {
            moveTo(at.x + r * 0.05f, at.y - r * 1.05f)
            quadraticTo(at.x + r * 0.5f, at.y - r * 1.55f, at.x + r * 0.85f, at.y - r * 1.15f)
            quadraticTo(at.x + r * 0.45f, at.y - r * 0.85f, at.x + r * 0.05f, at.y - r * 1.05f)
            close()
        }
        drawPath(leaf, Color(0xFF3E8E2E))
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.18f, at - Offset(r * 0.4f, r * 0.35f))
    }
}

/** Where the basket is enlarged about ([BASKET_SCALE]) on a canvas of [size]: the middle of its footprint. */
private fun basketPivot(size: Size): Offset =
    Offset(size.width * BASKET_CENTRE_X / CupShape.SQUAT.gridWidth, size.height * BASKET_BASE_Y / CupShape.SQUAT.gridHeight)

/** How far the basket is lifted at [t] (radians round the shake's loop), 0 to 1: up smoothly at each end of its rock. */
private fun basketLift(t: Float): Float = (1f - cos(8f * t)) / 2f

@Composable
private fun rememberBasketShakeLoop(): State<Float> = rememberInfiniteTransition(label = "basketShake").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(BASKET_SHAKE_LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
    label = "basketShakeLoop",
)

private val BasketYawSin = sin(BASKET_YAW_DEGREES * PI.toFloat() / 180f)
private val BasketYawCos = cos(BASKET_YAW_DEGREES * PI.toFloat() / 180f)
private val BasketElevationSin = sin(BASKET_ELEVATION_DEGREES * PI.toFloat() / 180f)
private val BasketElevationCos = cos(BASKET_ELEVATION_DEGREES * PI.toFloat() / 180f)

// How long one apple's tumble takes, as a fraction of the spill.
private const val BASKET_FLIGHT = 0.6f

/** An apple tumbling out: where it leaves the cloth, where it lands on the table, when it goes, its size and colour. */
private class BasketSpill(val fromX: Float, val fromZ: Float, val toX: Float, val toZ: Float, val start: Float, val radius: Float, val red: Boolean)

/**
 * A fresh handful of 1 to 3 apples to tumble out of the front: each from its own spot on the cloth,
 * landing on the table in front of the basket - inside fixed bounds, and clear of each other.
 */
private fun basketSpill(seed: Int): List<BasketSpill> {
    val random = Random(seed)
    val count = 1 + random.nextInt(3)
    val landed = mutableListOf<Pair<Float, Float>>()
    return buildList {
        repeat(count) { k ->
            var toX: Float
            var toZ: Float
            var tries = 0
            do {
                toX = -16f + random.nextFloat() * 34f
                toZ = BASKET_DEPTH / 2f + 4f + random.nextFloat() * 4f
                tries++
            } while (tries < 20 && landed.any { (x, z) -> (x - toX) * (x - toX) + (z - toZ) * (z - toZ) < 30f })
            landed += toX to toZ
            add(
                BasketSpill(
                    fromX = toX * 0.6f,
                    fromZ = BASKET_DEPTH / 2f - 4f,
                    toX = toX,
                    toZ = toZ,
                    start = 0.05f + k * 0.12f + random.nextFloat() * 0.08f,
                    radius = 2f + random.nextFloat() * 0.5f,
                    red = random.nextBoolean(),
                ),
            )
        }
    }
}
