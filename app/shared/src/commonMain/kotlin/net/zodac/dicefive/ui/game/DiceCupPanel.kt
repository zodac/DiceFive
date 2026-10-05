package net.zodac.dicefive.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.util.lerp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.game.style.FlowerpotGrowth
import net.zodac.dicefive.ui.game.style.LocalCupActivity
import net.zodac.dicefive.ui.game.style.LocalCupAnimated
import net.zodac.dicefive.ui.game.style.LocalFlowerpotGrowth
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.theme.TileIconColor

/**
 * [DiceCupPanel]'s cup-specific behaviour - the parts of the panel that only make sense for a turn
 * actually being played right now. [ReadOnlyScoreboard] passes `cup = null` instead of a second,
 * hand-copied panel layout, so its 5x tile and Totals button can never drift out of
 * sync with [GameBoard]'s - only the cup, roll count and undo button disappear.
 */
// The board deliberately avoids theme colour roles (see .claude/UI.md), so "disabled" here means
// alpha-fading the cup's own fixed art rather than reaching for M3's onSurface-alpha convention.
// How long before the dice settle the cup's fade finishes.
private const val CUP_FADE_EARLY_MILLIS = 100
private const val SPENT_CUP_SATURATION = 0.25f
private const val SPENT_CUP_BRIGHTNESS = 0.55f
// How long a Flowerpot that's just bloomed rests tipped over after pouring before it stands back up
// to show the sunflower off: time for the dice to settle and the bloom to finish growing in.
private const val BLOOM_STAND_UP_MILLIS = 1_400L

data class CupPanelState(
    val rollsRemaining: Int,
    val tilted: Boolean,
    val rolling: Boolean,
    /**
     * The last moments of a shake, when the cup has stopped shaking and is already tipping the dice
     * out, a few frames before they land - see `CUP_POUR_LEAD_MILLIS`. Only the cup's own art uses
     * it: the roll itself is still [rolling] until the dice land.
     */
    val pouring: Boolean = false,
    /**
     * A roll is under way - shaking, or its dice still tumbling to rest - so the cup takes no tap
     * until it's over (see `GameScreen`'s `onCupTap`), and says it's disabled meanwhile.
     */
    val rollInHand: Boolean = false,
    val canUndo: Boolean,
    /** Whether the undo button is shown at all - only in a solo game. */
    val showUndo: Boolean,
    val onCupTap: () -> Unit,
    val onUndo: () -> Unit,
    /** The current player's plant, for the Flowerpot cup - see [FlowerpotGrowth]. */
    val flowerpotGrowth: FlowerpotGrowth,
)

/**
 * The right-hand column beside the category grid: the prominent 5x tile (its top level with the
 * grid's first row), the (tappable) dice cup with its remaining-rolls count, and the Totals button
 * (Upper/Bonus/Lower, see [TotalsButton]) with undo - the cup and undo only when [cup] is non-null,
 * i.e. an actual turn is in progress rather than a read-only look at someone else's scorecard.
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
                    // Enabled even with no rolls left: onCupTap itself decides what a tap does in that
                    // case (see GameScreen) - counting it towards "No More Rolls" rather than the cup
                    // simply going dead once the useful taps run out. Only disabled while a roll is in
                    // hand, until its dice have settled - the same wait scoring has.
                    .then(
                        if (cup != null) {
                            Modifier
                                .clickable(
                                    interactionSource = cupInteractionSource,
                                    indication = null,
                                    enabled = !cup.rollInHand,
                                    onClickLabel = "Roll",
                                    role = Role.Button,
                                    onClick = cup.onCupTap,
                                )
                                // Said instead of the "x3" drawn beside the cup.
                                .clearAndSetSemantics {
                                    contentDescription = "Dice cup, ${cup.rollsRemaining} ${if (cup.rollsRemaining == 1) "roll" else "rolls"} left"
                                    role = Role.Button
                                    if (cup.rollInHand) disabled()
                                    onClick(label = "Roll") {
                                        cup.onCupTap()
                                        true
                                    }
                                }
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
                        // Greyed and darkened once rolls run out - the cup stays tappable (see the comment on
                        // this Box's parent) but visually reads as spent rather than still live. Done by
                        // recolouring rather than fading, so the cup stays opaque and the table behind it
                        // (stars, say) doesn't show through. Fades over the second half of the last roll's
                        // toss, ending a little before the dice settle.
                        // The exception is a Flowerpot that's grown its sunflower, which keeps its colour
                        // and, once it's poured, stands back up so its bloom is seen in full - it still
                        // can't be rolled.
                        val outOfRolls = cup.rollsRemaining <= 0 && !cup.rolling
                        val showingOff = outOfRolls && visualTheme.diceCupStyle.showsOffWhenSpent(cup.flowerpotGrowth)
                        val depleted = outOfRolls && !showingOff
                        val lifecycle = LocalLifecycleOwner.current.lifecycle
                        var standingForBloom by remember { mutableStateOf(false) }
                        LaunchedEffect(showingOff) {
                            standingForBloom = false
                            if (showingOff) {
                                lifecycle.delayWhileResumed(BLOOM_STAND_UP_MILLIS)
                                standingForBloom = true
                            }
                        }
                        // Under reduced motion it's simply grey once spent, with no fade.
                        val spent by animateFloatAsState(
                            targetValue = if (depleted) 1f else 0f,
                            animationSpec = if (LocalReduceMotion.current) {
                                snap()
                            } else if (depleted) {
                                tween(DICE_TOSS_MILLIS / 2, delayMillis = DICE_TOSS_MILLIS / 2 - CUP_FADE_EARLY_MILLIS, easing = LinearEasing)
                            } else {
                                tween(DICE_TOSS_MILLIS / 2, easing = LinearEasing)
                            },
                            label = "cupSpent",
                        )
                        Box(
                            modifier = Modifier.size(104.dp).spentLook(spent),
                            contentAlignment = Alignment.Center,
                        ) {
                            val cupStyle = visualTheme.diceCupStyle
                            // A spent cup sits still: no ambient animation once it's dimmed. The dice
                            // double as the table's activity - holding one counts as doing something.
                            CompositionLocalProvider(
                                LocalCupAnimated provides (cup.rollsRemaining > 0),
                                LocalCupActivity provides dice,
                                LocalFlowerpotGrowth provides cup.flowerpotGrowth,
                            ) {
                                // Under reduced motion the cup doesn't shake (the shake sound and buzz still play -
                                // GameScreen) or pour: it stands while the roll is in it and is simply tipped once
                                // the roll lands and scoring opens.
                                val reduceMotion = LocalReduceMotion.current
                                cupStyle.Cup(
                                    rolling = cup.rolling && !cup.pouring && !reduceMotion,
                                    tilted = if (reduceMotion) {
                                        cup.tilted && !cup.rolling && !standingForBloom
                                    } else {
                                        (cup.tilted || cup.pouring) && !standingForBloom
                                    },
                                    // A cup's shape grid is its size in dp here - tall or squat, both fit this 104dp box.
                                    modifier = Modifier.size(width = cupStyle.shape.gridWidth.dp, height = cupStyle.shape.gridHeight.dp),
                                )
                            }
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
                // Both buttons at the right, Totals just left of Undo - Undo where it always was.
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                val upperTotal = player?.upperSectionTotal ?: 0
                val upperBonus = player?.upperSectionBonus ?: 0
                val lowerTotal = (player?.lowerSectionTotal ?: 0) + (player?.fiveOfAKindBonusTotal ?: 0)
                // Clearance from the score grid's rightmost column - which can render a 2-digit score
                // past its own column's edge - comes from GameBoard's inter-panel gap and weight split,
                // not from padding here specifically, so every row of this panel (this one, the cup, the
                // 5x tile above) gets the same protection instead of just this one.
                TotalsButton(upperTotal = upperTotal, upperBonus = upperBonus, lowerTotal = lowerTotal)
                if (cup != null && cup.showUndo) {
                    UndoButton(enabled = cup.canUndo, onClick = cup.onUndo)
                }
            }
        }
    }
}

/** Recolours everything drawn by [spent] (0-1) towards grey and dark, without making any of it see-through. */
private fun Modifier.spentLook(spent: Float): Modifier = if (spent <= 0f) this else drawWithContent {
    val brightness = lerp(1f, SPENT_CUP_BRIGHTNESS, spent)
    val matrix = ColorMatrix().apply {
        setToSaturation(lerp(1f, SPENT_CUP_SATURATION, spent))
        timesAssign(ColorMatrix(floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
    }
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(matrix) }
    drawIntoCanvas { canvas ->
        // Past the box on every side: a cup's art can reach out of it (the Flowerpot's sunflower
        // stands well above it), and a layer only the box's size would cut that off.
        canvas.saveLayer(size.toRect().inflate(size.minDimension), paint)
        drawContent()
        canvas.restore()
    }
}
