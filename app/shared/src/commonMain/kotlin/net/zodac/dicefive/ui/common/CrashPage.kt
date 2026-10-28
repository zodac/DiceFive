package net.zodac.dicefive.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.app_name
import net.zodac.dicefive.resources.common_crash_advice
import net.zodac.dicefive.resources.common_crash_body
import net.zodac.dicefive.resources.common_crash_copied
import net.zodac.dicefive.resources.common_crash_copy
import net.zodac.dicefive.resources.common_crash_hide_details
import net.zodac.dicefive.resources.common_crash_report
import net.zodac.dicefive.resources.common_crash_restart
import net.zodac.dicefive.resources.common_crash_show_details
import net.zodac.dicefive.resources.common_crash_title
import net.zodac.dicefive.ui.icons.Symbols

/**
 * Shown in place of the app after an error nothing caught stopped it: what has happened, buttons that [onRestart] the
 * app and [onReport] the [details] as a GitHub issue, then the details themselves - the error's report - in a section
 * collapsed until Show details opens it, scrolling in the room below, with a button that [onCopy]s them. Copy details says Copied once it has, which
 * TalkBack announces. See [FailurePage].
 */
@Composable
fun CrashPage(details: String, onRestart: () -> Unit, onCopy: () -> Unit, onReport: () -> Unit) {
    val appName = stringResource(Res.string.app_name)
    var expanded by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current
    FailurePage(
        title = stringResource(Res.string.common_crash_title),
        body = stringResource(Res.string.common_crash_body, appName),
        advice = stringResource(Res.string.common_crash_advice),
        actions = {
            // Side by side where they fit, one above the other where they don't (a large font).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onRestart) { Text(stringResource(Res.string.common_crash_restart)) }
                FilledTonalButton(onClick = onReport) { Text(stringResource(Res.string.common_crash_report)) }
            }
        },
        // Under the centred part, so opening it never moves what's above: it fills the room below, and scrolls in it.
        below = {
            AnimatedVisibility(
                visible = expanded,
                enter = if (reduceMotion) EnterTransition.None else expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    ScrollingColumn(modifier = Modifier.fillMaxWidth()) {
                        FailureDetails(details, action = { CopyDetailsButton(onCopy) })
                    }
                }
            }
        },
    ) {
        TextButton(onClick = { expanded = !expanded }, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
            Text(stringResource(if (expanded) Res.string.common_crash_hide_details else Res.string.common_crash_show_details))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            // Turned up while the section is open: the words already say which way it goes, so it isn't spoken.
            Icon(
                imageVector = Symbols.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

/** Copies the details, then says Copied, which TalkBack announces. */
@Composable
private fun CopyDetailsButton(onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    FilledTonalButton(
        onClick = {
            onCopy()
            copied = true
        },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        // Both labels laid out, one shown: the button stays the wider one's width.
        Box(contentAlignment = Alignment.Center) {
            ShownIf(!copied) { Text(stringResource(Res.string.common_crash_copy), it) }
            ShownIf(copied) { Text(stringResource(Res.string.common_crash_copied), it) }
        }
    }
}

/** [content], laid out either way, but seen and spoken only when [shown]. */
@Composable
private fun ShownIf(shown: Boolean, content: @Composable (Modifier) -> Unit) {
    content(if (shown) Modifier else Modifier.alpha(0f).clearAndSetSemantics {})
}
