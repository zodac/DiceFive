package net.zodac.dicefive.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.theme.DiceFiveTheme

@Composable
fun MenuScreen(
    hasInProgressGame: Boolean,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onScores: () -> Unit,
    onAchievements: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResumeDialog by remember { mutableStateOf(false) }

    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text("Resume game?") },
            text = { Text("You have a game in progress.") },
            confirmButton = {
                TextButton(onClick = { showResumeDialog = false; onContinue() }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showResumeDialog = false; onNewGame() }) { Text("New Game") }
            },
        )
    }

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
            Button(
                onClick = { if (hasInProgressGame) showResumeDialog = true else onNewGame() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Play") }
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
        MenuScreen(
            hasInProgressGame = false,
            onContinue = {},
            onNewGame = {},
            onScores = {},
            onAchievements = {},
            onSettings = {},
            onAbout = {},
        )
    }
}
