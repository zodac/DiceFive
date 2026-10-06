package net.zodac.dicefive.game

import kotlin.math.pow
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.HitTarget
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory

/**
 * How a CPU plays a Hit List card (see `GameMode.HIT_LIST`), which [AiTurnPlayer]'s own rules can't: its targets are
 * drawn per game, and an exact hit depends on which column each die is in, where everything else it plays only cares
 * what the dice show.
 *
 * - **EASY**: rolls everything until something is hit (a partial hit isn't enough - nearly every roll has one), then
 *   takes the most points.
 * - **MEDIUM**: stops on any hit; otherwise holds towards the open target most worth chasing - its points times the
 *   odds of hitting it with the rolls left ([hitOdds]) - in any order. Scores the most points, a target before the
 *   Alibi on a tie, and with nothing hit crosses off the box least worth keeping ([worthKeeping]). A plain hit on a
 *   target naming all five numbers goes in the Alibi while it's open, to try again for the double.
 * - **HARD**: weighs both ways of chasing every open target - any order ([hitOdds]), or each number in its place for
 *   double ([exactOdds]), either one falling back on the partial hit its held dice make - against stopping with what
 *   it has. Picks the box whose score most beats what keeping it
 *   for later is worth, so it will put a hit in the Alibi to try its target again for an exact hit.
 *
 * Every rule here reads the player's own card and the dice as they lie, in column order.
 */
internal object HitListPlay {

    /** The most rerolls planned over, as for [AiTurnPlayer]'s Hard - stored rolls can pile up past what changes anything. */
    private const val MAX_PLANNED_ROLLS = 9

    private const val DIE_FACES = 6

    /** The chance a die misses a number it's rolled for. */
    private const val MISS_ODDS = 5.0 / 6.0

    /** Which of [dice] to hold before the next roll (every one means "stop rolling") - see the class doc. */
    fun chooseHolds(player: PlayerState, dice: List<Die>, rollsRemaining: Int): Set<Int> {
        val stop = dice.indices.toSet()
        // A real hit, not a partial one - almost every roll scores a little somewhere.
        val anyHit = player.hitList.any { (category, target) -> player.isOpen(category) && target.isHit(dice) }
        if (player.difficulty == Difficulty.EASY) return if (anyHit) stop else emptySet()
        if (player.difficulty == Difficulty.MEDIUM && anyHit) return stop

        val rolls = rollsRemaining.coerceAtMost(MAX_PLANNED_ROLLS)
        val exactAllowed = player.difficulty == Difficulty.HARD
        var bestValue = if (exactAllowed) bestScore(player, dice).toDouble() else 0.0
        var bestHolds = if (exactAllowed) stop else emptySet()
        for ((category, target) in player.hitList) {
            if (!player.isOpen(category)) continue
            val anyOrder = holdsForHit(target, dice)
            val anyOdds = hitOdds(neededAfter(target, dice, anyOrder), rolls, extraDice = dice.size - target.called.size)
            // A chase that misses still keeps the partial hit its held dice already make.
            val anyValue = target.points * anyOdds + (1 - anyOdds) * target.partialPoints(anyOrder.map { dice[it] })
            if (anyValue > bestValue) {
                bestValue = anyValue
                bestHolds = anyOrder
            }
            if (!exactAllowed || dice.size != target.places.size) continue
            val inPlace = holdsInPlace(target, dice)
            val exactChance = exactOdds(target.called.size - inPlace.size, rolls)
            val exactValue = target.exactPoints * exactChance + (1 - exactChance) * target.partialPoints(inPlace.map { dice[it] })
            if (exactValue > bestValue) {
                bestValue = exactValue
                bestHolds = inPlace
            }
        }
        return bestHolds
    }

    /** The box an AI scores [dice] in - see the class doc. */
    fun chooseCategory(player: PlayerState, dice: List<Die>, available: List<ScoreCategory>, rollsPerTurn: Int): ScoreCategory =
        when (player.difficulty) {
            Difficulty.EASY -> available.maxBy { ScoreCalculator.scoreFor(player, it, dice) }
            Difficulty.MEDIUM -> {
                val best = available.maxWith(
                    compareBy(
                        { ScoreCalculator.scoreFor(player, it, dice) },
                        // A target over the Alibi on equal points, and of nothing hit, the box least worth keeping goes.
                        { if (it == ScoreCategory.ALIBI) 0 else 1 },
                        { -worthKeeping(player, it, rollsPerTurn) },
                    ),
                )
                // A plain hit on a target naming all five numbers goes in the Alibi, keeping the target for its double.
                val target = player.hitList[best]
                val plainFullHit = target != null && target.called.size == target.places.size && ScoreCalculator.scoreFor(player, best, dice) == target.points
                if (plainFullHit && ScoreCategory.ALIBI in available) ScoreCategory.ALIBI else best
            }
            Difficulty.HARD -> available.maxBy { ScoreCalculator.scoreFor(player, it, dice) - worthKeeping(player, it, rollsPerTurn) }
        }

    /**
     * What leaving [category] open for a later turn is worth: a whole turn's chance of hitting its target, plus of an
     * exact hit for the extra points - or, for the Alibi, the best of that among the open targets, since it takes the
     * points of whichever one it stands in for.
     */
    private fun worthKeeping(player: PlayerState, category: ScoreCategory, rollsPerTurn: Int): Double {
        val rolls = rollsPerTurn.coerceAtMost(MAX_PLANNED_ROLLS)
        fun turnValue(target: HitTarget): Double {
            val extra = target.places.size - target.called.size
            return target.points * (hitOdds(target.called.sorted(), rolls, extra) + exactOdds(target.called.size, rolls))
        }
        if (category == ScoreCategory.ALIBI) {
            return player.hitList.filter { (it, _) -> player.isOpen(it) }.values.maxOfOrNull { it.points * hitOdds(it.called.sorted(), rolls, it.places.size - it.called.size) } ?: 0.0
        }
        return turnValue(player.targetOf(category))
    }

    private fun bestScore(player: PlayerState, dice: List<Die>): Int =
        player.categories.filter { player.isOpen(it) }.maxOfOrNull { ScoreCalculator.scoreFor(player, it, dice) } ?: 0

    /**
     * The dice to hold towards hitting [target] in any order: as many of its numbers as the dice show, each die once -
     * one already in its own place first, so a hit chased this way keeps every chance of being exact too.
     */
    fun holdsForHit(target: HitTarget, dice: List<Die>): Set<Int> {
        val wanted = target.called.toMutableList()
        val held = mutableSetOf<Int>()
        if (dice.size == target.places.size) {
            for (index in dice.indices) {
                val place = target.places[index]
                if (place != null && dice[index].value == place && wanted.remove(place)) held += index
            }
        }
        for (index in dice.indices) {
            if (index !in held && wanted.remove(dice[index].value)) held += index
        }
        return held
    }

    /** The dice already showing their place's number in [target] - what's held to chase an exact hit. */
    private fun holdsInPlace(target: HitTarget, dice: List<Die>): Set<Int> =
        dice.indices.filter { target.places[it] != null && dice[it].value == target.places[it] }.toSet()

    /** [target]'s numbers still to roll once [held] are kept, sorted. */
    private fun neededAfter(target: HitTarget, dice: List<Die>, held: Set<Int>): List<Int> {
        val needed = target.called.toMutableList()
        for (index in held) needed.remove(dice[index].value)
        return needed.sorted()
    }

    /**
     * The chance of rolling every number of [needed] within [rolls] rolls, rerolling those dice and [extraDice] more
     * that nothing needs, and holding each match as it comes - the best that can be done for one target. Zero when
     * there are fewer dice than numbers to find.
     */
    fun hitOdds(needed: List<Int>, rolls: Int, extraDice: Int): Double {
        if (needed.isEmpty()) return 1.0
        if (rolls <= 0 || extraDice < 0) return 0.0
        val key = HitOddsKey(needed, rolls, extraDice)
        HIT_ODDS[key]?.let { return it }
        var odds = 0.0
        for ((counts, chance) in rollOutcomes(needed.size + extraDice)) {
            val left = needed.toMutableList()
            for (face in 1..DIE_FACES) repeat(counts[face - 1]) { left.remove(face) }
            odds += chance * hitOdds(left, rolls - 1, extraDice)
        }
        HIT_ODDS[key] = odds
        return odds
    }

    /** The chance [places] dice each land their own place's number within [rolls] rolls, each held once it does. */
    fun exactOdds(places: Int, rolls: Int): Double = (1 - MISS_ODDS.pow(rolls)).pow(places)

    private data class HitOddsKey(val needed: List<Int>, val rolls: Int, val extraDice: Int)

    private val HIT_ODDS = mutableMapOf<HitOddsKey, Double>()

    /** Every way [dice] dice can land, as a count per face, with its chance - so a roll's outcomes are a few hundred, not 6^5. */
    private fun rollOutcomes(dice: Int): List<Pair<IntArray, Double>> = ROLL_OUTCOMES.getOrPut(dice) {
        val outcomes = mutableListOf<Pair<IntArray, Double>>()
        val counts = IntArray(DIE_FACES)
        val total = DIE_FACES.toDouble().pow(dice)
        fun fill(face: Int, left: Int) {
            if (face == DIE_FACES - 1) {
                counts[face] = left
                val ways = factorial(dice) / counts.fold(1.0) { product, count -> product * factorial(count) }
                outcomes += counts.copyOf() to ways / total
                return
            }
            for (count in 0..left) {
                counts[face] = count
                fill(face + 1, left - count)
            }
        }
        fill(0, dice)
        outcomes
    }

    private val ROLL_OUTCOMES = mutableMapOf<Int, List<Pair<IntArray, Double>>>()

    private fun factorial(n: Int): Double = (1..n).fold(1.0) { product, value -> product * value }
}
