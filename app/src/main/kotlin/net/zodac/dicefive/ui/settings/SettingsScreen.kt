package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.data.settings.Theme
import net.zodac.dicefive.ui.common.ScreenScaffold

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme by viewModel.theme.collectAsState()
    val confirmBeforeLeavingGame by viewModel.confirmBeforeLeavingGame.collectAsState()

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
    }
}

/**
 * Three mutually exclusive options that each fit in a word: M3 points at a segmented button for
 * exactly this, and it puts the whole choice on one line instead of a three-row radio group.
 */
@Composable
private fun ThemeSelector(selected: Theme, onSelect: (Theme) -> Unit) {
    val options = Theme.entries

    Column(modifier = Modifier.padding(16.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(text = option.name.lowercase().replaceFirstChar(Char::uppercase))
                }
            }
        }
    }
}
