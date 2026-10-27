package net.zodac.dicefive.ui.style

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.theme.RabbitEye
import net.zodac.dicefive.ui.theme.RabbitFur
import net.zodac.dicefive.ui.theme.RabbitFurShade
import net.zodac.dicefive.ui.theme.RabbitPink

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

// The rabbit rides a short rail along the opening, like the Martini's olive: the hat's tilt sets
// where on it the rabbit sits (the low end once the hat lies tipped over), and the slosh of a shake
// or a pour nudges it by at most RABBIT_SWING_MAX and leans it by at most RABBIT_LEAN_MAX degrees -
// however hard the hat is thrown about, it never travels further than RABBIT_RAIL_HALF_LENGTH (grid
// units) either way from the centre line, and never bounces.
internal const val RABBIT_RAIL_HALF_LENGTH = 4f
private const val RABBIT_SWING_PER_DEGREE = 0.2f
private const val RABBIT_SWING_MAX = 1.5f
private const val RABBIT_LEAN_PER_DEGREE = 0.3f
private const val RABBIT_LEAN_MAX = 6f

/**
 * Where the rabbit sits on its rail (grid units from the centre line) with the hat at [rotation]
 * degrees (clockwise) and its contents lagging by [slosh] (see [CupPose.slosh]): towards whichever
 * side is lower, the whole way there once the hat is tipped over, plus a little swing from the slosh,
 * never past either end.
 */
internal fun rabbitRailX(rotation: Float, slosh: Float): Float {
    val tilt = sin(rotation * PI.toFloat() / 180f) / sin(abs(RESTING_TILT_DEGREES) * PI.toFloat() / 180f) * RABBIT_RAIL_HALF_LENGTH
    val swing = (-slosh * RABBIT_SWING_PER_DEGREE).coerceIn(-RABBIT_SWING_MAX, RABBIT_SWING_MAX)
    return (tilt + swing).coerceIn(-RABBIT_RAIL_HALF_LENGTH, RABBIT_RAIL_HALF_LENGTH)
}

/** How far (degrees, clockwise) the rabbit leans as its contents lag the hat by [slosh]: its head left behind. */
internal fun rabbitLean(slosh: Float): Float = (-slosh * RABBIT_LEAN_PER_DEGREE).coerceIn(-RABBIT_LEAN_MAX, RABBIT_LEAN_MAX)

/**
 * A top hat turned upside down: the crown as the cup, a [CupPalette.accent] ribbon, and the wide
 * brim round the opening. Drawn [CupShape.SQUAT], so it's a hat rather than a stovepipe.
 *
 * An easter egg: left lying tipped over after a roll for a few seconds ([RABBIT_IDLE_MILLIS]) with
 * nothing happening at the table (see [LocalCupActivity]), a rabbit pokes its head and paws out of
 * it for [RABBIT_PEEK_MILLIS], twitches an ear, and ducks back in - telling [LocalOnRabbitSeen] as
 * it does, which is what unlocks The Magician's Secret. Once per quiet spell - anything the player
 * does sends it straight back down and starts the wait again.
 *
 * With [rabbitAlwaysOut] - the secret hat that earns - the rabbit is simply always there, up out of
 * the opening. Either way it rides a short rail along the opening ([rabbitRailX]), moving with the
 * hat's tilt and the slosh of a shake but never far, and never bouncing.
 */
class TopHatDiceCupStyle(
    override val id: String,
    private val palette: CupPalette,
    private val rabbitAlwaysOut: Boolean = false,
) : DiceCupStyle {
    override val shape: CupShape = CupShape.SQUAT

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val peek = remember { Animatable(if (rabbitAlwaysOut) 1f else 0f) }
        val earTwitch = remember { Animatable(0f) }
        val activity = LocalCupActivity.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val onRabbitSeen by rememberUpdatedState(LocalOnRabbitSeen.current)
        val reduceMotion = LocalReduceMotion.current
        LaunchedEffect(tilted, rolling, activity, reduceMotion) {
            if (rabbitAlwaysOut) return@LaunchedEffect
            // Under reduced motion the rabbit still peeks (its sighting is an achievement), but simply appears and
            // is gone again for the same time out, with no rising, sinking or ear twitches.
            if (reduceMotion) peek.snapTo(0f) else peek.animateTo(0f, tween(RABBIT_RISE_MILLIS / 2))
            if (!tilted || rolling) return@LaunchedEffect
            lifecycle.delayWhileResumed(Random.nextLong(RABBIT_IDLE_MILLIS.first, RABBIT_IDLE_MILLIS.last + 1))
            onRabbitSeen()
            if (reduceMotion) {
                peek.snapTo(1f)
                lifecycle.delayWhileResumed(RABBIT_PEEK_MILLIS)
                peek.snapTo(0f)
                return@LaunchedEffect
            }
            val rise = tween<Float>(RABBIT_RISE_MILLIS, easing = FastOutSlowInEasing)
            peek.animateTo(1f, rise)
            val twitches = 4 * RABBIT_TWITCH_MILLIS
            val hold = RABBIT_PEEK_MILLIS - 2 * RABBIT_RISE_MILLIS - twitches
            lifecycle.delayWhileResumed(hold / 2)
            repeat(2) {
                earTwitch.animateTo(1f, tween(RABBIT_TWITCH_MILLIS))
                earTwitch.animateTo(0f, tween(RABBIT_TWITCH_MILLIS))
            }
            lifecycle.delayWhileResumed(hold / 2)
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
            if (progress > 0f) drawRabbit(progress, earTwitch.value, rabbitRailX(pose.rotation, pose.slosh), rabbitLean(pose.slosh))
        }
    }

    /**
     * The rabbit, risen [progress] (0..1) of the way out of the opening: head and ears clipped to
     * the opening and everything above it, so whatever's still inside the hat stays hidden behind
     * the front rim, then its paws over that rim once it's most of the way up. [twitch] (0..1)
     * flicks its right ear. [slide] moves the whole rabbit that many grid units along the opening,
     * leaning [lean] degrees (clockwise) as it goes.
     */
    private fun CupDrawScope.drawRabbit(progress: Float, twitch: Float, slide: Float = 0f, lean: Float = 0f) {
        // Every measurement below is a multiple of k, the rabbit's size, around the centre line.
        val k = RABBIT_SCALE
        val headY = HAT_BRIM_Y + 14f * k - 20f * k * progress
        val opening = mouthBounds(HAT_OPENING_RADIUS, HAT_BRIM_Y)
        // The opening, plus everything above its centre line: where the rabbit can show. Above the line
        // it reaches well past the opening's sides - only what's down inside the hat needs hiding, and a
        // rabbit slid to one end and leaning would otherwise have the tip of its ear cut off square.
        val visible = Path().apply {
            addOval(opening)
            addRect(Rect(Offset(opening.left - gx(HAT_OPENING_RADIUS), gy(-60f)), Size(opening.width + gx(2f * HAT_OPENING_RADIUS), gy(60f + HAT_BRIM_Y))))
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

        // Worked out here too, for the same reason.
        val slidePx = gx(slide)
        val leanPivot = Offset(gx(centreX), gy(HAT_BRIM_Y))
        clipPath(visible) {
            translate(left = slidePx) {
                rotate(lean, leanPivot) {
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
            }
        }

        // Paws on the front rim, arriving over the last stretch of the rise.
        val pawAlpha = ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f)
        if (pawAlpha > 0f) {
            for (dx in listOf(-7f, 7f)) {
                val x = centreX + dx * k + slide
                val across = ((x - centreX) / HAT_OPENING_RADIUS).coerceIn(-1f, 1f)
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
