package net.zodac.dicefive.game

import kotlin.math.pow
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection

/**
 * Hard: between rolls, evaluates every distinct hold and picks the one with the highest expected value by the end of
 * the turn, through every reroll left (see [DiceSpace] - an exact expectation over every possible reroll, not a
 * heuristic). Both that and its category choice value a finished hand the same way ([HardTurn]): in Standard, by perfect
 * play's value of the rest of the game ([StandardPerfectPlayTable]); in other modes, by how far a box's score beats its
 * [CATEGORY_BASELINES] entry - its average when a whole turn chases it - plus its share of the upper bonus. So a rare,
 * restrictive category (e.g. Full House) can beat a nominally higher-scoring but easy-to-satisfy-later one (e.g.
 * Chance), and a set of low numbers is worth chasing for 5x.
 *
 * In a mode with coloured dice, a "face" is a number AND a colour, so its expectation covers both.
 */
internal object HardCpu : CpuStrategy {

    /**
     * Every category's expected score when a whole turn - every roll the mode allows - is played to
     * fill it and nothing else, per mode: Hard's opportunity-cost baseline, what leaving the box open
     * for a later turn is worth. A single roll's average would undersell the rare boxes - a Large
     * Straight averages about a point on one roll but ten when a turn chases it - and make every
     * straight look like nearly 40 points' profit. Lazy per mode, so a process that only ever plays
     * one mode never pays for another's.
     */
    private val CATEGORY_BASELINES: Map<Card, Lazy<DoubleArray>> =
        Card.all.associateWith { card -> lazy { categoryBaseline(card) } }

    /**
     * A scorecard's shape: a [mode]'s boxes, with or without the Extended Scores modifier's three. What
     * Hard's per-mode tables are built for, since the extra boxes change every hand's scores.
     */
    private data class Card(val mode: GameMode, val extendedScores: Boolean) {
        val categories: List<ScoreCategory> = mode.categoriesWith(extendedScores)

        companion object {
            val all: List<Card> = GameMode.entries.flatMap { listOf(Card(it, false), Card(it, true)) }

            fun of(player: PlayerState) = Card(player.gameMode, player.extendedScores)
        }
    }

    /**
     * Every hand of each mode's dice, scored in every one of its categories - built on a mode's first
     * Hard decision and kept: Tricolour's 26,334 hands take a moment to score, Standard's 252 none.
     */
    private val HAND_SCORING: Map<Card, Lazy<HandScoring>> =
        Card.all.associateWith { card -> lazy { HandScoring(card.mode, ROLL_SPACES.getValue(card.mode).value.hands, card.categories) } }

    /** Every roll of each mode's dice and the hands they're scored as - see [RollSpace]. Built with [HAND_SCORING]. */
    private val ROLL_SPACES: Map<GameMode, Lazy<RollSpace>> =
        GameMode.entries.associateWith { mode -> lazy { RollSpace(facesOf(mode), mode) } }

    /**
     * Each box scores on its dice's numbers or on their colours, never both, so it's chased over just
     * the faces that matter to it - six numbers, or three colours - which keeps even a coloured mode's
     * whole-turn search to a few thousand hands.
     */
    private fun categoryBaseline(card: Card): DoubleArray {
        val mode = card.mode
        val numberSpace by lazy { RollSpace(mode.dieValues.map { Die(value = it) }, mode) }
        val colourSpace = if (mode.dieColours.isEmpty()) null else RollSpace(mode.dieColours.map { Die(value = mode.dieValues.first, colour = it) }, mode)
        return DoubleArray(card.categories.size) { index ->
            val category = card.categories[index]
            // A number of D20's: the dice showing it, held through every roll - each die has that chance of landing it
            // in one of them. Worked out rather than searched, since a search over twenty faces would take as long as a game.
            if (category.number != null && (category.section == ScoreSection.FIRST_HALF || category.section == ScoreSection.SECOND_HALF)) {
                val faces = mode.dieValues.count().toDouble()
                return@DoubleArray requireNotNull(category.number) * mode.diceCount * (1 - ((faces - 1) / faces).pow(mode.rollsPerTurn))
            }
            val space = if (category.section == ScoreSection.COLOUR) requireNotNull(colourSpace) else numberSpace
            val scores = DoubleArray(space.hands.handCount) { hand -> DiceScoring.score(category, space.hands.diceOf(hand)).toDouble() }
            space.rolls.keepValues(space.rollValues(scores), mode.rollsPerTurn)[space.rolls.emptyKeep]
        }
    }

    /** Builds what decisions in [mode] (with the Extended Scores boxes, if [extendedScores]) need - see [AiTurnPlayer.prepareHard]. */
    fun prepare(mode: GameMode, extendedScores: Boolean) {
        val card = Card(mode, extendedScores)
        ROLL_SPACES.getValue(mode).value
        HAND_SCORING.getValue(card).value
        CATEGORY_BASELINES.getValue(card).value
    }

    /** The most rerolls Hard plans over at once - see [chooseHolds]. */
    private const val MAX_PLANNED_ROLLS = 9

    override fun chooseHolds(state: GameState, player: PlayerState, perfectPlay: StandardPerfectPlayTable?): Set<Int> {
        val dice = state.dice
        val turn = HardTurn(player, perfectPlay)
        val space = turn.rolls.rolls
        // Stored rolls can pile up past what's worth planning for: each extra pass adds a search over every
        // hold and past a handful of rerolls the values have stopped moving.
        val keepValues = space.keepValues(turn.rolls.rollValues(turn.endValues()), state.rollsRemaining.coerceAtMost(MAX_PLANNED_ROLLS))
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

    /** Where only held dice score, it searches every hold of the dice it's allowed, as in any mode. */
    override fun chooseHoldsForHand(state: GameState, player: PlayerState, perfectPlay: StandardPerfectPlayTable?): Set<Int> =
        chooseHolds(state, player, perfectPlay)

    override fun handValuer(player: PlayerState, perfectPlay: StandardPerfectPlayTable?): (List<Die>) -> Double =
        HardTurn(player, perfectPlay)::bestValue

    // Hard values whole hands; one with dice missing (locked by Unlucky Dice) is chosen for as Medium would.
    override fun chooseCategory(
        state: GameState,
        player: PlayerState,
        hand: List<Die>,
        available: List<ScoreCategory>,
        perfectPlay: StandardPerfectPlayTable?,
    ): ScoreCategory =
        if (hand.size < state.gameMode.scoringDiceCount) MediumCpu.chooseCategory(player, hand, available)
        else HardTurn(player, perfectPlay).bestCategory(hand)

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
        private val card = Card.of(player)
        private val scoring = HAND_SCORING.getValue(card).value
        val space: DiceSpace = scoring.space
        val rolls: RollSpace = ROLL_SPACES.getValue(mode).value
        private val filledMask = scoring.filledMask(player)
        private val fiveScored = scoring.fiveOfAKindScored(player)
        private val upperTotal = player.cappedUpperTotal()
        // The bundled table is Standard's own card, so the extra boxes put a game outside it.
        private val table = perfectPlay.takeIf { mode.playsByPerfectPlayTable(player.extendedScores) }
        private val baseline = CATEGORY_BASELINES.getValue(card).value

        /**
         * For each box in a section with a bonus, the bonus's stake in each point scored there, for the estimate:
         * it's earned at an average of so many of each number (in every slot of each box) - three for the upper bonus, about
         * two and a half for each of D20's halves - so each point above that par (or below it) moves the player that
         * share of the bonus nearer to (or further from) it - the bonus's amount over its threshold. Nothing once the
         * bonus is won, or out of reach even with five of every open number. Zero for a box in no such section.
         */
        private val bonusPerPoint = DoubleArray(scoring.categories.size)

        /** For each box in a section with a bonus, the score at par: its number times that section's average count. */
        private val bonusPar = DoubleArray(scoring.categories.size)

        init {
            val boxes = scoring.categories
            fun stake(section: ScoreSection, threshold: Int, amount: Int, par: Double) {
                val indices = boxes.indices.filter { boxes[it].section == section }
                val total = player.sectionTotal(section)
                val bestStillPossible = total + indices.sumOf { index ->
                    val box = boxes[index]
                    if (player.isOpen(box)) requireNotNull(box.countedNumbers).last * mode.scoringDiceCount * (mode.scoresPerCategory - player.scoresIn(box).size) else 0
                }
                if (total >= threshold || bestStillPossible < threshold) return
                for (index in indices) {
                    bonusPerPoint[index] = amount.toDouble() / threshold
                    // A wildcard is on top of the numbers the threshold is an average of, so every point of it is above par.
                    bonusPar[index] = boxes[index].number?.let { it * par } ?: 0.0
                }
            }
            if (player.hasUpperBonus) stake(ScoreSection.UPPER, player.upperBonusThreshold, mode.upperBonusAmount, UPPER_PAR_COUNT.toDouble())
            for (bonus in mode.sectionBonuses) {
                val numbers = boxes.filter { it.section == bonus.section }.sumOf { it.number ?: 0 }
                stake(bonus.section, bonus.threshold, bonus.amount, bonus.threshold.toDouble() / numbers)
            }
        }

        private fun boxValue(category: Int, score: Int, chip: Int): Double {
            if (table != null) {
                return StandardPerfectPlayTable.afterScoring(scoring, filledMask, upperTotal, fiveScored, category, score) { mask, upper, five, bonus ->
                    score + chip + bonus + table.valueOf(mask, upper, five)
                }
            }
            val share = (score - bonusPar[category]) * bonusPerPoint[category]
            return score + chip - baseline[category] + share
        }

        /** What each finished hand is worth: its best legal box. */
        fun endValues(): DoubleArray = DoubleArray(space.handCount) { hand -> bestValueOf(hand) }

        /** What [dice], a whole hand, are worth: their best legal box, as [endValues] rates it. */
        fun bestValue(dice: List<Die>): Double = bestValueOf(space.handOf(dice))

        private fun bestValueOf(hand: Int): Double {
            var best = Double.NEGATIVE_INFINITY
            scoring.forEachLegal(hand, filledMask, fiveScored) { category, score, chip ->
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

    /** How many of a number, on average, earn the upper bonus: its threshold is three of each - see [HardTurn]. */
    private const val UPPER_PAR_COUNT = 3
}
