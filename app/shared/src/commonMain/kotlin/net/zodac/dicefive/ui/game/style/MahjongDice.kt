package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.zodac.dicefive.ui.theme.GoldAccent

/** The three numbered suits of mahjong tiles, which a [MahjongDiceStyle] draws its faces as. */
enum class MahjongSuit {
    /** Characters: the Chinese numeral over the character 萬 (ten thousand). */
    MANZU,

    /** Circles (dots): coins, as many as the number. */
    PINZU,

    /** Bamboo: sticks of bamboo, as many as the number - and a bird for the 1, as on real tiles. */
    SOZU,
}

private val MahjongBlue = Color(0xFF1E4C9A)
private val MahjongGreen = Color(0xFF17703A)
private val MahjongRed = Color(0xFFC0242A)
private val MahjongInk = Color(0xFF16182A)

// Text faces: laid out at this size, then scaled to the die.
private const val MAHJONG_LAYOUT_PX = 100f

private val ChineseNumerals = listOf("一", "二", "三", "四", "五", "六")
private const val WAN = "萬"

/**
 * Mahjong-tile dice in one [suit]: an ivory tile face with a jade back showing at its edge, and the
 * tile of the die's number engraved on it in the tiles' own blue, green and red. On a coloured roll
 * everything is engraved in the roll's pip colour instead.
 */
class MahjongDiceStyle(
    override val id: String,
    private val suit: MahjongSuit,
    private val face: Color,
    private val faceShade: Color,
    private val back: Color,
    private val inks: List<Color>? = null,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = when (suit) {
        MahjongSuit.MANZU -> MahjongRed
        MahjongSuit.PINZU -> MahjongBlue
        MahjongSuit.SOZU -> MahjongGreen
    }

    override fun recoloured(palette: DieColourPalette): DiceStyle =
        MahjongDiceStyle(id, suit, palette.diceTop, palette.diceBottom, palette.diceBottom, List(4) { palette.pip }, palette.heldRing)

    override val cornerPercent: Int = 14

    // The tiles' colours: blue, green, red and the black ink - or the one colour of a recoloured die.
    private val blue get() = inks?.get(0) ?: MahjongBlue
    private val green get() = inks?.get(1) ?: MahjongGreen
    private val red get() = inks?.get(2) ?: MahjongRed
    private val ink get() = inks?.get(3) ?: MahjongInk

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val text = if (suit == MahjongSuit.MANZU) rememberManzuLayouts(value) else null
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(face, faceShade)),
            edge = back,
            edgeWidth = 2.dp,
            pipColor = red,
            cornerPercent = cornerPercent,
            pipShape = PipShape.CUSTOM,
            pipPadding = 0.dp,
            heldRingColor = heldRing,
            customPips = {
                drawCachedSurface(TileFace(suit, value, inks, face)) {
                    drawTileBevel()
                    when (suit) {
                        MahjongSuit.MANZU -> drawManzu(text!!)
                        MahjongSuit.PINZU -> drawPinzu(value)
                        MahjongSuit.SOZU -> drawSozu(value)
                    }
                }
            },
        )
    }

    private data class TileFace(val suit: MahjongSuit, val value: Int, val inks: List<Color>?, val face: Color)

    /** The tile's carved face: a soft shadow just inside its edge, as the face curves down to the back. */
    private fun DrawScope.drawTileBevel() {
        val m = size.minDimension
        drawRoundRect(
            Color.Black.copy(alpha = 0.08f),
            topLeft = Offset(m * 0.05f, m * 0.05f),
            size = Size(size.width - m * 0.1f, size.height - m * 0.1f),
            cornerRadius = CornerRadius(m * 0.08f),
            style = Stroke(width = m * 0.03f),
        )
    }

    private fun DrawScope.drawManzu(text: Pair<TextLayoutResult, TextLayoutResult>) {
        val (numeral, wan) = text
        drawGlyph(numeral, Offset(size.width / 2f, size.height * 0.29f), size.minDimension * 0.4f, ink)
        drawGlyph(wan, Offset(size.width / 2f, size.height * 0.7f), size.minDimension * 0.44f, red)
    }

    /** [layout] scaled to [height] and centred on [centre]. */
    private fun DrawScope.drawGlyph(layout: TextLayoutResult, centre: Offset, height: Float, colour: Color) {
        val scale = height / layout.size.height
        withTransform({
            translate(centre.x - layout.size.width * scale / 2f, centre.y - layout.size.height * scale / 2f)
            scale(scale, scale, Offset.Zero)
        }) { drawText(layout, colour) }
    }

    private fun DrawScope.drawPinzu(value: Int) {
        val (radius, coins) = when (value) {
            1 -> 0.3f to listOf(Offset(0.5f, 0.5f) to green)
            2 -> 0.17f to listOf(Offset(0.5f, 0.28f) to green, Offset(0.5f, 0.72f) to blue)
            3 -> 0.14f to listOf(Offset(0.26f, 0.25f) to blue, Offset(0.5f, 0.5f) to red, Offset(0.74f, 0.75f) to green)
            4 -> 0.15f to listOf(Offset(0.3f, 0.3f) to blue, Offset(0.7f, 0.3f) to green, Offset(0.3f, 0.7f) to green, Offset(0.7f, 0.7f) to blue)
            5 -> 0.13f to listOf(
                Offset(0.27f, 0.27f) to blue, Offset(0.73f, 0.27f) to green, Offset(0.5f, 0.5f) to red,
                Offset(0.27f, 0.73f) to green, Offset(0.73f, 0.73f) to blue,
            )
            else -> 0.12f to listOf(
                Offset(0.31f, 0.2f) to green, Offset(0.69f, 0.2f) to green,
                Offset(0.31f, 0.55f) to red, Offset(0.69f, 0.55f) to red, Offset(0.31f, 0.82f) to red, Offset(0.69f, 0.82f) to red,
            )
        }
        val m = size.minDimension
        for ((at, colour) in coins) {
            val centre = Offset(at.x * size.width, at.y * size.height)
            if (value == 1) drawGrandCoin(centre, m * radius) else drawCoin(centre, m * radius, colour)
        }
    }

    /** One coin of the circles suit: a ring, a ring of eight petals inside it, and a hub with a hole. */
    private fun DrawScope.drawCoin(centre: Offset, radius: Float, colour: Color) {
        inUnit(centre, radius) {
            drawCircle(colour, 0.92f, Offset.Zero, style = Stroke(width = 0.16f))
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f
                drawCircle(colour, 0.13f, Offset(cos(a), sin(a)) * 0.58f)
            }
            drawCircle(colour, 0.3f, Offset.Zero)
            drawCircle(face, 0.12f, Offset.Zero)
        }
    }

    /** The 1 of circles: one great ornate coin - rings of green, red and blue round a petalled hub. */
    private fun DrawScope.drawGrandCoin(centre: Offset, radius: Float) {
        inUnit(centre, radius) {
            drawCircle(green, 0.97f, Offset.Zero, style = Stroke(width = 0.07f))
            for (k in 0 until 16) {
                val a = k * PI.toFloat() / 8f
                drawCircle(green, 0.075f, Offset(cos(a), sin(a)) * 0.84f)
            }
            drawCircle(red, 0.7f, Offset.Zero, style = Stroke(width = 0.1f))
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f + PI.toFloat() / 8f
                val petal = Path().apply {
                    val tip = Offset(cos(a), sin(a)) * 0.56f
                    val side = Offset(-sin(a), cos(a)) * 0.12f
                    val root = Offset(cos(a), sin(a)) * 0.2f
                    moveTo(root.x + side.x, root.y + side.y)
                    quadraticTo(tip.x + side.x, tip.y + side.y, tip.x, tip.y)
                    quadraticTo(tip.x - side.x, tip.y - side.y, root.x - side.x, root.y - side.y)
                    close()
                }
                drawPath(petal, blue)
            }
            drawCircle(red, 0.2f, Offset.Zero)
            drawCircle(face, 0.08f, Offset.Zero)
        }
    }

    private fun DrawScope.drawSozu(value: Int) {
        if (value == 1) {
            drawBird(Offset(size.width / 2f, size.height * 0.52f), size.minDimension * 0.36f)
            return
        }
        val (length, sticks) = when (value) {
            2 -> 0.3f to listOf(Offset(0.5f, 0.3f) to green, Offset(0.5f, 0.7f) to blue)
            3 -> 0.3f to listOf(Offset(0.5f, 0.29f) to green, Offset(0.33f, 0.71f) to blue, Offset(0.67f, 0.71f) to blue)
            4 -> 0.3f to listOf(Offset(0.33f, 0.29f) to green, Offset(0.67f, 0.29f) to blue, Offset(0.33f, 0.71f) to blue, Offset(0.67f, 0.71f) to green)
            5 -> 0.28f to listOf(
                Offset(0.25f, 0.29f) to green, Offset(0.75f, 0.29f) to blue, Offset(0.5f, 0.5f) to red,
                Offset(0.25f, 0.71f) to blue, Offset(0.75f, 0.71f) to green,
            )
            else -> 0.3f to listOf(
                Offset(0.25f, 0.29f) to green, Offset(0.5f, 0.29f) to green, Offset(0.75f, 0.29f) to green,
                Offset(0.25f, 0.71f) to blue, Offset(0.5f, 0.71f) to blue, Offset(0.75f, 0.71f) to blue,
            )
        }
        val m = size.minDimension
        for ((at, colour) in sticks) drawBamboo(Offset(at.x * size.width, at.y * size.height), m * length, m * 0.085f, colour)
    }

    /** One stick of bamboo [length] long and [width] wide: rounded ends, a node in the middle and at each end, a lit edge. */
    private fun DrawScope.drawBamboo(centre: Offset, length: Float, width: Float, colour: Color) {
        val top = centre.y - length / 2f
        drawRoundRect(colour, topLeft = Offset(centre.x - width / 2f, top), size = Size(width, length), cornerRadius = CornerRadius(width / 2f))
        drawLine(face.copy(alpha = 0.7f), Offset(centre.x - width * 0.12f, top + width * 0.6f), Offset(centre.x - width * 0.12f, top + length - width * 0.6f), strokeWidth = width * 0.18f)
        // The joints: one in the middle, a slight swelling either end.
        for (y in listOf(centre.y)) {
            drawLine(colour, Offset(centre.x - width * 0.62f, y), Offset(centre.x + width * 0.62f, y), strokeWidth = width * 0.3f, cap = StrokeCap.Round)
        }
    }

    /**
     * The 1 of bamboo: a bird (a sparrow, as many sets draw it) perched facing left - a green body
     * and fanned tail, a blue wing, a red crest and breast.
     */
    private fun DrawScope.drawBird(centre: Offset, radius: Float) {
        inUnit(centre, radius) {
            // The tail: three feathers fanned up behind it.
            for ((k, colour) in listOf(green, blue, green).withIndex()) {
                val a = -0.25f - k * 0.4f
                val tip = Offset(0.35f + cos(a) * 0.85f, -0.05f + sin(a) * 0.85f)
                val feather = Path().apply {
                    moveTo(0.3f, 0.12f)
                    quadraticTo((0.3f + tip.x) / 2f + 0.15f, (0.12f + tip.y) / 2f + 0.1f, tip.x, tip.y)
                    quadraticTo((0.3f + tip.x) / 2f - 0.1f, (0.12f + tip.y) / 2f - 0.05f, 0.18f, 0.0f)
                    close()
                }
                drawPath(feather, colour)
            }
            // The legs and the twig it's perched on.
            drawLine(ink, Offset(-0.55f, 0.72f), Offset(0.55f, 0.72f), strokeWidth = 0.06f, cap = StrokeCap.Round)
            drawLine(red, Offset(-0.12f, 0.4f), Offset(-0.18f, 0.72f), strokeWidth = 0.05f, cap = StrokeCap.Round)
            drawLine(red, Offset(0.05f, 0.4f), Offset(0.02f, 0.72f), strokeWidth = 0.05f, cap = StrokeCap.Round)
            // The body, the breast, the wing.
            drawOval(green, topLeft = Offset(-0.5f, -0.2f), size = Size(0.95f, 0.65f))
            drawOval(red, topLeft = Offset(-0.5f, 0.0f), size = Size(0.5f, 0.42f))
            val wing = Path().apply {
                moveTo(-0.2f, -0.08f)
                quadraticTo(0.25f, -0.15f, 0.42f, 0.18f)
                quadraticTo(0.1f, 0.3f, -0.15f, 0.15f)
                close()
            }
            drawPath(wing, blue)
            // The head: a crest, an eye, a beak.
            drawCircle(green, 0.24f, Offset(-0.52f, -0.3f))
            val crest = Path().apply {
                moveTo(-0.62f, -0.48f)
                lineTo(-0.5f, -0.78f)
                lineTo(-0.42f, -0.5f)
                lineTo(-0.3f, -0.66f)
                lineTo(-0.32f, -0.4f)
                close()
            }
            drawPath(crest, red)
            drawCircle(face, 0.07f, Offset(-0.57f, -0.33f))
            drawCircle(ink, 0.04f, Offset(-0.58f, -0.33f))
            drawPath(polygonPath(listOf(Offset(-0.74f, -0.3f), Offset(-0.95f, -0.22f), Offset(-0.73f, -0.18f))), ink)
        }
    }
}

/** The 萬 suit's two characters for [value], laid out once at [MAHJONG_LAYOUT_PX], to be scaled to the die. */
@Composable
private fun rememberManzuLayouts(value: Int): Pair<TextLayoutResult, TextLayoutResult> {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    return remember(value, measurer, density) {
        val fontSize = with(density) { MAHJONG_LAYOUT_PX.toSp() }
        val style = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = fontSize, lineHeight = fontSize)
        measurer.measure(ChineseNumerals[value - 1], style) to measurer.measure(WAN, style)
    }
}
