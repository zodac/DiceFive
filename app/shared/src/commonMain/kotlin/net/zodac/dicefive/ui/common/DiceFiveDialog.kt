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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
 *
 * [title] is null for a dialog whose icon and message say it all. [message] can be an
 * [AnnotatedString], e.g. from [parseInlineMarkup], to highlight part of it.
 *
 * [dismissLabel]/[onDismiss] are both null for a purely informational dialog with nothing to
 * confirm or decline - just the one button, [confirmLabel], to close it. Passing one without the
 * other is a caller error.
 */
@Composable
fun DiceFiveDialog(
    icon: ImageVector,
    title: String?,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) = DiceFiveDialog(
    icon = icon,
    title = title,
    message = AnnotatedString(message),
    confirmLabel = confirmLabel,
    onConfirm = onConfirm,
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    dismissLabel = dismissLabel,
    onDismiss = onDismiss,
    iconTint = iconTint,
)

@Composable
fun DiceFiveDialog(
    icon: ImageVector,
    title: String?,
    message: AnnotatedString,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    // Overridable for the one icon in the app that's drawn in its own fixed colours rather than
    // meant to be tinted - see Achievement.iconTintOrUnspecified. Every other caller leaves this at
    // its default.
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(36.dp),
            )
        },
        title = title?.let {
            {
                Text(
                    text = it,
                    // The page titles' face and gold, as every dialog in the app has it.
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
        dismissButton = if (dismissLabel != null && onDismiss != null) {
            { FilledTonalButton(onClick = onDismiss) { Text(dismissLabel) } }
        } else {
            null
        },
        shape = MaterialTheme.shapes.extraLarge,
        // A step above the page's own surface, so the dialog reads as sitting on top of it rather
        // than being a patch cut out of it.
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    )
}
