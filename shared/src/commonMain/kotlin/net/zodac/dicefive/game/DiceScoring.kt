package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.ScoreCategory

/**
 * Stateless scoring rules for a single dice roll. Does not know about a
 * player's scorecard — see [ScoreCalculator] for the joker rule,
 * which depends on what's already been scored.
 */
object DiceScoring {

    /**
     * What a filled FIVE_OF_A_KIND box is worth. Also how "this player rolled one" is recognised after
     * the fact, since a scorecard only stores the value - see [ScoreCalculator]'s joker rule and
     * [AchievementEngine].
     */
    val FIVE_OF_A_KIND_SCORE: Int = requireNotNull(ScoreCategory.FIVE_OF_A_KIND.fixedScore)

    private val STRAIGHT_RUN = listOf(1, 2, 3, 4, 5, 6)

    fun score(category: ScoreCategory, dice: List<Die>): Int {
        val values = dice.map { it.value }
        val counts = values.groupingBy { it }.eachCount()
        return when (category) {
            ScoreCategory.ONES -> scoreUpper(values, 1)
            ScoreCategory.TWOS -> scoreUpper(values, 2)
            ScoreCategory.THREES -> scoreUpper(values, 3)
            ScoreCategory.FOURS -> scoreUpper(values, 4)
            ScoreCategory.FIVES -> scoreUpper(values, 5)
            ScoreCategory.SIXES -> scoreUpper(values, 6)
            ScoreCategory.THREE_OF_A_KIND -> if (counts.values.any { it >= 3 }) values.sum() else 0
            ScoreCategory.FOUR_OF_A_KIND -> if (counts.values.any { it >= 4 }) values.sum() else 0
            ScoreCategory.FULL_HOUSE -> fixedScoreIf(category, isHouse(counts))
            ScoreCategory.SMALL_STRAIGHT -> fixedScoreIf(category, hasStraight(values.toSet(), 4))
            ScoreCategory.LARGE_STRAIGHT -> fixedScoreIf(category, hasStraight(values.toSet(), 5))
            ScoreCategory.FIVE_OF_A_KIND -> fixedScoreIf(category, isFiveOfAKind(dice))
            ScoreCategory.CHANCE -> values.sum()
            ScoreCategory.REDS, ScoreCategory.YELLOWS, ScoreCategory.BLUES ->
                fixedScoreIf(category, isAllOneColour(dice, requireNotNull(category.matchingColour)))
            // Every die needs a colour: a colourless mode's dice would otherwise all group under null.
            ScoreCategory.COLOURED_HOUSE ->
                fixedScoreIf(category, dice.all { it.colour != null } && isHouse(dice.groupingBy { it.colour }.eachCount()))
        }
    }

    fun isFiveOfAKind(dice: List<Die>): Boolean =
        dice.isNotEmpty() && dice.all { it.value == dice.first().value }

    private fun fixedScoreIf(category: ScoreCategory, matched: Boolean): Int =
        if (matched) requireNotNull(category.fixedScore) else 0

    private fun scoreUpper(values: List<Int>, target: Int): Int =
        values.count { it == target } * target

    /** Three of one thing and two of another - numbers for Full House, colours for Coloured House. */
    private fun isHouse(counts: Map<*, Int>): Boolean =
        counts.values.sorted() == listOf(2, 3)

    private fun isAllOneColour(dice: List<Die>, colour: DieColour): Boolean =
        dice.isNotEmpty() && dice.all { it.colour == colour }

    private fun hasStraight(distinctValues: Set<Int>, length: Int): Boolean =
        STRAIGHT_RUN.windowed(length).any { window -> distinctValues.containsAll(window) }
}
