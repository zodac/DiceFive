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
        require(oddsPercent in ODDS_PERCENT) { "Unlucky odds must be ${ODDS_PERCENT.first}-${ODDS_PERCENT.last}%" }
        require(maxDice in MAX_DICE) { "Most unlucky dice must be ${MAX_DICE.first}-${MAX_DICE.last}" }
    }

    companion object {
        /** The odds a rolled die can be given of landing unlucky, in percent. */
        val ODDS_PERCENT: IntRange = 10..50

        /** How far apart the odds can be set. */
        const val ODDS_STEP_PERCENT = 10
        const val DEFAULT_ODDS_PERCENT = 10

        /** The caps on how many dice can land unlucky on one roll. */
        val MAX_DICE: IntRange = 1..5
        const val DEFAULT_MAX_DICE = 1
    }
}
