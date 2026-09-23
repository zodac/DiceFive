package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.game.ScoreCalculator
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme

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
    val canScore = state.phase == TurnPhase.ROLLED && player?.type == PlayerType.HUMAN
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
