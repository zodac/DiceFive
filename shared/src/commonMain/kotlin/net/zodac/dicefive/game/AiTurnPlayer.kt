package net.zodac.dicefive.game

import kotlin.random.Random
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection

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
 *   Category choice picks the open category whose score most exceeds its [CATEGORY_BASELINES] entry - its
 *   average value on a single random roll - so a rare, restrictive category (e.g. Full House) can
 *   beat a nominally higher-scoring but easy-to-satisfy-later one (e.g. Chance).
 *
 * Every rule comes from the player's own [GameMode] - its dice, faces and colours, and which
 * categories are on the card. In a mode with coloured dice, a "face" is a number AND a colour, so
 * Hard's expectation covers both, and Medium's rules of thumb also chase a colour set.
 */
object AiTurnPlayer {

    private val STRAIGHT_WINDOWS = (1..6).toList().windowed(4) + (1..6).toList().windowed(5)

    /**
     * Every category's average score on a single random (no-reroll) roll, per mode - Hard's
     * opportunity-cost baseline. Lazy per mode, so a process that only ever plays one mode never
     * pays for another's.
     */
    private val CATEGORY_BASELINES: Map<GameMode, Lazy<Map<ScoreCategory, Double>>> =
        GameMode.entries.associateWith { mode -> lazy { categoryBaseline(mode) } }

    private fun categoryBaseline(mode: GameMode): Map<ScoreCategory, Double> {
        var totalWeight = 0L
        val totals = mode.categories.associateWith { 0.0 }.toMutableMap()
        forEachOutcome(facesOf(mode), mode.diceCount) { dice, weight ->
            for (category in mode.categories) {
                totals[category] = totals.getValue(category) + weight * DiceScoring.score(category, dice)
            }
            totalWeight += weight
        }
        return totals.mapValues { (_, total) -> total / totalWeight }
    }

    /** Which dice indices an AI would hold before its next reroll, given the current (just-rolled) dice. */
    fun chooseHolds(state: GameState): Set<Int> {
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        return when (player.difficulty) {
            Difficulty.EASY -> chooseHoldsEasy(player, state.dice)
            Difficulty.MEDIUM -> chooseHoldsMedium(player, state.dice)
            Difficulty.HARD -> chooseHoldsHard(player, state.dice)
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
     * Whether Medium should hold every die and stop rolling outright, rather than reroll for
     * something better - a fixed set of "good enough" shapes, not a computed expectation (that's
     * Hard's job). Covers both halves of the rule of thumb: a strong lower-section hand already in
     * hand (Full House, Large Straight, or a Small Straight once Large Straight is no longer worth
     * chasing), and a 3-or-4-of-a-kind on a high upper value (4/5/6) that's worth banking in its
     * upper box rather than gambling the rest of the roll for a fifth match. Any open colour box the
     * dice already fill (a colour set, or a coloured house) is banked the same way.
     */
    private fun shouldStopEarlyMedium(player: PlayerState, dice: List<Die>): Boolean {
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
        ScoreCategory.REDS,
        ScoreCategory.YELLOWS,
        ScoreCategory.BLUES,
        ScoreCategory.LARGE_STRAIGHT,
        ScoreCategory.SMALL_STRAIGHT,
        ScoreCategory.FULL_HOUSE,
        ScoreCategory.COLOURED_HOUSE,
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

    private fun chooseHoldsHard(player: PlayerState, dice: List<Die>): Set<Int> {
        val faces = facesOf(player.gameMode)
        // Holding different dice that show the same faces (two 3s, say) is the same decision, so
        // each distinct held set is only ever evaluated once.
        val evByHeldFaces = mutableMapOf<List<Die>, Double>()
        var bestMask = 0
        var bestEv = Double.NEGATIVE_INFINITY
        for (mask in 0 until (1 shl dice.size)) {
            val heldDice = dice.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }.map(::faceOf)
            val key = heldDice.sortedWith(FACE_ORDER)
            val ev = evByHeldFaces.getOrPut(key) { expectedBestValue(player, faces, heldDice, dice.size - heldDice.size) }
            if (ev > bestEv) {
                bestEv = ev
                bestMask = mask
            }
        }
        return dice.indices.filter { (bestMask shr it) and 1 == 1 }.toSet()
    }

    /** Exact expected value of the best open category, averaged over every possible outcome of rerolling [freeCount] dice alongside [heldDice]. */
    private fun expectedBestValue(player: PlayerState, faces: List<Die>, heldDice: List<Die>, freeCount: Int): Double {
        var total = 0.0
        var totalWeight = 0L
        forEachOutcome(faces, freeCount) { rolled, weight ->
            val dice = heldDice + rolled
            val available = ScoreCalculator.availableCategories(player, dice)
            total += weight * available.maxOf { scoreWithBonus(player, it, dice) }
            totalWeight += weight
        }
        return total / totalWeight
    }

    private fun scoreWithBonus(player: PlayerState, category: ScoreCategory, dice: List<Die>): Int =
        ScoreCalculator.scoreFor(player, category, dice) + ScoreCalculator.fiveOfAKindBonusFor(player, dice)

    /** Open category whose score most exceeds its own baseline (see [CATEGORY_BASELINES]) - the biggest "surplus" over what it's typically worth. */
    private fun chooseCategoryHard(player: PlayerState, dice: List<Die>, available: List<ScoreCategory>): ScoreCategory {
        val baseline = CATEGORY_BASELINES.getValue(player.gameMode).value
        return available.maxBy { ScoreCalculator.scoreFor(player, it, dice) - baseline.getValue(it) }
    }

    /** Every face a die can land on in [mode] - a number, plus a colour when the mode has them - each equally likely. */
    private fun facesOf(mode: GameMode): List<Die> =
        mode.dieValues.flatMap { value ->
            if (mode.dieColours.isEmpty()) listOf(Die(value = value)) else mode.dieColours.map { Die(value = value, colour = it) }
        }

    /** A die as just its face - dropping whether it's held, which a reroll's outcome doesn't care about. */
    private fun faceOf(die: Die): Die = Die(value = die.value, colour = die.colour)

    private val FACE_ORDER = compareBy<Die>({ it.value }, { it.colour?.ordinal ?: -1 })

    /**
     * Invokes [action] once per distinct outcome of rolling [count] dice that each land on one of
     * [faces] - as an unordered set of faces, never the same set twice - along with how many of the
     * equally likely *ordered* rolls produce it (the multinomial coefficient). An exact expectation
     * is then `sum(weight * value) / sum(weight)`.
     *
     * Unordered rather than every ordered roll because scoring never depends on die order, and the
     * ordered count is what coloured dice make unaffordable: five dice with 18 faces each is 1.9
     * million ordered rolls, but only 26,334 distinct ones.
     */
    private fun forEachOutcome(faces: List<Die>, count: Int, action: (List<Die>, Long) -> Unit) {
        val indices = IntArray(count)
        while (true) {
            val multiplicities = indices.toList().groupingBy { it }.eachCount().values
            val weight = factorial(count) / multiplicities.fold(1L) { product, n -> product * factorial(n) }
            action(indices.map { faces[it] }, weight)

            // Next non-decreasing sequence of face indices: bump the rightmost one that can still
            // go up, and reset everything after it to match, so no set of faces is ever repeated.
            var position = count - 1
            while (position >= 0 && indices[position] == faces.size - 1) position--
            if (position < 0) break
            indices[position]++
            for (following in position + 1 until count) indices[following] = indices[position]
        }
    }

    private fun factorial(n: Int): Long = (2..n).fold(1L) { product, factor -> product * factor }

    private const val SMALL_STRAIGHT_LENGTH = 4

    /** Medium's colour chase: this many dice already sharing a colour is one short of a set - see [colourChaseHoldIndices]. */
    private const val COLOUR_CHASE_GROUP_SIZE = 4

    /** Medium's early-stop threshold: a group this size or bigger, on a die face this high, is worth banking in its upper box. */
    private const val STRONG_UPPER_VALUE = 4
    private const val STRONG_UPPER_GROUP_SIZE = 3
}
