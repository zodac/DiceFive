package net.zodac.dicefive.model

import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.score_blues
import net.zodac.dicefive.resources.score_blues_irish
import net.zodac.dicefive.resources.score_chance
import net.zodac.dicefive.resources.score_coloured_house
import net.zodac.dicefive.resources.score_evens
import net.zodac.dicefive.resources.score_fives
import net.zodac.dicefive.resources.score_fours
import net.zodac.dicefive.resources.score_full_house
import net.zodac.dicefive.resources.score_large_straight
import net.zodac.dicefive.resources.score_odds
import net.zodac.dicefive.resources.score_ones
import net.zodac.dicefive.resources.score_reds
import net.zodac.dicefive.resources.score_reds_irish
import net.zodac.dicefive.resources.score_sixes
import net.zodac.dicefive.resources.score_small_straight
import net.zodac.dicefive.resources.score_threes
import net.zodac.dicefive.resources.score_two_pair
import net.zodac.dicefive.resources.score_twos
import net.zodac.dicefive.resources.score_yellows
import net.zodac.dicefive.resources.score_yellows_irish
import org.jetbrains.compose.resources.StringResource

/**
 * Which part of the scorecard a [ScoreCategory] belongs to. [UPPER] is what the upper-section bonus
 * is measured against, and what the joker rule forces a matching 5x into first. [COLOUR] is kept
 * apart from [LOWER] rather than folded into it, so a lower-section total (and "Lower Class") means
 * the same thing in every [GameMode]. [EXTENDED] is the same for the Extended Scores modifier's boxes, and
 * [HIT_LIST] for Hit List's targets and its Alibi, and [FIRST_HALF] and [SECOND_HALF] for D20's numbers 1-10 and 11-20.
 */
enum class ScoreSection {
    UPPER,
    LOWER,
    COLOUR,
    EXTENDED,
    HIT_LIST,
    FIRST_HALF,
    SECOND_HALF,
}

/**
 * How a [ScoreCategory] scores the dice: the rule every box of the same kind shares, with what tells it apart (a number,
 * a run's length, a colour). Scoring (`DiceScoring`), the board's icons and names and the CPU all read the rule rather
 * than the box, so a box of an existing kind needs nothing but its entry.
 */
sealed interface ScoreRule {
    /** Every die showing [number], added up - an upper box, or one of D20's numbers. */
    data class Numbered(val number: Int) : ScoreRule

    /** Every die showing a number in [numbers], added up, whichever numbers they are - a D20 wildcard. */
    data class Wildcard(val numbers: IntRange) : ScoreRule

    /** At least [count] dice of one number: every die added up. */
    data class OfAKind(val count: Int) : ScoreRule

    /** Three dice of one number and two of another, for the box's fixed score. */
    data object House : ScoreRule

    /** A run of [length] consecutive numbers among the dice, for the box's fixed score. */
    data class Straight(val length: Int) : ScoreRule

    /** Every die the same number, for the box's fixed score. */
    data object FiveOfAKind : ScoreRule

    /** Every die added up, whatever they show. */
    data object Chance : ScoreRule

    /** Every die showing [colour], for the box's fixed score. */
    data class ColourSet(val colour: DieColour) : ScoreRule

    /** Three dice of one colour and two of another - a full house by colour - for the box's fixed score. */
    data object ColouredHouse : ScoreRule

    /** Two different numbers each on at least two dice: the four dice making the pairs added up, whatever the fifth shows. */
    data object TwoPair : ScoreRule

    /** The [even] dice added up - or the odd ones. */
    data class Parity(val even: Boolean) : ScoreRule

    /** Hit List's target number [index] (from 1): whatever [HitTarget] the game drew for it. */
    data class Target(val index: Int) : ScoreRule

    /** Hit List's stand-in for a target - see [ScoreCategory.ALIBI]. */
    data object Alibi : ScoreRule
}

/**
 * Every scorecard category any [GameMode] can use. A category's own scoring rule is fixed - five reds
 * are worth 40 wherever [REDS] appears - and a mode chooses which of them are on its card (see
 * [GameMode.categories]), so a player's scorecard only ever holds its own mode's categories.
 *
 * [rule] is how it scores (see [ScoreRule]). [fixedScore] is set for the pattern categories worth a flat amount when
 * the dice match, and null for the ones worth whatever the dice add up to. [jokerFreeFill] marks the categories the
 * joker rule lets a repeat 5x fill at their full [fixedScore] regardless of what the dice show (see
 * `ScoreCalculator`). [featured] is the box with a tile of its own beside the grid, rather than a place in it - 5x, or a
 * card without one's Alibi.
 *
 * [mark] is the game's own mark for a box whose name it is - on its tile, spoken, and in a table's header - never
 * translated; null for a box named in words. [displayName] is the name of a box named in words ([irishName] in Luck of
 * the Irish's colours, for a colour box), null for one named by its mark or by its [rule] (a target, a D20 number or
 * wildcard).
 *
 * The [ScoreSection.HIT_LIST] boxes have no rule of their own: what each target calls is drawn when a game
 * starts (see [HitTarget]), so they're scored against the player's [PlayerState.hitList] by `ScoreCalculator`.
 *
 * Declaration order is scorecard order: the upper section (ONES..SIXES), then the lower section,
 * then the colour section, then the extended one - the three boxes the Extended Scores modifier adds
 * to whichever mode's card (see [GameMode.categoriesWith]).
 */
enum class ScoreCategory(
    override val id: String,
    val section: ScoreSection,
    val rule: ScoreRule,
    val fixedScore: Int? = null,
    val jokerFreeFill: Boolean = false,
    val featured: Boolean = false,
    val mark: String? = null,
    val displayName: StringResource? = null,
    val irishName: StringResource? = null,
) : StoredId {
    ONES("ONES", ScoreSection.UPPER, ScoreRule.Numbered(1), displayName = Res.string.score_ones),
    TWOS("TWOS", ScoreSection.UPPER, ScoreRule.Numbered(2), displayName = Res.string.score_twos),
    THREES("THREES", ScoreSection.UPPER, ScoreRule.Numbered(3), displayName = Res.string.score_threes),
    FOURS("FOURS", ScoreSection.UPPER, ScoreRule.Numbered(4), displayName = Res.string.score_fours),
    FIVES("FIVES", ScoreSection.UPPER, ScoreRule.Numbered(5), displayName = Res.string.score_fives),
    SIXES("SIXES", ScoreSection.UPPER, ScoreRule.Numbered(6), displayName = Res.string.score_sixes),
    THREE_OF_A_KIND("THREE_OF_A_KIND", ScoreSection.LOWER, ScoreRule.OfAKind(3), mark = "3x"), // i18n: not translated - the game's mark for the box
    FOUR_OF_A_KIND("FOUR_OF_A_KIND", ScoreSection.LOWER, ScoreRule.OfAKind(4), mark = "4x"), // i18n: not translated - the game's mark for the box
    FULL_HOUSE("FULL_HOUSE", ScoreSection.LOWER, ScoreRule.House, fixedScore = 25, jokerFreeFill = true, displayName = Res.string.score_full_house),
    SMALL_STRAIGHT(
        "SMALL_STRAIGHT", ScoreSection.LOWER, ScoreRule.Straight(4), fixedScore = 30, jokerFreeFill = true, displayName = Res.string.score_small_straight,
    ),
    LARGE_STRAIGHT(
        "LARGE_STRAIGHT", ScoreSection.LOWER, ScoreRule.Straight(5), fixedScore = 40, jokerFreeFill = true, displayName = Res.string.score_large_straight,
    ),
    FIVE_OF_A_KIND("FIVE_OF_A_KIND", ScoreSection.LOWER, ScoreRule.FiveOfAKind, fixedScore = 50, featured = true, mark = "5x"), // i18n: not translated - the game's mark for the box
    CHANCE("CHANCE", ScoreSection.LOWER, ScoreRule.Chance, displayName = Res.string.score_chance),

    REDS(
        "REDS", ScoreSection.COLOUR, ScoreRule.ColourSet(DieColour.RED), fixedScore = 40,
        displayName = Res.string.score_reds, irishName = Res.string.score_reds_irish,
    ),
    YELLOWS(
        "YELLOWS", ScoreSection.COLOUR, ScoreRule.ColourSet(DieColour.YELLOW), fixedScore = 40,
        displayName = Res.string.score_yellows, irishName = Res.string.score_yellows_irish,
    ),
    BLUES(
        "BLUES", ScoreSection.COLOUR, ScoreRule.ColourSet(DieColour.BLUE), fixedScore = 40,
        displayName = Res.string.score_blues, irishName = Res.string.score_blues_irish,
    ),

    /** Three dice of one colour and two of another - a full house by colour rather than by number. */
    COLOURED_HOUSE(
        "COLOURED_HOUSE", ScoreSection.COLOUR, ScoreRule.ColouredHouse, fixedScore = 25, jokerFreeFill = true,
        displayName = Res.string.score_coloured_house,
    ),

    /** Two different numbers each on at least two dice: the four dice making the pairs are added up, whatever the fifth shows. */
    TWO_PAIR("TWO_PAIR", ScoreSection.EXTENDED, ScoreRule.TwoPair, displayName = Res.string.score_two_pair),

    /** The even dice added up. */
    EVENS("EVENS", ScoreSection.EXTENDED, ScoreRule.Parity(even = true), displayName = Res.string.score_evens),

    /** The odd dice added up. */
    ODDS("ODDS", ScoreSection.EXTENDED, ScoreRule.Parity(even = false), displayName = Res.string.score_odds),

    /** Hit List's targets, in card order - each one whatever [HitTarget] the game drew for it. */
    TARGET_1("TARGET_1", ScoreSection.HIT_LIST, ScoreRule.Target(1)),
    TARGET_2("TARGET_2", ScoreSection.HIT_LIST, ScoreRule.Target(2)),
    TARGET_3("TARGET_3", ScoreSection.HIT_LIST, ScoreRule.Target(3)),
    TARGET_4("TARGET_4", ScoreSection.HIT_LIST, ScoreRule.Target(4)),
    TARGET_5("TARGET_5", ScoreSection.HIT_LIST, ScoreRule.Target(5)),
    TARGET_6("TARGET_6", ScoreSection.HIT_LIST, ScoreRule.Target(6)),
    TARGET_7("TARGET_7", ScoreSection.HIT_LIST, ScoreRule.Target(7)),
    TARGET_8("TARGET_8", ScoreSection.HIT_LIST, ScoreRule.Target(8)),
    TARGET_9("TARGET_9", ScoreSection.HIT_LIST, ScoreRule.Target(9)),
    TARGET_10("TARGET_10", ScoreSection.HIT_LIST, ScoreRule.Target(10)),
    TARGET_11("TARGET_11", ScoreSection.HIT_LIST, ScoreRule.Target(11)),
    TARGET_12("TARGET_12", ScoreSection.HIT_LIST, ScoreRule.Target(12)),

    /**
     * Hit List's stand-in for a target: a hit scored here instead of in its target, which stays open for another
     * try. Worth the highest [HitTarget.points] among the open targets the dice hit - never doubled.
     */
    ALIBI("ALIBI", ScoreSection.HIT_LIST, ScoreRule.Alibi, featured = true, mark = "Alibi"), // i18n: not translated - the game's mark for the box

    /** D20's numbers: each adds up the dice showing its number, like an upper box - 1 to 10 in the first half, 11 to 20 in the second. */
    D20_1("D20_1", ScoreSection.FIRST_HALF, ScoreRule.Numbered(1)),
    D20_2("D20_2", ScoreSection.FIRST_HALF, ScoreRule.Numbered(2)),
    D20_3("D20_3", ScoreSection.FIRST_HALF, ScoreRule.Numbered(3)),
    D20_4("D20_4", ScoreSection.FIRST_HALF, ScoreRule.Numbered(4)),
    D20_5("D20_5", ScoreSection.FIRST_HALF, ScoreRule.Numbered(5)),
    D20_6("D20_6", ScoreSection.FIRST_HALF, ScoreRule.Numbered(6)),
    D20_7("D20_7", ScoreSection.FIRST_HALF, ScoreRule.Numbered(7)),
    D20_8("D20_8", ScoreSection.FIRST_HALF, ScoreRule.Numbered(8)),
    D20_9("D20_9", ScoreSection.FIRST_HALF, ScoreRule.Numbered(9)),
    D20_10("D20_10", ScoreSection.FIRST_HALF, ScoreRule.Numbered(10)),
    D20_11("D20_11", ScoreSection.SECOND_HALF, ScoreRule.Numbered(11)),
    D20_12("D20_12", ScoreSection.SECOND_HALF, ScoreRule.Numbered(12)),
    D20_13("D20_13", ScoreSection.SECOND_HALF, ScoreRule.Numbered(13)),
    D20_14("D20_14", ScoreSection.SECOND_HALF, ScoreRule.Numbered(14)),
    D20_15("D20_15", ScoreSection.SECOND_HALF, ScoreRule.Numbered(15)),
    D20_16("D20_16", ScoreSection.SECOND_HALF, ScoreRule.Numbered(16)),
    D20_17("D20_17", ScoreSection.SECOND_HALF, ScoreRule.Numbered(17)),
    D20_18("D20_18", ScoreSection.SECOND_HALF, ScoreRule.Numbered(18)),
    D20_19("D20_19", ScoreSection.SECOND_HALF, ScoreRule.Numbered(19)),
    D20_20("D20_20", ScoreSection.SECOND_HALF, ScoreRule.Numbered(20)),

    /**
     * D20's wildcards, one for each half: every die showing a number in its half added up, whichever numbers they are - a
     * box that takes any of 1 to 10, or any of 11 to 20.
     */
    D20_WILD_LOW("D20_WILD_LOW", ScoreSection.FIRST_HALF, ScoreRule.Wildcard(1..10)),
    D20_WILD_HIGH("D20_WILD_HIGH", ScoreSection.SECOND_HALF, ScoreRule.Wildcard(11..20)),
    ;

    /** The face a numbered box adds up ([ScoreRule.Numbered]) - ONES..SIXES, and D20's twenty - null for every other box. */
    val number: Int?
        get() = (rule as? ScoreRule.Numbered)?.number

    /** The faces a D20 wildcard box adds up ([ScoreRule.Wildcard]), null for every other box. */
    val wildcardNumbers: IntRange?
        get() = (rule as? ScoreRule.Wildcard)?.numbers

    /** The colour a single-colour box needs every die to show ([ScoreRule.ColourSet]), null for every other box. */
    val matchingColour: DieColour?
        get() = (rule as? ScoreRule.ColourSet)?.colour

    /** The faces this box counts - one for a numbered box, a range for a wildcard, null for a box that counts no number. */
    val countedNumbers: IntRange?
        get() = number?.let { it..it } ?: wildcardNumbers

    /** Whether this is one of Hit List's targets (not its Alibi). */
    val isTarget: Boolean
        get() = rule is ScoreRule.Target

    companion object {
        /** The upper section's boxes, ONES..SIXES, in order: the one for each face is at `face - 1`. */
        val UPPER: List<ScoreCategory> = entries.filter { it.section == ScoreSection.UPPER }

        /** The boxes the Extended Scores modifier adds to a card, in scorecard order. */
        val EXTENDED: List<ScoreCategory> = entries.filter { it.section == ScoreSection.EXTENDED }

        /** Every Hit List target box, in card order. */
        val TARGETS: List<ScoreCategory> = entries.filter { it.isTarget }

        /** D20's whole card: its twenty number boxes, then the two wildcards. */
        val D20_CARD: List<ScoreCategory> = entries.filter { it.section == ScoreSection.FIRST_HALF || it.section == ScoreSection.SECOND_HALF }

        /** D20's twenty number boxes, 1 to 20. */
        val D20_NUMBERS: List<ScoreCategory> = D20_CARD.filter { it.number != null }

        /** The upper box for [face], or null for a face with none (above 6). */
        fun upperFor(face: Int): ScoreCategory? = UPPER.getOrNull(face - 1)
    }
}
