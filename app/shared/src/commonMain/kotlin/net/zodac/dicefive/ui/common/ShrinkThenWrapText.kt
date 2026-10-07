package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val available = constraints.maxWidth
        val fit = remember(text, base, minFontSize, fontStep, available, density) {
            if (available == Constraints.Infinity) {
                FontFit(base.fontSize, wraps = false)
            } else {
                FontFitCache.getOrPut(FontFitKey(listOf(text), base, minFontSize, fontStep, available, density.density, density.fontScale)) {
                    fitFontSize(base.fontSize, minFontSize, fontStep) { size ->
                        measurer.measure(text = text, style = base.copy(fontSize = size), maxLines = 1, softWrap = false).size.width <= available
                    }
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

/** Everything a [FontFit] depends on: the [texts] sized together, the style, the limits, the width and the screen's scale. */
internal data class FontFitKey(
    val texts: List<String>,
    val style: TextStyle,
    val min: TextUnit,
    val step: TextUnit,
    val availablePx: Int,
    val density: Float,
    val fontScale: Float,
)

/**
 * The fits worked out so far, so a page opened again doesn't measure its labels again to choose a size: a measure
 * per label per size tried, on top of the label's own layout, was a tenth of opening Settings. An LRU of
 * [FONT_FIT_CACHE_SIZE], touched only from composition - the main thread - so it needs no lock.
 */
internal object FontFitCache {
    // Insertion-ordered, and a hit is moved to the end, so the first entry is always the least recently used.
    private val fits = LinkedHashMap<FontFitKey, FontFit>()

    fun getOrPut(key: FontFitKey, fit: () -> FontFit): FontFit {
        fits.remove(key)?.let { hit -> return hit.also { fits[key] = it } }
        return fit().also {
            fits[key] = it
            if (fits.size > FONT_FIT_CACHE_SIZE) fits.remove(fits.keys.first())
        }
    }
}

// Room for every label on every page at once, a few times over (rotation, a font scale change).
private const val FONT_FIT_CACHE_SIZE = 256

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
