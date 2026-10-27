package net.zodac.dicefive.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.game.TutorialMessage
import net.zodac.dicefive.game.TutorialStep
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.app_name
import net.zodac.dicefive.resources.game_tutorial_bonus_earned
import net.zodac.dicefive.resources.game_tutorial_hold_chase_straight
import net.zodac.dicefive.resources.game_tutorial_hold_five
import net.zodac.dicefive.resources.game_tutorial_hold_fives
import net.zodac.dicefive.resources.game_tutorial_hold_fours_three_x
import net.zodac.dicefive.resources.game_tutorial_hold_fourth_six
import net.zodac.dicefive.resources.game_tutorial_hold_full_house_pair
import net.zodac.dicefive.resources.game_tutorial_hold_full_house_start
import net.zodac.dicefive.resources.game_tutorial_hold_high_dice
import net.zodac.dicefive.resources.game_tutorial_hold_it_too
import net.zodac.dicefive.resources.game_tutorial_hold_ones
import net.zodac.dicefive.resources.game_tutorial_hold_run
import net.zodac.dicefive.resources.game_tutorial_hold_run_five
import net.zodac.dicefive.resources.game_tutorial_hold_six
import net.zodac.dicefive.resources.game_tutorial_hold_sixes
import net.zodac.dicefive.resources.game_tutorial_hold_straight_again
import net.zodac.dicefive.resources.game_tutorial_hold_threes
import net.zodac.dicefive.resources.game_tutorial_hold_twos
import net.zodac.dicefive.resources.game_tutorial_next_turn_cd
import net.zodac.dicefive.resources.game_tutorial_open_totals
import net.zodac.dicefive.resources.game_tutorial_open_totals_bonus
import net.zodac.dicefive.resources.game_tutorial_previous_turn_cd
import net.zodac.dicefive.resources.game_tutorial_roll_again
import net.zodac.dicefive.resources.game_tutorial_roll_for_five
import net.zodac.dicefive.resources.game_tutorial_roll_for_five_again
import net.zodac.dicefive.resources.game_tutorial_roll_for_four
import net.zodac.dicefive.resources.game_tutorial_roll_for_four_again
import net.zodac.dicefive.resources.game_tutorial_roll_for_run_end
import net.zodac.dicefive.resources.game_tutorial_roll_last
import net.zodac.dicefive.resources.game_tutorial_roll_last_try
import net.zodac.dicefive.resources.game_tutorial_roll_new_turn
import net.zodac.dicefive.resources.game_tutorial_roll_other_dice
import net.zodac.dicefive.resources.game_tutorial_score_chance
import net.zodac.dicefive.resources.game_tutorial_score_five_x
import net.zodac.dicefive.resources.game_tutorial_score_fives
import net.zodac.dicefive.resources.game_tutorial_score_fives_mistake
import net.zodac.dicefive.resources.game_tutorial_score_four_x
import net.zodac.dicefive.resources.game_tutorial_score_fours_early
import net.zodac.dicefive.resources.game_tutorial_score_full_house
import net.zodac.dicefive.resources.game_tutorial_score_joker
import net.zodac.dicefive.resources.game_tutorial_score_large_straight
import net.zodac.dicefive.resources.game_tutorial_score_sixes
import net.zodac.dicefive.resources.game_tutorial_score_three_x
import net.zodac.dicefive.resources.game_tutorial_score_threes
import net.zodac.dicefive.resources.game_tutorial_score_twos
import net.zodac.dicefive.resources.game_tutorial_score_zero
import net.zodac.dicefive.resources.game_tutorial_undo_mistake
import net.zodac.dicefive.resources.game_tutorial_upper_progress
import net.zodac.dicefive.resources.game_tutorial_welcome
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.VerticalScrollbar
import net.zodac.dicefive.ui.common.parseInlineMarkup
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.icons.Symbols
import org.jetbrains.compose.resources.StringResource

/**
 * The tutorial's card, in place of the player tabs: [coachName] and the [turn] it is, over what [step] says. The name
 * and words are one node for a screen reader, a polite live region, so each new step is read out as it arrives; the
 * turn is a node of its own. Text in
 * `backticks` - a box's name - is gold, and **bold** a number. Its height is the caller's (see [coachCardHeight]): at a
 * larger font the words scroll inside it, with the app's scrollbar.
 */
@Composable
internal fun CoachCard(
    step: TutorialStep,
    coachName: String,
    turn: String,
    onBack: (() -> Unit)?,
    onForward: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val text = tutorialText(step.message)
    val gold = MaterialTheme.colorScheme.primary
    val styled = remember(text, gold) { parseInlineMarkup(text, codeStyle = SpanStyle(color = gold, fontWeight = FontWeight.Bold)) }
    val scroll = rememberScrollState()
    // Each step's words start from the top.
    LaunchedEffect(step) { scroll.scrollTo(0) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        border = BorderStroke(1.dp, gold),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            TurnArrow(back = true, onClick = onBack)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(vertical = CARD_PADDING_VERTICAL)
                        // The name and the words: one node, read out as each step arrives.
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = coachName,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = gold,
                            modifier = Modifier.weight(1f),
                        )
                        // A node of its own, so it isn't read out again with every step.
                        Text(
                            text = turn,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics(mergeDescendants = true) {},
                        )
                    }
                    Text(text = styled, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            // Takes room beside the words only while they scroll (a larger font); at the standard size it draws nothing.
            VerticalScrollbar(scroll, width = SCROLLBAR_WIDTH, modifier = Modifier.fillMaxHeight())
            TurnArrow(back = false, onClick = onForward)
        }
    }
}

/**
 * The card's back or forward to another turn: a strip down its edge, the card's height and [ARROW_WIDTH] wide, so it's
 * an easy target. Shown, but dimmed and off, with nowhere to go ([onClick] null).
 */
@Composable
private fun TurnArrow(back: Boolean, onClick: (() -> Unit)?) {
    val label = stringResource(if (back) Res.string.game_tutorial_previous_turn_cd else Res.string.game_tutorial_next_turn_cd)
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .width(ARROW_WIDTH)
            .fillMaxHeight()
            // The whole strip takes the tap, but shows it as an icon button does: the pressed state layer and ripple
            // on a circle round the arrow (below), not the strip's full height.
            .clickable(interactionSource = interactions, indication = null, enabled = onClick != null, role = Role.Button) { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ARROW_STATE_LAYER)
                .clip(CircleShape)
                .indication(interactions, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (back) Symbols.KeyboardArrowLeft else Symbols.KeyboardArrowRight,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (onClick != null) 1f else DISABLED_ARROW_ALPHA),
            )
        }
    }
}

/**
 * The height a [CoachCard] [width] wide needs for the longest of [steps]' words at the standard font size - so a card
 * held at it never grows or shrinks between steps, and at a larger font the words scroll instead.
 */
@Composable
internal fun coachCardHeight(steps: List<TutorialStep>, width: Dp): Dp {
    val measurer = rememberTextMeasurer()
    val density = Density(LocalDensity.current.density, fontScale = 1f)
    val body = MaterialTheme.typography.bodyMedium
    val label = MaterialTheme.typography.labelLarge.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)
    val texts = steps.map { tutorialText(it.message) }
    return remember(texts, width, density, body, label) {
        with(density) {
            val textWidth = (width - ARROW_WIDTH * 2).coerceAtLeast(1.dp).roundToPx()
            val labelHeight = measurer.measure("Coach", label, density = density).size.height.toDp() // i18n: not translated - measured for its line height, not shown
            texts.maxOf { text ->
                val words = measurer.measure(parseInlineMarkup(text), body, constraints = Constraints(maxWidth = textWidth), density = density).size.height.toDp()
                labelHeight + words + CARD_PADDING_VERTICAL * 2 + CARD_BORDER * 2
            }
        }
    }
}

// The back and forward strips at the card's edges: M3's 48dp minimum touch target across.
private val ARROW_WIDTH = 48.dp
// M3's icon button state layer: a 40dp circle round the glyph.
private val ARROW_STATE_LAYER = 40.dp
private val CARD_PADDING_VERTICAL = 8.dp
private const val DISABLED_ARROW_ALPHA = 0.3f
private val CARD_BORDER = 1.dp
// The scrollbar beside the words, when they scroll.
private val SCROLLBAR_WIDTH = 10.dp

/** [message]'s words, given the app's name to use where it's written (the welcome). */
@Composable
private fun tutorialText(message: TutorialMessage): String = stringResource(message.resource, stringResource(Res.string.app_name))

/** The string each [TutorialMessage] is written in: `game_tutorial_` and its name. */
private val TutorialMessage.resource: StringResource
    get() = when (this) {
        TutorialMessage.WELCOME -> Res.string.game_tutorial_welcome
        TutorialMessage.HOLD_SIXES -> Res.string.game_tutorial_hold_sixes
        TutorialMessage.ROLL_AGAIN -> Res.string.game_tutorial_roll_again
        TutorialMessage.HOLD_FOURTH_SIX -> Res.string.game_tutorial_hold_fourth_six
        TutorialMessage.ROLL_LAST -> Res.string.game_tutorial_roll_last
        TutorialMessage.SCORE_FIVE_X -> Res.string.game_tutorial_score_five_x
        TutorialMessage.ROLL_NEW_TURN -> Res.string.game_tutorial_roll_new_turn
        TutorialMessage.ROLL_OTHER_DICE -> Res.string.game_tutorial_roll_other_dice
        TutorialMessage.ROLL_LAST_TRY -> Res.string.game_tutorial_roll_last_try
        TutorialMessage.HOLD_IT_TOO -> Res.string.game_tutorial_hold_it_too
        TutorialMessage.HOLD_CHASE_STRAIGHT -> Res.string.game_tutorial_hold_chase_straight
        TutorialMessage.ROLL_FOR_FIVE -> Res.string.game_tutorial_roll_for_five
        TutorialMessage.ROLL_FOR_FIVE_AGAIN -> Res.string.game_tutorial_roll_for_five_again
        TutorialMessage.SCORE_CHANCE -> Res.string.game_tutorial_score_chance
        TutorialMessage.HOLD_RUN -> Res.string.game_tutorial_hold_run
        TutorialMessage.HOLD_RUN_FIVE -> Res.string.game_tutorial_hold_run_five
        TutorialMessage.ROLL_FOR_RUN_END -> Res.string.game_tutorial_roll_for_run_end
        TutorialMessage.SCORE_LARGE_STRAIGHT -> Res.string.game_tutorial_score_large_straight
        TutorialMessage.HOLD_THREES -> Res.string.game_tutorial_hold_threes
        TutorialMessage.SCORE_THREES -> Res.string.game_tutorial_score_threes
        TutorialMessage.HOLD_STRAIGHT_AGAIN -> Res.string.game_tutorial_hold_straight_again
        TutorialMessage.ROLL_FOR_FOUR -> Res.string.game_tutorial_roll_for_four
        TutorialMessage.ROLL_FOR_FOUR_AGAIN -> Res.string.game_tutorial_roll_for_four_again
        TutorialMessage.SCORE_ZERO -> Res.string.game_tutorial_score_zero
        TutorialMessage.SCORE_FOURS_EARLY -> Res.string.game_tutorial_score_fours_early
        TutorialMessage.HOLD_FIVES -> Res.string.game_tutorial_hold_fives
        TutorialMessage.SCORE_FIVES_MISTAKE -> Res.string.game_tutorial_score_fives_mistake
        TutorialMessage.UNDO_MISTAKE -> Res.string.game_tutorial_undo_mistake
        TutorialMessage.SCORE_FOUR_X -> Res.string.game_tutorial_score_four_x
        TutorialMessage.HOLD_TWOS -> Res.string.game_tutorial_hold_twos
        TutorialMessage.SCORE_TWOS -> Res.string.game_tutorial_score_twos
        TutorialMessage.HOLD_FULL_HOUSE_START -> Res.string.game_tutorial_hold_full_house_start
        TutorialMessage.HOLD_FULL_HOUSE_PAIR -> Res.string.game_tutorial_hold_full_house_pair
        TutorialMessage.SCORE_FULL_HOUSE -> Res.string.game_tutorial_score_full_house
        TutorialMessage.HOLD_FIVE -> Res.string.game_tutorial_hold_five
        TutorialMessage.SCORE_FIVES -> Res.string.game_tutorial_score_fives
        TutorialMessage.OPEN_TOTALS -> Res.string.game_tutorial_open_totals
        TutorialMessage.UPPER_PROGRESS -> Res.string.game_tutorial_upper_progress
        TutorialMessage.HOLD_ONES -> Res.string.game_tutorial_hold_ones
        TutorialMessage.SCORE_JOKER -> Res.string.game_tutorial_score_joker
        TutorialMessage.HOLD_SIX -> Res.string.game_tutorial_hold_six
        TutorialMessage.SCORE_SIXES -> Res.string.game_tutorial_score_sixes
        TutorialMessage.OPEN_TOTALS_BONUS -> Res.string.game_tutorial_open_totals_bonus
        TutorialMessage.BONUS_EARNED -> Res.string.game_tutorial_bonus_earned
        TutorialMessage.HOLD_FOURS_THREE_X -> Res.string.game_tutorial_hold_fours_three_x
        TutorialMessage.HOLD_HIGH_DICE -> Res.string.game_tutorial_hold_high_dice
        TutorialMessage.SCORE_THREE_X -> Res.string.game_tutorial_score_three_x
    }
