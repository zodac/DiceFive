package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.data.settings.Theme
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.SegmentedChoiceRow

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme by viewModel.theme.collectAsState()
    val confirmBeforeLeavingGame by viewModel.confirmBeforeLeavingGame.collectAsState()
    // Saveable: a rotation mid-confirmation shouldn't silently drop the question.
    var showResetAchievementsConfirmation by rememberSaveable { mutableStateOf(false) }
    var showResetScoresConfirmation by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = "Settings", onBack = onBack, modifier = modifier, scrollable = true) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            ThemeSelector(selected = theme, onSelect = viewModel::setTheme)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Gameplay",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            ListItem(
                headlineContent = { Text("Confirm before leaving") },
                supportingContent = { Text("Ask first when backing out of a game in progress") },
                trailingContent = {
                    Switch(checked = confirmBeforeLeavingGame, onCheckedChange = viewModel::setConfirmBeforeLeavingGame)
                },
                // The Card already supplies the surface; an opaque ListItem container would paint a
                // second, slightly different one on top of it.
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Reset",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            ListItem(
                headlineContent = { Text("Reset achievements") },
                supportingContent = { Text("Clear every unlock and all progress on this device") },
                trailingContent = {
                    TextButton(
                        onClick = { showResetAchievementsConfirmation = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Reset")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text("Reset leaderboard & statistics") },
                supportingContent = { Text("Clear every recorded score on this device") },
                trailingContent = {
                    TextButton(
                        onClick = { showResetScoresConfirmation = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Reset")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    if (showResetAchievementsConfirmation) {
        DiceFiveDialog(
            icon = Icons.Filled.RestartAlt,
            title = "Reset achievements?",
            message = "Every achievement will be locked again and all progress towards them lost. " +
                "This can't be undone. Your scores and settings are not affected.",
            confirmLabel = "Reset",
            onConfirm = {
                viewModel.resetAchievements()
                showResetAchievementsConfirmation = false
            },
            dismissLabel = "Cancel",
            onDismiss = { showResetAchievementsConfirmation = false },
            onDismissRequest = { showResetAchievementsConfirmation = false },
        )
    }

    if (showResetScoresConfirmation) {
        DiceFiveDialog(
            icon = Icons.Filled.RestartAlt,
            title = "Reset leaderboard & statistics?",
            message = "Every recorded score will be deleted, clearing the Leaderboard and Statistics screens. " +
                "This can't be undone. Achievements and settings are not affected, though any achievement " +
                "progress measured against the leaderboard will start over.",
            confirmLabel = "Reset",
            onConfirm = {
                viewModel.resetScores()
                showResetScoresConfirmation = false
            },
            dismissLabel = "Cancel",
            onDismiss = { showResetScoresConfirmation = false },
            onDismissRequest = { showResetScoresConfirmation = false },
        )
    }
}

/**
 * Three mutually exclusive options that each fit in a word: M3 points at a segmented button for
 * exactly this, and it puts the whole choice on one line instead of a three-row radio group.
 */
@Composable
private fun ThemeSelector(selected: Theme, onSelect: (Theme) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        SegmentedChoiceRow(
            options = Theme.entries,
            selected = selected,
            onSelect = onSelect,
            label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
