package net.zodac.dicefive.ui.style

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import net.zodac.dicefive.data.settings.MotionGroup
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.PacedEffect

// The RGB cup, mat and background - to go with the RGB dice, and lit on the same clock (RGB_CYCLE_SECONDS),
// so a table of them changes colour together. Each comes as Rainbow (all one colour at a time) or Wave (the
// colours chasing round it), black or white, like the dice - the background with more of a keyboard's lighting effects, and dark only.

private val RgbSwatch = Color(0xFFFF3B6B)

/** White light, to be tinted the lighting's colour as it's drawn. */
private fun tint(colour: Color) = ColorFilter.tint(colour, BlendMode.SrcIn)

// While shaken, how far an RGB cup's art is through its rattle - a clock that only runs during a shake.
private const val RGB_SHAKE_FADE_MILLIS = 220

/**
 * How far into a shake an RGB cup is, in seconds, while [rolling] (never under reduced motion, which the game
 * already turns into no shake), with how strongly it's shaking, 0 to 1, easing in and out at either end.
 */
@Composable
private fun rememberRgbShake(rolling: Boolean): Pair<State<Float>, State<Float>> {
    val strength = animateFloatAsState(
        targetValue = if (rolling && !LocalReduceMotion.current) 1f else 0f,
        animationSpec = tween(RGB_SHAKE_FADE_MILLIS),
        label = "rgbCupShake", // i18n: not translated - an animation label, not shown
    )
    val seconds = remember { mutableFloatStateOf(0f) }
    if (strength.value > 0f) {
        PacedEffect(Unit) {
            var last = -1L
            while (true) {
                withInfiniteAnimationFrameNanos { now ->
                    if (last >= 0L) seconds.floatValue = (seconds.floatValue + (now - last) / 1_000_000_000f) % 1000f
                    last = now
                }
            }
        }
    }
    return seconds to strength
}

// The Halo cup: five rings, top to bottom, each a little narrower than the one above, floating over a base.
private const val HALO_RINGS = 5
private const val HALO_TOP_Y = 14f
private const val HALO_RING_GAP = 12f
private const val HALO_TOP_RADIUS = 22.5f
private const val HALO_RADIUS_STEP = 1.1f
private const val HALO_BASE_Y = 72f
private const val HALO_BASE_RADIUS = 19f
private const val HALO_BASE_THICKNESS = 3f

// Idle, each ring bobs this far (grid units) every HALO_BOB_SECONDS, out of step with its neighbours; shaken, they
// spread apart up to HALO_SPREAD each from the middle one, and rattle.
private const val HALO_BOB = 0.7f
private const val HALO_BOB_SECONDS = 2.4f
private const val HALO_SPREAD = 3f
private const val HALO_RATTLE = 0.7f

// A Wave stack's colour climbs it: each ring this far round the hues behind the one below.
private const val HALO_WAVE_SPREAD = 0.11f

/** One die of a [HaloDicePile]: where it rests, in grid units from the cup's centre line and top, turned [degrees], showing [value]. */
internal data class HaloDieSpot(val x: Float, val y: Float, val degrees: Float, val value: Int)

/** Dice heaped on the Halo's base, [size] grid units across, each drawn over those before it. */
internal data class HaloDicePile(val size: Float, val spots: List<HaloDieSpot>)

// The heap's sizes: 20 up to five dice, and 16 for the two more that a game can have.
private const val HALO_DIE_SIZE = 20f
private const val HALO_DIE_SIZE_CROWDED = 16f
private const val HALO_MOST_DICE_BIG = 5
private const val HALO_MOST_DICE = 7

// The heap's front row, on the base, with the middle die in front of the others.
private val HALO_FRONT_LEFT = HaloDieSpot(-6f, 67f, -4f, 3)
private val HALO_FRONT_RIGHT = HaloDieSpot(6f, 67f, 4f, 1)
private val HALO_FRONT_MIDDLE = HaloDieSpot(0f, 69f, 3f, 5)
private val HALO_FRONT = listOf(HALO_FRONT_LEFT, HALO_FRONT_RIGHT, HALO_FRONT_MIDDLE)

/**
 * The heap [count] dice make on the Halo's base: none to seven, more being held to seven. Every die stays inside the rings,
 * however it's turned: wider, and it would show past their sides, so the dice overlap rather than spread (`RgbHaloDiceTest`).
 */
internal fun haloDicePile(count: Int): HaloDicePile = when (count.coerceAtMost(HALO_MOST_DICE)) {
    0 -> HaloDicePile(HALO_DIE_SIZE, emptyList())
    1 -> HaloDicePile(HALO_DIE_SIZE, listOf(HALO_FRONT_MIDDLE))
    2 -> HaloDicePile(HALO_DIE_SIZE, listOf(HaloDieSpot(-6f, 67.5f, -4f, 3), HaloDieSpot(6f, 67.5f, 4f, 5)))
    3 -> HaloDicePile(HALO_DIE_SIZE, HALO_FRONT)
    4 -> HaloDicePile(HALO_DIE_SIZE, listOf(HaloDieSpot(0f, 53f, 6f, 6)) + HALO_FRONT)
    HALO_MOST_DICE_BIG -> HaloDicePile(HALO_DIE_SIZE, listOf(HaloDieSpot(-5f, 53.5f, 6f, 6), HaloDieSpot(5f, 52.5f, -5f, 2)) + HALO_FRONT)
    else -> {
        val top = if (count == HALO_MOST_DICE_BIG + 1) {
            listOf(HaloDieSpot(0.5f, 42f, 9f, 4))
        } else {
            listOf(HaloDieSpot(-3.5f, 42f, 9f, 4), HaloDieSpot(4f, 41f, -7f, 6))
        }
        val middle = listOf(HaloDieSpot(-6f, 54f, -6f, 2), HaloDieSpot(6f, 54f, 7f, 1))
        val front = listOf(HaloDieSpot(-7.8f, 68f, -6f, 3), HaloDieSpot(7.8f, 68f, 6f, 6), HaloDieSpot(0f, 70f, 3f, 5))
        HaloDicePile(HALO_DIE_SIZE_CROWDED, top + middle + front)
    }
}

// A die in the heap is laid out at the tray's usual size and drawn scaled down: a die's art is made for that size (its pips'
// padding is in dp), and its faces are the ones already painted for the tray.
private val HALO_DIE_LAYOUT_SIZE = 44.dp

// Whichever dice lie this low rest on the base, and cast a shadow on it.
private const val HALO_ON_BASE_Y = 65f

// Shaken, how far each die turns (degrees), hops and slides (grid units), and how many faces it shows a second.
internal const val HALO_DIE_TURN = 14f
private const val HALO_DIE_HOP = 6f
internal const val HALO_DIE_SLIDE = 0.8f
private const val HALO_DIE_FACES_PER_SECOND = 9f

/**
 * An RGB cup - to go with the RGB dice: a Halo stack, five separate LED rings hovering one above another in the shape
 * of a cup over a lit base, with nothing between them but the base's glow - so the dice inside ([LocalCupDice], in the
 * player's own style) show through the gaps until they're poured. [body] is the rings' sleeves and the base: black, or
 * white (frosted tubes). The rings bob gently, out of step; shaken, they jolt apart and rattle, and the dice tumble,
 * settling back together as it pours. Rainbow, it's all one colour at a time; as a [wave], the colours climb the stack
 * ring by ring. Still under reduced motion, rings together.
 *
 * Live per frame while upright: the base, its glow, up to seven of the player's dice as they draw themselves, and each
 * ring's back and front half - eight strokes a ring. Once poured, the dice are gone from the composition.
 */
class RgbHaloDiceCupStyle(override val id: String, private val body: CupPalette, private val wave: Boolean) : DiceCupStyle, Swatched {
    override val swatch: Color = RgbSwatch

    // White rings are frosted tubes that glow all through; black ones a dark sleeve round a bright core.
    private val frosted = body.mid.luminance() > 0.5f

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val seconds = rememberArtSeconds(MotionGroup.DICE_CUP)
        val (shakeSeconds, shake) = rememberRgbShake(rolling)
        val dice = LocalCupDice.current
        val pile = remember(dice.count) { haloDicePile(dice.count) }
        // Where ring [i] is: its bob, and while shaken its spread and rattle.
        fun ringOffset(i: Int): Offset {
            val strength = shake.value
            val rattle = shakeSeconds.value
            val bob = HALO_BOB * sin(2f * PI.toFloat() * seconds.value / HALO_BOB_SECONDS + i * 1.1f)
            val spread = (i - (HALO_RINGS - 1) / 2f) * HALO_SPREAD * (0.75f + 0.25f * sin(rattle * 31f + i * 1.7f))
            val jolt = HALO_RATTLE * sin(rattle * 47f + i * 2.3f)
            return Offset(strength * HALO_RATTLE * sin(rattle * 39f + i * 1.3f), bob + strength * (spread + jolt))
        }
        LayeredCupCanvas(
            rolling = rolling,
            tilted = tilted,
            modifier = modifier,
            back = {
                val phase = seconds.value / RGB_CYCLE_SECONDS
                val colour = rgbColour(phase)
                drawPool(colour)
                drawBase(colour)
                for (i in 0 until HALO_RINGS) drawRing(i, ringOffset(i), phase, front = false)
                if (holdsDice(pose)) drawDiceShadows(pile)
            },
            front = {
                val phase = seconds.value / RGB_CYCLE_SECONDS
                for (i in 0 until HALO_RINGS) drawRing(i, ringOffset(i), phase, front = true)
            },
        ) { gridUnit, pose ->
            val holding by remember(pose) { derivedStateOf { holdsDice(pose) } }
            if (holding) {
                pile.spots.forEachIndexed { index, spot ->
                    key(index) { HaloDie(dice.style, spot, index, pile.size, gridUnit, shakeSeconds, shake) }
                }
            }
        }
    }

    /** Whether the dice are still in it: gone while it's tipped past halfway, poured out - by how far it's actually turned, so they go (and come back as it stands up) on the frame it turns. */
    private fun holdsDice(pose: CupPose) = pose.rotation > RESTING_TILT_DEGREES / 2f

    /** One die of the heap, in the player's [style], tumbling by [shake]'s strength. Only a new face recomposes it. */
    @Composable
    private fun HaloDie(style: DiceStyle, spot: HaloDieSpot, index: Int, size: Float, gridUnit: Dp, shakeSeconds: State<Float>, shake: State<Float>) {
        val value by remember(spot, index) {
            derivedStateOf {
                val step = if (shake.value > 0f) (shakeSeconds.value * HALO_DIE_FACES_PER_SECOND + index * 1.7f).toInt() else 0
                (spot.value - 1 + step * (index + 2)) % PIP_FACES + 1
            }
        }
        val scale = gridUnit * size / HALO_DIE_LAYOUT_SIZE
        // A die that stands up stays standing, at rest.
        val restDegrees = if (style.standsUpright) 0f else spot.degrees
        Box(
            modifier = Modifier
                .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                .offset(x = gridUnit * (CUP_GRID_WIDTH / 2f + spot.x) - HALO_DIE_LAYOUT_SIZE / 2, y = gridUnit * spot.y - HALO_DIE_LAYOUT_SIZE / 2)
                .requiredSize(HALO_DIE_LAYOUT_SIZE)
                .graphicsLayer {
                    val strength = shake.value
                    val time = shakeSeconds.value
                    scaleX = scale
                    scaleY = scale
                    rotationZ = restDegrees + strength * HALO_DIE_TURN * sin(time * (23f + index * 3f) + index)
                    translationX = (gridUnit * strength * HALO_DIE_SLIDE * sin(time * (19f + index * 5f) + index * 3f)).toPx()
                    translationY = -(gridUnit * strength * HALO_DIE_HOP * sin(time * (29f + index * 4f) + index * 2f).coerceAtLeast(0f)).toPx()
                },
        ) {
            CompositionLocalProvider(LocalDieIndex provides index) {
                style.Die(value = value, held = false, modifier = Modifier.fillMaxSize())
            }
        }
    }

    private fun ringColour(phase: Float, ring: Int) = if (wave) rgbColour(phase + (HALO_RINGS - 1 - ring) * HALO_WAVE_SPREAD) else rgbColour(phase)

    /** The light pooled on the table round the base. */
    private fun CupDrawScope.drawPool(colour: Color) {
        val at = Offset(gx(centreX), gy(HALO_BASE_Y + HALO_BASE_THICKNESS))
        val reach = gx(HALO_BASE_RADIUS + 12f)
        scale(1f, CUP_VIEW_SQUASH, at) {
            drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.55f), colour.copy(alpha = 0.15f), Color.Transparent), center = at, radius = reach), reach, at)
        }
        drawContactShadow(HALO_BASE_RADIUS, HALO_BASE_Y + HALO_BASE_THICKNESS - 1f, alpha = 0.45f)
    }

    /** The base: a puck with an LED strip round its edge, throwing a faint beam up through the rings. */
    private fun CupDrawScope.drawBase(colour: Color) {
        val side = roundSection(HALO_BASE_RADIUS, HALO_BASE_Y, HALO_BASE_RADIUS, HALO_BASE_Y + HALO_BASE_THICKNESS)
        drawPath(side, roundShading(body, HALO_BASE_RADIUS))
        val top = mouthBounds(HALO_BASE_RADIUS, HALO_BASE_Y)
        drawOval(lerp(body.mid, body.light, 0.35f), top.topLeft, top.size)
        drawOval(lerp(body.interior, colour, 0.25f), top.topLeft + Offset(top.width * 0.12f, top.height * 0.12f), Size(top.width * 0.76f, top.height * 0.76f))
        val strip = frontArcPath(HALO_BASE_RADIUS + 0.2f, HALO_BASE_Y + HALO_BASE_THICKNESS * 0.5f)
        drawPath(strip, colour.copy(alpha = 0.3f), style = Stroke(width = gy(2.4f)))
        drawPath(strip, colour, style = Stroke(width = gy(0.9f)))
        drawPath(strip, Color.White.copy(alpha = 0.6f), style = Stroke(width = gy(0.3f)))
        // The light rising from the base: a soft glow in the hollow of the stack.
        val glowAt = Offset(gx(centreX), gy(HALO_BASE_Y - 10f))
        val glow = gx(HALO_BASE_RADIUS)
        scale(0.9f, 1.5f, glowAt) {
            drawSoftSpot(colour.copy(alpha = 0.22f), glowAt, glow)
        }
    }

    /** A shadow under each die of the [pile] that lies on the base. */
    private fun CupDrawScope.drawDiceShadows(pile: HaloDicePile) {
        val width = gx(pile.size * 1.08f)
        val height = gy(pile.size * 0.3f)
        for (spot in pile.spots) {
            if (spot.y < HALO_ON_BASE_Y) continue
            drawOval(Color.Black.copy(alpha = 0.4f), Offset(gx(centreX + spot.x) - width / 2f, gy(spot.y + pile.size * 0.5f) - height * 0.6f), Size(width, height))
        }
    }

    /** Ring [i]'s [front] half (or its back, behind the dice), moved by [offset]: a lit tube, glowing. */
    private fun CupDrawScope.drawRing(i: Int, offset: Offset, phase: Float, front: Boolean) {
        val radius = HALO_TOP_RADIUS - i * HALO_RADIUS_STEP
        val y = HALO_TOP_Y + i * HALO_RING_GAP
        val colour = ringColour(phase, i)
        val half = Path().apply {
            val ry = radius * CUP_VIEW_SQUASH
            val control = gy(y + (if (front) ry else -ry) * 4f / 3f)
            moveTo(gx(centreX - radius), gy(y))
            cubicTo(gx(centreX - radius), control, gx(centreX + radius), control, gx(centreX + radius), gy(y))
        }
        half.translate(Offset(gx(offset.x), gy(offset.y)))
        val dim = if (front) 1f else 0.45f
        drawPath(half, colour, alpha = 0.14f * dim, style = Stroke(width = gy(6f), cap = StrokeCap.Round))
        drawPath(half, colour, alpha = 0.28f * dim, style = Stroke(width = gy(3.6f), cap = StrokeCap.Round))
        if (frosted) {
            drawPath(half, lerp(colour, Color.White, 0.55f), alpha = dim, style = Stroke(width = gy(2.6f), cap = StrokeCap.Round))
            drawPath(half, Color.White, alpha = 0.8f * dim, style = Stroke(width = gy(0.9f), cap = StrokeCap.Round))
        } else {
            drawPath(half, lerp(body.mid, colour, 0.25f), alpha = dim, style = Stroke(width = gy(2.8f), cap = StrokeCap.Round))
            drawPath(half, colour, alpha = dim, style = Stroke(width = gy(1.4f), cap = StrokeCap.Round))
            drawPath(half, Color.White, alpha = 0.7f * dim, style = Stroke(width = gy(0.4f), cap = StrokeCap.Round))
        }
    }
}

// The Lantern cup: a hexagonal shell, tapering from its open top to its foot, a flat face to the front.
private const val LANTERN_TOP_Y = 12f
private const val LANTERN_TOP_RADIUS = 24f
private const val LANTERN_BASE_Y = 74f
private const val LANTERN_BASE_RADIUS = 18.5f

// The holes punched in every face, in staggered rows.
private const val LANTERN_HOLE_ROWS = 7
private const val LANTERN_HOLE_COLUMNS = 4
private const val LANTERN_HOLE_RADIUS = 2.1f

// How many board heights one round of a Wave lantern's hues is, climbing it.
private const val LANTERN_WAVE_HEIGHTS = 1.1f

/**
 * An RGB cup - to go with the RGB dice: a Lantern, a tapering hexagonal shell of dark (or white) metal punched all over
 * with hexagonal holes and lit from inside, so its colour shows only through the holes and up out of its open top,
 * spilling onto the table round its foot. Rainbow, it's all one colour at a time; as a [wave], the colours climb it
 * row by row. Shaken, the light inside flickers and flares as if overloading. Still under reduced motion.
 *
 * The shell is painted once, and its holes' light once as white; live per frame are the light's colour (a tint, or
 * for a Wave a gradient in an offscreen layer the cup's size), the glow from the top, and the pool on the table.
 */
class RgbLanternDiceCupStyle(override val id: String, private val body: CupPalette, private val wave: Boolean) : DiceCupStyle, Swatched {
    override val swatch: Color = RgbSwatch

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val seconds = rememberArtSeconds(MotionGroup.DICE_CUP)
        val (shakeSeconds, shake) = rememberRgbShake(rolling)
        CupCanvas(rolling, tilted, modifier) {
            val phase = seconds.value / RGB_CYCLE_SECONDS
            val strength = shake.value
            // Overloading: the light stutters off and on, and runs whiter.
            val stutter = if (strength > 0f && sin(shakeSeconds.value * 53f) + 0.6f * sin(shakeSeconds.value * 23f) > 0.5f) 0.55f * strength else 0f
            val brightness = 1f - stutter
            val colour = lerp(rgbColour(phase), Color.White, 0.3f * strength)
            drawPool(colour, brightness)
            drawContactShadow(LANTERN_BASE_RADIUS, LANTERN_BASE_Y, alpha = 0.45f)
            drawCachedSurface(RgbLanternShell(body)) { CupDrawScope(this, CupShape.TALL, CupPose(0f, 0f)).paintShell() }
            val holes = cachedSurface("rgb_lantern_holes") { CupDrawScope(this, CupShape.TALL, CupPose(0f, 0f)).paintHoles() }
            if (holes != null) {
                if (wave) {
                    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                    drawImage(holes, alpha = brightness)
                    val band = size.height * LANTERN_WAVE_HEIGHTS
                    // Climbing: each hue moves up as the colours cycle.
                    val shift = band * (phase % 1f)
                    drawRect(Brush.verticalGradient(rgbSpectrum(0f), startY = size.height - shift, endY = size.height - shift - band, tileMode = TileMode.Repeated), blendMode = BlendMode.SrcIn)
                    if (strength > 0f) drawRect(Color.White.copy(alpha = 0.3f * strength), blendMode = BlendMode.SrcAtop)
                    drawContext.canvas.restore()
                } else {
                    drawImage(holes, alpha = brightness, colorFilter = tint(colour))
                }
            }
            drawOpening(if (wave) rgbColour(phase + 0.5f) else colour, brightness)
        }
    }

    /** The point at [degrees] round the shell (0 is straight to the front), at [y] of radius [radius]. */
    private fun CupDrawScope.corner(degrees: Float, y: Float, radius: Float): Offset {
        val a = degrees * PI.toFloat() / 180f
        return Offset(gx(centreX + radius * sin(a)), gy(y + radius * CUP_VIEW_SQUASH * cos(a)))
    }

    private fun CupDrawScope.topCorner(degrees: Float) = corner(degrees, LANTERN_TOP_Y, LANTERN_TOP_RADIUS)

    private fun CupDrawScope.bottomCorner(degrees: Float) = corner(degrees, LANTERN_BASE_Y, LANTERN_BASE_RADIUS)

    /** The three faces that show, by their middles' angle round the shell: left, front and right. */
    private val faces = listOf(-60f, 0f, 60f)

    /** The light through the holes on the table round the foot. */
    private fun CupDrawScope.drawPool(colour: Color, brightness: Float) {
        val at = Offset(gx(centreX), gy(LANTERN_BASE_Y + 2f))
        val reach = gx(LANTERN_BASE_RADIUS + 13f)
        scale(1f, CUP_VIEW_SQUASH, at) {
            drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.5f * brightness), colour.copy(alpha = 0.15f * brightness), Color.Transparent), center = at, radius = reach), reach, at)
        }
    }

    /** The shell: its three faces in metal, lit from the left, each hole a dark punched edge, and its edges catching the light. */
    private fun CupDrawScope.paintShell() {
        for (middle in faces) {
            val face = polygonPath(listOf(topCorner(middle - 30f), topCorner(middle + 30f), bottomCorner(middle + 30f), bottomCorner(middle - 30f)))
            val shade = when {
                middle < 0f -> lerp(body.mid, body.light, 0.35f)
                middle > 0f -> lerp(body.mid, body.dark, 0.6f)
                else -> body.mid
            }
            drawPath(face, Brush.verticalGradient(listOf(lerp(shade, Color.White, 0.06f), lerp(shade, Color.Black, 0.18f)), startY = gy(LANTERN_TOP_Y), endY = gy(LANTERN_BASE_Y)))
            forEachHole(middle) { hole -> drawPath(hole, lerp(shade, Color.Black, 0.55f), style = Stroke(width = gx(0.9f))) }
        }
        for (middle in faces) {
            drawLine(lerp(body.light, Color.White, 0.25f).copy(alpha = 0.6f), topCorner(middle - 30f), bottomCorner(middle - 30f), strokeWidth = gx(0.45f))
        }
        drawLine(lerp(body.dark, Color.Black, 0.3f), topCorner(90f), bottomCorner(90f), strokeWidth = gx(0.45f))
        drawPath(polygonPath(listOf(bottomCorner(-90f), bottomCorner(-30f), bottomCorner(30f), bottomCorner(90f))), lerp(body.dark, Color.Black, 0.3f), style = Stroke(width = gx(0.6f)))
    }

    /** The holes' light, in white: each hole lit, brightest on the front face, with a little glow round it. */
    private fun CupDrawScope.paintHoles() {
        for (middle in faces) {
            val strength = if (middle == 0f) 1f else 0.82f
            forEachHole(middle) { hole -> drawPath(hole, Color.White.copy(alpha = 0.3f * strength), style = Stroke(width = gx(1.6f))) }
            forEachHole(middle) { hole -> drawPath(hole, Color.White.copy(alpha = strength)) }
        }
    }

    /** Every hole punched in the face whose middle is [middle] degrees round: small hexagons in staggered rows, foreshortened as the face is turned. */
    private fun CupDrawScope.forEachHole(middle: Float, draw: (Path) -> Unit) {
        val topLeft = topCorner(middle - 30f)
        val topRight = topCorner(middle + 30f)
        val bottomLeft = bottomCorner(middle - 30f)
        val bottomRight = bottomCorner(middle + 30f)
        val turned = cos(middle * PI.toFloat() / 180f)
        for (row in 0 until LANTERN_HOLE_ROWS) {
            val down = (row + 0.7f) / (LANTERN_HOLE_ROWS + 0.4f)
            val columns = if (row % 2 == 0) LANTERN_HOLE_COLUMNS else LANTERN_HOLE_COLUMNS - 1
            for (column in 0 until columns) {
                val across = (column + if (row % 2 == 0) 0.5f else 1f) / LANTERN_HOLE_COLUMNS
                val left = lerp(topLeft, bottomLeft, down)
                val right = lerp(topRight, bottomRight, down)
                val at = lerp(left, right, across)
                val radius = gx(LANTERN_HOLE_RADIUS) * (1f - down * 0.18f)
                draw(polygonPath(List(6) { k -> Offset(at.x + radius * turned * cos(k * PI.toFloat() / 3f), at.y + radius * sin(k * PI.toFloat() / 3f)) }))
            }
        }
    }

    /** The open top: the dark inside lit by the light within, its glow welling up out of it, and the rim. */
    private fun CupDrawScope.drawOpening(colour: Color, brightness: Float) {
        val rim = polygonPath(listOf(-90f, -30f, 30f, 90f, 150f, 210f).map { topCorner(it) })
        val middle = Offset(gx(centreX), gy(LANTERN_TOP_Y))
        val reach = gx(LANTERN_TOP_RADIUS)
        drawPath(rim, body.interior)
        drawPath(rim, Brush.radialGradient(listOf(colour.copy(alpha = 0.85f * brightness), colour.copy(alpha = 0.25f * brightness), Color.Transparent), center = middle, radius = reach))
        drawPath(rim, lerp(body.light, Color.White, 0.2f), style = Stroke(width = gx(1.2f)))
        val glow = gx(LANTERN_TOP_RADIUS * 0.9f)
        scale(1f, 0.6f, middle) {
            drawSoftSpot(colour.copy(alpha = 0.3f * brightness), middle, glow)
        }
    }
}

private data class RgbLanternShell(val body: CupPalette)

/**
 * An RGB desk mat - to go with the RGB dice: a fine-woven cloth, black or white, its edge an LED strip lighting the
 * cloth round it, and a die's five in LEDs glowing in its corner like a maker's mark. Rainbow, it's all one colour
 * at a time; as a [wave], the colours chase round the edge. Still under reduced motion.
 *
 * Live per frame: five strokes of the edge and the five LEDs; the weave is painted once.
 */
class RgbDiceMat(id: String, palette: MatPalette, private val wave: Boolean) : PatternedDiceMat(id, palette) {
    override val animated: Boolean = true

    override fun DrawScope.drawPattern() = drawPattern(seconds = 0f)

    override fun DrawScope.drawPattern(seconds: Float) {
        drawCachedSurface("mat:$id") { paintWeave() }
        val phase = seconds / RGB_CYCLE_SECONDS
        val light: Brush = if (wave) Brush.sweepGradient(rgbSpectrum(phase), center) else SolidColor(rgbColour(phase))
        val inset = 2.5.dp.toPx()
        val topLeft = Offset(inset, inset)
        val box = Size(size.width - inset * 2, size.height - inset * 2)
        val corner = CornerRadius(16.dp.toPx() - inset)
        for ((width, alpha) in listOf(16f to 0.07f, 9f to 0.14f, 5f to 0.3f)) {
            drawRoundRect(light, topLeft, box, corner, alpha = alpha, style = Stroke(width = width.dp.toPx()))
        }
        drawRoundRect(light, topLeft, box, corner, style = Stroke(width = 2.dp.toPx()))
        drawRoundRect(Color.White.copy(alpha = 0.55f), topLeft, box, corner, style = Stroke(width = 0.7.dp.toPx()))
        drawMark(phase)
    }

    /** A five in LEDs in the bottom right corner, lit the colour the edge is beside it. */
    private fun DrawScope.drawMark(phase: Float) {
        val pitch = 4.5.dp.toPx()
        val middle = Offset(size.width - 18.dp.toPx(), size.height - 18.dp.toPx())
        val turn = atan2(middle.y - center.y, middle.x - center.x) / (2f * PI.toFloat())
        val colour = rgbColour(if (wave) phase + turn else phase)
        for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, 0f to 0f, -1f to 1f, 1f to 1f)) {
            val spot = middle + Offset(dx * pitch, dy * pitch)
            val reach = pitch * 1.1f
            drawSoftSpot(colour.copy(alpha = 0.5f), spot, reach)
            drawCircle(colour, pitch * 0.32f, spot)
            drawCircle(lerp(colour, Color.White, 0.75f), pitch * 0.15f, spot)
        }
    }

    /** The cloth's weave: fine diagonal threads, a shade lighter and darker than it. */
    private fun DrawScope.paintWeave() {
        val pitch = 1.6.dp.toPx()
        val thread = 0.5.dp.toPx()
        var x = -size.height
        var odd = false
        while (x < size.width) {
            drawLine(if (odd) Color.White.copy(alpha = 0.035f) else Color.Black.copy(alpha = 0.08f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = thread)
            x += pitch
            odd = !odd
        }
    }
}

/** A lighting effect from a gaming keyboard's software, for the RGB background. */
enum class RgbEffect {
    /** The whole panel one colour at a time, going round the hues - and breathing, brighter and dimmer. */
    RAINBOW,

    /** Diagonal bands of every colour sweeping across. */
    WAVE,

    /** Rings of colour spreading out from a point, one after another. */
    RIPPLE,

    /** The colours turning round the middle like a wheel. */
    SWIRL,

    /** A dim glow going round the hues, lights twinkling on and off across it in colours of their own. */
    STARLIGHT,
}

// The LED grid behind the panel: a light every RGB_MATRIX_PITCH dp, every other row set half a pitch over.
private const val RGB_MATRIX_PITCH = 13f

// How many board widths across one round of the hues is, for the Wave's bands and the Ripple's rings.
private const val RGB_WAVE_BOARD_WIDTHS = 0.85f
private const val RGB_RIPPLE_BOARD_WIDTHS = 0.4f

// Rainbow breathes in and out every RGB_BREATH_SECONDS.
private const val RGB_BREATH_SECONDS = 4f

// Starlight: at most this many lights twinkling at once, each for RGB_TWINKLE_SECONDS of every RGB_TWINKLE_PERIOD.
private const val RGB_TWINKLES = 22
private const val RGB_TWINKLE_SECONDS = 1.4f
private const val RGB_TWINKLE_PERIOD = 3.2f

// Faint enough to read the scorecard's numbers over.
private const val RGB_MATRIX_STRENGTH = 0.5f

/**
 * An RGB light panel - to go with the RGB dice: a dark smoked panel over a grid of LEDs, each a soft point of light
 * with a faint glow spread between them, lit with one of a gaming keyboard's lighting [effect]s (see [RgbEffect]).
 * Dark, as every background is, for the scorecard's numbers to read over. Still under reduced motion.
 *
 * Live per frame: the grid's light, painted once in white, stamped and coloured - by a tint (Rainbow, Starlight) or
 * a gradient laid over it in an offscreen layer (Wave, Ripple, Swirl) - and Starlight's few twinkles.
 */
class RgbLightBackground(override val id: String, private val palette: BackgroundPalette, private val effect: RgbEffect) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    private var seconds by mutableFloatStateOf(0f)

    @Composable
    override fun Animate() {
        TwinkleClock(MotionGroup.BACKGROUND) { seconds = it }
    }

    override fun DrawScope.drawScoreAreaDecoration() {
        val pitch = RGB_MATRIX_PITCH.dp.toPx()
        val light = cachedSurface("background:rgb_matrix") { paintMatrix(pitch) } ?: return
        val phase = seconds / RGB_CYCLE_SECONDS
        when (effect) {
            RgbEffect.RAINBOW -> {
                val breath = 0.5f + 0.5f * sin(2f * PI.toFloat() * seconds / RGB_BREATH_SECONDS)
                drawImage(light, alpha = RGB_MATRIX_STRENGTH * (0.55f + 0.45f * breath), colorFilter = tint(rgbColour(phase)))
            }
            RgbEffect.STARLIGHT -> {
                drawImage(light, alpha = RGB_MATRIX_STRENGTH * 0.5f, colorFilter = tint(rgbColour(phase)))
                drawTwinkles(pitch)
            }
            else -> {
                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                drawImage(light, alpha = RGB_MATRIX_STRENGTH)
                drawRect(effectBrush(phase), blendMode = BlendMode.SrcIn)
                drawContext.canvas.restore()
            }
        }
        // The smoked panel's sheen, across its top.
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.04f), Color.Transparent), endY = size.height * 0.35f))
    }

    /** The colours laid over the light, the hues moving on as they cycle: away from the middle (Ripple), round it (Swirl) or across (Wave). */
    private fun DrawScope.effectBrush(phase: Float): Brush = when (effect) {
        RgbEffect.RIPPLE -> Brush.radialGradient(
            rgbSpectrum(-phase * 2f),
            center = Offset(size.width * 0.5f, size.height * 0.42f),
            radius = size.width * RGB_RIPPLE_BOARD_WIDTHS,
            tileMode = TileMode.Repeated,
        )
        RgbEffect.SWIRL -> Brush.sweepGradient(rgbSpectrum(phase), center)
        else -> {
            val band = size.width * RGB_WAVE_BOARD_WIDTHS
            // Diagonally, down and to the right.
            val shift = band * (phase % 1f)
            Brush.linearGradient(rgbSpectrum(0f).reversed(), start = Offset(shift, 0f), end = Offset(shift + band, band * 0.5f), tileMode = TileMode.Repeated)
        }
    }

    /** Starlight's twinkles: single lights of the grid flaring up in a colour of their own and fading. */
    private fun DrawScope.drawTwinkles(pitch: Float) {
        val columns = (size.width / pitch).toInt() + 1
        val rows = (size.height / pitch).toInt() + 1
        for (slot in 0 until RGB_TWINKLES) {
            val (where, glow) = sparkle(id.hashCode(), slot, seconds, period = RGB_TWINKLE_PERIOD, flash = RGB_TWINKLE_SECONDS) ?: continue
            val row = (where.y * rows).toInt()
            val spot = matrixSpot((where.x * columns).toInt(), row, pitch)
            val colour = rgbColour(where.x * 7f + where.y * 3f)
            val reach = pitch * 1.8f
            drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = glow), colour.copy(alpha = 0.35f * glow), Color.Transparent), center = spot, radius = reach), reach, spot)
            drawCircle(lerp(colour, Color.White, 0.55f).copy(alpha = glow), pitch * 0.2f, spot)
        }
    }

    /** The grid's light, in white: a soft point at every LED, and a faint even glow behind them all. */
    private fun DrawScope.paintMatrix(pitch: Float) {
        drawRect(Color.White.copy(alpha = 0.1f))
        val reach = pitch * 0.42f
        var row = 0
        while (row * pitch * 0.87f < size.height + pitch) {
            var column = 0
            while (column * pitch < size.width + pitch) {
                val spot = matrixSpot(column, row, pitch)
                drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0.3f), Color.Transparent), center = spot, radius = reach), reach, spot)
                column++
            }
            row++
        }
    }

    /** Where the LED in [column] and [row] is: rows a little closer than columns, every other one set half a [pitch] over. */
    private fun matrixSpot(column: Int, row: Int, pitch: Float) =
        Offset((column + if (row % 2 == 1) 0.5f else 0f) * pitch, (row + 0.5f) * pitch * 0.87f)
}
