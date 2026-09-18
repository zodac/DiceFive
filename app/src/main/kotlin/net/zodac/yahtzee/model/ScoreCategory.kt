package net.zodac.yahtzee.model

/**
 * The scoring categories on a standard Yahtzee scorecard.
 * Scoring rules for each category will be implemented once game requirements are finalized.
 */
enum class ScoreCategory {
    ONES,
    TWOS,
    THREES,
    FOURS,
    FIVES,
    SIXES,
    THREE_OF_A_KIND,
    FOUR_OF_A_KIND,
    FULL_HOUSE,
    SMALL_STRAIGHT,
    LARGE_STRAIGHT,
    YAHTZEE,
    CHANCE,
}
