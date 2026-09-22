package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The app's one dialog shape, so a question from the menu and a question from the board look like
 * the same app asking.
 *
 * It's still M3's `AlertDialog` - the scrim, focus handling, predictive-back and accessibility
 * semantics are worth far more than a hand-rolled modal - but dressed as a deliberate object: an
 * icon above a centred title, a raised container a step up from the page behind it, and real
 * buttons instead of two near-identical text links, so the confirming action is obvious.
 *
 * [onDismissRequest] is separate from [onDismiss] on purpose: backing out of a dialog (tapping the
 * scrim, or the system back) is not the same as choosing its second option. Where a caller wants
 * them to be the same, it passes the same lambda.
 */
@Composable
fun DiceFiveDialog(
    icon: ImageVector,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            FilledTonalButton(onClick = onDismiss) { Text(dismissLabel) }
        },
        shape = MaterialTheme.shapes.extraLarge,
        // A step above the page's own surface, so the dialog reads as sitting on top of it rather
        // than being a patch cut out of it.
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    )
}
