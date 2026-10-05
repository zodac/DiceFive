package net.zodac.dicefive.data.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import net.zodac.dicefive.data.JsonArray
import net.zodac.dicefive.data.JsonNull
import net.zodac.dicefive.data.JsonNumber
import net.zodac.dicefive.data.JsonObject
import net.zodac.dicefive.data.parseJson
import net.zodac.dicefive.data.toJson
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.oneScoreEach

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
    fun `round trips the seconds left on the turn timer`() {
        val state = GameState(
            turnTimer = TurnTimer.SECONDS_60,
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)),
            turnSecondsLeft = 23,
        )

        assertEquals(23, GameStateJson.decode(GameStateJson.encode(state)).turnSecondsLeft)
    }

    @Test
    fun `a save without seconds left decodes to a full timer`() {
        val state = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)))
        val withoutField = (parseJson(GameStateJson.encode(state)) as JsonObject).let { obj ->
            JsonObject(obj.fields.filterKeys { it != "turnSecondsLeft" })
        }.toJson()

        assertEquals(null, GameStateJson.decode(withoutField).turnSecondsLeft)
    }

    @Test
    fun `round trips a partially and fully filled scorecard - including zero scores`() {
        val scorecard = GameMode.STANDARD.categories.associateWith { category ->
            when (category) {
                ScoreCategory.ONES -> 3
                ScoreCategory.FULL_HOUSE -> 0
                else -> null
            }
        }
        val state = GameState(
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, scorecard = oneScoreEach(scorecard), fiveOfAKindBonusCount = 2)),
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
        assertEquals(3, decoded.players.single().scoresIn(ScoreCategory.ONES).singleOrNull())
        assertEquals(0, decoded.players.single().scoresIn(ScoreCategory.FULL_HOUSE).singleOrNull())
        assertEquals(null, decoded.players.single().scoresIn(ScoreCategory.CHANCE).singleOrNull())
    }

    @Test
    fun `round trips a finished game`() {
        val scorecard = GameMode.STANDARD.categories.associateWith { listOf(10) }
        val state = GameState(
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, scorecard = scorecard)),
            isGameOver = true,
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
    }

    @Test
    fun `round trips a Tricolour game - including each die's colour and the colour boxes`() {
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
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, gameMode = mode, scorecard = oneScoreEach(scorecard))),
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
    fun `round trips a Stud game - including which hold slot each held die is in`() {
        val mode = GameMode.STUD
        val state = GameState(
            gameMode = mode,
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, gameMode = mode)),
            dice = listOf(
                Die(value = 6, isHeld = true, heldSlot = 2),
                Die(value = 1),
                Die(value = 6, isHeld = true, heldSlot = 0),
                Die(value = 3),
                Die(value = 4),
                Die(value = 6, isHeld = true, heldSlot = 1),
                Die(value = 2),
            ),
            phase = TurnPhase.ROLLED,
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
        assertEquals(listOf(6, 6, 6), decoded.scoringDice.map { it.value })
    }

    @Test
    fun `round trips a player's lastRoll - held state included`() {
        val lastRoll = List(5) { Die(value = it % 6 + 1, isHeld = it % 2 == 0) }
        val state = GameState(
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, lastRoll = lastRoll)),
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
        assertEquals(lastRoll, decoded.players.single().lastRoll)
    }

    @Test
    fun `decodes a player saved without a lastRoll - no finished turn yet - as none`() {
        val state = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)))
        val saved = GameStateJson.encode(state).toJsonObject()
        val player = (saved["players"] as JsonArray).items[0] as JsonObject
        val withoutLastRoll = JsonObject(saved.fields + ("players" to JsonArray(listOf(JsonObject(player.fields - "lastRoll"))))).toJson()

        val decoded = GameStateJson.decode(withoutLastRoll)

        assertEquals(null, decoded.players.single().lastRoll)
    }

    @Test
    fun `round trips a player's last scored box - and decodes a save from before it was kept as none`() {
        val state = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, lastScoredCategory = ScoreCategory.FULL_HOUSE)))

        assertEquals(state, GameStateJson.decode(GameStateJson.encode(state)))

        val saved = GameStateJson.encode(state).toJsonObject()
        val player = (saved["players"] as JsonArray).items[0] as JsonObject
        val older = JsonObject(saved.fields + ("players" to JsonArray(listOf(JsonObject(player.fields - "lastScoredCategory"))))).toJson()

        assertEquals(null, GameStateJson.decode(older).players.single().lastScoredCategory)
    }

    @Test
    fun `round trips each player's roll count`() {
        val state = GameState(
            players = listOf(
                PlayerState(name = "Player 1", type = PlayerType.HUMAN, rollCount = 27),
                PlayerState(name = "Player 2", type = PlayerType.AI, rollCount = 25),
            ),
        )

        assertEquals(state, GameStateJson.decode(GameStateJson.encode(state)))
    }

    @Test
    fun `decodes a player saved before rolls were counted as having rolled none`() {
        val state = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, rollCount = 12)))
        val saved = GameStateJson.encode(state).toJsonObject()
        val player = (saved["players"] as JsonArray).items[0] as JsonObject
        val withoutRollCount = JsonObject(saved.fields + ("players" to JsonArray(listOf(JsonObject(player.fields - "rollCount"))))).toJson()

        val decoded = GameStateJson.decode(withoutRollCount)

        assertEquals(0, decoded.players.single().rollCount)
    }

    @Test
    fun `round trips a Third Wind game - every slot of every box in the order it was scored`() {
        val mode = GameMode.THIRD_WIND
        val base = PlayerState(name = "Player 1", type = PlayerType.HUMAN, gameMode = mode)
        val player = base.copy(
            scorecard = base.scorecard + mapOf(
                ScoreCategory.FIVES to listOf(15, 10, 20),
                ScoreCategory.CHANCE to listOf(22, 0),
                ScoreCategory.FIVE_OF_A_KIND to listOf(50),
            ),
        )
        val state = GameState(gameMode = mode, players = listOf(player))

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
        assertEquals(listOf(22, 0), decoded.players.single().scoresIn(ScoreCategory.CHANCE))
        assertEquals(emptyList<Int>(), decoded.players.single().scoresIn(ScoreCategory.ONES))
    }

    @Test
    fun `decodes a save from before a box could hold several scores - a number per filled box and null per open one`() {
        val state = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)))
        val saved = GameStateJson.encode(state).toJsonObject()
        val player = (saved["players"] as JsonArray).items[0] as JsonObject
        val oldScorecard = JsonObject(
            GameMode.STANDARD.categories.associate { category ->
                category.name to when (category) {
                    ScoreCategory.ONES -> JsonNumber("3")
                    ScoreCategory.FULL_HOUSE -> JsonNumber("0")
                    else -> JsonNull
                }
            },
        )
        val older = JsonObject(saved.fields + ("players" to JsonArray(listOf(JsonObject(player.fields + ("scorecard" to oldScorecard)))))).toJson()

        val decoded = GameStateJson.decode(older).players.single()

        assertEquals(listOf(3), decoded.scoresIn(ScoreCategory.ONES))
        assertEquals(listOf(0), decoded.scoresIn(ScoreCategory.FULL_HOUSE))
        assertEquals(emptyList<Int>(), decoded.scoresIn(ScoreCategory.CHANCE))
        assertEquals(2, decoded.turnsTaken)
    }

    @Test
    fun `a save missing a required field fails to decode rather than guessing`() {
        val state = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)))
        for (field in listOf("gameMode", "turnTimer", "phase", "players")) {
            val incomplete = JsonObject(GameStateJson.encode(state).toJsonObject().fields - field).toJson()

            assertFailsWith<IllegalArgumentException>("missing $field") { GameStateJson.decode(incomplete) }
        }
    }
}

private fun String.toJsonObject(): JsonObject = parseJson(this) as JsonObject
