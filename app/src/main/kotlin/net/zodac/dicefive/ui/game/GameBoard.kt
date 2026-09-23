package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.game.ScoreCalculator
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileIconColor

/** Fixed height for the score-grid + cup section, so its two columns line up row-for-row. */
private val BOARD_HEIGHT = 380.dp

/**
 * The scoring area: the active player's category grid on the left, the 5x tile / dice cup /
 * upper-bonus tracker on the right. Uses [LocalGameVisualTheme]'s scoreAreaBrush for its
 * background - swap that theme value to re-skin it independently of the dice tray below.
 */
@Composable
fun GameBoard(
    state: GameState,
    rolling: Boolean,
    canUndo: Boolean,
    onScoreCategory: (ScoreCategory) -> Unit,
    onCupTap: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visualTheme = LocalGameVisualTheme.current
    val player = state.currentPlayer
    val rolled = state.phase == TurnPhase.ROLLED
    // Tapping a box to score it is human-only - an AI player's own turn plays itself. The preview
    // glow/number is a different thing: it's informational, not an affordance, so it isn't gated
    // on who's playing - it shows for an AI's rolled dice the same as a human's, otherwise every AI
    // turn (especially Easy, which never holds anything) just changes numbers with no visual cue
    // of what's about to happen.
    val canScore = rolled && player?.type == PlayerType.HUMAN
    val available = player?.let { ScoreCalculator.availableCategories(it, state.dice) }.orEmpty().toSet()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BOARD_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(visualTheme.background.scoreAreaBrush)
            .padding(14.dp),
    ) {
        ScoreGrid(
            player = player,
            dice = state.dice,
            canScore = canScore,
            showPreview = rolled,
            available = available,
            onScoreCategory = onScoreCategory,
            // Equal weight with the cup panel, not less: the grid splits into two columns per row,
            // so per-category it actually has HALF the width the cup panel's single-column content
            // gets - it needs the room more, not less.
            modifier = Modifier.weight(1f),
        )
        // Wider than a purely decorative gap needs to be: a 2-digit score in the grid's rightmost
        // column can render past its own column's edge (see the Visible-overflow comment on that
        // Text in ScoreGrid.kt) - this gap doubles as the dead space that spillover lands in
        // harmlessly, before it would otherwise reach the cup panel's content.
        Spacer(modifier = Modifier.width(20.dp))
        DiceCupPanel(
            player = player,
            dice = state.dice,
            canScore = canScore,
            showPreview = rolled,
            available = available,
            rollsRemaining = state.rollsRemaining,
            // Directly from state.phase, not persisted across turns: a new turn resets it to
            // AWAITING_ROLL, and the cup should go back to standing right then, before anyone has
            // rolled - not stay tipped over from the previous player's last roll. What makes THIS
            // roll's shake look the same as a same-turn reroll's isn't keeping this true across
            // the boundary; it's that Cup already forces itself upright the instant a shake
            // starts, whatever `tilted` was beforehand (see LeatherDiceCupStyle).
            tilted = state.phase == TurnPhase.ROLLED,
            rolling = rolling,
            canUndo = canUndo,
            onScoreCategory = onScoreCategory,
            onCupTap = onCupTap,
            onUndo = onUndo,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The read-only counterpart to [GameBoard]: another player's scorecard, shown when the active
 * player taps that player's tab in [PlayerHeaderBar]. Same category grid and 5x tile, laid out
 * to match the live board's proportions, but with no dice cup, roll counter or undo button - none
 * of those act on a turn that isn't actually happening.
 */
@Composable
fun ReadOnlyScoreboard(player: PlayerState, modifier: Modifier = Modifier) {
    val visualTheme = LocalGameVisualTheme.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BOARD_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(visualTheme.background.scoreAreaBrush)
            .padding(14.dp),
    ) {
        ScoreGrid(
            player = player,
            dice = emptyList(),
            canScore = false,
            showPreview = false,
            available = emptySet(),
            onScoreCategory = {},
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            CategoryCell(
                category = ScoreCategory.FIVE_OF_A_KIND,
                player = player,
                canScore = false,
                showPreview = false,
                available = emptySet(),
                dice = emptyList(),
                onScoreCategory = {},
                prominent = true,
                modifier = Modifier.weight(2f).fillMaxWidth(),
            )
            // Same weight split as DiceCupPanel's cup box + bottom row, minus the cup itself - kept
            // empty rather than filled with a placeholder, per the read-only view's "no mat/dice/
            // cup" requirement.
            Spacer(modifier = Modifier.weight(3f))
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(text = "Upper: ${player.upperSectionTotal}", color = TileIconColor, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Bonus: ${player.upperSectionBonus}",
                        color = if (player.upperSectionBonus > 0) GoldAccent else TileIconColor,
                        fontWeight = if (player.upperSectionBonus > 0) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
