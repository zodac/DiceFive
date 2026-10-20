package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxWithConstraints
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.resources.common_bonus_amount
import net.zodac.dicefive.resources.Res
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import net.zodac.dicefive.game.HandCompletion
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
import net.zodac.dicefive.ui.style.FlowerpotGrowth
import net.zodac.dicefive.ui.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.theme.color

/** Inset of the score-grid + cup section's content. Its height is fixed per game mode (see
 * [scoreBoardHeight]), so its two columns line up row-for-row. */
internal val BOARD_PADDING = 14.dp

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
    landscape: Boolean = false,
) {
    val visualTheme = LocalGameVisualTheme.current
    visualTheme.background.Animate()

    val layout = boardLayout(categories)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(if (landscape) landscapeBoardHeight(categories) else gameBoardHeight(categories))
            .clip(RoundedCornerShape(16.dp))
            .background(visualTheme.background.scoreAreaBrush)
            .drawBehind { with(visualTheme.background) { drawScoreAreaDecoration() } }
            .padding(BOARD_PADDING),
    ) {
        if (landscape) {
            CompositionLocalProvider(LocalBonusFontSize provides BONUS_MAX_SIZE) {
                LandscapeBoard(categories, player, dice, canScore, showPreview, available, onScoreCategory, cup, maxWidth)
            }
            return@BoxWithConstraints
        }
        // Two equal halves, not less for the grid: it splits into two columns per row, so per-category it actually has
        // HALF the width the cup panel's single-column content gets - it needs the room more, not less. A joker's bonus is
        // written as big as the tightest box on the board allows, at one size for all of them; where that's the cost of a
        // few dp, the split moves to make room (see [fitBonus]).
        val pane = (maxWidth - BOARD_PANE_GAP) / 2
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val chip = player?.gameMode?.fiveOfAKindBonusAmount ?: 0
        val bonusText = stringResource(Res.string.common_bonus_amount, chip)
        val stacked = (player?.gameMode?.scoresPerCategory ?: 1) > 1
        val scoreStyle = MaterialTheme.typography.labelMedium
        val bonusStyle = MaterialTheme.typography.labelSmall.copy(fontSize = BONUS_MAX_SIZE, fontWeight = FontWeight.Bold)
        val fit = remember(layout, pane, density, bonusText, stacked, scoreStyle, bonusStyle) {
            with(density) {
                val bonusWidth = measurer.measure(bonusText, bonusStyle).size.width.toDp()
                // Third Wind writes it after a two digit score on the same line.
                val reserve = if (stacked) measurer.measure("88", scoreStyle).size.width.toDp() + SLOT_BONUS_GAP else 0.dp
                fitBonus(layout, pane, bonusWidth, reserve)
            }
        }
        val shift = fit.paneShift
        CompositionLocalProvider(LocalBonusFontSize provides fit.fontSize) {
            // D20's card: four columns, with the cup centred under the inner two.
            if (layout.columns != null) {
                ColumnBoard(layout, player, dice, canScore, showPreview, available, onScoreCategory, cup, pane, BOARD_PANE_GAP)
            } else Row(modifier = Modifier.fillMaxSize()) {
                ScoreGrid(
                    categories = categories,
                    player = player,
                    dice = dice,
                    canScore = canScore,
                    showPreview = showPreview,
                    available = available,
                    onScoreCategory = onScoreCategory,
                    modifier = Modifier.width(pane + shift),
                )
                // Wider than a purely decorative gap needs to be: a 2-digit score in the grid's rightmost
                // column can render past its own column's edge (see the Visible-overflow comment on that
                // Text in ScoreGrid.kt) - this gap doubles as the dead space that spillover lands in
                // harmlessly, before it would otherwise reach the cup panel's content.
                Spacer(modifier = Modifier.width(BOARD_PANE_GAP))
                DiceCupPanel(
                    categories = categories,
                    player = player,
                    dice = dice,
                    canScore = canScore,
                    showPreview = showPreview,
                    available = available,
                    onScoreCategory = onScoreCategory,
                    cup = cup,
                    modifier = Modifier.width(pane - shift),
                )
            }
        }
    }
}

/** The gap between the score grid and the cup panel. */
private val BOARD_PANE_GAP = 20.dp

/** The landscape board's columns. */
private const val LANDSCAPE_COLUMNS = 6

/** The columns the featured box spans on the landscape board, and the buttons beside it. */
private const val LANDSCAPE_SPAN = 2

/**
 * Where every box of a card sits on the landscape board: each section in card order, across [rows] of up to
 * [LANDSCAPE_COLUMNS], then a last row with [beforeFeatured] at its left, the [featured] box, and Totals and Undo across the
 * last two columns. The featured box is a wide tile across the middle two columns, or a single tile in the fourth where three
 * boxes come before it.
 *
 * A card with no featured box puts the buttons at the right of its last row where there's room. Otherwise each goes in the
 * spare last column of a row, Totals in the earlier, or failing that both in a row of their own.
 */
internal class LandscapeLayout(
    val rows: List<List<ScoreCategory>>,
    val featured: ScoreCategory?,
    val beforeFeatured: List<ScoreCategory> = emptyList(),
) {
    /** Whether the featured box is a single tile, not a wide one. */
    val featuredNarrow: Boolean
        get() = beforeFeatured.size > LANDSCAPE_SPAN

    /** The rows the buttons sit at the right of: one for both, or two - Totals in the first, Undo in the second. */
    val buttonRows: List<Int>
        get() {
            if (featured != null || rows.isEmpty()) return listOf(rows.size)
            if (rows.last().size <= LANDSCAPE_COLUMNS - LANDSCAPE_SPAN) return listOf(rows.lastIndex)
            val spare = rows.indices.filter { rows[it].size < LANDSCAPE_COLUMNS }
            return if (spare.size >= 2) spare.takeLast(2) else listOf(rows.size)
        }

    val rowCount: Int
        get() = if (rows.size in buttonRows) rows.size + 1 else rows.size
}

/**
 * How a card of [categories] is laid out on a landscape screen - see [LandscapeLayout]. Each section starts a row, but for a
 * group of boxes that fits whole in the row before it: Extended Scores' Evens and Odds, or its Two Pair (grouped as under 5x
 * on a phone, [EXTENDED_SIDE_ROWS]). A last row of no more than three boxes goes before the featured box.
 */
internal fun landscapeLayout(categories: List<ScoreCategory>): LandscapeLayout {
    val featured = categories.firstOrNull { it.featured }
    val groups = categories.filter { it != featured }.groupBy { it.section }.values.flatMap { section ->
        EXTENDED_SIDE_ROWS.map { row -> row.filter { it in section } }.filter { it.isNotEmpty() }.ifEmpty { section.chunked(LANDSCAPE_COLUMNS) }
    }
    val rows = mutableListOf<List<ScoreCategory>>()
    for (group in groups) {
        val last = rows.lastOrNull()
        if (last != null && last.size + group.size <= LANDSCAPE_COLUMNS) rows[rows.lastIndex] = last + group else rows += group
    }
    if (featured != null && rows.size > 1 && rows.last().size < LANDSCAPE_COLUMNS - LANDSCAPE_SPAN) {
        return LandscapeLayout(rows.dropLast(1), featured, rows.last())
    }
    return LandscapeLayout(rows, featured)
}

/** How tall the landscape board is for a card of [categories]: a row of tiles per row of the layout, padding and all. */
internal fun landscapeBoardHeight(categories: List<ScoreCategory>): Dp {
    val rows = landscapeLayout(categories).rowCount
    return REGULAR_TILE_SIZE * rows + GRID_ROW_SPACING * (rows - 1) + BOARD_PADDING * 2
}

/**
 * The board laid out for a landscape screen (see [LandscapeLayout]), in six columns of [width]. No cup: it's beside the dice
 * tray (see [GameCup]).
 */
@Composable
private fun LandscapeBoard(
    categories: List<ScoreCategory>,
    player: PlayerState?,
    dice: List<Die>,
    canScore: Boolean,
    showPreview: Boolean,
    available: Set<ScoreCategory>,
    onScoreCategory: (ScoreCategory) -> Unit,
    cup: CupPanelState?,
    width: Dp,
) {
    val layout = landscapeLayout(categories)
    val column = (width - COLUMN_GAP * (LANDSCAPE_COLUMNS - 1)) / LANDSCAPE_COLUMNS
    val span = column * LANDSCAPE_SPAN + COLUMN_GAP * (LANDSCAPE_SPAN - 1)
    val lastColumn = Modifier.offset(x = (column + COLUMN_GAP) * (LANDSCAPE_COLUMNS - 1)).width(column).fillMaxHeight()
    @Composable
    fun cell(category: ScoreCategory, index: Int, wide: Boolean = false) = CategoryCell(
        category = category,
        player = player,
        canScore = canScore,
        showPreview = showPreview,
        available = available,
        dice = dice,
        onScoreCategory = onScoreCategory,
        wide = wide,
        modifier = Modifier.offset(x = (column + COLUMN_GAP) * index).width(if (wide) span else column).fillMaxHeight(),
    )
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GRID_ROW_SPACING)) {
        for (rowIndex in 0 until layout.rowCount) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                (layout.rows.getOrNull(rowIndex) ?: layout.beforeFeatured).forEachIndexed { index, category -> cell(category, index) }
                if (rowIndex == layout.rows.size) {
                    layout.featured?.let { featured ->
                        if (layout.featuredNarrow) cell(featured, layout.beforeFeatured.size) else cell(featured, (LANDSCAPE_COLUMNS - LANDSCAPE_SPAN) / 2, wide = true)
                    }
                }
                val buttonRows = layout.buttonRows
                when {
                    buttonRows.size == 1 && rowIndex == buttonRows[0] ->
                        PanelButtons(boardLayout(categories), player, cup, REGULAR_GRID_ROWS, Modifier.align(Alignment.CenterEnd).width(span).fillMaxHeight())

                    buttonRows.size > 1 && rowIndex == buttonRows[0] -> Box(modifier = lastColumn, contentAlignment = Alignment.CenterStart) {
                        PanelButtons(boardLayout(categories), player, cup?.copy(showUndo = false), REGULAR_GRID_ROWS)
                    }

                    buttonRows.size > 1 && rowIndex == buttonRows[1] && cup?.showUndo == true -> Box(modifier = lastColumn, contentAlignment = Alignment.CenterStart) {
                        UndoButton(enabled = cup.canUndo, onClick = cup.onUndo, minSize = REGULAR_TILE_SIZE)
                    }
                }
            }
        }
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
    // The board laid out for a landscape screen (see LandscapeBoard), its cup drawn beside the dice tray.
    landscape: Boolean = false,
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
    // Where only held dice score (GameMode.scoresHeldDiceOnly), a CPU's board reads the dice it has held. A human's
    // reads them completed: every box previews the best hand the held dice can make in it, and any of them can be
    // tapped - the rest of the hand is chosen for the player (see HandCompletion).
    val rollInHand = rolling || diceSettling
    val hand = state.scoringDice
    val projected = remember(state, rollInHand) {
        if (rolled && !rollInHand && !state.hasFullHand && player?.type == PlayerType.HUMAN) HandCompletion.projectedHands(state) else emptyMap()
    }
    val completable = projected.isNotEmpty()
    val canScore = rolled && player?.type == PlayerType.HUMAN && !rollInHand && (state.hasFullHand || completable)
    val available = if (completable) projected.keys else player?.let { ScoreCalculator.availableCategories(it, hand) }.orEmpty().toSet()

    // The 5x tile flashes gold the first time each player's dice settle on a 5x they can score as one.
    val showPreview = rolled && !rollInHand && (hand.isNotEmpty() || completable)
    val fiveOfAKindShowing = showPreview && ScoreCategory.FIVE_OF_A_KIND in available && DiceScoring.isFiveOfAKind(hand)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var flashFiveOfAKind by remember { mutableStateOf(false) }
    LaunchedEffect(fiveOfAKindShowing, state.currentPlayerIndex) {
        if (fiveOfAKindShowing && claimFiveOfAKindFlash(state.currentPlayerIndex)) {
            flashFiveOfAKind = true
            // finally: scoring before the hold ends cancels this effect, which must still switch the flash off.
            try {
                lifecycle.delayWhileResumed(FIVE_OF_A_KIND_FLASH_HOLD_MILLIS)
            } finally {
                flashFiveOfAKind = false
            }
        }
    }

    CompositionLocalProvider(LocalFiveOfAKindFlash provides flashFiveOfAKind, LocalProjectedHands provides projected) {
        ScoreBoardRow(
            categories = state.categories,
            player = player,
            dice = hand,
            canScore = canScore,
            showPreview = showPreview,
            available = available,
            onScoreCategory = onScoreCategory,
            cup = cupPanelState(state, rolling, pouring, rollInHand, canUndo, onCupTap, onUndo),
            landscape = landscape,
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
        turnKey = state.scoresEntered,
        flowerpotGrowth = FlowerpotGrowth(
            stage = player?.flowerpotStage ?: 0,
            grower = state.currentPlayerIndex,
        ),
    )
}

/**
 * The live turn's dice cup on its own, for the landscape layout: the board above without it ([GameBoard]'s
 * `landscape`), and this beside the dice tray on the right, so the cup is still on the right-hand side.
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
fun ReadOnlyScoreboard(player: PlayerState, modifier: Modifier = Modifier, landscape: Boolean = false) {
    // The box this player's last turn went in, picked out in their colour - the scorecard on its own
    // shows what they've scored, but not which of it was their last turn (see LastScoredHighlight).
    // Their colour is the one picked at setup, through the same PlayerColour.color their tab uses, so the two always match.
    val lastScored = player.lastScoredCategory?.let { LastScoredHighlight(it, player.colour.color) }
    CompositionLocalProvider(LocalLastScoredHighlight provides lastScored) {
        ReadOnlyScoreboardRow(player, modifier, landscape)
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
private fun ReadOnlyScoreboardRow(player: PlayerState, modifier: Modifier, landscape: Boolean) {
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
        landscape = landscape,
    )
}
