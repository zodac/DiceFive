package net.zodac.dicefive.ui.style

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

// A bauble's size, as a fraction of the pip area.
private const val BAUBLE_FRACTION = 0.11f

/**
 * Snowcapped dice - the Christmas dice's first colour: a [light] to [dark] face with snow drifted
 * over its top edge (a soft wavy hem and an icicle, between the pip columns so it never runs into a
 * pip), a few flakes falling below, and snowball pips in [pip]. On a coloured roll the face takes the
 * roll's colours and the snowballs its pip colour; the snow stays white.
 */
class SnowcapDiceStyle(
    override val id: String,
    private val light: Color,
    private val dark: Color,
    private val pip: Color = Color.White,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = light

    override fun recoloured(palette: DieColourPalette): DiceStyle = SnowcapDiceStyle(id, palette.diceTop, palette.diceBottom, palette.pip, palette.heldRing)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val pattern = naturalPatternSeed(value, styleSeed = 73)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(light, dark)),
            edge = lerp(dark, Color.Black, 0.3f),
            pipColor = pip,
            pipShape = PipShape.CUSTOM,
            heldRingColor = heldRing,
            customPips = { drawCachedSurface(Snowballs(value, pip)) { drawSnowballs(value, pip) } },
        ) {
            drawCachedSurface(SnowcapFace(pattern)) { paintSnowcap(pattern) }
        }
    }

    private data class Snowballs(val value: Int, val pip: Color)

    private data class SnowcapFace(val pattern: Int)
}

/** The snow over the face's top edge and the flakes falling below it, from [pattern]. */
private fun DrawScope.paintSnowcap(pattern: Int) {
    val m = size.minDimension
    val random = Random(pattern)
    repeat(14) {
        val at = Offset(random.nextFloat() * size.width, size.height * (0.2f + random.nextFloat() * 0.8f))
        drawCircle(Color.White.copy(alpha = 0.18f + random.nextFloat() * 0.2f), m * (0.008f + random.nextFloat() * 0.012f), at)
    }
    val snow = Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        val steps = 10
        for (i in steps downTo 0) lineTo(size.width * i / steps, m * (0.085f + 0.02f * sin(i * 1.3f + pattern)))
        close()
    }
    drawPath(snow, Color.Black.copy(alpha = 0.18f), style = Stroke(width = m * 0.03f))
    drawPath(snow, Brush.verticalGradient(listOf(Color.White, SnowShade), startY = 0f, endY = m * 0.11f))
    // An icicle, between the pip columns.
    val drip = size.width * (0.44f + random.nextFloat() * 0.12f)
    drawLine(Color.White, Offset(drip, m * 0.08f), Offset(drip, m * 0.13f), strokeWidth = m * 0.035f, cap = StrokeCap.Round)
}

/** [value]'s pips as snowballs of [pip], shaded away from the light and casting a little shadow. */
private fun DrawScope.drawSnowballs(value: Int, pip: Color) {
    val r = size.minDimension * 0.1f
    drawPipPositions(value) { c ->
        drawCircle(Color.Black.copy(alpha = 0.22f), r, c + Offset(r * 0.1f, r * 0.15f))
        drawCircle(Brush.radialGradient(listOf(lerp(pip, Color.White, 0.3f), lerp(pip, Color.Black, 0.08f)), c - Offset(r * 0.3f, r * 0.3f), r * 1.2f), r, c)
    }
}

private val SnowShade = Color(0xFFE3EEF7)

/**
 * Bauble dice: a pine-green face lightly dusted with snowflakes, its pips Christmas baubles hanging
 * from little gold caps - alternately [bauble] and [baubleAlt], each with a shine at its top left. On
 * a coloured roll the face takes the roll's colours and every bauble its pip colour.
 */
class BaubleDiceStyle(
    override val id: String,
    private val light: Color,
    private val dark: Color,
    private val bauble: Color,
    private val baubleAlt: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = light

    override fun recoloured(palette: DieColourPalette): DiceStyle =
        BaubleDiceStyle(id, palette.diceTop, palette.diceBottom, palette.pip, palette.pip, palette.heldRing)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val pattern = naturalPatternSeed(value, styleSeed = 71)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(light, dark)),
            edge = lerp(dark, Color.Black, 0.3f),
            pipColor = bauble,
            pipShape = PipShape.CUSTOM,
            heldRingColor = heldRing,
            customPips = { drawCachedSurface(Baubles(value, bauble, baubleAlt)) { drawBaubles(value, bauble, baubleAlt) } },
        ) {
            drawCachedSurface(Snowflakes(pattern)) { paintSnowflakes(pattern) }
        }
    }

    private data class Baubles(val value: Int, val bauble: Color, val baubleAlt: Color)

    private data class Snowflakes(val pattern: Int)
}

/** A few faint snowflakes scattered over the face, from [pattern]. */
private fun DrawScope.paintSnowflakes(pattern: Int) {
    val random = Random(pattern * 3 + 1)
    val m = size.minDimension
    repeat(7) {
        val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
        val r = m * (0.04f + random.nextFloat() * 0.04f)
        val turn = random.nextFloat()
        val flake = Color.White.copy(alpha = 0.16f + random.nextFloat() * 0.1f)
        for (arm in 0 until 6) {
            val a = turn + arm * PI.toFloat() / 3f
            val tip = at + Offset(cos(a), sin(a)) * r
            drawLine(flake, at, tip, strokeWidth = m * 0.012f, cap = StrokeCap.Round)
            // A little V near each tip.
            val fork = at + Offset(cos(a), sin(a)) * (r * 0.6f)
            for (side in listOf(-0.6f, 0.6f)) {
                drawLine(flake, fork, fork + Offset(cos(a + side), sin(a + side)) * (r * 0.35f), strokeWidth = m * 0.009f, cap = StrokeCap.Round)
            }
        }
    }
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), Offset(size.width * 0.3f, size.height * 0.25f), m * 0.7f))
}

/** [value]'s pips as baubles, alternately [bauble] and [alt], each hanging from a gold cap and shining at its top left. */
private fun DrawScope.drawBaubles(value: Int, bauble: Color, alt: Color) {
    val radius = size.minDimension * BAUBLE_FRACTION
    var index = 0
    drawPipPositions(value) { centre ->
        val colour = if (index++ % 2 == 0) bauble else alt
        drawCircle(Color.Black.copy(alpha = 0.25f), radius, centre + Offset(radius * 0.12f, radius * 0.16f))
        drawCircle(
            Brush.radialGradient(listOf(lerp(colour, Color.White, 0.35f), colour, lerp(colour, Color.Black, 0.35f)), centre - Offset(radius * 0.35f, radius * 0.35f), radius * 1.4f),
            radius,
            centre,
        )
        // The cap and its loop, on top.
        val capWidth = radius * 0.6f
        val capTop = centre.y - radius - radius * 0.22f
        drawLine(lerp(GoldAccent, Color.Black, 0.25f), Offset(centre.x, capTop - radius * 0.12f), Offset(centre.x, capTop), strokeWidth = radius * 0.4f, cap = StrokeCap.Round)
        drawRect(GoldAccent, Offset(centre.x - capWidth / 2f, capTop), Size(capWidth, radius * 0.32f))
        drawCircle(Color.White.copy(alpha = 0.75f), radius * 0.22f, centre - Offset(radius * 0.38f, radius * 0.38f))
    }
}

/**
 * Gift-wrapped dice - to go with the Christmas cup's red: each a present in polka-dotted [paper] (its
 * accent for the dots), a [ribbon] running down the middle of the face to a bow at the top, and white
 * pips ringed in the paper's own dark, so they read on the paper and the ribbon alike. On a coloured
 * roll the paper takes the roll's colours, and the pips and ribbon its pip colour.
 */
class GiftDiceStyle(
    override val id: String,
    private val paper: CupPalette,
    private val ribbon: Color,
    private val pip: Color = Color.White,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = paper.mid

    override fun recoloured(palette: DieColourPalette): DiceStyle {
        val coloured = CupPalette(
            dark = lerp(palette.diceBottom, Color.Black, 0.4f), light = palette.diceTop, mid = palette.diceBottom,
            accent = lerp(palette.diceTop, Color.White, 0.6f), interior = palette.diceBottom,
        )
        // The ribbon in a shade of the pip colour - picked to stand out on that colour, as gold wouldn't on yellow.
        return GiftDiceStyle(id, coloured, lerp(palette.pip, palette.diceBottom, 0.25f), palette.pip, palette.heldRing)
    }

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(paper.light, paper.mid)),
            edge = paper.dark,
            pipColor = pip,
            pipShape = PipShape.CUSTOM,
            heldRingColor = heldRing,
            customPips = { drawCachedSurface(GiftPips(value, pip, paper.dark)) { drawGiftPips(value, pip, paper.dark) } },
        ) {
            drawCachedSurface(GiftFace(paper, ribbon)) { paintGiftWrap(paper, ribbon) }
        }
    }

    private data class GiftFace(val paper: CupPalette, val ribbon: Color)

    private data class GiftPips(val value: Int, val pip: Color, val ring: Color)
}

/** The wrapped face: polka dots, the ribbon down its middle, and a bow tied at the top. */
private fun DrawScope.paintGiftWrap(paper: CupPalette, ribbon: Color) {
    val m = size.minDimension
    val gap = m * 0.2f
    var row = 0
    var y = gap / 2f
    while (y < size.height + gap) {
        var x = if (row % 2 == 0) gap / 4f else gap * 0.75f
        while (x < size.width + gap) {
            drawCircle(paper.accent.copy(alpha = 0.45f), m * 0.025f, Offset(x, y))
            x += gap
        }
        y += gap / 2f
        row++
    }
    val band = m * 0.13f
    drawRibbonBand(Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), band, ribbon)
    drawBow(Offset(size.width / 2f, m * 0.1f), m * 0.1f, ribbon)
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), Offset(size.width * 0.3f, size.height * 0.25f), m * 0.7f))
}

/** [value]'s pips in [pip], each ringed in [ring] and casting a little shadow, so they stand out on paper and ribbon. */
private fun DrawScope.drawGiftPips(value: Int, pip: Color, ring: Color) {
    val radius = size.minDimension * 0.1f
    drawPipPositions(value) { centre ->
        drawCircle(Color.Black.copy(alpha = 0.25f), radius * 1.1f, centre + Offset(radius * 0.1f, radius * 0.15f))
        drawCircle(ring, radius * 1.12f, centre)
        drawCircle(pip, radius, centre)
    }
}

