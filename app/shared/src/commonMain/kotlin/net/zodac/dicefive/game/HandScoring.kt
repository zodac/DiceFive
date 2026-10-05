package net.zodac.dicefive.game

import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection

/**
 * Every hand of [space] scored in every category of [mode] once, up front, and [ScoreCalculator]'s
 * joker rule as plain arithmetic over them - which boxes a hand may go in and what each scores - so
 * Hard can value every finished hand of a turn, tens of thousands of them with coloured dice, without
 * scoring any of them again. [ScoreCalculator] stays the rule; this mirrors it, and `HandScoringTest`
 * checks the two agree.
 *
 * A scorecard is described here by which boxes are full - no slot left to score in - (a bit per
 * category, in [mode]'s order) and whether the 5x box makes another 5x a joker - all the joker rule
 * looks at.
 */
internal class HandScoring(val mode: GameMode, val space: DiceSpace) {

    val categories: List<ScoreCategory> = mode.categories
    private val categoryCount = categories.size

    /** Where the 5x box sits in [categories]. */
    val fiveOfAKindIndex: Int = categories.indexOf(ScoreCategory.FIVE_OF_A_KIND)

    /** Each category's number, if it's in the upper section - zero otherwise. */
    val upperValue: IntArray = IntArray(categoryCount) { index ->
        if (categories[index].section == ScoreSection.UPPER) PlayerState.UPPER_CATEGORIES.indexOf(categories[index]) + 1 else 0
    }

    private val upperIndexForValue = IntArray(UPPER_VALUES + 1) { value ->
        if (value == 0) -1 else categories.indexOf(PlayerState.UPPER_CATEGORIES[value - 1])
    }
    private val freeFill = BooleanArray(categoryCount) { categories[it].jokerFreeFill }
    private val fixedScore = IntArray(categoryCount) { categories[it].fixedScore ?: 0 }
    private val fiveOfAKindScore = categories.getOrNull(fiveOfAKindIndex)?.fixedScore ?: 0

    private val rawScore = IntArray(space.handCount * categoryCount)

    /** The number a hand's five matching dice show, or 0 if they don't all match. */
    private val fiveOfAKindValue = IntArray(space.handCount)

    init {
        for (hand in 0 until space.handCount) {
            val dice = space.diceOf(hand)
            for (category in 0 until categoryCount) rawScore[hand * categoryCount + category] = DiceScoring.score(categories[category], dice)
            if (DiceScoring.isFiveOfAKind(dice)) fiveOfAKindValue[hand] = dice.first().value
        }
    }

    /** The scorecard state of [player], as [forEachLegal] reads it: a bit per full box. */
    fun filledMask(player: PlayerState): Int =
        categories.withIndex().sumOf { (index, category) -> if (!player.isOpen(category)) 1 shl index else 0 }

    /** Whether [player]'s 5x box makes another 5x a joker - see [PlayerState.fiveOfAKindJokerActive]. */
    fun fiveOfAKindScored(player: PlayerState): Boolean = fiveOfAKindIndex >= 0 && player.fiveOfAKindJokerActive

    /** Whether scoring [score] in [category] fills the 5x box with its full score. */
    fun scoresFiveOfAKind(category: Int, score: Int): Boolean = category == fiveOfAKindIndex && score == fiveOfAKindScore

    /**
     * Calls [action] with each category [hand] may legally be scored in, given the filled boxes
     * ([filledMask]) and whether the 5x box holds its full score - with what it scores there and any
     * 5x bonus chip. The same boxes, scores and chips as [ScoreCalculator.availableCategories],
     * [ScoreCalculator.scoreFor] and [ScoreCalculator.fiveOfAKindBonusFor].
     */
    inline fun forEachLegal(hand: Int, filledMask: Int, fiveOfAKindScored: Boolean, action: (category: Int, score: Int, chip: Int) -> Unit) {
        val count = categoryCountValue
        val jokerValue = if (fiveOfAKindScored) fiveOfAKindValueOf(hand) else 0
        if (jokerValue == 0) {
            for (category in 0 until count) if (isOpen(filledMask, category)) action(category, rawScoreOf(hand, category), 0)
            return
        }
        val chip = mode.fiveOfAKindBonusAmount
        // 1. The matching upper box, if it's open - no other choice.
        val forced = upperIndexOf(jokerValue)
        if (forced >= 0 && isOpen(filledMask, forced)) {
            action(forced, rawScoreOf(hand, forced), chip)
            return
        }
        // 2. Otherwise any open box outside the upper section - the free-fill ones at their full value.
        var anyOutsideUpper = false
        for (category in 0 until count) {
            if (isOpen(filledMask, category) && upperValue[category] == 0) {
                anyOutsideUpper = true
                action(category, if (isFreeFill(category)) fixedScoreOf(category) else rawScoreOf(hand, category), chip)
            }
        }
        if (anyOutsideUpper) return
        // 3. Otherwise any open box at all.
        for (category in 0 until count) if (isOpen(filledMask, category)) action(category, rawScoreOf(hand, category), chip)
    }

    @PublishedApi internal val categoryCountValue: Int get() = categoryCount

    @PublishedApi internal fun isOpen(filledMask: Int, category: Int): Boolean = (filledMask shr category) and 1 == 0

    @PublishedApi internal fun rawScoreOf(hand: Int, category: Int): Int = rawScore[hand * categoryCount + category]

    @PublishedApi internal fun fiveOfAKindValueOf(hand: Int): Int = fiveOfAKindValue[hand]

    @PublishedApi internal fun upperIndexOf(value: Int): Int = upperIndexForValue[value]

    @PublishedApi internal fun isFreeFill(category: Int): Boolean = freeFill[category]

    @PublishedApi internal fun fixedScoreOf(category: Int): Int = fixedScore[category]

    private companion object {
        const val UPPER_VALUES = 6
    }
}
