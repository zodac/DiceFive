package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * Renders the in-progress game held by [GameViewModel.game]. Dice tray,
 * hold toggles, scorecard grid, and AI auto-play land in Phase 4 - see
 * .claude/DESIGN.md; for now this just proves the setup -> game hand-off.
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.game.collectAsState()
    val currentState = state ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "DiceFive")
        Text(text = "Current player: ${currentState.currentPlayer?.name}")
        Text(text = "Rolls remaining: ${currentState.rollsRemaining}")
    }
}

@Preview(showBackground = true)
@Composable
private fun GameScreenPreview() {
    val viewModel = GameViewModel().apply { startGame() }
    DiceFiveTheme {
        GameScreen(viewModel = viewModel)
    }
}
