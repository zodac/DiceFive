package net.zodac.dicefive.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.game.TutorialStep
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.game_totals_alibi_spoken
import net.zodac.dicefive.resources.game_totals_bonus
import net.zodac.dicefive.resources.game_totals_bonus_earned_spoken
import net.zodac.dicefive.resources.game_totals_cd
import net.zodac.dicefive.resources.game_totals_first_half
import net.zodac.dicefive.resources.game_totals_first_half_spoken
import net.zodac.dicefive.resources.game_totals_lower
import net.zodac.dicefive.resources.game_totals_lower_spoken
import net.zodac.dicefive.resources.game_totals_no_bonus_spoken
import net.zodac.dicefive.resources.game_totals_progress
import net.zodac.dicefive.resources.game_totals_second_half
import net.zodac.dicefive.resources.game_totals_second_half_spoken
import net.zodac.dicefive.resources.game_totals_show_action
import net.zodac.dicefive.resources.game_totals_targets
import net.zodac.dicefive.resources.game_totals_targets_spoken
import net.zodac.dicefive.resources.game_totals_upper
import net.zodac.dicefive.resources.game_totals_upper_progress_spoken
import net.zodac.dicefive.resources.game_totals_upper_spoken
import net.zodac.dicefive.ui.common.AppTooltip
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.grouped
import net.zodac.dicefive.ui.common.joinClauses
import net.zodac.dicefive.ui.common.rememberAppTooltipState
import net.zodac.dicefive.ui.common.scorePulse
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileIconColor
import net.zodac.dicefive.ui.theme.TileTealBorder
import net.zodac.dicefive.ui.theme.TileTealBottom
import net.zodac.dicefive.ui.theme.TileTealTop

/**
 * The scorecard's section totals - Upper, the upper bonus and Lower - behind an icon-only Σ button,
 * shown in a tooltip a tap toggles (it stays open until dismissed, not timing out) and a long press shows. They used to sit beside the cup as three lines of text,
 * which a large system font wrapped and broke; a button holds its size, and they're a
 * glance-when-needed, not something to keep in view. Styled like [UndoButton] beside it.
 *
 * The glyph turns gold once the bonus is earned, as the old Bonus line did. A tooltip isn't
 * announced, so a screen reader hears the totals as the button's state ([totalsSpokenState]) - its
 * action toggles the tooltip too.
 */
@Composable
fun TotalsButton(
    upperTotal: Int,
    upperBonus: Int,
    lowerTotal: Int,
    modifier: Modifier = Modifier,
    minSize: Dp = 48.dp,
    upperTarget: Int? = null,
    enabled: Boolean = true,
    flourish: Boolean = false,
) {
    TotalsButton(
        lines = listOf(
            TotalLine(stringResource(Res.string.game_totals_upper), upperTotal, gold = upperBonus > 0, target = upperTarget),
            TotalLine(stringResource(Res.string.game_totals_bonus), upperBonus, gold = upperBonus > 0),
            TotalLine(stringResource(Res.string.game_totals_lower), lowerTotal),
        ),
        highlighted = upperBonus > 0,
        earned = if (upperBonus > 0) 1 else 0,
        spokenState = totalsSpokenState(upperTotal, upperBonus, lowerTotal, upperTarget),
        modifier = modifier,
        minSize = minSize,
        enabled = enabled,
        flourish = flourish,
    )
}

/**
 * D20's totals - its two halves, each against the [firstTarget] or [secondTarget] that earns its bonus, and the
 * [bonus] earned so far - behind the same button. The glyph's top half turns gold once the 1st half's bonus is
 * earned and its bottom half once the 2nd's, so one earned reads as half lit; both earned lights all of it.
 */
@Composable
fun D20TotalsButton(
    firstHalf: Int,
    secondHalf: Int,
    firstTarget: Int,
    secondTarget: Int,
    bonus: Int,
    modifier: Modifier = Modifier,
    minSize: Dp = 48.dp,
    enabled: Boolean = true,
    flourish: Boolean = false,
) {
    val firstEarned = firstHalf >= firstTarget
    val secondEarned = secondHalf >= secondTarget
    TotalsButton(
        lines = listOf(
            TotalLine(stringResource(Res.string.game_totals_first_half), firstHalf, gold = firstEarned, target = firstTarget),
            TotalLine(stringResource(Res.string.game_totals_second_half), secondHalf, gold = secondEarned, target = secondTarget),
            TotalLine(stringResource(Res.string.game_totals_bonus), bonus, gold = bonus > 0),
        ),
        highlighted = firstEarned && secondEarned,
        earned = listOf(firstEarned, secondEarned).count { it },
        litHalves = setOfNotNull(IconHalf.TOP.takeIf { firstEarned }, IconHalf.BOTTOM.takeIf { secondEarned }),
        spokenState = d20TotalsSpokenState(firstHalf, secondHalf, firstTarget, secondTarget, bonus),
        modifier = modifier,
        minSize = minSize,
        enabled = enabled,
        flourish = flourish,
    )
}

/**
 * Hit List's totals - its targets, and the Alibi - behind the same button: a card of targets has no sections or
 * bonus to show.
 */
@Composable
fun HitListTotalsButton(targetsTotal: Int, alibiTotal: Int, modifier: Modifier = Modifier, minSize: Dp = 48.dp, enabled: Boolean = true) {
    TotalsButton(
        lines = listOf(TotalLine(stringResource(Res.string.game_totals_targets), targetsTotal), TotalLine(requireNotNull(ScoreCategory.ALIBI.mark), alibiTotal)),
        highlighted = false,
        earned = 0,
        spokenState = hitListTotalsSpokenState(targetsTotal, alibiTotal),
        modifier = modifier,
        minSize = minSize,
        enabled = enabled,
    )
}

/**
 * The tooltip's totals as a small table: each [TotalLine]'s label flush left, in the board's face (Sora, bold
 * only), and its number flush right, with thousands separators, in the system font - Sora's digits are
 * proportional, a "1" narrower than a "0", where the system font's are all one width and line up down the
 * column. The column is as wide as the widest line, so the gap between the two sides is the same on every line.
 */
@Composable
private fun TotalsTable(lines: List<TotalLine>) {
    val style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        for (line in lines) {
            val color = if (line.gold) GoldAccent else Color.Unspecified
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = line.label, style = style.copy(fontFamily = SoraFontFamily), color = color, modifier = Modifier.weight(1f))
                Text(
                    text = line.target?.let { stringResource(Res.string.game_totals_progress, line.value, it) } ?: line.value.grouped(),
                    style = style,
                    color = color,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = TOTALS_COLUMN_GAP),
                )
            }
        }
    }
}

private val TOTALS_COLUMN_GAP = 16.dp

/** How big the button swells, at the middle of its [BONUS_POP_MILLIS], when a bonus is earned. */
private const val BONUS_POP_SCALE = 1.5f

/** How long the pop lasts: half growing to [BONUS_POP_SCALE], half shrinking back. */
private const val BONUS_POP_MILLIS = 1000

/** Whether the bonuses earned going from [previous] to [now] gained one, which is when the button pops. */
internal fun bonusJustEarned(previous: Int, now: Int): Boolean = now > previous

/** One line of a [TotalsButton]'s tooltip: [label] and [value] - out of [target], when it has one - in gold when [gold]. */
private class TotalLine(val label: String, val value: Int, val gold: Boolean = false, val target: Int? = null)

/**
 * The button itself: [lines] in its tooltip, its glyph gold while [highlighted], and [spokenState] what TalkBack hears.
 * Does nothing unless [enabled]. With [flourish] (a solo game, where the board stays up to be seen), it pops once each time
 * [earned] (the bonuses earned so far) rises while it's on screen.
 */
@Composable
private fun TotalsButton(
    lines: List<TotalLine>,
    highlighted: Boolean,
    earned: Int,
    spokenState: String,
    modifier: Modifier,
    minSize: Dp,
    litHalves: Set<IconHalf> = emptySet(),
    enabled: Boolean = true,
    flourish: Boolean = false,
) {
    val tooltipState = rememberAppTooltipState(persistent = true)
    val scope = rememberCoroutineScope()
    val totalsName = stringResource(Res.string.game_totals_cd)
    val showTotalsLabel = stringResource(Res.string.game_totals_show_action)
    val color = if (highlighted) GoldAccent else TileIconColor
    val shape = RoundedCornerShape(10.dp)
    // In the tutorial, the button only opens the totals at the step that asks for them - outlined then - and opening
    // them moves it on.
    val tutorial = LocalTutorialGuide.current
    val opens = enabled && (tutorial == null || tutorial.opensTotals)
    val toggle = {
        // Before the toggle: a persistent tooltip's show() only returns once it's dismissed.
        tutorial?.onTotalsOpened()
        scope.launch { tooltipState.toggle() }
    }
    // The tooltip's own anchor merges this button into one TalkBack node of its own, which keeps the
    // name, state and actions but not the role - so the role goes on that node too.
    // In the tutorial the totals stay up while the card talks about them, and the tap on the cup that carries on goes
    // straight to the cup, as well as closing them; the roll closes them if that tap didn't.
    // A CPU's turn leaves them shut: closed if they were open as it began.
    LaunchedEffect(enabled) { if (!enabled) tooltipState.dismiss() }
    // A flourish for a bonus just earned: not on first composition, nor when one is undone.
    val pop = remember { Animatable(1f) }
    // Medium and High only: Low and Off (and reduced motion) never pop.
    val popAllowed = scorePulse
    var shownEarned by remember { mutableIntStateOf(earned) }
    LaunchedEffect(earned) {
        val rose = bonusJustEarned(shownEarned, earned)
        shownEarned = earned
        if (rose && flourish && popAllowed) {
            pop.animateTo(BONUS_POP_SCALE, tween(BONUS_POP_MILLIS / 2, easing = FastOutSlowInEasing))
            pop.animateTo(1f, tween(BONUS_POP_MILLIS / 2, easing = FastOutSlowInEasing))
        }
    }
    if (tutorial != null) {
        LaunchedEffect(tutorial.rolling) { if (tutorial.rolling) tooltipState.dismiss() }
        // A jump to another turn closes them too: they only stay up for the step straight after opening them.
        var previousStep by remember { mutableStateOf(tutorial.step) }
        LaunchedEffect(tutorial.step) {
            if (previousStep !is TutorialStep.OpenTotals) tooltipState.dismiss()
            previousStep = tutorial.step
        }
    }
    // Above its siblings, so its pop swells over the Undo button beside it rather than behind it. On a Box of its own:
    // TooltipBox puts its modifier on an inner anchor, not on the node its parent lays out.
    Box(modifier = modifier.zIndex(1f)) {
        AppTooltip(
            body = { TotalsTable(lines) },
            state = tooltipState,
            enabled = opens,
            focusable = tutorial == null,
            modifier = Modifier.semantics { role = Role.Button },
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    // Before the clip, which would cut off an outline drawn round the button.
                    .then(if (tutorial?.opensTotals == true) Modifier.tutorialOutline(tutorial.pulse, cornerRadius = 10.dp) else Modifier)
                    // Before clickable, so its own click semantics don't leak through - see UI.md.
                    .clearAndSetSemantics {
                        contentDescription = totalsName
                        stateDescription = spokenState
                        role = Role.Button
                        if (opens) {
                            onClick(label = showTotalsLabel) {
                                toggle()
                                true
                            }
                        } else {
                            disabled()
                        }
                    }
                    // M3's 48dp minimum touch target, which the glyph alone (when a large font drops the label) is short of.
                    .sizeIn(minWidth = minSize, minHeight = minSize)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(TileTealTop, TileTealBottom)))
                    .border(1.dp, TileTealBorder, shape)
                    .clickable(enabled = opens, role = Role.Button) { toggle() }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Decorative: the button's own semantics, above, name it and say the totals.
                BoardButtonIcon(imageVector = Icons.Filled.Functions, contentDescription = null, tint = color, litHalves = litHalves)
            }
        }
    }
}

/** What a screen reader hears for [TotalsButton]: every total, the bonus said to be earned once it is. */
@Composable
internal fun totalsSpokenState(upperTotal: Int, upperBonus: Int, lowerTotal: Int, upperTarget: Int? = null): String = joinClauses(
    listOf(
        if (upperTarget != null) stringResource(Res.string.game_totals_upper_progress_spoken, upperTotal, upperTarget) else stringResource(Res.string.game_totals_upper_spoken, upperTotal),
        if (upperBonus > 0) stringResource(Res.string.game_totals_bonus_earned_spoken, upperBonus) else stringResource(Res.string.game_totals_no_bonus_spoken),
        stringResource(Res.string.game_totals_lower_spoken, lowerTotal),
    ),
)

/** What a screen reader hears for [HitListTotalsButton]. */
@Composable
internal fun hitListTotalsSpokenState(targetsTotal: Int, alibiTotal: Int): String =
    joinClauses(listOf(stringResource(Res.string.game_totals_targets_spoken, targetsTotal), stringResource(Res.string.game_totals_alibi_spoken, alibiTotal)))

/** What a screen reader hears for [D20TotalsButton]: both halves out of their targets, and the bonuses said to be earned once they are. */
@Composable
internal fun d20TotalsSpokenState(firstHalf: Int, secondHalf: Int, firstTarget: Int, secondTarget: Int, bonus: Int): String = joinClauses(
    listOf(
        stringResource(Res.string.game_totals_first_half_spoken, firstHalf, firstTarget),
        stringResource(Res.string.game_totals_second_half_spoken, secondHalf, secondTarget),
        if (bonus > 0) stringResource(Res.string.game_totals_bonus_earned_spoken, bonus) else stringResource(Res.string.game_totals_no_bonus_spoken),
    ),
)
