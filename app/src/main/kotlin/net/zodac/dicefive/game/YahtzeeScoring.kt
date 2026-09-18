package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.ScoreCategory

/**
 * Stateless scoring rules for a single dice roll. Does not know about a
 * player's scorecard — see [ScoreCalculator] for the Yahtzee joker rule,
 * which depends on what's already been scored.
 */
object YahtzeeScoring {

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
            ScoreCategory.FULL_HOUSE -> if (isFullHouse(counts)) 25 else 0
            ScoreCategory.SMALL_STRAIGHT -> if (hasStraight(values.toSet(), 4)) 30 else 0
            ScoreCategory.LARGE_STRAIGHT -> if (hasStraight(values.toSet(), 5)) 40 else 0
            ScoreCategory.YAHTZEE -> if (counts.values.any { it == 5 }) 50 else 0
            ScoreCategory.CHANCE -> values.sum()
        }
    }

    fun isYahtzee(dice: List<Die>): Boolean =
        dice.map { it.value }.groupingBy { it }.eachCount().values.any { it == 5 }

    private fun scoreUpper(values: List<Int>, target: Int): Int =
        values.count { it == target } * target

    private fun isFullHouse(counts: Map<Int, Int>): Boolean =
        counts.values.sorted() == listOf(2, 3)

    private fun hasStraight(distinctValues: Set<Int>, length: Int): Boolean =
        STRAIGHT_RUN.windowed(length).any { window -> distinctValues.containsAll(window) }
}
