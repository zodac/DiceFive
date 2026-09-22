package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.ui.theme.PlayerColors

/**
 * The top row of player tabs: name, running total, and (for the active player) a colored outline
 * plus a small dot underneath - the only "whose turn is it" indicator, since the scoring grid
 * below always shows just the active player's own card.
 */
@Composable
fun PlayerHeaderBar(players: List<PlayerState>, currentPlayerIndex: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        players.forEachIndexed { index, player ->
            PlayerTab(
                name = player.name,
                score = player.totalScore,
                color = PlayerColors[index % PlayerColors.size],
                active = index == currentPlayerIndex,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlayerTab(name: String, score: Int, color: Color, active: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .clip(shape)
            .then(if (active) Modifier.border(1.5.dp, color.copy(alpha = 0.85f), shape) else Modifier)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = name,
            color = color,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = score.toString(),
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) color else Color.Transparent),
        )
    }
}
