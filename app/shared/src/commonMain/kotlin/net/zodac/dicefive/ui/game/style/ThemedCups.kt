package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.zodac.dicefive.ui.common.LocalReduceMotion

// The Glitter cup's sides: a gentle taper from its lip to its base.
private const val GLITTER_CUP_TOP_RADIUS = 21f
private const val GLITTER_CUP_TOP_Y = 10f
private const val GLITTER_CUP_BASE_RADIUS = 18f
private const val GLITTER_CUP_BASE_Y = 76f

/**
 * A glitter cup - to go with the Glitter dice: a tapered cup coated all over in [glitter], shaded
 * round its curve, with a gold lip and foot band, and a couple of sparkles flashing on it. The
 * glitter is painted once; only the sparkles are live. None under reduced motion.
 */
class GlitterDiceCupStyle(override val id: String, private val glitter: Color, private val trim: Color) : DiceCupStyle, Swatched {
    override val swatch: Color = glitter

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val seconds = rememberArtSeconds()
        CupCanvas(rolling, tilted, modifier) {
            drawContactShadow(GLITTER_CUP_BASE_RADIUS, GLITTER_CUP_BASE_Y)
            val body = roundSection(GLITTER_CUP_TOP_RADIUS, GLITTER_CUP_TOP_Y, GLITTER_CUP_BASE_RADIUS, GLITTER_CUP_BASE_Y)
            val left = gx(centreX - GLITTER_CUP_TOP_RADIUS)
            val right = gx(centreX + GLITTER_CUP_TOP_RADIUS)
            val shading = Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.6f),
                0.28f to Color.White.copy(alpha = 0.22f),
                0.42f to Color.Transparent,
                0.75f to Color.Black.copy(alpha = 0.2f),
                1f to Color.Black.copy(alpha = 0.65f),
                startX = left,
                endX = right,
            )
            val pitch = 1.4.dp.toPx()
            clipPath(body) {
                drawRect(glitter)
                drawCachedSurface(GlitterCupSurface(id, glitter)) { paintGlitter(id.hashCode(), glitter, pitch) }
                drawRect(shading)
            }
            drawPath(frontArcPath(GLITTER_CUP_BASE_RADIUS + 0.3f, GLITTER_CUP_BASE_Y - 3f), trim, style = Stroke(width = gy(2.2f)))
            drawOpenMouth(GLITTER_CUP_TOP_RADIUS, GLITTER_CUP_TOP_Y, lerp(glitter, Color.Black, 0.3f), lerp(glitter, Color.Black, 0.8f), trim, 1.6f)
            val time = seconds.value
            for (slot in 0 until 2) {
                val (at, grow) = sparkle(id.hashCode(), slot, time, period = 1.9f) ?: continue
                val x = gx(centreX - GLITTER_CUP_BASE_RADIUS + 4f + at.x * (GLITTER_CUP_BASE_RADIUS * 2f - 8f))
                val y = gy(GLITTER_CUP_TOP_Y + 8f + at.y * (GLITTER_CUP_BASE_Y - GLITTER_CUP_TOP_Y - 16f))
                drawSparkle(Offset(x, y), gx(6f) * grow)
            }
        }
    }
}

// The gift box's lid: how long it takes to fall off, how much of that is the fall before its bounce,
// where it comes to rest (its centre, on the box's grid, on screen), and how high it rattles in a shake.
private const val GIFT_LID_FALL_MILLIS = 620
private const val GIFT_LID_FLIGHT = 0.82f
private const val GIFT_LID_LANDED_X = 53f
private const val GIFT_LID_LANDED_Y = 72f

// How far up and out the lid pops off the box before it drops, and how much of its flight that takes.
private const val GIFT_LID_POP_X = 26f
private const val GIFT_LID_POP_Y = 12f
private const val GIFT_LID_POP = 0.35f
private const val GIFT_LID_RATTLE = 1.6f

/** The lid's rattle while the box is shaken: 0 (sitting on the box) to 1 (bounced up), and back, quickly. */
@Composable
private fun rememberLidRattle(): State<Float> = rememberInfiniteTransition(label = "giftLidRattle").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(70, easing = LinearEasing), RepeatMode.Reverse),
    label = "giftLidRattleLift",
)

private data class GlitterCupSurface(val id: String, val colour: Color)

// The Neon cup's pulse, and where its two tubes run round it.
private const val NEON_CUP_PULSE_SECONDS = 2f
private const val NEON_CUP_RADIUS = 20f
private val NEON_CUP_RINGS = listOf(22f, 64f)

/**
 * A neon cup - to go with the Neon dice: a matte black cup with two neon tubes wrapped round it, one
 * [upper] and one [lower], their light spilling onto the cup, and its lip lit by the upper. The two
 * pulse gently, out of step. Steady under reduced motion.
 */
class NeonDiceCupStyle(override val id: String, private val body: CupPalette, private val upper: Color, private val lower: Color) : DiceCupStyle, Swatched {
    override val swatch: Color = upper

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val seconds = rememberArtSeconds()
        CupCanvas(rolling, tilted, modifier) {
            val swell = 0.5f + 0.5f * sin(2f * PI.toFloat() * seconds.value / NEON_CUP_PULSE_SECONDS)
            drawContactShadow(NEON_CUP_RADIUS, 76f)
            val cup = roundSection(NEON_CUP_RADIUS, 9f, NEON_CUP_RADIUS, 76f)
            drawPath(cup, roundShading(body, NEON_CUP_RADIUS))
            for ((i, y) in NEON_CUP_RINGS.withIndex()) {
                val colour = if (i == 0) upper else lower
                val glow = 0.55f + 0.45f * (if (i == 0) swell else 1f - swell)
                // The light it throws on the cup either side of it.
                val spill = gy(14f)
                val at = gy(y + NEON_CUP_RADIUS * CUP_VIEW_SQUASH)
                clipPath(cup) {
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, colour.copy(alpha = 0.3f * glow), Color.Transparent), startY = at - spill, endY = at + spill),
                    )
                }
                val ring = frontArcPath(NEON_CUP_RADIUS + 0.6f, y)
                for ((width, alpha) in listOf(5f to 0.12f, 3f to 0.22f, 1.8f to 0.45f)) {
                    drawPath(ring, colour.copy(alpha = alpha * glow), style = Stroke(width = gy(width), cap = StrokeCap.Round))
                }
                drawPath(ring, lerp(colour, Color.White, 0.25f), style = Stroke(width = gy(1.1f), cap = StrokeCap.Round))
                drawPath(ring, lerp(colour, Color.White, 0.8f), style = Stroke(width = gy(0.4f), cap = StrokeCap.Round))
            }
            drawOpenMouth(NEON_CUP_RADIUS, 9f, body.mid, body.interior, lerp(upper, Color.White, 0.3f).copy(alpha = 0.6f + 0.4f * swell), 1.1f)
        }
    }
}

/**
 * A gift box - to go with the Ribbon dice: a box wrapped in [paper] (polka-dotted in its accent), seen
 * a little from above and to the left, tied with a [ribbon] round its lid and sides and a bow on top.
 * It shakes like any cup, its lid rattling on top as it does; as it tips over to pour, the lid slides
 * off and tumbles to the table in front of it, landing with a little bounce, leaving the box open.
 * Standing back up for the next shake, the lid is back on. Under reduced motion the lid doesn't
 * rattle, and is simply off once the box has poured.
 */
class GiftBoxDiceCupStyle(override val id: String, private val paper: CupPalette, private val ribbon: Color) : DiceCupStyle, Swatched {
    override val swatch: Color = paper.mid
    override val shape: CupShape = CupShape.MEDIUM

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val open = tilted && !rolling
        val reduceMotion = LocalReduceMotion.current
        // How far the lid is through falling off, 0 (on) to 1 (lying on the table). It's back on the
        // instant the box stands up again.
        val lidFall by animateFloatAsState(
            targetValue = if (open) 1f else 0f,
            animationSpec = if (open && !reduceMotion) tween(GIFT_LID_FALL_MILLIS, easing = LinearEasing) else snap(),
            label = "giftLidFall",
        )
        val rattle = if (rolling && !reduceMotion) rememberLidRattle() else null
        CupCanvas(rolling, tilted, modifier, shape) {
            fun p(x: Float, y: Float) = Offset(gx(x), gy(y))
            val ribbonDeep = lerp(ribbon, Color.Black, 0.3f)
            val ribbonSheen = lerp(ribbon, Color.White, 0.45f)
            // The contact shadow, the box's own footprint.
            drawPath(polygonPath(listOf(p(7f, 73f), p(46f, 73f), p(60f, 66f), p(21f, 66f))), Color.Black.copy(alpha = 0.35f))

            val front = polygonPath(listOf(p(9f, 27f), p(45f, 27f), p(45f, 72f), p(9f, 72f)))
            val side = polygonPath(listOf(p(45f, 27f), p(57f, 20f), p(57f, 65f), p(45f, 72f)))
            drawPath(front, Brush.horizontalGradient(listOf(lerp(paper.mid, paper.light, 0.4f), paper.mid), startX = gx(9f), endX = gx(45f)))
            drawPath(side, paper.dark)
            // Polka dots, worked out here: the grid isn't reachable inside clipPath.
            val dots = buildList {
                for (row in 0..5) for (col in 0..4) add(p(12f + col * 8f + (row % 2) * 4f, 31f + row * 8f))
            }
            val sideDots = buildList {
                for (row in 0..5) for (col in 0..1) {
                    val x = 48f + col * 6f + (row % 2) * 3f
                    add(p(x, 30f + row * 8f - (x - 45f) * 7f / 12f))
                }
            }
            val dot = gx(1.3f)
            clipPath(front) { for (d in dots) drawCircle(paper.accent.copy(alpha = 0.7f), dot, d) }
            clipPath(side) { for (d in sideDots) drawCircle(paper.accent.copy(alpha = 0.45f), dot, d) }
            // The ribbon down the front and the side.
            drawPath(polygonPath(listOf(p(24.5f, 27f), p(29.5f, 27f), p(29.5f, 72f), p(24.5f, 72f))), ribbon)
            drawLine(ribbonSheen.copy(alpha = 0.5f), p(26f, 27f), p(26f, 72f), strokeWidth = gx(0.9f))
            drawPath(polygonPath(listOf(p(49.5f, 24.4f), p(52.5f, 22.6f), p(52.5f, 67.6f), p(49.5f, 69.4f))), ribbonDeep)

            if (lidFall == 0f) {
                val lift = rattle?.value?.let { gy(GIFT_LID_RATTLE * it) } ?: 0f
                translate(top = -lift) { this@CupCanvas.drawLid(::p, ribbon, ribbonDeep, ribbonSheen) }
            } else {
                // The empty box: its dark inside, the far walls catching a little light.
                drawPath(polygonPath(listOf(p(9f, 27f), p(45f, 27f), p(57f, 20f), p(21f, 20f))), paper.interior)
                drawPath(polygonPath(listOf(p(21f, 20f), p(57f, 20f), p(55f, 23f), p(22.5f, 23f))), lerp(paper.interior, paper.mid, 0.35f))
                drawPath(polygonPath(listOf(p(9f, 27f), p(21f, 20f), p(22.5f, 23f), p(11f, 27f))), lerp(paper.interior, paper.mid, 0.2f))
                drawFallingLid(lidFall, ::p, ribbon, ribbonDeep, ribbonSheen)
            }
        }
    }

    /**
     * The lid [fall] of the way through coming off: carried with the box at first, then thrown up off
     * it and dropping - faster and faster, turning as it goes - to lie flat on the table in front, where
     * it bounces once. Worked out on screen, not in the tipping box's own turned frame, so it lands
     * level on the table however far the box has turned.
     */
    private fun CupDrawScope.drawFallingLid(fall: Float, p: (Float, Float) -> Offset, ribbon: Color, ribbonDeep: Color, ribbonSheen: Color) {
        val pivot = Offset(size.width * 0.5f, size.height * PIVOT_Y_FRACTION)
        val lidCentre = p(33f, 22f)
        val boxAngle = pose.rotation
        val radians = boxAngle * PI.toFloat() / 180f
        val fromPivot = lidCentre - pivot
        // Where the lid is while it's still on the box, on screen.
        val onBox = pivot + Offset(fromPivot.x * cos(radians) - fromPivot.y * sin(radians), fromPivot.x * sin(radians) + fromPivot.y * cos(radians))
        val landed = p(GIFT_LID_LANDED_X, GIFT_LID_LANDED_Y)
        val flight = (fall / GIFT_LID_FLIGHT).coerceAtMost(1f)
        // First it pops up off the box and out to the side, slowing as it rises; then it drops - faster
        // and faster - past the box's side to the table in front of it.
        val peak = onBox + Offset(gx(GIFT_LID_POP_X), -gy(GIFT_LID_POP_Y))
        val centre: Offset
        val turned: Float
        if (flight < GIFT_LID_POP) {
            val u = flight / GIFT_LID_POP
            val rise = 1f - (1f - u) * (1f - u)
            centre = onBox + (peak - onBox) * rise
            turned = rise * 0.35f
        } else {
            val u = (flight - GIFT_LID_POP) / (1f - GIFT_LID_POP)
            centre = peak + (landed - peak) * (u * u)
            turned = 0.35f + 0.65f * u * u
        }
        val bounce = if (fall > GIFT_LID_FLIGHT) gy(2.2f) * sin(PI.toFloat() * (fall - GIFT_LID_FLIGHT) / (1f - GIFT_LID_FLIGHT)) else 0f
        val at = centre - Offset(0f, bounce)
        val drop = flight * flight
        val angle = boxAngle * (1f - turned) + 40f * sin(PI.toFloat() * flight)
        val cup = this
        // Worked out here: the grid isn't reachable inside withTransform.
        val shadowTopLeft = Offset(landed.x - gx(24f), gy(GIFT_LID_LANDED_Y + 10f) - gy(2.5f))
        val shadowSize = Size(gx(48f), gy(5f))
        withTransform({ rotate(-boxAngle, pivot) }) {
            // Its shadow on the table, growing sharper as it comes down.
            drawOval(
                Color.Black.copy(alpha = 0.3f * drop),
                topLeft = shadowTopLeft,
                size = shadowSize,
            )
            withTransform({
                translate(at.x - lidCentre.x, at.y - lidCentre.y)
                rotate(angle, lidCentre)
            }) {
                with(cup) { drawLid(p, ribbon, ribbonDeep, ribbonSheen) }
            }
        }
    }

    /** The closed lid: a shallow box over the top, overhanging a little, with the ribbon crossing it and a bow. */
    private fun CupDrawScope.drawLid(p: (Float, Float) -> Offset, ribbon: Color, ribbonDeep: Color, ribbonSheen: Color) {
        val lidFront = polygonPath(listOf(p(7.5f, 21f), p(46.5f, 21f), p(46.5f, 30f), p(7.5f, 30f)))
        val lidSide = polygonPath(listOf(p(46.5f, 21f), p(58.5f, 14f), p(58.5f, 23f), p(46.5f, 30f)))
        val lidTop = polygonPath(listOf(p(7.5f, 21f), p(46.5f, 21f), p(58.5f, 14f), p(19.5f, 14f)))
        drawPath(polygonPath(listOf(p(7.5f, 30.5f), p(46.5f, 30.5f), p(46.5f, 31.5f), p(7.5f, 31.5f))), Color.Black.copy(alpha = 0.25f))
        drawPath(lidFront, lerp(paper.mid, paper.light, 0.3f))
        drawPath(lidSide, lerp(paper.dark, Color.Black, 0.1f))
        drawPath(lidTop, paper.light)
        drawPath(polygonPath(listOf(p(24.5f, 21f), p(29.5f, 21f), p(29.5f, 30f), p(24.5f, 30f))), ribbon)
        drawPath(polygonPath(listOf(p(49.5f, 19.25f), p(52.5f, 17.5f), p(52.5f, 26.5f), p(49.5f, 28.25f))), ribbonDeep)
        // Across the top: front to back, and side to side.
        drawPath(polygonPath(listOf(p(24.5f, 21f), p(29.5f, 21f), p(41.5f, 14f), p(36.5f, 14f))), ribbon)
        drawPath(polygonPath(listOf(p(13.5f, 16.6f), p(52.5f, 16.6f), p(52.5f, 18.4f), p(13.5f, 18.4f))), lerp(ribbon, ribbonSheen, 0.2f))
        drawBow(p(33f, 16.5f), gx(6f), ribbon)
        drawLine(Color.White.copy(alpha = 0.35f), p(7.5f, 21f), p(46.5f, 21f), strokeWidth = gy(0.5f))
    }
}
