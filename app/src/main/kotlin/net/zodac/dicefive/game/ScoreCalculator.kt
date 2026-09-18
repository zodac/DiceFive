package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory

/**
 * Resolves what a player may score with their current dice, applying the
 * Yahtzee joker rule: rolling a second (or later) Yahtzee after the
 * YAHTZEE box already shows 50 always earns a +100 bonus chip, and forces
 * the player into the matching upper-section box if it's still open —
 * otherwise they may free-fill Full House/Small Straight/Large Straight at
 * full value, or zero any other open box.
 */
object ScoreCalculator {

    private val UPPER_CATEGORY_FOR_VALUE = mapOf(
        1 to ScoreCategory.ONES,
        2 to ScoreCategory.TWOS,
        3 to ScoreCategory.THREES,
        4 to ScoreCategory.FOURS,
        5 to ScoreCategory.FIVES,
        6 to ScoreCategory.SIXES,
    )

    private val JOKER_FREE_SCORES = mapOf(
        ScoreCategory.FULL_HOUSE to 25,
        ScoreCategory.SMALL_STRAIGHT to 30,
        ScoreCategory.LARGE_STRAIGHT to 40,
    )

    /** Categories the player may legally choose for their current dice. */
    fun availableCategories(player: PlayerState, dice: List<Die>): List<ScoreCategory> {
        val open = ScoreCategory.entries.filter { player.scorecard[it] == null }
        if (!isJokerSituation(player, dice)) return open

        val forced = UPPER_CATEGORY_FOR_VALUE[dice.first().value]?.takeIf { it in open }
        return forced?.let { listOf(it) } ?: open
    }

    /** The scorecard cell value for [category] with the current [dice] (excludes any Yahtzee bonus chip). */
    fun scoreFor(player: PlayerState, category: ScoreCategory, dice: List<Die>): Int =
        if (isJokerSituation(player, dice) && category in JOKER_FREE_SCORES) {
            JOKER_FREE_SCORES.getValue(category)
        } else {
            YahtzeeScoring.score(category, dice)
        }

    /** Whether scoring the current [dice] earns this player a +100 Yahtzee bonus chip. */
    fun awardsYahtzeeBonus(player: PlayerState, dice: List<Die>): Boolean =
        isJokerSituation(player, dice)

    private fun isJokerSituation(player: PlayerState, dice: List<Die>): Boolean =
        YahtzeeScoring.isYahtzee(dice) && player.scorecard[ScoreCategory.YAHTZEE] == 50
}
