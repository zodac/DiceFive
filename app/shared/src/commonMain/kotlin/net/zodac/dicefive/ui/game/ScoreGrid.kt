package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.game.ScoreCalculator
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection
import net.zodac.dicefive.ui.game.style.LocalIrishTricolour
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileIconColor

/** Standard's grid: six rows, upper section beside lower. More than this and the tiles go compact. */
private const val REGULAR_GRID_ROWS = 6
private val GRID_ROW_SPACING = 6.dp

/** The board's height for a Standard-sized grid - see [scoreBoardHeight]. */
private val REGULAR_BOARD_HEIGHT = 380.dp

/**
 * The grid's rows for [gameMode], top to bottom, each one or two categories wide: the upper section
 * down the left column beside the lower section, then anything left over (Tricolour's colour boxes)
 * two to a row underneath. 5x is left out - it has its own prominent tile beside the cup.
 */
internal fun scoreGridRows(gameMode: GameMode): List<List<ScoreCategory>> {
    val gridCategories = gameMode.categories.filter { it != ScoreCategory.FIVE_OF_A_KIND }
    val upper = gridCategories.filter { it.section == ScoreSection.UPPER }
    val others = gridCategories - upper.toSet()
    val sideBySide = upper.zip(others) { left, right -> listOf(left, right) }
    val leftOver = upper.drop(others.size) + others.drop(upper.size)
    return sideBySide + leftOver.chunked(2)
}

/**
 * How tall the scoring area is for [gameMode]'s grid. Standard's six rows keep the board's original
 * height; more rows than that switch to compact tiles (see [CategoryTile]) and grow the board just
 * enough to fit one per row: [COMPACT_TILE_SIZE] plus the row gap each, inside [padding] top and
 * bottom. At Tricolour's eight rows that's 396dp, only 16dp taller than Standard.
 */
internal fun scoreBoardHeight(gameMode: GameMode, padding: Dp): Dp {
    val rows = scoreGridRows(gameMode).size
    if (rows <= REGULAR_GRID_ROWS) return REGULAR_BOARD_HEIGHT
    return maxOf(REGULAR_BOARD_HEIGHT, (COMPACT_TILE_SIZE + GRID_ROW_SPACING) * rows + padding * 2)
}

/** The grid's tile size for [rowCount] rows - regular for Standard's six, compact beyond that. */
private fun gridTileSize(rowCount: Int): Dp = if (rowCount > REGULAR_GRID_ROWS) COMPACT_TILE_SIZE else REGULAR_TILE_SIZE

/**
 * How far below the top of [gameMode]'s [gridHeight]-tall grid its first row's tiles sit: the rows
 * share the height equally and centre their tile vertically in it. The 5x tile beside the grid uses
 * this to put its top level with Ones and 3x in every mode, rather than a hand-tuned nudge that would
 * only suit one mode's row count and tile size.
 */
internal fun firstRowTileInset(gameMode: GameMode, gridHeight: Dp): Dp {
    val rows = scoreGridRows(gameMode).size
    val rowHeight = (gridHeight - GRID_ROW_SPACING * (rows - 1)) / rows
    return ((rowHeight - gridTileSize(rows)) / 2).coerceAtLeast(0.dp)
}

/**
 * The two-column scorecard grid for the active player only - other players' progress is
 * summarized in the header tabs instead, matching the reference layout. [canScore] and
 * [available] are precomputed once by the caller and shared with the 5x tile beside the cup.
 * Which boxes it shows, and how they're laid out, come from [gameMode] (see [scoreGridRows]).
 */
@Composable
fun ScoreGrid(
    gameMode: GameMode,
    player: PlayerState?,
    dice: List<Die>,
    canScore: Boolean,
    showPreview: Boolean,
    available: Set<ScoreCategory>,
    onScoreCategory: (ScoreCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = scoreGridRows(gameMode)
    val compact = gridTileSize(rows.size) == COMPACT_TILE_SIZE
    Column(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(GRID_ROW_SPACING)) {
        for (row in rows) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                // Gives the first column's score text (up to 2 digits) clearance before the
                // second column's tile starts - otherwise they visually touch/overlap.
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                for (category in row) {
                    CategoryCell(
                        category = category,
                        player = player,
                        canScore = canScore,
                        showPreview = showPreview,
                        available = available,
                        dice = dice,
                        onScoreCategory = onScoreCategory,
                        compact = compact,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                // A lone category on the last row keeps to the left column's width.
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun CategoryCell(
    category: ScoreCategory,
    player: PlayerState?,
    canScore: Boolean,
    showPreview: Boolean,
    available: Set<ScoreCategory>,
    dice: List<Die>,
    onScoreCategory: (ScoreCategory) -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    compact: Boolean = false,
) {
    val filled = player?.scorecard?.get(category)
    // Legal-to-tap (canScore, human-only) and legal-to-preview (showPreview, any player whose
    // dice have actually been rolled) are deliberately separate: tapping a box to score it only
    // ever makes sense for the human at the controls, but the gold "worth picking" glow and the
    // number preview are just information about the dice that already landed - an AI's own roll
    // is exactly as previewable as a human's, it's just never the human tapping it in.
    val isLegalChoice = player != null && canScore && category in available
    val canPreview = player != null && showPreview && category in available
    val previewScore = if (canPreview) ScoreCalculator.scoreFor(player, category, dice) else null
    val isGoodChoice = previewScore != null && previewScore > 0
    // Every 5x after the first earns a +100 bonus chip tracked separately from the scorecard
    // entry itself (which stays 50) - see PlayerState.fiveOfAKindBonusCount/Total and
    // ScoreCalculator.awardsFiveOfAKindBonus. Zero for every other category.
    val fiveOfAKindBonusCount = if (category == ScoreCategory.FIVE_OF_A_KIND) player?.fiveOfAKindBonusCount ?: 0 else 0
    // Whether this roll would earn the +100 bonus - unconditional on which category ends up
    // chosen, per the official joker rule (see ScoreCalculator's class doc): a repeat 5x
    // always pays the bonus, it only dictates/restricts which box the roll can go in.
    val bonusThisTurn = player != null && showPreview && ScoreCalculator.awardsFiveOfAKindBonus(player, dice)
    // The 5x box itself is never a "legal choice" again once filled (it's not in `available`,
    // so isGoodChoice above is always false for it) - but a repeat 5x still means the bonus
    // will be earned this turn, so without this, rolling one gave no visual sign anything special
    // was about to happen. This preview only ever shows on the 5x tile - not on whichever
    // category the roll ends up scored in - since the bonus is a 5x-box concept, and showing
    // it a second time on the scoring category tile implied it depended on that specific category,
    // when per the official joker rule it doesn't (see ScoreCalculator's class doc).
    val fiveOfAKindTileBonusPreview = category == ScoreCategory.FIVE_OF_A_KIND && bonusThisTurn
    val pendingBonusAmount = if (category == ScoreCategory.FIVE_OF_A_KIND) {
        (player?.fiveOfAKindBonusTotal ?: 0) + if (fiveOfAKindTileBonusPreview) player.gameMode.fiveOfAKindBonusAmount else 0
    } else {
        0
    }

    // One node for a screen reader - the tile's name and the score beside it together, rather than an
    // unlabelled button and a stray number - replacing the tile's own click with the same action.
    val irish = LocalIrishTricolour.current
    val spokenState = when {
        filled != null -> buildString {
            append("Scored $filled")
            if (pendingBonusAmount > 0) append(", plus $pendingBonusAmount bonus")
        }
        previewScore != null -> buildString {
            append("Would score $previewScore")
            if (fiveOfAKindTileBonusPreview) append(", plus $pendingBonusAmount bonus")
        }
        else -> "Open"
    }
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = category.spokenName(irish)
            stateDescription = spokenState
            if (isLegalChoice) {
                role = Role.Button
                onClick(label = "Score") {
                    onScoreCategory(category)
                    true
                }
            }
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryTile(
            category = category,
            // Not `|| fiveOfAKindTileBonusPreview`: the 5x tile is never actually pickable again
            // once scored (it isn't a legal choice), so glowing it like an open, scorable box
            // would be misleading - the +score line below is the preview, the tile's look doesn't
            // change.
            highlighted = isGoodChoice,
            prominent = prominent,
            compact = compact,
            scored = filled != null,
            fiveOfAKindBonusCount = fiveOfAKindBonusCount,
            onClick = if (isLegalChoice) { { onScoreCategory(category) } } else null,
        )
        if (fiveOfAKindBonusCount > 0 || fiveOfAKindTileBonusPreview) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = (filled ?: previewScore ?: 0).toString(),
                    color = if (isGoodChoice) GoldAccent else TileIconColor,
                    fontWeight = if (isGoodChoice) FontWeight.Bold else FontWeight.Normal,
                    style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                )
                Text(
                    // The total bonus on the 5x tile, not one line per extra 5x - ten of
                    // them is still just one "+900" line, not ten "+100"s.
                    text = "+$pendingBonusAmount",
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                    // Smaller than the 5x tile's own bonus line for a regular (non-prominent)
                    // category cell - those rows are much shorter, with far less vertical room to
                    // spare for a second line than the big prominent 5x tile has.
                    style = if (prominent) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                )
            }
        } else {
            val text = filled?.toString() ?: previewScore?.toString() ?: "-"
            Text(
                text = text,
                color = if (isGoodChoice) GoldAccent else TileIconColor.copy(alpha = if (filled != null) 1f else 0.55f),
                fontWeight = if (isGoodChoice) FontWeight.Bold else FontWeight.Normal,
                style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                // The weighted width here is razor-thin by design (see the Row's spacedBy comment
                // above) - just enough for a single digit. Clip was hard-cropping the second digit
                // of any score above 9 (Fives, Chance, ...); Visible lets it spill into that
                // reserved gap instead of being cut off.
                overflow = TextOverflow.Visible,
                softWrap = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
