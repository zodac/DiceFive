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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.zodac.dicefive.game.DiceScoring
import net.zodac.dicefive.game.ScoreCalculator
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.flowerpotStage
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.game.style.FlowerpotGrowth
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.theme.playerColor

/** Inset of the score-grid + cup section's content. Its height is fixed per game mode (see
 * [scoreBoardHeight]), so its two columns line up row-for-row. */
private val BOARD_PADDING = 14.dp

/** How tall the score board is for a card of [categories], padding and all - see [scoreBoardHeight]. */
internal fun gameBoardHeight(categories: List<ScoreCategory>): Dp = scoreBoardHeight(categories, BOARD_PADDING)

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
    categories: List<ScoreCategory>,
    player: PlayerState?,
    dice: List<Die>,
    canScore: Boolean,
    showPreview: Boolean,
    available: Set<ScoreCategory>,
    onScoreCategory: (ScoreCategory) -> Unit,
    cup: CupPanelState?,
    modifier: Modifier = Modifier,
    showCup: Boolean = true,
) {
    val visualTheme = LocalGameVisualTheme.current
    visualTheme.background.Animate()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(gameBoardHeight(categories))
            .clip(RoundedCornerShape(16.dp))
            .background(visualTheme.background.scoreAreaBrush)
            .drawBehind { with(visualTheme.background) { drawScoreAreaDecoration() } }
            .padding(BOARD_PADDING),
    ) {
        ScoreGrid(
            categories = categories,
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
            categories = categories,
            player = player,
            dice = dice,
            canScore = canScore,
            showPreview = showPreview,
            available = available,
            onScoreCategory = onScoreCategory,
            cup = cup,
            showCup = showCup,
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
    pouring: Boolean = false,
    diceSettling: Boolean = false,
    // False where the game is laid out side by side: the cup is drawn over the dice tray instead (GameCup).
    showCup: Boolean = true,
    // Whether a seat's first 5x of the game is still to be flashed: true once per seat, see GameViewModel.claimFiveOfAKindFlash.
    claimFiveOfAKindFlash: (Int) -> Boolean = { false },
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
    //
    // Where only held dice score (GameMode.scoresHeldDiceOnly), the board reads the held hand: it
    // previews what those dice would score as soon as one is held, but a box can only be tapped once
    // every hold slot is filled.
    val rollInHand = rolling || diceSettling
    val hand = state.scoringDice
    val canScore = rolled && player?.type == PlayerType.HUMAN && !rollInHand && state.hasFullHand
    val available = player?.let { ScoreCalculator.availableCategories(it, hand) }.orEmpty().toSet()

    // The 5x tile flashes gold the first time each player's dice settle on a 5x they can score as one.
    val showPreview = rolled && !rollInHand && hand.isNotEmpty()
    val fiveOfAKindShowing = showPreview && ScoreCategory.FIVE_OF_A_KIND in available && DiceScoring.isFiveOfAKind(hand)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var flashFiveOfAKind by remember { mutableStateOf(false) }
    LaunchedEffect(fiveOfAKindShowing, state.currentPlayerIndex) {
        if (fiveOfAKindShowing && claimFiveOfAKindFlash(state.currentPlayerIndex)) {
            flashFiveOfAKind = true
            lifecycle.delayWhileResumed(FIVE_OF_A_KIND_FLASH_HOLD_MILLIS)
            flashFiveOfAKind = false
        }
    }

    CompositionLocalProvider(LocalFiveOfAKindFlash provides flashFiveOfAKind) {
        ScoreBoardRow(
            categories = state.categories,
            player = player,
            dice = hand,
            canScore = canScore,
            showPreview = showPreview,
            available = available,
            onScoreCategory = onScoreCategory,
            cup = cupPanelState(state, rolling, pouring, rollInHand, canUndo, onCupTap, onUndo),
            showCup = showCup,
            modifier = modifier,
        )
    }
}

/** How long the 5x tile stays solid gold before fading back - the same hold as an achievement row's flash. */
private const val FIVE_OF_A_KIND_FLASH_HOLD_MILLIS = 900L

/** Whether the 5x tile is flashing gold for a player's first 5x - provided by [GameBoard] for [CategoryCell] to find. */
internal val LocalFiveOfAKindFlash = compositionLocalOf { false }

/** The cup's part of [state]'s turn, the same whether it's drawn in the board or beside it ([GameCup]). */
private fun cupPanelState(
    state: GameState,
    rolling: Boolean,
    pouring: Boolean,
    rollInHand: Boolean,
    canUndo: Boolean,
    onCupTap: () -> Unit,
    onUndo: () -> Unit,
): CupPanelState {
    val player = state.currentPlayer
    return CupPanelState(
        rollsRemaining = state.rollsRemaining,
        // Directly from state.phase, not persisted across turns: a new turn resets it to
        // AWAITING_ROLL, and the cup should go back to standing right then, before anyone has
        // rolled - not stay tipped over from the previous player's last roll. What makes THIS
        // roll's shake look the same as a same-turn reroll's isn't keeping this true across
        // the boundary; it's that Cup already forces itself upright the instant a shake
        // starts, whatever `tilted` was beforehand (see rememberCupRotation).
        tilted = state.phase == TurnPhase.ROLLED,
        rolling = rolling,
        pouring = pouring,
        rollInHand = rollInHand,
        canUndo = canUndo,
        // Undo is solo-only: with other players it either reaches back into their finished turn
        // or is cleared by the AI's move almost at once.
        showUndo = state.players.size == 1,
        onCupTap = onCupTap,
        onUndo = onUndo,
        flowerpotGrowth = FlowerpotGrowth(
            stage = player?.flowerpotStage ?: 0,
            grower = state.currentPlayerIndex,
        ),
    )
}

/**
 * The live turn's dice cup on its own, for where the game is laid out side by side: the board on the
 * left without it ([GameBoard]'s `showCup = false`), and this over the dice tray on the right, so the
 * cup is still on the right-hand side and the dice pour out under it.
 */
@Composable
fun GameCup(
    state: GameState,
    rolling: Boolean,
    canUndo: Boolean,
    onCupTap: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    pouring: Boolean = false,
    diceSettling: Boolean = false,
) {
    DiceCup(
        cup = cupPanelState(state, rolling, pouring, rolling || diceSettling, canUndo, onCupTap, onUndo),
        dice = state.scoringDice,
        modifier = modifier,
        countFirst = true,
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
fun ReadOnlyScoreboard(player: PlayerState, seat: Int, modifier: Modifier = Modifier) {
    // The box this player's last turn went in, picked out in their colour - the scorecard on its own
    // shows what they've scored, but not which of it was their last turn (see LastScoredHighlight).
    // Their colour from their seat, through the same playerColor their tab uses, so the two always match.
    val lastScored = player.lastScoredCategory?.let { LastScoredHighlight(it, playerColor(seat)) }
    CompositionLocalProvider(LocalLastScoredHighlight provides lastScored) {
        ReadOnlyScoreboardRow(player, modifier)
    }
}

/**
 * Which box a read-only scorecard's player last scored in, and their colour to mark it in - provided by
 * [ReadOnlyScoreboard] for [CategoryCell] to find, rather than threaded through every layer between.
 * Null on the live board, which marks nothing as a last score.
 */
internal class LastScoredHighlight(val category: ScoreCategory, val color: Color)

internal val LocalLastScoredHighlight = staticCompositionLocalOf<LastScoredHighlight?> { null }

@Composable
private fun ReadOnlyScoreboardRow(player: PlayerState, modifier: Modifier) {
    ScoreBoardRow(
        categories = player.categories,
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
