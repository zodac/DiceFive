package net.zodac.dicefive.data.game

import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.GameType
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class GameStateJsonTest {

    @Test
    fun `round trips a fresh game with open scorecards`() {
        val state = GameState(
            gameType = GameType.CLASSIC,
            players = listOf(
                PlayerState(name = "Player 1", type = PlayerType.HUMAN),
                PlayerState(name = "Bot", type = PlayerType.AI, difficulty = Difficulty.HARD),
            ),
            currentPlayerIndex = 1,
            dice = List(5) { Die(value = it % 6 + 1, isHeld = it % 2 == 0) },
            rollsRemaining = 2,
            phase = TurnPhase.ROLLED,
            isGameOver = false,
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
    }

    @Test
    fun `round trips a partially and fully filled scorecard, including zero scores`() {
        val scorecard = ScoreCategory.entries.associateWith { category ->
            when (category) {
                ScoreCategory.ONES -> 3
                ScoreCategory.FULL_HOUSE -> 0
                else -> null
            }
        }
        val state = GameState(
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, scorecard = scorecard, fiveOfAKindBonusCount = 2)),
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
        assertEquals(3, decoded.players.single().scorecard[ScoreCategory.ONES])
        assertEquals(0, decoded.players.single().scorecard[ScoreCategory.FULL_HOUSE])
        assertEquals(null, decoded.players.single().scorecard[ScoreCategory.CHANCE])
    }

    @Test
    fun `round trips a finished game`() {
        val scorecard = ScoreCategory.entries.associateWith { 10 }
        val state = GameState(
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, scorecard = scorecard)),
            isGameOver = true,
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
    }
}
