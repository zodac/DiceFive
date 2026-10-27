package net.zodac.dicefive.ui.style

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import net.zodac.dicefive.ui.common.ambientMotion

// Shared geometry for round cups - everything that isn't the faceted prism or the bulging barrel.
// All of it is on the 58 x 84 cup grid and seen from the same raised angle (CUP_VIEW_SQUASH), so a
// circle round the cup's axis - rim, base, band, stitching - is the front half of a squashed ellipse.

/**
 * A round cup's colours: [dark] for the shaded edges, [light] for the lit side, [mid] for the rest
 * of the body and the inside of the far wall, [accent] for its trim, and [interior] for the depths.
 */
data class CupPalette(val dark: Color, val light: Color, val mid: Color, val accent: Color, val interior: Color)

/** The radius at [y] of a side tapering straight from [topRadius] at [topY] to [bottomRadius] at [bottomY]. */
internal fun taper(topRadius: Float, topY: Float, bottomRadius: Float, bottomY: Float, y: Float): Float =
    topRadius + (bottomRadius - topRadius) * (y - topY) / (bottomY - topY)

/** Adds the front half of the circle of [radius] at [y], left to right or right to left. */
internal fun CupDrawScope.frontArc(path: Path, radius: Float, y: Float, leftToRight: Boolean = true) {
    // 4/3 of the ellipse's half-height puts a cubic's midpoint exactly on the ellipse.
    val controlY = gy(y + radius * CUP_VIEW_SQUASH * 4f / 3f)
    val from = if (leftToRight) centreX - radius else centreX + radius
    val to = if (leftToRight) centreX + radius else centreX - radius
    path.cubicTo(gx(from), controlY, gx(to), controlY, gx(to), gy(y))
}

/** The front half of the circle of [radius] at [y] on its own, for a band or a line of stitching. */
internal fun CupDrawScope.frontArcPath(radius: Float, y: Float): Path = Path().apply {
    moveTo(gx(centreX - radius), gy(y))
    frontArc(this, radius, y)
}

/**
 * A round section of cup from [topRadius] at [topY] down to [bottomRadius] at [bottomY]. With
 * [curvedTop] false its top is a straight line - right for a body whose top the mouth covers; true
 * follows the front of the top circle instead, for a band or collar drawn over the body.
 */
internal fun CupDrawScope.roundSection(
    topRadius: Float,
    topY: Float,
    bottomRadius: Float,
    bottomY: Float,
    curvedTop: Boolean = false,
): Path = Path().apply {
    moveTo(gx(centreX - topRadius), gy(topY))
    lineTo(gx(centreX - bottomRadius), gy(bottomY))
    frontArc(this, bottomRadius, bottomY)
    lineTo(gx(centreX + topRadius), gy(topY))
    if (curvedTop) frontArc(this, topRadius, topY, leftToRight = false)
    close()
}

/** Dark edges and a lit band left of centre, so a flat outline reads as a curved surface. */
internal fun CupDrawScope.roundShading(palette: CupPalette, radius: Float): Brush = Brush.horizontalGradient(
    0f to palette.dark,
    0.3f to palette.light,
    0.58f to palette.mid,
    1f to palette.dark,
    startX = gx(centreX - radius),
    endX = gx(centreX + radius),
)

internal fun CupDrawScope.drawContactShadow(radius: Float, baseY: Float, alpha: Float = 0.4f) {
    val shadowRadius = radius + 3f
    drawOval(
        color = Color.Black.copy(alpha = alpha),
        topLeft = Offset(gx(centreX - shadowRadius), gy(baseY + 2f - shadowRadius * CUP_VIEW_SQUASH)),
        size = Size(gx(2f * shadowRadius), gy(2f * shadowRadius * CUP_VIEW_SQUASH)),
    )
}

internal fun CupDrawScope.mouthBounds(radius: Float, y: Float): Rect =
    Rect(Offset(gx(centreX - radius), gy(y - radius * CUP_VIEW_SQUASH)), Size(gx(2f * radius), gy(2f * radius * CUP_VIEW_SQUASH)))

/**
 * The open top: the inside of the far wall lit in [wall] just below the rim, falling into
 * [interior] further down, ringed by a [rim] lip [rimWidth] grid units thick.
 */
internal fun CupDrawScope.drawOpenMouth(radius: Float, y: Float, wall: Color, interior: Color, rim: Color, rimWidth: Float) {
    val bounds = mouthBounds(radius, y)
    val mouth = Path().apply { addOval(bounds) }
    // Worked out here: the cup grid isn't reachable from inside clipPath's own DrawScope.
    val depth = gy(6f)
    clipPath(mouth) {
        drawOval(color = wall, topLeft = bounds.topLeft, size = bounds.size)
        translate(top = depth) { drawOval(color = interior, topLeft = bounds.topLeft, size = bounds.size) }
    }
    drawOval(color = rim, topLeft = bounds.topLeft, size = bounds.size, style = Stroke(width = gy(rimWidth)))
}

/**
 * The liquid in a cup at this moment: [slope] is how far its level surface runs down the cup per
 * grid unit across, from the cup's tilt, and [level] where that surface crosses the centre line.
 * Every cup with liquid in it - glass or solid - moves it the same way; see [liquidIn].
 */
internal class Liquid(val slope: Float, val level: Float)

/**
 * The liquid in a cup with a rim of [rimRadius] at [rimY], resting at [restLevel] when upright. The
 * one physics every liquid-filled cup shares; only where each one's liquid rests differs.
 *
 * Its surface is a level plane cutting through the pot, so when the pot tips it isn't turned as a
 * rigid disc: every point round its edge stays against the wall, but rides up it on the low side
 * and down it on the high side, by the tilt's slope times its distance across - a shear, not a
 * rotation. The slope follows [CupPose.surfaceTilt], which lags the pot on a spring, so the liquid
 * rushes to the low side, overshoots and washes back before settling. And if that ever lifts the
 * low side over the lip, the level drops so it just reaches the lip instead: it brims, never spills.
 */
internal fun CupDrawScope.liquidIn(rimY: Float, rimRadius: Float, restLevel: Float): Liquid {
    val slope = tan(pose.surfaceTilt.coerceIn(-60f, 60f) * PI.toFloat() / 180f)
    return Liquid(slope, maxOf(restLevel, rimY + rimRadius * abs(slope) + LIQUID_LIP_GAP))
}

/** A point on [liquid]'s surface, given where it would be at grid (x, y) if the surface were level - in pixels. */
internal fun CupDrawScope.onLiquid(liquid: Liquid, x: Float, y: Float) = Offset(gx(x), gy(y + liquid.slope * (x - centreX)))

/** [liquid]'s surface outline at [radius] (a circle round the pot, seen from the usual raised angle), [drop] below it. */
internal fun CupDrawScope.liquidRing(liquid: Liquid, radius: Float, drop: Float = 0f) = Path().apply {
    val twoPi = 2f * PI.toFloat()
    for (step in 0..48) {
        val angle = twoPi * step / 48
        val point = onLiquid(liquid, centreX + radius * cos(angle), liquid.level + drop + radius * CUP_VIEW_SQUASH * sin(angle))
        if (step == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

/**
 * All of [liquid]: its surface outline at [radius] plus everything below its level line, reaching
 * well past the walls either side so no edge shows - clip it to the opening to trim it to what's
 * visible.
 */
internal fun CupDrawScope.liquidVolume(liquid: Liquid, radius: Float) = liquidRing(liquid, radius).apply {
    val left = onLiquid(liquid, centreX - 60f, liquid.level)
    val right = onLiquid(liquid, centreX + 60f, liquid.level)
    moveTo(left.x, left.y)
    lineTo(right.x, right.y)
    lineTo(right.x, right.y + gy(120f))
    lineTo(left.x, left.y + gy(120f))
    close()
}

// How far below the lip a tipped liquid stops - brimming, but not quite over.
private const val LIQUID_LIP_GAP = 0.8f

/**
 * [liquid] seen through a glass [body]: all of it shows, [colour] clipped to the glass's outline,
 * with its surface - a ring of [surfaceRadius] - picked out a shade lighter.
 */
internal fun CupDrawScope.drawLiquidInGlass(body: Path, liquid: Liquid, colour: Color, surfaceRadius: Float) {
    // Worked out here: the cup grid can't be reached from inside clipPath's own DrawScope.
    val volume = liquidVolume(liquid, surfaceRadius)
    val surface = liquidRing(liquid, surfaceRadius)
    clipPath(body) {
        drawPath(volume, colour.copy(alpha = 0.5f))
        drawPath(surface, lerp(colour, Color.White, 0.3f).copy(alpha = 0.35f))
    }
}

/**
 * A displacement given as it should look on screen ([dx] right, [dy] down), turned into the cup's
 * own drawing - against the cup's rotation - so it points the same way on screen however the cup is
 * tilted. What keeps steam rising straight up and a highlight on the lit side of a bubble.
 */
internal fun CupDrawScope.screenwards(dx: Float, dy: Float): Offset {
    val radians = -pose.rotation * PI.toFloat() / 180f
    val c = cos(radians)
    val s = sin(radians)
    return Offset(dx * c - dy * s, dx * s + dy * c)
}

/**
 * Three thin, distinct lines of steam (the Takeaway's) rising straight up on screen from [sources] - points on a
 * liquid's surface, so a tipped cup steams from wherever its liquid has pooled. Each line sways more
 * the higher it climbs, with a wave travelling up it as the [cycle] turns, and fades in at the bottom
 * and out at the top. It's hidden wherever it passes over the cup's solid [body], except through its
 * [opening]: steam still inside a cup only shows through the top, never through the wall.
 */
private fun CupDrawScope.drawSteamLines(
    sources: List<Offset>,
    cycle: Float,
    colour: Color,
    body: Path,
    opening: Path,
    rise: Float,
    sway: Float,
) {
    val twoPi = 2f * PI.toFloat()
    val steps = 18
    // Everything in pixels up front: the cup grid can't be reached from inside clipPath.
    val segments = sources.flatMapIndexed { wisp, source ->
        val points = (0..steps).map { step ->
            val along = step / steps.toFloat()
            val drift = sin(along * twoPi * 1.2f - cycle * twoPi + wisp * 2.1f) * sway * along
            source + screenwards(gx(drift), -gy(along * rise))
        }
        points.zipWithNext().mapIndexed { i, (from, to) ->
            // Faint where it leaves the liquid, strongest part-way up, gone by the top.
            val along = (i + 0.5f) / steps
            Triple(from, to, sin(along * PI.toFloat()) * 0.45f)
        }
    }
    val width = gy(0.9f)
    val shell = Path.combine(PathOperation.Difference, body, opening)
    clipPath(shell, clipOp = ClipOp.Difference) {
        for ((from, to, alpha) in segments) {
            drawLine(colour.copy(alpha = alpha), from, to, strokeWidth = width, cap = StrokeCap.Round)
        }
    }
}

/**
 * A cup's ambient animation clock - 0 to 1, round and round every [millis] - or null while the cup
 * is spent ([LocalCupAnimated] off), when there's no clock at all: it leaves composition entirely,
 * so a still cup asks for no frames, and starts again from the beginning when the cup comes back.
 * Read the value only inside the cup's draw, so each tick just repaints it.
 */
@Composable
internal fun rememberAmbientCycle(millis: Int): State<Float>? {
    if (!LocalCupAnimated.current || !ambientMotion) return null
    return rememberInfiniteTransition(label = "cupAmbient").animateFloat( // i18n: not translated - an animation label, not shown
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing)),
        label = "cupAmbientCycle", // i18n: not translated - an animation label, not shown
    )
}

/** A soft leather cup with a rolled lip and stitched seams near the top and bottom. */
class LeatherDiceCupStyle(override val id: String, private val palette: CupPalette) : DiceCupStyle {
    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier) {
            drawContactShadow(19f, 76f)
            drawPath(roundSection(21f, 10f, 18.5f, 76f), roundShading(palette, 21f))
            val stitching = Stroke(width = gy(0.9f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(gx(2.2f), gx(1.6f))))
            for (y in listOf(17f, 69f)) {
                drawPath(frontArcPath(taper(21f, 10f, 18.5f, 76f, y) - 1.2f, y), palette.accent, style = stitching)
            }
            drawOpenMouth(21f, 10f, palette.mid, palette.interior, palette.light, 1.8f)
        }
    }
}

/**
 * A straight casino shaker, one radius from lip to base, with a single [band] round its middle -
 * [CupPalette.accent] like its rim, unless it's given its own (the Gold cup's is the launcher
 * icon's navy, since a gold band would vanish into a gold body).
 */
class CasinoDiceCupStyle(
    override val id: String,
    private val palette: CupPalette,
    private val band: Color = palette.accent,
) : DiceCupStyle {
    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier) {
            drawContactShadow(20f, 76f)
            drawPath(roundSection(20f, 9f, 20f, 76f), roundShading(palette, 20f))
            drawPath(frontArcPath(20f, 62f), band, style = Stroke(width = gy(1f)))
            drawOpenMouth(20f, 9f, palette.mid, palette.interior, palette.accent, 1.1f)
        }
    }
}

// The oil drum: its radius, its top (a closed lid) and its foot, and where its two rolling hoops
// bulge out round it, a third of the way down and up.
private const val DRUM_RADIUS = 21f
private const val DRUM_TOP_Y = 12f
private const val DRUM_BASE_Y = 76f
private val DRUM_HOOPS = listOf(33.5f, 54.5f)
private val DrumBungSilver = Color(0xFFD8DCE0)

/**
 * A steel oil drum: a glossy enamelled cylinder with a bright highlight down it, two rolling hoops
 * standing out round it, a rolled rim at its top and foot, and a closed lid with two bungs - the big
 * one bright metal, the small one painted. [CupPalette.accent] is the hoops' and rims' shade.
 */
class OilDrumDiceCupStyle(override val id: String, private val palette: CupPalette) : DiceCupStyle, Swatched {
    override val swatch: Color = palette.mid

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier) {
            drawContactShadow(DRUM_RADIUS - 1f, DRUM_BASE_Y)
            drawPath(roundSection(DRUM_RADIUS, DRUM_TOP_Y, DRUM_RADIUS, DRUM_BASE_Y), drumEnamel(DRUM_RADIUS))
            // The rolled rim round its foot.
            drawPath(frontArcPath(DRUM_RADIUS, DRUM_BASE_Y - 2.2f), palette.light.copy(alpha = 0.45f), style = Stroke(width = gy(0.6f)))
            drawPath(frontArcPath(DRUM_RADIUS, DRUM_BASE_Y - 1.4f), palette.dark, style = Stroke(width = gy(0.7f)))
            for (y in DRUM_HOOPS) drawHoop(y)
            drawLid()
        }
    }

    /** Glossy enamel round the curve: dark at the edges, a hard bright highlight left of centre and a softer one right of it. */
    private fun CupDrawScope.drumEnamel(radius: Float): Brush = Brush.horizontalGradient(
        0f to palette.dark,
        0.12f to palette.mid,
        0.27f to palette.light,
        0.31f to lerp(palette.light, Color.White, 0.35f),
        0.36f to palette.light,
        0.55f to palette.mid,
        0.8f to lerp(palette.mid, palette.light, 0.35f),
        0.9f to palette.mid,
        1f to palette.dark,
        startX = gx(centreX - radius),
        endX = gx(centreX + radius),
    )

    /** A rolling hoop at [y]: a rib standing just proud of the side, lit along its top and shadowed under it. */
    private fun CupDrawScope.drawHoop(y: Float) {
        val rib = roundSection(DRUM_RADIUS + 0.7f, y - 1.3f, DRUM_RADIUS + 0.7f, y + 1.3f, curvedTop = true)
        drawPath(rib, drumEnamel(DRUM_RADIUS + 0.7f))
        drawPath(frontArcPath(DRUM_RADIUS + 0.6f, y - 1f), Color.White.copy(alpha = 0.35f), style = Stroke(width = gy(0.5f)))
        drawPath(frontArcPath(DRUM_RADIUS, y + 1.9f), palette.dark.copy(alpha = 0.7f), style = Stroke(width = gy(0.8f)))
        drawPath(frontArcPath(DRUM_RADIUS + 0.7f, y + 1.3f), palette.accent, style = Stroke(width = gy(0.4f)))
    }

    /**
     * The closed lid: a dished top inside the rolled chime round its edge, and its two bungs - the
     * big bright metal one towards the back left, the small painted one at the back right.
     */
    private fun CupDrawScope.drawLid() {
        val lid = mouthBounds(DRUM_RADIUS, DRUM_TOP_Y)
        val dish = mouthBounds(DRUM_RADIUS - 1.6f, DRUM_TOP_Y + 0.5f)
        drawOval(palette.mid, topLeft = lid.topLeft, size = lid.size)
        drawOval(
            Brush.linearGradient(listOf(lerp(palette.light, Color.White, 0.2f), palette.mid, palette.dark), start = dish.topLeft, end = dish.bottomRight),
            topLeft = dish.topLeft,
            size = dish.size,
        )
        drawOval(palette.dark.copy(alpha = 0.6f), topLeft = dish.topLeft, size = dish.size, style = Stroke(width = gy(0.5f)))
        // The chime: the rolled seam round the lid's edge, catching the light along its front.
        drawOval(palette.accent, topLeft = lid.topLeft, size = lid.size, style = Stroke(width = gy(1.3f)))
        drawPath(frontArcPath(DRUM_RADIUS, DRUM_TOP_Y), lerp(palette.light, Color.White, 0.3f), style = Stroke(width = gy(0.6f)))
        drawBung(-0.62f, -0.45f, 2.6f, DrumBungSilver)
        drawBung(0.66f, -0.4f, 1.6f, palette.mid)
    }

    /** A bung on the lid at ([u], [v]) - across and front to back, -1 to 1 - [radius] across, in [colour], with its raised ring. */
    private fun CupDrawScope.drawBung(u: Float, v: Float, radius: Float, colour: Color) {
        val rim = DRUM_RADIUS - 2.5f
        val at = Offset(gx(centreX + u * rim), gy(DRUM_TOP_Y + 0.5f + v * rim * CUP_VIEW_SQUASH))
        val size = Size(gx(2f * radius), gy(2f * radius * CUP_VIEW_SQUASH))
        val topLeft = at - Offset(size.width / 2f, size.height / 2f)
        drawOval(Color.Black.copy(alpha = 0.3f), topLeft = topLeft + Offset(gx(0.3f), gy(0.4f)), size = size)
        drawOval(Brush.linearGradient(listOf(lerp(colour, Color.White, 0.5f), colour, lerp(colour, Color.Black, 0.35f)), start = topLeft, end = topLeft + Offset(size.width, size.height)), topLeft = topLeft, size = size)
        val inner = Size(size.width * 0.6f, size.height * 0.6f)
        drawOval(lerp(colour, Color.Black, 0.3f), topLeft = at - Offset(inner.width / 2f, inner.height / 2f), size = inner, style = Stroke(width = gy(0.35f)))
    }
}

// The takeaway cup: its rim and base on the CupShape.MEDIUM grid, and the coffee's resting level -
// high enough to see standing, like a real full cup. Tipped all the way over it would reach past the
// lip, so there it brims at the lip instead (see [liquidIn]).
private const val TAKEAWAY_RIM_Y = 10f
private const val TAKEAWAY_RIM_RADIUS = 25f
private const val TAKEAWAY_BASE_Y = 67f
private const val TAKEAWAY_BASE_RADIUS = 19f
private const val COFFEE_LEVEL_Y = 16f
// How long each wisp of the coffee's steam takes to rise and fade.
private const val COFFEE_STEAM_MILLIS = 3200
private val Coffee = Color(0xFF3B2414)
private val Crema = Color(0xFF9A6A3E)

/**
 * A tapered paper takeaway cup with a card sleeve round its middle and a rolled lip, with hot coffee
 * inside - a crema-flecked surface that sloshes as the cup is shaken and poured, like every cup
 * with liquid in it (see [liquidIn]), with wisps of steam curling up off it while the cup is live. Drawn [CupShape.MEDIUM]: a takeaway cup is wider than a tumbler.
 */
class TakeawayDiceCupStyle(override val id: String, private val palette: CupPalette) : DiceCupStyle {
    override val shape: CupShape = CupShape.MEDIUM

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val steamCycle = rememberAmbientCycle(COFFEE_STEAM_MILLIS)
        CupCanvas(rolling, tilted, modifier, shape) {
            drawContactShadow(TAKEAWAY_BASE_RADIUS + 1f, TAKEAWAY_BASE_Y)
            val body = roundSection(TAKEAWAY_RIM_RADIUS, TAKEAWAY_RIM_Y, TAKEAWAY_BASE_RADIUS, TAKEAWAY_BASE_Y)
            drawPath(body, roundShading(palette, TAKEAWAY_RIM_RADIUS))
            val sleeve = CupPalette(
                dark = lerp(palette.accent, Color.Black, 0.35f),
                light = lerp(palette.accent, Color.White, 0.15f),
                mid = palette.accent,
                accent = palette.accent,
                interior = palette.interior,
            )
            val sleeveTop = 28f
            val sleeveBottom = 50f
            fun radiusAt(y: Float) = taper(TAKEAWAY_RIM_RADIUS, TAKEAWAY_RIM_Y, TAKEAWAY_BASE_RADIUS, TAKEAWAY_BASE_Y, y)
            drawPath(
                roundSection(radiusAt(sleeveTop) + 0.4f, sleeveTop, radiusAt(sleeveBottom) + 0.4f, sleeveBottom, curvedTop = true),
                roundShading(sleeve, TAKEAWAY_RIM_RADIUS),
            )
            drawCoffee()
            drawOval(
                palette.light,
                topLeft = mouthBounds(TAKEAWAY_RIM_RADIUS, TAKEAWAY_RIM_Y).topLeft,
                size = mouthBounds(TAKEAWAY_RIM_RADIUS, TAKEAWAY_RIM_Y).size,
                style = Stroke(width = gy(2.2f)),
            )
            // Read only here, inside the draw, so each tick just repaints the cup.
            steamCycle?.value?.let { drawCoffeeSteam(it, body) }
        }
    }

    /**
     * Three thin white lines of steam rising off the coffee - see [drawSteamLines].
     */
    private fun CupDrawScope.drawCoffeeSteam(cycle: Float, body: Path) {
        val coffee = liquidIn(TAKEAWAY_RIM_Y, TAKEAWAY_RIM_RADIUS, COFFEE_LEVEL_Y)
        val sources = listOf(-8f, 0f, 8f).map { dx -> onLiquid(coffee, centreX + dx, coffee.level - 3f) }
        val opening = Path().apply { addOval(mouthBounds(TAKEAWAY_RIM_RADIUS, TAKEAWAY_RIM_Y)) }
        drawSteamLines(sources, cycle, Color.White, body, opening, rise = 30f, sway = 3f)
    }

    /** The coffee, seen through the opening over the paper inside: dark, with a paler crema across the middle. */
    private fun CupDrawScope.drawCoffee() {
        val mouth = mouthBounds(TAKEAWAY_RIM_RADIUS, TAKEAWAY_RIM_Y)
        val coffee = liquidIn(TAKEAWAY_RIM_Y, TAKEAWAY_RIM_RADIUS, COFFEE_LEVEL_Y)
        val surfaceRadius = taper(TAKEAWAY_RIM_RADIUS, TAKEAWAY_RIM_Y, TAKEAWAY_BASE_RADIUS, TAKEAWAY_BASE_Y, COFFEE_LEVEL_Y)
        // Worked out here: the cup grid can't be reached from inside clipPath's own DrawScope.
        val volume = liquidVolume(coffee, surfaceRadius)
        val crema = liquidRing(coffee, surfaceRadius * 0.6f, drop = 0.3f)
        clipPath(Path().apply { addOval(mouth) }) {
            // The paper inside the cup, showing above the coffee.
            drawOval(lerp(palette.mid, Color.Black, 0.12f), topLeft = mouth.topLeft, size = mouth.size)
            drawPath(volume, Coffee)
            drawPath(crema, Crema.copy(alpha = 0.6f))
        }
    }
}
