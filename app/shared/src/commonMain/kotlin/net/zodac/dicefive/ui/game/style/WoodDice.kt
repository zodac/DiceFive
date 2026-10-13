package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

// An inlaid pip's size, as a fraction of the pip area, and how rounded the die's sanded corners are.
private const val WOOD_PIP_FRACTION = 0.105f
private const val WOOD_CORNER_PERCENT = 18

/**
 * Dice cut from a block of wood, [light] to [dark], with its [grain]: the long grain runs across four
 * faces, and the two opposite ends (1 and 6) show the end grain's growth rings, as a real wooden cube
 * does - different on every face of every die. The pips are plugs of a contrasting [pip] wood, sanded
 * flush. On a coloured roll the wood takes the roll's face colours and the pips its pip colour.
 */
class WoodDiceStyle(
    override val id: String,
    private val light: Color,
    private val dark: Color,
    private val grain: Color,
    private val pip: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = light

    override fun recoloured(palette: DieColourPalette): DiceStyle =
        WoodDiceStyle(id, palette.diceTop, palette.diceBottom, lerp(palette.diceBottom, Color.Black, 0.3f), palette.pip, palette.heldRing)

    override val cornerPercent: Int = WOOD_CORNER_PERCENT

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val pattern = naturalPatternSeed(value, styleSeed = 31)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(light, dark)),
            edge = lerp(dark, Color.Black, 0.3f),
            pipColor = pip,
            cornerPercent = WOOD_CORNER_PERCENT,
            pipShape = PipShape.CUSTOM,
            heldRingColor = heldRing,
            customPips = { drawCachedSurface(WoodPips(value, pip, pattern)) { drawInlaidPips(value, pip, pattern) } },
        ) {
            drawCachedSurface(WoodFace(light, grain, value == 1 || value == 6, pattern)) { paintWood(light, grain, value == 1 || value == 6, pattern) }
        }
    }

    private data class WoodFace(val light: Color, val grain: Color, val endGrain: Boolean, val pattern: Int)

    private data class WoodPips(val value: Int, val pip: Color, val pattern: Int)
}

/** The wood's grain over its face: long wavy lines, or (on an end) the rings round a heart off the face. */
private fun DrawScope.paintWood(light: Color, grain: Color, endGrain: Boolean, pattern: Int) {
    val random = Random(pattern * 7 + 3)
    val m = size.minDimension
    if (endGrain) {
        // The heart of the tree lies somewhere off one corner, so only arcs of its rings cross the face.
        val heart = Offset(
            if (random.nextBoolean()) -m * (0.2f + random.nextFloat() * 0.5f) else size.width + m * (0.2f + random.nextFloat() * 0.5f),
            size.height * random.nextFloat(),
        )
        var r = m * 0.1f
        while (r < m * 2.2f) {
            val ring = Path().apply {
                for (i in 0..72) {
                    val a = i * 2f * PI.toFloat() / 72
                    val wobble = 1f + sin(a * 5f + r) * 0.015f
                    val p = heart + Offset(cos(a), sin(a)) * (r * wobble)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }
            drawPath(ring, grain.copy(alpha = 0.22f + random.nextFloat() * 0.25f), style = Stroke(width = m * (0.008f + random.nextFloat() * 0.018f)))
            r += m * (0.035f + random.nextFloat() * 0.05f)
        }
    } else {
        val horizontal = random.nextBoolean()
        val along = if (horizontal) size.width else size.height
        val across = if (horizontal) size.height else size.width
        val phase = random.nextFloat() * 6.28f
        var at = -m * 0.05f
        while (at < across + m * 0.05f) {
            val wave = m * (0.01f + random.nextFloat() * 0.03f)
            val line = Path().apply {
                for (i in 0..24) {
                    val t = along * i / 24f
                    val off = at + sin(t / m * 3.4f + phase + at / m * 2f) * wave
                    val p = if (horizontal) Offset(t, off) else Offset(off, t)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }
            drawPath(line, grain.copy(alpha = 0.18f + random.nextFloat() * 0.3f), style = Stroke(width = m * (0.006f + random.nextFloat() * 0.02f)))
            at += m * (0.03f + random.nextFloat() * 0.06f)
        }
    }
    // A satin finish: soft light from the top left, the edges a touch darker.
    drawRect(Brush.radialGradient(listOf(lerp(light, Color.White, 0.5f).copy(alpha = 0.18f), Color.Transparent), Offset(size.width * 0.3f, size.height * 0.25f), m * 0.7f))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, grain.copy(alpha = 0.22f)), Offset(size.width / 2f, size.height / 2f), m * 0.78f))
}

/** [value]'s pips as plugs of [pip] wood, sanded flush: a fine dark seam round each, and a little grain of their own. */
private fun DrawScope.drawInlaidPips(value: Int, pip: Color, pattern: Int) {
    val random = Random(pattern * 5 + 11)
    val radius = size.minDimension * WOOD_PIP_FRACTION
    val seam = lerp(pip, Color.Black, 0.55f)
    drawPipPositions(value) { centre ->
        drawCircle(seam.copy(alpha = 0.7f), radius * 1.08f, centre)
        drawCircle(Brush.linearGradient(listOf(lerp(pip, Color.White, 0.12f), lerp(pip, Color.Black, 0.12f)), centre - Offset(radius, radius), centre + Offset(radius, radius)), radius, centre)
        val tilt = random.nextFloat() - 0.5f
        for (k in -1..1) {
            val y = centre.y + k * radius * 0.45f
            drawLine(seam.copy(alpha = 0.12f), Offset(centre.x - radius * 0.7f, y - tilt * radius * 0.3f), Offset(centre.x + radius * 0.7f, y + tilt * radius * 0.3f), strokeWidth = radius * 0.12f)
        }
    }
}
