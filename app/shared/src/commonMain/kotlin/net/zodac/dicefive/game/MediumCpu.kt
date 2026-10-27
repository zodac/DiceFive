package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection

/**
 * Medium: between rolls, holds dice by a simple rule of thumb (keep a forming straight, else keep the largest matching
 * group). Category choice is still greedy by raw score, but ties break toward the more restrictive/conditional category
 * (e.g. Four of a Kind over Chance) via a fixed priority order - a rule of thumb, not the computed opportunity cost
 * Hard uses. It also doesn't always burn every roll: a hand that's already a fixed set of "good enough" shapes (Full
 * House, Large Straight, a Small Straight with Large Straight no longer open, or three-plus matching 4s/5s/6s with that
 * upper box still open) is banked immediately rather than gambled on a reroll - see [shouldStopEarly]. Fixed shapes,
 * not a computed expectation, is what keeps this a notch below Hard.
 */
internal object MediumCpu : CpuStrategy {

    private val STRAIGHT_WINDOWS = (1..6).toList().windowed(4) + (1..6).toList().windowed(5)

    override fun chooseHolds(state: GameState, player: PlayerState, perfectPlay: StandardPerfectPlayTable?): Set<Int> =
        holdsFor(player, state.dice)

    /**
     * Judges the hand it would hold now ([AiTurnPlayer.chooseHand]) on its "good enough" shapes, and otherwise keeps to
     * its rules of thumb, no more dice than there are slots.
     */
    override fun chooseHoldsForHand(state: GameState, player: PlayerState, perfectPlay: StandardPerfectPlayTable?): Set<Int> {
        val dice = state.dice
        val stop = dice.indices.toSet()
        val hand = AiTurnPlayer.chooseHand(state, perfectPlay).sorted().map { dice[it] }
        if (shouldStopEarly(player, hand)) return stop
        val holds = holdsFor(player, dice)
        // Every slot's worth of one shape (five matching, say) is as good as Medium gets.
        return if (holds.size >= state.gameMode.scoringDiceCount) stop else holds
    }

    override fun chooseCategory(
        state: GameState,
        player: PlayerState,
        hand: List<Die>,
        available: List<ScoreCategory>,
        perfectPlay: StandardPerfectPlayTable?,
    ): ScoreCategory = chooseCategory(player, hand, available)

    /** The highest-scoring of [available] for [dice], the more restrictive one on a tie ([CATEGORY_RESTRICTIVENESS]). */
    fun chooseCategory(player: PlayerState, dice: List<Die>, available: List<ScoreCategory>): ScoreCategory =
        available.maxWith(
            compareBy(
                { ScoreCalculator.scoreFor(player, it, dice) },
                { -CATEGORY_RESTRICTIVENESS.getValue(it) },
            ),
        )

    private fun holdsFor(player: PlayerState, dice: List<Die>): Set<Int> {
        // Nothing but numbers: holding what's worth keeping costs no roll, so there is no hand to stop and bank early.
        if (player.categories.all { it.countedNumbers != null }) return numberHoldIndices(player, dice)
        if (shouldStopEarly(player, dice)) return dice.indices.toSet()

        val values = dice.map { it.value }

        val straightHold = bestStraightHoldIndices(dice)
        if (straightHold.size >= SMALL_STRAIGHT_LENGTH) return straightHold

        val colourHold = colourChaseHoldIndices(player, dice)
        if (colourHold.isNotEmpty()) return colourHold

        val largestGroupValue = values.withIndex()
            .groupBy { it.value }
            .entries
            .maxWithOrNull(compareBy({ (_, indices) -> indices.size }, { (value, _) -> value }))
            ?.key
        val group = values.withIndex().filter { it.value == largestGroupValue }
        return if (group.size >= 2) group.map { it.index }.toSet() else emptySet()
    }

    /**
     * Medium's holds on a card of nothing but numbers (D20's): the dice showing the open number worth the most as they
     * stand - its value times how many of it - provided that's more than a fresh die is likely to be worth anyway, and
     * otherwise nothing, to roll them all again. The other rules of thumb (straights, colours, the largest group) are for
     * boxes this card hasn't got.
     */
    private fun numberHoldIndices(player: PlayerState, dice: List<Die>): Set<Int> {
        val open = ScoreCalculator.availableCategories(player, dice).flatMap { category -> category.countedNumbers?.toList().orEmpty() }.toSet()
        val best = dice.withIndex().filter { it.value.value in open }.groupBy { it.value.value }
            .maxByOrNull { (value, group) -> value * group.size }
            ?: return emptySet()
        val faceMax = player.gameMode.dieValues.last
        return if (best.key * best.value.size > faceMax / 2) best.value.map { it.index }.toSet() else emptySet()
    }

    /**
     * Whether Medium should hold every die and stop rolling outright, rather than reroll for
     * something better - a fixed set of "good enough" shapes, not a computed expectation (that's
     * Hard's job). Covers both halves of the rule of thumb: a strong lower-section hand already in
     * hand (Full House, Large Straight, or a Small Straight once Large Straight is no longer worth
     * chasing), and a 3-or-4-of-a-kind on a high upper value (4/5/6) that's worth banking in its
     * upper box rather than gambling the rest of the roll for a fifth match. Any open colour box the
     * dice already fill (a colour set, or a coloured house) is banked the same way.
     */
    private fun shouldStopEarly(player: PlayerState, dice: List<Die>): Boolean {
        val available = ScoreCalculator.availableCategories(player, dice)
        val counts = dice.map { it.value }.groupingBy { it }.eachCount()

        if (available.any { it.section == ScoreSection.COLOUR && DiceScoring.score(it, dice) > 0 }) return true

        if (ScoreCategory.LARGE_STRAIGHT in available && DiceScoring.score(ScoreCategory.LARGE_STRAIGHT, dice) > 0) return true
        if (ScoreCategory.FULL_HOUSE in available && DiceScoring.score(ScoreCategory.FULL_HOUSE, dice) > 0) return true
        if (ScoreCategory.SMALL_STRAIGHT in available && ScoreCategory.LARGE_STRAIGHT !in available &&
            DiceScoring.score(ScoreCategory.SMALL_STRAIGHT, dice) > 0
        ) {
            return true
        }

        // The top third of the faces: 4, 5 and 6 on a six-sided die.
        val faceMax = player.gameMode.dieValues.last
        val strongValue = (faceMax * 2 + 2) / 3
        val strongUpperValue = counts.entries
            .firstOrNull { (value, count) -> value >= strongValue && count >= STRONG_UPPER_GROUP_SIZE }
            ?.key
        return strongUpperValue != null && available.any { it.countedNumbers?.contains(strongUpperValue) == true }
    }

    /**
     * The dice of the largest single-colour group, when it's [COLOUR_CHASE_GROUP_SIZE] or more strong
     * and that colour's box is still open - one die short of a 40-point set is worth rerolling the
     * odd one out for. Empty otherwise, including in a mode whose dice have no colour.
     */
    private fun colourChaseHoldIndices(player: PlayerState, dice: List<Die>): Set<Int> {
        val openColourBoxes = ScoreCalculator.availableCategories(player, dice).mapNotNull { it.matchingColour }
        val (colour, group) = dice.withIndex()
            .filter { it.value.colour != null }
            .groupBy { it.value.colour }
            .maxByOrNull { (_, group) -> group.size }
            ?: return emptySet()
        return if (group.size >= COLOUR_CHASE_GROUP_SIZE && colour in openColourBoxes) group.map { it.index }.toSet() else emptySet()
    }

    /**
     * One die index per distinct value in the longest run (4- or 5-length) present in [dice],
     * preferring the longer/higher run. Where a value shows on more than one die, a die already held
     * is kept over swapping it for an identical one.
     */
    private fun bestStraightHoldIndices(dice: List<Die>): Set<Int> {
        val distinct = dice.map { it.value }.toSet()
        val window = STRAIGHT_WINDOWS
            .filter { it.all { value -> value in distinct } }
            .maxWithOrNull(compareBy({ it.size }, { it.first() }))
            ?: return emptySet()
        return window.map { target ->
            dice.indices.filter { dice[it].value == target }.let { matches -> matches.firstOrNull { dice[it].isHeld } ?: matches.first() }
        }.toSet()
    }

    /**
     * Fixed rarest-first priority order used only to break a raw-score tie (most often
     * THREE_OF_A_KIND/FOUR_OF_A_KIND/CHANCE, which all score the same sum-of-dice when they
     * qualify): prefer spending a roll that happens to satisfy a hard-to-get category now, saving
     * the always-available ones (an upper box, CHANCE) for a worse future roll.
     */
    private val CATEGORY_RESTRICTIVENESS: Map<ScoreCategory, Int> = listOf(
        ScoreCategory.FIVE_OF_A_KIND,
        ScoreCategory.REDS,
        ScoreCategory.YELLOWS,
        ScoreCategory.BLUES,
        ScoreCategory.LARGE_STRAIGHT,
        ScoreCategory.SMALL_STRAIGHT,
        ScoreCategory.FULL_HOUSE,
        ScoreCategory.COLOURED_HOUSE,
        ScoreCategory.FOUR_OF_A_KIND,
        ScoreCategory.THREE_OF_A_KIND,
        ScoreCategory.TWO_PAIR,
        ScoreCategory.SIXES,
        ScoreCategory.FIVES,
        ScoreCategory.FOURS,
        ScoreCategory.THREES,
        ScoreCategory.TWOS,
        ScoreCategory.ONES,
        ScoreCategory.EVENS,
        ScoreCategory.ODDS,
        ScoreCategory.CHANCE,
    ).plus(ScoreCategory.D20_NUMBERS.reversed()).plus(listOf(ScoreCategory.D20_WILD_LOW, ScoreCategory.D20_WILD_HIGH)).withIndex().associate { (index, category) -> category to index }

    private const val SMALL_STRAIGHT_LENGTH = 4

    /** Medium's colour chase: this many dice already sharing a colour is one short of a set - see [colourChaseHoldIndices]. */
    private const val COLOUR_CHASE_GROUP_SIZE = 4

    /** Medium's early-stop threshold: a group this size or bigger, on a die face in the top third, is worth banking in its box. */
    private const val STRONG_UPPER_GROUP_SIZE = 3
}
