package net.zodac.dicefive.data.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.data.JsonArray
import net.zodac.dicefive.data.JsonNull
import net.zodac.dicefive.data.JsonNumber
import net.zodac.dicefive.data.JsonObject
import net.zodac.dicefive.data.JsonParseException
import net.zodac.dicefive.data.parseJson
import net.zodac.dicefive.data.toJson
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.UnluckyDice
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
    fun `round trips the roll modifiers and a player's stored rolls`() {
        val state = GameState(
            rollModifiers = RollModifiers(rollsPerTurn = 6, storedRolls = true, storedRollsMax = 12),
            rollsRemaining = 134,
            turnRolls = 140,
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, storedRolls = 128, rollsModified = true)),
        )

        assertEquals(state, GameStateJson.decode(GameStateJson.encode(state)))
    }

    @Test
    fun `a save from before roll modifiers loads with them off and the full allowance`() {
        val state = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)), rollsRemaining = 2)
        val old = GameStateJson.encode(state)
            .replace(Regex("\"turnRolls\":[^,}]*,?"), "")

        val decoded = GameStateJson.decode(old)

        assertEquals(RollModifiers(), decoded.rollModifiers)
        assertEquals(3, decoded.turnRolls)
        assertEquals(0, decoded.players.single().storedRolls)
        assertFalse(decoded.players.single().rollsModified)
    }

    @Test
    fun `round trips a player's colour and gives an old save each seat's default`() {
        val state = GameState(
            players = listOf(
                PlayerState(name = "A", type = PlayerType.HUMAN, colour = PlayerColour.LIME),
                PlayerState(name = "B", type = PlayerType.HUMAN, colour = PlayerColour.PINK),
            ),
        )
        assertEquals(listOf(PlayerColour.LIME, PlayerColour.PINK), GameStateJson.decode(GameStateJson.encode(state)).players.map { it.colour })

        val saved = GameStateJson.encode(state).toJsonObject()
        val players = (saved.fields.getValue("players") as JsonArray).items.map { JsonObject((it as JsonObject).fields - "colour") }
        val older = JsonObject(saved.fields + ("players" to JsonArray(players))).toJson()
        assertEquals(listOf(PlayerColour.CYAN, PlayerColour.GREEN), GameStateJson.decode(older).players.map { it.colour })
    }

    @Test
    fun `round trips whether a player's first 5x has been flashed and loads an old save as not yet`() {
        val flashed = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, fiveOfAKindFlashed = true)))
        val fresh = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)))

        assertTrue(GameStateJson.decode(GameStateJson.encode(flashed)).players.single().fiveOfAKindFlashed)
        assertFalse(GameStateJson.decode(GameStateJson.encode(fresh)).players.single().fiveOfAKindFlashed)
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

    @Test
    fun `round trips Extended Scores and a card with its boxes`() {
        val player = PlayerState(name = "Player 1", type = PlayerType.HUMAN, extendedScores = true)
        val state = GameState(
            extendedScores = true,
            players = listOf(
                player.copy(
                    scorecard = player.scorecard + mapOf(ScoreCategory.TWO_PAIR to listOf(22), ScoreCategory.ODDS to listOf(0)),
                    lastScoredCategory = ScoreCategory.TWO_PAIR,
                ),
            ),
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
        assertEquals(16, decoded.players.single().scorecard.size)
    }

    @Test
    fun `a save without Extended Scores loads with it off and the mode's own card`() {
        val state = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)))
        val saved = GameStateJson.encode(state)

        assertFalse("extendedScores" in saved)
        val decoded = GameStateJson.decode(saved)
        assertFalse(decoded.extendedScores)
        assertEquals(13, decoded.players.single().scorecard.size)
    }

    @Test
    fun `round trips Unlucky Dice and a locked die`() {
        val state = GameState(
            unluckyDice = UnluckyDice(oddsPercent = 30, maxDice = 2),
            dice = List(5) { Die(value = it + 1, isUnlucky = it == 3) },
            players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)),
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
        assertTrue(decoded.dice[3].isUnlucky)
    }

    @Test
    fun `a save without Unlucky Dice loads with it off and no locked die`() {
        val state = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)))
        val saved = GameStateJson.encode(state)

        assertFalse("unluckyOdds" in saved)
        assertFalse("isUnlucky" in saved)
        val decoded = GameStateJson.decode(saved)
        assertEquals(null, decoded.unluckyDice)
        assertTrue(decoded.dice.none { it.isUnlucky })
    }

    @Test
    fun `round trips a Quickfire game - the boxes switched off on the game and every card`() {
        val players = listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"), PlayerConfig(slot = 2, type = PlayerType.AI, name = "Bot"))
        val started = GameEngine.newGame(players, GameMode.QUICKFIRE, random = Random(4))
        val open = started.players[0].categories.first { started.players[0].isOpen(it) }
        val state = GameEngine.commitScore(
            started.copy(phase = TurnPhase.ROLLED, dice = List(5) { Die(value = 2) }),
            open,
        )

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
        assertEquals(7, decoded.disabledCategories.size)
        assertTrue(ScoreCategory.FIVE_OF_A_KIND in decoded.disabledCategories)
        assertTrue(decoded.players.all { it.disabledCategories == decoded.disabledCategories })
    }

    @Test
    fun `a Quickfire save from before the disabled boxes - the old one roll mode - has nothing to resume`() {
        val state = GameEngine.newGame(
            listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1")),
            GameMode.QUICKFIRE,
        )
        val saved = GameStateJson.encode(state).toJsonObject()
        val older = JsonObject(saved.fields - "disabledCategories").toJson()

        assertFailsWith<JsonParseException> { GameStateJson.decode(older) }
    }

    @Test
    fun `a mode that switches nothing off at random writes no disabled boxes - and loads with none`() {
        val state = GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)))
        val saved = GameStateJson.encode(state)

        assertFalse("disabledCategories" in saved)
        assertEquals(emptySet(), GameStateJson.decode(saved).disabledCategories)
    }

    @Test
    fun `round trips a Hit List game - its targets on the game and every card - with a score in the Alibi`() {
        var state = GameEngine.newGame(
            listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"), PlayerConfig(slot = 2, type = PlayerType.AI, name = "Bot")),
            GameMode.HIT_LIST,
            random = Random(9),
        )
        state = GameEngine.commitScore(state.copy(dice = List(5) { Die(value = 6) }, phase = TurnPhase.ROLLED), ScoreCategory.ALIBI)

        val decoded = GameStateJson.decode(GameStateJson.encode(state))

        assertEquals(state, decoded)
        assertTrue(decoded.hitList.values.any { target -> target.places.any { it == null } })
        assertTrue(decoded.players.all { it.hitList == state.hitList })
    }

    @Test
    fun `a Hit List save without its targets has nothing to resume - and other modes write none`() {
        val state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1")), GameMode.HIT_LIST)
        val saved = GameStateJson.encode(state).toJsonObject()

        assertFailsWith<JsonParseException> { GameStateJson.decode(JsonObject(saved.fields - "hitList").toJson()) }
        assertFalse("hitList" in GameStateJson.encode(GameState(players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)))))
    }
}

private fun String.toJsonObject(): JsonObject = parseJson(this) as JsonObject
