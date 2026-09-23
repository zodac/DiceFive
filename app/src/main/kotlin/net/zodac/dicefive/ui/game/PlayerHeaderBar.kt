package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.ui.theme.PlayerColors

/**
 * The top row of player tabs: name, running total, and (for the active player) a colored outline
 * plus a small dot underneath - the only "whose turn is it" indicator, since the scoring grid
 * below always shows just the active player's own card. Tapping a tab shows that player's
 * scorecard read-only in place of the live board (see [PlayerHeaderBar]'s `onPlayerTap`) - the
 * active player's own ring never moves for this, but the tab being viewed gets a dashed outline
 * so it's clear the board on screen isn't the current turn's.
 */
@Composable
fun PlayerHeaderBar(
    players: List<PlayerState>,
    currentPlayerIndex: Int,
    viewedPlayerIndex: Int?,
    onPlayerTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Three or four tabs leave roughly a quarter of the screen each, which a name at labelLarge
    // can outgrow even at the setup screen's length cap - so the name (not the score) steps down
    // a size, rather than every full-length name arriving pre-ellipsised.
    val compactNames = players.size > 2

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        players.forEachIndexed { index, player ->
            PlayerTab(
                name = player.name,
                score = player.totalScore,
                color = PlayerColors[index % PlayerColors.size],
                active = index == currentPlayerIndex,
                viewed = index == viewedPlayerIndex,
                compactName = compactNames,
                onClick = { onPlayerTap(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlayerTab(
    name: String,
    score: Int,
    color: Color,
    active: Boolean,
    viewed: Boolean,
    compactName: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .clip(shape)
            .then(if (active) Modifier.border(1.5.dp, color.copy(alpha = 0.85f), shape) else Modifier)
            .then(if (viewed) Modifier.dashedBorder(1.5.dp, color.copy(alpha = 0.85f), 10.dp) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = name,
            color = color,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = if (compactName) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
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

/** Compose's built-in `Modifier.border` has no dash-pattern option, so the "viewed player" ring
 * draws its own rounded-rect stroke instead of reusing the active player's solid one. */
private fun Modifier.dashedBorder(width: Dp, color: Color, cornerRadius: Dp): Modifier = drawWithContent {
    drawContent()
    val strokeWidthPx = width.toPx()
    val inset = strokeWidthPx / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        ),
    )
}
