package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
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
import kotlin.math.sqrt
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

/** A way of keeping a tally, from somewhere in the world. Each counts in fives. */
enum class TallySystem {
    /** The five-bar gate: four uprights struck through by a fifth - Europe, North America, Australia. */
    GATE,

    /** The character 正, five strokes to complete it - China, Japan, Korea. */
    ZHENG,

    /** A square drawn side by side, closed with a diagonal - Latin America and France. */
    SQUARE,

    /** The dot tally: four dots at a square's corners, then lines joining them - foresters and surveyors. */
    DOTS,
}

/** What a tally is written with, and so how a stroke looks. */
enum class TallyPen {
    /** Chalk on slate: broad, broken, a little powdery. */
    CHALK,

    /** An ink brush: thick, tapering at the end of each stroke. */
    BRUSH,

    /** A pencil: thin and grey, gone over twice. */
    PENCIL,

    /** A ballpoint: thin, even and dark. */
    PEN,
}

/** The surface a tally is kept on, under its marks. */
enum class TallySurface { SLATE, RICE_PAPER, NOTEBOOK, FIELD_BOOK }

/**
 * Tally-mark dice: every face counts its value the way [system] keeps a tally, written by hand with
 * [pen] on [surface] - so each stroke wobbles a little and no two dice are written quite alike. On
 * a coloured roll the surface takes the roll's face colours and the marks its pip colour.
 */
class TallyDiceStyle(
    override val id: String,
    private val system: TallySystem,
    private val pen: TallyPen,
    private val surface: TallySurface,
    private val paper: Color,
    private val paperShade: Color,
    private val ink: Color,
    private val rule: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = paper

    override fun recoloured(palette: DieColourPalette): DiceStyle = TallyDiceStyle(
        id, system, pen, surface, palette.diceTop, palette.diceBottom, palette.pip, lerp(palette.diceBottom, palette.pip, 0.3f), palette.heldRing,
    )

    override val cornerPercent: Int = 12

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val pattern = naturalPatternSeed(value)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(paper, paperShade)),
            edge = paperShade,
            pipColor = ink,
            cornerPercent = cornerPercent,
            pipShape = PipShape.CUSTOM,
            pipPadding = 0.dp,
            heldRingColor = heldRing,
            customPips = { drawCachedSurface(Tally(value, ink, pattern)) { drawTally(value, pattern) } },
        ) {
            drawCachedSurface(TallyPaper(paper, paperShade, rule, pattern)) { paintSurface(pattern) }
        }
    }

    private data class Tally(val value: Int, val ink: Color, val pattern: Int)

    private data class TallyPaper(val paper: Color, val shade: Color, val rule: Color, val pattern: Int)

    private fun DrawScope.paintSurface(pattern: Int) {
        val random = Random(pattern * 3 + 41)
        val w = size.width
        val h = size.height
        val m = size.minDimension
        when (surface) {
            TallySurface.SLATE -> {
                // Old chalk wiped off in broad smudges.
                repeat(6) {
                    val at = Offset(random.nextFloat() * w, random.nextFloat() * h)
                    val r = m * (0.15f + random.nextFloat() * 0.3f)
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.09f), Color.Transparent), at, r), r, at)
                }
                repeat(4) {
                    val y = random.nextFloat() * h
                    drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(w, y + (random.nextFloat() - 0.5f) * h * 0.4f), strokeWidth = m * 0.08f)
                }
            }
            TallySurface.RICE_PAPER -> {
                // Long fibres laid every which way.
                repeat(60) {
                    val at = Offset(random.nextFloat() * w, random.nextFloat() * h)
                    val d = Offset(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f) * (m * 0.18f)
                    drawLine(rule.copy(alpha = 0.18f), at, at + d, strokeWidth = m * 0.005f)
                }
            }
            TallySurface.NOTEBOOK -> {
                // Ruled lines and a margin.
                var y = h * 0.14f
                while (y < h) {
                    drawLine(rule.copy(alpha = 0.55f), Offset(0f, y), Offset(w, y), strokeWidth = m * 0.008f)
                    y += h * 0.15f
                }
                drawLine(Color(0xFFE57373).copy(alpha = 0.7f), Offset(w * 0.1f, 0f), Offset(w * 0.1f, h), strokeWidth = m * 0.01f)
            }
            TallySurface.FIELD_BOOK -> {
                // A fine survey grid.
                for (k in 1 until 10) {
                    val x = w * k / 10f
                    val y = h * k / 10f
                    drawLine(rule.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, h), strokeWidth = m * 0.005f)
                    drawLine(rule.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y), strokeWidth = m * 0.005f)
                }
            }
        }
    }

    private fun DrawScope.drawTally(value: Int, pattern: Int) {
        val random = Random(pattern * 11 + 5)
        val (strokes, dots) = tallyMarks(system, value)
        val m = size.minDimension
        // Every mark drifts a little off true, as written by hand.
        fun hand(p: Offset) = Offset((p.x + (random.nextFloat() - 0.5f) * 0.025f) * size.width, (p.y + (random.nextFloat() - 0.5f) * 0.025f) * size.height)
        for (dot in dots) drawMarkDot(hand(dot), m, random)
        for ((from, to) in strokes) drawMarkStroke(hand(from), hand(to), m, random)
    }

    private fun DrawScope.drawMarkDot(at: Offset, m: Float, random: Random) {
        val r = m * when (pen) {
            TallyPen.CHALK -> 0.045f
            TallyPen.BRUSH -> 0.05f
            TallyPen.PENCIL, TallyPen.PEN -> 0.035f
        }
        drawCircle(ink, r * (0.9f + random.nextFloat() * 0.2f), at)
    }

    /** One stroke from [from] to [to], bowed a little, in the style of [pen]. */
    private fun DrawScope.drawMarkStroke(from: Offset, to: Offset, m: Float, random: Random) {
        val along = to - from
        val length = sqrt(along.x * along.x + along.y * along.y).coerceAtLeast(0.001f)
        val normal = Offset(-along.y / length, along.x / length)
        val bow = normal * ((random.nextFloat() - 0.5f) * length * 0.08f)
        fun curve(shift: Offset = Offset.Zero) = Path().apply {
            moveTo(from.x + shift.x, from.y + shift.y)
            val mid = (from + to) / 2f + bow + shift
            quadraticTo(mid.x, mid.y, to.x + shift.x, to.y + shift.y)
        }
        when (pen) {
            TallyPen.CHALK -> {
                // Several faint passes side by side, so the stroke is broad and broken.
                for (k in -2..2) {
                    val shift = normal * (k * m * 0.011f) + Offset((random.nextFloat() - 0.5f) * m * 0.01f, 0f)
                    drawPath(curve(shift), ink.copy(alpha = 0.45f + random.nextFloat() * 0.35f), style = Stroke(width = m * 0.018f, cap = StrokeCap.Round))
                }
            }
            TallyPen.BRUSH -> {
                // Pressed down hard at the start, lifting away to a point at the end.
                val start = m * 0.075f
                val steps = 14
                for (i in 0 until steps) {
                    val t0 = i / steps.toFloat()
                    val t1 = (i + 1) / steps.toFloat()
                    fun point(t: Float) = from * ((1 - t) * (1 - t)) + ((from + to) / 2f + bow) * (2 * t * (1 - t)) + to * (t * t)
                    val width = start * (1f - 0.75f * t1 * t1) * if (i == 0) 1.15f else 1f
                    drawLine(ink, point(t0), point(t1), strokeWidth = width, cap = StrokeCap.Round)
                }
            }
            TallyPen.PENCIL -> {
                drawPath(curve(), ink.copy(alpha = 0.75f), style = Stroke(width = m * 0.028f, cap = StrokeCap.Round))
                drawPath(curve(normal * (m * 0.008f)), ink.copy(alpha = 0.45f), style = Stroke(width = m * 0.018f, cap = StrokeCap.Round))
            }
            TallyPen.PEN -> drawPath(curve(), ink, style = Stroke(width = m * 0.03f, cap = StrokeCap.Round))
        }
    }
}

/** The strokes (from, to) and dots that tally [value] in [system], as fractions of the face. */
internal fun tallyMarks(system: TallySystem, value: Int): Pair<List<Pair<Offset, Offset>>, List<Offset>> = when (system) {
    TallySystem.GATE -> {
        // Uprights 0.13 apart, centred - and on a 6, the finished gate to the left and one more beside it.
        val gate = value >= 5
        val uprights = if (gate) 4 else value
        val left = if (value == 6) 0.17f else 0.5f - (uprights - 1) * 0.13f / 2f
        val strokes = List(uprights) { i -> Offset(left + i * 0.13f, 0.24f) to Offset(left + i * 0.13f - 0.02f, 0.76f) }.toMutableList()
        if (gate) strokes += Offset(left - 0.09f, 0.66f) to Offset(left + 3 * 0.13f + 0.08f, 0.32f)
        if (value == 6) strokes += Offset(0.83f, 0.24f) to Offset(0.81f, 0.76f)
        strokes to emptyList()
    }
    TallySystem.ZHENG -> {
        // 正, stroke by stroke: the top, the upright, the short bar on the right, the short upright
        // on the left, the base - and on a 6, the first stroke of the next.
        val zheng = listOf(
            Offset(0.1f, 0.1f) to Offset(0.9f, 0.1f),
            Offset(0.5f, 0.1f) to Offset(0.5f, 0.9f),
            Offset(0.5f, 0.5f) to Offset(0.84f, 0.5f),
            Offset(0.2f, 0.42f) to Offset(0.2f, 0.9f),
            Offset(0.02f, 0.92f) to Offset(0.98f, 0.92f),
        )
        fun placed(box: Offset, scale: Float, marks: List<Pair<Offset, Offset>>) = marks.map { (a, b) -> box + a * scale to box + b * scale }
        if (value <= 5) {
            placed(Offset(0.19f, 0.19f), 0.62f, zheng.take(value)) to emptyList()
        } else {
            (placed(Offset(0.07f, 0.25f), 0.5f, zheng) + placed(Offset(0.6f, 0.25f), 0.34f, zheng.take(1))) to emptyList()
        }
    }
    TallySystem.SQUARE -> {
        // Left, top, right, bottom, then the diagonal - and on a 6, the next square's first side.
        fun square(x0: Float, y0: Float, s: Float) = listOf(
            Offset(x0, y0) to Offset(x0, y0 + s),
            Offset(x0, y0) to Offset(x0 + s, y0),
            Offset(x0 + s, y0) to Offset(x0 + s, y0 + s),
            Offset(x0, y0 + s) to Offset(x0 + s, y0 + s),
            Offset(x0, y0) to Offset(x0 + s, y0 + s),
        )
        if (value <= 5) {
            square(0.26f, 0.26f, 0.48f).take(value) to emptyList()
        } else {
            (square(0.12f, 0.3f, 0.4f) + square(0.68f, 0.3f, 0.4f).take(1)) to emptyList()
        }
    }
    TallySystem.DOTS -> {
        // Dots at the corners first, then the sides joining them: the top, then the right.
        val corners = listOf(Offset(0.27f, 0.27f), Offset(0.73f, 0.27f), Offset(0.27f, 0.73f), Offset(0.73f, 0.73f))
        val sides = listOf(corners[0] to corners[1], corners[1] to corners[3])
        sides.take((value - 4).coerceAtLeast(0)) to corners.take(value.coerceAtMost(4))
    }
}
