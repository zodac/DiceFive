package net.zodac.dicefive.model

/**
 * A player's live state within a game: identity plus their scorecard.
 * [scorecard] maps every one of [gameMode]'s categories to its filled-in value, or `null`
 * while the box is still open. [fiveOfAKindBonusCount] tracks how many extra
 * 5x bonus chips this player has earned.
 *
 * [gameMode] is carried here as well as on [GameState] because it decides how this scorecard adds
 * up - which boxes it has, the upper-section bonus, what a bonus chip is worth - so a player's totals
 * never need the game around them to be read.
 */
data class PlayerState(
    val name: String,
    val type: PlayerType,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val gameMode: GameMode = GameMode.default,
    val scorecard: Map<ScoreCategory, Int?> = gameMode.categories.associateWith { null },
    val fiveOfAKindBonusCount: Int = 0,
) {

    val isScorecardComplete: Boolean
        get() = gameMode.categories.all { scorecard[it] != null }

    val upperSectionTotal: Int
        get() = sectionTotal(ScoreSection.UPPER)

    val upperSectionBonus: Int
        get() = if (upperSectionTotal >= gameMode.upperBonusThreshold) gameMode.upperBonusAmount else 0

    val lowerSectionTotal: Int
        get() = sectionTotal(ScoreSection.LOWER)

    /** Zero in a mode with no colour boxes. */
    val colourSectionTotal: Int
        get() = sectionTotal(ScoreSection.COLOUR)

    val fiveOfAKindBonusTotal: Int
        get() = fiveOfAKindBonusCount * gameMode.fiveOfAKindBonusAmount

    val totalScore: Int
        get() = upperSectionTotal + upperSectionBonus + lowerSectionTotal + colourSectionTotal + fiveOfAKindBonusTotal

    private fun sectionTotal(section: ScoreSection): Int =
        gameMode.categories.filter { it.section == section }.sumOf { scorecard[it] ?: 0 }

    companion object {
        /** ONES..SIXES, in pip order - so a category's index here, plus one, is the pip value it counts. */
        val UPPER_CATEGORIES: List<ScoreCategory> = ScoreCategory.entries.filter { it.section == ScoreSection.UPPER }
    }
}
