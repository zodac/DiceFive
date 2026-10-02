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

    /** Every run of 4 and of 5 consecutive faces, by length - built once rather than on every straight check. */
    private val STRAIGHT_WINDOWS: Map<Int, List<List<Int>>> = listOf(4, 5).associateWith { (1..6).toList().windowed(it) }

    // Only what each category needs is worked out from the dice: Hard's hold search scores every
    // open category of tens of thousands of hands per decision in Tricolour, so the counts and
    // distinct values a category never looks at aren't built for it.
    fun score(category: ScoreCategory, dice: List<Die>): Int = when (category) {
        ScoreCategory.ONES -> scoreUpper(dice, 1)
        ScoreCategory.TWOS -> scoreUpper(dice, 2)
        ScoreCategory.THREES -> scoreUpper(dice, 3)
        ScoreCategory.FOURS -> scoreUpper(dice, 4)
        ScoreCategory.FIVES -> scoreUpper(dice, 5)
        ScoreCategory.SIXES -> scoreUpper(dice, 6)
        ScoreCategory.THREE_OF_A_KIND -> if (valueCounts(dice).values.any { it >= 3 }) dice.sumOf { it.value } else 0
        ScoreCategory.FOUR_OF_A_KIND -> if (valueCounts(dice).values.any { it >= 4 }) dice.sumOf { it.value } else 0
        ScoreCategory.FULL_HOUSE -> fixedScoreIf(category, isHouse(valueCounts(dice)))
        ScoreCategory.SMALL_STRAIGHT -> fixedScoreIf(category, hasStraight(dice, 4))
        ScoreCategory.LARGE_STRAIGHT -> fixedScoreIf(category, hasStraight(dice, 5))
        ScoreCategory.FIVE_OF_A_KIND -> fixedScoreIf(category, isFiveOfAKind(dice))
        ScoreCategory.CHANCE -> dice.sumOf { it.value }
        ScoreCategory.REDS, ScoreCategory.YELLOWS, ScoreCategory.BLUES ->
            fixedScoreIf(category, isAllOneColour(dice, requireNotNull(category.matchingColour)))
        // Every die needs a colour: a colourless mode's dice would otherwise all group under null.
        ScoreCategory.COLOURED_HOUSE ->
            fixedScoreIf(category, dice.all { it.colour != null } && isHouse(dice.groupingBy { it.colour }.eachCount()))
    }

    fun isFiveOfAKind(dice: List<Die>): Boolean =
        dice.isNotEmpty() && dice.all { it.value == dice.first().value }

    private fun fixedScoreIf(category: ScoreCategory, matched: Boolean): Int =
        if (matched) requireNotNull(category.fixedScore) else 0

    private fun scoreUpper(dice: List<Die>, target: Int): Int =
        dice.count { it.value == target } * target

    private fun valueCounts(dice: List<Die>): Map<Int, Int> = dice.groupingBy { it.value }.eachCount()

    /** Three of one thing and two of another - numbers for Full House, colours for Coloured House. */
    private fun isHouse(counts: Map<*, Int>): Boolean =
        counts.values.sorted() == listOf(2, 3)

    private fun isAllOneColour(dice: List<Die>, colour: DieColour): Boolean =
        dice.isNotEmpty() && dice.all { it.colour == colour }

    private fun hasStraight(dice: List<Die>, length: Int): Boolean {
        val distinctValues = dice.mapTo(HashSet()) { it.value }
        return STRAIGHT_WINDOWS.getValue(length).any { window -> distinctValues.containsAll(window) }
    }
}
