package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState

/**
 * Perfect solo play's expected score for the rest of a Standard game, from the start of every turn
 * that can happen - what Hard looks up as a finished hand's future, in place of its estimate, so in
 * Standard it plays the best a solo player can (about 254.5 a game on average, against ~239 by
 * estimate). Generated once, by [solve], and bundled as [RESOURCE_PATH]; regenerated only when
 * Standard's rules change - `StandardPerfectPlayTableTest` re-works a sample of its states from its own
 * values ([mismatchedStates]) and fails if the bundled copy no longer matches.
 *
 * A turn's start is all that matters to the rest of the game: which boxes are filled, the upper
 * section's total (capped at the bonus threshold - past it, more makes no difference), and whether
 * the 5x box holds its full score (which makes later 5x jokers worth a bonus chip). 536,448 of those
 * can happen; each value is kept to 1/[SCALE] of a point in two bytes - 1 MB in all.
 */
class StandardPerfectPlayTable private constructor(private val values: ShortArray) {

    /** The expected rest-of-game score, playing perfectly, from the start of a turn in this state. */
    internal fun valueOf(filledMask: Int, upperTotal: Int, fiveOfAKindScored: Boolean): Double =
        decodeValue(values[StateIndex.of(filledMask, upperTotal, fiveOfAKindScored)])

    /** As written to [RESOURCE_PATH]: a short header, then every state's value in [StateIndex] order. */
    internal fun encode(): ByteArray {
        val bytes = ByteArray(HEADER_SIZE + values.size * 2)
        MAGIC.copyInto(bytes)
        bytes[MAGIC.size] = FORMAT_VERSION
        writeInt(bytes, MAGIC.size + 1, values.size)
        for ((index, value) in values.withIndex()) {
            bytes[HEADER_SIZE + index * 2] = value.toByte()
            bytes[HEADER_SIZE + index * 2 + 1] = (value.toInt() shr 8).toByte()
        }
        return bytes
    }

    /**
     * Every Standard start-of-turn state, numbered: for each set of filled boxes in turn, each upper
     * total it can have (only those reachable with exactly those upper boxes filled), and - once the
     * 5x box is filled - whether it holds its full score.
     */
    private object StateIndex {
        private val mode = GameMode.STANDARD
        private val scoring = HandScoring(mode, DiceSpace(mode.dieValues.map { Die(value = it) }, mode.diceCount))
        val categoryCount = mode.categories.size
        val threshold = mode.upperBonusThreshold

        /** Per set of filled boxes: a bit for every upper total it can have. */
        val reachableUpperTotals: LongArray = LongArray(1 shl categoryCount) { mask ->
            var reachable = 1L
            for (category in 0 until categoryCount) {
                val value = scoring.upperValue[category]
                if (value == 0 || (mask shr category) and 1 == 0) continue
                var next = 0L
                for (total in 0..threshold) {
                    if ((reachable shr total) and 1L == 0L) continue
                    for (count in 0..mode.diceCount) next = next or (1L shl minOf(threshold, total + count * value))
                }
                reachable = next
            }
            reachable
        }

        fun fiveOfAKindStates(mask: Int): Int = if ((mask shr scoring.fiveOfAKindIndex) and 1 == 1) 2 else 1

        private val maskStart = IntArray((1 shl categoryCount) + 1).also { start ->
            for (mask in 0 until (1 shl categoryCount)) {
                start[mask + 1] = start[mask] + reachableUpperTotals[mask].countOneBits() * fiveOfAKindStates(mask)
            }
        }

        val size: Int get() = maskStart.last()

        fun of(filledMask: Int, upperTotal: Int, fiveOfAKindScored: Boolean): Int {
            val upper = minOf(threshold, upperTotal)
            val reachable = reachableUpperTotals[filledMask]
            require((reachable shr upper) and 1L == 1L) { "Upper total $upper can't happen with boxes $filledMask filled" }
            val position = (reachable and ((1L shl upper) - 1)).countOneBits()
            val five = if (fiveOfAKindScored) 1 else 0
            return maskStart[filledMask] + position * fiveOfAKindStates(filledMask) + five
        }

        init {
            check(threshold < Long.SIZE_BITS) { "Upper totals up to the threshold must fit a Long's bits" }
        }
    }

    companion object {
        /** Where the bundled table lives among the Compose resources. */
        internal const val RESOURCE_PATH = "files/standard_perfect_play.bin"

        /** Values are kept to 1/SCALE of a point: two bytes hold up to 2,047 points, far beyond any expectation. */
        private const val SCALE = 32.0
        private val MAGIC = byteArrayOf('D'.code.toByte(), '5'.code.toByte(), 'P'.code.toByte(), 'P'.code.toByte())
        private const val FORMAT_VERSION: Byte = 1
        private const val HEADER_SIZE = 9

        private fun encodeValue(value: Double): Short {
            val scaled = kotlin.math.round(value * SCALE).toInt()
            check(scaled in 0..UShort.MAX_VALUE.toInt()) { "A rest-of-game expectation of $value doesn't fit" }
            return scaled.toShort()
        }

        private fun decodeValue(stored: Short): Double = (stored.toInt() and 0xFFFF) / SCALE

        /** Reads a table [encode]d earlier, checking it's whole and in this version's format. */
        internal fun decode(bytes: ByteArray): StandardPerfectPlayTable {
            require(bytes.size >= HEADER_SIZE && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Not a perfect-play table" }
            require(bytes[MAGIC.size] == FORMAT_VERSION) { "Perfect-play table format ${bytes[MAGIC.size]}, expected $FORMAT_VERSION" }
            val count = readInt(bytes, MAGIC.size + 1)
            require(count == StateIndex.size && bytes.size == HEADER_SIZE + count * 2) { "Perfect-play table has $count states, expected ${StateIndex.size}" }
            return StandardPerfectPlayTable(
                ShortArray(count) { index ->
                    ((bytes[HEADER_SIZE + index * 2].toInt() and 0xFF) or ((bytes[HEADER_SIZE + index * 2 + 1].toInt() and 0xFF) shl 8)).toShort()
                },
            )
        }

        /**
         * Works the whole table out from Standard's rules, through the game's own scoring
         * ([HandScoring]) - about a quarter of a minute on a laptop core, so it's run once and
         * bundled rather than on a phone. Backwards from a full scorecard (worth nothing more): each
         * state's value is a perfectly played turn's average, where every finished hand goes in
         * whichever box makes its score, any 5x bonus chip and upper bonus, plus the state it leaves
         * behind, worth the most. Each state is worked from the stored (rounded) values of the states
         * after it, so the table can be checked against itself exactly.
         */
        internal fun solve(): StandardPerfectPlayTable {
            val solver = Solver()
            val values = ShortArray(StateIndex.size)
            solver.forEachState(fromTheEnd = true) { mask, upper, fiveScored ->
                values[StateIndex.of(mask, upper, fiveScored)] = encodeValue(solver.turnValue(values, mask, upper, fiveScored))
            }
            return StandardPerfectPlayTable(values)
        }

        /**
         * Re-works every [stride]th state of [table] from the table's own stored values of the states
         * after it, as [solve] did, and lists each one that comes out different - what a rule change the
         * table wasn't regenerated for does to it. A sample, not the whole table, so it's cheap enough for
         * every test run: any rule change worth the name moves thousands of states.
         */
        internal fun mismatchedStates(table: StandardPerfectPlayTable, stride: Int): List<String> {
            val solver = Solver()
            val mismatched = mutableListOf<String>()
            solver.forEachState(fromTheEnd = false) { mask, upper, fiveScored ->
                val index = StateIndex.of(mask, upper, fiveScored)
                if (index % stride != 0) return@forEachState
                val expected = encodeValue(solver.turnValue(table.values, mask, upper, fiveScored))
                if (expected != table.values[index]) mismatched += "boxes $mask, upper $upper, 5x scored $fiveScored: ${decodeValue(table.values[index])} != ${decodeValue(expected)}"
            }
            return mismatched
        }

        /** Standard's scoring, and the perfectly played value of one turn from the stored values after it. */
        private class Solver {
            val mode = GameMode.STANDARD
            val space = DiceSpace(mode.dieValues.map { Die(value = it) }, mode.diceCount)
            val scoring = HandScoring(mode, space)
            private val endValues = DoubleArray(space.handCount)

            /** Every start-of-turn state short of a full scorecard - [fromTheEnd] in the order [solve] needs. */
            inline fun forEachState(fromTheEnd: Boolean, use: (mask: Int, upper: Int, fiveScored: Boolean) -> Unit) {
                val masks = (0 until (1 shl StateIndex.categoryCount) - 1)
                for (mask in if (fromTheEnd) masks.sortedByDescending { it.countOneBits() } else masks.toList()) {
                    val reachable = StateIndex.reachableUpperTotals[mask]
                    for (upper in 0..StateIndex.threshold) {
                        if ((reachable shr upper) and 1L == 0L) continue
                        for (five in 0 until StateIndex.fiveOfAKindStates(mask)) use(mask, upper, five == 1)
                    }
                }
            }

            fun turnValue(values: ShortArray, mask: Int, upper: Int, fiveScored: Boolean): Double {
                for (hand in 0 until space.handCount) {
                    var best = Double.NEGATIVE_INFINITY
                    scoring.forEachLegal(hand, mask, fiveScored) { category, score, chip ->
                        val value = afterScoring(scoring, mask, upper, fiveScored, category, score) { nextMask, nextUpper, nextFive, bonus ->
                            score + chip + bonus + decodeValue(values[StateIndex.of(nextMask, nextUpper, nextFive)])
                        }
                        if (value > best) best = value
                    }
                    endValues[hand] = best
                }
                return space.keepValues(endValues, mode.rollsPerTurn)[space.emptyKeep]
            }
        }

        /**
         * Where scoring [score] in [category] leaves a player - the filled boxes, upper total and 5x
         * state the table is keyed by - and any upper bonus it earns on the way, handed to [use].
         */
        internal inline fun <R> afterScoring(
            scoring: HandScoring,
            mask: Int,
            upper: Int,
            fiveScored: Boolean,
            category: Int,
            score: Int,
            use: (nextMask: Int, nextUpper: Int, nextFiveScored: Boolean, upperBonus: Int) -> R,
        ): R {
            val mode = scoring.mode
            var nextUpper = upper
            var bonus = 0
            if (scoring.upperValue[category] != 0) {
                if (upper < mode.upperBonusThreshold && upper + score >= mode.upperBonusThreshold) bonus = mode.upperBonusAmount
                nextUpper = minOf(mode.upperBonusThreshold, upper + score)
            }
            val nextFive = if (category == scoring.fiveOfAKindIndex) scoring.scoresFiveOfAKind(category, score) else fiveScored
            return use(mask or (1 shl category), nextUpper, nextFive, bonus)
        }

        private fun writeInt(bytes: ByteArray, at: Int, value: Int) {
            for (index in 0 until Int.SIZE_BYTES) bytes[at + index] = (value shr (index * 8)).toByte()
        }

        private fun readInt(bytes: ByteArray, at: Int): Int {
            var value = 0
            for (index in 0 until Int.SIZE_BYTES) value = value or ((bytes[at + index].toInt() and 0xFF) shl (index * 8))
            return value
        }
    }
}

/** [player]'s upper total as the table counts it - capped at the bonus threshold. */
internal fun PlayerState.cappedUpperTotal(): Int = minOf(upperSectionTotal, upperBonusThreshold)
