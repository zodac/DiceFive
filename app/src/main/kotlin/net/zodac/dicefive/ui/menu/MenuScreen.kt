package net.zodac.dicefive.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.theme.DiceFiveTheme

@Composable
fun MenuScreen(
    onPlay: () -> Unit,
    onScores: () -> Unit,
    onAchievements: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "DiceFive", style = MaterialTheme.typography.headlineLarge)

        Column(
            modifier = Modifier.padding(top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { Text("Play") }
            Button(onClick = onScores, modifier = Modifier.fillMaxWidth()) { Text("Scores") }
            Button(onClick = onAchievements, modifier = Modifier.fillMaxWidth()) { Text("Achievements") }
            Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
            Button(onClick = onAbout, modifier = Modifier.fillMaxWidth()) { Text("About") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MenuScreenPreview() {
    DiceFiveTheme {
        MenuScreen(onPlay = {}, onScores = {}, onAchievements = {}, onSettings = {}, onAbout = {})
    }
}
