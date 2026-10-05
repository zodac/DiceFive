package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.common.LocalReduceMotion

/**
 * A hen's colours: [feather] her body ([light] where it's lit, [dark] in shadow), [tail] her tail
 * feathers, [comb] her comb and wattle, [beak] her beak, and [straw] the nest she sits on.
 */
class HenPalette(
    val feather: Color,
    val light: Color,
    val dark: Color,
    val tail: Color,
    val comb: Color = Color(0xFFD7262B),
    val beak: Color = Color(0xFFF2A33A),
    val straw: Color = Color(0xFFD9B25E),
)

// The nest, on the MEDIUM grid (66 x 76): its rim's centre and radius, and how deep its front wall is -
// all of it, straws and shadow included, kept inside the grid: it's painted into an image the canvas's
// size, which would cut off anything below it.
private const val NEST_X = 33f
private const val NEST_RIM_Y = 60f
private const val NEST_RADIUS = 26f
private const val NEST_DEPTH = 7f

// How far she's drawn down from where her shapes are laid out, sinking her body into the nest so its
// front wall hides her underside.
private const val HEN_SINK = 7.5f

// The flap as the dice are poured: how long it lasts (a toss's length, so she settles as the dice
// land), how high she hops - a tenth of her own height, comb to underside - and the wings' pivot at her
// shoulder.
private const val FLAP_MILLIS = 900
private const val HOP_HEIGHT = 5.6f
private val Shoulder = Offset(38f, 40f)

/**
 * A hen on her nest. She's shaken like any cup, flapping her wing as she goes, but never tips: as the
 * dice are poured she crouches, then hops up off the nest beating both wings, beak open and a feather
 * or two flying, as if laying them - and settles back down as the dice land on the mat (as eggs, with
 * the Egg dice). Once she's done she's back as she was: the poured dice are the outcome
 * ([changesWhenPoured]), and like any cup she fades once the turn's rolls are spent. A game being
 * continued shows her settled; under reduced motion she doesn't flap or hop at all.
 *
 * The nest is painted once and stamped (behind her, then its front wall over her); she's a dozen or
 * so live shapes, redrawn only while she moves.
 */
class ChickenDiceCupStyle(override val id: String, private val palette: HenPalette) : DiceCupStyle, Swatched {
    override val shape: CupShape = CupShape.MEDIUM
    override val swatch: Color = palette.feather
    override val changesWhenPoured: Boolean = false

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val reduceMotion = LocalReduceMotion.current
        val laid = tilted && !rolling
        // 1 is at rest: she only flaps when the dice are poured, never on first appearing.
        val flap = remember { Animatable(1f) }
        val firstLaid = remember { booleanArrayOf(true) }
        LaunchedEffect(laid) {
            val first = firstLaid[0]
            firstLaid[0] = false
            if (!laid || reduceMotion || first && tilted) return@LaunchedEffect
            flap.snapTo(0f)
            flap.animateTo(1f, tween(FLAP_MILLIS, easing = LinearEasing))
        }
        CupCanvas(rolling, tilted, modifier, shape, tips = false) {
            drawCachedSurface(NestBack(id)) { CupDrawScope(this, CupShape.MEDIUM, CupPose(0f, 0f)).paintNestBack() }
            val t = flap.value
            val swell = if (t >= 1f) 0f else sin(PI.toFloat() * t)
            // Shaking, her wing flaps with the shake; hopping, both are thrown up and beat.
            // Clockwise lifts it: the wing reaches back from her shoulder, so turning it clockwise raises its tip.
            val wing = pose.rotation * 3f + swell * (25f + 45f * (0.5f + 0.5f * sin(t * 10f * PI.toFloat())))
            val rise = gy(HEN_SINK) - gy(hopHeight(t))
            transformed({ translate(0f, rise) }) {
                drawHen(wing = wing, beakOpen = swell * 0.6f, headLift = swell)
            }
            drawCachedSurface(NestFront(id)) { CupDrawScope(this, CupShape.MEDIUM, CupPose(0f, 0f)).paintNestFront() }
            if (t < 1f) drawFlyingFeathers(t)
        }
    }

    private data class NestBack(val id: String)

    private data class NestFront(val id: String)

    private fun CupDrawScope.p(x: Float, y: Float) = Offset(gx(x), gy(y))

    private fun CupDrawScope.drawHen(wing: Float, beakOpen: Float, headLift: Float) {
        val body = palette.feather
        val shading = Brush.linearGradient(
            0f to palette.light,
            0.45f to body,
            1f to palette.dark,
            start = p(24f, 30f),
            end = p(40f, 64f),
        )
        // Her tail: three feathers fanned up behind her.
        for ((tip, width) in listOf(Offset(3f, 25f) to 5f, Offset(7f, 19f) to 5.5f, Offset(14f, 18f) to 5f)) {
            val base = Offset(16f, 42f)
            val dir = (tip - base).let { it / it.getDistance() }
            val side = Offset(-dir.y, dir.x) * width
            val feather = Path().apply {
                moveTo(gx(base.x + side.x * 0.4f), gy(base.y + side.y * 0.4f))
                quadraticTo(gx((base.x + tip.x) / 2 + side.x), gy((base.y + tip.y) / 2 + side.y), gx(tip.x), gy(tip.y))
                quadraticTo(gx((base.x + tip.x) / 2 - side.x), gy((base.y + tip.y) / 2 - side.y), gx(base.x - side.x * 0.4f), gy(base.y - side.y * 0.4f))
                close()
            }
            drawPath(feather, palette.tail)
            drawPath(feather, palette.dark.copy(alpha = 0.5f), style = Stroke(gy(0.5f)))
        }
        // Her far wing, behind her and a little up and along - hidden while it's folded, and raised a
        // touch further than the near one, so the two show apart.
        val farShift = Offset(gx(2.5f), -gy(2f))
        transformed({ translate(farShift.x, farShift.y) }) { drawWing(wing * 1.15f, far = true) }
        // Her body: a plump round back, the breast pushed forward.
        val hen = Path().apply {
            moveTo(gx(10f), gy(46f))
            cubicTo(gx(11f), gy(35f), gx(26f), gy(30f), gx(38f), gy(33f))
            cubicTo(gx(45f), gy(35f), gx(51f), gy(40f), gx(52f), gy(47f))
            cubicTo(gx(53f), gy(56f), gx(46f), gy(64f), gx(33f), gy(65f))
            cubicTo(gx(19f), gy(66f), gx(9f), gy(59f), gx(10f), gy(46f))
            close()
        }
        drawPath(hen, shading)
        drawPath(hen, palette.dark.copy(alpha = 0.6f), style = Stroke(gy(0.6f)))
        // Her neck, up to her head - lifted and thrust forward as she flaps.
        val lift = Offset(gx(1.5f) * headLift, -gy(2.5f) * headLift)
        val neck = Path().apply {
            moveTo(gx(36f), gy(35f))
            quadraticTo(gx(38f), gy(26f), gx(42f) + lift.x, gy(20f) + lift.y)
            lineTo(gx(54f) + lift.x, gy(24f) + lift.y)
            quadraticTo(gx(52f), gy(34f), gx(50f), gy(42f))
            close()
        }
        drawPath(neck, shading)
        transformed({ translate(lift.x, lift.y) }) { drawHead(beakOpen) }
        // Her near wing, folded along her side, turned about her shoulder.
        drawWing(wing, far = false)
    }

    /**
     * A wing, scalloped with feathers, turned [angle] about her shoulder: the [far] one, behind her
     * body and in shadow, only shows once it's raised.
     */
    private fun CupDrawScope.drawWing(angle: Float, far: Boolean) {
        val shoulder = p(Shoulder.x, Shoulder.y)
        val feather = if (far) lerp(palette.feather, palette.dark, 0.5f) else palette.feather
        transformed({ rotate(angle, shoulder) }) {
            val wingPath = Path().apply {
                moveTo(gx(41f), gy(41f))
                cubicTo(gx(36f), gy(37f), gx(23f), gy(38f), gx(17f), gy(47f))
                cubicTo(gx(22f), gy(52f), gx(28f), gy(56f), gx(36f), gy(55f))
                cubicTo(gx(42f), gy(53f), gx(44f), gy(46f), gx(41f), gy(41f))
                close()
            }
            drawPath(wingPath, Brush.linearGradient(listOf(feather, palette.dark), start = p(30f, 40f), end = p(30f, 56f)))
            for (k in 0..2) {
                val y = 47f + k * 2.6f
                drawPath(
                    Path().apply {
                        moveTo(gx(21f + k * 2f), gy(y))
                        quadraticTo(gx(30f), gy(y + 3f), gx(40f - k), gy(y - 2f))
                    },
                    palette.dark.copy(alpha = 0.55f),
                    style = Stroke(gy(0.55f), cap = StrokeCap.Round),
                )
            }
            drawPath(wingPath, palette.dark.copy(alpha = 0.7f), style = Stroke(gy(0.6f)))
        }
    }

    private fun CupDrawScope.drawHead(beakOpen: Float) {
        // Comb on top, three rounded points.
        for ((x, y, r) in listOf(Triple(43.5f, 14f, 2.6f), Triple(47.5f, 12f, 3.1f), Triple(51.5f, 13.5f, 2.6f))) {
            drawCircle(palette.comb, gy(r), p(x, y))
        }
        drawCircle(
            Brush.radialGradient(listOf(palette.light, palette.feather), center = p(45f, 18f), radius = gy(9f)),
            gy(7.5f),
            p(48f, 21f),
        )
        // The beak: its lower half drops open as she flaps.
        val open = 3f * beakOpen
        drawPath(
            Path().apply {
                moveTo(gx(54.5f), gy(19.5f - open * 0.3f)); lineTo(gx(61f), gy(22f - open * 0.4f)); lineTo(gx(54.5f), gy(23f)); close()
            },
            palette.beak,
        )
        drawPath(
            Path().apply { moveTo(gx(54.5f), gy(23f)); lineTo(gx(59.5f), gy(23.5f + open)); lineTo(gx(54.5f), gy(25f)); close() },
            lerp(palette.beak, Color.Black, 0.2f),
        )
        if (beakOpen > 0.05f) {
            drawPath(
                Path().apply { moveTo(gx(55f), gy(23f)); lineTo(gx(59f), gy(22.6f - open * 0.3f)); lineTo(gx(58.5f), gy(23.4f + open * 0.8f)); close() },
                Color(0xFF5A1010),
            )
        }
        // The wattle, hanging under the beak.
        drawOval(palette.comb, topLeft = p(52.5f, 24.5f), size = Size(gx(3.6f), gy(5.5f)))
        // Her eye, with a glint.
        drawCircle(Color(0xFF1A1208), gy(1.4f), p(50.5f, 19.5f))
        drawCircle(Color.White, gy(0.5f), p(50.9f, 19.1f))
    }

    /** The far half of the nest: its rim behind her and the hollow she sits in. */
    private fun CupDrawScope.paintNestBack() {
        // Its shadow on the table.
        val shadow = NEST_RADIUS + 2f
        drawOval(
            Color.Black.copy(alpha = 0.35f),
            topLeft = p(NEST_X - shadow, NEST_RIM_Y + NEST_DEPTH - shadow * CUP_VIEW_SQUASH * 0.6f),
            size = Size(gx(2f * shadow), gy(2f * shadow * CUP_VIEW_SQUASH * 0.6f)),
        )
        val rim = Size(gx(2f * NEST_RADIUS), gy(2f * NEST_RADIUS * CUP_VIEW_SQUASH))
        val rimTop = p(NEST_X - NEST_RADIUS, NEST_RIM_Y - NEST_RADIUS * CUP_VIEW_SQUASH)
        drawOval(lerp(palette.straw, Color.Black, 0.25f), topLeft = rimTop, size = rim)
        val hollow = 0.82f
        drawOval(
            lerp(palette.straw, Color.Black, 0.6f),
            topLeft = p(NEST_X - NEST_RADIUS * hollow, NEST_RIM_Y - NEST_RADIUS * hollow * CUP_VIEW_SQUASH),
            size = Size(gx(2f * NEST_RADIUS * hollow), gy(2f * NEST_RADIUS * hollow * CUP_VIEW_SQUASH)),
        )
        // Straws along the far rim.
        val random = Random(7)
        repeat(26) {
            val a = PI.toFloat() + random.nextFloat() * PI.toFloat()
            val r = NEST_RADIUS * (0.84f + random.nextFloat() * 0.16f)
            val x = NEST_X + r * cos(a)
            val y = NEST_RIM_Y + r * CUP_VIEW_SQUASH * sin(a)
            drawStraw(random, x, y)
        }
    }

    /** The near half: the woven front wall she sits down behind, straws criss-crossing its face. */
    private fun CupDrawScope.paintNestFront() {
        val wall = Path().apply {
            moveTo(gx(NEST_X - NEST_RADIUS), gy(NEST_RIM_Y))
            for (step in 0..24) {
                val a = PI.toFloat() * step / 24f
                lineTo(gx(NEST_X - NEST_RADIUS * cos(a)), gy(NEST_RIM_Y + NEST_RADIUS * CUP_VIEW_SQUASH * sin(a)))
            }
            for (step in 24 downTo 0) {
                val a = PI.toFloat() * step / 24f
                val r = NEST_RADIUS * 0.86f
                lineTo(gx(NEST_X - r * cos(a)), gy(NEST_RIM_Y + NEST_DEPTH + r * CUP_VIEW_SQUASH * sin(a) * 0.7f))
            }
            close()
        }
        drawPath(
            wall,
            Brush.horizontalGradient(
                0f to lerp(palette.straw, Color.Black, 0.35f),
                0.35f to palette.straw,
                1f to lerp(palette.straw, Color.Black, 0.4f),
                startX = gx(NEST_X - NEST_RADIUS),
                endX = gx(NEST_X + NEST_RADIUS),
            ),
        )
        val random = Random(11)
        repeat(70) {
            val a = random.nextFloat() * PI.toFloat()
            val down = random.nextFloat()
            val r = NEST_RADIUS * (1f - 0.14f * down)
            val x = NEST_X - r * cos(a)
            val y = NEST_RIM_Y + down * NEST_DEPTH + r * CUP_VIEW_SQUASH * sin(a) * (1f - 0.3f * down)
            drawStraw(random, x, y)
        }
        // Loose ends sticking up over the rim, in front of her.
        repeat(12) {
            val a = 0.15f + random.nextFloat() * (PI.toFloat() - 0.3f)
            val x = NEST_X - NEST_RADIUS * cos(a)
            val y = NEST_RIM_Y + NEST_RADIUS * CUP_VIEW_SQUASH * sin(a)
            drawStraw(random, x, y, up = true)
        }
    }

    private fun CupDrawScope.drawStraw(random: Random, x: Float, y: Float, up: Boolean = false) {
        val angle = if (up) -PI.toFloat() / 2f + (random.nextFloat() - 0.5f) * 1.6f else (random.nextFloat() - 0.5f) * 1.2f
        val length = if (up) 3f + random.nextFloat() * 3f else 4f + random.nextFloat() * 5f
        val dx = cos(angle) * length / 2f
        val dy = sin(angle) * length / 2f
        val tone = when (random.nextInt(3)) {
            0 -> lerp(palette.straw, Color.White, 0.35f)
            1 -> palette.straw
            else -> lerp(palette.straw, Color.Black, 0.35f)
        }
        val start = if (up) p(x, y) else p(x - dx, y - dy)
        val end = if (up) p(x + dx * 2f, y + dy * 2f) else p(x + dx, y + dy)
        drawLine(tone, start, end, strokeWidth = gy(0.7f), cap = StrokeCap.Round)
    }

    /** Two feathers shaken loose, flying up and back off her as she flaps, fading as they go. */
    private fun CupDrawScope.drawFlyingFeathers(t: Float) {
        for ((k, from) in listOf(Offset(24f, 40f + HEN_SINK), Offset(30f, 36f + HEN_SINK)).withIndex()) {
            val f = ((t - 0.1f - k * 0.12f) / 0.75f).coerceIn(0f, 1f)
            if (f <= 0f || f >= 1f) continue
            val at = from + Offset(-14f * f - 4f * k, -16f * f + 10f * f * f)
            val tilt = 40f * sin(f * 3f * PI.toFloat()) + k * 60f
            val pivot = p(at.x, at.y)
            transformed({ rotate(tilt, pivot) }) {
                drawOval(
                    palette.feather.copy(alpha = 1f - f),
                    topLeft = p(at.x - 1.2f, at.y - 3f),
                    size = Size(gx(2.4f), gy(6f)),
                )
                drawLine(palette.dark.copy(alpha = 1f - f), p(at.x, at.y - 3f), p(at.x, at.y + 3.5f), strokeWidth = gy(0.4f))
            }
        }
    }
}

/**
 * [block] drawn under [transform] - like `withTransform`, but [block] keeps this [CupDrawScope] as its
 * receiver, so the grid ([CupDrawScope.gx]/[CupDrawScope.gy]) is still reachable inside it.
 */
private inline fun CupDrawScope.transformed(transform: DrawTransform.() -> Unit, block: () -> Unit) {
    drawContext.canvas.save()
    drawContext.transform.transform()
    block()
    drawContext.canvas.restore()
}

/**
 * How high (grid units) she is [t] (0-1) through her flap: a quick crouch, the hop - [HOP_HEIGHT] at
 * its peak - and a little dip as she lands back on the nest, ending exactly where she started.
 */
private fun hopHeight(t: Float): Float = when {
    t >= 1f -> 0f
    t < HOP_START -> -1f * sin(PI.toFloat() * t / HOP_START)
    t < HOP_END -> HOP_HEIGHT * sin(PI.toFloat() * (t - HOP_START) / (HOP_END - HOP_START))
    else -> -0.8f * sin(PI.toFloat() * (t - HOP_END) / (1f - HOP_END))
}

// When, through the flap, she leaves the nest and lands back on it.
private const val HOP_START = 0.12f
private const val HOP_END = 0.74f
