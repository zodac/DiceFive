package net.zodac.dicefive.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameType
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.ui.game.GameSetupState
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.game.PlayerSetupSlot

@Composable
fun GameSetupScreen(
    viewModel: GameViewModel,
    onStartGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val setup by viewModel.setup.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Game Setup", style = MaterialTheme.typography.headlineMedium)

        PlayerCountSelector(
            count = setup.playerCount,
            onCountChange = viewModel::setPlayerCount,
        )

        HorizontalDivider()

        for (slot in setup.playerSlots.take(setup.playerCount)) {
            PlayerSlotEditor(
                slot = slot,
                onTypeChange = { type -> viewModel.setPlayerType(slot.slot, type) },
                onNameChange = { name -> viewModel.setPlayerName(slot.slot, name) },
            )
        }

        HorizontalDivider()

        GameTypeSelector(
            selected = setup.gameType,
            onSelect = viewModel::setGameType,
        )

        Button(
            onClick = {
                viewModel.startGame()
                onStartGame()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start Game")
        }
    }
}

@Composable
private fun PlayerCountSelector(count: Int, onCountChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Players:", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onCountChange(count - 1) }, enabled = count > GameSetupState.MIN_PLAYERS) { Text("-") }
        Text(text = count.toString(), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onCountChange(count + 1) }, enabled = count < GameSetupState.MAX_PLAYERS) { Text("+") }
    }
}

@Composable
private fun PlayerSlotEditor(
    slot: PlayerSetupSlot,
    onTypeChange: (PlayerType) -> Unit,
    onNameChange: (String) -> Unit,
) {
    val isFirstPlayer = slot.slot == 1

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Player ${slot.slot}" + if (isFirstPlayer) " (You)" else "", style = MaterialTheme.typography.titleSmall)

        if (isFirstPlayer) {
            Text(text = "Human", style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeToggleButton(label = "Human", selected = slot.type == PlayerType.HUMAN) { onTypeChange(PlayerType.HUMAN) }
                TypeToggleButton(label = "AI", selected = slot.type == PlayerType.AI) { onTypeChange(PlayerType.AI) }
            }
        }

        when (slot.type) {
            PlayerType.HUMAN -> OutlinedTextField(
                value = slot.name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            PlayerType.AI -> {
                Text(text = "Name generated at game start", style = MaterialTheme.typography.bodySmall)
                DisabledDifficultySelector(difficulty = slot.difficulty)
            }
        }
    }
}

@Composable
private fun TypeToggleButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun DisabledDifficultySelector(difficulty: Difficulty) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Difficulty (coming soon):", style = MaterialTheme.typography.bodySmall)
        for (option in Difficulty.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = option == difficulty, onClick = null, enabled = false)
                Text(text = option.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun GameTypeSelector(selected: GameType, onSelect: (GameType) -> Unit) {
    Column {
        Text(text = "Game Type", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected == GameType.CLASSIC, onClick = { onSelect(GameType.CLASSIC) })
            Text(text = "Classic")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = false, onClick = null, enabled = false)
            Text(text = "Extended (coming soon)", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
