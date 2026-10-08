package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.game.DICE_TOSS_MILLIS
import net.zodac.dicefive.ui.theme.GoldAccent

// The egg's size within the die's square, as fractions of it: a little taller than wide, and fatter
// below its middle than above, like a real egg standing on its broad end.
private const val EGG_HEIGHT = 1f
private const val EGG_WIDTH = 0.82f
private const val EGG_TAPER = 0.2f

// The box a face's pips are laid out in, and each pip's radius, as fractions of the die.
private const val EGG_PIP_BOX_WIDTH = 0.5f
private const val EGG_PIP_BOX_HEIGHT = 0.6f
private const val EGG_PIP_RADIUS = 0.068f

// The golden egg's sparkle: how small it shrinks to (of its full size), and how long a swell or a shrink takes.
private const val SPARKLE_MIN_SCALE = 0.75f
private const val SPARKLE_MILLIS = 1300

// How many whole turns an egg rolls end over end in a toss, before it rocks to a stop upright.
private const val EGG_TOSS_TURNS = 2f
private const val EGG_ROCK_DEGREES = 16f

/**
 * Where the egg's outline is at [step] of [steps] round it, in a unit box centred on 0 (y down): a
 * circle squeezed at the top and swollen at the bottom.
 */
private fun eggPoint(step: Int, steps: Int): Offset {
    val a = 2f * PI.toFloat() * step / steps
    val y = -cos(a)
    return Offset(sin(a) * (1f + EGG_TAPER * y) * EGG_WIDTH / 2f, y * EGG_HEIGHT / 2f)
}

private val EggOutline: List<Offset> = (0 until 48).map { eggPoint(it, 48) }

/** The egg's outline at [size], centred in it. */
private fun eggPath(size: Size): Path = Path().apply {
    val side = size.minDimension
    EggOutline.forEachIndexed { i, p ->
        val x = size.width / 2 + p.x * side
        val y = size.height / 2 + p.y * side
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * How far an egg [tumbleMillis] into a toss is turned, in degrees: rolling end over end, slowing,
 * then rocking on its broad end before it settles upright - as eggs do. Upright (0) at rest (null).
 */
internal fun eggTilt(dieIndex: Int, tumbleMillis: Float?): Float {
    if (tumbleMillis == null) return 0f
    val p = (tumbleMillis / DICE_TOSS_MILLIS).coerceIn(0f, 1f)
    val direction = if (Random(dieIndex * 17 + 3).nextBoolean()) 1f else -1f
    val roll = EGG_TOSS_TURNS * 360f * (1f - p / 0.7f).coerceAtLeast(0f).let { it * it }
    val rock = if (p < 0.55f) 0f else EGG_ROCK_DEGREES * sin((p - 0.55f) / 0.45f * 3f * PI.toFloat()) * (1f - p) / 0.45f
    return direction * (roll + rock)
}

/**
 * Egg dice: not cubes at all but eggs, each standing upright on its broad end, speckled all over,
 * with a die's pips painted on its front - in the other egg's shell colour (brown pips on the white
 * egg, cream on the brown). Every face of every die is speckled differently. A toss
 * rolls an egg end over end, slowing, and it rocks on its base before settling upright; at rest it
 * always stands straight up ([standsUpright]), never lying at an angle like a die.
 */
class EggDiceStyle(
    override val id: String,
    private val shell: Color,
    private val shade: Color,
    private val pip: Color,
    private val heldRing: Color = GoldAccent,
    private val speckled: Boolean = true,
    private val metallic: Boolean = false,
    private val speckleColour: Color? = null,
    private val sparkle: Boolean = metallic,
) : DiceStyle, Swatched {
    override val swatch: Color = shell
    override val tumblesItself: Boolean = true
    override val lockedChainReach: Float = 0.66f
    override val standsUpright: Boolean = true

    override fun recoloured(palette: DieColourPalette): DiceStyle =
        EggDiceStyle(id, palette.diceTop, palette.diceBottom, palette.pip, palette.heldRing, speckled, metallic = false, speckleColour, sparkle)

    override fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape {
        val tilt = eggTilt(dieIndex, tumbleMillis)
        return GenericShape { size, _ ->
            val side = size.minDimension
            val radians = tilt * PI.toFloat() / 180f
            val c = cos(radians)
            val s = sin(radians)
            EggOutline.forEachIndexed { i, p ->
                val x = size.width / 2 + (p.x * c - p.y * s) * side
                val y = size.height / 2 + (p.x * s + p.y * c) * side
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val dieIndex = LocalDieIndex.current
        val tilt = eggTilt(dieIndex, LocalDieTumbleMillis.current)
        val seed = naturalPatternSeed(value)
        // The golden egg's sparkle: its size, read only while drawing. Still under reduced motion, and a plain
        // egg never starts the clock.
        val sparkleScale: State<Float> = if (sparkle && !LocalReduceMotion.current) {
            rememberInfiniteTransition(label = "goldenEggSparkle").animateFloat( // i18n: not translated - an animation label, not shown
                initialValue = SPARKLE_MIN_SCALE,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(SPARKLE_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            )
        } else {
            remember { mutableFloatStateOf(1f) }
        }
        val speckles = remember(seed, speckled) { if (speckled) eggSpeckles(seed) else emptyList() }

        Canvas(modifier = modifier.dieShadow(shadowShape(value, dieIndex, LocalDieTumbleMillis.current))) {
            val side = size.minDimension
            val egg = eggPath(size)
            val centre = Offset(size.width / 2, size.height / 2)
            rotate(tilt, centre) {
                // The shell, lit from above and to the left, darkening round its edge.
                if (metallic) {
                    // Gold: a smooth turn from a bright lit shoulder to a deep amber foot, with a restrained
                    // sheen laid over it (below) and the edge falling away into bronze.
                    val from = centre + Offset(-side * 0.3f, -side * 0.45f)
                    val to = centre + Offset(side * 0.3f, side * 0.45f)
                    drawPath(egg, Brush.linearGradient(*GoldBase, start = from, end = to))
                    drawPath(
                        egg,
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to GoldEdge.copy(alpha = 0.3f),
                            startY = centre.y - side * EGG_HEIGHT / 2f,
                            endY = centre.y + side * EGG_HEIGHT / 2f,
                        ),
                    )
                } else drawPath(
                    egg,
                    Brush.radialGradient(
                        0f to lerp(shell, Color.White, 0.55f),
                        0.35f to shell,
                        1f to shade,
                        center = centre + Offset(-side * 0.12f, -side * 0.18f),
                        radius = side * 0.62f,
                    ),
                )
                clipPath(egg) {
                    for ((at, r, a) in speckles) {
                        drawCircle((speckleColour ?: lerp(shade, Color(0xFF3A2010), 0.55f)).copy(alpha = a), r * side, centre + at * side)
                    }
                    if (metallic) {
                        // Two tall pale reflections, a narrow dark gap between them, each fading at its ends.
                        for ((x, w, alpha) in GoldReflections) {
                            val strip = Path().apply {
                                moveTo(centre.x + side * x, centre.y - side * 0.38f)
                                quadraticTo(centre.x + side * (x - 0.02f), centre.y, centre.x + side * x, centre.y + side * 0.3f)
                                lineTo(centre.x + side * (x + w), centre.y + side * 0.3f)
                                quadraticTo(centre.x + side * (x + w - 0.02f), centre.y, centre.x + side * (x + w), centre.y - side * 0.38f)
                                close()
                            }
                            drawPath(
                                strip,
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.3f to GoldGlow.copy(alpha = alpha),
                                    0.7f to GoldGlow.copy(alpha = alpha),
                                    1f to Color.Transparent,
                                    startY = centre.y - side * 0.38f,
                                    endY = centre.y + side * 0.3f,
                                ),
                            )
                        }
                    }
                    if (sparkle) {
                        // A four-point sparkle high on its shoulder, fixed in place and swelling and shrinking.
                        drawSparkle(centre + Offset(-side * 0.135f, -side * 0.23f), side * 0.115f * sparkleScale.value, sparkleScale.value)
                    } else {
                        // A soft glint high on its shoulder.
                        drawOval(
                            Color.White.copy(alpha = 0.55f),
                            topLeft = centre + Offset(-side * 0.2f, -side * 0.33f),
                            size = Size(side * 0.13f, side * 0.2f),
                        )
                    }
                }
                // Its pips, on its broad front where the egg is widest and flattest: a die's layout in a
                // box a little taller than wide, as the egg is, each pip with a faint darker rim.
                val box = Size(side * EGG_PIP_BOX_WIDTH, side * EGG_PIP_BOX_HEIGHT)
                val boxTopLeft = centre + Offset(-box.width / 2f, -box.height / 2f + side * 0.06f)
                val radius = side * EGG_PIP_RADIUS
                for (at in pipLayout(value)) {
                    val pipCentre = boxTopLeft + Offset(at.x * box.width, at.y * box.height)
                    drawCircle(pip, radius, pipCentre)
                    drawCircle(lerp(pip, Color.Black, 0.25f), radius, pipCentre, style = Stroke(width = radius * 0.2f))
                }
                drawPath(
                    egg,
                    if (held) heldRing else shade.copy(alpha = 0.9f),
                    style = Stroke(width = if (held) 2.dp.toPx() else 0.8.dp.toPx()),
                )
            }
        }
    }
}

/**
 * The golden egg: a polished 24-carat shell, speckled in dark gold, with black pips, in the same shape as the other eggs.
 * The Egg style's secret colour once Eggcellent Discovery is earned - and the rare die
 * that rolls golden, one in a thousand, while the Egg dice and the Chicken cup are both picked.
 */
val GoldenEggDiceStyle = EggDiceStyle("egg_gold", Color(0xFFD9A420), Color(0xFF6E4400), Color(0xFF111111), metallic = true, speckleColour = Color(0xFF8A5A00))

// The golden egg's metal, from its lit shoulder to its shadowed foot, with a reflected band between.
private val GoldBase: Array<Pair<Float, Color>> = arrayOf(
    0f to Color(0xFFF8D460),
    0.35f to Color(0xFFE6B02A),
    0.7f to Color(0xFFCC9314),
    1f to Color(0xFF9A6606),
)

// The reflections' left edge and width (fractions of the die) and strength.
private val GoldReflections = listOf(Triple(-0.2f, 0.1f, 0.3f), Triple(0.05f, 0.07f, 0.18f))

private val GoldGlow = Color(0xFFFFF0B0)

/** Where the golden egg's edge falls away to. */
private val GoldEdge = Color(0xFF7A4A00)

/** A four-point star of [radius], pointed up, down, left and right, over a soft glow, at [strength] (0-1). */
private fun DrawScope.drawSparkle(at: Offset, radius: Float, strength: Float) {
    drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = 0.55f * strength), 1f to Color.Transparent, center = at, radius = radius * 0.9f), radius * 0.9f, at)
    val waist = radius * 0.16f
    drawPath(
        Path().apply {
            moveTo(at.x, at.y - radius)
            quadraticTo(at.x + waist, at.y - waist, at.x + radius, at.y)
            quadraticTo(at.x + waist, at.y + waist, at.x, at.y + radius)
            quadraticTo(at.x - waist, at.y + waist, at.x - radius, at.y)
            quadraticTo(at.x - waist, at.y - waist, at.x, at.y - radius)
            close()
        },
        Color.White.copy(alpha = 0.55f + 0.4f * strength),
    )
}

/** An egg's speckles: (where, relative to its centre, radius and strength), all as fractions of the die. */
private fun eggSpeckles(seed: Int): List<Triple<Offset, Float, Float>> {
    val random = Random(seed * 7 + 11)
    return List(34) {
        // Spread over the egg, a little thicker towards its top, as real speckling is.
        val r = sqrt(random.nextFloat()) * 0.47f
        val a = random.nextFloat() * 2f * PI.toFloat()
        val at = Offset(cos(a) * r * EGG_WIDTH / EGG_HEIGHT, sin(a) * r - 0.04f)
        Triple(at, 0.007f + random.nextFloat() * 0.016f, 0.3f + random.nextFloat() * 0.45f)
    }
}
