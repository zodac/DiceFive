package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.ui.common.BrandBackdrop
import net.zodac.dicefive.ui.common.PageColumn

/**
 * The end-of-game results page.
 *
 * A full themed page rather than a few lines appended under the board: the game is over, so the
 * board's felt, dice and scorecard are no longer what the player is looking at. The winner (or
 * winners - a tie on the top score is perfectly possible) gets a raised primary card with a
 * trophy; everyone else is a plain ranked row.
 */
@Composable
fun GameOverScreen(
    state: GameState,
    onBackToMenu: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranked = state.players.sortedByDescending { it.totalScore }
    val topScore = ranked.firstOrNull()?.totalScore ?: 0
    // Ties share the top spot: "the first player in the sorted list" would silently crown one of
    // two equal scores, which is the sort of thing that only ever shows up in a real game.
    val winners = ranked.filter { it.totalScore == topScore }
    val runnersUp = ranked.drop(winners.size)

    BrandBackdrop(modifier = modifier) {
        PageColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Game Over",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                text = if (winners.size > 1) "It's a tie!" else "${winners.firstOrNull()?.name ?: "Nobody"} wins",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            for (winner in winners) {
                WinnerCard(player = winner)
            }

            if (runnersUp.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        runnersUp.forEachIndexed { index, player ->
                            // Ranks continue past however many players shared the win.
                            RunnerUpRow(rank = winners.size + index + 1, player = player)
                        }
                    }
                }
            }

            // A fixed gap, not one that grows to push these to the bottom of the screen - the
            // achievement banner stack sits over the bottom half (see AchievementBannerHost), so
            // pinning these as a footer put them right where a banner could land on top of them.
            // They now just follow directly after the score list instead.
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onBackToMenu,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(text = "Back to Menu", style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(text = "Play Again", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WinnerCard(player: PlayerState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = "Winner",
                modifier = Modifier.size(44.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = "Winner", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = player.totalScore.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RunnerUpRow(rank: Int, player: PlayerState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = player.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = player.totalScore.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
