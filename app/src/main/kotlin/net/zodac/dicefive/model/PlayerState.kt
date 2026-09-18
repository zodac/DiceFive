package net.zodac.dicefive.model

/**
 * A player's live state within a game: identity plus their scorecard.
 * [scorecard] maps every [ScoreCategory] to its filled-in value, or `null`
 * while the box is still open. [yahtzeeBonusCount] tracks how many extra
 * Yahtzee bonus chips (+100 each) this player has earned.
 */
data class PlayerState(
    val name: String,
    val type: PlayerType,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val scorecard: Map<ScoreCategory, Int?> = ScoreCategory.entries.associateWith { null },
    val yahtzeeBonusCount: Int = 0,
) {

    val isScorecardComplete: Boolean
        get() = scorecard.values.none { it == null }

    val upperSectionTotal: Int
        get() = UPPER_CATEGORIES.sumOf { scorecard[it] ?: 0 }

    val upperSectionBonus: Int
        get() = if (upperSectionTotal >= UPPER_BONUS_THRESHOLD) UPPER_BONUS_AMOUNT else 0

    val lowerSectionTotal: Int
        get() = LOWER_CATEGORIES.sumOf { scorecard[it] ?: 0 }

    val yahtzeeBonusTotal: Int
        get() = yahtzeeBonusCount * YAHTZEE_BONUS_AMOUNT

    val totalScore: Int
        get() = upperSectionTotal + upperSectionBonus + lowerSectionTotal + yahtzeeBonusTotal

    companion object {
        const val UPPER_BONUS_THRESHOLD = 63
        const val UPPER_BONUS_AMOUNT = 35
        const val YAHTZEE_BONUS_AMOUNT = 100

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
