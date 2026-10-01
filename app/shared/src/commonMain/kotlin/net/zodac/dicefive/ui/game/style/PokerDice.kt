package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.mathjax_main_regular
import net.zodac.dicefive.ui.theme.GoldAccent
import org.jetbrains.compose.resources.Font

/** A playing card's suit, and the shape of its pip in a unit space two units tall round its centre. */
enum class Suit(val red: Boolean, build: () -> Path) {
    SPADES(false, ::spadePath),
    HEARTS(true, ::heartPath),
    CLUBS(false, ::clubPath),
    DIAMONDS(true, ::diamondPath),
    ;

    // Built the first time a die needs it, not when the class loads.
    val shape: Path by lazy(build)
}

/** Each value's card, 1 to 6: an Ace, then 2 to 6, each in the next suit round - spades, hearts, clubs, diamonds. */
private val PokerRanks = listOf("A", "2", "3", "4", "5", "6")
private val PokerSuits = listOf(Suit.SPADES, Suit.HEARTS, Suit.CLUBS, Suit.DIAMONDS, Suit.SPADES, Suit.HEARTS)

// The rank's index in the corners: its glyphs laid out at this size, then scaled to this height of the face.
private const val RANK_LAYOUT_PX = 100f
private const val RANK_HEIGHT = 0.2f
private const val INDEX_SUIT_RADIUS = 0.055f
private const val PIP_SUIT_RADIUS = 0.095f
private const val ACE_SUIT_RADIUS = 0.24f

/**
 * Playing-card dice: every face a card - an Ace for 1, then 2 to 6 - each in a different suit from
 * the one before (see [PokerSuits]), with its rank and suit in two opposite corners in a formal serif
 * (the bundled MathJax font, a Computer Modern) and its suit pips laid out the way a card's are, the
 * lower ones upside down. On a coloured roll every suit takes the roll's pip colour.
 */
class PokerDiceStyle(
    override val id: String,
    private val card: Color,
    private val cardShade: Color,
    private val red: Color = CardRed,
    private val black: Color = CardBlack,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = card

    override fun recoloured(palette: DieColourPalette): DiceStyle =
        PokerDiceStyle(id, palette.diceTop, palette.diceBottom, palette.pip, palette.pip, palette.heldRing)

    override val cornerPercent: Int = STYLED_DIE_CORNER_PERCENT

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val rank = rememberRankLayout(PokerRanks[value - 1])
        val suit = PokerSuits[value - 1]
        val ink = if (suit.red) red else black
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(card, cardShade)),
            edge = cardShade,
            pipColor = ink,
            pipShape = PipShape.CUSTOM,
            pipPadding = 0.dp,
            heldRingColor = heldRing,
            customPips = { drawCachedSurface(CardFace(value, ink, card)) { drawCard(value, suit, ink, rank) } },
        )
    }

    private data class CardFace(val value: Int, val ink: Color, val card: Color)

    private fun DrawScope.drawCard(value: Int, suit: Suit, ink: Color, rank: TextLayoutResult) {
        val m = size.minDimension
        // A fine frame just inside the edge, as a card's printed border.
        val inset = m * 0.045f
        drawRoundRect(
            ink.copy(alpha = 0.25f),
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(m * 0.14f),
            style = Stroke(width = m * 0.008f),
        )
        // The indices, top left and (turned round) bottom right.
        drawIndex(rank, suit, ink)
        withTransform({ rotate(180f, Offset(size.width / 2f, size.height / 2f)) }) { drawIndex(rank, suit, ink) }
        if (value == 1) {
            drawSuit(suit, Offset(size.width / 2f, size.height / 2f), m * ACE_SUIT_RADIUS, ink, upsideDown = false)
            // The Ace's flourish: a fine ring round its one great pip.
            drawCircle(ink.copy(alpha = 0.35f), m * 0.315f, Offset(size.width / 2f, size.height / 2f), style = Stroke(width = m * 0.008f))
            return
        }
        for (pip in CardPips.getValue(value)) {
            drawSuit(suit, Offset(pip.x * size.width, pip.y * size.height), m * PIP_SUIT_RADIUS, ink, upsideDown = pip.y > 0.55f)
        }
    }

    /** The rank and a small suit below it, in the top left corner. */
    private fun DrawScope.drawIndex(rank: TextLayoutResult, suit: Suit, ink: Color) {
        val m = size.minDimension
        val scale = m * RANK_HEIGHT / rank.size.height
        val centreX = m * 0.135f
        withTransform({
            translate(centreX - rank.size.width * scale / 2f, m * 0.055f)
            scale(scale, scale, Offset.Zero)
        }) { drawText(rank, ink) }
        drawSuit(suit, Offset(centreX, m * 0.06f + m * RANK_HEIGHT + m * INDEX_SUIT_RADIUS * 0.6f), m * INDEX_SUIT_RADIUS, ink, upsideDown = false)
    }
}

/** The rank [text] laid out once in the bundled serif at [RANK_LAYOUT_PX], to be scaled to the die. */
@Composable
private fun rememberRankLayout(text: String): TextLayoutResult {
    // Keyed on the font itself: where it loads asynchronously (iOS), it's laid out again once it arrives.
    val font = Font(Res.font.mathjax_main_regular)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    return remember(text, font, measurer, density) {
        val fontSize = with(density) { RANK_LAYOUT_PX.toSp() }
        measurer.measure(text, TextStyle(fontFamily = FontFamily(font), fontSize = fontSize, lineHeight = fontSize))
    }
}

/** Where the pips of a card of each value from 2 to 6 sit, as fractions of the face - a card's layout, squared up. */
private val CardPips: Map<Int, List<Offset>> = mapOf(
    2 to listOf(Offset(0.5f, 0.27f), Offset(0.5f, 0.73f)),
    3 to listOf(Offset(0.5f, 0.24f), Offset(0.5f, 0.5f), Offset(0.5f, 0.76f)),
    4 to listOf(Offset(0.35f, 0.28f), Offset(0.65f, 0.28f), Offset(0.35f, 0.72f), Offset(0.65f, 0.72f)),
    5 to listOf(Offset(0.35f, 0.26f), Offset(0.65f, 0.26f), Offset(0.5f, 0.5f), Offset(0.35f, 0.74f), Offset(0.65f, 0.74f)),
    6 to listOf(Offset(0.35f, 0.24f), Offset(0.65f, 0.24f), Offset(0.35f, 0.5f), Offset(0.65f, 0.5f), Offset(0.35f, 0.76f), Offset(0.65f, 0.76f)),
)

/** A [suit] pip [radius] tall either side of [centre], the way up a card prints it. */
private fun DrawScope.drawSuit(suit: Suit, centre: Offset, radius: Float, ink: Color, upsideDown: Boolean) {
    inUnit(centre, radius, if (upsideDown) 180f else 0f) { drawPath(suit.shape, ink) }
}

private fun heartPath(): Path = Path().apply {
    moveTo(0f, 1f)
    cubicTo(-0.35f, 0.66f, -1f, 0.22f, -1f, -0.36f)
    cubicTo(-1f, -0.86f, -0.38f, -1.06f, 0f, -0.56f)
    cubicTo(0.38f, -1.06f, 1f, -0.86f, 1f, -0.36f)
    cubicTo(1f, 0.22f, 0.35f, 0.66f, 0f, 1f)
    close()
}

private fun diamondPath(): Path = Path().apply {
    moveTo(0f, -1f)
    quadraticTo(0.3f, -0.4f, 0.75f, 0f)
    quadraticTo(0.3f, 0.4f, 0f, 1f)
    quadraticTo(-0.3f, 0.4f, -0.75f, 0f)
    quadraticTo(-0.3f, -0.4f, 0f, -1f)
    close()
}

private fun spadePath(): Path = Path().apply {
    moveTo(0f, -1f)
    cubicTo(-0.35f, -0.62f, -1f, -0.24f, -1f, 0.24f)
    cubicTo(-1f, 0.7f, -0.42f, 0.84f, -0.08f, 0.46f)
    quadraticTo(-0.12f, 0.82f, -0.38f, 1f)
    lineTo(0.38f, 1f)
    quadraticTo(0.12f, 0.82f, 0.08f, 0.46f)
    cubicTo(0.42f, 0.84f, 1f, 0.7f, 1f, 0.24f)
    cubicTo(1f, -0.24f, 0.35f, -0.62f, 0f, -1f)
    close()
}

private fun clubPath(): Path {
    // Built as a union, so the overlapping lobes and stalk don't cancel each other out.
    val r = 0.42f
    val lobes = listOf(Offset(0f, -0.52f) to r, Offset(-0.5f, 0.1f) to r, Offset(0.5f, 0.1f) to r, Offset(0f, 0f) to 0.3f)
    val stalk = Path().apply {
        moveTo(-0.08f, 0.2f)
        quadraticTo(-0.12f, 0.8f, -0.38f, 1f)
        lineTo(0.38f, 1f)
        quadraticTo(0.12f, 0.8f, 0.08f, 0.2f)
        close()
    }
    return lobes.fold(stalk) { club, (centre, radius) -> Path.combine(PathOperation.Union, club, Path().apply { addOval(Rect(centre, radius)) }) }
}

private val CardRed = Color(0xFFC8102E)
private val CardBlack = Color(0xFF16161A)
