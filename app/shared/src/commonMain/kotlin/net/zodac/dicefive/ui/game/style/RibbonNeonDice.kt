package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import net.zodac.dicefive.ui.theme.GoldAccent

/** Each number's own colour, 1 to 6, for a style that gives every face a different one. */
class FaceColours(val colours: List<Color>) {
    init {
        require(colours.size == 6) { "One colour for each face" }
    }

    operator fun get(value: Int): Color = colours[(value - 1).coerceIn(0, 5)]

    companion object {
        /** Every face the same [colour] - a recoloured die, whose colour is the roll's. */
        fun all(colour: Color) = FaceColours(List(6) { colour })
    }
}

val RibbonColours = FaceColours(
    listOf(Color(0xFFE53935), Color(0xFFF57C00), Color(0xFFE0A800), Color(0xFF3D9A42), Color(0xFF1E7FD8), Color(0xFF8E24AA)),
)

val NeonColours = FaceColours(
    listOf(Color(0xFFFF3FA4), Color(0xFF3FE6FF), Color(0xFF9DFF3F), Color(0xFFFF9A3F), Color(0xFFB57BFF), Color(0xFFFFE53F)),
)

private const val RIBBON_CORNER_PERCENT = 18

/**
 * Ribbon dice: a plain die with its number laid out in a flowing length of satin ribbon - curling in
 * and out at its ends, twisting over once or twice along the way, its satin catching the light as it
 * turns, its ends cut into swallowtails - and every number a different colour of ribbon ([colours]).
 * Painted once per face; see [paintRibbonDigit].
 */
class RibbonDiceStyle(
    override val id: String,
    private val face: Color,
    private val shade: Color,
    private val colours: FaceColours,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = face
    override val cornerPercent: Int = RIBBON_CORNER_PERCENT

    override fun recoloured(palette: DieColourPalette): DiceStyle =
        RibbonDiceStyle(id, palette.diceTop, palette.diceBottom, FaceColours.all(palette.pip), palette.heldRing)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) = StyledDie(
        value = value,
        held = held,
        modifier = modifier,
        face = Brush.linearGradient(listOf(face, shade)),
        edge = lerp(shade, Color.Black, 0.2f),
        pipColor = colours[value],
        cornerPercent = RIBBON_CORNER_PERCENT,
        pipShape = PipShape.CUSTOM,
        heldRingColor = heldRing,
    ) {
        drawCachedSurface(RibbonSurface(value, colours[value])) { paintRibbonDigit(value, colours[value]) }
    }
}

private data class RibbonSurface(val value: Int, val colour: Color)

private const val NEON_CORNER_PERCENT = 12

private data class NeonTube(val value: Int, val colour: Color)

// A neon tube's slow pulse: a full swell and fade every NEON_PULSE_SECONDS, never dimmer than NEON_PULSE_FLOOR.
private const val NEON_PULSE_SECONDS = 1.8f
private const val NEON_PULSE_FLOOR = 0.7f

/**
 * Neon dice: a black panel with its number bent out of a glowing neon tube - layered halos round a
 * bright core, a little capped electrode at each end, the panel itself lit by its glow - and every
 * number a different colour of gas ([colours]). The glow pulses gently, each die on its own beat;
 * steady under reduced motion. Painted once per face and colour - the pulse is the image's opacity.
 */
class NeonDiceStyle(
    override val id: String,
    private val panel: Color,
    private val colours: FaceColours,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = colours[1]
    override val bodyColor: Color = panel
    override val cornerPercent: Int = NEON_CORNER_PERCENT

    override fun recoloured(palette: DieColourPalette): DiceStyle = NeonDiceStyle(id, panel, FaceColours.all(palette.swatch), palette.heldRing)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val seconds = rememberArtSeconds()
        val phase = LocalDieIndex.current * 1.7f
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(lerp(panel, Color.White, 0.06f), panel)),
            edge = lerp(panel, Color.White, 0.18f),
            pipColor = colours[value],
            cornerPercent = NEON_CORNER_PERCENT,
            pipShape = PipShape.CUSTOM,
            pipPadding = 0.dp,
            customPips = {
                val swell = 0.5f + 0.5f * sin(2f * PI.toFloat() * seconds.value / NEON_PULSE_SECONDS + phase)
                // The tube is painted once, at full glow, and the pulse is just how opaque it's drawn.
                val tube = cachedSurface(NeonTube(it, colours[it])) { drawNeonDigit(it, colours[it], glow = 1f) }
                tube?.let { image -> drawImage(image, alpha = NEON_PULSE_FLOOR + (1f - NEON_PULSE_FLOOR) * swell) }
            },
            heldRingColor = heldRing,
        ) {
            // The panel's mounting screws, one in each corner.
            val inset = size.minDimension * 0.11f
            for (corner in listOf(Offset(inset, inset), Offset(size.width - inset, inset), Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset))) {
                drawCircle(Color(0xFF55555C), size.minDimension * 0.025f, corner)
            }
        }
    }
}

// The tube's width, and how far its halo reaches from the tube's centre line, as fractions of the die's side.
private const val NEON_TUBE_WIDTH = 0.065f
private const val NEON_HALO_REACH = 0.18f
private const val NEON_GLOW_EDGE_WIDTH = 0.11f

// The halo's layers, its opacity next to the tube, and how quickly it fades from there to nothing at its reach.
private const val NEON_HALO_LAYERS = 40
private const val NEON_HALO_PEAK = 0.95f
private const val NEON_HALO_FALLOFF = 1.15f

/** [value] as a neon tube of [colour], its halo at [glow] (0-1) of full strength. */
internal fun DrawScope.drawNeonDigit(value: Int, colour: Color, glow: Float) {
    val side = size.minDimension
    val centre = Offset(size.width / 2f, size.height / 2f)
    // The tube lights the panel round it.
    drawCircle(
        Brush.radialGradient(listOf(colour.copy(alpha = 0.5f * glow), Color.Transparent), center = centre, radius = side * 0.75f),
        side * 0.75f,
        centre,
    )
    val strokes = strokeDigitPaths(value, Offset(side * 0.1f, side * 0.09f), side * 0.8f)
    // Every stroke in one path, so where two strokes meet (the 4's crossbar) their glow isn't doubled.
    val digit = Path().apply { for (stroke in strokes) addPath(stroke.path) }
    fun tube(c: Color, w: Float) = drawPath(digit, c, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // The halo: many faint layers, widest first, each adding just enough that the glow fades smoothly
    // from the tube to nothing - a few strong layers showed each one's edge as a line round the number.
    var covered = 0f
    for (layer in NEON_HALO_LAYERS downTo 1) {
        val reach = layer.toFloat() / NEON_HALO_LAYERS
        val target = NEON_HALO_PEAK * glow * (1f - reach).pow(NEON_HALO_FALLOFF)
        val alpha = (1f - (1f - target) / (1f - covered)).coerceIn(0f, 1f)
        tube(colour.copy(alpha = alpha), NEON_TUBE_WIDTH * side + 2f * reach * (NEON_HALO_REACH - NEON_TUBE_WIDTH / 2f) * side)
        covered = target
    }
    // One crisp edge where the glow is brightest, hugging the tube, so the number still has an outline.
    tube(colour.copy(alpha = 0.5f * glow), side * NEON_GLOW_EDGE_WIDTH)
    tube(lerp(colour, Color.White, 0.2f), side * NEON_TUBE_WIDTH)
    tube(lerp(colour, Color.White, 0.75f).copy(alpha = 0.6f + 0.4f * glow), side * 0.026f)
    // The electrodes: a dark cap on each end of the tube.
    for (stroke in strokes) for (end in listOf(stroke.start, stroke.end)) drawCircle(lerp(colour, Color.Black, 0.55f), side * 0.022f, end)
}
