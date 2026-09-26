package net.zodac.dicefive.model

/**
 * Which part of the scorecard a [ScoreCategory] belongs to. [UPPER] is what the upper-section bonus
 * is measured against, and what the joker rule forces a matching 5x into first. [COLOUR] is kept
 * apart from [LOWER] rather than folded into it, so a lower-section total (and "Lower Class") means
 * the same thing in every [GameMode].
 */
enum class ScoreSection {
    UPPER,
    LOWER,
    COLOUR,
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
 *
 * Declaration order is scorecard order: the upper section (ONES..SIXES), then the lower section,
 * then the colour section.
 */
enum class ScoreCategory(
    val section: ScoreSection,
    val fixedScore: Int? = null,
    val jokerFreeFill: Boolean = false,
    val matchingColour: DieColour? = null,
) {
    ONES(ScoreSection.UPPER),
    TWOS(ScoreSection.UPPER),
    THREES(ScoreSection.UPPER),
    FOURS(ScoreSection.UPPER),
    FIVES(ScoreSection.UPPER),
    SIXES(ScoreSection.UPPER),
    THREE_OF_A_KIND(ScoreSection.LOWER),
    FOUR_OF_A_KIND(ScoreSection.LOWER),
    FULL_HOUSE(ScoreSection.LOWER, fixedScore = 25, jokerFreeFill = true),
    SMALL_STRAIGHT(ScoreSection.LOWER, fixedScore = 30, jokerFreeFill = true),
    LARGE_STRAIGHT(ScoreSection.LOWER, fixedScore = 40, jokerFreeFill = true),
    FIVE_OF_A_KIND(ScoreSection.LOWER, fixedScore = 50),
    CHANCE(ScoreSection.LOWER),

    REDS(ScoreSection.COLOUR, fixedScore = 40, matchingColour = DieColour.RED),
    YELLOWS(ScoreSection.COLOUR, fixedScore = 40, matchingColour = DieColour.YELLOW),
    BLUES(ScoreSection.COLOUR, fixedScore = 40, matchingColour = DieColour.BLUE),

    /** Three dice of one colour and two of another - a full house by colour rather than by number. */
    COLOURED_HOUSE(ScoreSection.COLOUR, fixedScore = 25, jokerFreeFill = true),
}
