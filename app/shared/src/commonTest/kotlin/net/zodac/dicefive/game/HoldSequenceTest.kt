package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoldSequenceTest {

    /** Every die held then released, in order: the toggles before the last, and whether the last finished it. */
    private fun HoldSequence.play(dice: Int = 5): Pair<List<Boolean>, Boolean> {
        val toggles = (0 until dice).flatMap { listOf(it to true, it to false) }
        val before = toggles.dropLast(1).map { (index, holding) -> onToggle(index, holding, dice) }
        val (index, holding) = toggles.last()
        return before to onToggle(index, holding, dice)
    }

    @Test
    fun `each die held then released in order finishes it - once - and again from the start`() {
        val sequence = HoldSequence()
        val (before, last) = sequence.play()
        assertTrue(before.none { it })
        assertTrue(last)
        assertTrue(sequence.play().second, "it starts again once finished")
        assertTrue(sequence.play(dice = 7).second, "every die of seven")
    }

    @Test
    fun `a toggle out of order starts it again - from there when it's the first die held`() {
        val sequence = HoldSequence()
        sequence.onToggle(0, holding = true, dieCount = 5)
        sequence.onToggle(0, holding = false, dieCount = 5)
        // Die 2 before die 1: back to the start.
        assertFalse(sequence.onToggle(2, holding = true, dieCount = 5))
        assertTrue(sequence.play().second)

        // Holding die 0 mid-way counts as the opening move of a fresh run.
        sequence.onToggle(0, holding = true, dieCount = 5)
        sequence.onToggle(0, holding = false, dieCount = 5)
        sequence.onToggle(0, holding = true, dieCount = 5)
        val rest = listOf(0 to false) + (1 until 5).flatMap { listOf(it to true, it to false) }
        assertEquals(listOf(true), rest.map { (index, holding) -> sequence.onToggle(index, holding, 5) }.filter { it })

        // A reset forgets a run part done.
        sequence.onToggle(0, holding = true, dieCount = 5)
        sequence.reset()
        assertFalse(sequence.onToggle(0, holding = false, dieCount = 5))
    }
}
