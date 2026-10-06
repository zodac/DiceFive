package net.zodac.dicefive.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 * behaviour are changed here and nowhere else. Don't reach for `TooltipBox` directly. [body] is what
 * it shows: usually a [message] of text (the overloads below), or a layout of its own (the board's
 * totals, a two-column table).
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
    body: @Composable () -> Unit,
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
                body()
            }
        },
        state = state.state,
        modifier = modifier,
        enableUserInput = enabled,
        content = content,
    )
}

/** [AppTooltip] for a [message] of text - what nearly every tooltip is. */
@Composable
fun AppTooltip(
    message: AnnotatedString,
    modifier: Modifier = Modifier,
    state: AppTooltipState = rememberAppTooltipState(),
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) = AppTooltip(
    body = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
    modifier = modifier,
    state = state,
    enabled = enabled,
    content = content,
)

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

/**
 * [AppTooltip] for content that's long-pressed rarely - a row among hundreds - so the tooltip is built only
 * when it's asked for. Until then [content] is drawn bare, with the long press picked up by a plain gesture
 * (a `TooltipBox` per row was a real share of a list's first frame); the first long press arms the real
 * tooltip around it and shows it, and from then on it behaves as [AppTooltip] does. [message] is read only
 * then, or when a screen reader asks - the `stateDescription` carries the same words, since a tooltip isn't
 * announced - so nothing is formatted for rows nobody touches.
 */
@Composable
fun OnDemandTooltip(message: () -> String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    // Spoken whether or not it's armed. Read lazily: the block runs when semantics are collected.
    val spoken = Modifier.semantics(mergeDescendants = true) { stateDescription = message().replace("\n", ", ") }
    if (armed) {
        val state = rememberAppTooltipState()
        LaunchedEffect(Unit) { state.show() }
        AppTooltip(message = message(), modifier = modifier.then(spoken), state = state, content = content)
    } else {
        Box(
            modifier = modifier
                .then(spoken)
                .semantics { onLongClick(label = "Show details") { armed = true; true } }
                .pointerInput(Unit) { detectTapGestures(onLongPress = { armed = true }) },
        ) { content() }
    }
}
