package net.zodac.dicefive.model

/**
 * The Unlucky Dice modifier, chosen on the setup screen and carried on [GameState] for the life of the
 * game. Null on the game means it's off.
 *
 * Every roll, each die that is rolled (never a held one) is [oddsPercent] likely to land unlucky
 * ([Die.isUnlucky]) - locked in chains, so it can't be held and doesn't score - but no more than
 * [maxDice] of them on any one roll. An unlucky die is rolled again with the rest on the next roll,
 * and can come up unlucky again or not.
 */
data class UnluckyDice(
    val oddsPercent: Int = DEFAULT_ODDS_PERCENT,
    val maxDice: Int = DEFAULT_MAX_DICE,
) {
    init {
        require(oddsPercent in MIN_ODDS_PERCENT..MAX_ODDS_PERCENT) { "Unlucky odds must be $MIN_ODDS_PERCENT-$MAX_ODDS_PERCENT%" }
        require(maxDice in MIN_MAX_DICE..MAX_MAX_DICE) { "Most unlucky dice must be $MIN_MAX_DICE-$MAX_MAX_DICE" }
    }

    companion object {
        const val MIN_ODDS_PERCENT = 10
        const val MAX_ODDS_PERCENT = 50

        /** How far apart the odds can be set. */
        const val ODDS_STEP_PERCENT = 10
        const val DEFAULT_ODDS_PERCENT = 10

        const val MIN_MAX_DICE = 1
        const val MAX_MAX_DICE = 5
        const val DEFAULT_MAX_DICE = 1
    }
}
