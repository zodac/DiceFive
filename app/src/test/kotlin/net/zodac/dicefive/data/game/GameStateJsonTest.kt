package net.zodac.dicefive.data.game

import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer
import org.junit.Assert.assertEquals
import org.junit.Test

class GameStateJsonTest {

    @Test
    fun `round trips a fresh game with open scorecards`() {
        val state = GameState(
            gameMode = GameMode.STANDARD,
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
    fun `round trips a non-default turn timer`() {
        val state = GameState(
            turnTimer = TurnTimer.SECONDS_60,
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)),
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(TurnTimer.SECONDS_60, decoded.turnTimer)
    }

    @Test
    fun `decodes a save from before the turn timer field existed as no timer`() {
        val state = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)))
        val legacyJson = org.json.JSONObject(GameStateJson.encode(state)).apply { remove("turnTimer") }.toString()

        val decoded = GameStateJson.decode(legacyJson)

        assertEquals(TurnTimer.NONE, decoded.turnTimer)
    }

    @Test
    fun `round trips a partially and fully filled scorecard, including zero scores`() {
        val scorecard = GameMode.STANDARD.categories.associateWith { category ->
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
        val scorecard = GameMode.STANDARD.categories.associateWith { 10 }
        val state = GameState(
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, scorecard = scorecard)),
            isGameOver = true,
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
    }

    @Test
    fun `round trips a Tricolour game, including each die's colour and the colour boxes`() {
        val mode = GameMode.TRICOLOUR
        val scorecard = mode.categories.associateWith { category ->
            when (category) {
                ScoreCategory.REDS -> 40
                ScoreCategory.COLOURED_HOUSE -> 0
                else -> null
            }
        }
        val state = GameState(
            gameMode = mode,
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, gameMode = mode, scorecard = scorecard)),
            dice = listOf(
                Die(value = 1, colour = DieColour.RED),
                Die(value = 2, colour = DieColour.YELLOW, isHeld = true),
                Die(value = 3, colour = DieColour.BLUE),
                Die(value = 4, colour = DieColour.RED),
                Die(value = 5, colour = DieColour.BLUE),
            ),
            phase = TurnPhase.ROLLED,
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
        assertEquals(GameMode.TRICOLOUR, decoded.players.single().gameMode)
    }

    @Test
    fun `decodes a save from before game modes existed as Standard`() {
        val state = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)))
        val legacyJson = org.json.JSONObject(GameStateJson.encode(state)).apply {
            remove("gameMode")
            put("gameType", "CLASSIC")
        }.toString()

        val decoded = GameStateJson.decode(legacyJson)

        assertEquals(GameMode.STANDARD, decoded.gameMode)
        assertEquals(state, decoded)
    }
}
