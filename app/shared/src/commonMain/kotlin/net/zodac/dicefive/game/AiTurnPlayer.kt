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
 * - **HARD**: between rolls, evaluates every distinct hold and picks the one with the highest
 *   expected value by the end of the turn, through every reroll left (see [DiceSpace] - an exact
 *   expectation over every possible reroll, not a heuristic). Both that and its category choice value
 *   a finished hand the same way ([HardTurn]): in Standard, by perfect play's value of the rest of
 *   the game ([StandardPerfectPlayTable]); in other modes, by how far a box's score beats its
 *   [CATEGORY_BASELINES] entry - its average when a whole turn chases it - plus its share of the upper
 *   bonus. So a rare, restrictive category (e.g. Full House) can beat a nominally higher-scoring but
 *   easy-to-satisfy-later one (e.g. Chance), and a set of low numbers is worth chasing for 5x.
 *
 * Every rule comes from the player's own [GameMode] - its dice, faces and colours, and which
 * categories are on the card. In a mode with coloured dice, a "face" is a number AND a colour, so
 * Hard's expectation covers both, and Medium's rules of thumb also chase a colour set.
 */
object AiTurnPlayer {

    private val STRAIGHT_WINDOWS = (1..6).toList().windowed(4) + (1..6).toList().windowed(5)

    /**
     * Every category's expected score when a whole turn - every roll the mode allows - is played to
     * fill it and nothing else, per mode: Hard's opportunity-cost baseline, what leaving the box open
     * for a later turn is worth. A single roll's average would undersell the rare boxes - a Large
     * Straight averages about a point on one roll but ten when a turn chases it - and make every
     * straight look like nearly 40 points' profit. Lazy per mode, so a process that only ever plays
     * one mode never pays for another's.
     */
    private val CATEGORY_BASELINES: Map<GameMode, Lazy<DoubleArray>> =
        GameMode.entries.associateWith { mode -> lazy { categoryBaseline(mode) } }

    /**
     * Every hand of each mode's dice, scored in every one of its categories - built on a mode's first
     * Hard decision and kept: Tricolour's 26,334 hands take a moment to score, Standard's 252 none.
     */
    private val HAND_SCORING: Map<GameMode, Lazy<HandScoring>> =
        GameMode.entries.associateWith { mode -> lazy { HandScoring(mode, ROLL_SPACES.getValue(mode).value.hands) } }

    /** Every roll of each mode's dice and the hands they're scored as - see [RollSpace]. Built with [HAND_SCORING]. */
    private val ROLL_SPACES: Map<GameMode, Lazy<RollSpace>> =
        GameMode.entries.associateWith { mode -> lazy { RollSpace(facesOf(mode), mode) } }

    /**
     * Each box scores on its dice's numbers or on their colours, never both, so it's chased over just
     * the faces that matter to it - six numbers, or three colours - which keeps even a coloured mode's
     * whole-turn search to a few thousand hands.
     */
    private fun categoryBaseline(mode: GameMode): DoubleArray {
        val numberSpace = RollSpace(mode.dieValues.map { Die(value = it) }, mode)
        val colourSpace = if (mode.dieColours.isEmpty()) null else RollSpace(mode.dieColours.map { Die(value = mode.dieValues.first, colour = it) }, mode)
        return DoubleArray(mode.categories.size) { index ->
            val category = mode.categories[index]
            val space = if (category.section == ScoreSection.COLOUR) requireNotNull(colourSpace) else numberSpace
            val scores = DoubleArray(space.hands.handCount) { hand -> DiceScoring.score(category, space.hands.diceOf(hand)).toDouble() }
            space.rolls.keepValues(space.rollValues(scores), mode.rollsPerTurn)[space.rolls.emptyKeep]
        }
    }

    /**
     * Which dice indices an AI would hold before its next reroll, given the current (just-rolled) dice.
     * [perfectPlay] is Standard's perfect-play table, which Hard plays by in Standard when it's given
     * (see [StandardPerfectPlayTable]); without it, or in another mode, Hard estimates.
     *
     * Every die means "stop rolling": the turn is scored as it stands. In a mode where only held dice
     * score ([GameMode.scoresHeldDiceOnly]) that's the only way to ask for all of them, since there
     * are fewer hold slots than dice - every other answer there holds no more than the slots allow,
     * and the hand to score is then picked by [chooseHand].
     */
    fun chooseHolds(state: GameState, perfectPlay: StandardPerfectPlayTable? = null): Set<Int> {
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        if (state.gameMode.scoresHeldDiceOnly) return chooseHoldsFromRoll(state, player, perfectPlay)
        return when (player.difficulty) {
            Difficulty.EASY -> chooseHoldsEasy(player, state.dice)
            Difficulty.MEDIUM -> chooseHoldsMedium(player, state.dice)
            Difficulty.HARD -> chooseHoldsHard(player, state.dice, state.rollsRemaining, perfectPlay)
        }
    }

    /**
     * [chooseHolds] where only held dice score: Easy and Medium judge the hand they'd hold now
     * ([chooseHand]) - Easy stopping as soon as it scores at all, Medium on its "good enough" shapes -
     * and otherwise Medium keeps to its rules of thumb, no more dice than there are slots; and Hard
     * searches every hold of the dice it's allowed, as in any mode.
     */
    private fun chooseHoldsFromRoll(state: GameState, player: PlayerState, perfectPlay: StandardPerfectPlayTable?): Set<Int> {
        val dice = state.dice
        val stop = dice.indices.toSet()
        val slots = state.gameMode.scoringDiceCount
        if (player.difficulty == Difficulty.HARD) return chooseHoldsHard(player, dice, state.rollsRemaining, perfectPlay)

        val hand = chooseHand(state, perfectPlay).sorted().map { dice[it] }
        return when (player.difficulty) {
            Difficulty.EASY -> if (hasPossibleScore(player, hand)) stop else emptySet()
            else -> {
                if (shouldStopEarlyMedium(player, hand)) return stop
                val holds = chooseHoldsMedium(player, dice)
                // Every slot's worth of one shape (five matching, say) is as good as Medium gets.
                if (holds.size >= slots) stop else holds
            }
        }
    }

    /**
     * Which dice an AI would score with, in a mode where only held dice score - the
     * [GameMode.scoringDiceCount] of them it holds once it's done rolling. Easy and Medium take the
     * hand that scores the most anywhere open; Hard the one its valuation rates highest. On a tie, the
     * hand that changes fewer of the dice already held.
     */
    fun chooseHand(state: GameState, perfectPlay: StandardPerfectPlayTable? = null): Set<Int> {
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        val dice = state.dice
        val hard = if (player.difficulty == Difficulty.HARD) HardTurn(player, perfectPlay) else null
        var best = emptySet<Int>()
        var bestValue = Double.NEGATIVE_INFINITY
        var bestChanges = Int.MAX_VALUE
        forEachCombination(dice.size, state.gameMode.scoringDiceCount) { indices ->
            val hand = indices.map { dice[it] }
            val value = hard?.bestValue(hand) ?: bestRawScore(player, hand).toDouble()
            val changes = dice.indices.count { dice[it].isHeld != (it in indices) }
            if (value > bestValue || (value == bestValue && changes < bestChanges)) {
                best = indices.toSet()
                bestValue = value
                bestChanges = changes
            }
        }
        return best
    }

    private fun bestRawScore(player: PlayerState, hand: List<Die>): Int =
        ScoreCalculator.availableCategories(player, hand).maxOf { ScoreCalculator.scoreFor(player, it, hand) }

    /** Calls [action] with every way of picking [size] of the indices `0 until [count]`, in ascending order. */
    private inline fun forEachCombination(count: Int, size: Int, action: (List<Int>) -> Unit) {
        val picked = IntArray(size) { it }
        if (size > count) return
        while (true) {
            action(picked.toList())
            var position = size - 1
            while (position >= 0 && picked[position] == count - size + position) position--
            if (position < 0) return
            picked[position]++
            for (next in position + 1 until size) picked[next] = picked[next - 1] + 1
        }
    }

    /**
     * Builds what Hard's decisions in [mode] need - every hand scored, and each box's baseline - ahead
     * of its first one. Kept for the process, so only the first call does anything: with Tricolour's
     * coloured dice that's most of a second on a laptop, so a game with a Hard CPU starts it in the
     * background rather than leave the CPU's first roll to wait on it.
     */
    fun prepareHard(mode: GameMode) {
        ROLL_SPACES.getValue(mode).value
        HAND_SCORING.getValue(mode).value
        CATEGORY_BASELINES.getValue(mode).value
    }

    /**
     * Applies a hold decision (e.g. from [chooseHolds]) to every die that isn't already in the right
     * state - every release first, so a hold slot let go of is free for a die held in its place.
     */
    fun applyHolds(state: GameState, holdIndices: Set<Int>): GameState {
        var current = state
        state.dice.forEachIndexed { index, die ->
            if (die.isHeld && index !in holdIndices) current = GameEngine.toggleHold(current, index)
        }
        state.dice.forEachIndexed { index, die ->
            if (!die.isHeld && index in holdIndices) current = GameEngine.toggleHold(current, index)
        }
        return current
    }

    /** The category an AI would choose for its current (fully-rolled) dice - [perfectPlay] as for [chooseHolds]. */
    /** The category an AI would choose for its current (fully-rolled) hand - [GameState.scoringDice] - [perfectPlay] as for [chooseHolds]. */
    fun chooseCategory(state: GameState, perfectPlay: StandardPerfectPlayTable? = null): ScoreCategory {
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        val hand = state.scoringDice
        val available = ScoreCalculator.availableCategories(player, hand)
        check(available.isNotEmpty()) { "No available categories to score" }
        return when (player.difficulty) {
            Difficulty.EASY -> available.maxBy { ScoreCalculator.scoreFor(player, it, hand) }
            Difficulty.MEDIUM -> chooseCategoryMedium(player, hand, available)
            Difficulty.HARD -> HardTurn(player, perfectPlay).bestCategory(hand)
        }
    }

    /**
     * Pure end-to-end simulation of an AI's whole turn: roll, hold, roll, hold, roll, then - where only
     * held dice score - hold the hand, then score. Used by tests and as a reference for GameViewModel's
     * animated version.
     */
    fun playTurn(state: GameState, random: Random = Random.Default, perfectPlay: StandardPerfectPlayTable? = null): GameState {
        var current = state
        while (current.rollsRemaining > 0) {
            current = GameEngine.rollDice(current, random)
            if (current.rollsRemaining > 0) {
                val holds = chooseHolds(current, perfectPlay)
                if (holds.size == current.dice.size) break
                current = applyHolds(current, holds)
            }
        }
        if (current.gameMode.scoresHeldDiceOnly) current = applyHolds(current, chooseHand(current, perfectPlay))
        return GameEngine.commitScore(current, chooseCategory(current, perfectPlay))
    }

    // ---- Easy: reroll everything until something scores, then take the best of it ----------------

    /**
     * Easy never holds individual dice - it's all of them or none. Once at least one open category
     * would score above zero, there's nothing left to decide, so it holds everything (the same
     * "stop rolling" signal Medium/Hard use) rather than spend a roll it doesn't need; while nothing
     * scores, it holds nothing and lets every die reroll.
     */
    private fun chooseHoldsEasy(player: PlayerState, dice: List<Die>): Set<Int> =
        if (hasPossibleScore(player, dice)) dice.indices.toSet() else emptySet()

    private fun hasPossibleScore(player: PlayerState, dice: List<Die>): Boolean =
        ScoreCalculator.availableCategories(player, dice).any { ScoreCalculator.scoreFor(player, it, dice) > 0 }

    // ---- Medium: rule-of-thumb holds, fixed-priority category tie-break -------------------------

    private fun chooseHoldsMedium(player: PlayerState, dice: List<Die>): Set<Int> {
        if (shouldStopEarlyMedium(player, dice)) return dice.indices.toSet()

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

    // ---- Hard: whole-turn expected-value holds, opportunity-cost category choice ---------------

    private fun chooseHoldsHard(player: PlayerState, dice: List<Die>, rerollsLeft: Int, perfectPlay: StandardPerfectPlayTable?): Set<Int> {
        val turn = HardTurn(player, perfectPlay)
        val space = turn.rolls.rolls
        val keepValues = space.keepValues(turn.rolls.rollValues(turn.endValues()), rerollsLeft)
        val heldMask = dice.withIndex().sumOf { (index, die) -> if (die.isHeld) 1 shl index else 0 }
        var bestMask = 0
        var bestEv = Double.NEGATIVE_INFINITY
        space.forEachSubKeep(space.handOf(dice)) { keep ->
            val mask = diceMaskFor(space, space.keepFaceCounts(keep), dice)
            val ev = keepValues[keep]
            // An exact tie goes to whichever hold changes fewer of the current ones. Two dice showing
            // the same face are never a choice here - a held set is just how many of each face, and
            // [diceMaskFor] takes it from the dice already held first - so a CPU never lets go of the 3
            // it was holding just to pick up another 3 that landed further left.
            val changes = (mask xor heldMask).countOneBits()
            if (ev > bestEv || (ev == bestEv && changes < (bestMask xor heldMask).countOneBits())) {
                bestEv = ev
                bestMask = mask
            }
        }
        return dice.indices.filter { (bestMask shr it) and 1 == 1 }.toSet()
    }

    /** Which of [dice] to hold for a held set of [counts] per face: dice already held first, then the rest, left to right. */
    private fun diceMaskFor(space: DiceSpace, counts: ByteArray, dice: List<Die>): Int {
        var mask = 0
        val wanted = counts.copyOf()
        for (heldFirst in listOf(true, false)) {
            for ((index, die) in dice.withIndex()) {
                if (die.isHeld != heldFirst) continue
                val face = space.faces.indexOf(faceOf(die))
                if (wanted[face] > 0) {
                    wanted[face]--
                    mask = mask or (1 shl index)
                }
            }
        }
        return mask
    }

    /**
     * One Hard decision for [player] as things stand: what every finished hand of this turn is worth,
     * and so which box a hand goes in - the one measure its holds and its category choice both go by,
     * so it never chases a hand it then wouldn't value. Each hand is worth its best legal box (by
     * [HandScoring], the joker rule included), where a box is worth:
     *
     * - in Standard, given [perfectPlay]: the score, any 5x bonus chip and any upper bonus it earns,
     *   plus the table's value of the scorecard it leaves - the rest of the game, played perfectly;
     * - otherwise, an estimate: the score (with any 5x bonus chip) less what a whole turn chasing that
     *   box averages ([CATEGORY_BASELINES] - a box is only worth filling for what it beats its usual
     *   worth by, so a rare one like Full House can beat a nominally higher-scoring but
     *   easy-to-satisfy-later one like Chance, and three 1s heading for 5x aren't written off as a low
     *   total), plus in the upper section the share of the upper bonus the score earns or costs.
     */
    private class HardTurn(player: PlayerState, perfectPlay: StandardPerfectPlayTable?) {
        private val mode = player.gameMode
        private val scoring = HAND_SCORING.getValue(mode).value
        val space: DiceSpace = scoring.space
        val rolls: RollSpace = ROLL_SPACES.getValue(mode).value
        private val filledMask = scoring.filledMask(player)
        private val fiveScored = scoring.fiveOfAKindScored(player)
        private val upperTotal = player.cappedUpperTotal()
        private val table = perfectPlay.takeIf { mode == GameMode.STANDARD }
        private val baseline = CATEGORY_BASELINES.getValue(mode).value

        /**
         * The upper bonus's stake in each point scored in the upper section, for the estimate: it's
         * earned at an average of three of each number (in every slot of each box), so each point above that par (or below it)
         * moves the player that share of the bonus nearer to (or further from) it -
         * [GameMode.upperBonusAmount] over [GameMode.upperBonusThreshold]. Nothing once the bonus is
         * won, or out of reach even with five of every open number.
         */
        private val upperBonusPerPoint: Double = run {
            val total = player.upperSectionTotal
            val bestStillPossible = total + PlayerState.UPPER_CATEGORIES.withIndex()
                .filter { (_, category) -> category in mode.categories }
                .sumOf { (index, category) -> (index + 1) * mode.scoringDiceCount * (mode.scoresPerCategory - player.scoresIn(category).size) }
            if (total >= mode.upperBonusThreshold || bestStillPossible < mode.upperBonusThreshold) {
                0.0
            } else {
                mode.upperBonusAmount.toDouble() / mode.upperBonusThreshold
            }
        }

        private fun boxValue(category: Int, score: Int, chip: Int): Double {
            if (table != null) {
                return StandardPerfectPlayTable.afterScoring(scoring, filledMask, upperTotal, fiveScored, category, score) { mask, upper, five, bonus ->
                    score + chip + bonus + table.valueOf(mask, upper, five)
                }
            }
            val upperValue = scoring.upperValue[category]
            val upperShare = if (upperValue == 0) 0.0 else (score - upperValue * UPPER_PAR_COUNT) * upperBonusPerPoint
            return score + chip - baseline[category] + upperShare
        }

        /** What each finished hand is worth: its best legal box. */
        fun endValues(): DoubleArray = DoubleArray(space.handCount) { hand ->
            var best = Double.NEGATIVE_INFINITY
            scoring.forEachLegal(hand, filledMask, fiveScored) { category, score, chip ->
                val value = boxValue(category, score, chip)
                if (value > best) best = value
            }
            best
        }

        /** What [dice], a whole hand, are worth: their best legal box, as [endValues] rates it. */
        fun bestValue(dice: List<Die>): Double {
            var best = Double.NEGATIVE_INFINITY
            scoring.forEachLegal(space.handOf(dice), filledMask, fiveScored) { category, score, chip ->
                val value = boxValue(category, score, chip)
                if (value > best) best = value
            }
            return best
        }

        /** The legal box [dice] are worth the most in. */
        fun bestCategory(dice: List<Die>): ScoreCategory {
            var best = -1
            var bestValue = Double.NEGATIVE_INFINITY
            scoring.forEachLegal(space.handOf(dice), filledMask, fiveScored) { category, score, chip ->
                val value = boxValue(category, score, chip)
                if (value > bestValue) {
                    bestValue = value
                    best = category
                }
            }
            return scoring.categories[best]
        }
    }

    /** Every face a die can land on in [mode] - a number, plus a colour when the mode has them - each equally likely. */
    private fun facesOf(mode: GameMode): List<Die> =
        mode.dieValues.flatMap { value ->
            if (mode.dieColours.isEmpty()) listOf(Die(value = value)) else mode.dieColours.map { Die(value = value, colour = it) }
        }

    /** A die as just its face - dropping whether it's held, which a reroll's outcome doesn't care about. */
    private fun faceOf(die: Die): Die = Die(value = die.value, colour = die.colour)

    private const val SMALL_STRAIGHT_LENGTH = 4

    /** How many of a number, on average, earn the upper bonus: its threshold is three of each - see [HardTurn]. */
    private const val UPPER_PAR_COUNT = 3

    /** Medium's colour chase: this many dice already sharing a colour is one short of a set - see [colourChaseHoldIndices]. */
    private const val COLOUR_CHASE_GROUP_SIZE = 4

    /** Medium's early-stop threshold: a group this size or bigger, on a die face this high, is worth banking in its upper box. */
    private const val STRONG_UPPER_VALUE = 4
    private const val STRONG_UPPER_GROUP_SIZE = 3
}
