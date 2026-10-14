package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

// The water set: the Ocean dice, mat and backgrounds, which share their waves and caustics.

/**
 * [rows] lines of wave crests in [colour] across the surface, each a gentle swell [wavelength] long
 * and [amplitude] high, with a faint band of the same colour hanging below it. Each row has its own
 * offset from [seed], and [seconds] moves them: alternate rows drift opposite ways at
 * [speed] wavelengths a second, and rise and fall a little. At 0 they're where [seed] put them.
 */
internal fun DrawScope.drawWaveCrests(
    colour: Color,
    rows: Int,
    amplitude: Float,
    wavelength: Float,
    strokeWidth: Float,
    seed: Int,
    seconds: Float = 0f,
    speed: Float = 0f,
    band: Float = 0f,
) {
    val random = Random(seed)
    val twoPi = 2f * PI.toFloat()
    val step = wavelength / 10f
    for (row in 0 until rows) {
        val offset = random.nextFloat() * twoPi
        val direction = if (row % 2 == 0) 1f else -1f
        val rowSpeed = speed * (0.7f + random.nextFloat() * 0.6f)
        val baseY = size.height * (row + 0.5f + (random.nextFloat() - 0.5f) * 0.3f) / rows
        val y = baseY + amplitude * 0.5f * sin(seconds * 0.5f + row * 1.7f)
        val phase = offset + direction * seconds * rowSpeed * twoPi
        // Further down reads as nearer: those rows are brighter.
        val strength = 0.55f + 0.45f * (row + 1f) / rows
        val crest = Path()
        var x = -step
        crest.moveTo(x, y + amplitude * sin(x / wavelength * twoPi + phase))
        while (x < size.width + step) {
            x += step
            crest.lineTo(x, y + amplitude * sin(x / wavelength * twoPi + phase))
        }
        if (band > 0f) {
            val fill = Path().apply {
                addPath(crest)
                lineTo(x, y + band)
                lineTo(-step, y + band)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(colour.copy(alpha = 0.06f * strength), Color.Transparent), startY = y - amplitude, endY = y + band))
        }
        drawPath(crest, colour.copy(alpha = colour.alpha * strength), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

/**
 * Caustics: the net of bright, wavering lines that sunlight through rippling water throws on the
 * bottom - rounded cells about [cell] across in [colour], each with a soft glow round its line.
 */
internal fun DrawScope.paintCaustics(colour: Color, cell: Float, seed: Int) {
    val random = Random(seed)
    val line = cell * 0.05f
    var row = 0
    var cy = -cell * 0.5f
    while (cy < size.height + cell) {
        var cx = if (row % 2 == 0) -cell * 0.5f else 0f
        while (cx < size.width + cell) {
            val centre = Offset(cx + (random.nextFloat() - 0.5f) * cell * 0.35f, cy + (random.nextFloat() - 0.5f) * cell * 0.35f)
            val corners = 6 + random.nextInt(3)
            val turn = random.nextFloat()
            val outline = smoothPath(
                List(corners) { i ->
                    val angle = 2f * PI.toFloat() * (i + turn) / corners
                    val reach = cell * (0.42f + random.nextFloat() * 0.14f)
                    centre + Offset(cos(angle) * reach, sin(angle) * reach)
                },
                closed = true,
            )
            val strength = 0.6f + random.nextFloat() * 0.4f
            drawPath(outline, colour.copy(alpha = colour.alpha * 0.18f * strength), style = Stroke(width = line * 3.5f))
            drawPath(outline, colour.copy(alpha = colour.alpha * strength), style = Stroke(width = line))
            cx += cell
        }
        cy += cell * 0.87f
        row++
    }
}

private data class OceanSurface(val seed: Int, val foam: Color)

/**
 * A die of sea water, [light] at the top darkening to [dark], with a few lines of [foam] wave crests
 * running across each face (placed from the die and its value, so each looks a little different) and
 * pearl [pip]s.
 */
class OceanDiceStyle(
    override val id: String,
    private val light: Color,
    private val dark: Color,
    private val foam: Color,
    private val pip: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = light

    override fun recoloured(palette: DieColourPalette): DiceStyle =
        OceanDiceStyle(id, palette.diceTop, palette.diceBottom, lerp(palette.diceTop, Color.White, 0.6f), palette.pip, palette.heldRing)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val seed = naturalPatternSeed(value, styleSeed = 41)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.verticalGradient(listOf(light, dark)),
            edge = lerp(light, Color.White, 0.35f),
            pipColor = pip,
            heldRingColor = heldRing,
        ) {
            drawCachedSurface(OceanSurface(seed, foam)) {
                drawWaveCrests(
                    foam.copy(alpha = 0.32f),
                    rows = 3,
                    amplitude = size.height * 0.035f,
                    wavelength = size.width * 0.55f,
                    strokeWidth = size.minDimension * 0.025f,
                    seed = seed,
                )
                drawSoftSpot(Color.White.copy(alpha = 0.22f), Offset(size.width * 0.3f, size.height * 0.15f), size.minDimension * 0.5f)
            }
        }
    }
}

/** Shallow sea over sand: caustics in [MatPalette.detail] rippling across the tray. Painted once. */
class OceanDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() = drawCachedSurface("mat:$id") {
        paintCaustics(palette.detail.copy(alpha = 0.3f), cell = 34.dp.toPx(), seed = 23)
        drawSoftSpot(palette.detail.copy(alpha = 0.12f), Offset(size.width * 0.3f, 0f), size.width * 0.6f)
    }
}

/**
 * The open sea: rows of wave crests in [BackgroundPalette.detail] rolling slowly across the table,
 * alternate rows each way, each rising and falling on the swell. Still under reduced motion.
 */
class OceanWavesBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    private var seconds by mutableFloatStateOf(0f)

    @Composable
    override fun Animate() {
        TwinkleClock { seconds = it }
    }

    override fun DrawScope.drawScoreAreaDecoration() {
        drawSoftSpot(palette.detail.copy(alpha = 0.10f), Offset(size.width * 0.5f, 0f), size.maxDimension * 0.6f)
        drawWaveCrests(
            palette.detail.copy(alpha = 0.16f),
            rows = 7,
            amplitude = 5.dp.toPx(),
            wavelength = 90.dp.toPx(),
            strokeWidth = 1.6.dp.toPx(),
            seed = 5,
            seconds = seconds,
            speed = 0.05f,
            band = 40.dp.toPx(),
        )
    }
}

/** A sunlit lagoon - to go with the Ocean mat: caustics in [BackgroundPalette.detail] over its floor. Painted once. */
class OceanLagoonBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() {
        drawCachedSurface("background:$id") {
            paintCaustics(palette.detail.copy(alpha = 0.09f), cell = 56.dp.toPx(), seed = 29)
            drawSoftSpot(palette.detail.copy(alpha = 0.10f), Offset(size.width * 0.5f, 0f), size.maxDimension * 0.6f)
        }
    }
}
