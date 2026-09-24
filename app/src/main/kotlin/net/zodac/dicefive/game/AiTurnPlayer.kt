package net.zodac.dicefive.game

import kotlin.random.Random
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory

/**
 * AI strategy, split by [Difficulty]:
 *
 * - **EASY**: never holds individual dice, but stops rerolling (all of them, together) as soon as
 *   any open category would score above zero, then greedily scores the highest-value one. If
 *   nothing scores by the third roll it's forced to take a zero somewhere, same as a human would
 *   be. No lookahead at all.
 * - **MEDIUM**: between rolls, holds dice by a simple rule of thumb (keep a forming straight, else
 *   keep the largest matching group). Category choice is still greedy by raw score, but ties break
 *   toward the more restrictive/conditional category (e.g. Four of a Kind over Chance) via a fixed
 *   priority order - a rule of thumb, not the computed opportunity cost Hard uses. It also doesn't
 *   always burn every roll: a hand that's already a fixed set of "good enough" shapes (Full House,
 *   Large Straight, a Small Straight with Large Straight no longer open, or three-plus matching 4s/
 *   5s/6s with that upper box still open) is banked immediately rather than gambled on a reroll -
 *   see [shouldStopEarlyMedium]. Fixed shapes, not a computed expectation, is what keeps this a
 *   notch below Hard.
 * - **HARD**: between rolls, exhaustively evaluates every one of the 32 hold/reroll subsets and
 *   picks the one with the highest expected best-category value on the next roll (see
 *   [expectedBestValue] - a real one-ply expectation over every possible reroll, not a heuristic).
 *   Category choice picks the open category whose score most exceeds [CATEGORY_BASELINE] - its
 *   average value on a single random roll - so a rare, restrictive category (e.g. Full House) can
 *   beat a nominally higher-scoring but easy-to-satisfy-later one (e.g. Chance).
 */
object AiTurnPlayer {

    private val STRAIGHT_WINDOWS = (1..6).toList().windowed(4) + (1..6).toList().windowed(5)

    /** Every category's average score on a single random (no-reroll) 5-dice roll - Hard's opportunity-cost baseline. */
    private val CATEGORY_BASELINE: Map<ScoreCategory, Double> by lazy {
        var outcomeCount = 0
        val totals = ScoreCategory.entries.associateWith { 0.0 }.toMutableMap()
        forEachOutcome(DICE_COUNT) { values ->
            val dice = values.map { Die(value = it) }
            for (category in ScoreCategory.entries) {
                totals[category] = totals.getValue(category) + DiceScoring.score(category, dice)
            }
            outcomeCount++
        }
        totals.mapValues { (_, total) -> total / outcomeCount }
    }

    /** Which dice indices an AI would hold before its next reroll, given the current (just-rolled) dice. */
    fun chooseHolds(state: GameState): Set<Int> {
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        return when (player.difficulty) {
            Difficulty.EASY -> chooseHoldsEasy(player, state.dice)
            Difficulty.MEDIUM -> chooseHoldsMedium(player, state.dice)
            Difficulty.HARD -> chooseHoldsHard(player, state.dice.map { it.value })
        }
    }

    /** Applies a hold decision (e.g. from [chooseHolds]) to every die that isn't already in the right state. */
    fun applyHolds(state: GameState, holdIndices: Set<Int>): GameState {
        var current = state
        state.dice.forEachIndexed { index, die ->
            if (die.isHeld != (index in holdIndices)) current = GameEngine.toggleHold(current, index)
        }
        return current
    }

    /** The category an AI would choose for its current (fully-rolled) dice. */
    fun chooseCategory(state: GameState): ScoreCategory {
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        val available = ScoreCalculator.availableCategories(player, state.dice)
        check(available.isNotEmpty()) { "No available categories to score" }
        return when (player.difficulty) {
            Difficulty.EASY -> available.maxBy { ScoreCalculator.scoreFor(player, it, state.dice) }
            Difficulty.MEDIUM -> chooseCategoryMedium(player, state.dice, available)
            Difficulty.HARD -> chooseCategoryHard(player, state.dice, available)
        }
    }

    /** Pure end-to-end simulation of an AI's whole turn: roll, hold, roll, hold, roll, then score. Used by tests and as a reference for GameViewModel's animated version. */
    fun playTurn(state: GameState, random: Random = Random.Default): GameState {
        var current = state
        while (current.rollsRemaining > 0) {
            current = GameEngine.rollDice(current, random)
            if (current.rollsRemaining > 0) {
                current = applyHolds(current, chooseHolds(current))
            }
        }
        return GameEngine.commitScore(current, chooseCategory(current))
    }

    // ---- Easy: reroll everything until something scores, then take the best of it ----------------

    /**
     * Easy never holds individual dice - it's all of them or none. Once at least one open category
     * would score above zero, there's nothing left to decide, so it holds everything (the same
     * "stop rolling" signal Medium/Hard use) rather than spend a roll it doesn't need; while nothing
     * scores, it holds nothing and lets every die reroll.
     */
    private fun chooseHoldsEasy(player: PlayerState, dice: List<Die>): Set<Int> {
        val available = ScoreCalculator.availableCategories(player, dice)
        val hasPossibleScore = available.any { ScoreCalculator.scoreFor(player, it, dice) > 0 }
        return if (hasPossibleScore) dice.indices.toSet() else emptySet()
    }

    // ---- Medium: rule-of-thumb holds, fixed-priority category tie-break -------------------------

    private fun chooseHoldsMedium(player: PlayerState, dice: List<Die>): Set<Int> {
        if (shouldStopEarlyMedium(player, dice)) return dice.indices.toSet()

        val values = dice.map { it.value }
        val straightHold = bestStraightHoldIndices(values)
        if (straightHold.size >= SMALL_STRAIGHT_LENGTH) return straightHold

        val largestGroupValue = values.withIndex()
            .groupBy { it.value }
            .entries
            .maxWithOrNull(compareBy({ (_, indices) -> indices.size }, { (value, _) -> value }))
            ?.key
        val group = values.withIndex().filter { it.value == largestGroupValue }
        return if (group.size >= 2) group.map { it.index }.toSet() else emptySet()
    }

    /**
     * Whether Medium should hold every die and stop rolling outright, rather than reroll for
     * something better - a fixed set of "good enough" shapes, not a computed expectation (that's
     * Hard's job). Covers both halves of the rule of thumb: a strong lower-section hand already in
     * hand (Full House, Large Straight, or a Small Straight once Large Straight is no longer worth
     * chasing), and a 3-or-4-of-a-kind on a high upper value (4/5/6) that's worth banking in its
     * upper box rather than gambling the rest of the roll for a fifth match.
     */
    private fun shouldStopEarlyMedium(player: PlayerState, dice: List<Die>): Boolean {
        val available = ScoreCalculator.availableCategories(player, dice)
        val counts = dice.map { it.value }.groupingBy { it }.eachCount()

        if (ScoreCategory.LARGE_STRAIGHT in available && DiceScoring.score(ScoreCategory.LARGE_STRAIGHT, dice) > 0) return true
        if (ScoreCategory.FULL_HOUSE in available && DiceScoring.score(ScoreCategory.FULL_HOUSE, dice) > 0) return true
        if (ScoreCategory.SMALL_STRAIGHT in available && ScoreCategory.LARGE_STRAIGHT !in available &&
            DiceScoring.score(ScoreCategory.SMALL_STRAIGHT, dice) > 0
        ) {
            return true
        }

        val strongUpperValue = counts.entries
            .firstOrNull { (value, count) -> value >= STRONG_UPPER_VALUE && count >= STRONG_UPPER_GROUP_SIZE }
            ?.key
        return strongUpperValue != null && UPPER_CATEGORY_FOR_VALUE.getValue(strongUpperValue) in available
    }

    private val UPPER_CATEGORY_FOR_VALUE = mapOf(
        1 to ScoreCategory.ONES,
        2 to ScoreCategory.TWOS,
        3 to ScoreCategory.THREES,
        4 to ScoreCategory.FOURS,
        5 to ScoreCategory.FIVES,
        6 to ScoreCategory.SIXES,
    )

    /** One die index per distinct value in the longest run (4- or 5-length) present in [values], preferring the longer/higher run. */
    private fun bestStraightHoldIndices(values: List<Int>): Set<Int> {
        val distinct = values.toSet()
        val window = STRAIGHT_WINDOWS
            .filter { it.all { value -> value in distinct } }
            .maxWithOrNull(compareBy({ it.size }, { it.first() }))
            ?: return emptySet()
        return window.map { target -> values.indexOfFirst { it == target } }.toSet()
    }

    private fun chooseCategoryMedium(player: PlayerState, dice: List<Die>, available: List<ScoreCategory>): ScoreCategory =
        available.maxWith(
            compareBy(
                { ScoreCalculator.scoreFor(player, it, dice) },
                { -CATEGORY_RESTRICTIVENESS.getValue(it) },
            ),
        )

    /**
     * Fixed rarest-first priority order used only to break a raw-score tie (most often
     * THREE_OF_A_KIND/FOUR_OF_A_KIND/CHANCE, which all score the same sum-of-dice when they
     * qualify): prefer spending a roll that happens to satisfy a hard-to-get category now, saving
     * the always-available ones (an upper box, CHANCE) for a worse future roll.
     */
    private val CATEGORY_RESTRICTIVENESS: Map<ScoreCategory, Int> = listOf(
        ScoreCategory.FIVE_OF_A_KIND,
        ScoreCategory.LARGE_STRAIGHT,
        ScoreCategory.SMALL_STRAIGHT,
        ScoreCategory.FULL_HOUSE,
        ScoreCategory.FOUR_OF_A_KIND,
        ScoreCategory.THREE_OF_A_KIND,
        ScoreCategory.SIXES,
        ScoreCategory.FIVES,
        ScoreCategory.FOURS,
        ScoreCategory.THREES,
        ScoreCategory.TWOS,
        ScoreCategory.ONES,
        ScoreCategory.CHANCE,
    ).withIndex().associate { (index, category) -> category to index }

    // ---- Hard: exhaustive expected-value holds, opportunity-cost category choice ---------------

    private fun chooseHoldsHard(player: PlayerState, values: List<Int>): Set<Int> {
        var bestMask = 0
        var bestEv = Double.NEGATIVE_INFINITY
        for (mask in 0 until (1 shl DICE_COUNT)) {
            val heldValues = values.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }
            val ev = expectedBestValue(player, heldValues, DICE_COUNT - heldValues.size)
            if (ev > bestEv) {
                bestEv = ev
                bestMask = mask
            }
        }
        return (0 until DICE_COUNT).filter { (bestMask shr it) and 1 == 1 }.toSet()
    }

    /** Exact expected value of the best open category, averaged over every possible outcome of rerolling [freeCount] dice alongside [heldValues]. */
    private fun expectedBestValue(player: PlayerState, heldValues: List<Int>, freeCount: Int): Double {
        var total = 0.0
        var outcomes = 0
        forEachOutcome(freeCount) { rolled ->
            val dice = (heldValues + rolled).map { Die(value = it) }
            val available = ScoreCalculator.availableCategories(player, dice)
            total += available.maxOf { scoreWithBonus(player, it, dice) }
            outcomes++
        }
        return total / outcomes
    }

    private fun scoreWithBonus(player: PlayerState, category: ScoreCategory, dice: List<Die>): Int {
        val bonus = if (ScoreCalculator.awardsFiveOfAKindBonus(player, dice)) PlayerState.FIVE_OF_A_KIND_BONUS_AMOUNT else 0
        return ScoreCalculator.scoreFor(player, category, dice) + bonus
    }

    /** Open category whose score most exceeds its own [CATEGORY_BASELINE] - the biggest "surplus" over what it's typically worth. */
    private fun chooseCategoryHard(player: PlayerState, dice: List<Die>, available: List<ScoreCategory>): ScoreCategory =
        available.maxBy { ScoreCalculator.scoreFor(player, it, dice) - CATEGORY_BASELINE.getValue(it) }

    /** Invokes [action] once per possible outcome of rolling [count] dice (order-independent, so duplicates recur - each is equally likely). */
    private fun forEachOutcome(count: Int, action: (List<Int>) -> Unit) {
        val current = IntArray(count) { 1 }
        while (true) {
            action(current.toList())
            var index = count - 1
            while (index >= 0 && current[index] == DIE_FACES) {
                current[index] = 1
                index--
            }
            if (index < 0) break
            current[index]++
        }
    }

    private const val DICE_COUNT = 5
    private const val DIE_FACES = 6
    private const val SMALL_STRAIGHT_LENGTH = 4

    /** Medium's early-stop threshold: a group this size or bigger, on a die face this high, is worth banking in its upper box. */
    private const val STRONG_UPPER_VALUE = 4
    private const val STRONG_UPPER_GROUP_SIZE = 3
}
