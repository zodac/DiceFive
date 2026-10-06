package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
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

// A neon tube's slow pulse: a full swell and fade every NEON_PULSE_SECONDS, never dimmer than NEON_PULSE_FLOOR.
private const val NEON_PULSE_SECONDS = 1.8f
private const val NEON_PULSE_FLOOR = 0.5f

/**
 * Neon dice: a black panel with its number bent out of a glowing neon tube - layered halos round a
 * bright core, a little capped electrode at each end, the panel itself lit by its glow - and every
 * number a different colour of gas ([colours]). The glow pulses gently, each die on its own beat;
 * steady under reduced motion.
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
                drawNeonDigit(it, colours[it], glow = NEON_PULSE_FLOOR + (1f - NEON_PULSE_FLOOR) * swell)
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

/** [value] as a neon tube of [colour], its halo at [glow] (0-1) of full strength. */
internal fun DrawScope.drawNeonDigit(value: Int, colour: Color, glow: Float) {
    val side = size.minDimension
    val centre = Offset(size.width / 2f, size.height / 2f)
    // The tube lights the panel round it.
    drawCircle(
        Brush.radialGradient(listOf(colour.copy(alpha = 0.28f * glow), Color.Transparent), center = centre, radius = side * 0.55f),
        side * 0.55f,
        centre,
    )
    for (stroke in strokeDigitPaths(value, Offset(side * 0.1f, side * 0.09f), side * 0.8f)) {
        fun tube(c: Color, w: Float) = drawPath(stroke.path, c, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        tube(colour.copy(alpha = 0.1f * glow), side * 0.3f)
        tube(colour.copy(alpha = 0.18f * glow), side * 0.19f)
        tube(colour.copy(alpha = 0.35f * glow), side * 0.115f)
        tube(lerp(colour, Color.White, 0.2f), side * 0.065f)
        tube(lerp(colour, Color.White, 0.75f).copy(alpha = 0.6f + 0.4f * glow), side * 0.026f)
        // The electrodes: a dark cap on each end of the tube.
        for (end in listOf(stroke.start, stroke.end)) drawCircle(lerp(colour, Color.Black, 0.55f), side * 0.022f, end)
    }
}
