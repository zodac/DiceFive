package net.zodac.dicefive.ui.rules

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.HitTarget
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_page_of
import net.zodac.dicefive.resources.rules_5x_and_joker_1
import net.zodac.dicefive.resources.rules_5x_and_joker_2
import net.zodac.dicefive.resources.rules_5x_and_joker_3
import net.zodac.dicefive.resources.rules_5x_and_joker_4
import net.zodac.dicefive.resources.rules_5x_and_joker_5
import net.zodac.dicefive.resources.rules_5x_and_joker_6
import net.zodac.dicefive.resources.rules_5x_and_joker_tab
import net.zodac.dicefive.resources.rules_5x_and_joker_title
import net.zodac.dicefive.resources.rules_example_any_place_spoken
import net.zodac.dicefive.resources.rules_example_coloured_die_spoken
import net.zodac.dicefive.resources.rules_example_equals
import net.zodac.dicefive.resources.rules_example_held_spoken
import net.zodac.dicefive.resources.rules_example_ignored_spoken
import net.zodac.dicefive.resources.rules_example_locked_spoken
import net.zodac.dicefive.resources.rules_example_nothing_held_spoken
import net.zodac.dicefive.resources.rules_example_roll_spoken
import net.zodac.dicefive.resources.rules_example_scores_spoken
import net.zodac.dicefive.resources.rules_example_spoken
import net.zodac.dicefive.resources.rules_example_target_spoken
import net.zodac.dicefive.resources.rules_example_tile_alibi_spoken
import net.zodac.dicefive.resources.rules_example_tile_target_spoken
import net.zodac.dicefive.resources.rules_example_worth_spoken
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
import net.zodac.dicefive.resources.rules_hit_list_6
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
import net.zodac.dicefive.resources.rules_modifiers_1
import net.zodac.dicefive.resources.rules_modifiers_10
import net.zodac.dicefive.resources.rules_modifiers_11
import net.zodac.dicefive.resources.rules_modifiers_12
import net.zodac.dicefive.resources.rules_modifiers_13
import net.zodac.dicefive.resources.rules_modifiers_14
import net.zodac.dicefive.resources.rules_modifiers_15
import net.zodac.dicefive.resources.rules_modifiers_16
import net.zodac.dicefive.resources.rules_modifiers_17
import net.zodac.dicefive.resources.rules_modifiers_2
import net.zodac.dicefive.resources.rules_modifiers_3
import net.zodac.dicefive.resources.rules_modifiers_4
import net.zodac.dicefive.resources.rules_modifiers_5
import net.zodac.dicefive.resources.rules_modifiers_6
import net.zodac.dicefive.resources.rules_modifiers_7
import net.zodac.dicefive.resources.rules_modifiers_8
import net.zodac.dicefive.resources.rules_modifiers_9
import net.zodac.dicefive.resources.rules_modifiers_tab
import net.zodac.dicefive.resources.rules_modifiers_title
import net.zodac.dicefive.resources.rules_name_exact_hit
import net.zodac.dicefive.resources.rules_name_hit
import net.zodac.dicefive.resources.rules_name_partial_hit
import net.zodac.dicefive.resources.rules_page_count
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
import net.zodac.dicefive.resources.rules_stud_1
import net.zodac.dicefive.resources.rules_stud_2
import net.zodac.dicefive.resources.rules_stud_3
import net.zodac.dicefive.resources.rules_stud_4
import net.zodac.dicefive.resources.rules_stud_5
import net.zodac.dicefive.resources.rules_stud_6
import net.zodac.dicefive.resources.rules_stud_7
import net.zodac.dicefive.resources.rules_stud_8
import net.zodac.dicefive.resources.rules_stud_9
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
import net.zodac.dicefive.ui.common.logoRollPose
import net.zodac.dicefive.ui.common.parseInlineMarkup
import net.zodac.dicefive.ui.common.playLogoRoll
import net.zodac.dicefive.ui.common.spokenList
import net.zodac.dicefive.ui.game.CUP_SHAKE_MILLIS
import net.zodac.dicefive.ui.game.CategoryTile
import net.zodac.dicefive.ui.game.LockedChains
import net.zodac.dicefive.ui.game.TurnTimerBadge
import net.zodac.dicefive.ui.game.style.ClassicGoldDiceCupStyle
import net.zodac.dicefive.ui.game.style.IvoryDiceStyle
import net.zodac.dicefive.ui.game.style.palette
import net.zodac.dicefive.ui.game.targetProgress
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
 * (or picked from the tab row) rather than scrolled past as one long page, so each (upper section,
 * lower section, the joker rule, the house rule tie-break, and one page per non-Standard game mode)
 * gets its own moment rather than blurring into the next.
 *
 * Kept in step with the actual rules engine: `ScoreCategory`'s fixed values (25/30/40/50/100),
 * `ScoreCalculator`'s joker rule priority, `game/TieBreak.kt`'s criterion order (see its own
 * doc comment), and each `GameMode`'s rolls, dice and timer - a rule change there should be
 * echoed here.
 */
private val RULES_PAGES = listOf(
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
            RulesStep(1, Res.string.rules_stud_4, roll(6, 6, 6, 2, 3, 5, 1, held = 3)),
            RulesStep(2, Res.string.rules_stud_5, roll(6, 6, 6, 6, 4, 2, 4, held = 4)),
            RulesStep(3, Res.string.rules_stud_6, roll(6, 6, 6, 6, 5, 1, 3, held = 5)),
            RulesStep(4, Res.string.rules_stud_7,
                dice(6, 6, 6, 6, 5, score = points(29)),
            ),
            text(Res.string.rules_stud_8),
            text(Res.string.rules_stud_9),
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
            RulesCategory(named(Res.string.rules_name_hit), Res.string.rules_hit_list_3,
                dice(2, 4, 1, 3, 5, counting = 4, score = points(20)).showing(RulesTile.Target(EXAMPLE_TARGET)),
            ),
            RulesCategory(named(Res.string.rules_name_exact_hit), Res.string.rules_hit_list_4,
                dice(4, 1, 3, 2, 6, counting = 4, score = points(40)).showing(RulesTile.Target(EXAMPLE_TARGET)),
            ),
            RulesCategory(named(Res.string.rules_name_partial_hit), Res.string.rules_hit_list_5,
                diceCounting(4, 1, 6, 2, 5, counting = setOf(0, 1, 3), score = points(10)).showing(RulesTile.Target(EXAMPLE_TARGET)),
            ),
            text(Res.string.rules_hit_list_6),
            text(Res.string.rules_hit_list_7),
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
    RulesPage(
        title = Res.string.rules_modifiers_title,
        tabLabel = Res.string.rules_modifiers_tab,
        blocks = listOf(
            text(Res.string.rules_modifiers_1),
            text(Res.string.rules_modifiers_2),
            text(Res.string.rules_modifiers_3),
            RulesTurnTimer,
            text(Res.string.rules_modifiers_4),
            text(Res.string.rules_modifiers_5),
            text(Res.string.rules_modifiers_6),
            text(Res.string.rules_modifiers_7),
            text(Res.string.rules_modifiers_8),
            text(Res.string.rules_modifiers_9),
            RulesCategory(named(Res.string.score_two_pair), Res.string.rules_modifiers_10, dice(4, 4, 2, 2, 4, counting = 4, score = points(12))),
            RulesCategory(named(Res.string.score_evens), Res.string.rules_modifiers_11, dice(6, 4, 2, 3, 1, counting = 3, score = points(12))),
            RulesCategory(named(Res.string.score_odds), Res.string.rules_modifiers_12, dice(5, 3, 3, 6, 2, counting = 3, score = points(11))),
            text(Res.string.rules_modifiers_13),
            text(Res.string.rules_modifiers_14),
            lockedDice(5, 5, 5, 2, 5, lockedIndex = 4, counting = 3, score = points(15)),
            text(Res.string.rules_modifiers_15),
            text(Res.string.rules_modifiers_16),
            text(Res.string.rules_modifiers_17),
        ),
    ),
)

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
 * The Rules page: a tab per [RulesPage] over a [HorizontalPager] of them. The tabs scroll, since the
 * list grows by a page with every game mode - a row of dots stopped saying where you were, or
 * letting you get to the last page, once there were more than a handful.
 *
 * A scrolling tab row gives no sign on its own that there's more of it: on a phone the first three
 * tabs can end right at the edge, so the row looks like all there is. A fade alone didn't fix that -
 * it only shows when a label happens to be under it, and with the selected tab centred the next one
 * can start just past the edge, leaving the fade over empty space. So each end with tabs beyond it
 * gets a [TabScrollChevron], which is there whatever the labels' widths, over a [fadeOffscreenEdges]
 * fade that keeps a cut-off label from running into it. The row and its tabs carry collection
 * semantics so TalkBack says "Tab, 1 of 7", the spoken form of the same hint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { RULES_PAGES.size })
    val coroutineScope = rememberCoroutineScope()
    val tabScrollState = rememberScrollState()

    ScreenScaffold(title = stringResource(Res.string.rules_title), onBack = onBack, modifier = modifier) {
        // A real Box, so the chevrons' align lands on their actual parent (see UI.md's gotchas).
        Box(modifier = Modifier.fillMaxWidth()) {
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                scrollState = tabScrollState,
                // Transparent over the backdrop, like the app bar above it, and flush with the page text.
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
                // Slides with the pages as they're swiped, rather than jumping once the next page is
                // the current one - see pagerIndicatorLayout.
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
                            pagerIndicatorLayout(measurable, constraints, tabPositions, pagerState.currentPage + pagerState.currentPageOffsetFraction)
                        },
                        width = Dp.Unspecified,
                        height = TAB_INDICATOR_HEIGHT,
                    )
                },
                // Raised off the row's bottom edge so it runs through the middle of the indicator, which
                // sits on that edge - left at the bottom, the indicator rests on top of the line instead.
                // (The indicator can't be lowered onto the line: the row clips anything below its bottom.)
                divider = {
                    HorizontalDivider(modifier = Modifier.padding(bottom = (TAB_INDICATOR_HEIGHT - DividerDefaults.Thickness) / 2))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .fadeOffscreenEdges(tabScrollState, clearWidth = TAB_EDGE_CLEAR, fadeWidth = TAB_EDGE_FADE, easeDistance = TAB_EDGE_EASE)
                    .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = RULES_PAGES.size) },
            ) {
                RULES_PAGES.forEachIndexed { index, rulesPage ->
                    Tab(
                        selected = index == pagerState.currentPage,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(text = stringResource(rulesPage.tabLabel), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.semantics {
                            collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1)
                        },
                    )
                }
            }
            TabScrollChevron(scrollState = tabScrollState, forward = false, modifier = Modifier.align(Alignment.CenterStart))
            TabScrollChevron(scrollState = tabScrollState, forward = true, modifier = Modifier.align(Alignment.CenterEnd))
        }

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
                    for (block in rulesPage.blocks) {
                        RulesBlockView(block)
                    }
                }
            }

            // In the screen's right-hand margin, beside the text rather than over it (as on Styles),
            // and only while the page showing is too long to fit.
            VerticalScrollbar(
                scrollState = pageScrollStates[pagerState.currentPage],
                width = SCREEN_MARGIN,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = SCREEN_MARGIN),
            )

            PageCountFooter(
                page = pagerState.currentPage,
                pageCount = RULES_PAGES.size,
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { footerHeightPx = it.height },
            )
        }
    }
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
    val amount = Regex(shape.split("%1\$d").joinToString("(\\d+)") { Regex.escape(it) })
    val plus = stringResource(Res.string.rules_plus_spoken)
    val spoken = remember(text, shape, plus) { text.replace(amount) { "\u0000${it.groupValues[1]}\u0000" }.replace(" + ", " $plus ") }
    return spoken.split('\u0000').mapIndexed { index, part -> if (index % 2 == 1) pluralStringResource(Res.plurals.rules_points_spoken, part.toInt(), part.toInt()) else part }.joinToString("")
}

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
    fun ExampleDie.spoken() = colour?.let { stringResource(Res.string.rules_example_coloured_die_spoken, it.name.lowercase(), value) } ?: value.toString()
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
private fun MeasureScope.pagerIndicatorLayout(
    measurable: Measurable,
    constraints: Constraints,
    tabPositions: List<TabPosition>,
    pagePosition: Float,
): MeasureResult {
    val position = pagePosition.coerceIn(0f, (tabPositions.size - 1).toFloat())
    val from = tabPositions[position.toInt()]
    val to = tabPositions[(position.toInt() + 1).coerceAtMost(tabPositions.size - 1)]
    val fraction = position - position.toInt()
    // Under the label, as the stock indicator sits (matchContentSize), not the whole tab. The
    // positions a scrollable tab row hands a custom indicator start at the label, not the tab - its
    // `left` is already in by the tab's padding - so the label's centre is half its width along.
    val width = lerp(from.contentWidth, to.contentWidth, fraction)
    val centre = lerp(from.left + from.contentWidth / 2, to.left + to.contentWidth / 2, fraction)
    val widthPx = width.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
    return layout(placeable.width, placeable.height) {
        placeable.place(x = (centre - width / 2).roundToPx(), y = 0)
    }
}

/** The tab row's indicator, as thick as the stock one - named so the divider can centre itself on it. */
private val TAB_INDICATOR_HEIGHT = 3.dp

/** Space between the end of a page's text and the top of the footer, once scrolled to the bottom. */
private val PAGE_FOOTER_GAP = 8.dp

/**
 * "1 of 7" pinned to the bottom of the pages - where you are and how many there are, in one glance,
 * alongside the tab row's chevrons (which say only that there's more). A small gold pill drawn over the
 * pages, not a row of its own, so it costs the pages no height: longer text scrolls behind it, the
 * pill's own background keeping it readable on top. Drawn by the shared [FooterPill].
 *
 * TalkBack hears "Page 1 of 7", and as a polite live region it's announced again whenever the page
 * changes - a swipe through the pager otherwise lands on a new page without a word.
 */
@Composable
private fun PageCountFooter(page: Int, pageCount: Int, modifier: Modifier = Modifier) {
    val pageSpoken = stringResource(Res.string.common_page_of, page + 1, pageCount)
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
        val startStrength = scrollState.edgeStrength(forward = false, over = ease)
        val endStrength = scrollState.edgeStrength(forward = true, over = ease)
        if (startStrength > 0f) {
            val edge = Color.Black.copy(alpha = 1f - startStrength)
            drawRect(
                brush = Brush.horizontalGradient(0f to edge, clearFraction to edge, 1f to Color.Black, startX = 0f, endX = width),
                size = Size(width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (endStrength > 0f) {
            val edge = Color.Black.copy(alpha = 1f - endStrength)
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
