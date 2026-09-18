package net.zodac.dicefive.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stub for Phase 2 (nav scaffold only) - player count/type/name and game
 * type selection lands in Phase 3. See .claude/DESIGN.md.
 */
@Composable
fun GameSetupScreen(
    onStartGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Game Setup", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onStartGame, modifier = Modifier.padding(top = 24.dp)) {
            Text("Start Game")
        }
    }
}
