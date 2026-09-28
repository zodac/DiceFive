package net.zodac.dicefive.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileIconColor

/**
 * [DiceCupPanel]'s cup-specific behaviour - the parts of the panel that only make sense for a turn
 * actually being played right now. [ReadOnlyScoreboard] passes `cup = null` instead of a second,
 * hand-copied panel layout, so its 5x tile and Upper/Bonus/Lower tracker can never drift out of
 * sync with [GameBoard]'s - only the cup, roll count and undo button disappear.
 */
// The board deliberately avoids theme colour roles (see .claude/UI.md), so "disabled" here means
// alpha-fading the cup's own fixed art rather than reaching for M3's onSurface-alpha convention.
private const val DEPLETED_CUP_ALPHA = 0.4f

data class CupPanelState(
    val rollsRemaining: Int,
    val tilted: Boolean,
    val rolling: Boolean,
    val canUndo: Boolean,
    val onCupTap: () -> Unit,
    val onUndo: () -> Unit,
)

/**
 * The right-hand column beside the category grid: the prominent 5x tile (its top level with the
 * grid's first row), the (tappable) dice cup with its remaining-rolls count, and the upper-section
 * bonus tracker with undo - the last three only when [cup] is non-null, i.e. an actual turn is in
 * progress rather than a read-only look at someone else's scorecard.
 */
@Composable
fun DiceCupPanel(
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

    // Same height as the grid beside it (both fill the board's padded Row), which is what lets
    // firstRowTileInset find where that grid's first row sits.
    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val topInset = firstRowTileInset(gameMode, maxHeight)
        Column(modifier = Modifier.fillMaxSize()) {
            // Top-aligned, topInset down, so the tile's top edge is level with Ones and 3x in the
            // grid beside it - it used to sit centred in this space, noticeably lower than that row.
            Box(modifier = Modifier.weight(2f).fillMaxWidth()) {
                CategoryCell(
                    category = ScoreCategory.FIVE_OF_A_KIND,
                    player = player,
                    canScore = canScore,
                    showPreview = showPreview,
                    available = available,
                    dice = dice,
                    onScoreCategory = onScoreCategory,
                    prominent = true,
                    modifier = Modifier.padding(top = topInset).fillMaxWidth(),
                )
            }

            val cupInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    // weight(3f) reproduces the same row height this area already had - only the width
                    // changes here (fillMaxWidth, below), not the height. Kept even with no cup to draw
                    // (cup == null) so the 5x tile and the stats row below stay at the same heights
                    // either way - only this box's own content disappears.
                    .weight(3f)
                    .fillMaxWidth()
                    // The cup itself rotates (shake + tilt) via graphicsLayer, which only affects
                    // painting, not this composable's own layout/hit-test bounds. Rather than rely on
                    // Compose's hit-testing following that rotation (unreliable in practice - the tap
                    // target stayed pinned to the upright pose after the visual tilted), the clickable
                    // area is this separate, non-rotating Box wrapping the cup and its label, widened to
                    // the full row width so the tap target reaches as far right as the undo button
                    // below it - not just the 104dp square the cup art itself occupies.
                    //
                    // indication = null drops the default ripple: at this size it painted as an
                    // obvious translucent white rectangle over the whole tap target on press, which
                    // read as a rendering glitch rather than a press effect.
                    //
                    // Always enabled, even with no rolls left: onCupTap itself decides what a tap does
                    // in that case (see GameScreen) - counting it towards "No More Rolls" rather than
                    // the cup simply going dead once the useful taps run out.
                    .then(
                        if (cup != null) {
                            Modifier.clickable(interactionSource = cupInteractionSource, indication = null, onClick = cup.onCupTap)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (cup != null) {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Dimmed once rolls run out - the cup stays tappable (see the comment on
                        // this Box's parent) but visually reads as spent rather than still live,
                        // its contact shadow fading along with the rest of the art since the alpha
                        // applies to the whole Canvas draw, shadow included.
                        val cupAlpha = if (cup.rollsRemaining <= 0) DEPLETED_CUP_ALPHA else 1f
                        Box(
                            modifier = Modifier.size(104.dp).alpha(cupAlpha),
                            contentAlignment = Alignment.Center,
                        ) {
                            visualTheme.diceCupStyle.Cup(
                                rolling = cup.rolling,
                                tilted = cup.tilted,
                                modifier = Modifier.size(width = 58.dp, height = 84.dp),
                            )
                        }
                        Text(
                            text = "x${cup.rollsRemaining}",
                            color = TileIconColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val upperTotal = player?.upperSectionTotal ?: 0
                val upperBonus = player?.upperSectionBonus ?: 0
                val lowerTotal = (player?.lowerSectionTotal ?: 0) + (player?.fiveOfAKindBonusTotal ?: 0)
                // Clearance from the score grid's rightmost column - which can render a 2-digit score
                // past its own column's edge - comes from GameBoard's inter-panel gap and weight split,
                // not from padding here specifically, so every row of this panel (this one, the cup, the
                // 5x tile above) gets the same protection instead of just this one.
                Column {
                    SectionStatRow(label = "Upper:", value = upperTotal)
                    SectionStatRow(
                        label = "Bonus:",
                        value = upperBonus,
                        color = if (upperBonus > 0) GoldAccent else TileIconColor,
                        fontWeight = if (upperBonus > 0) FontWeight.Bold else FontWeight.Normal,
                    )
                    SectionStatRow(label = "Lower:", value = lowerTotal)
                }
                if (cup != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    UndoButton(enabled = cup.canUndo, onClick = cup.onUndo)
                }
            }
        }
    }
}

/**
 * One line of the Upper/Bonus/Lower summary. The label sits in a fixed-width column so the values
 * line up regardless of how wide "Upper:"/"Bonus:"/"Lower:" render in a proportional font.
 */
@Composable
internal fun SectionStatRow(
    label: String,
    value: Int,
    color: Color = TileIconColor,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Row {
        Text(
            text = label,
            color = color,
            fontWeight = fontWeight,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = "$value",
            color = color,
            fontWeight = fontWeight,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
