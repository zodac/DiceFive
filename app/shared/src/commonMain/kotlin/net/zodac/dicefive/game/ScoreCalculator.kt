package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection

/**
 * Resolves what a player may score with their current dice, applying the official joker
 * rule: once a player's FIVE_OF_A_KIND box already shows 50, rolling another 5x earns a bonus
 * chip ([net.zodac.dicefive.model.GameMode.fiveOfAKindBonusAmount]) unconditionally, and dictates -
 * not just previews - which box the roll must go in:
 *   1. The matching upper-section box, if it's still open - mandatory, no other choice.
 *   2. Otherwise, any still-open box outside the upper section - the player's choice, and every
 *      [ScoreCategory.jokerFreeFill] category (Full House, Small/Large Straight, and in Tricolour
 *      the Coloured House) scores its full value regardless of what the dice actually show. The
 *      single-colour boxes are open to it too, but score by the dice's real colours like any roll.
 *   3. Otherwise (every matching upper and lower box already filled), any remaining open box -
 *      the player's choice of which one eats the zero (an upper box scores 0 there naturally,
 *      since none of the dice match a different number).
 *
 * Which categories exist at all comes from the player's own [PlayerState.gameMode].
 */
object ScoreCalculator {

    /** Categories the player may legally choose for their current dice - see the class doc for the joker rule's forcing order. */
    fun availableCategories(player: PlayerState, dice: List<Die>): List<ScoreCategory> {
        val open = player.gameMode.categories.filter { player.scorecard[it] == null }
        if (!isJokerSituation(player, dice)) return open

        val forcedUpper = PlayerState.UPPER_CATEGORIES[dice.first().value - 1].takeIf { it in open }
        if (forcedUpper != null) return listOf(forcedUpper)

        val openOutsideUpper = open.filterNot { it.section == ScoreSection.UPPER }
        return openOutsideUpper.ifEmpty { open }
    }

    /** The scorecard cell value for [category] with the current [dice] (excludes any 5x bonus chip). */
    fun scoreFor(player: PlayerState, category: ScoreCategory, dice: List<Die>): Int =
        if (category.jokerFreeFill && isJokerSituation(player, dice)) {
            requireNotNull(category.fixedScore)
        } else {
            DiceScoring.score(category, dice)
        }

    /** Whether committing this roll (in whichever category ends up chosen) earns the 5x bonus chip. */
    fun awardsFiveOfAKindBonus(player: PlayerState, dice: List<Die>): Boolean = isJokerSituation(player, dice)

    /** What the 5x bonus chip for this roll is worth - zero if it doesn't earn one. */
    fun fiveOfAKindBonusFor(player: PlayerState, dice: List<Die>): Int =
        if (awardsFiveOfAKindBonus(player, dice)) player.gameMode.fiveOfAKindBonusAmount else 0

    private fun isJokerSituation(player: PlayerState, dice: List<Die>): Boolean =
        DiceScoring.isFiveOfAKind(dice) && player.scorecard[ScoreCategory.FIVE_OF_A_KIND] == DiceScoring.FIVE_OF_A_KIND_SCORE
}
