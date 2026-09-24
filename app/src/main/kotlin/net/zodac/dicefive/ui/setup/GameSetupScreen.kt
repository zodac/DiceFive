package net.zodac.dicefive.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameType
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.ui.common.PinnedActionBar
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.SegmentedChoiceRow
import net.zodac.dicefive.ui.game.GameSetupState
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.game.PlayerSetupSlot

/**
 * Fixed width for the per-row User/CPU control, so the name fields above and below it all end at
 * the same place instead of stepping in and out with the label lengths.
 */
private val TYPE_CONTROL_WIDTH = 76.dp

@Composable
fun GameSetupScreen(
    viewModel: GameViewModel,
    onStartGame: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val setup by viewModel.setup.collectAsState()

    ScreenScaffold(
        title = "New Game",
        onBack = onBack,
        modifier = modifier,
        scrollable = true,
        // Pinned rather than sitting at the end of the form: at four players the form is long
        // enough that a trailing button would need a scroll before the game could be started.
        bottomBar = {
            PinnedActionBar {
                Button(
                    onClick = {
                        viewModel.startGame()
                        onStartGame()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(text = "Start Game", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) {
        SetupCard(title = "Players") {
            PlayerCountSelector(count = setup.playerCount, onCountChange = viewModel::setPlayerCount)
        }

        // Player 1 is always the human at this device - their name lives in Settings now, so
        // there's nothing to configure for them here. Only the other seats (2-4) ever need a row,
        // and with one player there are none at all, so the whole card is skipped.
        val otherSlots = setup.playerSlots.take(setup.playerCount).drop(1)
        if (otherSlots.isNotEmpty()) {
            // One card holding every other player as a single row each, rather than a card per
            // player: three stacked cards, each with its own title, padding and controls, is what
            // pushed the form off the screen. No card title here - every row already names its own
            // player.
            SetupCard {
                otherSlots.forEachIndexed { index, slot ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    PlayerRow(
                        slot = slot,
                        onTypeChange = { type -> viewModel.setPlayerType(slot.slot, type) },
                        onNameChange = { name -> viewModel.setPlayerName(slot.slot, name) },
                        onDifficultyChange = { difficulty -> viewModel.setPlayerDifficulty(slot.slot, difficulty) },
                    )
                }
            }
        }

        SetupCard(title = "Game Type") {
            GameTypeSelector(selected = setup.gameType, onSelect = viewModel::setGameType)
        }
    }
}

/**
 * One section of the form - a card is M3's grouping surface for exactly this.
 *
 * [title] is optional: a section whose contents already label themselves (the player rows) doesn't
 * need a heading repeating it.
 */
@Composable
private fun SetupCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/**
 * Player count as four fixed choices rather than a -/+ stepper: the range is 1..4, so every option
 * fits on one line, and picking 4 is one tap instead of three.
 */
@Composable
private fun PlayerCountSelector(count: Int, onCountChange: (Int) -> Unit) {
    SegmentedChoiceRow(
        options = (GameSetupState.MIN_PLAYERS..GameSetupState.MAX_PLAYERS).toList(),
        selected = count,
        onSelect = onCountChange,
        label = Int::toString,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A player on one line: their name (editable for a User, automatic for a CPU) and the control
 * that switches between the two.
 *
 * The User/CPU control sits in the same inner [Row] as the name field or difficulty picker -
 * rather than a shared trailing slot alongside a [Column] that also carries the "Player N" label
 * for a CPU - so [Alignment.CenterVertically] centers it against the actual control it toggles,
 * not against that label plus the control's combined height.
 */
@Composable
private fun PlayerRow(
    slot: PlayerSetupSlot,
    onTypeChange: (PlayerType) -> Unit,
    onNameChange: (String) -> Unit,
    onDifficultyChange: (Difficulty) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (slot.type == PlayerType.AI) {
            Text(
                text = "Player ${slot.slot}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (slot.type) {
                PlayerType.HUMAN -> OutlinedTextField(
                    value = slot.name,
                    onValueChange = onNameChange,
                    label = { Text("Player ${slot.slot}") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )

                PlayerType.AI -> DifficultySelector(
                    selected = slot.difficulty,
                    onSelect = onDifficultyChange,
                    modifier = Modifier.weight(1f),
                )
            }

            FilterChip(
                selected = slot.type == PlayerType.AI,
                onClick = { onTypeChange(if (slot.type == PlayerType.AI) PlayerType.HUMAN else PlayerType.AI) },
                label = { Text(if (slot.type == PlayerType.AI) "CPU" else "User") },
                modifier = Modifier.width(TYPE_CONTROL_WIDTH),
            )
        }
    }
}

/**
 * Compact Easy/Medium/Hard picker for one AI slot - a segmented row rather than a full row of
 * chips, since it has to fit inside the player row alongside the name column.
 */
@Composable
private fun DifficultySelector(selected: Difficulty, onSelect: (Difficulty) -> Unit, modifier: Modifier = Modifier) {
    SegmentedChoiceRow(
        options = Difficulty.entries,
        selected = selected,
        onSelect = onSelect,
        label = { it.label },
        modifier = modifier,
        labelStyle = MaterialTheme.typography.labelSmall,
    )
}

private val Difficulty.label: String
    get() = when (this) {
        Difficulty.EASY -> "Easy"
        Difficulty.MEDIUM -> "Medium"
        Difficulty.HARD -> "Hard"
    }

@Composable
private fun GameTypeSelector(selected: GameType, onSelect: (GameType) -> Unit) {
    GameTypeOption(
        label = "Classic",
        selected = selected == GameType.CLASSIC,
        enabled = true,
        onSelect = { onSelect(GameType.CLASSIC) },
    )
    GameTypeOption(label = "Extended (coming soon)", selected = false, enabled = false, onSelect = {})
}

/**
 * The whole row is the target, not just the radio dot, and `selectable` with [Role.RadioButton] is
 * what tells accessibility services this is one option in a group.
 */
@Composable
private fun GameTypeOption(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
