package net.zodac.dicefive.ui.rules

import androidx.compose.foundation.layout.size
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
import net.zodac.dicefive.resources.rules_d20_1
import net.zodac.dicefive.resources.rules_d20_2
import net.zodac.dicefive.resources.rules_d20_3
import net.zodac.dicefive.resources.rules_d20_4
import net.zodac.dicefive.resources.rules_d20_5
import net.zodac.dicefive.resources.rules_d20_6
import net.zodac.dicefive.resources.rules_d20_7
import net.zodac.dicefive.resources.rules_d20_8
import net.zodac.dicefive.resources.rules_d20_tab
import net.zodac.dicefive.resources.rules_d20_title
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
import net.zodac.dicefive.resources.rules_how_to_play_tutorial
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
import net.zodac.dicefive.resources.rules_quickfire_1
import net.zodac.dicefive.resources.rules_quickfire_2
import net.zodac.dicefive.resources.rules_quickfire_3
import net.zodac.dicefive.resources.rules_quickfire_4
import net.zodac.dicefive.resources.rules_quickfire_5
import net.zodac.dicefive.resources.rules_quickfire_6
import net.zodac.dicefive.resources.rules_quickfire_tab
import net.zodac.dicefive.resources.rules_quickfire_title
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
import net.zodac.dicefive.ui.common.parseInlineMarkup
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
internal data class RulesPage(val title: StringResource, val tabLabel: StringResource, val blocks: List<RulesBlock>)

/** A group of [RulesPage]s - a tab of the top row, named [label], whose [pages] are the tabs of the row under it. */
internal class RulesGroup(val label: StringResource, val pages: List<RulesPage>)

internal sealed interface RulesBlock

/** The default cup with a handful of dice tipped out beside it - a picture for a page with no
 * example rolls of its own, so it doesn't look empty. */
internal data object RulesIllustration : RulesBlock

/** The button that starts the guided game - see [LocalStartTutorial]. */
internal data object RulesTutorialStart : RulesBlock

/** The game's own turn timer badge, stopped at [TURN_TIMER_EXAMPLE_SECONDS] so it flashes as it
 * does when a turn is running out. */
internal data object RulesTurnTimer : RulesBlock

/**
 * The dice mat as it looks while a finger is held on [category]'s box: a slot row, then a column per die (seven in Stud). The
 * columns of the dice at [taking] have a solid gold outline, those at [alternatives] (a die that would have scored the same) a
 * dashed one, and the other dice fade back - as in the game. The pressed box is drawn above it, lit.
 */
internal data class RulesMat(val values: List<Int>, val taking: Set<Int>, val alternatives: Set<Int>, val category: ScoreCategory) : RulesBlock

/** A paragraph of body text. */
internal data class RulesText(val text: StringResource) : RulesBlock

/** One step of an ordered list - "1.", "2." - with its text hanging beside the number, and an
 * optional [example] under it. */
internal data class RulesStep(val number: Int, val text: StringResource, val example: RulesDice? = null) : RulesBlock

/** A scoring category: its [name] as a small heading, what it takes in [description], and an
 * [example] roll scoring it. */
internal data class RulesCategory(val name: RulesName, val description: StringResource, val example: RulesDice) : RulesBlock

/** A [RulesCategory]'s heading: the name of a box (translated), or one of the game's marks ([ScoreCategory.mark]) as it is. */
internal sealed interface RulesName {
    data class Mark(val text: String) : RulesName

    data class Named(val resource: StringResource) : RulesName
}

/** [category]'s heading: its mark, or its name. */
private fun boxName(category: ScoreCategory): RulesName =
    category.mark?.let(RulesName::Mark) ?: RulesName.Named(requireNotNull(category.displayName) { "$category has no name" })

private fun named(resource: StringResource): RulesName = RulesName.Named(resource)

/**
 * What an example scores: one amount, or two added together ("20pts + 100pts" - a repeat 5x in its own box and
 * the bonus). Shown, and read aloud, in the player's language (see [pointsText]).
 */
internal class RulesScore(val amounts: List<Int>)

private fun points(vararg amounts: Int) = RulesScore(amounts.toList())

/** One die in a [RulesDice] example: its [value], whether it [counts] towards the category being
 * shown (the rest are drawn faded), the [colour] it rolled in Tricolour, if any, and whether it is [locked]
 * in chains by Unlucky Dice (drawn at full strength with the chains over it, and never counting). */
internal data class ExampleDie(val value: Int, val counts: Boolean = true, val colour: DieColour? = null, val locked: Boolean = false) {
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
internal data class RulesDice(
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
internal sealed interface RulesTile {
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
internal const val ANY_PLACE = 0

/** A Hit List target's places, left to right - [ANY_PLACE] where any die will do - worth [points], with its tile. */
private fun target(vararg places: Int, points: Int): RulesDice =
    RulesDice(
        places.map { ExampleDie(it) },
        score = points(points),
        isTarget = true,
        tile = RulesTile.Target(HitTarget(places.map { it.takeIf { place -> place != ANY_PLACE } }, points)),
    )

/** The target the Hit List page's examples chase: 4 1 3 2 and an open place, worth 20. */
internal val EXAMPLE_TARGET = HitTarget(listOf(4, 1, 3, 2, null), points = 20)

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
 * modifiers - each of the last two opening on an overview of what they are. How to Play starts the tutorial. A new mode or modifier
 * is a page in its group (and a mode that counts on the Leaderboard is named in `rules_modes_3`).
 *
 * Kept in step with the actual rules engine: `ScoreCategory`'s fixed values (25/30/40/50/100),
 * `ScoreCalculator`'s joker rule priority, `game/TieBreak.kt`'s criterion order (see its own
 * doc comment), and each `GameMode`'s rolls, dice and timer - a rule change there should be
 * echoed here.
 */
internal val RULES_GROUPS = listOf(
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
                    text(Res.string.rules_how_to_play_tutorial),
                    RulesTutorialStart,
                    RulesIllustration,
                ),
            ),
            RulesPage(
                title = Res.string.rules_upper_section_title,
                tabLabel = Res.string.rules_upper_section_tab,
                blocks = listOf(
                    text(Res.string.rules_upper_section_1),
                    RulesCategory(boxName(ScoreCategory.FIVES), Res.string.rules_upper_section_2, dice(5, 5, 5, 2, 1, counting = 3, score = points(15))),
                    text(Res.string.rules_upper_section_3),
                ),
            ),
            RulesPage(
                title = Res.string.rules_lower_section_title,
                tabLabel = Res.string.rules_lower_section_tab,
                blocks = listOf(
                    text(Res.string.rules_lower_section_1),
                    RulesCategory(boxName(ScoreCategory.THREE_OF_A_KIND), Res.string.rules_lower_section_2, dice(5, 5, 5, 2, 6, counting = 3, score = points(23))),
                    RulesCategory(boxName(ScoreCategory.FOUR_OF_A_KIND), Res.string.rules_lower_section_3, dice(4, 4, 4, 4, 1, counting = 4, score = points(17))),
                    RulesCategory(boxName(ScoreCategory.FULL_HOUSE), Res.string.rules_lower_section_4, dice(3, 3, 3, 6, 6, score = points(25))),
                    RulesCategory(boxName(ScoreCategory.SMALL_STRAIGHT), Res.string.rules_lower_section_5, dice(2, 3, 4, 5, 2, counting = 4, score = points(30))),
                    RulesCategory(boxName(ScoreCategory.LARGE_STRAIGHT), Res.string.rules_lower_section_6, dice(1, 2, 3, 4, 5, score = points(40))),
                    RulesCategory(boxName(ScoreCategory.FIVE_OF_A_KIND), Res.string.rules_lower_section_7, dice(6, 6, 6, 6, 6, score = points(50))),
                    RulesCategory(boxName(ScoreCategory.CHANCE), Res.string.rules_lower_section_8, dice(2, 3, 5, 5, 6, score = points(21))),
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
                    RulesCategory(boxName(ScoreCategory.REDS), Res.string.rules_tricolour_3,
                        colouredDice(2 to DieColour.RED, 5 to DieColour.RED, 1 to DieColour.RED, 6 to DieColour.RED, 3 to DieColour.RED, score = points(40)),
                    ),
                    RulesCategory(boxName(ScoreCategory.YELLOWS), Res.string.rules_tricolour_4,
                        colouredDice(4 to DieColour.YELLOW, 4 to DieColour.YELLOW, 1 to DieColour.YELLOW, 5 to DieColour.YELLOW, 2 to DieColour.YELLOW, score = points(40)),
                    ),
                    RulesCategory(boxName(ScoreCategory.BLUES), Res.string.rules_tricolour_5,
                        colouredDice(6 to DieColour.BLUE, 3 to DieColour.BLUE, 3 to DieColour.BLUE, 2 to DieColour.BLUE, 5 to DieColour.BLUE, score = points(40)),
                    ),
                    RulesCategory(boxName(ScoreCategory.COLOURED_HOUSE), Res.string.rules_tricolour_6,
                        colouredDice(1 to DieColour.RED, 4 to DieColour.RED, 6 to DieColour.RED, 2 to DieColour.BLUE, 5 to DieColour.BLUE, score = points(25)),
                    ),
                    text(Res.string.rules_tricolour_7),
                    text(Res.string.rules_tricolour_8),
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
                    text(Res.string.rules_stud_8),
                    text(Res.string.rules_stud_9),
                    RulesMat(
                        values = listOf(1, 2, 3, 4, 5, 6, 2),
                        taking = setOf(0, 1, 2, 3, 4),
                        alternatives = setOf(5),
                        category = ScoreCategory.LARGE_STRAIGHT,
                    ),
                ),
            ),
            RulesPage(
                title = Res.string.rules_d20_title,
                tabLabel = Res.string.rules_d20_tab,
                blocks = listOf(
                    text(Res.string.rules_d20_1),
                    text(Res.string.rules_d20_2),
                    dice(17, 3, 17, 9, 17, counting = 3, score = points(51)),
                    text(Res.string.rules_d20_7),
                    diceCounting(12, 3, 17, 9, 14, counting = setOf(0, 2, 4), score = points(43)),
                    text(Res.string.rules_d20_8),
                    text(Res.string.rules_d20_3),
                    text(Res.string.rules_d20_4),
                    text(Res.string.rules_d20_5),
                    text(Res.string.rules_d20_6),
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
                    RulesCategory(boxName(ScoreCategory.ALIBI), Res.string.rules_hit_list_9,
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
                    RulesCategory(boxName(ScoreCategory.TWO_PAIR), Res.string.rules_extended_scores_2, dice(4, 4, 2, 2, 4, counting = 4, score = points(12))),
                    RulesCategory(boxName(ScoreCategory.EVENS), Res.string.rules_extended_scores_3, dice(6, 4, 2, 3, 1, counting = 3, score = points(12))),
                    RulesCategory(boxName(ScoreCategory.ODDS), Res.string.rules_extended_scores_4, dice(5, 3, 3, 6, 2, counting = 3, score = points(11))),
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
internal val RULES_PAGES = RULES_GROUPS.flatMap { it.pages }

/** Where each of [RULES_GROUPS] starts in [RULES_PAGES], then one past the last page. */
internal val GROUP_STARTS = RULES_GROUPS.runningFold(0) { start, group -> start + group.pages.size }

/** Which of [RULES_GROUPS] the page at [page] is in. */
internal fun groupOf(page: Int): Int = GROUP_STARTS.indexOfLast { it <= page }.coerceAtMost(RULES_GROUPS.lastIndex)
