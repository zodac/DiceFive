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
    /** The dice this player's last completed turn was scored with - value and held/unheld state
     * both, as they stood the moment they tapped a category. Null before this player's first turn
     * ends. [GameState.dice] itself is reset for the next player the instant a turn advances, so
     * this is the only place a finished turn's roll survives - kept so another player can glance at
     * it via [net.zodac.dicefive.ui.game.ReadOnlyScoreboard] without it changing mid-glance. */
    val lastRoll: List<Die>? = null,
) {

    val isScorecardComplete: Boolean
        get() = gameMode.categories.all { scorecard[it] != null }

    val upperSectionTotal: Int
        get() = sectionTotal(ScoreSection.UPPER)

    val upperSectionBonus: Int
        get() = if (upperSectionTotal >= gameMode.upperBonusThreshold) gameMode.upperBonusAmount else 0

    val lowerSectionTotal: Int
        get() = sectionTotal(ScoreSection.LOWER)

    /**
     * [lowerSectionTotal] without the 5x box - what the game screen's "Lower:" counter and the
     * "Lower Class" achievement track, so a filled 5x (a fixed 50, or 0 as a joker fill) never
     * counts towards it the way the other lower-section categories, scored from the dice
     * themselves, do.
     */
    val lowerSectionTotalExcludingFiveOfAKind: Int
        get() = lowerSectionTotal - (scorecard[ScoreCategory.FIVE_OF_A_KIND] ?: 0)

    /** Zero in a mode with no colour boxes. */
    val colourSectionTotal: Int
        get() = sectionTotal(ScoreSection.COLOUR)

    val fiveOfAKindBonusTotal: Int
        get() = fiveOfAKindBonusCount * gameMode.fiveOfAKindBonusAmount

    /**
     * How many 5x this player actually scored: the 5x box itself if it holds its full score (a
     * zero there was never a 5x), plus one for every bonus chip, each of which is a later 5x. The
     * one definition both the leaderboard ([net.zodac.dicefive.data.scores.ScoreEntry]) and the
     * achievements' "scored a 5x" counter use.
     */
    val fiveOfAKindCount: Int
        get() = (if (scorecard[ScoreCategory.FIVE_OF_A_KIND] == ScoreCategory.FIVE_OF_A_KIND.fixedScore) 1 else 0) +
            fiveOfAKindBonusCount

    val totalScore: Int
        get() = upperSectionTotal + upperSectionBonus + lowerSectionTotal + colourSectionTotal + fiveOfAKindBonusTotal

    private fun sectionTotal(section: ScoreSection): Int =
        gameMode.categories.filter { it.section == section }.sumOf { scorecard[it] ?: 0 }

    companion object {
        /** ONES..SIXES, in pip order - so a category's index here, plus one, is the pip value it counts. */
        val UPPER_CATEGORIES: List<ScoreCategory> = ScoreCategory.entries.filter { it.section == ScoreSection.UPPER }
    }
}
