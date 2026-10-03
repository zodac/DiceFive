package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die

/**
 * Every distinct hand of [diceCount] dice that each land on one of [faces], every set of dice that
 * can be held out of one (smaller hands, down to holding nothing), and the odds of each hand coming
 * out of rerolling the rest - all numbered up front and laid out in flat arrays, so Hard's whole-turn
 * search ([keepValues], [bestKeepValues]) is a few passes of plain arithmetic over them rather than a
 * tree of map lookups. Costly to build for many faces, so each is built once and kept - see
 * [AiTurnPlayer]'s per-mode lazies.
 *
 * Hands and held sets are unordered - how many dice show each face - since scoring never depends on
 * die order: five dice of 18 faces is 1.9 million ordered rolls, but only 26,334 hands. Standard's
 * six faces make 252 hands and 462 held sets; Tricolour's 18 make 26,334 and 33,649.
 */
internal class DiceSpace(val faces: List<Die>, val diceCount: Int) {

    init {
        require(diceCount <= MAX_DICE) { "More dice than a space is built for" }
    }

    private val faceCount = faces.size

    // A hand or held set is keyed by its face counts, packed into one Long in base diceCount + 1.
    private val facePowers = LongArray(faceCount).also { powers ->
        var power = 1L
        for (index in powers.indices) {
            powers[index] = power
            if (index < powers.lastIndex) {
                check(power <= Long.MAX_VALUE / (diceCount + 1)) { "Too many faces to key a hand by" }
                power *= diceCount + 1
            }
        }
    }

    private val handFaceCounts = ArrayList<ByteArray>()
    private val handIndexByKey = HashMap<Long, Int>()
    private val keepFaceCounts = ArrayList<ByteArray>()
    private val keepIndexByKey = HashMap<Long, Int>()

    init {
        forEachCounts(diceCount) { counts ->
            handIndexByKey[keyOf(counts)] = handFaceCounts.size
            handFaceCounts += counts
        }
        for (held in 0..diceCount) {
            forEachCounts(held) { counts ->
                keepIndexByKey[keyOf(counts)] = keepFaceCounts.size
                keepFaceCounts += counts
            }
        }
    }

    val handCount: Int get() = handFaceCounts.size
    val keepCount: Int get() = keepFaceCounts.size

    /** The held set of no dice at all - a turn's first roll. */
    val emptyKeep: Int = keepIndexByKey.getValue(0L)

    // Each held set's reroll outcomes, as a run of (hand, chance) pairs: those of keep k sit at
    // outcomeStart[k] until outcomeStart[k + 1].
    private val outcomeStart = IntArray(keepCount + 1)
    private val outcomeHand: IntArray
    private val outcomeChance: DoubleArray

    // Each hand's distinct held sets, standing pat included, the same way.
    private val subKeepStart = IntArray(handCount + 1)
    private val subKeep: IntArray

    init {
        val hands = ArrayList<Int>()
        val chances = ArrayList<Double>()
        for ((keep, held) in keepFaceCounts.withIndex()) {
            outcomeStart[keep] = hands.size
            val free = diceCount - held.sum()
            var orderedRolls = 1.0
            repeat(free) { orderedRolls *= faceCount }
            forEachCounts(free) { rolled ->
                // How many of the equally likely ordered rolls give these faces: the multinomial coefficient.
                var ways = FACTORIALS[free]
                for (count in rolled) ways /= FACTORIALS[count.toInt()]
                hands += handIndexByKey.getValue(keyOf(held) + keyOf(rolled))
                chances += ways / orderedRolls
            }
        }
        outcomeStart[keepCount] = hands.size
        outcomeHand = hands.toIntArray()
        outcomeChance = chances.toDoubleArray()

        val subKeeps = ArrayList<Int>()
        for ((hand, counts) in handFaceCounts.withIndex()) {
            subKeepStart[hand] = subKeeps.size
            forEachSubCounts(counts) { held -> subKeeps += keepIndexByKey.getValue(keyOf(held)) }
        }
        subKeepStart[handCount] = subKeeps.size
        subKeep = subKeeps.toIntArray()
    }

    /** Which hand [dice] are - each die by its face (number and colour), held or not. */
    fun handOf(dice: List<Die>): Int = handIndexByKey.getValue(keyOf(countsOf(dice)))

    /** How many of [hand]'s dice show each of [faces]. */
    fun faceCounts(hand: Int): ByteArray = handFaceCounts[hand]

    /** How many dice of each of [faces] the held set [keep] holds. */
    fun keepFaceCounts(keep: Int): ByteArray = keepFaceCounts[keep]

    /** [hand] as dice, in face order. */
    fun diceOf(hand: Int): List<Die> = handFaceCounts[hand].withIndex().flatMap { (face, count) -> List(count.toInt()) { faces[face] } }

    /** Calls [action] with each distinct set of dice that can be held out of [hand], holding all of them included. */
    inline fun forEachSubKeep(hand: Int, action: (keep: Int) -> Unit) {
        for (index in subKeepStartOf(hand) until subKeepStartOf(hand + 1)) action(subKeepAt(index))
    }

    @PublishedApi internal fun subKeepStartOf(hand: Int): Int = subKeepStart[hand]

    @PublishedApi internal fun subKeepAt(index: Int): Int = subKeep[index]

    /** Each held set's value: the average, over every way the rest can land, of the hand it makes, by [handValues]. */
    fun keepValues(handValues: DoubleArray): DoubleArray = DoubleArray(keepCount) { keep ->
        var total = 0.0
        for (outcome in outcomeStart[keep] until outcomeStart[keep + 1]) total += outcomeChance[outcome] * handValues[outcomeHand[outcome]]
        total
    }

    /** Each hand's value with a reroll to come: its best held set, by [keepValues]. */
    fun bestKeepValues(keepValues: DoubleArray): DoubleArray = DoubleArray(handCount) { hand ->
        var best = Double.NEGATIVE_INFINITY
        forEachSubKeep(hand) { keep -> if (keepValues[keep] > best) best = keepValues[keep] }
        best
    }

    /**
     * Every held set's value with [rolls] rolls (the one it's held for included) still to come this
     * turn, given what each finished hand is worth ([endValues]): the last roll's held sets average
     * [endValues]; each earlier one averages the best held set of the hand it makes.
     */
    fun keepValues(endValues: DoubleArray, rolls: Int): DoubleArray {
        require(rolls >= 1) { "A held set is held for a roll" }
        var keepValues = keepValues(endValues)
        repeat(rolls - 1) { keepValues = keepValues(bestKeepValues(keepValues)) }
        return keepValues
    }

    private fun keyOf(counts: ByteArray): Long {
        var key = 0L
        for (face in counts.indices) key += counts[face] * facePowers[face]
        return key
    }

    private fun countsOf(dice: List<Die>): ByteArray {
        val counts = ByteArray(faceCount)
        for (die in dice) {
            val face = faces.indexOf(Die(value = die.value, colour = die.colour))
            require(face >= 0) { "A ${die.value} ${die.colour} isn't one of these dice's faces" }
            counts[face]++
        }
        return counts
    }

    /** Calls [action] with every way [size] dice can show [faceCount] faces, as counts per face. */
    private fun forEachCounts(size: Int, action: (ByteArray) -> Unit) {
        val counts = ByteArray(faceCount)
        fun fill(face: Int, left: Int) {
            if (face == faceCount - 1) {
                counts[face] = left.toByte()
                action(counts.copyOf())
                return
            }
            for (count in 0..left) {
                counts[face] = count.toByte()
                fill(face + 1, left - count)
            }
        }
        fill(0, size)
    }

    /** Calls [action] with every count vector no bigger, face by face, than [counts]. */
    private fun forEachSubCounts(counts: ByteArray, action: (ByteArray) -> Unit) {
        val held = ByteArray(faceCount)
        fun fill(face: Int) {
            if (face == faceCount) {
                action(held.copyOf())
                return
            }
            for (count in 0..counts[face]) {
                held[face] = count.toByte()
                fill(face + 1)
            }
        }
        fill(0)
    }

    private companion object {
        const val MAX_DICE = 12
        val FACTORIALS = DoubleArray(MAX_DICE + 1).also { table ->
            table[0] = 1.0
            for (n in 1..MAX_DICE) table[n] = table[n - 1] * n
        }
    }
}
