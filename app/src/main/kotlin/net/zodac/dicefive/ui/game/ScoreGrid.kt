package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.game.ScoreCalculator
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileIconColor

/** Lower-section categories excluding 5x, which gets its own prominent tile beside the cup. */
private val GRID_LOWER_CATEGORIES = ScoreCategory.entries
    .filterNot { it in PlayerState.UPPER_CATEGORIES || it == ScoreCategory.FIVE_OF_A_KIND }

/**
 * The two-column scorecard grid for the active player only - other players' progress is
 * summarized in the header tabs instead, matching the reference layout. [canScore] and
 * [available] are precomputed once by the caller and shared with the 5x tile beside the cup.
 */
@Composable
fun ScoreGrid(
    player: PlayerState?,
    dice: List<Die>,
    canScore: Boolean,
    available: Set<ScoreCategory>,
    onScoreCategory: (ScoreCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in PlayerState.UPPER_CATEGORIES.indices) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                // Gives the first column's score text (up to 2 digits) clearance before the
                // second column's tile starts - otherwise they visually touch/overlap.
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CategoryCell(
                    category = PlayerState.UPPER_CATEGORIES[row],
                    player = player,
                    canScore = canScore,
                    available = available,
                    dice = dice,
                    onScoreCategory = onScoreCategory,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                CategoryCell(
                    category = GRID_LOWER_CATEGORIES[row],
                    player = player,
                    canScore = canScore,
                    available = available,
                    dice = dice,
                    onScoreCategory = onScoreCategory,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
internal fun CategoryCell(
    category: ScoreCategory,
    player: PlayerState?,
    canScore: Boolean,
    available: Set<ScoreCategory>,
    dice: List<Die>,
    onScoreCategory: (ScoreCategory) -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    val filled = player?.scorecard?.get(category)
    // "Legal to pick" (any open box - the rules let you zero one deliberately) is distinct
    // from "worth picking" (glows gold): only a non-zero preview earns the highlight, so rolling
    // the dice doesn't light up every open box regardless of whether it'd actually score.
    val isLegalChoice = player != null && canScore && category in available
    val previewScore = if (isLegalChoice) ScoreCalculator.scoreFor(player!!, category, dice) else null
    val isGoodChoice = previewScore != null && previewScore > 0
    // Every 5x after the first earns a +100 bonus chip tracked separately from the scorecard
    // entry itself (which stays 50) - see PlayerState.fiveOfAKindBonusCount/Total and
    // ScoreCalculator.awardsFiveOfAKindBonus. Zero for every other category.
    val fiveOfAKindBonusCount = if (category == ScoreCategory.FIVE_OF_A_KIND) player?.fiveOfAKindBonusCount ?: 0 else 0
    // Whether this roll would earn the +100 bonus - unconditional on which category ends up
    // chosen, per the official joker rule (see ScoreCalculator's class doc): a repeat 5x
    // always pays the bonus, it only dictates/restricts which box the roll can go in.
    val bonusThisTurn = player != null && canScore && ScoreCalculator.awardsFiveOfAKindBonus(player, dice)
    // The 5x box itself is never a "legal choice" again once filled (it's not in `available`,
    // so isGoodChoice above is always false for it) - but a repeat 5x still means the bonus
    // will be earned this turn, so without this, rolling one gave no visual sign anything special
    // was about to happen. This preview only ever shows on the 5x tile - not on whichever
    // category the roll ends up scored in - since the bonus is a 5x-box concept, and showing
    // it a second time on the scoring category tile implied it depended on that specific category,
    // when per the official joker rule it doesn't (see ScoreCalculator's class doc).
    val fiveOfAKindTileBonusPreview = category == ScoreCategory.FIVE_OF_A_KIND && bonusThisTurn
    val pendingBonusAmount = if (category == ScoreCategory.FIVE_OF_A_KIND) {
        (player?.fiveOfAKindBonusTotal ?: 0) + if (fiveOfAKindTileBonusPreview) 100 else 0
    } else {
        0
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryTile(
            category = category,
            // Not `|| fiveOfAKindTileBonusPreview`: the 5x tile is never actually pickable again
            // once scored (it isn't a legal choice), so glowing it like an open, scorable box
            // would be misleading - the +score line below is the preview, the tile's look doesn't
            // change.
            highlighted = isGoodChoice,
            prominent = prominent,
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
