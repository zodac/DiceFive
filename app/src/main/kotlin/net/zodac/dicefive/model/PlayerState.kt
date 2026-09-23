package net.zodac.dicefive.model

/**
 * A player's live state within a game: identity plus their scorecard.
 * [scorecard] maps every [ScoreCategory] to its filled-in value, or `null`
 * while the box is still open. [fiveOfAKindBonusCount] tracks how many extra
 * 5x bonus chips (+100 each) this player has earned.
 */
data class PlayerState(
    val name: String,
    val type: PlayerType,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val scorecard: Map<ScoreCategory, Int?> = ScoreCategory.entries.associateWith { null },
    val fiveOfAKindBonusCount: Int = 0,
) {

    val isScorecardComplete: Boolean
        get() = scorecard.values.none { it == null }

    val upperSectionTotal: Int
        get() = UPPER_CATEGORIES.sumOf { scorecard[it] ?: 0 }

    val upperSectionBonus: Int
        get() = if (upperSectionTotal >= UPPER_BONUS_THRESHOLD) UPPER_BONUS_AMOUNT else 0

    val lowerSectionTotal: Int
        get() = LOWER_CATEGORIES.sumOf { scorecard[it] ?: 0 }

    val fiveOfAKindBonusTotal: Int
        get() = fiveOfAKindBonusCount * FIVE_OF_A_KIND_BONUS_AMOUNT

    val totalScore: Int
        get() = upperSectionTotal + upperSectionBonus + lowerSectionTotal + fiveOfAKindBonusTotal

    companion object {
        const val UPPER_BONUS_THRESHOLD = 63
        const val UPPER_BONUS_AMOUNT = 35
        const val FIVE_OF_A_KIND_BONUS_AMOUNT = 100

        /**
         * The highest [totalScore] the rules allow - the "perfect game." One turn banks the
         * actual 5x box (50); every other turn also rolls a 5x, each earning the +100 bonus chip
         * on top of whichever box it fills (an upper box via a same-value 5x, THREE_OF_A_KIND/
         * FOUR_OF_A_KIND/CHANCE via the dice's own sum, FULL_HOUSE/SMALL_STRAIGHT/LARGE_STRAIGHT
         * via the joker free-fill - see [net.zodac.dicefive.game.ScoreCalculator]):
         *
         * `5*(1+2+3+4+5+6)` upper, maxed = 105, `+35` upper bonus, `+50` the 5x box itself,
         * `+30+30+25+30+40+30` the other six lower boxes maxed = 185, `+12*100` every one of the
         * other 12 turns also being a 5x = 1200. `105+35+50+185+1200 = 1575`.
         *
         * Used to size the fixed-width score columns on the Leaderboard and Statistics screens.
         */
        const val MAX_POSSIBLE_SCORE = 1575

        val UPPER_CATEGORIES = listOf(
            ScoreCategory.ONES,
            ScoreCategory.TWOS,
            ScoreCategory.THREES,
            ScoreCategory.FOURS,
            ScoreCategory.FIVES,
            ScoreCategory.SIXES,
        )
        private val LOWER_CATEGORIES = ScoreCategory.entries - UPPER_CATEGORIES.toSet()
    }
}
