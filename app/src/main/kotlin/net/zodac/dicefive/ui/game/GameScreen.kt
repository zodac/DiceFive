package net.zodac.dicefive.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.ui.theme.DiceFiveTheme

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
    onBackToMenu: () -> Unit = {},
) {
    val state by viewModel.game.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val confirmBeforeLeaving by viewModel.confirmBeforeLeavingGame.collectAsState()
    val currentState = state ?: return
    var showLeaveConfirmation by remember { mutableStateOf(false) }

    // Redirect system back to Menu (default nav behavior would land on the setup form instead).
    BackHandler {
        if (!currentState.isGameOver && confirmBeforeLeaving) {
            showLeaveConfirmation = true
        } else {
            onBackToMenu()
        }
    }

    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmation = false },
            title = { Text("Leave game?") },
            text = { Text("Your progress is saved - you can continue this game later from Play.") },
            confirmButton = {
                TextButton(onClick = { showLeaveConfirmation = false; onBackToMenu() }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (currentState.isGameOver) {
            GameOverSummary(state = currentState, onBackToMenu = onBackToMenu)
        } else {
            InProgressGame(
                state = currentState,
                canUndo = canUndo,
                onUndo = viewModel::undo,
                onRoll = viewModel::rollDice,
                onToggleHold = viewModel::toggleHold,
                onScoreCategory = viewModel::commitScore,
            )
        }
    }
}

@Composable
private fun InProgressGame(
    state: GameState,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onRoll: () -> Unit,
    onToggleHold: (Int) -> Unit,
    onScoreCategory: (ScoreCategory) -> Unit,
) {
    val currentPlayer = state.currentPlayer
    val isHumanTurn = currentPlayer?.type == PlayerType.HUMAN
    val canRoll = isHumanTurn && state.rollsRemaining > 0
    val canHold = isHumanTurn && state.phase == TurnPhase.ROLLED && state.rollsRemaining > 0

    Text(text = "DiceFive", style = MaterialTheme.typography.headlineMedium)
    Text(
        text = if (isHumanTurn) "${currentPlayer.name}'s turn" else "${currentPlayer?.name} is playing...",
        style = MaterialTheme.typography.titleMedium,
    )

    DiceRow(dice = state.dice, enabled = canHold, onToggleHold = onToggleHold)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onRoll, enabled = canRoll) {
            Text(text = "Roll (${state.rollsRemaining} left)")
        }
        OutlinedButton(onClick = onUndo, enabled = canUndo) {
            Text("Undo")
        }
    }

    ScorecardView(
        state = state,
        onScoreCategory = onScoreCategory,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GameOverSummary(state: GameState, onBackToMenu: () -> Unit) {
    Text(text = "Game Over", style = MaterialTheme.typography.headlineMedium)

    val ranked = state.players.sortedByDescending { it.totalScore }
    for ((index, player) in ranked.withIndex()) {
        Text(text = "${index + 1}. ${player.name} - ${player.totalScore}", style = MaterialTheme.typography.titleMedium)
    }

    Button(onClick = onBackToMenu, modifier = Modifier.padding(top = 8.dp)) {
        Text("Back to Menu")
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
