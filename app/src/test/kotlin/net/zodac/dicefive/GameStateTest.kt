package net.zodac.dicefive

import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GameStateTest {

    @Test
    fun `new game starts with five dice and three rolls remaining`() {
        val state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1")))

        assertEquals(5, state.dice.size)
        assertEquals(3, state.rollsRemaining)
        assertEquals(TurnPhase.AWAITING_ROLL, state.phase)
        assertFalse(state.isGameOver)
    }

    @Test
    fun `new game starts with every scorecard box open`() {
        val state = GameEngine.newGame(
            listOf(
                PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"),
                PlayerConfig(slot = 2, type = PlayerType.AI, name = "Bot", difficulty = Difficulty.HARD),
            ),
        )

        assertEquals(2, state.players.size)
        for (player in state.players) {
            for (value in player.scorecard.values) {
                assertNull(value)
            }
        }
    }
}
