package net.zodac.dicefive.ui.style

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.data.settings.DefaultStyleIds
import net.zodac.dicefive.ui.theme.TileIconColor
import net.zodac.dicefive.ui.theme.TileIndigoBorder
import net.zodac.dicefive.ui.theme.TileIndigoBottom
import net.zodac.dicefive.ui.theme.TileIndigoTop
import net.zodac.dicefive.ui.theme.TileScoredBorder
import net.zodac.dicefive.ui.theme.TileScoredBottom
import net.zodac.dicefive.ui.theme.TileScoredTop
import net.zodac.dicefive.ui.theme.TileSkyBorder
import net.zodac.dicefive.ui.theme.TileSkyBottom
import net.zodac.dicefive.ui.theme.TileSkyTop
import net.zodac.dicefive.ui.theme.TileTealBorder
import net.zodac.dicefive.ui.theme.TileTealBottom
import net.zodac.dicefive.ui.theme.TileTealTop

// The scorecard's boxes (CategoryTile). A style colours an open box, D20's two halves, and a scored box, and may give the
// marks a typeface and the box a texture and a shape. What a box means is never the style's: the gold of a good pick,
// the solid outline of the last box scored, and the dashed outline of a disabled box look the same in every style.

/** Which of a box style's fills a box takes: D20's numbers 1-10 and 11-20 each have their own, every other box the open one. */
enum class BoxTone { OPEN, FIRST_HALF, SECOND_HALF }

/** One fill of a box: a [top]-to-[bottom] gradient, its rim, and the [ink] its glyph is drawn in. */
data class BoxPaint(val top: Color, val bottom: Color, val border: Color, val ink: Color) {
    val fill: List<Color> get() = listOf(top, bottom)
}

/**
 * The look of the scorecard's boxes: each [BoxTone]'s fill, and a scored box's - which must read as spent beside the
 * open ones. A box that's a good pick is drawn gold over any style.
 */
interface ScoreBoxStyle : TableArt {
    fun paint(tone: BoxTone): BoxPaint

    /** A box that's been scored, in any half. Its glyph is drawn in its [BoxPaint.ink], faded. */
    val scored: BoxPaint

    /** The typeface of the boxes' text marks (3x, ?, D20's numbers) - null for the brand font. */
    val markFont: FontFamily? get() = null

    /** How thick a box's rim is - none at zero. */
    val rimWidth: Dp get() = 1.dp

    /** The outline of a box whose corners round at [corner]. */
    fun shape(corner: Dp): Shape = RoundedCornerShape(corner)

    /**
     * A pattern over the box's fill and under its glyph, in [paint]'s colours, for a box this DrawScope's size whose corners
     * round at [cornerPx]. [seed] differs from box to box (a grain or vein that isn't the same on every box), and [lit] is
     * false for a scored box. Only a few strokes: every box on the board draws it. Nothing by default.
     */
    fun DrawScope.drawTexture(paint: BoxPaint, seed: Int, lit: Boolean, cornerPx: Float) {}

    /** Whether an open box has [drawLights] that change with time (RGB): drawn on the art clock, apart from the rest of the box. */
    val animated: Boolean get() = false

    /**
     * An open box's lights at [seconds] on the art clock (`rememberArtSeconds`), over its fill and under its glyph: for an
     * [animated] style. [tone] is which of D20's halves it's in, and [seed] differs from box to box, as in [drawTexture].
     */
    fun DrawScope.drawLights(paint: BoxPaint, tone: BoxTone, seed: Int, seconds: Float, cornerPx: Float) {}

    /** Whether an open box has a [drawGlow] that pulses on the art clock (Neon): drawn once, then only faded. */
    val pulses: Boolean get() = false

    /** An open box's glow at [alpha], over its fill: for a style that [pulses]. Its parts may overlap only faintly, as each is faded on its own. */
    fun DrawScope.drawGlow(paint: BoxPaint, cornerPx: Float, alpha: Float) {}

    /** How strongly a box's [drawGlow] shows at [seconds] on the art clock, [seed] setting each box on its own beat. */
    fun glowAlpha(seconds: Float, seed: Int): Float = 1f
}

/** A pattern a box style draws over its fill - see [ScoreBoxStyle.drawTexture]. */
private fun interface BoxTexture {
    fun DrawScope.draw(paint: BoxPaint, seed: Int, lit: Boolean, cornerPx: Float)
}

private class DrawnScoreBox(
    override val id: String,
    private val open: BoxPaint,
    private val firstHalf: BoxPaint,
    private val secondHalf: BoxPaint,
    override val scored: BoxPaint,
    override val markFont: FontFamily? = null,
    override val rimWidth: Dp = 1.dp,
    private val pixelCorners: Boolean = false,
    private val texture: BoxTexture? = null,
) : ScoreBoxStyle {
    override fun paint(tone: BoxTone): BoxPaint = when (tone) {
        BoxTone.OPEN -> open
        BoxTone.FIRST_HALF -> firstHalf
        BoxTone.SECOND_HALF -> secondHalf
    }

    override fun shape(corner: Dp): Shape = if (pixelCorners) PixelCornerShape else RoundedCornerShape(corner)

    override fun DrawScope.drawTexture(paint: BoxPaint, seed: Int, lit: Boolean, cornerPx: Float) {
        texture?.run { draw(paint, seed, lit, cornerPx) }
    }
}

/** The swatch a box style's Styles dot shows: its open fill's top. */
internal val ScoreBoxStyle.swatch: Color get() = paint(BoxTone.OPEN).top

/** A box with its corners cut away in two square steps, like an 8-bit sprite's. */
private object PixelCornerShape : Shape {
    private val STEP = 3.dp

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { STEP.toPx() }
        val (w, h) = size
        return Outline.Generic(
            Path().apply {
                moveTo(2 * s, 0f)
                lineTo(w - 2 * s, 0f)
                lineTo(w - 2 * s, s)
                lineTo(w - s, s)
                lineTo(w - s, 2 * s)
                lineTo(w, 2 * s)
                lineTo(w, h - 2 * s)
                lineTo(w - s, h - 2 * s)
                lineTo(w - s, h - s)
                lineTo(w - 2 * s, h - s)
                lineTo(w - 2 * s, h)
                lineTo(2 * s, h)
                lineTo(2 * s, h - s)
                lineTo(s, h - s)
                lineTo(s, h - 2 * s)
                lineTo(0f, h - 2 * s)
                lineTo(0f, 2 * s)
                lineTo(s, 2 * s)
                lineTo(s, s)
                lineTo(2 * s, s)
                close()
            },
        )
    }
}

/**
 * RGB: a box ringed by a lit strip, on the RGB dice's clock ([RGB_CYCLE_SECONDS]) so a table of them changes colour
 * together. Rainbow lights every box one colour at a time; a [wave] runs the colours down the card, box by box. D20's
 * second half is lit half a turn round the hues from the first, so the halves are always opposite colours. Scored, the
 * strip goes out, as Neon's tube does.
 */
private class RgbScoreBox(
    override val id: String,
    private val open: BoxPaint,
    private val firstHalf: BoxPaint,
    private val secondHalf: BoxPaint,
    override val scored: BoxPaint,
    private val wave: Boolean,
) : ScoreBoxStyle {
    override val rimWidth: Dp get() = 0.dp
    override val animated: Boolean get() = true

    override fun paint(tone: BoxTone): BoxPaint = when (tone) {
        BoxTone.OPEN -> open
        BoxTone.FIRST_HALF -> firstHalf
        BoxTone.SECOND_HALF -> secondHalf
    }

    override fun DrawScope.drawTexture(paint: BoxPaint, seed: Int, lit: Boolean, cornerPx: Float) {
        if (!lit) with(neonTube) { draw(paint, seed, false, cornerPx) }
    }

    override fun DrawScope.drawLights(paint: BoxPaint, tone: BoxTone, seed: Int, seconds: Float, cornerPx: Float) {
        val half = if (tone == BoxTone.SECOND_HALF) 0.5f else 0f
        val colour = rgbColour(seconds / RGB_CYCLE_SECONDS + half + if (wave) seed * RGB_WAVE_STEP else 0f)
        val inset = 4.dp.toPx()
        val topLeft = Offset(inset, inset)
        val strip = Size(size.width - 2 * inset, size.height - 2 * inset)
        val corner = CornerRadius((cornerPx - inset).coerceAtLeast(2.dp.toPx()))
        fun ring(width: Dp, ringColour: Color, alpha: Float) =
            drawRoundRect(ringColour, topLeft = topLeft, size = strip, cornerRadius = corner, alpha = alpha, style = Stroke(width.toPx()))
        ring(7.dp, colour, 0.12f)
        ring(4.dp, colour, 0.25f)
        ring(2.dp, colour, 1f)
        ring(0.7.dp, Color.White, 0.6f)
    }
}

/** How far round the hues a Wave's next box down the card is lit. */
private const val RGB_WAVE_STEP = 0.045f

// ---- Textures. Each a handful of strokes, in dp, scaled to the box.

/** Ruled paper: three feint lines across and, on a notepad, a margin line down the left. */
private fun ruled(margin: Color?) = BoxTexture { paint, _, lit, _ ->
    val alpha = if (lit) 1f else SCORED_TEXTURE_ALPHA
    val line = 1.dp.toPx()
    for (k in 1..3) {
        val y = size.height * k / 4
        drawLine(paint.border, Offset(0f, y), Offset(size.width, y), strokeWidth = line, alpha = 0.55f * alpha)
    }
    if (margin != null) {
        val x = size.width * 0.18f
        drawLine(margin, Offset(x, 0f), Offset(x, size.height), strokeWidth = line, alpha = 0.6f * alpha)
    }
}

/** Graph paper: a square grid of feint lines. */
private val graph = BoxTexture { paint, _, lit, _ ->
    val alpha = if (lit) 0.45f else 0.45f * SCORED_TEXTURE_ALPHA
    val cell = 8.dp.toPx()
    val line = 0.75.dp.toPx()
    var x = cell / 2
    while (x < size.width) {
        drawLine(paint.border, Offset(x, 0f), Offset(x, size.height), strokeWidth = line, alpha = alpha)
        x += cell
    }
    var y = cell / 2
    while (y < size.height) {
        drawLine(paint.border, Offset(0f, y), Offset(size.width, y), strokeWidth = line, alpha = alpha)
        y += cell
    }
}

/** Wood: gently waving grain lines across the box, a little different on each, and now and then a knot in a corner. */
private fun grain(dark: Color) = BoxTexture { _, seed, lit, _ ->
    val random = Random(seed * GRAIN_SEED)
    val alpha = if (lit) 0.32f else 0.32f * SCORED_TEXTURE_ALPHA
    val stroke = Stroke(width = 0.9.dp.toPx())
    val lines = 6
    for (i in 0 until lines) {
        val y = size.height * (i + 0.5f) / lines + (random.nextFloat() - 0.5f) * size.height * 0.06f
        val sway = (random.nextFloat() - 0.5f) * size.height * 0.16f
        val path = Path().apply {
            moveTo(0f, y)
            cubicTo(size.width * 0.33f, y + sway, size.width * 0.66f, y - sway, size.width, y + sway * 0.4f)
        }
        drawPath(path, dark, alpha = alpha, style = stroke)
    }
    if (seed % 3 == 0) {
        // In a corner, clear of the glyph.
        val corner = random.nextInt(4)
        val centre = Offset(size.width * (if (corner % 2 == 0) 0.2f else 0.8f), size.height * (if (corner < 2) 0.2f else 0.8f))
        val radius = size.minDimension * 0.07f
        drawOval(dark, topLeft = centre - Offset(radius * 1.6f, radius), size = Size(radius * 3.2f, radius * 2), alpha = alpha * 1.4f, style = stroke)
    }
}

/** A chalkboard: faint streaks of rubbed-out chalk, different on each box, and a chalk line round the edge. */
private val chalkDust = BoxTexture { paint, seed, lit, _ ->
    val random = Random(seed * CHALK_SEED)
    val alpha = if (lit) 1f else SCORED_TEXTURE_ALPHA
    repeat(3) {
        val w = size.width * (0.45f + random.nextFloat() * 0.4f)
        val h = size.height * (0.08f + random.nextFloat() * 0.08f)
        val x = random.nextFloat() * (size.width - w * 0.5f) - w * 0.25f
        val y = random.nextFloat() * (size.height - h)
        drawOval(paint.ink, topLeft = Offset(x, y), size = Size(w, h), alpha = 0.05f * alpha)
    }
    // A chalk line just inside the edge.
    val inset = 2.5.dp.toPx()
    drawRoundRect(
        paint.ink,
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius(4.dp.toPx()),
        alpha = 0.22f * alpha,
        style = Stroke(1.dp.toPx()),
    )
}

/** A cut gem: a lit facet over the top left, a shaded one under the bottom right, and a bevel. No glint: it read as a plus. */
private val facets = BoxTexture { _, _, lit, cornerPx ->
    val (w, h) = size
    val alpha = if (lit) 1f else SCORED_TEXTURE_ALPHA
    drawPath(Path().apply { moveTo(0f, 0f); lineTo(w * 0.85f, 0f); lineTo(0f, h * 0.85f); close() }, Color.White, alpha = 0.13f * alpha)
    drawPath(Path().apply { moveTo(w, h); lineTo(w * 0.35f, h); lineTo(w, h * 0.35f); close() }, Color.Black, alpha = 0.2f * alpha)
    val inset = 3.dp.toPx()
    drawRoundRect(
        Color.White,
        topLeft = Offset(inset, inset),
        size = Size(w - 2 * inset, h - 2 * inset),
        cornerRadius = CornerRadius((cornerPx - inset).coerceAtLeast(0f)),
        alpha = 0.22f * alpha,
        style = Stroke(1.dp.toPx()),
    )
}

/** Marble: two wandering veins in [vein], different on each box. */
private fun veins(vein: Color) = BoxTexture { _, seed, lit, _ ->
    val random = Random(seed * VEIN_SEED)
    val alpha = if (lit) 0.5f else 0.5f * SCORED_TEXTURE_ALPHA
    repeat(2) { index ->
        val path = Path()
        var x = 0f
        var y = size.height * (0.2f + random.nextFloat() * 0.6f)
        path.moveTo(x, y)
        val steps = 6
        repeat(steps) {
            x += size.width / steps
            y = (y + (random.nextFloat() - 0.45f) * size.height * 0.3f).coerceIn(0f, size.height)
            path.lineTo(x, y)
        }
        drawPath(path, vein, alpha = alpha, style = Stroke(width = (if (index == 0) 1.1.dp else 0.6.dp).toPx()))
    }
}

/** Granite: a scatter of light and dark flecks, different on each box. */
private fun flecks(light: Color, dark: Color) = BoxTexture { _, seed, lit, _ ->
    val random = Random(seed * FLECK_SEED)
    val alpha = if (lit) 0.6f else 0.6f * SCORED_TEXTURE_ALPHA
    repeat(FLECKS) { i ->
        val radius = (0.5f + random.nextFloat() * 0.7f) * density
        drawCircle(if (i % 3 == 0) light else dark, radius, Offset(random.nextFloat() * size.width, random.nextFloat() * size.height), alpha = alpha)
    }
}

/**
 * An 8-bit button: a light edge along the top and left, a dark one along the bottom and right, inside the stepped corners -
 * and on an arcade screen, [scanlines].
 */
private fun pixelBevel(scanlines: Boolean) = BoxTexture { _, _, lit, _ ->
    val step = 3.dp.toPx()
    val (w, h) = size
    val alpha = if (lit) 1f else SCORED_TEXTURE_ALPHA
    drawRect(Color.White, topLeft = Offset(2 * step, step), size = Size(w - 4 * step, step), alpha = 0.3f * alpha)
    drawRect(Color.White, topLeft = Offset(step, 2 * step), size = Size(step, h - 4 * step), alpha = 0.3f * alpha)
    drawRect(Color.Black, topLeft = Offset(2 * step, h - 2 * step), size = Size(w - 4 * step, step), alpha = 0.25f * alpha)
    drawRect(Color.Black, topLeft = Offset(w - 2 * step, 2 * step), size = Size(step, h - 4 * step), alpha = 0.25f * alpha)
    if (scanlines) {
        var y = 0f
        val gap = 3.dp.toPx()
        while (y < h) {
            drawLine(Color.Black, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(), alpha = 0.25f * alpha)
            y += gap
        }
    }
}

/** Draws a ring [width] wide round a neon tube's path, just inside the box's edge. */
private fun DrawScope.tubeRing(cornerPx: Float, width: Dp, colour: Color, alpha: Float) {
    val inset = 4.dp.toPx()
    drawRoundRect(
        colour,
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius((cornerPx - inset).coerceAtLeast(2.dp.toPx())),
        alpha = alpha,
        style = Stroke(width.toPx()),
    )
}

/**
 * A neon tube's steady core just inside the box's edge, in [BoxPaint.border] - its glow is [drawNeonGlow], which pulses:
 * unlit, a dull grey tube, once scored.
 */
private val neonTube = BoxTexture { paint, _, lit, cornerPx ->
    if (lit) {
        tubeRing(cornerPx, 2.dp, paint.border, 1f)
        tubeRing(cornerPx, 0.7.dp, Color.White, 0.65f)
    } else {
        tubeRing(cornerPx, 1.5.dp, paint.border, 1f)
    }
}

/** A lit neon tube's glow round its core, at [alpha]. */
private fun DrawScope.drawNeonGlow(paint: BoxPaint, cornerPx: Float, alpha: Float) {
    tubeRing(cornerPx, 7.dp, paint.border, 0.12f * alpha)
    tubeRing(cornerPx, 4.dp, paint.border, 0.25f * alpha)
}

/**
 * Neon: a lit tube round a dark box, its glow pulsing gently on the Neon dice's beat ([NEON_PULSE_SECONDS]), each box on
 * its own, and steady below the High animation level. Scored, the tube goes out.
 */
private class NeonScoreBox(
    override val id: String,
    private val open: BoxPaint,
    private val secondHalf: BoxPaint,
    override val scored: BoxPaint,
) : ScoreBoxStyle {
    // No rim of its own: the tube is the edge.
    override val rimWidth: Dp get() = 0.dp
    override val pulses: Boolean get() = true

    override fun paint(tone: BoxTone): BoxPaint = if (tone == BoxTone.SECOND_HALF) secondHalf else open

    override fun DrawScope.drawTexture(paint: BoxPaint, seed: Int, lit: Boolean, cornerPx: Float) {
        with(neonTube) { draw(paint, seed, lit, cornerPx) }
    }

    override fun DrawScope.drawGlow(paint: BoxPaint, cornerPx: Float, alpha: Float) = drawNeonGlow(paint, cornerPx, alpha)

    override fun glowAlpha(seconds: Float, seed: Int): Float {
        val swell = 0.5f + 0.5f * sin(2f * PI.toFloat() * seconds / NEON_PULSE_SECONDS + seed * NEON_BOX_BEAT)
        return NEON_GLOW_FLOOR + (1f - NEON_GLOW_FLOOR) * swell
    }
}

/** How dim a neon box's glow goes at the bottom of its pulse, and how far apart neighbouring boxes' beats are (radians). */
private const val NEON_GLOW_FLOOR = 0.4f
private const val NEON_BOX_BEAT = 1.7f

private const val SCORED_TEXTURE_ALPHA = 0.45f
private const val FLECKS = 36
private const val GRAIN_SEED = 7919
private const val CHALK_SEED = 104_729
private const val VEIN_SEED = 15_485_863
private const val FLECK_SEED = 32_452_843

// ---- The styles, family by family in the registry's order.

private fun paint(top: Long, bottom: Long, border: Long, ink: Long) = BoxPaint(Color(top), Color(bottom), Color(border), Color(ink))

/** Every box style's art, by name - see `ScoreBoxes`, which joins them to the registry. */
internal object ScoreBoxArt {
    // Classic: the board as it's always been - teal, and D20's sky blue and indigo.
    val classicTeal: ScoreBoxStyle = DrawnScoreBox(
        DefaultStyleIds.SCORE_BOX,
        open = BoxPaint(TileTealTop, TileTealBottom, TileTealBorder, TileIconColor),
        firstHalf = BoxPaint(TileSkyTop, TileSkyBottom, TileSkyBorder, TileIconColor),
        secondHalf = BoxPaint(TileIndigoTop, TileIndigoBottom, TileIndigoBorder, TileIconColor),
        scored = BoxPaint(TileScoredTop, TileScoredBottom, TileScoredBorder, TileIconColor),
    )

    // Paper: a scorepad. Pencil-dark ink, and grey once scored, like a crossed-off line.
    private val paperScored = paint(0xFF8C887E, 0xFF6F6B62, 0xFF5B5850, 0xFF26251F)

    val paperNotepad: ScoreBoxStyle = DrawnScoreBox(
        "paper_notepad",
        open = paint(0xFFFBF3B4, 0xFFF0E188, 0xFF8FB0D0, 0xFF26304A),
        firstHalf = paint(0xFFDCEBF8, 0xFFC4D9EE, 0xFF7FA3C8, 0xFF1F2E48),
        secondHalf = paint(0xFFF9DDE4, 0xFFEDC4CF, 0xFFC28E9C, 0xFF4A2230),
        scored = paperScored,
        markFont = FontFamily.Serif,
        texture = ruled(margin = Color(0xFFD9534F)),
    )

    val paperGraph: ScoreBoxStyle = DrawnScoreBox(
        "paper_graph",
        open = paint(0xFFF8FAFB, 0xFFE4EBEF, 0xFF8FB6D2, 0xFF1E3550),
        firstHalf = paint(0xFFDDF4E7, 0xFFC2E5D0, 0xFF7FB898, 0xFF1C4030),
        secondHalf = paint(0xFFEBE1F7, 0xFFD6C7EC, 0xFFA48CC8, 0xFF392552),
        scored = paperScored,
        markFont = FontFamily.Serif,
        texture = graph,
    )

    // Wood: grained boards, light ink on dark wood and dark on light. D20's halves are a light and a dark wood.
    private val woodScored = paint(0xFF5E554C, 0xFF403A33, 0xFF6F665C, 0xFFE8DED0)
    private val oak = paint(0xFFB27C47, 0xFF8A5A2E, 0xFFD39C62, 0xFFFFF3DE)
    private val walnut = paint(0xFF6E462A, 0xFF46291A, 0xFF936343, 0xFFF3E2C8)
    private val maple = paint(0xFFE8C892, 0xFFCFA66B, 0xFFF4DBB0, 0xFF4A2E14)
    private val cherry = paint(0xFFA44B30, 0xFF74301E, 0xFFCC7656, 0xFFFFE9D6)
    private val ebony = paint(0xFF3B2C24, 0xFF1D1511, 0xFF5E4A3E, 0xFFEED9B8)
    private val woodGrain = grain(Color(0xFF2A160A))

    val woodOak: ScoreBoxStyle = DrawnScoreBox("wood_oak", oak, maple, cherry, woodScored, texture = woodGrain)
    val woodWalnut: ScoreBoxStyle = DrawnScoreBox("wood_walnut", walnut, oak, ebony, woodScored, texture = woodGrain)
    val woodMaple: ScoreBoxStyle = DrawnScoreBox("wood_maple", maple, maple, walnut, woodScored, texture = woodGrain)

    // Chalkboard: chalk written on a board, in handwriting. D20's halves are two boards, in two chalks.
    private val chalkScored = paint(0xFF4A4A47, 0xFF333331, 0xFF5E5E59, 0xFFDADAD0)
    private val blackBoard = paint(0xFF34393B, 0xFF222628, 0xFF6B7270, 0xFFF2F2EA)
    private val greenBoard = paint(0xFF2F5442, 0xFF1D382B, 0xFF5E8270, 0xFFF4F4EC)

    val chalkboardBlack: ScoreBoxStyle = DrawnScoreBox(
        "chalkboard_black",
        open = blackBoard,
        firstHalf = greenBoard,
        secondHalf = blackBoard.copy(ink = Color(0xFFF7E58A)),
        scored = chalkScored,
        markFont = FontFamily.Cursive,
        texture = chalkDust,
    )

    val chalkboardGreen: ScoreBoxStyle = DrawnScoreBox(
        "chalkboard_green",
        open = greenBoard,
        firstHalf = greenBoard.copy(ink = Color(0xFFF6C6D8)),
        secondHalf = paint(0xFF4B3029, 0xFF30201B, 0xFF7A5A50, 0xFFF4F4EC),
        scored = chalkScored,
        markFont = FontFamily.Cursive,
        texture = chalkDust,
    )

    // Gems: cut stones with a glint. D20's halves are two stones of contrasting colours.
    private val gemScored = paint(0xFF3A3540, 0xFF24212A, 0xFF4E4856, 0xFFDDDDDD)
    private val ruby = paint(0xFFC4283F, 0xFF6E0F20, 0xFFF07A8C, 0xFFFFF0F2)
    private val sapphire = paint(0xFF2C52C4, 0xFF13246E, 0xFF7A9BF0, 0xFFEEF3FF)
    private val emerald = paint(0xFF1F9A5A, 0xFF0C5130, 0xFF6FE0A2, 0xFFEFFFF5)
    private val amethyst = paint(0xFF8B40C2, 0xFF4A1A72, 0xFFC99AF0, 0xFFF8F0FF)
    private val aquamarine = paint(0xFF1C8C98, 0xFF0E525C, 0xFF86E6EF, 0xFFF0FFFF)

    val gemsRuby: ScoreBoxStyle = DrawnScoreBox("gems_ruby", ruby, ruby, sapphire, gemScored, texture = facets)
    val gemsSapphire: ScoreBoxStyle = DrawnScoreBox("gems_sapphire", sapphire, sapphire, emerald, gemScored, texture = facets)
    val gemsEmerald: ScoreBoxStyle = DrawnScoreBox("gems_emerald", emerald, emerald, amethyst, gemScored, texture = facets)
    val gemsAmethyst: ScoreBoxStyle = DrawnScoreBox("gems_amethyst", amethyst, amethyst, aquamarine, gemScored, texture = facets)

    // Stone: polished marble and flecked granite, with carved serif marks. D20's halves are a light and a dark stone.
    private val stoneScored = paint(0xFF4B4A48, 0xFF33322F, 0xFF5E5C59, 0xFFDDDDDD)
    private val whiteMarble = paint(0xFFF3F1ED, 0xFFD9D5CE, 0xFFB7B1A8, 0xFF2B2A28)
    private val blackMarble = paint(0xFF2C2C2E, 0xFF151516, 0xFF5A5A5E, 0xFFF3EBD8)
    private val greenMarble = paint(0xFF2F5A48, 0xFF1C3A2E, 0xFF5C8A76, 0xFFF3F3EC)
    private val greyGranite = paint(0xFF7C7975, 0xFF55524F, 0xFF9A9693, 0xFFF7F5F2)
    private val redGranite = paint(0xFF9A4B3E, 0xFF6A2E25, 0xFFBE7466, 0xFFFFF1EC)

    /** Marble's veins are grey on white and gold on black, so a half's veins follow its own stone. */
    private val marbleVeins = BoxTexture { paint, seed, lit, corner ->
        val vein = if (paint.ink.luminance() > 0.5f) Color(0xFFD8BE7A) else Color(0xFF8C877F)
        with(veins(vein)) { draw(paint, seed, lit, corner) }
    }

    val stoneWhiteMarble: ScoreBoxStyle = DrawnScoreBox("stone_white_marble", whiteMarble, whiteMarble, blackMarble, stoneScored, FontFamily.Serif, texture = marbleVeins)
    val stoneBlackMarble: ScoreBoxStyle = DrawnScoreBox("stone_black_marble", blackMarble, greenMarble, blackMarble, stoneScored, FontFamily.Serif, texture = marbleVeins)
    val stoneGranite: ScoreBoxStyle =
        DrawnScoreBox("stone_granite", greyGranite, greyGranite, redGranite, stoneScored, FontFamily.Serif, texture = flecks(Color(0xFFF2EEE8), Color(0xFF1E1C1A)))

    // Pixel: 8-bit buttons with stepped corners and a monospaced font. Retro is a handheld's greens; Arcade a dark screen.
    val pixelRetro: ScoreBoxStyle = DrawnScoreBox(
        "pixel_retro",
        open = paint(0xFF9BBC0F, 0xFF8BAC0F, 0xFF306230, 0xFF0F380F),
        firstHalf = paint(0xFFB4D23A, 0xFF9BBC0F, 0xFF306230, 0xFF0F380F),
        secondHalf = paint(0xFF306230, 0xFF204A20, 0xFF0F380F, 0xFFD2EA7A),
        scored = paint(0xFF5E6356, 0xFF474B40, 0xFF33362F, 0xFF1E2018),
        markFont = FontFamily.Monospace,
        rimWidth = 2.dp,
        pixelCorners = true,
        texture = pixelBevel(scanlines = false),
    )

    val pixelArcade: ScoreBoxStyle = DrawnScoreBox(
        "pixel_arcade",
        open = paint(0xFF1A1438, 0xFF0C0920, 0xFFFFD23F, 0xFFFFFFFF),
        firstHalf = paint(0xFF0E2E40, 0xFF071822, 0xFF3FE6FF, 0xFFBFF7FF),
        secondHalf = paint(0xFF40103A, 0xFF22081E, 0xFFFF4FD8, 0xFFFFD0F5),
        scored = paint(0xFF2A2A30, 0xFF1A1A1E, 0xFF4A4A55, 0xFFCCCCCC),
        markFont = FontFamily.Monospace,
        rimWidth = 2.dp,
        pixelCorners = true,
        texture = pixelBevel(scanlines = true),
    )

    // Neon: a lit tube round a dark box, the marks in the tube's colour - see NeonScoreBox.
    private val neonScored = paint(0xFF1E1E22, 0xFF141416, 0xFF4A4A50, 0xFF9A9AA0)
    private val pinkTube = paint(0xFF1C0C18, 0xFF0D050B, 0xFFFF4FC8, 0xFFFFA8E4)
    private val cyanTube = paint(0xFF08171B, 0xFF040B0D, 0xFF3FE6FF, 0xFFA8F4FF)
    private val limeTube = paint(0xFF0E1A08, 0xFF070D04, 0xFF7DFF4F, 0xFFCBFFAF)

    // RGB: a black or a white body, like the RGB dice, D20's halves a lighter and a darker one. The strip's grey once scored.
    private val rgbBlack = paint(0xFF1A1A1F, 0xFF0B0B0E, 0xFF3A3A42, 0xFFF4F4F8)
    private val rgbBlackLight = paint(0xFF34343C, 0xFF202026, 0xFF4A4A52, 0xFFF4F4F8)
    private val rgbWhite = paint(0xFFF6F6F8, 0xFFDCDCE2, 0xFFB8B8C0, 0xFF1A1A20)
    private val rgbWhiteDark = paint(0xFFCDCDD4, 0xFFB0B0B8, 0xFF9A9AA4, 0xFF1A1A20)
    private val rgbBlackScored = paint(0xFF4A4A4F, 0xFF353539, 0xFF5E5E64, 0xFFB0B0B6)
    private val rgbWhiteScored = paint(0xFF8E8E94, 0xFF74747A, 0xFF5E5E64, 0xFF26262B)

    val rgbRainbow: ScoreBoxStyle = RgbScoreBox("rgb_rainbow", rgbBlack, rgbBlackLight, rgbBlack, rgbBlackScored, wave = false)
    val rgbWave: ScoreBoxStyle = RgbScoreBox("rgb_wave", rgbBlack, rgbBlackLight, rgbBlack, rgbBlackScored, wave = true)
    val rgbWhiteRainbow: ScoreBoxStyle = RgbScoreBox("rgb_white_rainbow", rgbWhite, rgbWhite, rgbWhiteDark, rgbWhiteScored, wave = false)
    val rgbWhiteWave: ScoreBoxStyle = RgbScoreBox("rgb_white_wave", rgbWhite, rgbWhite, rgbWhiteDark, rgbWhiteScored, wave = true)

    val neonPink: ScoreBoxStyle = NeonScoreBox("neon_pink", pinkTube, cyanTube, neonScored)
    val neonCyan: ScoreBoxStyle = NeonScoreBox("neon_cyan", cyanTube, limeTube, neonScored)
}
