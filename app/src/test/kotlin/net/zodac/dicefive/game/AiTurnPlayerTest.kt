package net.zodac.dicefive.game

import kotlin.random.Random
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTurnPlayerTest {

    @Test
    fun `chooseCategory picks the highest-scoring open category`() {
        val state = GameState(
            players = listOf(PlayerState(name = "Bot", type = PlayerType.AI)),
            dice = List(5) { Die(value = 6) },
            rollsRemaining = 0,
            phase = TurnPhase.ROLLED,
        )

        assertEquals(ScoreCategory.YAHTZEE, AiTurnPlayer.chooseCategory(state))
    }

    @Test
    fun `chooseCategory never picks an already-filled category`() {
        val scorecard = PlayerState(name = "Bot", type = PlayerType.AI).scorecard + (ScoreCategory.YAHTZEE to 50)
        val state = GameState(
            players = listOf(PlayerState(name = "Bot", type = PlayerType.AI, scorecard = scorecard)),
            dice = List(5) { Die(value = 6) },
            rollsRemaining = 0,
            phase = TurnPhase.ROLLED,
        )

        assertFalse(AiTurnPlayer.chooseCategory(state) == ScoreCategory.YAHTZEE)
    }

    @Test
    fun `playTurn rolls three times then scores exactly one category`() {
        val initial = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.AI, name = "Bot")))

        val result = AiTurnPlayer.playTurn(initial, random = Random(42))

        val filled = result.players.single().scorecard.values.count { it != null }
        assertEquals(1, filled)
        assertEquals(TurnPhase.AWAITING_ROLL, result.phase)
        assertEquals(3, result.rollsRemaining)
        assertTrue(result.dice.none { it.isHeld })
    }
}
