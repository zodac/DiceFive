package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

// A pressed pip's size, as a fraction of the pip area, and how rounded the die's soft corners are.
private const val LEATHER_PIP_FRACTION = 0.1f
private const val LEATHER_CORNER_PERCENT = 24

/**
 * Leather-covered dice - to go with the Leather cup, mat and background: a pebbled hide in [light] to
 * [dark] (different on every face of every die), stitched round just inside the edge in [thread], with
 * the pips pressed into it, each a hollow shadowed at its top left with its floor in [pip]. On a
 * coloured roll the hide takes the roll's face colours and the pips its pip colour.
 */
class LeatherDiceStyle(
    override val id: String,
    private val light: Color,
    private val dark: Color,
    private val thread: Color,
    private val pip: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = light

    override fun recoloured(palette: DieColourPalette): DiceStyle =
        LeatherDiceStyle(id, palette.diceTop, palette.diceBottom, lerp(palette.diceTop, Color.White, 0.6f), palette.pip, palette.heldRing)

    override val cornerPercent: Int = LEATHER_CORNER_PERCENT

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val pattern = naturalPatternSeed(value, styleSeed = 53)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(light, dark)),
            edge = lerp(dark, Color.Black, 0.35f),
            pipColor = pip,
            cornerPercent = LEATHER_CORNER_PERCENT,
            pipShape = PipShape.CUSTOM,
            heldRingColor = heldRing,
            customPips = { drawCachedSurface(LeatherPips(value, light, pip)) { drawPressedPips(value, light, pip) } },
        ) {
            drawCachedSurface(LeatherFace(light, dark, thread, pattern)) { paintLeather(light, dark, thread, pattern) }
        }
    }

    private data class LeatherFace(val light: Color, val dark: Color, val thread: Color, val pattern: Int)

    private data class LeatherPips(val value: Int, val light: Color, val pip: Color)
}

/** A pebbled hide of [light] to [dark] from [pattern], stitched round in [thread] just inside the edge. */
private fun DrawScope.paintLeather(light: Color, dark: Color, thread: Color, pattern: Int) {
    val m = size.minDimension
    paintPebbledHide(light, dark, Random(pattern * 13 + 5), pebbles = 1400, pebbleSize = m * 0.011f)
    val inset = m * 0.1f
    val stitch = Stroke(width = m * 0.022f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(m * 0.06f, m * 0.04f)))
    // The stitch holes' shadow, then the thread over it.
    drawRoundRect(
        Color.Black.copy(alpha = 0.3f), Offset(inset, inset + m * 0.01f), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(m * 0.14f),
        style = stitch,
    )
    drawRoundRect(thread, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(m * 0.14f), style = stitch)
}

/**
 * Pebbled leather: [pebbles] small bumps about [pebbleSize] across, each lit a touch at its top left
 * and shaded at its bottom right, over a soft sheen - shared by the Leather dice and background.
 */
internal fun DrawScope.paintPebbledHide(light: Color, dark: Color, random: Random, pebbles: Int, pebbleSize: Float, alpha: Float = 1f) {
    val m = size.minDimension
    val lit = lerp(light, Color.White, 0.3f).copy(alpha = 0.14f * alpha)
    val shade = lerp(dark, Color.Black, 0.4f).copy(alpha = 0.18f * alpha)
    repeat(pebbles) {
        val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
        val r = pebbleSize * (0.6f + random.nextFloat() * 0.8f)
        drawCircle(shade, r, at + Offset(r * 0.35f, r * 0.35f))
        drawCircle(lit, r * 0.85f, at - Offset(r * 0.2f, r * 0.2f))
    }
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.1f * alpha), Color.Transparent), Offset(size.width * 0.3f, size.height * 0.25f), m * 0.8f))
}

/** [value]'s pips pressed into the hide: each a hollow in shadow under its top-left wall, its floor [pip], its far lip lit. */
private fun DrawScope.drawPressedPips(value: Int, light: Color, pip: Color) {
    val radius = size.minDimension * LEATHER_PIP_FRACTION
    drawPipPositions(value) { centre ->
        drawCircle(lerp(light, Color.White, 0.3f).copy(alpha = 0.55f), radius * 1.05f, centre + Offset(radius * 0.14f, radius * 0.14f))
        drawCircle(
            Brush.linearGradient(listOf(lerp(pip, Color.Black, 0.5f), pip), centre - Offset(radius, radius), centre + Offset(radius * 0.4f, radius * 0.4f)),
            radius,
            centre,
        )
    }
}
