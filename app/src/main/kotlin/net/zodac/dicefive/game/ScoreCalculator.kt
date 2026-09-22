package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory

/**
 * Resolves what a player may score with their current dice, applying the official joker
 * rule: once a player's FIVE_OF_A_KIND box already shows 50, rolling another 5x earns a +100 bonus
 * chip unconditionally, and dictates - not just previews - which box the roll must go in:
 *   1. The matching upper-section box, if it's still open - mandatory, no other choice.
 *   2. Otherwise, any still-open LOWER-section box - the player's choice, and Full House/Small
 *      Straight/Large Straight score their full value (25/30/40) regardless of what the dice
 *      actually show.
 *   3. Otherwise (every matching upper and lower box already filled), any remaining open box -
 *      the player's choice of which one eats the zero (an upper box scores 0 there naturally,
 *      since none of the dice match a different number).
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

    /** Categories the player may legally choose for their current dice - see the class doc for the joker rule's forcing order. */
    fun availableCategories(player: PlayerState, dice: List<Die>): List<ScoreCategory> {
        val open = ScoreCategory.entries.filter { player.scorecard[it] == null }
        if (!isJokerSituation(player, dice)) return open

        val forcedUpper = UPPER_CATEGORY_FOR_VALUE.getValue(dice.first().value).takeIf { it in open }
        if (forcedUpper != null) return listOf(forcedUpper)

        val openLower = open.filterNot { it in PlayerState.UPPER_CATEGORIES }
        return openLower.ifEmpty { open }
    }

    /** The scorecard cell value for [category] with the current [dice] (excludes any 5x bonus chip). */
    fun scoreFor(player: PlayerState, category: ScoreCategory, dice: List<Die>): Int =
        if (isJokerSituation(player, dice) && category in JOKER_FREE_SCORES) {
            JOKER_FREE_SCORES.getValue(category)
        } else {
            DiceScoring.score(category, dice)
        }

    /** Whether committing this roll (in whichever category ends up chosen) earns the +100 5x bonus chip. */
    fun awardsFiveOfAKindBonus(player: PlayerState, dice: List<Die>): Boolean = isJokerSituation(player, dice)

    private fun isJokerSituation(player: PlayerState, dice: List<Die>): Boolean =
        DiceScoring.isFiveOfAKind(dice) && player.scorecard[ScoreCategory.FIVE_OF_A_KIND] == 50
}
