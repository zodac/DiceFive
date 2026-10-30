package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp

/**
 * A count banner that never wraps: [full] at [style]'s size, stepped down to [MIN_READABLE_FONT_SIZE] when it's too wide;
 * then [short] (the count without its word), likewise; then [percent] at the smallest size. Screen readers always get [full].
 */
@Composable
fun FittedCountText(full: String, short: String, percent: String, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val available = constraints.maxWidth
        val chosen = remember(full, short, percent, style, available, density) {
            if (available == Constraints.Infinity) return@remember full to style.fontSize
            val candidates = listOf(full, short)
            for (candidate in candidates) {
                val fit = FontFitCache.getOrPut(
                    FontFitKey(listOf(candidate), style, MIN_READABLE_FONT_SIZE, FONT_STEP, available, density.density, density.textScale),
                ) {
                    fitFontSize(style.fontSize, MIN_READABLE_FONT_SIZE, FONT_STEP) { size ->
                        measurer.measure(text = candidate, style = style.copy(fontSize = size), maxLines = 1, softWrap = false).size.width <= available
                    }
                }
                if (!fit.wraps) return@remember candidate to fit.size
            }
            percent to MIN_READABLE_FONT_SIZE
        }
        Text(
            text = chosen.first,
            style = style.copy(fontSize = chosen.second),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.semantics { contentDescription = full },
        )
    }
}

private val FONT_STEP = 0.5.sp

/** [count] of [total] as a whole percentage, rounded down so 100 means all of them; 0 when there are none. */
fun percentOf(count: Int, total: Int): Int = if (total <= 0) 0 else count * 100 / total
