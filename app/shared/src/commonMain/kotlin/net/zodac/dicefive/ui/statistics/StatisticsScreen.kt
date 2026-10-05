package net.zodac.dicefive.ui.statistics

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.dicefive.data.scores.PlayerStatistics
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.LazyListScrollbar
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.formatTimestamp
import net.zodac.dicefive.ui.common.grouped

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteName by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "Statistics", onBack = onBack, modifier = modifier) {
        // Nothing but the title bar until the stats are read, so "No stats yet" never flashes up
        // before the player cards.
        if (!state.isLoaded) return@ScreenScaffold
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

/**
 * One player's card. Closed it is just their name and best score (gold, thousands-separated), with a
 * dropdown arrow after the score; tapping the card opens the rest - first played, the win/loss record
 * and the lifetime totals. Long-pressing deletes, open or closed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerStatsCard(player: PlayerStatistics, onLongPress: () -> Unit) {
    var expanded by rememberSaveable(player.playerName) { mutableStateOf(false) }
    val deleteLabel = "Delete ${player.playerName}'s stats"
    val spoken = buildString {
        append("${player.playerName}. Best score ${player.maxScore.grouped()}.")
        if (expanded) {
            append(" First played ${formatTimestamp(player.firstPlayedEpochMillis)}.")
            append(" Played ${player.gamesPlayed}, won ${player.gamesWon}, lost ${player.gamesLost}.")
            append(" Win streak ${player.currentWinStreak}, best win streak ${player.bestWinStreak}.")
            append(" Total score ${player.totalScore.grouped()}. Average score ${player.averageScore.grouped()}.")
            append(" ${player.fiveOfAKindCount} 5x scored. ${player.soloGames} solo games played.")
        }
    }
    val toggleLabel = if (expanded) "Hide details" else "Show details"
    val reduceMotion = LocalReduceMotion.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // One announcement in words, not a dozen separate texts. The tap opens or closes the card (its
            // state is spoken, its action labelled), and the delete is in TalkBack's actions menu.
            .clearAndSetSemantics {
                contentDescription = spoken
                stateDescription = if (expanded) "Expanded" else "Collapsed"
                onClick(label = toggleLabel) {
                    expanded = !expanded
                    true
                }
                onLongClick(label = deleteLabel) {
                    onLongPress()
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction(deleteLabel) {
                        onLongPress()
                        true
                    },
                )
            }
            .combinedClickable(
                onClick = { expanded = !expanded },
                onClickLabel = toggleLabel,
                onLongClick = onLongPress,
                onLongClickLabel = deleteLabel,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).then(if (reduceMotion) Modifier else Modifier.animateContentSize()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                    text = player.maxScore.grouped(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                )
                // Decorative: the card's own state and action are spoken (above).
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "First played",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatTimestamp(player.firstPlayedEpochMillis),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End,
                    )
                }

                // FlowRows: at a large font the cells no longer fit across, and the last ones drop to a
                // second line rather than overlapping. At the normal size each is one row.
                StatRow {
                    StatCell(label = "Played", value = player.gamesPlayed.toString())
                    StatCell(label = "Won", value = player.gamesWon.toString())
                    StatCell(label = "Lost", value = player.gamesLost.toString())
                    StatCell(label = "Streak", value = player.currentWinStreak.toString())
                    // End-aligned rather than centred, so its last digit sits on the card's edge.
                    StatCell(label = "Best", value = player.bestWinStreak.toString(), alignment = Alignment.End)
                }

                StatRow {
                    StatCell(label = "Total score", value = player.totalScore.grouped(), alignment = Alignment.Start)
                    StatCell(label = "Average", value = player.averageScore.grouped())
                    StatCell(label = "5x", value = player.fiveOfAKindCount.grouped())
                    StatCell(label = "Solo", value = player.soloGames.grouped(), alignment = Alignment.End)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun StatCell(label: String, value: String, alignment: Alignment.Horizontal = Alignment.CenterHorizontally) {
    Column(horizontalAlignment = alignment) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
