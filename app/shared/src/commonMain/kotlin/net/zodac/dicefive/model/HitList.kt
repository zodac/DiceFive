package net.zodac.dicefive.model

import kotlin.random.Random

/**
 * The shape of a Hit List target, before its numbers are drawn: [pattern] has a letter per place, left to
 * right, where the same letter is the same number and a different letter a different one, and a [ANY] place
 * matches any die. "aab··" becomes, say, `4 · 4 · 1` once drawn and shuffled. [points] is what hitting it
 * scores (double for an exact hit) - set from how hard a whole turn finds it to hit (see `GameMode.HIT_LIST`'s
 * card), so a target with [ANY] places is worth less than one with every number named.
 */
data class TargetShape(val pattern: String, val points: Int) {
    init {
        require(pattern.count { it == ANY } <= MAX_ANY_PLACES) { "A target has at most $MAX_ANY_PLACES any places: $pattern" }
    }

    /** The distinct letters, each one number when drawn. */
    val letters: List<Char>
        get() = pattern.filter { it != ANY }.toList().distinct()

    companion object {
        /** A place that matches any die. */
        const val ANY = '·'

        /** The most places a target may leave as [ANY]. */
        const val MAX_ANY_PLACES = 2
    }
}

/**
 * One Hit List target, as drawn for a game: a number for each die's place, left to right - or null where any
 * die will do - and what it's worth.
 *
 * Every number it calls ([called]) being among the dice, in any order, is a hit, scoring [points]. Every called
 * number in its own place is an exact hit, scoring double ([exactPoints]). Only a whole hand can be exact: a hand
 * missing a die (one locked by Unlucky Dice) has no places to line up. Short of a hit, the dice score a partial hit
 * ([partialPoints]): half the points, times the share of its numbers they show, rounded to the nearest multiple of
 * [PARTIAL_STEP] - once they show at least [MIN_PARTIAL_ROLLED] of them.
 */
data class HitTarget(val places: List<Int?>, val points: Int) {

    /** The numbers this target calls, in place order, [places]' any places left out. */
    val called: List<Int>
        get() = places.filterNotNull()

    val exactPoints: Int
        get() = points * 2

    /** Whether every number [called] is among [dice], in any order - each die counting once. */
    fun isHit(dice: List<Die>): Boolean {
        val left = dice.mapTo(mutableListOf()) { it.value }
        return called.all { left.remove(it) }
    }

    /** Whether [dice] are a whole hand with every number [called] in its own place. */
    fun isExactHit(dice: List<Die>): Boolean =
        dice.size == places.size && places.indices.all { places[it] == null || places[it] == dice[it].value }

    /** How many of the numbers [called] are among [dice] - each die counting once. */
    fun rolledCount(dice: List<Die>): Int {
        val left = dice.mapTo(mutableListOf()) { it.value }
        return called.count { left.remove(it) }
    }

    /**
     * What [dice] score short of a hit: half of [points], times the share of [called] they show ([rolledCount]),
     * rounded to the nearest multiple of [PARTIAL_STEP], a half rounding up - so 4 of 5 on a 75 is 30, and 3 of 4 on a
     * 20 (7.5) is 10. Fewer than [MIN_PARTIAL_ROLLED] of its numbers score nothing, however the rounding would fall.
     */
    fun partialPoints(dice: List<Die>): Int {
        val rolled = rolledCount(dice)
        if (rolled < MIN_PARTIAL_ROLLED) return 0
        // Exact in whole numbers: points * rolled / (2 * named), plus half a step, then down to a step.
        val halves = called.size * 2
        return (points * rolled * 2 + PARTIAL_STEP * halves) / (halves * 2 * PARTIAL_STEP) * PARTIAL_STEP
    }

    /** What [dice] score on this target: [exactPoints] for an exact hit, [points] for a hit, otherwise [partialPoints]. */
    fun score(dice: List<Die>): Int = when {
        isExactHit(dice) -> exactPoints
        isHit(dice) -> points
        else -> partialPoints(dice)
    }

    /**
     * How each of [places] stands against [dice] - what the target's tile shows as it fills in. A number already in
     * its own place is [PlaceMatch.IN_PLACE]; otherwise a die showing it elsewhere (and not already claimed) is
     * [PlaceMatch.ROLLED]. Places come first, so a die can't be counted for two of them.
     */
    fun matches(dice: List<Die>): List<PlaceMatch> {
        val wholeHand = dice.size == places.size
        val result = MutableList(places.size) { index -> if (places[index] == null) PlaceMatch.ANY else PlaceMatch.MISSING }
        val unclaimed = dice.indices.toMutableList()
        if (wholeHand) {
            for (index in places.indices) {
                if (places[index] != null && dice[index].value == places[index]) {
                    result[index] = PlaceMatch.IN_PLACE
                    unclaimed -= index
                }
            }
        }
        for (index in places.indices) {
            val value = places[index] ?: continue
            if (result[index] != PlaceMatch.MISSING) continue
            val die = unclaimed.firstOrNull { dice[it].value == value } ?: continue
            result[index] = PlaceMatch.ROLLED
            unclaimed -= die
        }
        return result
    }

    companion object {
        /**
         * Draws [shape] as a target: a different number from 1-6 for each of its letters, then the places shuffled,
         * all from [random].
         */
        fun draw(shape: TargetShape, random: Random): HitTarget {
            val numbers = shape.letters.zip((1..DIE_FACES).shuffled(random)).toMap()
            val places = shape.pattern.map { numbers[it] }.shuffled(random)
            return HitTarget(places, shape.points)
        }

        private const val DIE_FACES = 6

        /** The fewest of a target's numbers the dice must show for a partial hit to score anything. */
        const val MIN_PARTIAL_ROLLED = 2

        /** What a partial hit is rounded to the nearest multiple of, so every score stays a multiple of five like the points. */
        const val PARTIAL_STEP = 5
    }
}

/** How one place of a [HitTarget] stands against the dice - see [HitTarget.matches]. */
enum class PlaceMatch {
    /** A place any die fills. */
    ANY,

    /** No die shows this place's number (that isn't already counted for another place). */
    MISSING,

    /** A die shows this place's number, but not in this place. */
    ROLLED,

    /** The die in this place shows its number. */
    IN_PLACE,
}
