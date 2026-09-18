package net.zodac.yahtzee

import net.zodac.yahtzee.model.GameState
import org.junit.Assert.assertEquals
import org.junit.Test

class GameStateTest {

    @Test
    fun `new game state starts with five dice and three rolls remaining`() {
        val state = GameState()

        assertEquals(5, state.dice.size)
        assertEquals(3, state.rollsRemaining)
    }
}
