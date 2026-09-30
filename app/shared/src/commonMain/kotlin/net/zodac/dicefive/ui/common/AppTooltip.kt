package net.zodac.dicefive.ui.common

import androidx.compose.foundation.border
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** What [AppTooltip] shows on request - see [rememberAppTooltipState]; wraps Material's experimental state. */
@OptIn(ExperimentalMaterial3Api::class)
class AppTooltipState internal constructor(internal val state: TooltipState) {
    /** Shows the tooltip, until it times out. */
    suspend fun show() = state.show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppTooltipState(): AppTooltipState {
    val state = rememberTooltipState()
    return remember(state) { AppTooltipState(state) }
}

/**
 * The one tooltip every screen uses - a plain tooltip over [content] - so its look, placement and
 * behaviour are changed here and nowhere else. Don't reach for `TooltipBox` directly.
 *
 * By default a long press shows it ([enabled] false switches that off). Pass [state] to show it from
 * something else too, such as a tap: `state.show()` in a coroutine. Give the content a matching action
 * for a screen reader - a tooltip isn't announced, so it can't be the only way to reach what it says.
 */
// rememberPlainTooltipPositionProvider is deprecated in favour of rememberTooltipPositionProvider,
// which doesn't exist yet in material3 1.4.0 - it arrives with the 1.5.0 line. Swap it over then.
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTooltip(
    message: AnnotatedString,
    modifier: Modifier = Modifier,
    state: AppTooltipState = rememberAppTooltipState(),
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            // Raised off the dark page by a lighter container, a gold outline and a shadow - branded
            // (gold is the app's one accent) yet plainly not part of the content it floats over. The
            // default inverse-surface tooltip is a stark light-grey slab on this dark theme.
            PlainTooltip(
                modifier = Modifier.border(TOOLTIP_OUTLINE, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                shape = MaterialTheme.shapes.small,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = TOOLTIP_ELEVATION,
            ) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        },
        state = state.state,
        modifier = modifier,
        enableUserInput = enabled,
        content = content,
    )
}

/** [AppTooltip] for a plain-text [message]. */
@Composable
fun AppTooltip(
    message: String,
    modifier: Modifier = Modifier,
    state: AppTooltipState = rememberAppTooltipState(),
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) = AppTooltip(AnnotatedString(message), modifier, state, enabled, content)

private val TOOLTIP_OUTLINE = 1.dp
private val TOOLTIP_ELEVATION = 6.dp

/** A tooltip message in [parseInlineMarkup]'s markup, with `` `backticked` `` text in bold gold. */
@Composable
fun tooltipMarkup(text: String): AnnotatedString {
    val gold = MaterialTheme.colorScheme.primary
    return remember(text, gold) {
        parseInlineMarkup(text, codeStyle = SpanStyle(fontWeight = FontWeight.Bold, color = gold))
    }
}
