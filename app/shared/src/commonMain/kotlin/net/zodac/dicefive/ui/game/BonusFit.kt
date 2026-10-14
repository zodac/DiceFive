package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_bonus_amount
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.theme.GoldAccent

/**
 * The font size every joker bonus on the board is written at, set by [ScoreBoardRow] from [fitBonus]; unspecified where even
 * [BONUS_MIN_SIZE] won't fit, when every bonus is a bare "+" (the amount is spoken either way). One size for the whole board, so a
 * screen never shows two.
 */
internal val LocalBonusFontSize = compositionLocalOf { TextUnit.Unspecified }

/** The largest a joker bonus is written: `labelSmall`'s size. */
internal val BONUS_MAX_SIZE = 11.sp

/** The smallest: a highlight beside a score, not text to read at length. Below it the board writes a bare "+". */
internal val BONUS_MIN_SIZE = 8.sp

/** Kept clear between a bonus that spills past its column and what's beside it. */
private val BONUS_MARGIN = 2.dp

/** Kept clear between a bonus in the cup panel's last column and the board's edge, which its padding is the room for. */
private val BOARD_EDGE_MARGIN = 4.dp

/** The most the score grid and the cup panel trade width to make room, either way, where there are boxes under 5x. */
private val SIDE_ROWS_MAX_SHIFT = 12.dp

/** The most the cup panel gives the grid where the large 5x tile has room to spare. */
private val MAX_PANE_SHIFT = 16.dp

/** How the board is divided, and how big a joker's bonus is written on it: see [fitBonus]. */
internal class BonusFit(
    /** What the cup panel gives the score grid (taking it from the grid, when negative). */
    val paneShift: Dp,
    /** The size of every bonus on the board, or unspecified for a bare "+". */
    val fontSize: TextUnit,
)

/**
 * Divides a board of [layout], its panes each [pane] wide, and sizes its bonus text, so the bonus is as big as the tightest box
 * allows: a bonus is written under (or, in Third Wind, after) its score and may spill past its column - into the gap before the next
 * column's tile, or in the cup panel's last column into the board's padding.
 *
 * - [bonusWidth] is how wide "+100" is at [BONUS_MAX_SIZE]; [reserve] what a score takes before it on its line (Third Wind's), else 0.
 * - The one size is the room in the tightest column over [bonusWidth], at most [BONUS_MAX_SIZE]; under [BONUS_MIN_SIZE] it is unspecified.
 * - Where boxes sit under 5x (their tile is the pane's width) the split moves up to [SIDE_ROWS_MAX_SHIFT] either way to even
 *   the grid's room against the cup panel's. With nothing under it the cup panel gives the grid what's spare beside the large
 *   5x tile (never shrinking it), up to [MAX_PANE_SHIFT]. Any other card keeps equal panes.
 */
internal fun fitBonus(layout: BoardLayout, pane: Dp, bonusWidth: Dp, reserve: Dp): BonusFit {
    val tile = gridTileSize(layout.rowCount)
    val spare = (pane - FEATURED_MAX_WIDTH - LARGE_FIVE_SCORE_ROOM).coerceIn(0.dp, MAX_PANE_SHIFT)
    val shifts = when {
        layout.featured != ScoreCategory.FIVE_OF_A_KIND -> listOf(0.dp)
        layout.sideRows.isNotEmpty() -> (0..SIDE_ROWS_MAX_SHIFT.value.toInt()).flatMap { listOf(it.dp, -it.dp) }.distinct()
        else -> listOf(spare)
    }
    // The room for a bonus in a column of a pane of width [width]: what's left of the column after its tile and the gap to its score,
    // plus what it may spill into.
    fun room(width: Dp, spill: Dp): Dp = (width - COLUMN_GAP) / 2 - tile - TILE_SCORE_GAP + spill - BONUS_MARGIN - reserve
    fun tightest(shift: Dp): Dp {
        val grid = room(pane + shift, COLUMN_GAP)
        if (layout.sideRows.isEmpty() || layout.featured != ScoreCategory.FIVE_OF_A_KIND) return grid
        val panel = room(pane - shift, BOARD_PADDING - BOARD_EDGE_MARGIN)
        return if (panel < grid) panel else grid
    }
    // The first of the shifts, nearest none, with the most room.
    val best = shifts.fold(shifts.first()) { chosen, candidate -> if (tightest(candidate) > tightest(chosen)) candidate else chosen }
    val size = BONUS_MAX_SIZE * (tightest(best) / bonusWidth).coerceAtMost(1f)
    return BonusFit(best, if (size >= BONUS_MIN_SIZE) size else TextUnit.Unspecified)
}

/** A joker's bonus of [amount], at the board's [LocalBonusFontSize] - or a bare "+" where it has none. */
@Composable
internal fun BonusText(amount: Int, modifier: Modifier = Modifier) {
    val size = LocalBonusFontSize.current
    val style = MaterialTheme.typography.labelSmall
    Text(
        text = if (size == TextUnit.Unspecified) "+" else stringResource(Res.string.common_bonus_amount, amount),
        color = GoldAccent,
        fontWeight = FontWeight.Bold,
        style = if (size == TextUnit.Unspecified) style else style.copy(fontSize = size),
        maxLines = 1,
        overflow = TextOverflow.Visible,
        softWrap = false,
        modifier = modifier,
    )
}
