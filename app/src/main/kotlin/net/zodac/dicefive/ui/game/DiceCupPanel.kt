package net.zodac.dicefive.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileIconColor

/**
 * The right-hand column beside the category grid: the prominent 5x tile, the (tappable)
 * dice cup with its remaining-rolls count, and the upper-section bonus tracker with undo.
 */
@Composable
fun DiceCupPanel(
    player: PlayerState?,
    dice: List<Die>,
    canScore: Boolean,
    available: Set<ScoreCategory>,
    rollsRemaining: Int,
    tilted: Boolean,
    rolling: Boolean,
    canRoll: Boolean,
    canUndo: Boolean,
    onScoreCategory: (ScoreCategory) -> Unit,
    onCupTap: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visualTheme = LocalGameVisualTheme.current

    Column(modifier = modifier.fillMaxHeight()) {
        CategoryCell(
            category = ScoreCategory.FIVE_OF_A_KIND,
            player = player,
            canScore = canScore,
            available = available,
            dice = dice,
            onScoreCategory = onScoreCategory,
            prominent = true,
            modifier = Modifier.weight(2f).fillMaxWidth(),
        )

        val cupInteractionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                // weight(3f) reproduces the same row height this area already had - only the width
                // changes here (fillMaxWidth, below), not the height.
                .weight(3f)
                .fillMaxWidth()
                // The cup itself rotates (shake + tilt) via graphicsLayer, which only affects
                // painting, not this composable's own layout/hit-test bounds. Rather than rely on
                // Compose's hit-testing following that rotation (unreliable in practice - the tap
                // target stayed pinned to the upright pose after the visual tilted), the clickable
                // area is this separate, non-rotating Box wrapping the cup and its label, widened to
                // the full row width so the tap target reaches as far right as the undo button
                // below it - not just the 104dp square the cup art itself occupies.
                //
                // indication = null drops the default ripple: at this size it painted as an
                // obvious translucent white rectangle over the whole tap target on press, which
                // read as a rendering glitch rather than a press effect.
                .clickable(
                    interactionSource = cupInteractionSource,
                    indication = null,
                    enabled = canRoll,
                    onClick = onCupTap,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    visualTheme.diceCupStyle.Cup(
                        rolling = rolling,
                        tilted = tilted,
                        modifier = Modifier.size(width = 58.dp, height = 84.dp),
                    )
                }
                Text(
                    text = "x$rollsRemaining",
                    color = TileIconColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val upperTotal = player?.upperSectionTotal ?: 0
            val upperBonus = player?.upperSectionBonus ?: 0
            // Clearance from the score grid's rightmost column - which can render a 2-digit score
            // past its own column's edge - comes from GameBoard's inter-panel gap and weight split,
            // not from padding here specifically, so every row of this panel (this one, the cup, the
            // 5x tile above) gets the same protection instead of just this one.
            Column {
                Text(text = "Upper: $upperTotal", color = TileIconColor, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Bonus: $upperBonus",
                    color = if (upperBonus > 0) GoldAccent else TileIconColor,
                    fontWeight = if (upperBonus > 0) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            UndoButton(enabled = canUndo, onClick = onUndo)
        }
    }
}
