package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.data.settings.Theme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val theme by viewModel.theme.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

        Column {
            Text(text = "Theme", style = MaterialTheme.typography.titleMedium)
            for (option in Theme.entries) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = option == theme, onClick = { viewModel.setTheme(option) })
                    Text(text = option.name.lowercase().replaceFirstChar(Char::uppercase))
                }
            }
        }
    }
}
