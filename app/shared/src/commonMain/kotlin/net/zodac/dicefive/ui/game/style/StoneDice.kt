package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

/** A kind of stone, and so how its face is textured - see [paintStone]. */
enum class StoneKind {
    /** Coarse crystals: dark, pale and pink grains packed together. */
    GRANITE,

    /** Fine layers it splits along, running across the face, with a dull sheen. */
    SLATE,

    /** Sand laid down in wavy bands of slightly different tones, gritty all over. */
    SANDSTONE,

    /** Pale and soft, mottled, with fine pores and the odd fossil shell. */
    LIMESTONE,

    /** Near-black volcanic rock, pocked with the holes gas bubbles left. */
    BASALT,
}

// A carved pip's size, as a fraction of the pip area, and how rounded the die's chiselled corners are.
private const val STONE_PIP_FRACTION = 0.105f
private const val STONE_CORNER_PERCENT = 16

/**
 * Dice cut from stone: a face of [kind]'s own texture in [light] to [dark] - different on every face
 * of every die, as real stone never repeats - with pips carved into it, each a hollow in shadow at
 * its top left and catching the light at its bottom right, its floor in [pip]. On a coloured roll the
 * stone takes the roll's face colours and the pips its pip colour.
 */
class StoneDiceStyle(
    override val id: String,
    private val kind: StoneKind,
    private val light: Color,
    private val dark: Color,
    private val pip: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = light

    override fun recoloured(palette: DieColourPalette): DiceStyle =
        StoneDiceStyle(id, kind, palette.diceTop, palette.diceBottom, palette.pip, palette.heldRing)

    override val cornerPercent: Int = STONE_CORNER_PERCENT

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val pattern = naturalPatternSeed(value, styleSeed = kind.ordinal * 7)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(light, dark)),
            edge = lerp(dark, Color.Black, 0.25f),
            pipColor = pip,
            cornerPercent = STONE_CORNER_PERCENT,
            pipShape = PipShape.CUSTOM,
            heldRingColor = heldRing,
            customPips = { drawCachedSurface(StonePips(value, light, dark, pip, pattern)) { drawCarvedPips(value, light, dark, pip, pattern) } },
        ) {
            drawCachedSurface(StoneFace(kind, light, dark, pattern)) { paintStone(kind, light, dark, pattern) }
        }
    }

    private data class StoneFace(val kind: StoneKind, val light: Color, val dark: Color, val pattern: Int)

    private data class StonePips(val value: Int, val light: Color, val dark: Color, val pip: Color, val pattern: Int)
}

/**
 * [value]'s pips carved into stone of [light] to [dark] (the Stone dice, and the Marble ones in the same
 * family): each a slightly irregular hollow - chisel work, not a drill - in shadow under its top-left
 * wall, its floor in [pip], and its bottom-right lip catching the light. [pattern] makes each face's
 * hollows its own.
 */
internal fun DrawScope.drawCarvedPips(value: Int, light: Color, dark: Color, pip: Color, pattern: Int) {
    val random = Random(pattern * 3 + 17)
    val radius = size.minDimension * STONE_PIP_FRACTION
    drawPipPositions(value) { centre ->
        val outline = List(12) { i ->
            val a = i * 2f * PI.toFloat() / 12
            val r = radius * (0.94f + random.nextFloat() * 0.12f)
            centre + Offset(cos(a) * r, sin(a) * r)
        }
        val hollow = smoothPath(outline, closed = true)
        // The lit lip, just outside the hollow at its bottom right.
        drawPath(smoothPath(outline.map { it + Offset(radius * 0.12f, radius * 0.12f) }, closed = true), lerp(light, Color.White, 0.35f).copy(alpha = 0.7f))
        drawPath(hollow, Brush.linearGradient(listOf(lerp(pip, Color.Black, 0.45f), pip, lerp(pip, light, 0.15f)), start = centre - Offset(radius, radius), end = centre + Offset(radius, radius)))
        drawPath(hollow, lerp(dark, Color.Black, 0.5f).copy(alpha = 0.6f), style = Stroke(width = radius * 0.1f))
    }
}

/** [kind]'s texture over a face of [light] to [dark], from [pattern] - painted once per face. */
private fun DrawScope.paintStone(kind: StoneKind, light: Color, dark: Color, pattern: Int) {
    val random = Random(pattern * 11 + kind.ordinal)
    when (kind) {
        StoneKind.GRANITE -> paintGranite(light, dark, random)
        StoneKind.SLATE -> paintSlate(light, dark, random)
        StoneKind.SANDSTONE -> paintSandstone(light, dark, random)
        StoneKind.LIMESTONE -> paintLimestone(light, dark, random)
        StoneKind.BASALT -> paintBasalt(light, dark, random)
    }
    // Every stone: a soft polish from the top left, and its edges worn a little darker.
    val m = size.minDimension
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), Offset(size.width * 0.3f, size.height * 0.25f), m * 0.7f))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, dark.copy(alpha = 0.3f)), Offset(size.width / 2f, size.height / 2f), m * 0.75f))
}

private fun DrawScope.anywhere(random: Random) = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)

/** A small rough grain at [at], about [radius] across: a few corners, each its own distance out. */
private fun grain(at: Offset, radius: Float, random: Random): Path {
    val corners = 4 + random.nextInt(3)
    val turn = random.nextFloat() * 6.28f
    return polygonPath(List(corners) { i ->
        val a = turn + i * 6.28f / corners
        at + Offset(cos(a), sin(a)) * (radius * (0.6f + random.nextFloat() * 0.6f))
    })
}

/** Granite: packed crystals - dark mica and hornblende, glassy grey quartz, pale and pink feldspar. */
private fun DrawScope.paintGranite(light: Color, dark: Color, random: Random) {
    val m = size.minDimension
    val minerals = listOf(
        Color(0xFF1E1E22) to 0.3f,
        lerp(light, Color.White, 0.5f) to 0.25f,
        lerp(light, Color(0xFFD9A08C), 0.6f) to 0.25f,
        lerp(dark, Color(0xFF8A8F96), 0.5f) to 0.2f,
    )
    repeat(1400) {
        val roll = random.nextFloat()
        var sum = 0f
        val colour = minerals.first { (_, share) -> sum += share; roll <= sum }.first
        drawPath(grain(anywhere(random), m * (0.005f + random.nextFloat() * 0.011f), random), colour.copy(alpha = 0.8f))
    }
}

/** Slate: the fine layers it splits along, running across the face at a slant, some lighter, some darker. */
private fun DrawScope.paintSlate(light: Color, dark: Color, random: Random) {
    val m = size.minDimension
    val slant = (random.nextFloat() - 0.5f) * 0.3f
    var y = -m * 0.2f
    while (y < size.height + m * 0.2f) {
        val tone = if (random.nextBoolean()) lerp(light, Color.White, 0.25f) else lerp(dark, Color.Black, 0.3f)
        val wave = random.nextFloat() * 6.28f
        val layer = Path().apply {
            moveTo(0f, y)
            for (i in 1..12) {
                val x = size.width * i / 12f
                lineTo(x, y + x * slant + sin(i * 0.8f + wave) * m * 0.006f)
            }
        }
        drawPath(layer, tone.copy(alpha = 0.18f + random.nextFloat() * 0.2f), style = Stroke(width = m * (0.006f + random.nextFloat() * 0.02f)))
        y += m * (0.025f + random.nextFloat() * 0.05f)
    }
    // A faint rusty stain or two, where water has run over it.
    repeat(2) {
        val at = anywhere(random)
        val r = m * (0.12f + random.nextFloat() * 0.15f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF8A6A4A).copy(alpha = 0.18f), Color.Transparent), at, r), r, at)
    }
}

/** Sandstone: bands of sand in gently wavy layers, a shade lighter or darker each, gritty all over. */
private fun DrawScope.paintSandstone(light: Color, dark: Color, random: Random) {
    val m = size.minDimension
    val slant = (random.nextFloat() - 0.5f) * 0.25f
    var y = -m * 0.1f
    while (y < size.height + m * 0.15f) {
        val thick = m * (0.04f + random.nextFloat() * 0.1f)
        val tone = lerp(lerp(light, Color(0xFFE0A070), 0.25f), dark, random.nextFloat() * 0.7f)
        val phase = random.nextFloat() * 6.28f
        val band = Path().apply {
            moveTo(0f, y)
            for (i in 1..16) {
                val x = size.width * i / 16f
                lineTo(x, y + x * slant + sin(i * 0.5f + phase) * m * 0.025f)
            }
        }
        drawPath(band, tone.copy(alpha = 0.35f), style = Stroke(width = thick))
        y += thick * (0.8f + random.nextFloat() * 0.5f)
    }
    repeat(380) {
        val tone = if (random.nextBoolean()) lerp(light, Color.White, 0.4f) else lerp(dark, Color.Black, 0.35f)
        drawCircle(tone.copy(alpha = 0.35f), m * (0.003f + random.nextFloat() * 0.005f), anywhere(random))
    }
}

/** Limestone: pale and soft, mottled, with fine pores and now and then the imprint of a little shell. */
private fun DrawScope.paintLimestone(light: Color, dark: Color, random: Random) {
    val m = size.minDimension
    repeat(8) {
        val at = anywhere(random)
        val r = m * (0.12f + random.nextFloat() * 0.25f)
        val tint = if (random.nextBoolean()) lerp(light, Color.White, 0.5f) else dark
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.3f), Color.Transparent), at, r), r, at)
    }
    repeat(140) {
        drawCircle(lerp(dark, Color.Black, 0.3f).copy(alpha = 0.3f), m * (0.003f + random.nextFloat() * 0.006f), anywhere(random))
    }
    // A fossil or two: a little coiled shell, or a ribbed fan.
    val ink = lerp(dark, Color.Black, 0.25f).copy(alpha = 0.45f)
    repeat(1 + random.nextInt(2)) {
        val at = anywhere(random)
        val r = m * (0.06f + random.nextFloat() * 0.05f)
        if (random.nextBoolean()) {
            val coil = Path()
            for (step in 0..40) {
                val t = step / 40f
                val a = t * 4f * PI.toFloat()
                val p = at + Offset(cos(a), sin(a)) * (r * (0.15f + 0.85f * t))
                if (step == 0) coil.moveTo(p.x, p.y) else coil.lineTo(p.x, p.y)
            }
            drawPath(coil, ink, style = Stroke(width = m * 0.008f, cap = StrokeCap.Round))
        } else {
            val turn = random.nextFloat() * 6.28f
            for (k in -3..3) {
                val a = turn + k * 0.22f
                drawLine(ink, at, at + Offset(cos(a), sin(a)) * r, strokeWidth = m * 0.007f, cap = StrokeCap.Round)
            }
            val degrees = 180f / PI.toFloat()
            drawArc(ink, (turn - 0.66f) * degrees, 1.32f * degrees, useCenter = false, topLeft = at - Offset(r, r), size = Size(r * 2f, r * 2f), style = Stroke(width = m * 0.007f))
        }
    }
}

/** Basalt: near-black and fine-grained, pocked with the holes left by gas bubbles in the cooling lava. */
private fun DrawScope.paintBasalt(light: Color, dark: Color, random: Random) {
    val m = size.minDimension
    repeat(300) {
        drawCircle((if (random.nextBoolean()) lerp(light, Color.White, 0.2f) else lerp(dark, Color.Black, 0.4f)).copy(alpha = 0.35f), m * (0.003f + random.nextFloat() * 0.005f), anywhere(random))
    }
    repeat(22) {
        val at = anywhere(random)
        val r = m * (0.008f + random.nextFloat().let { it * it } * 0.03f)
        // Each hole: its lit lower lip, then the hole itself, darkest under its upper wall.
        drawCircle(lerp(light, Color.White, 0.25f).copy(alpha = 0.5f), r, at + Offset(r * 0.2f, r * 0.25f))
        drawCircle(Brush.radialGradient(listOf(lerp(dark, Color.Black, 0.7f), lerp(dark, Color.Black, 0.3f)), at - Offset(r * 0.3f, r * 0.3f), r * 1.3f), r, at)
    }
}
