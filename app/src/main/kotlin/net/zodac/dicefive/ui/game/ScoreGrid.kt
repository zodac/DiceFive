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

/** Lower-section categories excluding Yahtzee, which gets its own prominent tile beside the cup. */
private val GRID_LOWER_CATEGORIES = ScoreCategory.entries
    .filterNot { it in PlayerState.UPPER_CATEGORIES || it == ScoreCategory.YAHTZEE }

/**
 * The two-column scorecard grid for the active player only - other players' progress is
 * summarized in the header tabs instead, matching the reference layout. [canScore] and
 * [available] are precomputed once by the caller and shared with the Yahtzee tile beside the cup.
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
    // "Legal to pick" (any open box - Yahtzee rules let you zero one deliberately) is distinct
    // from "worth picking" (glows gold): only a non-zero preview earns the highlight, so rolling
    // the dice doesn't light up every open box regardless of whether it'd actually score.
    val isLegalChoice = player != null && canScore && category in available
    val previewScore = if (isLegalChoice) ScoreCalculator.scoreFor(player!!, category, dice) else null
    val isGoodChoice = previewScore != null && previewScore > 0
    // Every Yahtzee after the first earns a +100 bonus chip tracked separately from the scorecard
    // entry itself (which stays 50) - see PlayerState.yahtzeeBonusCount/Total and
    // ScoreCalculator.awardsYahtzeeBonus. Zero for every other category.
    val yahtzeeBonusCount = if (category == ScoreCategory.YAHTZEE) player?.yahtzeeBonusCount ?: 0 else 0
    // The Yahtzee box itself is never a "legal choice" again once filled (it's not in `available`,
    // so isGoodChoice above is always false for it) - but the +100 bonus applies automatically to
    // whichever OTHER category gets committed this turn, so without this, rolling a second (or
    // later) Yahtzee gave no visual sign anything special was about to happen.
    val awardsBonusPreview = category == ScoreCategory.YAHTZEE && canScore && player != null &&
        ScoreCalculator.awardsYahtzeeBonus(player, dice)
    val pendingBonusTotal = (player?.yahtzeeBonusTotal ?: 0) + if (awardsBonusPreview) 100 else 0

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryTile(
            category = category,
            // Not `|| awardsBonusPreview`: the tile itself is never actually pickable again once
            // scored (it isn't a legal choice), so glowing it like an open, scorable box would be
            // misleading - the +score line below is the preview, the tile's look doesn't change.
            highlighted = isGoodChoice,
            prominent = prominent,
            scored = filled != null,
            yahtzeeBonusCount = yahtzeeBonusCount,
            onClick = if (isLegalChoice) { { onScoreCategory(category) } } else null,
        )
        if (yahtzeeBonusCount > 0 || awardsBonusPreview) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = filled.toString(),
                    color = TileIconColor,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                )
                Text(
                    // The total bonus, not one line per extra Yahtzee - ten of them is still just
                    // one "+900" line here, not ten "+100"s. While awardsBonusPreview, this is the
                    // pending total (what it'll become once ANY category is committed this turn),
                    // not just what's already been earned.
                    text = "+$pendingBonusTotal",
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
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
