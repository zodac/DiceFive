package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_failure_details

/**
 * A page shown in place of the app when it can't go on ([DatabaseFailurePage], [CrashPage]): [title] as a heading, then
 * [body] and [advice], the [actions] if there are any, then [details] - for the error, see [FailureDetails] - all
 * centred in the screen. [below] hangs under them, in the room left down to the screen's foot (at least
 * [MIN_BELOW_HEIGHT]), and growing (a section opening) never moves them.
 */
@Composable
internal fun FailurePage(
    title: String,
    body: String,
    advice: String,
    actions: (@Composable () -> Unit)? = null,
    below: @Composable () -> Unit = {},
    details: @Composable ColumnScope.() -> Unit,
) {
    BrandBackdrop(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // The height PageColumn's content fills: the screen, less the system bars and its own margins.
            val density = LocalDensity.current
            val bars = WindowInsets.systemBars
            val viewport = constraints.maxHeight - bars.getTop(density) - bars.getBottom(density) - with(density) { (PAGE_VERTICAL_PADDING * 2).roundToPx() }
            PageColumn {
                PinnedCentre(viewport = viewport, below = below) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            style = dialogTitleStyle,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = advice,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        actions?.let {
                            Spacer(Modifier.height(24.dp))
                            it()
                        }
                        Spacer(Modifier.height(24.dp))
                        details()
                    }
                }
            }
        }
    }
}

/** The least room [FailurePage]'s `below` is given, on a screen too short (or a font too large) to leave it more. */
private val MIN_BELOW_HEIGHT = 160.dp

/**
 * [centred] in the middle of a [viewport] px high - or at its top, if it doesn't fit - with [below] under it, given the
 * room left down to the viewport's foot (at least [MIN_BELOW_HEIGHT]). However tall [below] grows, [centred] stays
 * where it is.
 */
@Composable
private fun PinnedCentre(viewport: Int, below: @Composable () -> Unit, centred: @Composable () -> Unit) {
    Layout(contents = listOf(centred, below), modifier = Modifier.fillMaxWidth()) { (centredPart, belowPart), constraints ->
        val loose = constraints.copy(minHeight = 0)
        val top = centredPart.map { it.measure(loose) }
        val topHeight = top.sumOf { it.height }
        val y = maxOf(0, (viewport - topHeight) / 2)
        val room = maxOf(viewport - y - topHeight, MIN_BELOW_HEIGHT.roundToPx())
        val bottom = belowPart.map { it.measure(loose.copy(maxHeight = room)) }
        val height = maxOf(viewport, y + topHeight + bottom.sumOf { it.height })
        layout(constraints.maxWidth, height) {
            var next = y
            (top + bottom).forEach { placeable ->
                placeable.place((constraints.maxWidth - placeable.width) / 2, next)
                next += placeable.height
            }
        }
    }
}

/**
 * The error on a [FailurePage]: what the details are for, the [action] if there is one, then [details] - the error as
 * it was thrown, untranslated - selectable, to copy into a report.
 */
@Composable
internal fun FailureDetails(details: String, action: (@Composable () -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(Res.string.common_failure_details),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        action?.let {
            Spacer(Modifier.height(12.dp))
            it()
        }
        Spacer(Modifier.height(if (action == null) 8.dp else 12.dp))
        // Code, read left to right in every language: in a right-to-left one, its brackets would otherwise be turned round.
        SelectionContainer(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Left,
            )
        }
    }
}
