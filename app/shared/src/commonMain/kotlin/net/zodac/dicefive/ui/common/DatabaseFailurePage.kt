package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.app_name
import net.zodac.dicefive.resources.common_database_failure_advice
import net.zodac.dicefive.resources.common_database_failure_body
import net.zodac.dicefive.resources.common_database_failure_details
import net.zodac.dicefive.resources.common_database_failure_title

/**
 * Shown in place of every page while the scores database can't be opened (`AppContainer.databaseFailure`): what has
 * happened and what to do about it, then [details] - the error as it was thrown, untranslated - selectable, to copy
 * into a report.
 */
@Composable
fun DatabaseFailurePage(details: String) {
    val appName = stringResource(Res.string.app_name)
    BrandBackdrop(modifier = Modifier.fillMaxSize()) {
        PageColumn(verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(Res.string.common_database_failure_title),
                style = dialogTitleStyle,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.common_database_failure_body, appName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.common_database_failure_advice),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.common_database_failure_details),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
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
}
