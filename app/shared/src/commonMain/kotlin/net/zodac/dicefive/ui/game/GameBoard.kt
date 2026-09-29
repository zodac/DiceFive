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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.game.ScoreCalculator
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme

/** Inset of the score-grid + cup section's content. Its height is fixed per game mode (see
 * [scoreBoardHeight]), so its two columns line up row-for-row. */
private val BOARD_PADDING = 14.dp

/**
 * The scoring area shared by a live turn ([GameBoard]) and a read-only look at another player
 * ([ReadOnlyScoreboard]): the category grid on the left, the 5x tile / dice cup / upper-bonus
 * tracker on the right. The two callers differ only in what they pass in - dice, whether a
 * category can be tapped, and [cup] (null for a read-only view, since nothing here is a turn
 * that's actually happening) - never in this layout itself, so the two boards can't drift apart.
 * Uses [LocalGameVisualTheme]'s scoreAreaBrush for its background - swap that theme value to
 * re-skin it independently of the dice tray below.
 */
@Composable
private fun ScoreBoardRow(
    gameMode: GameMode,
    player: PlayerState?,
    dice: List<Die>,
    canScore: Boolean,
    showPreview: Boolean,
    available: Set<ScoreCategory>,
    onScoreCategory: (ScoreCategory) -> Unit,
    cup: CupPanelState?,
    modifier: Modifier = Modifier,
) {
    val visualTheme = LocalGameVisualTheme.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(scoreBoardHeight(gameMode, BOARD_PADDING))
            .clip(RoundedCornerShape(16.dp))
            .background(visualTheme.background.scoreAreaBrush)
            .drawBehind { with(visualTheme.background) { drawScoreAreaDecoration() } }
            .padding(BOARD_PADDING),
    ) {
        ScoreGrid(
            gameMode = gameMode,
            player = player,
            dice = dice,
            canScore = canScore,
            showPreview = showPreview,
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
            gameMode = gameMode,
            player = player,
            dice = dice,
            canScore = canScore,
            showPreview = showPreview,
            available = available,
            onScoreCategory = onScoreCategory,
            cup = cup,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun GameBoard(
    state: GameState,
    rolling: Boolean,
    canUndo: Boolean,
    onScoreCategory: (ScoreCategory) -> Unit,
    onCupTap: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    diceSettling: Boolean = false,
) {
    val player = state.currentPlayer
    val rolled = state.phase == TurnPhase.ROLLED
    // Tapping a box to score it is human-only - an AI player's own turn plays itself. The preview
    // glow/number is a different thing: it's informational, not an affordance, so it isn't gated
    // on who's playing - it shows for an AI's rolled dice the same as a human's, otherwise every AI
    // turn (especially Easy, which never holds anything) just changes numbers with no visual cue
    // of what's about to happen.
    //
    // Neither shows while a roll is in hand: `rolled` alone only reflects the PREVIOUS roll's result
    // while the cup is mid-shake for a reroll (state.phase doesn't move off ROLLED until the shake
    // finishes and the new dice actually land), and once they land they're still tumbling to a stop
    // ([diceSettling]) - so nothing is highlighted or tappable until the dice the scores are for
    // are sitting still, and a tap can never score against dice about to change.
    val rollInHand = rolling || diceSettling
    val canScore = rolled && player?.type == PlayerType.HUMAN && !rollInHand
    val available = player?.let { ScoreCalculator.availableCategories(it, state.dice) }.orEmpty().toSet()

    ScoreBoardRow(
        gameMode = state.gameMode,
        player = player,
        dice = state.dice,
        canScore = canScore,
        showPreview = rolled && !rollInHand,
        available = available,
        onScoreCategory = onScoreCategory,
        cup = CupPanelState(
            rollsRemaining = state.rollsRemaining,
            // Directly from state.phase, not persisted across turns: a new turn resets it to
            // AWAITING_ROLL, and the cup should go back to standing right then, before anyone has
            // rolled - not stay tipped over from the previous player's last roll. What makes THIS
            // roll's shake look the same as a same-turn reroll's isn't keeping this true across
            // the boundary; it's that Cup already forces itself upright the instant a shake
            // starts, whatever `tilted` was beforehand (see rememberCupRotation).
            tilted = state.phase == TurnPhase.ROLLED,
            rolling = rolling,
            canUndo = canUndo,
            onCupTap = onCupTap,
            onUndo = onUndo,
        ),
        modifier = modifier,
    )
}

/**
 * The read-only counterpart to [GameBoard]: another player's scorecard, shown when the active
 * player taps that player's tab in [PlayerHeaderBar]. Same [ScoreBoardRow] as the live board, just
 * with no dice, nothing tappable and `cup = null` - no dice cup, roll counter or undo button, since
 * none of those act on a turn that isn't actually happening. Deliberately doesn't also render
 * [DiceTray]: [PlayerState.lastRoll], when this player has one, is shown by the caller instead
 * (see [InProgressGame] in GameScreen.kt), through the exact same [DiceTray] call the live board
 * uses - so the gap between board and tray, and everything else about how it looks, is identical
 * either way rather than a second copy that can silently drift from the first.
 */
@Composable
fun ReadOnlyScoreboard(player: PlayerState, modifier: Modifier = Modifier) {
    ScoreBoardRow(
        gameMode = player.gameMode,
        player = player,
        dice = emptyList(),
        canScore = false,
        showPreview = false,
        available = emptySet(),
        onScoreCategory = {},
        cup = null,
        modifier = modifier,
    )
}
