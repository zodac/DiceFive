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
        ScoreCategory.TWO_PAIR -> scoreTwoPair(dice)
        ScoreCategory.EVENS -> dice.sumOf { if (it.value % 2 == 0) it.value else 0 }
        ScoreCategory.ODDS -> dice.sumOf { if (it.value % 2 != 0) it.value else 0 }
        // Each game draws its own targets, so there's no rule here to score them by.
        ScoreCategory.TARGET_1, ScoreCategory.TARGET_2, ScoreCategory.TARGET_3, ScoreCategory.TARGET_4,
        ScoreCategory.TARGET_5, ScoreCategory.TARGET_6, ScoreCategory.TARGET_7, ScoreCategory.TARGET_8,
        ScoreCategory.TARGET_9, ScoreCategory.TARGET_10, ScoreCategory.TARGET_11, ScoreCategory.TARGET_12,
        ScoreCategory.ALIBI,
        -> throw IllegalArgumentException("$category is scored against the player's hit list - see ScoreCalculator.scoreFor")
    }

    /**
     * Whether [dice] are a 5x: at least five of them, all matching. Fewer never are - a part-held
     * hand in a mode where only held dice score is previewed, and three matching dice there aren't one.
     */
    fun isFiveOfAKind(dice: List<Die>): Boolean =
        dice.size >= FIVE_OF_A_KIND_DICE && dice.all { it.value == dice.first().value }

    private const val FIVE_OF_A_KIND_DICE = 5

    private fun fixedScoreIf(category: ScoreCategory, matched: Boolean): Int =
        if (matched) requireNotNull(category.fixedScore) else 0

    private fun scoreUpper(dice: List<Die>, target: Int): Int =
        dice.count { it.value == target } * target

    private fun valueCounts(dice: List<Die>): Map<Int, Int> = dice.groupingBy { it.value }.eachCount()

    /**
     * Two different numbers each on at least two dice: the four dice making those pairs, added up. A third
     * die matching one of the pairs isn't counted, and four of one number is one pair, not two.
     */
    private fun scoreTwoPair(dice: List<Die>): Int {
        val pairs = valueCounts(dice).filterValues { it >= 2 }.keys
        return if (pairs.size == 2) pairs.sum() * 2 else 0
    }

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
