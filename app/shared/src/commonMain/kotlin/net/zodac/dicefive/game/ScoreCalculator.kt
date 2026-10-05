package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection
import net.zodac.dicefive.model.TimeoutPick

/**
 * Resolves what a player may score with their current dice, applying the official joker
 * rule: once a player's FIVE_OF_A_KIND box already shows 50 (in a mode with several slots a box,
 * once every one of them is used and one shows 50 - see [PlayerState.fiveOfAKindJokerActive]),
 * rolling another 5x earns a bonus
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
 * Which categories exist at all comes from the player's own [PlayerState.gameMode]. "Open" means a
 * box with a slot still to score in ([PlayerState.isOpen]).
 */
object ScoreCalculator {

    /** Categories the player may legally choose for their current dice - see the class doc for the joker rule's forcing order. */
    fun availableCategories(player: PlayerState, dice: List<Die>): List<ScoreCategory> {
        val open = player.gameMode.categories.filter { player.isOpen(it) }
        if (!isJokerSituation(player, dice)) return open

        val forcedUpper = PlayerState.UPPER_CATEGORIES[dice.first().value - 1].takeIf { it in open }
        if (forcedUpper != null) return listOf(forcedUpper)

        val openOutsideUpper = open.filterNot { it.section == ScoreSection.UPPER }
        return openOutsideUpper.ifEmpty { open }
    }

    /**
     * Where a turn that ran out of time is scored, by the player's mode's [TimeoutPick]: the first
     * open category, or the one [dice] score least in (the first of them, in scorecard order, on a
     * tie). Always one of [availableCategories], so the joker rule still decides what's open.
     */
    fun timeoutCategory(player: PlayerState, dice: List<Die>): ScoreCategory {
        val available = availableCategories(player, dice)
        return when (player.gameMode.timeoutPick) {
            TimeoutPick.FIRST_OPEN -> available.first()
            // minBy keeps the first of equal minimums, which is the scorecard-order tie-break.
            TimeoutPick.LOWEST_SCORE -> available.minBy { scoreFor(player, it, dice) }
        }
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
        DiceScoring.isFiveOfAKind(dice) && player.fiveOfAKindJokerActive
}
