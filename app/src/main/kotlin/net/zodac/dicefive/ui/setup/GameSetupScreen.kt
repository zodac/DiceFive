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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.GameType
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.ui.common.PinnedActionBar
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.game.GameSetupState
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.game.PlayerSetupSlot

/**
 * Fixed width for the per-row Human/AI control, so the name fields above and below it all end at
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

        // One card holding every player as a single row each, rather than a card per player: four
        // stacked cards, each with its own title, padding and controls, is what pushed the form
        // off the screen. No card title here - every row already names its own player.
        SetupCard {
            setup.playerSlots.take(setup.playerCount).forEachIndexed { index, slot ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                PlayerRow(
                    slot = slot,
                    onTypeChange = { type -> viewModel.setPlayerType(slot.slot, type) },
                    onNameChange = { name -> viewModel.setPlayerName(slot.slot, name) },
                )
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
    val options = GameSetupState.MIN_PLAYERS..GameSetupState.MAX_PLAYERS

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == count,
                onClick = { onCountChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.count()),
            ) {
                Text(text = option.toString())
            }
        }
    }
}

/**
 * A player on one line: their name (editable for a human, automatic for an AI) and the control
 * that switches between the two.
 */
@Composable
private fun PlayerRow(
    slot: PlayerSetupSlot,
    onTypeChange: (PlayerType) -> Unit,
    onNameChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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

            PlayerType.AI -> Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Player ${slot.slot}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Named at game start",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The difficulty control was a whole extra row per player and every option in it is
                // disabled until the AI actually has difficulty levels - so it's one line of text
                // until it does something.
                Text(
                    text = "Medium difficulty (coming soon)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (slot.slot == 1) {
            // Slot 1 is always the human at the phone, so there's nothing to toggle - but the
            // space is still reserved, to keep every row's name field the same width.
            Text(
                text = "You",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(TYPE_CONTROL_WIDTH),
                textAlign = TextAlign.Center,
            )
        } else {
            FilterChip(
                selected = slot.type == PlayerType.AI,
                onClick = { onTypeChange(if (slot.type == PlayerType.AI) PlayerType.HUMAN else PlayerType.AI) },
                label = { Text("AI") },
                modifier = Modifier.width(TYPE_CONTROL_WIDTH),
            )
        }
    }
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
