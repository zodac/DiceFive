package net.zodac.dicefive.ui.statistics

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.dicefive.data.scores.PlayerStatistics
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.LazyListScrollbar
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.formatTimestamp

/** [GameMode.HIGHEST_POSSIBLE_SCORE] (a perfect game in whichever mode allows the most) is the longest a score can ever be. */
private val MAX_SCORE_DISPLAY_WIDTH = GameMode.HIGHEST_POSSIBLE_SCORE.toString().length

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteName by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "Statistics", onBack = onBack, modifier = modifier) {
        if (state.players.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No stats yet - play a game!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        } else {
            val listState = rememberLazyListState()

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Room on the right for the scrollbar so it doesn't sit on top of a card's edge.
                    contentPadding = PaddingValues(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.players, key = { it.playerName }) { player ->
                        PlayerStatsCard(player, onLongPress = { pendingDeleteName = player.playerName })
                    }
                }
                LazyListScrollbar(listState = listState)
            }
        }

        pendingDeleteName?.let { playerName ->
            DiceFiveDialog(
                icon = Icons.Filled.DeleteForever,
                title = "Delete stats?",
                message = "Would you like to delete $playerName's stats? Their leaderboard scores will not be affected.",
                confirmLabel = "Delete",
                onConfirm = {
                    viewModel.dismissPlayer(playerName)
                    pendingDeleteName = null
                },
                dismissLabel = "Cancel",
                onDismiss = { pendingDeleteName = null },
                onDismissRequest = { pendingDeleteName = null },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerStatsCard(player: PlayerStatistics, onLongPress: () -> Unit) {
    val spoken = "${player.playerName}. First played ${formatTimestamp(player.firstPlayedEpochMillis)}. Best score ${player.maxScore}. " +
        "Played ${player.gamesPlayed}, won ${player.gamesWon}, lost ${player.gamesLost}. " +
        "Win streak ${player.currentWinStreak}, best win streak ${player.bestWinStreak}."
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // One announcement in words, not a dozen separate texts, and no "double tap to activate" for the
            // tap that does nothing: the delete is the card's one action, in TalkBack's actions menu.
            .clearAndSetSemantics {
                contentDescription = spoken
                onLongClick(label = "Delete ${player.playerName}'s stats") {
                    onLongPress()
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Delete ${player.playerName}'s stats") {
                        onLongPress()
                        true
                    },
                )
            }
            .combinedClickable(onClick = {}, onLongClick = onLongPress, onLongClickLabel = "Delete ${player.playerName}'s stats"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Name, first-played timestamp, and max score share one baseline - sized down from
            // their old solo-row/captioned style so a max-length (10-character) name, a date, and
            // a 4-digit score all fit on one line without wrapping or crowding into each other.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = player.playerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    // A second line before an ellipsis, so a name at a large font wraps rather than losing its end.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatTimestamp(player.firstPlayedEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Wraps at a large font rather than ellipsising a date into something unreadable, sharing
                    // what's left of the row with the name instead of squeezing it out.
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    // Space-padded to a fixed width: the name before it fills whatever's left in
                    // the row, so a shorter score (fewer digits) would otherwise let the name grow
                    // into that space and shove the timestamp sideways, card to card.
                    text = player.maxScore.toString().padStart(MAX_SCORE_DISPLAY_WIDTH),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // A FlowRow: at a large font the five cells no longer fit across, and the last ones drop to a
            // second line rather than overlapping. At the normal size it's one row, as before.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCell(label = "Played", value = player.gamesPlayed.toString())
                StatCell(label = "Won", value = player.gamesWon.toString())
                StatCell(label = "Lost", value = player.gamesLost.toString())
                StatCell(label = "Streak", value = player.currentWinStreak.toString())
                StatCell(label = "Best", value = player.bestWinStreak.toString())
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
