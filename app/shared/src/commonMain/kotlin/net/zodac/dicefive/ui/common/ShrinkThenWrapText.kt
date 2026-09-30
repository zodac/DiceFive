package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** The smallest text the app shrinks to fit a line - past this it wraps instead of getting smaller. */
val MIN_READABLE_FONT_SIZE = 12.sp

/**
 * [text] on one line: at [style]'s size when it fits, otherwise stepped down by [fontStep] to
 * [minFontSize] (never below - text that small is hard to read, and shrinking to fit undoes the
 * player's own large-font choice), and only when even that doesn't fit, wrapped onto up to
 * [wrappedMaxLines] lines at [minFontSize], ellipsised past those.
 *
 * So a label that fits today looks exactly as it did; one that used to be shrunk below
 * [minFontSize] (or, at a large system font, cut off) wraps instead. The full text is always what a
 * screen reader gets.
 *
 * The size is chosen by measuring [text] against the width on offer, rather than with
 * `TextAutoSize`, because that can only shrink: it can't say "it still doesn't fit" and hand over
 * to wrapping. Not usable inside an intrinsic-size layout (it composes on the width it's given).
 */
@Composable
fun ShrinkThenWrapText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = MIN_READABLE_FONT_SIZE,
    fontStep: TextUnit = 0.5.sp,
    wrappedMaxLines: Int = 2,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    val measurer = rememberTextMeasurer()
    val base = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style
    BoxWithConstraints(modifier = modifier) {
        val available = constraints.maxWidth
        val fit = remember(text, base, minFontSize, fontStep, available) {
            if (available == Constraints.Infinity) {
                FontFit(base.fontSize, wraps = false)
            } else {
                fitFontSize(base.fontSize, minFontSize, fontStep) { size ->
                    measurer.measure(text = text, style = base.copy(fontSize = size), maxLines = 1, softWrap = false).size.width <= available
                }
            }
        }
        Text(
            text = text,
            color = color,
            style = base.copy(fontSize = fit.size),
            maxLines = if (fit.wraps) wrappedMaxLines else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A [fitFontSize] result: the size to draw at, and whether even the smallest didn't fit a line. */
internal data class FontFit(val size: TextUnit, val wraps: Boolean)

/**
 * The largest size from [max] down to [min] in [step]s at which [fitsOneLine] holds; if none does,
 * [min] with [FontFit.wraps] set. A [max] at or below [min] (or one that isn't a size at all) is
 * tried on its own.
 */
internal fun fitFontSize(max: TextUnit, min: TextUnit, step: TextUnit, fitsOneLine: (TextUnit) -> Boolean): FontFit {
    if (!max.isSp || !min.isSp || max.value <= min.value) return FontFit(max, wraps = !fitsOneLine(max))
    var size = max.value
    while (size > min.value) {
        if (fitsOneLine(size.sp)) return FontFit(size.sp, wraps = false)
        size -= step.value
    }
    return if (fitsOneLine(min)) FontFit(min, wraps = false) else FontFit(min, wraps = true)
}
