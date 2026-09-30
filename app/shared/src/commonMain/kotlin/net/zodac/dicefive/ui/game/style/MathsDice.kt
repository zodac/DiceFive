package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import net.zodac.dicefive.resources.mathjax_math_italic
import net.zodac.dicefive.ui.theme.GoldAccent
import org.jetbrains.compose.resources.Font

/**
 * A glyph's advance and ink bounds, in ems (up is positive), read from the MathJax font's own
 * metrics - so a formula can be laid out and centred exactly as the font draws it, without
 * measuring ink at runtime.
 */
private class Glyph(val advance: Float, val left: Float, val bottom: Float, val right: Float, val top: Float)

// MathJax_Main Regular: the digits, the operators, the brackets and "ln".
private val MainGlyphs = mapOf(
    '0' to Glyph(0.5f, 0.039f, -0.022f, 0.46f, 0.666f),
    '2' to Glyph(0.5f, 0.05f, 0f, 0.449f, 0.666f),
    '3' to Glyph(0.5f, 0.042f, -0.022f, 0.457f, 0.665f),
    '5' to Glyph(0.5f, 0.05f, -0.022f, 0.449f, 0.666f),
    '!' to Glyph(0.278f, 0.078f, 0.001f, 0.199f, 0.716f),
    'l' to Glyph(0.278f, 0.026f, 0f, 0.263f, 0.694f),
    'n' to Glyph(0.556f, 0.025f, 0f, 0.542f, 0.442f),
    '⌊' to Glyph(0.444f, 0.174f, -0.25f, 0.422f, 0.75f),
    '⌋' to Glyph(0.444f, 0.021f, -0.25f, 0.269f, 0.75f),
    '√' to Glyph(0.833f, 0.072f, -0.2f, 0.853f, 0.8f),
)

// MathJax_Math Italic: the variable and the constants.
private val ItalicGlyphs = mapOf(
    'x' to Glyph(0.572f, 0.035f, -0.011f, 0.522f, 0.442f),
    'e' to Glyph(0.466f, 0.039f, -0.011f, 0.429f, 0.442f),
    'π' to Glyph(0.57f, 0.019f, -0.011f, 0.573f, 0.431f),
)

// TeX's layout constants for a formula in display style: how much smaller a superscript is set, and
// how far its baseline is raised; and the default rule thickness, which the root's bar is drawn in.
private const val SCRIPT_SCALE = 0.7f
private const val SUPERSCRIPT_RISE = 0.413f
private const val RULE_THICKNESS = 0.04f

// TeX's thin space, which it puts between an operator name like "ln" and what it's applied to.
private const val THIN_SPACE = 1f / 6f

/** A run of [text], its baseline at [x] ems along and [rise] ems up, set at [scale] of the formula's size. */
private data class Run(val text: String, val italic: Boolean, val x: Float, val rise: Float = 0f, val scale: Float = 1f) {
    private val glyphs = text.map { (if (italic) ItalicGlyphs else MainGlyphs).getValue(it) }
    private val advances = glyphs.runningFold(0f) { at, glyph -> at + glyph.advance }

    val end: Float = x + advances.last() * scale
    val left: Float = x + glyphs.first().left * scale
    val right: Float = x + (advances[glyphs.size - 1] + glyphs.last().right) * scale
    val top: Float = rise + glyphs.maxOf { it.top } * scale
    val bottom: Float = rise + glyphs.minOf { it.bottom } * scale
}

/**
 * One face's formula: its [runs] of text, and - for a root - the bar over the radicand, from
 * [barFrom] to [barTo] ems along with its top edge [barTop] ems up. Its ink box is kept, so the face
 * can centre what's actually drawn.
 */
private class Formula(val runs: List<Run>, val barFrom: Float = 0f, val barTo: Float = 0f, val barTop: Float = 0f) {
    private val hasBar = barTo > barFrom
    val left: Float = runs.minOf { it.left }
    val right: Float = if (hasBar) maxOf(runs.maxOf { it.right }, barTo) else runs.maxOf { it.right }
    val top: Float = if (hasBar) maxOf(runs.maxOf { it.top }, barTop) else runs.maxOf { it.top }
    val bottom: Float = runs.minOf { it.bottom }

    fun drawBar(scope: DrawScope, color: Color, emPx: Float) {
        if (!hasBar) return
        scope.drawRect(color, Offset(barFrom * emPx, -barTop * emPx), Size((barTo - barFrom) * emPx, RULE_THICKNESS * emPx))
    }
}

/** [parts] set one after another on the baseline, each as (text, italic) - the italic ones in the math italic font. */
private fun sequence(vararg parts: Pair<String, Boolean>): Formula {
    var x = 0f
    return Formula(parts.map { (text, italic) -> Run(text, italic, x).also { x = it.end } })
}

/**
 * [base] raised to the power [exponent], the exponent set as a superscript after it - after
 * [operator] (an operator name like "ln") and a thin space, when there is one.
 */
private fun power(base: String, italicBase: Boolean, exponent: String, operator: String? = null): Formula {
    val operatorRun = operator?.let { Run(it, italic = false, x = 0f) }
    val baseRun = Run(base, italicBase, operatorRun?.let { it.end + THIN_SPACE } ?: 0f)
    val exponentRun = Run(exponent, italic = false, x = baseRun.end, rise = SUPERSCRIPT_RISE, scale = SCRIPT_SCALE)
    return Formula(listOfNotNull(operatorRun, baseRun, exponentRun))
}

/**
 * The square root of [radicand]: the font's root sign, the radicand after it, and a bar of TeX's
 * rule thickness running on from the sign's top-right corner over the radicand - the way TeX builds
 * a root, since the sign alone has no bar.
 */
private fun root(radicand: String): Formula {
    val sign = MainGlyphs.getValue('√')
    val digits = Run(radicand, italic = false, x = sign.advance)
    return Formula(listOf(Run("√", italic = false, x = 0f), digits), barFrom = sign.advance, barTo = digits.end, barTop = sign.top)
}

/** Each face's formula, by value: x⁰, ln e², ⌊π⌋, 2², √25, 3!. */
private val Formulas: List<Formula> = listOf(
    power("x", italicBase = true, exponent = "0"),
    power("e", italicBase = true, exponent = "2", operator = "ln"),
    sequence("⌊" to false, "π" to true, "⌋" to false),
    power("2", italicBase = false, exponent = "2"),
    root("25"),
    sequence("3!" to false),
)

// How big one em of the formulas is, as a fraction of the die - one size for every face, set so the
// widest (√25, with ln e² a hair narrower) still clears the die's rounded corners.
private const val MATHS_EM_FRACTION = 0.4f

// The size the formulas' text is laid out at, in pixels to the em, before being scaled to the die.
// Laid out once at this size rather than per die size, so a tumbling die never lays out text again.
private const val LAYOUT_EM_PX = 100f

/**
 * Maths dice: every face a small formula that works out to its value - x⁰, ln e², ⌊π⌋, 2², √25 and 3! -
 * set in the MathJax TeX fonts, the Computer Modern faces Wikipedia's formulas are drawn in (see
 * `app/licensing/`), in [ink] on a face shading from [light] to [dark].
 */
class MathsDiceStyle(
    override val id: String,
    private val light: Color,
    private val dark: Color,
    private val ink: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = light

    override fun recoloured(palette: DieColourPalette): DiceStyle = MathsDiceStyle(id, palette.diceTop, palette.diceBottom, palette.pip, palette.heldRing)

    override val cornerPercent: Int = STYLED_DIE_CORNER_PERCENT

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val formula = Formulas[value - 1]
        val layouts = rememberFormulaLayouts(formula)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(light, dark)),
            edge = dark,
            pipColor = ink,
            pipShape = PipShape.CUSTOM,
            pipPadding = 0.dp,
            heldRingColor = heldRing,
            customPips = { drawFormula(formula, layouts, ink) },
        )
    }
}

/** Each of [formula]'s runs laid out in its font at [LAYOUT_EM_PX] - once per face, not per frame. */
@Composable
private fun rememberFormulaLayouts(formula: Formula): List<TextLayoutResult> {
    // Keyed on the fonts themselves: where a font resource loads asynchronously (iOS), the first
    // composition gets a stand-in, and the formula is laid out again once the real font arrives.
    val mainFont = Font(Res.font.mathjax_main_regular)
    val italicFont = Font(Res.font.mathjax_math_italic)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    return remember(formula, mainFont, italicFont, measurer, density) {
        val main = FontFamily(mainFont)
        val italic = FontFamily(italicFont)
        formula.runs.map { run ->
            val fontSize = with(density) { (LAYOUT_EM_PX * run.scale).toSp() }
            measurer.measure(run.text, TextStyle(fontFamily = if (run.italic) italic else main, fontSize = fontSize, lineHeight = fontSize))
        }
    }
}

/** [formula] in [color], scaled to the die and centred on its ink, each run placed on its own baseline. */
private fun DrawScope.drawFormula(formula: Formula, layouts: List<TextLayoutResult>, color: Color) {
    val scale = size.minDimension * MATHS_EM_FRACTION / LAYOUT_EM_PX
    // The formula's baseline origin, placed so its ink box sits in the middle of the face.
    val originX = size.width / 2f - (formula.left + formula.right) / 2f * LAYOUT_EM_PX * scale
    val originY = size.height / 2f + (formula.top + formula.bottom) / 2f * LAYOUT_EM_PX * scale
    withTransform({
        translate(originX, originY)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        formula.runs.forEachIndexed { i, run ->
            val layout = layouts[i]
            drawText(layout, color, topLeft = Offset(run.x * LAYOUT_EM_PX, -run.rise * LAYOUT_EM_PX - layout.firstBaseline))
        }
        formula.drawBar(this, color, LAYOUT_EM_PX)
    }
}
