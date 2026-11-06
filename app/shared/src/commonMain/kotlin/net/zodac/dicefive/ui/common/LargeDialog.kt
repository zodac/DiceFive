package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_ok

/** The margin inside a [LargeDialog], round its content. */
val LARGE_DIALOG_MARGIN = 20.dp

/**
 * A raised reading surface for a lot of text - About, Licences - rather than a [DiceFiveDialog], which asks a question:
 * most of the screen's height, no wider than the app's content, with [content] above an OK button at its bottom end, as
 * a [DiceFiveDialog]'s one button sits. [contentModifier] is applied to the column holding both, inside the surface.
 */
@Composable
fun LargeDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f)
                .widthIn(max = CONTENT_MAX_WIDTH),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(contentModifier)
                    .padding(LARGE_DIALOG_MARGIN),
            ) {
                content()
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismissRequest) { Text(stringResource(Res.string.common_ok)) }
                }
            }
        }
    }
}

/** A [LargeDialog]'s title: the brand font, bold and gold, and a heading for a screen reader. */
@Composable
fun LargeDialogTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = dialogTitleStyle,
        modifier = modifier.semantics { heading() },
    )
}
