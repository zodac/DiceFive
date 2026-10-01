package net.zodac.dicefive.ui.styles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DicePickRollTest {

    @Test
    fun aPickedDieShowsEveryFaceAndFinishesOnItsTilesFive() {
        assertEquals((1..6).toSet(), DIE_PICK_ROLL_FACES.toSet())
        assertEquals(6, DIE_PICK_ROLL_FACES.size)
        assertEquals(5, DIE_PICK_ROLL_FACES.last())
    }

    @Test
    fun aPickedDieOnlyTurnsOverToANeighbouringFace() {
        // From the tile's 5, through each face in turn: never to the same face or the opposite one (they add up to 7).
        (listOf(5) + DIE_PICK_ROLL_FACES).zipWithNext().forEach { (from, to) ->
            assertNotEquals(from, to, "$from to $to")
            assertNotEquals(7, from + to, "$from to $to")
        }
    }
}
