package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

/**
 * Glitter in [base]: a flake about every [pitch] pixels, each a tiny tilted square at its own shade -
 * most of them a little darker or lighter than [base], one in several catching the light near white.
 * Hundreds of flakes, so paint it through [drawCachedSurface], never live.
 */
internal fun DrawScope.paintGlitter(seed: Int, base: Color, pitch: Float) {
    val random = Random(seed)
    val count = (size.width * size.height / (pitch * pitch)).toInt()
    val deep = lerp(base, Color.Black, 0.45f)
    val bright = lerp(base, Color.White, 0.6f)
    repeat(count) {
        val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
        val flake = pitch * (0.35f + random.nextFloat() * 0.45f)
        val roll = random.nextFloat()
        val colour = when {
            roll < 0.12f -> lerp(bright, Color.White, random.nextFloat())
            roll < 0.55f -> lerp(base, bright, random.nextFloat())
            else -> lerp(deep, base, random.nextFloat())
        }
        rotate(random.nextFloat() * 90f, at) {
            drawRect(colour, topLeft = at - Offset(flake / 2f, flake / 2f), size = Size(flake, flake))
        }
    }
}

/** A sparkle: a four-pointed star of light [radius] across, at [centre], in a soft glow of [colour]. */
internal fun DrawScope.drawSparkle(centre: Offset, radius: Float, colour: Color = Color.White, alpha: Float = 1f) {
    if (radius <= 0f || alpha <= 0f) return
    drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.45f * alpha), Color.Transparent), center = centre, radius = radius * 0.7f), radius * 0.7f, centre)
    val star = Path().apply {
        val waist = radius * 0.13f
        moveTo(centre.x, centre.y - radius)
        quadraticTo(centre.x + waist, centre.y - waist, centre.x + radius, centre.y)
        quadraticTo(centre.x + waist, centre.y + waist, centre.x, centre.y + radius)
        quadraticTo(centre.x - waist, centre.y + waist, centre.x - radius, centre.y)
        quadraticTo(centre.x - waist, centre.y - waist, centre.x, centre.y - radius)
        close()
    }
    drawPath(star, Color.White.copy(alpha = alpha))
}

/**
 * Where the sparkle in [slot] is [seconds] in, and how big (0-1), or null between sparkles. Each slot
 * flashes once every [period] seconds - growing and shrinking over its first [flash] - somewhere new
 * each time, as a fraction of the area it's in; [seed] keeps every piece of art's sparkles its own.
 */
internal fun sparkle(seed: Int, slot: Int, seconds: Float, period: Float = 1.6f, flash: Float = 0.45f): Pair<Offset, Float>? {
    val offset = Random(seed * 131 + slot).nextFloat() * period
    val t = seconds + offset
    val cycle = floor(t / period).toInt()
    val into = t - cycle * period
    if (into > flash || seconds == 0f) return null
    val random = Random(seed * 7919 + slot * 104729 + cycle)
    return Offset(random.nextFloat(), random.nextFloat()) to sin(PI.toFloat() * into / flash)
}

/** The surface a glitter die's face is painted once for: its pattern and its colour. */
private data class GlitterSurface(val seed: Int, val colour: Color)

private const val GLITTER_CORNER_PERCENT = 20

// How many sparkles can be on one die at once.
private const val GLITTER_DIE_SPARKLES = 2

/**
 * Glitter dice: every number its own colour of glitter ([colours]) - so a roll comes up a scatter of
 * colours - under a clear glossy coat, with pearl-white pips, and now and then a sparkle flashing
 * somewhere on it (at most [GLITTER_DIE_SPARKLES] at a time). The glitter is painted once for each
 * face of each die; only the sparkles are live. None under reduced motion.
 */
class GlitterDiceStyle(
    override val id: String,
    private val colours: FaceColours,
    private val pip: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = colours[1]
    override val bodyColor: Color = colours[3]
    override val cornerPercent: Int = GLITTER_CORNER_PERCENT

    override fun recoloured(palette: DieColourPalette): DiceStyle = GlitterDiceStyle(id, FaceColours.all(palette.diceTop), palette.pip, palette.heldRing)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val seed = naturalPatternSeed(value)
        val seconds = rememberArtSeconds()
        val colour = colours[value]
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(colour, lerp(colour, Color.Black, 0.3f))),
            edge = lerp(colour, Color.Black, 0.45f),
            pipColor = pip,
            cornerPercent = GLITTER_CORNER_PERCENT,
            pipShape = PipShape.CUSTOM,
            customPips = { face ->
                val radius = size.minDimension * 0.11f
                drawPipPositions(face) { centre ->
                    drawCircle(Color.Black.copy(alpha = 0.35f), radius * 1.12f, centre + Offset(0f, radius * 0.12f))
                    drawCircle(Brush.radialGradient(listOf(Color.White, pip, lerp(pip, Color.Black, 0.18f)), center = centre - Offset(radius * 0.35f, radius * 0.35f), radius = radius * 1.4f), radius, centre)
                }
                val time = seconds.value
                for (slot in 0 until GLITTER_DIE_SPARKLES) {
                    val (at, grow) = sparkle(seed, slot, time) ?: continue
                    drawSparkle(Offset(at.x * size.width, at.y * size.height), size.minDimension * 0.2f * grow)
                }
            },
            heldRingColor = heldRing,
        ) {
            drawCachedSurface(GlitterSurface(seed, colour)) { paintGlitter(seed, colour, pitch = 1.5.dp.toPx()) }
            // The clear coat's gloss, a soft band across the top left.
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.28f, size.height * 0.22f), radius = size.minDimension * 0.5f),
                size.minDimension * 0.5f,
                Offset(size.width * 0.28f, size.height * 0.22f),
            )
            drawRect(Color.White.copy(alpha = 0.18f), style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}

val GlitterColours = FaceColours(
    listOf(Color(0xFFE0458F), Color(0xFFD9A030), Color(0xFFA9B2BC), Color(0xFF2A9E5E), Color(0xFF3060D0), Color(0xFF8A44D0)),
)
