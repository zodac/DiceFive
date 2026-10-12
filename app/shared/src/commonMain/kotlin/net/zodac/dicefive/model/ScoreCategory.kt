package net.zodac.dicefive.model

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
 * Every scorecard category any [GameMode] can use. A category's own scoring rule is fixed - five reds
 * are worth 40 wherever [REDS] appears - and a mode chooses which of them are on its card (see
 * [GameMode.categories]), so a player's scorecard only ever holds its own mode's categories.
 *
 * [fixedScore] is set for the pattern categories worth a flat amount when the dice match, and null
 * for the ones worth whatever the dice add up to. [jokerFreeFill] marks the categories the joker rule
 * lets a repeat 5x fill at their full [fixedScore] regardless of what the dice show (see
 * `ScoreCalculator`). [matchingColour] is the colour a single-colour box needs all five dice to show.
 * [featured] is the box with a tile of its own beside the grid, rather than a place in it - 5x, or a card
 * without one's Alibi.
 *
 * [number] is the face a numbered box adds up - ONES..SIXES, and D20's twenty (`D20_1`..`D20_20`) - null for every other box.
 * [wildcardNumbers] is the faces a D20 wildcard box adds up - every die showing one of them - null for every other box.
 *
 * The [ScoreSection.HIT_LIST] boxes have no rule of their own: what each target calls is drawn when a game
 * starts (see [HitTarget]), so they're scored against the player's [PlayerState.hitList] by `ScoreCalculator`.
 *
 * Declaration order is scorecard order: the upper section (ONES..SIXES), then the lower section,
 * then the colour section, then the extended one - the three boxes the Extended Scores modifier adds
 * to whichever mode's card (see [GameMode.categoriesWith]).
 */
enum class ScoreCategory(
    val section: ScoreSection,
    val fixedScore: Int? = null,
    val jokerFreeFill: Boolean = false,
    val matchingColour: DieColour? = null,
    val featured: Boolean = false,
    val number: Int? = null,
    val wildcardNumbers: IntRange? = null,
) {
    ONES(ScoreSection.UPPER, number = 1),
    TWOS(ScoreSection.UPPER, number = 2),
    THREES(ScoreSection.UPPER, number = 3),
    FOURS(ScoreSection.UPPER, number = 4),
    FIVES(ScoreSection.UPPER, number = 5),
    SIXES(ScoreSection.UPPER, number = 6),
    THREE_OF_A_KIND(ScoreSection.LOWER),
    FOUR_OF_A_KIND(ScoreSection.LOWER),
    FULL_HOUSE(ScoreSection.LOWER, fixedScore = 25, jokerFreeFill = true),
    SMALL_STRAIGHT(ScoreSection.LOWER, fixedScore = 30, jokerFreeFill = true),
    LARGE_STRAIGHT(ScoreSection.LOWER, fixedScore = 40, jokerFreeFill = true),
    FIVE_OF_A_KIND(ScoreSection.LOWER, fixedScore = 50, featured = true),
    CHANCE(ScoreSection.LOWER),

    REDS(ScoreSection.COLOUR, fixedScore = 40, matchingColour = DieColour.RED),
    YELLOWS(ScoreSection.COLOUR, fixedScore = 40, matchingColour = DieColour.YELLOW),
    BLUES(ScoreSection.COLOUR, fixedScore = 40, matchingColour = DieColour.BLUE),

    /** Three dice of one colour and two of another - a full house by colour rather than by number. */
    COLOURED_HOUSE(ScoreSection.COLOUR, fixedScore = 25, jokerFreeFill = true),

    /** Two different numbers each on at least two dice: the four dice making the pairs are added up, whatever the fifth shows. */
    TWO_PAIR(ScoreSection.EXTENDED),

    /** The even dice added up. */
    EVENS(ScoreSection.EXTENDED),

    /** The odd dice added up. */
    ODDS(ScoreSection.EXTENDED),

    /** Hit List's targets, in card order - each one whatever [HitTarget] the game drew for it. */
    TARGET_1(ScoreSection.HIT_LIST),
    TARGET_2(ScoreSection.HIT_LIST),
    TARGET_3(ScoreSection.HIT_LIST),
    TARGET_4(ScoreSection.HIT_LIST),
    TARGET_5(ScoreSection.HIT_LIST),
    TARGET_6(ScoreSection.HIT_LIST),
    TARGET_7(ScoreSection.HIT_LIST),
    TARGET_8(ScoreSection.HIT_LIST),
    TARGET_9(ScoreSection.HIT_LIST),
    TARGET_10(ScoreSection.HIT_LIST),
    TARGET_11(ScoreSection.HIT_LIST),
    TARGET_12(ScoreSection.HIT_LIST),

    /**
     * Hit List's stand-in for a target: a hit scored here instead of in its target, which stays open for another
     * try. Worth the highest [HitTarget.points] among the open targets the dice hit - never doubled.
     */
    ALIBI(ScoreSection.HIT_LIST, featured = true),

    /** D20's numbers: each adds up the dice showing its number, like an upper box - 1 to 10 in the first half, 11 to 20 in the second. */
    D20_1(ScoreSection.FIRST_HALF, number = 1),
    D20_2(ScoreSection.FIRST_HALF, number = 2),
    D20_3(ScoreSection.FIRST_HALF, number = 3),
    D20_4(ScoreSection.FIRST_HALF, number = 4),
    D20_5(ScoreSection.FIRST_HALF, number = 5),
    D20_6(ScoreSection.FIRST_HALF, number = 6),
    D20_7(ScoreSection.FIRST_HALF, number = 7),
    D20_8(ScoreSection.FIRST_HALF, number = 8),
    D20_9(ScoreSection.FIRST_HALF, number = 9),
    D20_10(ScoreSection.FIRST_HALF, number = 10),
    D20_11(ScoreSection.SECOND_HALF, number = 11),
    D20_12(ScoreSection.SECOND_HALF, number = 12),
    D20_13(ScoreSection.SECOND_HALF, number = 13),
    D20_14(ScoreSection.SECOND_HALF, number = 14),
    D20_15(ScoreSection.SECOND_HALF, number = 15),
    D20_16(ScoreSection.SECOND_HALF, number = 16),
    D20_17(ScoreSection.SECOND_HALF, number = 17),
    D20_18(ScoreSection.SECOND_HALF, number = 18),
    D20_19(ScoreSection.SECOND_HALF, number = 19),
    D20_20(ScoreSection.SECOND_HALF, number = 20),

    /**
     * D20's wildcards, one for each half: every die showing a number in its half added up, whichever numbers they are - a
     * box that takes any of 1 to 10, or any of 11 to 20.
     */
    D20_WILD_LOW(ScoreSection.FIRST_HALF, wildcardNumbers = 1..10),
    D20_WILD_HIGH(ScoreSection.SECOND_HALF, wildcardNumbers = 11..20),
    ;

    /** The faces this box counts - one for a numbered box, a range for a wildcard, null for a box that counts no number. */
    val countedNumbers: IntRange?
        get() = number?.let { it..it } ?: wildcardNumbers

    /** Whether this is one of Hit List's targets (not its Alibi). */
    val isTarget: Boolean
        get() = this in TARGETS

    companion object {
        /** The boxes the Extended Scores modifier adds to a card, in scorecard order. */
        val EXTENDED: List<ScoreCategory> = entries.filter { it.section == ScoreSection.EXTENDED }

        /** Every Hit List target box, in card order. */
        val TARGETS: List<ScoreCategory> = entries.filter { it.section == ScoreSection.HIT_LIST && it != ALIBI }

        /** D20's whole card: its twenty number boxes, then the two wildcards. */
        val D20_CARD: List<ScoreCategory> = entries.filter { it.section == ScoreSection.FIRST_HALF || it.section == ScoreSection.SECOND_HALF }

        /** D20's twenty number boxes, 1 to 20. */
        val D20_NUMBERS: List<ScoreCategory> = D20_CARD.filter { it.number != null }
    }
}
