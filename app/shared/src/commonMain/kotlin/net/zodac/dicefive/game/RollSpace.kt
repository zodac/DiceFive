package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode

/**
 * What a turn in [mode] rolls ([rolls]) and the hands it's scored as ([hands]), each die landing on
 * one of [faces]. In a mode where every die scores they're the same space. In one where only the held
 * dice score ([GameMode.scoresHeldDiceOnly]), [rolls] is every roll of [GameMode.diceCount] dice, at
 * most [GameMode.scoringDiceCount] of them held, and a finished roll is worth its best hand - the
 * dice it would then hold ([rollValues]).
 *
 * Stud's seven dice make 792 rolls and 462 held sets, each with up to 21 hands in it.
 */
internal class RollSpace(faces: List<Die>, mode: GameMode) {

    val hands: DiceSpace = DiceSpace(faces, mode.scoringDiceCount)

    val rolls: DiceSpace = if (mode.scoresHeldDiceOnly) DiceSpace(faces, mode.diceCount, maxHeld = mode.scoringDiceCount) else hands

    // Each roll's distinct hands, as runs: those of roll r sit at handStart[r] until handStart[r + 1].
    // Empty when rolls and hands are one space.
    private val handStart = IntArray(if (rolls === hands) 0 else rolls.handCount + 1)
    private val handOfRoll: IntArray

    init {
        val handsOfRolls = ArrayList<Int>()
        if (rolls !== hands) {
            for (roll in 0 until rolls.handCount) {
                handStart[roll] = handsOfRolls.size
                rolls.forEachSubKeep(roll) { keep ->
                    val counts = rolls.keepFaceCounts(keep)
                    if (counts.sum() == mode.scoringDiceCount) {
                        handsOfRolls += hands.handOf(counts.withIndex().flatMap { (face, count) -> List(count.toInt()) { faces[face] } })
                    }
                }
            }
            handStart[rolls.handCount] = handsOfRolls.size
        }
        handOfRoll = handsOfRolls.toIntArray()
    }

    /** What each finished roll is worth: its best hand, by [handValues]. [handValues] itself when every die scores. */
    fun rollValues(handValues: DoubleArray): DoubleArray {
        if (rolls === hands) return handValues
        return DoubleArray(rolls.handCount) { roll ->
            var best = Double.NEGATIVE_INFINITY
            for (index in handStart[roll] until handStart[roll + 1]) {
                val value = handValues[handOfRoll[index]]
                if (value > best) best = value
            }
            best
        }
    }
}
