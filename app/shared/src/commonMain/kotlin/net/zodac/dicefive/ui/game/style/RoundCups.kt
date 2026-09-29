package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import kotlinx.coroutines.delay
import net.zodac.dicefive.ui.theme.RabbitEye
import net.zodac.dicefive.ui.theme.RabbitFur
import net.zodac.dicefive.ui.theme.RabbitFurShade
import net.zodac.dicefive.ui.theme.RabbitPink

// Shared geometry for round cups - everything that isn't the faceted prism or the bulging barrel.
// All of it is on the 58 x 84 cup grid and seen from the same raised angle (CUP_VIEW_SQUASH), so a
// circle round the cup's axis - rim, base, band, stitching - is the front half of a squashed ellipse.

/**
 * A round cup's colours: [dark] for the shaded edges, [light] for the lit side, [mid] for the rest
 * of the body and the inside of the far wall, [accent] for its trim, and [interior] for the depths.
 */
data class CupPalette(val dark: Color, val light: Color, val mid: Color, val accent: Color, val interior: Color)

/** The radius at [y] of a side tapering straight from [topRadius] at [topY] to [bottomRadius] at [bottomY]. */
private fun taper(topRadius: Float, topY: Float, bottomRadius: Float, bottomY: Float, y: Float): Float =
    topRadius + (bottomRadius - topRadius) * (y - topY) / (bottomY - topY)

/** Adds the front half of the circle of [radius] at [y], left to right or right to left. */
private fun CupDrawScope.frontArc(path: Path, radius: Float, y: Float, leftToRight: Boolean = true) {
    // 4/3 of the ellipse's half-height puts a cubic's midpoint exactly on the ellipse.
    val controlY = gy(y + radius * CUP_VIEW_SQUASH * 4f / 3f)
    val from = if (leftToRight) centreX - radius else centreX + radius
    val to = if (leftToRight) centreX + radius else centreX - radius
    path.cubicTo(gx(from), controlY, gx(to), controlY, gx(to), gy(y))
}

/** The front half of the circle of [radius] at [y] on its own, for a band or a line of stitching. */
private fun CupDrawScope.frontArcPath(radius: Float, y: Float): Path = Path().apply {
    moveTo(gx(centreX - radius), gy(y))
    frontArc(this, radius, y)
}

/**
 * A round section of cup from [topRadius] at [topY] down to [bottomRadius] at [bottomY]. With
 * [curvedTop] false its top is a straight line - right for a body whose top the mouth covers; true
 * follows the front of the top circle instead, for a band or collar drawn over the body.
 */
private fun CupDrawScope.roundSection(
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
private fun CupDrawScope.roundShading(palette: CupPalette, radius: Float): Brush = Brush.horizontalGradient(
    0f to palette.dark,
    0.3f to palette.light,
    0.58f to palette.mid,
    1f to palette.dark,
    startX = gx(centreX - radius),
    endX = gx(centreX + radius),
)

private fun CupDrawScope.drawContactShadow(radius: Float, baseY: Float, alpha: Float = 0.4f) {
    val shadowRadius = radius + 3f
    drawOval(
        color = Color.Black.copy(alpha = alpha),
        topLeft = Offset(gx(centreX - shadowRadius), gy(baseY + 2f - shadowRadius * CUP_VIEW_SQUASH)),
        size = Size(gx(2f * shadowRadius), gy(2f * shadowRadius * CUP_VIEW_SQUASH)),
    )
}

private fun CupDrawScope.mouthBounds(radius: Float, y: Float): Rect =
    Rect(Offset(gx(centreX - radius), gy(y - radius * CUP_VIEW_SQUASH)), Size(gx(2f * radius), gy(2f * radius * CUP_VIEW_SQUASH)))

/**
 * The open top: the inside of the far wall lit in [wall] just below the rim, falling into
 * [interior] further down, ringed by a [rim] lip [rimWidth] grid units thick.
 */
private fun CupDrawScope.drawOpenMouth(radius: Float, y: Float, wall: Color, interior: Color, rim: Color, rimWidth: Float) {
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
private class Liquid(val slope: Float, val level: Float)

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
private fun CupDrawScope.liquidIn(rimY: Float, rimRadius: Float, restLevel: Float): Liquid {
    val slope = tan(pose.surfaceTilt.coerceIn(-60f, 60f) * PI.toFloat() / 180f)
    return Liquid(slope, maxOf(restLevel, rimY + rimRadius * abs(slope) + LIQUID_LIP_GAP))
}

/** A point on [liquid]'s surface, given where it would be at grid (x, y) if the surface were level - in pixels. */
private fun CupDrawScope.onLiquid(liquid: Liquid, x: Float, y: Float) = Offset(gx(x), gy(y + liquid.slope * (x - centreX)))

/** [liquid]'s surface outline at [radius] (a circle round the pot, seen from the usual raised angle), [drop] below it. */
private fun CupDrawScope.liquidRing(liquid: Liquid, radius: Float, drop: Float = 0f) = Path().apply {
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
private fun CupDrawScope.liquidVolume(liquid: Liquid, radius: Float) = liquidRing(liquid, radius).apply {
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
private fun CupDrawScope.drawLiquidInGlass(body: Path, liquid: Liquid, colour: Color, surfaceRadius: Float) {
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
private fun CupDrawScope.screenwards(dx: Float, dy: Float): Offset {
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
private fun rememberAmbientCycle(millis: Int): State<Float>? {
    if (!LocalCupAnimated.current) return null
    return rememberInfiniteTransition(label = "cupAmbient").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing)),
        label = "cupAmbientCycle",
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

/** A straight casino shaker, one radius from lip to base, with a single band of [CupPalette.accent] round its middle. */
class CasinoDiceCupStyle(override val id: String, private val palette: CupPalette) : DiceCupStyle {
    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier) {
            drawContactShadow(20f, 76f)
            drawPath(roundSection(20f, 9f, 20f, 76f), roundShading(palette, 20f))
            drawPath(frontArcPath(20f, 62f), palette.accent, style = Stroke(width = gy(1f)))
            drawOpenMouth(20f, 9f, palette.mid, palette.interior, palette.accent, 1.1f)
        }
    }
}

/**
 * A glass tumbler: see-through [tint] glass with bright edges and a vertical highlight, the whole
 * rim outlined since the far side shows through, and a [liquid] inside that stays level and
 * sloshes as the glass is shaken and poured (see [liquidIn]).
 */
class GlassDiceCupStyle(override val id: String, private val tint: Color, private val liquid: Color) : DiceCupStyle, Swatched {
    override val swatch: Color = liquid

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier) {
            drawContactShadow(17f, 77f, alpha = 0.2f)
            val body = roundSection(20f, 10f, 17f, 77f)
            drawPath(body, tint.copy(alpha = 0.18f))
            drawLiquidInGlass(body, liquidIn(rimY = 10f, rimRadius = 20f, restLevel = 38f), liquid, surfaceRadius = taper(20f, 10f, 17f, 77f, 38f))

            val edge = Color.White.copy(alpha = 0.5f)
            drawLine(edge, Offset(gx(9f), gy(10f)), Offset(gx(12f), gy(77f)), strokeWidth = gy(1f))
            drawLine(edge, Offset(gx(49f), gy(10f)), Offset(gx(46f), gy(77f)), strokeWidth = gy(1f))
            drawPath(frontArcPath(17f, 77f), edge, style = Stroke(width = gy(1f)))
            // The thick glass base, as a second, fainter line just above the bottom edge.
            drawPath(frontArcPath(17.2f, 73.5f), Color.White.copy(alpha = 0.25f), style = Stroke(width = gy(0.8f)))
            drawLine(
                Color.White.copy(alpha = 0.45f),
                Offset(gx(15f), gy(16f)),
                Offset(gx(16.5f), gy(70f)),
                strokeWidth = gy(1.6f),
                cap = StrokeCap.Round,
            )
            drawLine(
                Color.White.copy(alpha = 0.18f),
                Offset(gx(43f), gy(16f)),
                Offset(gx(41.5f), gy(70f)),
                strokeWidth = gy(1f),
                cap = StrokeCap.Round,
            )

            val mouth = mouthBounds(20f, 10f)
            drawOval(tint.copy(alpha = 0.12f), topLeft = mouth.topLeft, size = mouth.size)
            drawOval(Color.White.copy(alpha = 0.75f), topLeft = mouth.topLeft, size = mouth.size, style = Stroke(width = gy(1.1f)))
        }
    }
}

/**
 * A metal tankard: wide and stout, with two raised bands and a handle on its right. Drawn
 * [CupShape.SQUAT], since a real tankard is nearly as wide as it is tall.
 */
class TankardDiceCupStyle(override val id: String, private val palette: CupPalette) : DiceCupStyle {
    override val shape: CupShape = CupShape.SQUAT

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier, shape) {
            drawContactShadow(23f, 56f)
            // Drawn before the body, so its ends tuck in behind it.
            val handle = Path().apply {
                moveTo(gx(56f), gy(20f))
                cubicTo(gx(75f), gy(18f), gx(75f), gy(52f), gx(56f), gy(50f))
            }
            drawPath(handle, palette.dark, style = Stroke(width = gy(4.6f), cap = StrokeCap.Round))
            drawPath(handle, palette.light, style = Stroke(width = gy(1.3f), cap = StrokeCap.Round))

            drawPath(roundSection(22f, 12f, 22f, 56f), roundShading(palette, 22f))
            for (y in listOf(19f, 49f)) {
                drawPath(frontArcPath(22f, y), palette.accent, style = Stroke(width = gy(2.2f)))
            }
            drawOpenMouth(22f, 12f, palette.mid, palette.interior, palette.light, 1.5f)
        }
    }
}

// The flowerpot's soil and seedling.
private val SoilTop = Color(0xFF5A3D28)
private val SoilBottom = Color(0xFF3E2819)
private val SoilGrainDark = Color(0xFF2A1A0F)
private val SoilGrainLight = Color(0xFF7A5738)
private val SproutStem = Color(0xFF5DA34A)
private val SproutLeaf = Color(0xFF7CC95C)
private val SproutLeafDark = Color(0xFF4E8F3A)

// The Top Hat's rabbit: how long the hat has to lie tipped over and untouched before it peeks out (a
// fresh random wait each time, so it's not like clockwork), how long it stays, and how long it
// takes to pop up or duck back down.
private val RABBIT_IDLE_MILLIS = 5_000L..10_000L
private const val RABBIT_PEEK_MILLIS = 5_000L
private const val RABBIT_RISE_MILLIS = 450
private const val RABBIT_TWITCH_MILLIS = 140

// The hat's proportions: the opening's radius and the brim's height, with the crown exactly as deep
// as the opening is wide - a hat, not a stovepipe - and the brim reaching out past the opening.
private const val HAT_OPENING_RADIUS = 23f
private const val HAT_BRIM_Y = 11f
private const val HAT_DEPTH = HAT_OPENING_RADIUS * 2f
private const val HAT_BRIM_RADIUS = 36f

// The rabbit's size relative to the proportions it was sketched at.
private const val RABBIT_SCALE = 1.4f


/**
 * A top hat turned upside down: the crown as the cup, a [CupPalette.accent] ribbon, and the wide
 * brim round the opening. Drawn [CupShape.SQUAT], so it's a hat rather than a stovepipe.
 *
 * An easter egg: left lying tipped over after a roll for a few seconds ([RABBIT_IDLE_MILLIS]) with
 * nothing happening at the table (see [LocalCupActivity]), a rabbit pokes its head and paws out of
 * it for [RABBIT_PEEK_MILLIS], twitches an ear, and ducks back in - telling [LocalOnRabbitSeen] as
 * it does, which is what unlocks The Magician's Secret. Once per quiet spell - anything the player
 * does sends it straight back down and starts the wait again.
 */
class TopHatDiceCupStyle(override val id: String, private val palette: CupPalette) : DiceCupStyle {
    override val shape: CupShape = CupShape.SQUAT

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val peek = remember { Animatable(0f) }
        val earTwitch = remember { Animatable(0f) }
        val activity = LocalCupActivity.current
        val onRabbitSeen by rememberUpdatedState(LocalOnRabbitSeen.current)
        LaunchedEffect(tilted, rolling, activity) {
            peek.animateTo(0f, tween(RABBIT_RISE_MILLIS / 2))
            if (!tilted || rolling) return@LaunchedEffect
            delay(Random.nextLong(RABBIT_IDLE_MILLIS.first, RABBIT_IDLE_MILLIS.last + 1))
            val rise = tween<Float>(RABBIT_RISE_MILLIS, easing = FastOutSlowInEasing)
            onRabbitSeen()
            peek.animateTo(1f, rise)
            val twitches = 4 * RABBIT_TWITCH_MILLIS
            val hold = RABBIT_PEEK_MILLIS - 2 * RABBIT_RISE_MILLIS - twitches
            delay(hold / 2)
            repeat(2) {
                earTwitch.animateTo(1f, tween(RABBIT_TWITCH_MILLIS))
                earTwitch.animateTo(0f, tween(RABBIT_TWITCH_MILLIS))
            }
            delay(hold / 2)
            peek.animateTo(0f, rise)
        }

        CupCanvas(rolling, tilted, modifier, shape) {
            val crownBase = HAT_BRIM_Y + HAT_DEPTH
            drawContactShadow(HAT_OPENING_RADIUS + 1f, crownBase)
            drawPath(roundSection(HAT_OPENING_RADIUS, HAT_BRIM_Y + 2f, HAT_OPENING_RADIUS + 1f, crownBase), roundShading(palette, HAT_OPENING_RADIUS + 1f))
            // Just below the brim, which hides the top of the crown at the front.
            drawPath(
                roundSection(HAT_OPENING_RADIUS + 0.1f, HAT_BRIM_Y + 9f, HAT_OPENING_RADIUS + 0.3f, HAT_BRIM_Y + 17f, curvedTop = true),
                palette.accent,
            )
            // The brim: its edge, then its top face, then the opening in the middle of it.
            drawPath(roundSection(HAT_BRIM_RADIUS, HAT_BRIM_Y, HAT_BRIM_RADIUS, HAT_BRIM_Y + 2f), palette.dark)
            val brim = mouthBounds(HAT_BRIM_RADIUS, HAT_BRIM_Y)
            drawOval(roundShading(palette, HAT_BRIM_RADIUS), topLeft = brim.topLeft, size = brim.size)
            drawOpenMouth(HAT_OPENING_RADIUS, HAT_BRIM_Y, palette.dark, palette.interior, palette.dark, 0.8f)

            // Read only here, inside the draw, so the peek just repaints the hat.
            val progress = peek.value
            if (progress > 0f) drawRabbit(progress, earTwitch.value)
        }
    }

    /**
     * The rabbit, risen [progress] (0..1) of the way out of the opening: head and ears clipped to
     * the opening and everything above it, so whatever's still inside the hat stays hidden behind
     * the front rim, then its paws over that rim once it's most of the way up. [twitch] (0..1)
     * flicks its right ear.
     */
    private fun CupDrawScope.drawRabbit(progress: Float, twitch: Float) {
        // Every measurement below is a multiple of k, the rabbit's size, around the centre line.
        val k = RABBIT_SCALE
        val headY = HAT_BRIM_Y + 14f * k - 20f * k * progress
        val opening = mouthBounds(HAT_OPENING_RADIUS, HAT_BRIM_Y)
        // The opening, plus everything above its centre line across its width: where the rabbit can show.
        val visible = Path().apply {
            addOval(opening)
            addRect(Rect(Offset(opening.left, gy(-60f)), Size(opening.width, gy(60f + HAT_BRIM_Y))))
        }
        // Worked out here: the cup grid isn't reachable from inside clipPath's own DrawScope.
        val head = Rect(Offset(gx(centreX - 8f * k), gy(headY - 6.5f * k)), Size(gx(16f * k), gy(13f * k)))
        val ears = listOf(-12f to -5f, 12f + 18f * twitch to 5f).map { (angle, dx) ->
            val x = centreX + dx * k
            Triple(angle, Offset(gx(x), gy(headY - 5f * k)), Rect(Offset(gx(x - 2.5f * k), gy(headY - 19f * k)), Size(gx(5f * k), gy(15f * k))))
        }
        val eyes = listOf(-3.5f, 3.5f).map { Offset(gx(centreX + it * k), gy(headY - 1f * k)) }
        val eyeRadius = gy(1.2f * k)
        val nose = Rect(Offset(gx(centreX - 1.2f * k), gy(headY + 1.6f * k)), Size(gx(2.4f * k), gy(1.8f * k)))
        val whiskerWidth = gy(0.4f)
        val whiskers = listOf(-1f, 1f).flatMap { side ->
            listOf(-0.8f, 0.8f).map { tilt ->
                Offset(gx(centreX + side * 3f * k), gy(headY + 2.5f * k)) to
                    Offset(gx(centreX + side * 10f * k), gy(headY + (2.5f + tilt * 1.5f) * k))
            }
        }

        clipPath(visible) {
            for ((angle, pivot, ear) in ears) {
                rotate(angle, pivot) {
                    drawOval(RabbitFur, topLeft = ear.topLeft, size = ear.size)
                    val inner = ear.deflate(ear.width * 0.28f)
                    drawOval(RabbitPink, topLeft = inner.topLeft, size = inner.size)
                }
            }
            drawOval(Brush.verticalGradient(listOf(RabbitFur, RabbitFurShade), startY = head.top, endY = head.bottom), topLeft = head.topLeft, size = head.size)
            for (eye in eyes) {
                drawCircle(RabbitEye, radius = eyeRadius, center = eye)
                drawCircle(Color.White, radius = eyeRadius * 0.35f, center = eye - Offset(eyeRadius * 0.3f, eyeRadius * 0.3f))
            }
            drawOval(RabbitPink, topLeft = nose.topLeft, size = nose.size)
            for ((from, to) in whiskers) drawLine(RabbitFurShade, from, to, strokeWidth = whiskerWidth)
        }

        // Paws on the front rim, arriving over the last stretch of the rise.
        val pawAlpha = ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f)
        if (pawAlpha > 0f) {
            for (dx in listOf(-7f, 7f)) {
                val x = centreX + dx * k
                val across = (x - centreX) / HAT_OPENING_RADIUS
                val rimY = HAT_BRIM_Y + HAT_OPENING_RADIUS * CUP_VIEW_SQUASH * sqrt(1f - across * across)
                val paw = Rect(Offset(gx(x - 2.5f * k), gy(rimY - 2.2f * k)), Size(gx(5f * k), gy(3.6f * k)))
                drawOval(RabbitFur.copy(alpha = pawAlpha), topLeft = paw.topLeft, size = paw.size)
                for (toe in listOf(-0.9f, 0.9f)) {
                    drawLine(
                        RabbitFurShade.copy(alpha = pawAlpha),
                        Offset(gx(x + toe * k), gy(rimY + 0.4f * k)),
                        Offset(gx(x + toe * k), gy(rimY + 1.3f * k)),
                        strokeWidth = gy(0.35f * k),
                    )
                }
            }
        }
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

/**
 * A terracotta flowerpot: short and stout, tapering towards the base, with a thick collar round
 * the top whose rim is a flat ring round the soil - and a tiny seedling poking out of it, which
 * wiggles as the pot is shaken. Drawn [CupShape.SQUAT].
 */
class FlowerpotDiceCupStyle(override val id: String, private val palette: CupPalette) : DiceCupStyle {
    override val shape: CupShape = CupShape.SQUAT

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier, shape) {
            drawContactShadow(19f, 56f)
            drawPath(roundSection(24f, 19f, 18f, 56f), roundShading(palette, 24f))
            drawPath(roundSection(27f, 11f, 27f, 20f), roundShading(palette, 27f))
            drawPath(frontArcPath(27f, 20f), palette.dark, style = Stroke(width = gy(0.8f)))
            val rim = mouthBounds(27f, 11f)
            drawOval(palette.light, topLeft = rim.topLeft, size = rim.size)
            drawOpenMouth(23.5f, 11.6f, palette.dark, palette.interior, palette.dark, 0.6f)
            drawSoil()
            drawSprout()
        }
    }

    /** Soil filling the pot to just below its rim, with a scatter of darker and lighter grains. */
    private fun CupDrawScope.drawSoil() {
        val opening = Path().apply { addOval(mouthBounds(23.5f, 11.6f)) }
        val soil = mouthBounds(23f, 13.2f)
        val random = Random(7)
        // Worked out here: the cup grid isn't reachable from inside clipPath's own DrawScope.
        val grains = List(40) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val reach = sqrt(random.nextFloat())
            Triple(
                Offset(gx(centreX + cos(angle) * reach * 21f), gy(13.2f + sin(angle) * reach * 21f * CUP_VIEW_SQUASH)),
                gy(0.35f + random.nextFloat() * 0.5f),
                if (random.nextBoolean()) SoilGrainDark else SoilGrainLight,
            )
        }
        clipPath(opening) {
            drawOval(Brush.verticalGradient(listOf(SoilTop, SoilBottom), startY = soil.top, endY = soil.bottom), topLeft = soil.topLeft, size = soil.size)
            for ((centre, radius, colour) in grains) drawCircle(colour, radius, centre)
        }
    }

    /**
     * A tiny seedling in the middle of the soil: a curved stem and two leaves. It sways against the
     * pot's movement on the same springy lag a liquid uses ([CupPose.slosh]), so it wiggles as the
     * pot is shaken and sways back to upright after a pour.
     */
    private fun CupDrawScope.drawSprout() {
        val base = Offset(gx(centreX), gy(13.8f))
        val stem = Path().apply {
            moveTo(base.x, base.y)
            quadraticTo(gx(centreX - 1.5f), gy(9f), gx(centreX + 0.5f), gy(4.5f))
        }
        val leftLeaf = Path().apply {
            moveTo(gx(centreX + 0.5f), gy(5f))
            quadraticTo(gx(centreX - 3.5f), gy(1.5f), gx(centreX - 6.5f), gy(3.5f))
            quadraticTo(gx(centreX - 3f), gy(6.5f), gx(centreX + 0.5f), gy(5f))
            close()
        }
        val rightLeaf = Path().apply {
            moveTo(gx(centreX + 0.5f), gy(4.5f))
            quadraticTo(gx(centreX + 3f), gy(0f), gx(centreX + 6.5f), gy(1f))
            quadraticTo(gx(centreX + 4f), gy(4.8f), gx(centreX + 0.5f), gy(4.5f))
            close()
        }
        val stemWidth = gy(1.1f)
        val sway = (-pose.slosh * 1.5f).coerceIn(-30f, 30f)
        rotate(degrees = sway, pivot = base) {
            drawPath(stem, SproutStem, style = Stroke(width = stemWidth, cap = StrokeCap.Round))
            drawPath(leftLeaf, SproutLeafDark)
            drawPath(rightLeaf, SproutLeaf)
        }
    }
}

// How long one bubble takes to rise, swell and pop, and one wisp of steam to rise and fade.
private const val CAULDRON_CYCLE_MILLIS = 2400

// The cauldron's rim and opening, and the brew's level: high enough to fill most of the opening seen
// from above, standing, with a band of the dark inner wall above it. Tipped all the way over (the
// cup's resting tilt), a pot this full would overflow - the brew drops to just under the lip on its
// low side (liquidIn) and what's lost is the drips down its side; it's full again next turn. Kept
// deep enough to hold every bit of its level down to that lip, the brew only showed as a strip
// behind the front of the rim, and the pot looked empty.
private const val BREW_RIM_Y = 18f
private const val BREW_MOUTH_RADIUS = 25f
private const val BREW_LEVEL_Y = 27f
/** A bubble in the brew: where on its surface ([u] across, [v] front to back, each -1..1), when in the cycle it starts, and how big it gets. */
private data class Bubble(val u: Float, val v: Float, val start: Float, val radius: Float)

private val CAULDRON_BUBBLES = listOf(
    Bubble(-0.5f, -0.2f, 0f, 1.6f),
    Bubble(0.3f, 0.3f, 0.23f, 1.3f),
    Bubble(0.6f, -0.4f, 0.47f, 1.1f),
    Bubble(-0.1f, 0.5f, 0.66f, 1.5f),
    Bubble(-0.7f, 0.35f, 0.81f, 1f),
    Bubble(0.1f, -0.5f, 0.12f, 1.2f),
)

/**
 * A round-bellied iron cauldron with a heavy lip and a ring handle hanging from a lug on each side,
 * holding a glowing [CupPalette.accent] brew a little below the rim - deeper [CupPalette.interior]
 * round its edge - that stays level and sloshes as the pot is shaken and tipped. The brew never stops working: bubbles rise, swell and pop, froth heaves round its edge, and
 * wisps of steam drift up off it. Drawn [CupShape.SQUAT], as wide as it is tall.
 */
class CauldronDiceCupStyle(override val id: String, private val palette: CupPalette) : DiceCupStyle {
    override val shape: CupShape = CupShape.SQUAT

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        // Only composed while animating: once the cup is spent the transition leaves composition
        // entirely, so it stops asking for frames, and the brew goes calm - no bubbles, froth or
        // steam - until the next turn starts it working again from the beginning.
        val brewCycle = rememberAmbientCycle(CAULDRON_CYCLE_MILLIS)
        CupCanvas(rolling, tilted, modifier, shape) {
            // Read only here, inside the draw, so each frame just repaints the cup rather than recomposing.
            val cycle = brewCycle?.value
            drawContactShadow(26f, 56f)

            // Ring handles, drawn first so the pot's edge hides where each passes behind it.
            for (x in listOf(4f, 72f)) {
                val ring = Rect(Offset(gx(x - 3.25f), gy(23.5f)), Size(gx(6.5f), gy(7.5f)))
                drawOval(palette.dark, topLeft = ring.topLeft, size = ring.size, style = Stroke(width = gy(2.8f)))
                drawOval(palette.light, topLeft = ring.topLeft, size = ring.size, style = Stroke(width = gy(0.8f)))
            }

            val belly = Path().apply {
                moveTo(gx(13f), gy(18f))
                cubicTo(gx(1f), gy(28f), gx(1f), gy(50f), gx(18f), gy(58f))
                quadraticTo(gx(38f), gy(66f), gx(58f), gy(58f))
                cubicTo(gx(75f), gy(50f), gx(75f), gy(28f), gx(63f), gy(18f))
                close()
            }
            drawPath(belly, roundShading(palette, 37f))

            // The lugs the rings hang from, riveted to the side just under the lip.
            for (x in listOf(4.5f, 66.5f)) {
                drawRoundRect(palette.mid, topLeft = Offset(gx(x), gy(19.5f)), size = Size(gx(5f), gy(6f)), cornerRadius = CornerRadius(gy(1f)))
                drawRoundRect(
                    palette.dark,
                    topLeft = Offset(gx(x), gy(19.5f)),
                    size = Size(gx(5f), gy(6f)),
                    cornerRadius = CornerRadius(gy(1f)),
                    style = Stroke(width = gy(0.6f)),
                )
                drawCircle(palette.light, radius = gy(0.8f), center = Offset(gx(x + 2.5f), gy(22.5f)))
            }

            drawBrew(cycle)
            drawDrips()
            if (cycle != null) drawSteam(cycle, belly)
        }
    }

    /**
     * The brew, a little below the rim, seen through the opening: the dark inner wall above it, then
     * its surface - deep round the edge, glowing in the middle, and, while it's working ([cycle]),
     * froth heaving round the glow and bubbles swelling and popping.
     *
     * It sloshes like any liquid in an opaque pot (see [liquidIn]), the froth and bubbles riding
     * on its surface. A null [cycle] is a spent cup's calm brew, just the glow.
     */
    private fun CupDrawScope.drawBrew(cycle: Float?) {
        val mouth = mouthBounds(BREW_MOUTH_RADIUS, BREW_RIM_Y)
        val brew = liquidIn(BREW_RIM_Y, BREW_MOUTH_RADIUS, BREW_LEVEL_Y)
        val twoPi = 2f * PI.toFloat()
        val froth = lerp(palette.accent, Color.White, 0.35f)
        val bubble = lerp(palette.accent, Color.White, 0.5f)

        // Everything in pixels up front: the cup grid can't be reached from inside clipPath.
        val volume = liquidVolume(brew, BREW_MOUTH_RADIUS)
        val glow = liquidRing(brew, 20f, drop = 0.6f)
        val frothDots = if (cycle == null) emptyList() else (0 until 12).map { i ->
            val angle = twoPi * i / 12
            val wobble = sin(twoPi * (cycle * 2f + i * 0.37f))
            onLiquid(brew, centreX + 19f * cos(angle), brew.level + 19f * CUP_VIEW_SQUASH * sin(angle)) to gy(1.6f + 0.6f * wobble)
        }
        val bubbles = if (cycle == null) emptyList() else CAULDRON_BUBBLES.map { b ->
            val t = (cycle + b.start) % 1f
            Triple(onLiquid(brew, centreX + b.u * 14f, brew.level + b.v * 14f * CUP_VIEW_SQUASH), gy(b.radius), t)
        }

        // Each bubble's highlight sits towards the light, up and to the left on screen, however the pot tilts.
        val glint = screenwards(-1f, -1f)
        clipPath(Path().apply { addOval(mouth) }) {
            // The far inner wall, showing above the brew.
            drawOval(lerp(palette.dark, palette.mid, 0.5f), topLeft = mouth.topLeft, size = mouth.size)
            drawPath(volume, palette.interior)
            drawPath(glow, palette.accent)
            for ((dot, radius) in frothDots) {
                drawCircle(froth.copy(alpha = 0.8f), radius = radius, center = dot)
            }
            for ((at, radius, t) in bubbles) {
                when {
                    // Swelling up...
                    t < 0.7f -> {
                        val r = radius * (t / 0.7f)
                        drawCircle(bubble, radius = r, center = at)
                        drawCircle(Color.White.copy(alpha = 0.7f), radius = r * 0.35f, center = at + glint * (r * 0.35f))
                    }
                    // ...then popping into a spreading, fading ring.
                    t < 0.85f -> {
                        val burst = (t - 0.7f) / 0.15f
                        drawCircle(
                            bubble.copy(alpha = 1f - burst),
                            radius = radius * (1f + burst * 1.5f),
                            center = at,
                            style = Stroke(width = radius * 0.3f),
                        )
                    }
                }
            }
        }
        drawOval(palette.dark, topLeft = mouth.topLeft, size = mouth.size, style = Stroke(width = gy(2.6f)))
        drawOval(palette.light, topLeft = mouth.topLeft, size = mouth.size, style = Stroke(width = gy(0.7f)))
    }

    /**
     * Three faint glowing puffs in the brew's colour, swelling and fading as they rise. Each starts
     * on the brew's own surface, so tipped over they come off wherever the brew has pooled, and rise
     * straight up on screen from there (see [screenwards]) - hidden wherever they'd be over the
     * pot's iron rather than above it.
     */
    private fun CupDrawScope.drawSteam(cycle: Float, belly: Path) {
        val brew = liquidIn(BREW_RIM_Y, BREW_MOUTH_RADIUS, BREW_LEVEL_Y)
        // Everything in pixels up front: the cup grid can't be reached from inside clipPath.
        val puffs = (0 until 3).map { i ->
            val t = (cycle + i / 3f) % 1f
            // Rising off the brew's own surface - so off wherever it has pooled when the pot is
            // tipped - then straight up on screen.
            val source = onLiquid(brew, centreX + (i - 1) * 10f, brew.level - 3f)
            val centre = source + screenwards(gx(sin(t * 2f * PI.toFloat()) * 3f), -gy(8f + t * 22f))
            Triple(centre, gy(3f + t * 4f), (1f - t) * 0.25f)
        }
        // Hidden over the pot's iron, showing only above it or through its opening: a puff still
        // inside the pot is behind its wall, not stuck to the outside of it.
        val opening = Path().apply { addOval(mouthBounds(BREW_MOUTH_RADIUS, BREW_RIM_Y)) }
        clipPath(Path.combine(PathOperation.Difference, belly, opening), clipOp = ClipOp.Difference) {
            for ((centre, radius, alpha) in puffs) {
                drawCircle(palette.accent.copy(alpha = alpha), radius = radius, center = centre)
            }
        }
    }

    /**
     * A few old drips of brew down the front of the pot from the lip - fixed, not part of the
     * sloshing, just there to say the cauldron's been used. Each a streak ending in a drop.
     */
    private fun CupDrawScope.drawDrips() {
        // Duller than the live brew - dried on the iron rather than glowing.
        val stain = lerp(palette.accent, palette.interior, 0.6f).copy(alpha = 0.9f)
        val sheen = lerp(palette.accent, Color.White, 0.2f).copy(alpha = 0.25f)
        for ((x, length, width) in listOf(Triple(24f, 6f, 1.6f), Triple(31f, 9f, 2f), Triple(50f, 4.5f, 1.4f))) {
            val across = (x - centreX) / BREW_MOUTH_RADIUS
            // Where the lip's front edge is at this point, so the drip starts right on it.
            val lip = BREW_RIM_Y + BREW_MOUTH_RADIUS * CUP_VIEW_SQUASH * sqrt(1f - across * across)
            val bottom = lip + length
            // Wide where it came over the lip, narrowing as it ran, ending in a bead.
            val drip = Path().apply {
                moveTo(gx(x - width), gy(lip - 0.6f))
                cubicTo(gx(x - width * 0.5f), gy(lip + length * 0.3f), gx(x - width * 0.3f), gy(lip + length * 0.7f), gx(x - width * 0.28f), gy(bottom))
                lineTo(gx(x + width * 0.28f), gy(bottom))
                cubicTo(gx(x + width * 0.3f), gy(lip + length * 0.7f), gx(x + width * 0.5f), gy(lip + length * 0.3f), gx(x + width), gy(lip - 0.6f))
                close()
            }
            drawPath(drip, stain)
            drawCircle(stain, radius = gx(width * 0.5f), center = Offset(gx(x), gy(bottom)))
            drawLine(sheen, Offset(gx(x - width * 0.2f), gy(lip + 0.5f)), Offset(gx(x - width * 0.15f), gy(lip + length * 0.6f)), strokeWidth = gx(width * 0.25f), cap = StrokeCap.Round)
        }
    }
}

/**
 * A laboratory beaker: wide, see-through [tint] glass with white measurement marks, a pouring spout
 * on the rim, and a [liquid] that stays level and sloshes as it's shaken and poured. Drawn
 * [CupShape.SQUAT], as squat as a real beaker.
 */
class BeakerDiceCupStyle(override val id: String, private val tint: Color, private val liquid: Color) : DiceCupStyle, Swatched {
    override val swatch: Color = liquid
    override val shape: CupShape = CupShape.SQUAT

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier, shape) {
            drawContactShadow(23f, 56f, alpha = 0.2f)
            val body = roundSection(23f, 12f, 23f, 56f)
            drawPath(body, tint.copy(alpha = 0.22f))
            drawLiquidInGlass(body, liquidIn(rimY = 12f, rimRadius = 23f, restLevel = 36f), liquid, surfaceRadius = 23f)

            val glass = Color.White.copy(alpha = 0.55f)
            drawLine(glass, Offset(gx(15f), gy(12f)), Offset(gx(15f), gy(56f)), strokeWidth = gy(1f))
            drawLine(glass, Offset(gx(61f), gy(12f)), Offset(gx(61f), gy(56f)), strokeWidth = gy(1f))
            drawPath(frontArcPath(23f, 56f), glass, style = Stroke(width = gy(1f)))
            for ((index, y) in listOf(22f, 29f, 36f, 43f, 50f).withIndex()) {
                val length = if (index % 2 == 0) 8f else 5f
                drawLine(Color.White.copy(alpha = 0.75f), Offset(gx(20f), gy(y)), Offset(gx(20f + length), gy(y)), strokeWidth = gy(0.8f))
            }

            val rim = mouthBounds(23f, 12f)
            drawOval(tint.copy(alpha = 0.12f), topLeft = rim.topLeft, size = rim.size)
            drawOval(Color.White.copy(alpha = 0.8f), topLeft = rim.topLeft, size = rim.size, style = Stroke(width = gy(1.1f)))
            val spout = Path().apply {
                moveTo(gx(16.5f), gy(9f))
                quadraticTo(gx(11f), gy(8f), gx(10f), gy(10.5f))
                quadraticTo(gx(12.5f), gy(11f), gx(16.2f), gy(13.5f))
            }
            drawPath(spout, Color.White.copy(alpha = 0.8f), style = Stroke(width = gy(1f)))
        }
    }
}
