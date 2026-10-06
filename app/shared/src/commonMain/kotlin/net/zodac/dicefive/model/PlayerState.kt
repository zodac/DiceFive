package net.zodac.dicefive.model

/**
 * A player's live state within a game: identity plus their scorecard.
 * [scorecard] maps every one of this player's [categories] to the scores filled into it so far, in the
 * order they went in - empty while the box is untouched, and full once it holds
 * [GameMode.scoresPerCategory] of them (one, in every mode but Third Wind). [fiveOfAKindBonusCount]
 * tracks how many extra 5x bonus chips this player has earned.
 *
 * [gameMode] is carried here as well as on [GameState] because it decides how this scorecard adds
 * up - which boxes it has, how many slots each, the upper-section bonus, what a bonus chip is worth -
 * so a player's totals never need the game around them to be read.
 */
data class PlayerState(
    val name: String,
    val type: PlayerType,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val gameMode: GameMode = GameMode.default,
    /** Whether the Extended Scores modifier is on, adding its boxes to [categories]. */
    val extendedScores: Boolean = false,
    val scorecard: Map<ScoreCategory, List<Int>> = gameMode.categoriesWith(extendedScores).associateWith { emptyList() },
    val fiveOfAKindBonusCount: Int = 0,
    /** The dice this player's last completed turn was scored with - value and held/unheld state
     * both, as they stood the moment they tapped a category. Null before this player's first turn
     * ends. [GameState.dice] itself is reset for the next player the instant a turn advances, so
     * this is the only place a finished turn's roll survives - kept so another player can glance at
     * it via [net.zodac.dicefive.ui.game.ReadOnlyScoreboard] without it changing mid-glance. */
    val lastRoll: List<Die>? = null,
    /** The box this player's last completed turn was scored in - null before their first turn ends.
     * Shown with [lastRoll] when another player looks at this scorecard, so they can see what that
     * roll went on, not just the roll. */
    val lastScoredCategory: ScoreCategory? = null,
    /** How many times this player has rolled so far this game, every turn's rolls added up - what
     * the Flowerpot cup's plant grows by (see [flowerpotGrowthStage]). */
    val rollCount: Int = 0,
    /** Rolls this player left unused at the end of their last turn and keeps for their next - always 0
     * unless the Stored Rolls modifier is on. */
    val storedRolls: Int = 0,
    /** Whether a roll modifier is on, so this player's rolls (and so their totals) aren't what the
     * mode's own rules would give - what keeps the achievements measured on totals from being handed out. */
    val rollsModified: Boolean = false,
) {

    /** Every box on this player's card: [gameMode]'s own, plus the Extended Scores modifier's when it's on. */
    val categories: List<ScoreCategory>
        get() = gameMode.categoriesWith(extendedScores)

    /** How many turns this player's game lasts: a turn for every slot of every box on the card. */
    val turnsPerGame: Int
        get() = categories.size * gameMode.scoresPerCategory

    /**
     * The most rolls this player can make in their game - [GameMode.maxRollsPerGame], stretched over the
     * turns the extra boxes add. Always a whole number: it's the mode's rolls a turn times its turns.
     */
    val maxRollsPerGame: Int
        get() = gameMode.maxRollsPerGame / gameMode.turnsPerGame * turnsPerGame

    /** The scores filled into [category] so far, in the order they went in. */
    fun scoresIn(category: ScoreCategory): List<Int> = scorecard[category].orEmpty()

    /** Whether [category] still has a slot to score in. */
    fun isOpen(category: ScoreCategory): Boolean = scoresIn(category).size < gameMode.scoresPerCategory

    /** Whether [category] holds [score] in any of its slots. */
    fun hasScore(category: ScoreCategory, score: Int): Boolean = score in scoresIn(category)

    /** Every score on the card, one per turn taken. */
    val allScores: List<Int>
        get() = categories.flatMap { scoresIn(it) }

    /** How many turns this player has scored so far - one per filled slot. */
    val turnsTaken: Int
        get() = categories.sumOf { scoresIn(it).size }

    /** How many turns this player has still to take - one per open slot. */
    val turnsLeft: Int
        get() = turnsPerGame - turnsTaken

    val isScorecardComplete: Boolean
        get() = turnsLeft == 0

    val upperSectionTotal: Int
        get() = sectionTotal(ScoreSection.UPPER)

    val upperSectionBonus: Int
        get() = if (upperSectionTotal >= gameMode.upperBonusThreshold) gameMode.upperBonusAmount else 0

    val lowerSectionTotal: Int
        get() = sectionTotal(ScoreSection.LOWER)

    /**
     * [lowerSectionTotal] without the 5x box - what the "Lower Class" achievement tracks, so a
     * filled 5x (a fixed 50, or 0 as a joker fill) never counts towards it the way the other
     * lower-section categories, scored from the dice themselves, do. Repeat-5x bonus chips were
     * never part of [lowerSectionTotal] in the first place, so there's nothing extra to subtract
     * for those.
     */
    val lowerSectionTotalExcludingFiveOfAKind: Int
        get() = lowerSectionTotal - scoresIn(ScoreCategory.FIVE_OF_A_KIND).sum()

    /** Zero in a mode with no colour boxes. */
    val colourSectionTotal: Int
        get() = sectionTotal(ScoreSection.COLOUR)

    /** Zero without the Extended Scores modifier. */
    val extendedSectionTotal: Int
        get() = sectionTotal(ScoreSection.EXTENDED)

    val fiveOfAKindBonusTotal: Int
        get() = fiveOfAKindBonusCount * gameMode.fiveOfAKindBonusAmount

    /**
     * Whether the 5x box is full and at least one of its slots holds its full score - what makes
     * another 5x a joker, earning a bonus chip (see `ScoreCalculator`). In a one-slot mode, simply
     * that the box shows 50.
     */
    val fiveOfAKindJokerActive: Boolean
        get() = !isOpen(ScoreCategory.FIVE_OF_A_KIND) && hasScore(ScoreCategory.FIVE_OF_A_KIND, FIVE_OF_A_KIND_FULL_SCORE)

    /**
     * How many 5x this player actually scored: every slot of the 5x box holding its full score (a
     * zero there was never a 5x), plus one for every bonus chip, each of which is a later 5x. The
     * one definition both the leaderboard ([net.zodac.dicefive.data.scores.ScoreEntry]) and the
     * achievements' "scored a 5x" counter use.
     */
    val fiveOfAKindCount: Int
        get() = scoresIn(ScoreCategory.FIVE_OF_A_KIND).count { it == FIVE_OF_A_KIND_FULL_SCORE } + fiveOfAKindBonusCount

    val totalScore: Int
        get() = upperSectionTotal + upperSectionBonus + lowerSectionTotal + colourSectionTotal + extendedSectionTotal + fiveOfAKindBonusTotal

    private fun sectionTotal(section: ScoreSection): Int =
        categories.filter { it.section == section }.sumOf { scoresIn(it).sum() }

    companion object {
        /** ONES..SIXES, in pip order - so a category's index here, plus one, is the pip value it counts. */
        val UPPER_CATEGORIES: List<ScoreCategory> = ScoreCategory.entries.filter { it.section == ScoreSection.UPPER }

        private val FIVE_OF_A_KIND_FULL_SCORE: Int = requireNotNull(ScoreCategory.FIVE_OF_A_KIND.fixedScore)
    }
}
