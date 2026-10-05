package net.zodac.dicefive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.TurnPhase

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
            for (scores in player.scorecard.values) {
                assertTrue(scores.isEmpty())
            }
        }
    }
}
