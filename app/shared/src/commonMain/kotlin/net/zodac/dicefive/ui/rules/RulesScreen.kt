package net.zodac.dicefive.ui.rules

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.HitTarget
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.rules_5x_and_joker_1
import net.zodac.dicefive.resources.rules_5x_and_joker_2
import net.zodac.dicefive.resources.rules_5x_and_joker_3
import net.zodac.dicefive.resources.rules_5x_and_joker_4
import net.zodac.dicefive.resources.rules_5x_and_joker_5
import net.zodac.dicefive.resources.rules_5x_and_joker_6
import net.zodac.dicefive.resources.rules_5x_and_joker_tab
import net.zodac.dicefive.resources.rules_5x_and_joker_title
import net.zodac.dicefive.resources.rules_example_any_place_spoken
import net.zodac.dicefive.resources.rules_example_blue_die_spoken
import net.zodac.dicefive.resources.rules_example_equals
import net.zodac.dicefive.resources.rules_example_held_spoken
import net.zodac.dicefive.resources.rules_example_ignored_spoken
import net.zodac.dicefive.resources.rules_example_locked_spoken
import net.zodac.dicefive.resources.rules_example_nothing_held_spoken
import net.zodac.dicefive.resources.rules_example_red_die_spoken
import net.zodac.dicefive.resources.rules_example_roll_spoken
import net.zodac.dicefive.resources.rules_example_scores_spoken
import net.zodac.dicefive.resources.rules_example_spoken
import net.zodac.dicefive.resources.rules_example_target_spoken
import net.zodac.dicefive.resources.rules_example_tile_alibi_spoken
import net.zodac.dicefive.resources.rules_example_tile_target_spoken
import net.zodac.dicefive.resources.rules_example_worth_spoken
import net.zodac.dicefive.resources.rules_example_yellow_die_spoken
import net.zodac.dicefive.resources.rules_extended_scores_1
import net.zodac.dicefive.resources.rules_extended_scores_2
import net.zodac.dicefive.resources.rules_extended_scores_3
import net.zodac.dicefive.resources.rules_extended_scores_4
import net.zodac.dicefive.resources.rules_extended_scores_5
import net.zodac.dicefive.resources.rules_extended_scores_tab
import net.zodac.dicefive.resources.rules_extended_scores_title
import net.zodac.dicefive.resources.rules_group_gameplay
import net.zodac.dicefive.resources.rules_group_modes
import net.zodac.dicefive.resources.rules_group_modifiers
import net.zodac.dicefive.resources.rules_hit_list_1
import net.zodac.dicefive.resources.rules_hit_list_10
import net.zodac.dicefive.resources.rules_hit_list_11
import net.zodac.dicefive.resources.rules_hit_list_12
import net.zodac.dicefive.resources.rules_hit_list_13
import net.zodac.dicefive.resources.rules_hit_list_14
import net.zodac.dicefive.resources.rules_hit_list_15
import net.zodac.dicefive.resources.rules_hit_list_16
import net.zodac.dicefive.resources.rules_hit_list_17
import net.zodac.dicefive.resources.rules_hit_list_2
import net.zodac.dicefive.resources.rules_hit_list_3
import net.zodac.dicefive.resources.rules_hit_list_4
import net.zodac.dicefive.resources.rules_hit_list_5
import net.zodac.dicefive.resources.rules_hit_list_7
import net.zodac.dicefive.resources.rules_hit_list_8
import net.zodac.dicefive.resources.rules_hit_list_9
import net.zodac.dicefive.resources.rules_hit_list_tab
import net.zodac.dicefive.resources.rules_hit_list_title
import net.zodac.dicefive.resources.rules_how_to_play_1
import net.zodac.dicefive.resources.rules_how_to_play_2
import net.zodac.dicefive.resources.rules_how_to_play_3
import net.zodac.dicefive.resources.rules_how_to_play_4
import net.zodac.dicefive.resources.rules_how_to_play_tab
import net.zodac.dicefive.resources.rules_how_to_play_title
import net.zodac.dicefive.resources.rules_lower_section_1
import net.zodac.dicefive.resources.rules_lower_section_2
import net.zodac.dicefive.resources.rules_lower_section_3
import net.zodac.dicefive.resources.rules_lower_section_4
import net.zodac.dicefive.resources.rules_lower_section_5
import net.zodac.dicefive.resources.rules_lower_section_6
import net.zodac.dicefive.resources.rules_lower_section_7
import net.zodac.dicefive.resources.rules_lower_section_8
import net.zodac.dicefive.resources.rules_lower_section_tab
import net.zodac.dicefive.resources.rules_lower_section_title
import net.zodac.dicefive.resources.rules_modes_1
import net.zodac.dicefive.resources.rules_modes_2
import net.zodac.dicefive.resources.rules_modes_3
import net.zodac.dicefive.resources.rules_modes_4
import net.zodac.dicefive.resources.rules_modes_tab
import net.zodac.dicefive.resources.rules_modes_title
import net.zodac.dicefive.resources.rules_modifiers_1
import net.zodac.dicefive.resources.rules_modifiers_2
import net.zodac.dicefive.resources.rules_modifiers_tab
import net.zodac.dicefive.resources.rules_modifiers_title
import net.zodac.dicefive.resources.rules_name_exact_hit
import net.zodac.dicefive.resources.rules_name_hit
import net.zodac.dicefive.resources.rules_name_partial_hit
import net.zodac.dicefive.resources.rules_number_of_rolls_1
import net.zodac.dicefive.resources.rules_number_of_rolls_2
import net.zodac.dicefive.resources.rules_number_of_rolls_tab
import net.zodac.dicefive.resources.rules_number_of_rolls_title
import net.zodac.dicefive.resources.rules_page_count
import net.zodac.dicefive.resources.rules_page_in_group_spoken
import net.zodac.dicefive.resources.rules_plus_spoken
import net.zodac.dicefive.resources.rules_points_short
import net.zodac.dicefive.resources.rules_points_spoken
import net.zodac.dicefive.resources.rules_points_sum
import net.zodac.dicefive.resources.rules_quickfire_1
import net.zodac.dicefive.resources.rules_quickfire_2
import net.zodac.dicefive.resources.rules_quickfire_3
import net.zodac.dicefive.resources.rules_quickfire_4
import net.zodac.dicefive.resources.rules_quickfire_5
import net.zodac.dicefive.resources.rules_quickfire_6
import net.zodac.dicefive.resources.rules_quickfire_tab
import net.zodac.dicefive.resources.rules_quickfire_title
import net.zodac.dicefive.resources.rules_step_number
import net.zodac.dicefive.resources.rules_stored_rolls_1
import net.zodac.dicefive.resources.rules_stored_rolls_2
import net.zodac.dicefive.resources.rules_stored_rolls_3
import net.zodac.dicefive.resources.rules_stored_rolls_tab
import net.zodac.dicefive.resources.rules_stored_rolls_title
import net.zodac.dicefive.resources.rules_stud_1
import net.zodac.dicefive.resources.rules_stud_2
import net.zodac.dicefive.resources.rules_stud_3
import net.zodac.dicefive.resources.rules_stud_4
import net.zodac.dicefive.resources.rules_stud_5
import net.zodac.dicefive.resources.rules_stud_6
import net.zodac.dicefive.resources.rules_stud_7
import net.zodac.dicefive.resources.rules_stud_tab
import net.zodac.dicefive.resources.rules_stud_title
import net.zodac.dicefive.resources.rules_third_wind_1
import net.zodac.dicefive.resources.rules_third_wind_10
import net.zodac.dicefive.resources.rules_third_wind_11
import net.zodac.dicefive.resources.rules_third_wind_12
import net.zodac.dicefive.resources.rules_third_wind_2
import net.zodac.dicefive.resources.rules_third_wind_3
import net.zodac.dicefive.resources.rules_third_wind_4
import net.zodac.dicefive.resources.rules_third_wind_5
import net.zodac.dicefive.resources.rules_third_wind_6
import net.zodac.dicefive.resources.rules_third_wind_7
import net.zodac.dicefive.resources.rules_third_wind_8
import net.zodac.dicefive.resources.rules_third_wind_9
import net.zodac.dicefive.resources.rules_third_wind_tab
import net.zodac.dicefive.resources.rules_third_wind_title
import net.zodac.dicefive.resources.rules_tie_breaks_1
import net.zodac.dicefive.resources.rules_tie_breaks_2
import net.zodac.dicefive.resources.rules_tie_breaks_3
import net.zodac.dicefive.resources.rules_tie_breaks_4
import net.zodac.dicefive.resources.rules_tie_breaks_5
import net.zodac.dicefive.resources.rules_tie_breaks_6
import net.zodac.dicefive.resources.rules_tie_breaks_7
import net.zodac.dicefive.resources.rules_tie_breaks_8
import net.zodac.dicefive.resources.rules_tie_breaks_tab
import net.zodac.dicefive.resources.rules_tie_breaks_title
import net.zodac.dicefive.resources.rules_timer_example_cd
import net.zodac.dicefive.resources.rules_title
import net.zodac.dicefive.resources.rules_tricolour_1
import net.zodac.dicefive.resources.rules_tricolour_2
import net.zodac.dicefive.resources.rules_tricolour_3
import net.zodac.dicefive.resources.rules_tricolour_4
import net.zodac.dicefive.resources.rules_tricolour_5
import net.zodac.dicefive.resources.rules_tricolour_6
import net.zodac.dicefive.resources.rules_tricolour_7
import net.zodac.dicefive.resources.rules_tricolour_8
import net.zodac.dicefive.resources.rules_tricolour_tab
import net.zodac.dicefive.resources.rules_tricolour_title
import net.zodac.dicefive.resources.rules_turn_timer_1
import net.zodac.dicefive.resources.rules_turn_timer_2
import net.zodac.dicefive.resources.rules_turn_timer_tab
import net.zodac.dicefive.resources.rules_turn_timer_title
import net.zodac.dicefive.resources.rules_unlucky_dice_1
import net.zodac.dicefive.resources.rules_unlucky_dice_2
import net.zodac.dicefive.resources.rules_unlucky_dice_3
import net.zodac.dicefive.resources.rules_unlucky_dice_4
import net.zodac.dicefive.resources.rules_unlucky_dice_tab
import net.zodac.dicefive.resources.rules_unlucky_dice_title
import net.zodac.dicefive.resources.rules_upper_section_1
import net.zodac.dicefive.resources.rules_upper_section_2
import net.zodac.dicefive.resources.rules_upper_section_3
import net.zodac.dicefive.resources.rules_upper_section_tab
import net.zodac.dicefive.resources.rules_upper_section_title
import net.zodac.dicefive.resources.score_blues
import net.zodac.dicefive.resources.score_chance
import net.zodac.dicefive.resources.score_coloured_house
import net.zodac.dicefive.resources.score_evens
import net.zodac.dicefive.resources.score_fives
import net.zodac.dicefive.resources.score_full_house
import net.zodac.dicefive.resources.score_large_straight
import net.zodac.dicefive.resources.score_odds
import net.zodac.dicefive.resources.score_reds
import net.zodac.dicefive.resources.score_small_straight
import net.zodac.dicefive.resources.score_two_pair
import net.zodac.dicefive.resources.score_yellows
import net.zodac.dicefive.ui.common.FooterPill
import net.zodac.dicefive.ui.common.LOGO_DICE
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.VerticalScrollbar
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.common.joinClauses
import net.zodac.dicefive.ui.common.joinSentences
import net.zodac.dicefive.ui.common.localised
import net.zodac.dicefive.ui.common.logoRollPose
import net.zodac.dicefive.ui.common.parseInlineMarkup
import net.zodac.dicefive.ui.common.playLogoRoll
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.spokenList
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.game.CUP_SHAKE_MILLIS
import net.zodac.dicefive.ui.game.CategoryTile
import net.zodac.dicefive.ui.game.LockedChains
import net.zodac.dicefive.ui.game.TurnTimerBadge
import net.zodac.dicefive.ui.game.style.ClassicGoldDiceCupStyle
import net.zodac.dicefive.ui.game.style.IvoryDiceStyle
import net.zodac.dicefive.ui.game.style.palette
import net.zodac.dicefive.ui.game.targetProgress
import org.jetbrains.compose.resources.StringResource

/** One page of [RulesScreen]. [title] heads the page itself; [tabLabel] is the shorter name its tab
 * carries, so the tab row shows more than one or two tabs at a time. [blocks] render in order, each
 * as its own kind of block rather than as hand-typed markup (a leading "- " for a bullet, "[25pts]:"
 * after a name), so every list and every category on every page is laid out the same way.
 *
 * Text may use [parseInlineMarkup]'s markers, to one convention throughout: `backticks` (gold
 * monospace, like a [RulesCategory]'s heading) for a scoring category's name, *italic* for the name
 * of a section, mode or setting, and **bold** for a number of points or a count. Nothing else is
 * styled, and no dice are written out as text - a [RulesDice] row shows them instead. */
private data class RulesPage(val title: StringResource, val tabLabel: StringResource, val blocks: List<RulesBlock>)

/** A group of [RulesPage]s - a tab of the top row, named [label], whose [pages] are the tabs of the row under it. */
private class RulesGroup(val label: StringResource, val pages: List<RulesPage>)

private sealed interface RulesBlock

/** The default cup with a handful of dice tipped out beside it - a picture for a page with no
 * example rolls of its own, so it doesn't look empty. */
private data object RulesIllustration : RulesBlock

/** The game's own turn timer badge, stopped at [TURN_TIMER_EXAMPLE_SECONDS] so it flashes as it
 * does when a turn is running out. */
private data object RulesTurnTimer : RulesBlock

/** A paragraph of body text. */
private data class RulesText(val text: StringResource) : RulesBlock

/** One step of an ordered list - "1.", "2." - with its text hanging beside the number, and an
 * optional [example] under it. */
private data class RulesStep(val number: Int, val text: StringResource, val example: RulesDice? = null) : RulesBlock

/** A scoring category: its [name] as a small heading, what it takes in [description], and an
 * [example] roll scoring it. */
private data class RulesCategory(val name: RulesName, val description: StringResource, val example: RulesDice) : RulesBlock

/** A [RulesCategory]'s heading: the name of a box (translated), or one of the game's marks ("3x", "5x", "Alibi") as it is. */
private sealed interface RulesName {
    data class Mark(val text: String) : RulesName

    data class Named(val resource: StringResource) : RulesName
}

private fun mark(text: String): RulesName = RulesName.Mark(text)

private fun named(resource: StringResource): RulesName = RulesName.Named(resource)

/**
 * What an example scores: one amount, or two added together ("20pts + 100pts" - a repeat 5x in its own box and
 * the bonus). Shown, and read aloud, in the player's language (see [pointsText]).
 */
private class RulesScore(val amounts: List<Int>)

private fun points(vararg amounts: Int) = RulesScore(amounts.toList())

/** One die in a [RulesDice] example: its [value], whether it [counts] towards the category being
 * shown (the rest are drawn faded), the [colour] it rolled in Tricolour, if any, and whether it is [locked]
 * in chains by Unlucky Dice (drawn at full strength with the chains over it, and never counting). */
private data class ExampleDie(val value: Int, val counts: Boolean = true, val colour: DieColour? = null, val locked: Boolean = false) {
    /** A Hit List target's place that any die fills - drawn as an empty, dashed die. */
    val anyPlace: Boolean
        get() = value == ANY_PLACE
}

/**
 * A row of example dice illustrating the rule above it, with what they score alongside - or, with no
 * [score], a roll on its way to a hand. The faded dice don't count towards it, or with [fadedNotHeld]
 * are the ones left unheld. With [isTarget], it's a Hit List target rather than a roll - its places, any die
 * filling an [ExampleDie.anyPlace] - and [score] what it's worth.
 */
private data class RulesDice(
    val dice: List<ExampleDie>,
    val score: RulesScore?,
    val fadedNotHeld: Boolean = false,
    val isTarget: Boolean = false,
    val tile: RulesTile? = null,
) : RulesBlock

/**
 * A Hit List box drawn at the end of a [RulesDice] row, as the board shows it for those dice - so the page shows what
 * a target's tile looks like on its own, part filled and fully filled. A [Target] is matched against the row's dice
 * (not for the row that is the target itself); the [Alibi] lights up as a box the dice can score in.
 */
private sealed interface RulesTile {
    data class Target(val target: HitTarget) : RulesTile

    data object Alibi : RulesTile
}

/** This row with [tile] drawn at its end. */
private fun RulesDice.showing(tile: RulesTile): RulesDice = copy(tile = tile)

private fun text(text: StringResource) = RulesText(text)

/** Five example dice where the die at [lockedIndex] is locked by Unlucky Dice and the first [counting] of the others make the category. */
private fun lockedDice(vararg values: Int, lockedIndex: Int, counting: Int, score: RulesScore): RulesDice {
    var seen = 0
    return RulesDice(
        values.mapIndexed { index, value ->
            if (index == lockedIndex) ExampleDie(value, counts = false, locked = true) else ExampleDie(value, counts = seen++ < counting)
        },
        score,
    )
}

/** Five example dice, the first [counting] of which make the category. */
private fun dice(vararg values: Int, counting: Int = values.size, score: RulesScore): RulesDice =
    RulesDice(values.mapIndexed { index, value -> ExampleDie(value, counts = index < counting) }, score)

/** A roll of example dice, the first [held] of which are held - drawn and read out as such, with no score. */
private fun roll(vararg values: Int, held: Int): RulesDice =
    RulesDice(values.mapIndexed { index, value -> ExampleDie(value, counts = index < held) }, score = null, fadedNotHeld = true)

/** Five example dice, the ones at [counting] making the category - for a category whose dice aren't simply the first few. */
private fun diceCounting(vararg values: Int, counting: Set<Int>, score: RulesScore): RulesDice =
    RulesDice(values.mapIndexed { index, value -> ExampleDie(value, counts = index in counting) }, score)

/** A roll of example dice, the ones at [held] held - for a roll whose held dice aren't simply the first few. */
private fun rollHolding(vararg values: Int, held: Set<Int>): RulesDice =
    RulesDice(values.mapIndexed { index, value -> ExampleDie(value, counts = index in held) }, score = null, fadedNotHeld = true)

/** What a Hit List [target] example writes for a place any die fills. */
private const val ANY_PLACE = 0

/** A Hit List target's places, left to right - [ANY_PLACE] where any die will do - worth [points], with its tile. */
private fun target(vararg places: Int, points: Int): RulesDice =
    RulesDice(
        places.map { ExampleDie(it) },
        score = points(points),
        isTarget = true,
        tile = RulesTile.Target(HitTarget(places.map { it.takeIf { place -> place != ANY_PLACE } }, points)),
    )

/** The target the Hit List page's examples chase: 4 1 3 2 and an open place, worth 20. */
private val EXAMPLE_TARGET = HitTarget(listOf(4, 1, 3, 2, null), points = 20)

/** Five coloured example dice for Tricolour, the first [counting] of which make the category. */
private fun colouredDice(vararg dice: Pair<Int, DieColour>, counting: Int = dice.size, score: RulesScore): RulesDice =
    RulesDice(dice.mapIndexed { index, (value, colour) -> ExampleDie(value, counts = index < counting, colour = colour) }, score)

/**
 * The rules explained in the player's own words, not the rulebook's - one page per idea, swiped
 * (or picked from the tab rows) rather than scrolled past as one long page, so each (upper section,
 * lower section, the joker rule, the house rule tie-break, one page per non-Standard game mode and
 * one per modifier) gets its own moment rather than blurring into the next.
 *
 * Grouped as the top tab row shows them: the rules themselves, then the game modes, then the
 * modifiers - each of the last two opening on an overview of what they are. A new mode or modifier
 * is a page in its group (and a mode that counts on the Leaderboard is named in `rules_modes_3`).
 *
 * Kept in step with the actual rules engine: `ScoreCategory`'s fixed values (25/30/40/50/100),
 * `ScoreCalculator`'s joker rule priority, `game/TieBreak.kt`'s criterion order (see its own
 * doc comment), and each `GameMode`'s rolls, dice and timer - a rule change there should be
 * echoed here.
 */
private val RULES_GROUPS = listOf(
    RulesGroup(
        label = Res.string.rules_group_gameplay,
        pages = listOf(
            RulesPage(
                title = Res.string.rules_how_to_play_title,
                tabLabel = Res.string.rules_how_to_play_tab,
                blocks = listOf(
                    text(Res.string.rules_how_to_play_1),
                    text(Res.string.rules_how_to_play_2),
                    text(Res.string.rules_how_to_play_3),
                    text(Res.string.rules_how_to_play_4),
                    RulesIllustration,
                ),
            ),
            RulesPage(
                title = Res.string.rules_upper_section_title,
                tabLabel = Res.string.rules_upper_section_tab,
                blocks = listOf(
                    text(Res.string.rules_upper_section_1),
                    RulesCategory(named(Res.string.score_fives), Res.string.rules_upper_section_2, dice(5, 5, 5, 2, 1, counting = 3, score = points(15))),
                    text(Res.string.rules_upper_section_3),
                ),
            ),
            RulesPage(
                title = Res.string.rules_lower_section_title,
                tabLabel = Res.string.rules_lower_section_tab,
                blocks = listOf(
                    text(Res.string.rules_lower_section_1),
                    RulesCategory(mark("3x"), Res.string.rules_lower_section_2, dice(5, 5, 5, 2, 6, counting = 3, score = points(23))),
                    RulesCategory(mark("4x"), Res.string.rules_lower_section_3, dice(4, 4, 4, 4, 1, counting = 4, score = points(17))),
                    RulesCategory(named(Res.string.score_full_house), Res.string.rules_lower_section_4, dice(3, 3, 3, 6, 6, score = points(25))),
                    RulesCategory(named(Res.string.score_small_straight), Res.string.rules_lower_section_5, dice(2, 3, 4, 5, 2, counting = 4, score = points(30))),
                    RulesCategory(named(Res.string.score_large_straight), Res.string.rules_lower_section_6, dice(1, 2, 3, 4, 5, score = points(40))),
                    RulesCategory(mark("5x"), Res.string.rules_lower_section_7, dice(6, 6, 6, 6, 6, score = points(50))),
                    RulesCategory(named(Res.string.score_chance), Res.string.rules_lower_section_8, dice(2, 3, 5, 5, 6, score = points(21))),
                ),
            ),
            RulesPage(
                title = Res.string.rules_5x_and_joker_title,
                tabLabel = Res.string.rules_5x_and_joker_tab,
                blocks = listOf(
                    text(Res.string.rules_5x_and_joker_1),
                    text(Res.string.rules_5x_and_joker_2),
                    text(Res.string.rules_5x_and_joker_3),
                    RulesStep(1, Res.string.rules_5x_and_joker_4,
                        dice(4, 4, 4, 4, 4, score = points(20, 100)),
                    ),
                    RulesStep(2, Res.string.rules_5x_and_joker_5),
                    RulesStep(3, Res.string.rules_5x_and_joker_6),
                ),
            ),
            RulesPage(
                title = Res.string.rules_tie_breaks_title,
                tabLabel = Res.string.rules_tie_breaks_tab,
                blocks = listOf(
                    text(Res.string.rules_tie_breaks_1),
                    RulesStep(1, Res.string.rules_tie_breaks_2),
                    RulesStep(2, Res.string.rules_tie_breaks_3),
                    RulesStep(3, Res.string.rules_tie_breaks_4),
                    RulesStep(4, Res.string.rules_tie_breaks_5),
                    RulesStep(5, Res.string.rules_tie_breaks_6),
                    RulesStep(6, Res.string.rules_tie_breaks_7),
                    text(Res.string.rules_tie_breaks_8),
                ),
            ),
        ),
    ),
    RulesGroup(
        label = Res.string.rules_group_modes,
        pages = listOf(
            RulesPage(
                title = Res.string.rules_modes_title,
                tabLabel = Res.string.rules_modes_tab,
                blocks = listOf(
                    text(Res.string.rules_modes_1),
                    text(Res.string.rules_modes_2),
                    text(Res.string.rules_modes_3),
                    text(Res.string.rules_modes_4),
                ),
            ),
            RulesPage(
                title = Res.string.rules_tricolour_title,
                tabLabel = Res.string.rules_tricolour_tab,
                blocks = listOf(
                    text(Res.string.rules_tricolour_1),
                    text(Res.string.rules_tricolour_2),
                    RulesCategory(named(Res.string.score_reds), Res.string.rules_tricolour_3,
                        colouredDice(2 to DieColour.RED, 5 to DieColour.RED, 1 to DieColour.RED, 6 to DieColour.RED, 3 to DieColour.RED, score = points(40)),
                    ),
                    RulesCategory(named(Res.string.score_yellows), Res.string.rules_tricolour_4,
                        colouredDice(4 to DieColour.YELLOW, 4 to DieColour.YELLOW, 1 to DieColour.YELLOW, 5 to DieColour.YELLOW, 2 to DieColour.YELLOW, score = points(40)),
                    ),
                    RulesCategory(named(Res.string.score_blues), Res.string.rules_tricolour_5,
                        colouredDice(6 to DieColour.BLUE, 3 to DieColour.BLUE, 3 to DieColour.BLUE, 2 to DieColour.BLUE, 5 to DieColour.BLUE, score = points(40)),
                    ),
                    RulesCategory(named(Res.string.score_coloured_house), Res.string.rules_tricolour_6,
                        colouredDice(1 to DieColour.RED, 4 to DieColour.RED, 6 to DieColour.RED, 2 to DieColour.BLUE, 5 to DieColour.BLUE, score = points(25)),
                    ),
                    text(Res.string.rules_tricolour_7),
                    text(Res.string.rules_tricolour_8),
                ),
            ),
            RulesPage(
                title = Res.string.rules_quickfire_title,
                tabLabel = Res.string.rules_quickfire_tab,
                blocks = listOf(
                    text(Res.string.rules_quickfire_1),
                    text(Res.string.rules_quickfire_2),
                    text(Res.string.rules_quickfire_3),
                    text(Res.string.rules_quickfire_4),
                    text(Res.string.rules_quickfire_5),
                    text(Res.string.rules_quickfire_6),
                ),
            ),
            RulesPage(
                title = Res.string.rules_stud_title,
                tabLabel = Res.string.rules_stud_tab,
                blocks = listOf(
                    text(Res.string.rules_stud_1),
                    text(Res.string.rules_stud_2),
                    text(Res.string.rules_stud_3),
                    diceCounting(6, 6, 6, 6, 5, 2, 1, counting = setOf(0, 1, 2, 3, 4), score = points(29)),
                    text(Res.string.rules_stud_4),
                    diceCounting(1, 2, 3, 4, 5, 6, 2, counting = setOf(0, 1, 2, 3, 4), score = points(40)),
                    text(Res.string.rules_stud_5),
                    diceCounting(1, 2, 3, 4, 5, 6, 2, counting = setOf(1, 2, 3, 4, 5), score = points(40)),
                    text(Res.string.rules_stud_6),
                    text(Res.string.rules_stud_7),
                ),
            ),
            RulesPage(
                title = Res.string.rules_third_wind_title,
                tabLabel = Res.string.rules_third_wind_tab,
                blocks = listOf(
                    text(Res.string.rules_third_wind_1),
                    text(Res.string.rules_third_wind_2),
                    text(Res.string.rules_third_wind_3),
                    text(Res.string.rules_third_wind_4),
                    text(Res.string.rules_third_wind_5),
                    RulesStep(1, Res.string.rules_third_wind_6, roll(5, 5, 5, 2, 1, held = 3)),
                    RulesStep(2, Res.string.rules_third_wind_7, roll(5, 5, 5, 5, 3, held = 4)),
                    RulesStep(3, Res.string.rules_third_wind_8),
                    RulesStep(4, Res.string.rules_third_wind_9,
                        dice(5, 5, 5, 5, 6, counting = 4, score = points(20)),
                    ),
                    text(Res.string.rules_third_wind_10),
                    text(Res.string.rules_third_wind_11),
                    text(Res.string.rules_third_wind_12),
                ),
            ),
            RulesPage(
                title = Res.string.rules_hit_list_title,
                tabLabel = Res.string.rules_hit_list_tab,
                blocks = listOf(
                    text(Res.string.rules_hit_list_1),
                    text(Res.string.rules_hit_list_2),
                    target(4, 1, 3, 2, ANY_PLACE, points = 20),
                    text(Res.string.rules_hit_list_7),
                    RulesCategory(named(Res.string.rules_name_hit), Res.string.rules_hit_list_3,
                        dice(2, 4, 1, 3, 5, counting = 4, score = points(20)).showing(RulesTile.Target(EXAMPLE_TARGET)),
                    ),
                    RulesCategory(named(Res.string.rules_name_exact_hit), Res.string.rules_hit_list_4,
                        dice(4, 1, 3, 2, 6, counting = 4, score = points(40)).showing(RulesTile.Target(EXAMPLE_TARGET)),
                    ),
                    RulesCategory(named(Res.string.rules_name_partial_hit), Res.string.rules_hit_list_5,
                        diceCounting(4, 1, 6, 2, 5, counting = setOf(0, 1, 3), score = points(10)).showing(RulesTile.Target(EXAMPLE_TARGET)),
                    ),
                    text(Res.string.rules_hit_list_8),
                    RulesCategory(mark("Alibi"), Res.string.rules_hit_list_9,
                        dice(2, 4, 1, 3, 5, counting = 4, score = points(20)).showing(RulesTile.Alibi),
                    ),
                    text(Res.string.rules_hit_list_10),
                    text(Res.string.rules_hit_list_11),
                    RulesStep(1, Res.string.rules_hit_list_12, rollHolding(4, 1, 5, 6, 2, held = setOf(0, 1, 4)).showing(RulesTile.Target(EXAMPLE_TARGET))),
                    RulesStep(2, Res.string.rules_hit_list_13, rollHolding(4, 1, 3, 6, 2, held = setOf(0, 1, 2, 4)).showing(RulesTile.Target(EXAMPLE_TARGET))),
                    RulesStep(3, Res.string.rules_hit_list_14),
                    RulesStep(4, Res.string.rules_hit_list_15,
                        dice(4, 1, 3, 2, 2, counting = 4, score = points(40)).showing(RulesTile.Target(EXAMPLE_TARGET)),
                    ),
                    text(Res.string.rules_hit_list_16),
                    text(Res.string.rules_hit_list_17),
                ),
            ),
        ),
    ),
    RulesGroup(
        label = Res.string.rules_group_modifiers,
        pages = listOf(
            RulesPage(
                title = Res.string.rules_modifiers_title,
                tabLabel = Res.string.rules_modifiers_tab,
                blocks = listOf(
                    text(Res.string.rules_modifiers_1),
                    text(Res.string.rules_modifiers_2),
                ),
            ),
            RulesPage(
                title = Res.string.rules_extended_scores_title,
                tabLabel = Res.string.rules_extended_scores_tab,
                blocks = listOf(
                    text(Res.string.rules_extended_scores_1),
                    RulesCategory(named(Res.string.score_two_pair), Res.string.rules_extended_scores_2, dice(4, 4, 2, 2, 4, counting = 4, score = points(12))),
                    RulesCategory(named(Res.string.score_evens), Res.string.rules_extended_scores_3, dice(6, 4, 2, 3, 1, counting = 3, score = points(12))),
                    RulesCategory(named(Res.string.score_odds), Res.string.rules_extended_scores_4, dice(5, 3, 3, 6, 2, counting = 3, score = points(11))),
                    text(Res.string.rules_extended_scores_5),
                ),
            ),
            RulesPage(
                title = Res.string.rules_number_of_rolls_title,
                tabLabel = Res.string.rules_number_of_rolls_tab,
                blocks = listOf(
                    text(Res.string.rules_number_of_rolls_1),
                    text(Res.string.rules_number_of_rolls_2),
                ),
            ),
            RulesPage(
                title = Res.string.rules_stored_rolls_title,
                tabLabel = Res.string.rules_stored_rolls_tab,
                blocks = listOf(
                    text(Res.string.rules_stored_rolls_1),
                    text(Res.string.rules_stored_rolls_2),
                    text(Res.string.rules_stored_rolls_3),
                ),
            ),
            RulesPage(
                title = Res.string.rules_turn_timer_title,
                tabLabel = Res.string.rules_turn_timer_tab,
                blocks = listOf(
                    text(Res.string.rules_turn_timer_1),
                    RulesTurnTimer,
                    text(Res.string.rules_turn_timer_2),
                ),
            ),
            RulesPage(
                title = Res.string.rules_unlucky_dice_title,
                tabLabel = Res.string.rules_unlucky_dice_tab,
                blocks = listOf(
                    text(Res.string.rules_unlucky_dice_1),
                    lockedDice(5, 5, 5, 2, 5, lockedIndex = 4, counting = 3, score = points(15)),
                    text(Res.string.rules_unlucky_dice_2),
                    text(Res.string.rules_unlucky_dice_3),
                    text(Res.string.rules_unlucky_dice_4),
                ),
            ),
        ),
    ),
)

/** Every page, group after group - the order the pager swipes through them in. */
private val RULES_PAGES = RULES_GROUPS.flatMap { it.pages }

/** Where each of [RULES_GROUPS] starts in [RULES_PAGES], then one past the last page. */
private val GROUP_STARTS = RULES_GROUPS.runningFold(0) { start, group -> start + group.pages.size }

/** Which of [RULES_GROUPS] the page at [page] is in. */
private fun groupOf(page: Int): Int = GROUP_STARTS.indexOfLast { it <= page }.coerceAtMost(RULES_GROUPS.lastIndex)

/** How far in from each end of the tab row its tabs are hidden outright while there are more to
 * scroll to: the chevron's glyph (24dp, centred in its 48dp button, so ending 36dp in) plus a small
 * gap, so no label is ever drawn underneath it. */
private val TAB_EDGE_CLEAR = 40.dp

/** Past [TAB_EDGE_CLEAR], how far the tabs take to fade back in to full strength. */
private val TAB_EDGE_FADE = 24.dp

/** How much scrolling is left before an end's chevron and fade start easing away - so they fade out
 * as the last tab comes into view rather than switching off at the very end. */
private val TAB_EDGE_EASE = 48.dp

/** How much of the tab row's visible width a chevron tap scrolls by - less than all of it, so the
 * tab that was cut off at the edge is still in view afterwards, now whole. */
private const val TAB_CHEVRON_SCROLL_FRACTION = 0.6f

/**
 * The Rules page: two rows of tabs over a [HorizontalPager] of every [RulesPage]. The top row picks
 * one of [RULES_GROUPS] - the rules themselves, the game modes or the modifiers - and the row under
 * it has a tab per page of that group, so any group is a tap away from any page (Material's primary
 * and secondary tabs). The pager runs through every page, group after group: a swipe past a group's
 * last page carries on into the next group, and the top row follows. A tab snaps straight to its page
 * rather than animating there: an animated jump slides through, and so builds, every page between
 * (up to a whole group's), which a phone feels as lag - and keeps the rows scrolling the while, so a
 * second tap meanwhile only stops the scroll instead of reaching its tab. The page row scrolls, as a
 * group can have more pages than fit - a row of dots stopped saying where you were, or letting you
 * get to the last page, once there were more than a handful.
 *
 * A scrolling tab row gives no sign on its own that there's more of it: on a phone the first three
 * tabs can end right at the edge, so the row looks like all there is. A fade alone didn't fix that -
 * it only shows when a label happens to be under it, and with the selected tab centred the next one
 * can start just past the edge, leaving the fade over empty space. So each end with tabs beyond it
 * gets a [TabScrollChevron], which is there whatever the labels' widths, over a [fadeOffscreenEdges]
 * fade that keeps a cut-off label from running into it. Both rows and their tabs carry collection
 * semantics so TalkBack says "Tab, 1 of 3" and "Tab, 1 of 6", the spoken form of the same hint.
 */
@Composable
fun RulesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { RULES_PAGES.size })
    val coroutineScope = rememberCoroutineScope()
    // Only the group is read here, not the page: a page change then rebuilds only what shows the page (the two tabs
    // whose selection changes, the page row's indicator, the scrollbar and the footer), not the whole screen.
    val currentGroup by remember { derivedStateOf { groupOf(pagerState.currentPage) } }

    ScreenScaffold(title = stringResource(Res.string.rules_title), onBack = onBack, modifier = modifier) {
        GroupTabRow(
            currentGroup = currentGroup,
            // To the group's first page - its overview, for the modes and the modifiers. A tap on the group already
            // showing leaves the page where it is.
            onSelect = { group -> if (group != currentGroup) coroutineScope.launch { pagerState.scrollToPage(GROUP_STARTS[group]) } },
        )
        PageTabRow(pagerState = pagerState, group = currentGroup, onSelect = { page -> coroutineScope.launch { pagerState.scrollToPage(page) } })

        // The footer floats over the pages rather than taking a row of its own: pinned to the bottom,
        // with the page text scrolling behind it, and each page padded by the footer's measured height
        // (so a large font still clears it) plus a gap, so the last line can always scroll above it.
        var footerHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val pageBottomPadding = with(density) { footerHeightPx.toDp() } + PAGE_FOOTER_GAP
        // Held here rather than inside each page, so the scrollbar beside the pager can follow whichever
        // page is showing.
        val pageScrollStates = remember { List(RULES_PAGES.size) { ScrollState(initial = 0) } }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // A gap between pages wider than the screen's side margin, so a page never shows a sliver
            // of its neighbour's text at its edge.
            HorizontalPager(state = pagerState, pageSpacing = PAGE_SPACING, modifier = Modifier.fillMaxSize()) { page ->
                val rulesPage = RULES_PAGES[page]
                val shownBlocks = rememberShownBlocks(rulesPage.blocks.size)
                Column(modifier = Modifier.fillMaxSize().verticalScroll(pageScrollStates[page]).padding(bottom = pageBottomPadding)) {
                    Text(
                        text = stringResource(rulesPage.title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    for (block in rulesPage.blocks.take(shownBlocks)) {
                        RulesBlockView(block)
                    }
                }
            }

            PageScrollbar(pagerState, pageScrollStates)

            PageCountFooter(
                pagerState = pagerState,
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { footerHeightPx = it.height },
            )
        }
    }
}

/** The top row: a tab per one of [RULES_GROUPS], [currentGroup] selected; [onSelect] is handed a tapped tab's group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupTabRow(currentGroup: Int, onSelect: (Int) -> Unit) {
    val groupLabels = RULES_GROUPS.map { stringResource(it.label) }
    val scrollState = rememberScrollState()
    val placements = remember { TabPlacements() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // A real Box, so the chevrons' align lands on their actual parent (see UI.md's gotchas).
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tabWidths = groupTabWidths(groupLabels, constraints.maxWidth)
        KeepTabInView(scrollState, placements) { currentGroup.toFloat() }
        PrimaryScrollableTabRow(
            // Never changes, so the row never re-centres itself - see KeepTabInView. The indicator and each tab's
            // selected state follow currentGroup themselves.
            selectedTabIndex = 0,
            scrollState = scrollState,
            // Transparent over the backdrop, like the app bar above it, and flush with the page text.
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(currentGroup, matchContentSize = true),
                    width = Dp.Unspecified,
                    height = TAB_INDICATOR_HEIGHT,
                )
            },
            divider = { TabRowDivider() },
            // Each tab is sized by groupTabWidths instead.
            minTabWidth = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fadeOffscreenEdges(scrollState, clearWidth = TAB_EDGE_CLEAR, fadeWidth = TAB_EDGE_FADE, easeDistance = TAB_EDGE_EASE)
                .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = RULES_GROUPS.size) },
        ) {
            groupLabels.forEachIndexed { index, label ->
                val width = tabWidths[index]
                Tab(
                    selected = index == currentGroup,
                    onClick = { onSelect(index) },
                    text = { Text(text = label, maxLines = 1, softWrap = false) },
                    modifier = remember(width, placements, index, rtl) { Modifier.width(width).rowTab(placements, index, rtl) },
                )
            }
        }
        TabScrollChevron(scrollState = scrollState, forward = false, modifier = Modifier.align(Alignment.CenterStart))
        TabScrollChevron(scrollState = scrollState, forward = true, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/**
 * The row under [GroupTabRow]: a tab per page of [group], the current one selected; [onSelect] is handed a tapped tab's
 * page (its index in [RULES_PAGES]). Reads the current page only where it's needed - each tab's selection (through
 * [derivedStateOf], so a page change recomposes only the two tabs it changes), the indicator's layout, and
 * [KeepTabInView] - so the row itself isn't rebuilt on every page change.
 */
@Composable
private fun PageTabRow(pagerState: PagerState, group: Int, onSelect: (Int) -> Unit) {
    val groupStart = GROUP_STARTS[group]
    val groupPages = RULES_GROUPS[group].pages
    // A group's page row starts scrolled to its start, not wherever the last group's was left.
    val scrollState = remember(group) { ScrollState(initial = 0) }
    val placements = remember(group) { TabPlacements() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduceMotion = LocalReduceMotion.current
    val coroutineScope = rememberCoroutineScope()
    // A tap snaps the pager straight to its page (animating the pages would cost a frame of two whole pages), but the
    // indicator slides there through the tabs in between, as a swipe would carry it: this holds where it is while
    // it does, and the pager's own position is shown the rest of the time.
    val tapSlide = remember(group) { Animatable(0f) }
    var tapSliding by remember(group) { mutableStateOf(false) }
    // Where the indicator is, in tabs from the group's first: read in layout and by KeepTabInView, never in composition.
    val indicatorPosition = { if (tapSliding) tapSlide.value else pagerState.currentPage + pagerState.currentPageOffsetFraction - groupStart }
    // Runs in the tap itself, with the indicator pinned where it was *before* the pager moves: starting it from an effect
    // instead let the pager's jump reach the indicator first, which showed it at the new tab for a frame before sliding.
    val slideTo: (Int) -> Unit = { page ->
        val from = indicatorPosition()
        coroutineScope.launch {
            try {
                tapSlide.snapTo(from)
                tapSliding = true
                onSelect(page)
                if (!reduceMotion) {
                    val to = (page - groupStart).toFloat()
                    val millis = (TAP_SLIDE_BASE_MILLIS + TAP_SLIDE_PER_TAB_MILLIS * abs(to - from)).coerceAtMost(TAP_SLIDE_MAX_MILLIS)
                    tapSlide.animateTo(to, tween(millis.toInt(), easing = FastOutSlowInEasing))
                }
            } finally {
                // Back to following the pager - which is already on the page, so nothing moves.
                tapSliding = false
            }
        }
    }
    // A real Box, so the chevrons' align lands on their actual parent (see UI.md's gotchas).
    Box(modifier = Modifier.fillMaxWidth()) {
        // Follows the indicator, so the row scrolls along with it as it does through a swipe, rather than ahead of it.
        KeepTabInView(scrollState, placements) { indicatorPosition() }
        SecondaryScrollableTabRow(
            // Never changes, so the row never re-centres itself - see KeepTabInView. The indicator follows the pager,
            // and each tab's selected state the current page.
            selectedTabIndex = 0,
            scrollState = scrollState,
            // Transparent over the backdrop, like the app bar above it, and flush with the page text.
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            // Slides with the pages as they're swiped, rather than jumping once the next page is
            // the current one - see pagerIndicatorLayout.
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
                        pagerIndicatorLayout(measurable, constraints, tabPositions, indicatorPosition())
                    }.testTag(TAB_INDICATOR_TAG),
                    height = SECONDARY_INDICATOR_HEIGHT,
                )
            },
            divider = { HorizontalDivider() },
            modifier = Modifier
                .fillMaxWidth()
                .fadeOffscreenEdges(scrollState, clearWidth = TAB_EDGE_CLEAR, fadeWidth = TAB_EDGE_FADE, easeDistance = TAB_EDGE_EASE)
                .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = groupPages.size) },
        ) {
            groupPages.forEachIndexed { index, rulesPage ->
                val page = groupStart + index
                val selected by remember(page) { derivedStateOf { pagerState.currentPage == page } }
                Tab(
                    selected = selected,
                    onClick = { slideTo(page) },
                    text = { Text(text = stringResource(rulesPage.tabLabel), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = remember(placements, index, rtl) { Modifier.rowTab(placements, index, rtl) },
                )
            }
        }
        TabScrollChevron(scrollState = scrollState, forward = false, modifier = Modifier.align(Alignment.CenterStart))
        TabScrollChevron(scrollState = scrollState, forward = true, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/** How long the indicator takes to slide to a tapped tab: this much, plus [TAP_SLIDE_PER_TAB_MILLIS] for each tab it passes, up to [TAP_SLIDE_MAX_MILLIS]. */
private const val TAP_SLIDE_BASE_MILLIS = 160f
private const val TAP_SLIDE_PER_TAB_MILLIS = 50f
private const val TAP_SLIDE_MAX_MILLIS = 400f

/** A tab's place in its row: recorded for [KeepTabInView], and announced as its position in the row ("Tab, 2 of 6"). */
private fun Modifier.rowTab(placements: TabPlacements, index: Int, rtl: Boolean): Modifier = this
    .recordPlacement(placements, index, rtl)
    .semantics { collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1) }

/** The scrollbar for whichever page is showing: in the screen's right-hand margin, beside the text rather than over it
 * (as on Styles), and only while that page is too long to fit. */
@Composable
private fun BoxScope.PageScrollbar(pagerState: PagerState, pageScrollStates: List<ScrollState>) {
    VerticalScrollbar(
        scrollState = pageScrollStates[pagerState.currentPage],
        width = SCREEN_MARGIN,
        modifier = Modifier.align(Alignment.TopEnd).offset(x = SCREEN_MARGIN),
    )
}

/** Body text of every block, styled by its [parseInlineMarkup] markers - a scoring category's name
 * in the same gold monospace as a [RulesCategory]'s heading. TalkBack hears it with "pts" spoken
 * as "points" ([spokenPoints]). */
@Composable
private fun RulesBodyText(resource: StringResource, modifier: Modifier = Modifier) {
    val text = stringResource(resource)
    val categoryStyle = SpanStyle(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
    val styled = remember(text, categoryStyle) { parseInlineMarkup(text.keepCategoryNamesWhole(), codeStyle = categoryStyle) }
    val spoken = spokenPoints(styled.text)
    Text(
        text = styled,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { contentDescription = spoken },
    )
}

/** Swaps the spaces inside each `backticked` category name for non-breaking ones, so a name like
 * "Small Straight" never wraps across two lines. */
private fun String.keepCategoryNamesWhole(): String =
    replace(Regex("`[^`]*`")) { match -> match.value.replace(' ', '\u00A0') }

/**
 * "15pts" as "15 points" (and "+" as "plus"), so TalkBack doesn't read the abbreviation out as letters. What counts as
 * an amount of points is [rules_points_short]'s own shape - so a language that writes it "15 pt" is found as that.
 */
@Composable
private fun spokenPoints(text: String): String {
    val shape = stringResource(Res.string.rules_points_short)
    // Any script's digits: the amounts are drawn in the strings' numerals ("٢٥"), and \d is only 0-9.
    val amount = remember(shape) { Regex(shape.split("%1\$d").joinToString("(\\p{Nd}+)") { Regex.escape(it) }) }
    val plus = stringResource(Res.string.rules_plus_spoken)
    val spoken = remember(text, shape, plus) { text.replace(amount) { "\u0000${it.groupValues[1]}\u0000" }.replace(" + ", " $plus ") }
    return spoken.split('\u0000').mapIndexed { index, part -> if (index % 2 == 1) part.digitsValue().let { pluralStringResource(Res.plurals.rules_points_spoken, it, it) } else part }.joinToString("")
}

/** The number these digits, of any script, write ("25" or "٢٥" is 25). */
private fun String.digitsValue(): Int = fold(0) { value, digit -> value * 10 + digit.digitToInt() }

/** What [score] is drawn as: "20pts", or "20pts + 100pts". */
@Composable
private fun RulesScore.pointsText(): String {
    val parts = amounts.map { stringResource(Res.string.rules_points_short, it) }
    return parts.reduce { first, second -> stringResource(Res.string.rules_points_sum, first, second) }
}

/** Space under every block, so paragraphs, steps and categories are spaced alike. */
private val BLOCK_GAP = 10.dp

/** Space under a [RulesCategory] - twice [BLOCK_GAP], so each category, with its example, reads as
 * its own entry rather than running into the next. */
private val CATEGORY_GAP = 20.dp

/** How far a step's text (and its example) hangs in from its number. */
private val STEP_INDENT = 24.dp

/** Draws one [RulesBlock] of a page. */
@Composable
private fun RulesBlockView(block: RulesBlock) {
    when (block) {
        is RulesText -> RulesBodyText(block.text, modifier = Modifier.padding(bottom = BLOCK_GAP))
        RulesIllustration -> RulesIllustrationView(modifier = Modifier.padding(top = 24.dp, bottom = BLOCK_GAP))
        // One TalkBack stop describing the example, in place of the badge's own live region - which
        // would announce "time running out" as if a turn on this page really were.
        RulesTurnTimer -> {
            val timerExample = pluralStringResource(Res.plurals.rules_timer_example_cd, TURN_TIMER_EXAMPLE_SECONDS, TURN_TIMER_EXAMPLE_SECONDS)
            TurnTimerBadge(
                secondsRemaining = TURN_TIMER_EXAMPLE_SECONDS,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = BLOCK_GAP)
                    .clearAndSetSemantics {
                        contentDescription = timerExample
                    },
            )
        }
        is RulesDice -> RulesDiceRow(block, modifier = Modifier.padding(bottom = BLOCK_GAP))
        is RulesStep -> Column(modifier = Modifier.padding(bottom = BLOCK_GAP).semantics(mergeDescendants = true) {}) {
            Row {
                Text(
                    text = stringResource(Res.string.rules_step_number, block.number),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(STEP_INDENT),
                )
                RulesBodyText(block.text, modifier = Modifier.weight(1f))
            }
            block.example?.let { RulesDiceRow(it, modifier = Modifier.padding(start = STEP_INDENT, top = 6.dp)) }
        }
        // One TalkBack stop for the name, what it takes and the example, not three.
        is RulesCategory -> Column(modifier = Modifier.padding(bottom = CATEGORY_GAP).semantics(mergeDescendants = true) {}) {
            Text(
                text = when (val name = block.name) {
                    is RulesName.Mark -> name.text
                    is RulesName.Named -> stringResource(name.resource)
                },
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            RulesBodyText(block.description)
            RulesDiceRow(block.example, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** The size of each die in a [RulesDiceRow]. */
private val EXAMPLE_DIE_SIZE = 28.dp

/** How faint a die that doesn't count towards the example's category is drawn. */
private const val EXAMPLE_DIE_FADED_ALPHA = 0.35f

/**
 * Five example dice in the Classic style (each in its own colour in a Tricolour example), then what
 * they score. Dice that don't count towards the category are faded, so "three 5s, plus two others"
 * reads at a glance. TalkBack hears it as one sentence - the dice, which don't count and the score -
 * rather than five unlabelled images.
 */
@Composable
private fun RulesDiceRow(example: RulesDice, modifier: Modifier = Modifier) {
    val spoken = example.spokenDescription()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        val anyPlaceColor = MaterialTheme.colorScheme.onSurfaceVariant
        for (die in example.dice) {
            val style = die.colour?.let { IvoryDiceStyle.recoloured(it.palette) } ?: IvoryDiceStyle
            val dieModifier = Modifier
                .padding(end = 6.dp)
                .size(EXAMPLE_DIE_SIZE)
                .graphicsLayer { alpha = if (die.counts || die.locked) 1f else EXAMPLE_DIE_FADED_ALPHA }
            if (example.isTarget && die.anyPlace) {
                Box(modifier = dieModifier.drawBehind { drawAnyPlace(anyPlaceColor) })
            } else if (die.locked) {
                Box(modifier = dieModifier) {
                    style.Die(value = die.value, held = false, modifier = Modifier.fillMaxSize())
                    LockedChains(shape = style.shadowShape(die.value, 0, null), reach = style.lockedChainReach, modifier = Modifier.matchParentSize())
                }
            } else {
                style.Die(value = die.value, held = false, modifier = dieModifier)
            }
        }
        example.score?.let { score ->
            Text(
                text = stringResource(Res.string.rules_example_equals, score.pointsText()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        example.tile?.let { tile ->
            Spacer(modifier = Modifier.weight(1f))
            RulesTileView(tile, example)
        }
    }
}

/** [tile] as the board draws it for [example]'s dice - see [RulesTile]. */
@Composable
private fun RulesTileView(tile: RulesTile, example: RulesDice) {
    when (tile) {
        is RulesTile.Target -> {
            val dice = example.dice.map { Die(value = it.value) }
            val matches = if (example.isTarget) null else tile.target.matches(dice)
            CategoryTile(
                category = ScoreCategory.TARGET_1,
                // Lit, as on the board, for a hit - not a partial one.
                highlighted = !example.isTarget && tile.target.isHit(dice),
                target = tile.target,
                matches = matches,
            )
        }
        RulesTile.Alibi -> CategoryTile(category = ScoreCategory.ALIBI, highlighted = true)
    }
}

/** A target's open place: a die's outline, dashed, with nothing on it. */
private fun DrawScope.drawAnyPlace(color: Color) {
    val stroke = ANY_PLACE_STROKE.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(size.minDimension * ANY_PLACE_CORNER_FRACTION),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ANY_PLACE_DASH.toPx(), ANY_PLACE_DASH.toPx()))),
    )
}

private val ANY_PLACE_STROKE = 1.5.dp
private val ANY_PLACE_DASH = 3.dp
private const val ANY_PLACE_CORNER_FRACTION = 0.2f

/**
 * "Example: 5, 5, 5, 2, 6. The 2 and 6 don't count. Scores 23 points." - the row read aloud. A roll
 * on its way to a hand says which dice are held instead: "Example roll: 6, 6, 6, 2, 3. Held: 6, 6
 * and 6." A Hit List target says its places: "Example target: 4, 1, 3, 2, any. Worth 20 points."
 */
@Composable
private fun RulesDice.spokenDescription(): String {
    val anyPlace = stringResource(Res.string.rules_example_any_place_spoken)
    @Composable
    fun ExampleDie.spoken() = colour?.let {
        // A whole phrase per colour, not the colour's name lower-cased: not every language lower-cases it mid-sentence.
        val phrase = when (it) {
            DieColour.RED -> Res.string.rules_example_red_die_spoken
            DieColour.YELLOW -> Res.string.rules_example_yellow_die_spoken
            DieColour.BLUE -> Res.string.rules_example_blue_die_spoken
        }
        stringResource(phrase, value)
    } ?: value.localised()
    if (isTarget) {
        val places = joinClauses(dice.map { if (it.anyPlace) anyPlace else it.spoken() })
        return joinSentences(
            listOfNotNull(
                stringResource(Res.string.rules_example_target_spoken, places),
                score?.let { stringResource(Res.string.rules_example_worth_spoken, spokenPoints(it.pointsText())) },
            ),
        )
    }
    val tileSentence = when (val shown = tile) {
        is RulesTile.Target -> {
            val hand = dice.map { Die(value = it.value) }
            stringResource(Res.string.rules_example_tile_target_spoken, targetProgress(shown.target, shown.target.matches(hand), shown.target.score(hand)))
        }
        RulesTile.Alibi -> stringResource(Res.string.rules_example_tile_alibi_spoken)
        null -> null
    }
    val all = joinClauses(dice.map { it.spoken() })
    if (fadedNotHeld) {
        val held = dice.filter { it.counts }.map { it.spoken() }
        val heldSentence = if (held.isEmpty()) {
            stringResource(Res.string.rules_example_nothing_held_spoken)
        } else {
            stringResource(Res.string.rules_example_held_spoken, spokenList(held))
        }
        return joinSentences(listOfNotNull(stringResource(Res.string.rules_example_roll_spoken, all), heldSentence, tileSentence))
    }
    val locked = dice.filter { it.locked }.map { it.spoken() }
    val ignored = dice.filterNot { it.counts || it.locked }.map { it.spoken() }
    return joinSentences(
        listOfNotNull(
            stringResource(Res.string.rules_example_spoken, all),
            locked.takeIf { it.isNotEmpty() }?.let { pluralStringResource(Res.plurals.rules_example_locked_spoken, it.size, spokenList(it)) },
            ignored.takeIf { it.isNotEmpty() }?.let { pluralStringResource(Res.plurals.rules_example_ignored_spoken, it.size, spokenList(it)) },
            score?.let { stringResource(Res.string.rules_example_scores_spoken, spokenPoints(it.pointsText())) },
            tileSentence,
        ),
    )
}

/** The seconds the Modifiers page's example timer is stopped at - inside the game's last few, so it flashes. */
private const val TURN_TIMER_EXAMPLE_SECONDS = 4

/** How many of a page's blocks are built in the frame it opens on - about a screenful. */
private const val FIRST_BLOCKS = 4

/** How many more of a page's blocks are built in each frame after that, until it's whole. */
private const val BLOCKS_PER_FRAME = 2

/**
 * How many of a page's [count] blocks to build so far: [FIRST_BLOCKS] at once, then [BLOCKS_PER_FRAME] more each
 * frame. Building a long page (Hit List's dozen example rows and tiles) all in the frame it opens on held that frame up
 * long enough to feel on a phone, when only its first screenful can be seen; the rest is below the fold, and is built
 * over the next few frames instead (a tenth of a second for the longest page), before anyone can scroll to it.
 */
@Composable
private fun rememberShownBlocks(count: Int): Int {
    var shown by remember { mutableIntStateOf(minOf(count, FIRST_BLOCKS)) }
    LaunchedEffect(count) {
        while (shown < count) {
            withFrameNanos { }
            shown = minOf(count, shown + BLOCKS_PER_FRAME)
        }
    }
    return shown
}

/** ScreenScaffold's side margin, which the pages' scrollbar sits in. */
private val SCREEN_MARGIN = 20.dp

/** The gap between two pages of the pager - twice [SCREEN_MARGIN], so neither page's text shows in
 * the other's margin when they're not quite lined up. */
private val PAGE_SPACING = SCREEN_MARGIN * 2

/** How tall the cup in [RulesIllustrationView] stands. */
private val ILLUSTRATION_CUP_HEIGHT = 104.dp

/** How big each die in [RulesIllustrationView] is. */
private val ILLUSTRATION_DIE_SIZE = 36.dp

/**
 * The Classic cup standing beside five Classic dice - always those, whatever the player has picked,
 * as the Rules describe the game rather than their table. They play like the main menu's logo:
 * tapping the cup shakes it, as in a game, and tapping the dice rolls them (see [playLogoRoll]),
 * landing back on the faces they started on; a tap mid-shake or mid-roll is ignored. Unlike the
 * menu's dice, they unlock nothing - "Not Those Dice!" is the menu's alone.
 *
 * Decoration all the same, so no TalkBack stop: raw taps rather than clickables, which would give
 * TalkBack an unnamed "double tap to activate" that does nothing a screen reader user could use.
 * Under reduced motion neither moves, as on the menu.
 */
@Composable
private fun RulesIllustrationView(modifier: Modifier = Modifier) {
    val cupStyle = ClassicGoldDiceCupStyle
    val diceStyle = IvoryDiceStyle
    val cupWidth = ILLUSTRATION_CUP_HEIGHT * (cupStyle.shape.gridWidth / cupStyle.shape.gridHeight)
    val reduceMotion = LocalReduceMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    // How far into a roll the dice are, or null at rest; and whether the cup is mid-shake.
    var rollMillis by remember { mutableStateOf<Float?>(null) }
    var cupShaking by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
    ) {
        cupStyle.Cup(
            rolling = cupShaking,
            tilted = false,
            modifier = Modifier
                .size(width = cupWidth, height = ILLUSTRATION_CUP_HEIGHT)
                .pointerInput(reduceMotion) {
                    detectTapGestures {
                        if (!cupShaking && !reduceMotion) {
                            cupShaking = true
                            scope.launch {
                                lifecycle.delayWhileResumed(CUP_SHAKE_MILLIS)
                                cupShaking = false
                            }
                        }
                    }
                },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.pointerInput(reduceMotion) {
                detectTapGestures {
                    if (rollMillis == null && !reduceMotion) {
                        scope.launch {
                            playLogoRoll { rollMillis = it }
                            rollMillis = null
                        }
                    }
                }
            },
        ) {
            // The menu's fan, die for die: the same faces in the same order, tilts and drops.
            LOGO_DICE.forEachIndexed { index, die ->
                val pose = rollMillis?.let { logoRollPose(index, die.value, it) }
                diceStyle.Die(
                    value = pose?.value ?: die.value,
                    held = false,
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .offset(y = die.drop - ILLUSTRATION_DIE_SIZE * (pose?.hop ?: 0f))
                        .size(ILLUSTRATION_DIE_SIZE)
                        .rotate(die.tilt + (pose?.spinDegrees ?: 0f)),
                )
            }
        }
    }
}

/**
 * Places the tab row's indicator under [pagePosition] - the pager's current page plus how far it's
 * been swiped towards the next (`currentPage + currentPageOffsetFraction`) - so it slides between
 * two tabs with the finger, its width easing from one label's to the other's. Read during layout,
 * so following a swipe re-places the indicator without recomposing the tab row.
 */
fun MeasureScope.pagerIndicatorLayout(
    measurable: Measurable,
    constraints: Constraints,
    tabPositions: List<TabPosition>,
    pagePosition: Float,
): MeasureResult {
    val position = pagePosition.coerceIn(0f, (tabPositions.size - 1).toFloat())
    val from = tabPositions[position.toInt()]
    val to = tabPositions[(position.toInt() + 1).coerceAtMost(tabPositions.size - 1)]
    val fraction = position - position.toInt()
    // Under the label, as the stock indicator sits (matchContentSize): centred on its tab, as wide as its label.
    // Each position's `left` is its tab's start (not its label's), and `width` its whole tab - a tab can be wider
    // than its label (a minimum width, or the tab's padding), so the tab's centre is `left + width / 2`.
    val width = lerp(from.contentWidth, to.contentWidth, fraction)
    val centre = lerp(from.left + from.width / 2, to.left + to.width / 2, fraction)
    val widthPx = width.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
    // Material then places the indicator itself, centred under the row's *selected* tab: in by half of that tab's
    // width less the indicator's own (SecondaryScrollableTabRow, for the tab `selectedTabIndex` names - always 0 here,
    // see PageTabRow; not clamped at 0 in material3 1.4.0, as it later is - RulesTabIndicatorTest catches an upgrade changing
    // it). Whatever that comes to is already added, so it's taken off - leaving it in puts the indicator out by half the
    // difference between tab 0's width and the shown tab's, which only shows once the tabs differ in width.
    val materialInset = (tabPositions[0].width.roundToPx() - placeable.width) / 2
    return layout(placeable.width, placeable.height) {
        // Relative, so a right-to-left row (whose tab positions count from the right) puts it under its tab, not mirrored away.
        placeable.placeRelative(x = (centre - width / 2).roundToPx() - materialInset, y = 0)
    }
}

/** Test tag of the page row's indicator, so a test can check it sits under the selected tab. */
internal const val TAB_INDICATOR_TAG = "rulesTabIndicator"

/** How far a [Tab]'s label is padded in from each side of it - Material's own padding, which the group row has to count in when it sizes its tabs. */
private val TAB_LABEL_PADDING = 16.dp

/**
 * How wide each group tab is, across [rowWidthPx]: all alike, as Material's fixed tabs are, while every label fits a
 * third - otherwise each as wide as its label needs, plus an equal share of what's left, so a long label ("Modificadores")
 * is never cut off and the row still spans the width. Only when the labels don't fit the row at all (a large font) does
 * it scroll, with the same chevrons as the page row.
 *
 * Worked in whole pixels, adding up to exactly the row: widths even a pixel over make it scrollable, and a
 * tap on a scrollable row while it scrolls is taken as "stop", not passed to the tab - see [KeepTabInView].
 */
@Composable
private fun groupTabWidths(labels: List<String>, rowWidthPx: Int): List<Dp> {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleSmall
    val density = LocalDensity.current
    return remember(labels, style, rowWidthPx, density) {
        val padding = with(density) { (TAB_LABEL_PADDING * 2).roundToPx() }
        // A pixel over, so a label measured at exactly its width isn't cut off by rounding.
        val natural = labels.map { label -> measurer.measure(label, style, maxLines = 1, softWrap = false).size.width + 1 + padding }
        val equal = rowWidthPx / labels.size
        val base = if (natural.all { it <= equal }) List(labels.size) { 0 } else natural
        val left = (rowWidthPx - base.sum()).coerceAtLeast(0)
        // What's left shared out, and the pixels that don't divide evenly given one each to the first tabs, so the
        // row spans its width exactly.
        val widths = base.mapIndexed { index, width -> width + left / labels.size + if (index < left % labels.size) 1 else 0 }
        with(density) { widths.map { it.toDp() } }
    }
}

/** Where each tab of a scrolling row was last placed along it, by its index: its start and end in pixels, from the
 * row's start. Written as the tabs are placed, read by [KeepTabInView]; not state, as nothing redraws from it. */
private class TabPlacements {
    val starts = mutableMapOf<Int, Int>()
    val ends = mutableMapOf<Int, Int>()
}

/** Records where this tab is placed in its row into [placements], counted from the row's start - its right edge when
 * [rtl], as the row's scroll is counted. */
private fun Modifier.recordPlacement(placements: TabPlacements, index: Int, rtl: Boolean): Modifier = onPlaced { coordinates ->
    val parentWidth = coordinates.parentLayoutCoordinates?.size?.width ?: return@onPlaced
    val left = coordinates.positionInParent().x.roundToInt()
    val right = left + coordinates.size.width
    placements.starts[index] = if (rtl) parentWidth - right else left
    placements.ends[index] = if (rtl) parentWidth - left else right
}

/**
 * Scrolls a tab row just enough to bring its [selected] tab clear of the edge fades, and only if it isn't already -
 * after a swipe to a page whose tab is off screen, say. Material's scrollable rows instead re-centre the selected tab,
 * with a scroll animation, every time it changes; and while a scrollable is scrolling, Compose takes a tap on it as
 * "stop scrolling" and never passes it to the tab. So a second tap soon after the first (within ~300ms in tests, longer
 * on a slow phone) was lost, which on a device felt like tabs that only worked some of the time. Both rows are given a
 * `selectedTabIndex` that never changes, so they never re-centre, and this keeps the selected tab in view instead: a
 * tap on a tab that's already in view doesn't scroll anything, and one partly under an edge fade moves the row at once
 * (as the page itself snaps), so there's never a scroll under the next tap.
 */
@Composable
private fun KeepTabInView(scrollState: ScrollState, placements: TabPlacements, selected: () -> Float) {
    val edge = with(LocalDensity.current) { (TAB_EDGE_CLEAR + TAB_EDGE_FADE).roundToPx() }
    LaunchedEffect(scrollState, placements) {
        snapshotFlow(selected).collectLatest { position ->
            // Between two tabs while the indicator is on its way: the tab it is passing, as far along as it is, so a row that
            // has to scroll goes smoothly with the indicator instead of jumping a tab's width as each tab is passed.
            val first = floor(position).toInt()
            val fraction = position - first
            var bounds = placements.boundsAt(first, fraction)
            if (bounds == null) {
                // Placed by the next frame, if the row (or this tab) is new.
                withFrameNanos { }
                bounds = placements.boundsAt(first, fraction) ?: return@collectLatest
            }
            val (start, end) = bounds
            val viewport = scrollState.viewportSize
            // Each end's fade (and chevron) only covers the row while there's more beyond it.
            val visibleStart = scrollState.value + if (scrollState.value > 0) edge else 0
            val visibleEnd = scrollState.value + viewport - if (scrollState.value < scrollState.maxValue) edge else 0
            val target = when {
                start < visibleStart -> start - edge
                end > visibleEnd -> end - viewport + edge
                else -> return@collectLatest
            }
            // At once, not animated: a scroll in progress would swallow the next tap, as below.
            scrollState.scrollTo(target.coerceIn(0, scrollState.maxValue))
        }
    }
}

/** The start and end of the place [fraction] of the way from tab [index] to the next, or null if either isn't placed yet. */
private fun TabPlacements.boundsAt(index: Int, fraction: Float): Pair<Int, Int>? {
    val start = starts[index] ?: return null
    val end = ends[index] ?: return null
    if (fraction <= 0f) return start to end
    val nextStart = starts[index + 1] ?: return start to end
    val nextEnd = ends[index + 1] ?: return start to end
    return (start + (nextStart - start) * fraction).roundToInt() to (end + (nextEnd - end) * fraction).roundToInt()
}

/** The group row's indicator, as thick as the stock one - named so the divider can centre itself on it. */
private val TAB_INDICATOR_HEIGHT = 3.dp

/** The page row's indicator: thinner and flat, as a secondary row's is, so it reads as under the group row's. */
private val SECONDARY_INDICATOR_HEIGHT = 2.dp

/**
 * The group row's divider, raised off the row's bottom edge so it runs through the middle of the
 * indicator, which sits on that edge - left at the bottom, the indicator rests on top of the line
 * instead. (The indicator can't be lowered onto the line: the row clips anything below its bottom.)
 */
@Composable
private fun TabRowDivider() {
    HorizontalDivider(modifier = Modifier.padding(bottom = (TAB_INDICATOR_HEIGHT - DividerDefaults.Thickness) / 2))
}

/** Space between the end of a page's text and the top of the footer, once scrolled to the bottom. */
private val PAGE_FOOTER_GAP = 8.dp

/**
 * "2 of 6" pinned to the bottom of the pages - where you are in the group showing and how many pages
 * it has, in one glance, alongside the page row's chevrons (which say only that there's more). A small
 * gold pill drawn over the pages, not a row of its own, so it costs the pages no height: longer text
 * scrolls behind it, the pill's own background keeping it readable on top. Drawn by the shared [FooterPill].
 *
 * TalkBack hears "Modes, page 2 of 6", and as a polite live region it's announced again whenever the
 * page changes - a swipe through the pager otherwise lands on a new page without a word, and one past
 * a group's last page lands in another group without saying so.
 */
@Composable
private fun PageCountFooter(pagerState: PagerState, modifier: Modifier = Modifier) {
    val group = groupOf(pagerState.currentPage)
    val page = pagerState.currentPage - GROUP_STARTS[group]
    val pageCount = RULES_GROUPS[group].pages.size
    val pageSpoken = stringResource(Res.string.rules_page_in_group_spoken, stringResource(RULES_GROUPS[group].label), page + 1, pageCount)
    FooterPill(
        text = stringResource(Res.string.rules_page_count, page + 1, pageCount),
        modifier = modifier.semantics {
            contentDescription = pageSpoken
            liveRegion = LiveRegionMode.Polite
        },
    )
}

/**
 * Hides a horizontally scrolling row's content at whichever end has more beyond it: fully across
 * [clearWidth], where that end's [TabScrollChevron] sits, so its glyph is never drawn over a label,
 * then fading back in over [fadeWidth]. Both deepen with how far there is left to scroll (up to
 * [easeDistance]), so they ease away as the last tab comes fully into view instead of vanishing at
 * the end. Drawn only: nothing is hidden from touch or from TalkBack.
 */
private fun Modifier.fadeOffscreenEdges(scrollState: ScrollState, clearWidth: Dp, fadeWidth: Dp, easeDistance: Dp): Modifier = this
    // Offscreen so the DstIn masks below cut into the row's own pixels, not the backdrop behind it.
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val width = (clearWidth + fadeWidth).toPx().coerceAtMost(size.width / 2)
        if (width <= 0f) return@drawWithContent
        // Where along the mask the clear zone gives way to the fade, as a fraction of it.
        val clearFraction = (clearWidth.toPx() / width).coerceIn(0f, 1f)
        val ease = easeDistance.toPx()
        // The scroll state counts from the row's start, which is its right edge in a right-to-left layout; the masks are
        // drawn in screen coordinates, so the strengths swap sides there.
        val rtl = layoutDirection == LayoutDirection.Rtl
        val leftStrength = scrollState.edgeStrength(forward = rtl, over = ease)
        val rightStrength = scrollState.edgeStrength(forward = !rtl, over = ease)
        if (leftStrength > 0f) {
            val edge = Color.Black.copy(alpha = 1f - leftStrength)
            drawRect(
                brush = Brush.horizontalGradient(0f to edge, clearFraction to edge, 1f to Color.Black, startX = 0f, endX = width),
                size = Size(width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (rightStrength > 0f) {
            val edge = Color.Black.copy(alpha = 1f - rightStrength)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    (1f - clearFraction) to edge,
                    1f to edge,
                    startX = size.width - width,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - width, 0f),
                size = Size(width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * How much more there is to scroll towards one end, from 0 (none) to 1 ([over] or more) - so an edge
 * hint deepens with the distance left rather than switching on and off, and eases away as the last
 * tab comes fully into view instead of vanishing at the end.
 */
private fun ScrollState.edgeStrength(forward: Boolean, over: Float): Float {
    val remaining = if (forward) maxValue - value else value
    return (remaining / over).coerceIn(0f, 1f)
}

/**
 * A chevron at one end of the tab row while there are more tabs that way - the hint that there's
 * more, which the fade can't give by itself when no label happens to be under it. A tap scrolls the
 * row that way rather than changing page: it points at more tabs, not at the next page.
 *
 * Kept out of TalkBack (`clearAndSetSemantics`): the tabs it scrolls to are TalkBack stops already,
 * each announced with its position and the count ("Tab, 5 of 7"), so it would only add a stop that
 * says nothing new.
 */
@Composable
private fun TabScrollChevron(scrollState: ScrollState, forward: Boolean, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val shown by remember(scrollState, forward) {
        derivedStateOf { if (forward) scrollState.canScrollForward else scrollState.canScrollBackward }
    }
    // Not composed at all when there's nothing that way, so it never sits over a tab taking its taps.
    if (!shown) return

    IconButton(
        onClick = {
            val distance = scrollState.viewportSize * TAB_CHEVRON_SCROLL_FRACTION
            coroutineScope.launch { scrollState.animateScrollBy(if (forward) distance else -distance) }
        },
        modifier = modifier
            .graphicsLayer { alpha = scrollState.edgeStrength(forward, over = TAB_EDGE_EASE.toPx()) }
            .clearAndSetSemantics {},
    ) {
        Icon(
            imageVector = if (forward) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
