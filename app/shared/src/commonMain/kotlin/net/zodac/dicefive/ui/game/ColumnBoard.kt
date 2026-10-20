package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory

/**
 * A board laid out by [BoardLayout.columns] (D20's): four columns of boxes, each as wide as a column of the two
 * panes beside each other, with [paneGap] between the second and third. The dice cup and the buttons are centred
 * under the inner two, from the first row they are both empty: the cup over every row but the last, the buttons in
 * it. Each column is read on its own, top to bottom, so TalkBack takes a half's numbers in order.
 */
@Composable
internal fun ColumnBoard(
    layout: BoardLayout,
    player: PlayerState?,
    dice: List<Die>,
    canScore: Boolean,
    showPreview: Boolean,
    available: Set<ScoreCategory>,
    onScoreCategory: (ScoreCategory) -> Unit,
    cup: CupPanelState?,
    pane: Dp,
    paneGap: Dp,
    modifier: Modifier = Modifier,
) {
    val columns = layout.columns ?: return
    val rows = layout.rowCount
    val compact = gridTileSize(rows) == COMPACT_TILE_SIZE
    val column = (pane - COLUMN_GAP) / 2
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val rowHeight = (maxHeight - GRID_ROW_SPACING * (rows - 1)) / rows
        Row(modifier = Modifier.fillMaxSize()) {
            columns.forEachIndexed { index, boxes ->
                if (index > 0) Spacer(modifier = Modifier.width(if (index == columns.size / 2) paneGap else COLUMN_GAP))
                Column(
                    modifier = Modifier.width(column).fillMaxHeight().semantics { isTraversalGroup = true },
                    verticalArrangement = Arrangement.spacedBy(GRID_ROW_SPACING),
                ) {
                    for (row in 0 until rows) {
                        val category = boxes.getOrNull(row)
                        if (category == null) {
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            CategoryCell(
                                category = category,
                                player = player,
                                canScore = canScore,
                                showPreview = showPreview,
                                available = available,
                                dice = dice,
                                onScoreCategory = onScoreCategory,
                                compact = compact,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        val cupFirstRow = layout.cupFirstRow
        val cupRows = rows - 1 - cupFirstRow
        Column(
            modifier = Modifier
                .offset(x = column + COLUMN_GAP, y = (rowHeight + GRID_ROW_SPACING) * cupFirstRow)
                .width(column * 2 + paneGap)
                .semantics { isTraversalGroup = true },
            verticalArrangement = Arrangement.spacedBy(GRID_ROW_SPACING),
        ) {
            // Kept with no cup to draw (cup == null), so the buttons stay where they are.
            DiceCup(
                cup = cup,
                dice = dice,
                modifier = Modifier.fillMaxWidth().height(rowHeight * cupRows + GRID_ROW_SPACING * (cupRows - 1)),
            )
            PanelButtons(layout, player, cup, rows, Modifier.fillMaxWidth().height(rowHeight), centred = true)
        }
    }
}
