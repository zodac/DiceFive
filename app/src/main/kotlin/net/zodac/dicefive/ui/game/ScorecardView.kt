package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.game.ScoreCalculator
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase

/**
 * A category-by-player grid. Filled boxes show their score; the current
 * human player's open, currently-available boxes are tappable buttons
 * previewing what they'd score; every other open box just shows a dash.
 */
@Composable
fun ScorecardView(
    state: GameState,
    onScoreCategory: (ScoreCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPlayer = state.currentPlayer
    val canScore = state.phase == TurnPhase.ROLLED && currentPlayer?.type == PlayerType.HUMAN
    val available = currentPlayer?.let { ScoreCalculator.availableCategories(it, state.dice) }.orEmpty().toSet()

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = "", modifier = Modifier.weight(2f))
            state.players.forEachIndexed { index, player ->
                Text(
                    text = player.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (index == state.currentPlayerIndex) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
        HorizontalDivider()

        for (category in ScoreCategory.entries) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = category.displayName(), modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                state.players.forEachIndexed { index, player ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val filled = player.scorecard[category]
                        when {
                            filled != null -> Text(text = filled.toString(), style = MaterialTheme.typography.bodySmall)
                            index == state.currentPlayerIndex && canScore && category in available ->
                                TextButton(onClick = { onScoreCategory(category) }, contentPadding = PaddingValues(0.dp)) {
                                    Text(
                                        text = ScoreCalculator.scoreFor(player, category, state.dice).toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }

                            else -> Text(text = "-", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        HorizontalDivider()

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Total", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
            state.players.forEach { player ->
                Text(text = player.totalScore.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun ScoreCategory.displayName(): String =
    name.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
