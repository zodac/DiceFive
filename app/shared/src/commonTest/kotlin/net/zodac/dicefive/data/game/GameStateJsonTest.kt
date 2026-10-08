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
import net.zodac.dicefive.data.JsonValue
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
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.oneScoreEach

class GameStateJsonTest {

    private val human = PlayerState(name = "Player 1", type = PlayerType.HUMAN)

    /** [state] encoded and decoded again - which must give back exactly [state]. */
    private fun roundTripped(state: GameState): GameState = GameStateJson.decode(GameStateJson.encode(state)).also { assertEquals(state, it) }

    /** [state] saved as an older version did: without [field], or with [replaced] standing in for it, on the game or its first player. */
    private fun savedWithout(state: GameState, field: String, onPlayer: Boolean = false, replaced: JsonValue? = null): String {
        val saved = GameStateJson.encode(state).toJsonObject()
        if (!onPlayer) return JsonObject(saved.fields - field).toJson()
        val players = (saved["players"] as JsonArray).items.map { it as JsonObject }
        val first = JsonObject(players[0].fields - field + listOfNotNull(replaced?.let { field to it }))
        return JsonObject(saved.fields + ("players" to JsonArray(listOf(first) + players.drop(1)))).toJson()
    }

    @Test
    fun `every part of a game round trips - turn - dice - timer - scorecards - each player's history and settings`() {
        // A fresh game with open scorecards, part-way through a CPU's turn.
        roundTripped(
            GameState(
                gameMode = GameMode.STANDARD,
                players = listOf(human, PlayerState(name = "Bot", type = PlayerType.AI, difficulty = Difficulty.HARD)),
                currentPlayerIndex = 1,
                dice = List(5) { Die(value = it % 6 + 1, isHeld = it % 2 == 0) },
                rollsRemaining = 2,
                phase = TurnPhase.ROLLED,
                isGameOver = false,
            ),
        )

        // A non-default turn timer and the seconds left on it.
        val timed = roundTripped(GameState(turnTimer = TurnTimer.SECONDS_60, players = listOf(human), turnSecondsLeft = 23))
        assertEquals(TurnTimer.SECONDS_60, timed.turnTimer)
        assertEquals(23, timed.turnSecondsLeft)

        // A partially filled scorecard - zero scores included - and a finished game.
        val partial = GameMode.STANDARD.categories.associateWith { category ->
            when (category) {
                ScoreCategory.ONES -> 3
                ScoreCategory.FULL_HOUSE -> 0
                else -> null
            }
        }
        val decoded = roundTripped(GameState(players = listOf(human.copy(scorecard = oneScoreEach(partial), fiveOfAKindBonusCount = 2)))).players.single()
        assertEquals(3, decoded.scoresIn(ScoreCategory.ONES).singleOrNull())
        assertEquals(0, decoded.scoresIn(ScoreCategory.FULL_HOUSE).singleOrNull())
        assertEquals(null, decoded.scoresIn(ScoreCategory.CHANCE).singleOrNull())
        roundTripped(GameState(players = listOf(human.copy(scorecard = GameMode.STANDARD.categories.associateWith { listOf(10) })), isGameOver = true))

        // Each player's last roll (held state included), last scored box, roll count, colour and first-5x flash.
        val lastRoll = List(5) { Die(value = it % 6 + 1, isHeld = it % 2 == 0) }
        assertEquals(lastRoll, roundTripped(GameState(players = listOf(human.copy(lastRoll = lastRoll)))).players.single().lastRoll)
        roundTripped(GameState(players = listOf(human.copy(lastScoredCategory = ScoreCategory.FULL_HOUSE))))
        roundTripped(GameState(players = listOf(human.copy(rollCount = 27), PlayerState(name = "Player 2", type = PlayerType.AI, rollCount = 25))))
        val coloured = GameState(players = listOf(PlayerState(name = "A", type = PlayerType.HUMAN, colour = PlayerColour.LIME), PlayerState(name = "B", type = PlayerType.HUMAN, colour = PlayerColour.PINK)))
        assertEquals(listOf(PlayerColour.LIME, PlayerColour.PINK), roundTripped(coloured).players.map { it.colour })
        assertTrue(roundTripped(GameState(players = listOf(human.copy(fiveOfAKindFlashed = true)))).players.single().fiveOfAKindFlashed)
        assertFalse(roundTripped(GameState(players = listOf(human))).players.single().fiveOfAKindFlashed)

        // The roll modifiers and a player's stored rolls.
        roundTripped(
            GameState(
                rollModifiers = RollModifiers(rollsPerTurn = 6, storedRolls = true, storedRollsMax = 12),
                rollsRemaining = 134,
                turnRolls = 140,
                players = listOf(human.copy(storedRolls = 128, rollsModified = true)),
            ),
        )
    }

    @Test
    fun `every mode and modifier round trips - with the dice - boxes and draws each one adds`() {
        // Tricolour: each die's colour and the colour boxes.
        val tricolour = GameMode.TRICOLOUR
        val colourCard = tricolour.categories.associateWith { category ->
            when (category) {
                ScoreCategory.REDS -> 40
                ScoreCategory.COLOURED_HOUSE -> 0
                else -> null
            }
        }
        val tricolourGame = roundTripped(
            GameState(
                gameMode = tricolour,
                players = listOf(human.copy(gameMode = tricolour, scorecard = oneScoreEach(colourCard))),
                dice = listOf(
                    Die(value = 1, colour = DieColour.RED),
                    Die(value = 2, colour = DieColour.YELLOW, isHeld = true),
                    Die(value = 3, colour = DieColour.BLUE),
                    Die(value = 4, colour = DieColour.RED),
                    Die(value = 5, colour = DieColour.BLUE),
                ),
                phase = TurnPhase.ROLLED,
            ),
        )
        assertEquals(GameMode.TRICOLOUR, tricolourGame.players.single().gameMode)

        // Stud: which hold slot each held die is in.
        val stud = roundTripped(
            GameState(
                gameMode = GameMode.STUD,
                players = listOf(human.copy(gameMode = GameMode.STUD)),
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
            ),
        )
        assertEquals(listOf(6, 6, 6), stud.scoringDice.map { it.value })

        // Third Wind: every slot of every box in the order it was scored.
        val thirdWindPlayer = PlayerState(name = "Player 1", type = PlayerType.HUMAN, gameMode = GameMode.THIRD_WIND)
        val thirdWind = roundTripped(
            GameState(
                gameMode = GameMode.THIRD_WIND,
                players = listOf(
                    thirdWindPlayer.copy(
                        scorecard = thirdWindPlayer.scorecard + mapOf(
                            ScoreCategory.FIVES to listOf(15, 10, 20),
                            ScoreCategory.CHANCE to listOf(22, 0),
                            ScoreCategory.FIVE_OF_A_KIND to listOf(50),
                        ),
                    ),
                ),
            ),
        ).players.single()
        assertEquals(listOf(22, 0), thirdWind.scoresIn(ScoreCategory.CHANCE))
        assertEquals(emptyList<Int>(), thirdWind.scoresIn(ScoreCategory.ONES))

        // Extended Scores, and a card with its boxes.
        val extendedPlayer = PlayerState(name = "Player 1", type = PlayerType.HUMAN, extendedScores = true)
        val extended = roundTripped(
            GameState(
                extendedScores = true,
                players = listOf(
                    extendedPlayer.copy(
                        scorecard = extendedPlayer.scorecard + mapOf(ScoreCategory.TWO_PAIR to listOf(22), ScoreCategory.ODDS to listOf(0)),
                        lastScoredCategory = ScoreCategory.TWO_PAIR,
                    ),
                ),
            ),
        )
        assertEquals(16, extended.players.single().scorecard.size)

        // Unlucky Dice, and a locked die.
        val unlucky = roundTripped(
            GameState(unluckyDice = UnluckyDice(oddsPercent = 30, maxDice = 2), dice = List(5) { Die(value = it + 1, isUnlucky = it == 3) }, players = listOf(human)),
        )
        assertTrue(unlucky.dice[3].isUnlucky)

        // Quickfire: the boxes switched off, on the game and every card.
        val twoSeats = listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"), PlayerConfig(slot = 2, type = PlayerType.AI, name = "Bot"))
        val started = GameEngine.newGame(twoSeats, GameMode.QUICKFIRE, random = Random(4))
        val open = started.players[0].categories.first { started.players[0].isOpen(it) }
        val quickfire = roundTripped(GameEngine.commitScore(started.copy(phase = TurnPhase.ROLLED, dice = List(5) { Die(value = 2) }), open))
        assertEquals(7, quickfire.disabledCategories.size)
        assertTrue(ScoreCategory.FIVE_OF_A_KIND in quickfire.disabledCategories)
        assertTrue(quickfire.players.all { it.disabledCategories == quickfire.disabledCategories })

        // Hit List: its targets on the game and every card, with a score in the Alibi.
        val hitList = GameEngine.newGame(twoSeats, GameMode.HIT_LIST, random = Random(9))
        val dealt = roundTripped(GameEngine.commitScore(hitList.copy(dice = List(5) { Die(value = 6) }, phase = TurnPhase.ROLLED), ScoreCategory.ALIBI))
        assertTrue(dealt.hitList.values.any { target -> target.places.any { it == null } })
        assertTrue(dealt.players.all { it.hitList == dealt.hitList })
    }

    @Test
    fun `a save from before a field was kept loads with its default - and a game without a modifier writes none of its fields`() {
        val plain = GameState(players = listOf(human), rollsRemaining = 2)

        // Roll modifiers off and the full allowance.
        val noRollModifiers = GameStateJson.decode(GameStateJson.encode(plain).replace(Regex("\"turnRolls\":[^,}]*,?"), ""))
        assertEquals(RollModifiers(), noRollModifiers.rollModifiers)
        assertEquals(3, noRollModifiers.turnRolls)
        assertEquals(0, noRollModifiers.players.single().storedRolls)
        assertFalse(noRollModifiers.players.single().rollsModified)

        // A full timer, each seat's default colour, no last roll (no finished turn yet), no last scored box, no rolls.
        assertEquals(null, GameStateJson.decode(savedWithout(plain, "turnSecondsLeft")).turnSecondsLeft)
        val twoColours = GameState(players = listOf(PlayerState(name = "A", type = PlayerType.HUMAN, colour = PlayerColour.LIME), PlayerState(name = "B", type = PlayerType.HUMAN, colour = PlayerColour.PINK)))
        val saved = GameStateJson.encode(twoColours).toJsonObject()
        val uncoloured = (saved.fields.getValue("players") as JsonArray).items.map { JsonObject((it as JsonObject).fields - "colour") }
        assertEquals(listOf(PlayerColour.CYAN, PlayerColour.GREEN), GameStateJson.decode(JsonObject(saved.fields + ("players" to JsonArray(uncoloured))).toJson()).players.map { it.colour })
        assertEquals(null, GameStateJson.decode(savedWithout(plain, "lastRoll", onPlayer = true)).players.single().lastRoll)
        val scored = GameState(players = listOf(human.copy(lastScoredCategory = ScoreCategory.FULL_HOUSE, rollCount = 12)))
        assertEquals(null, GameStateJson.decode(savedWithout(scored, "lastScoredCategory", onPlayer = true)).players.single().lastScoredCategory)
        assertEquals(0, GameStateJson.decode(savedWithout(scored, "rollCount", onPlayer = true)).players.single().rollCount)

        // From before a box could hold several scores: a number per filled box and null per open one.
        val oldScorecard = JsonObject(
            GameMode.STANDARD.categories.associate { category ->
                category.name to when (category) {
                    ScoreCategory.ONES -> JsonNumber("3")
                    ScoreCategory.FULL_HOUSE -> JsonNumber("0")
                    else -> JsonNull
                }
            },
        )
        val singleScores = GameStateJson.decode(savedWithout(plain, "scorecard", onPlayer = true, replaced = oldScorecard)).players.single()
        assertEquals(listOf(3), singleScores.scoresIn(ScoreCategory.ONES))
        assertEquals(listOf(0), singleScores.scoresIn(ScoreCategory.FULL_HOUSE))
        assertEquals(emptyList<Int>(), singleScores.scoresIn(ScoreCategory.CHANCE))
        assertEquals(2, singleScores.turnsTaken)

        // A plain game writes no Extended Scores, Unlucky Dice, switched-off boxes or Hit List - and loads with none.
        val written = GameStateJson.encode(plain)
        for (field in listOf("extendedScores", "unluckyOdds", "isUnlucky", "disabledCategories", "hitList")) assertFalse(field in written, "$field written")
        val loaded = GameStateJson.decode(written)
        assertFalse(loaded.extendedScores)
        assertEquals(13, loaded.players.single().scorecard.size)
        assertEquals(null, loaded.unluckyDice)
        assertTrue(loaded.dice.none { it.isUnlucky })
        assertEquals(emptySet(), loaded.disabledCategories)
    }

    @Test
    fun `a save that can't be resumed fails to decode rather than guessing - a required field or a mode's draw - missing`() {
        val plain = GameState(players = listOf(human))
        for (field in listOf("gameMode", "turnTimer", "phase", "players")) {
            assertFailsWith<IllegalArgumentException>("missing $field") { GameStateJson.decode(savedWithout(plain, field)) }
        }

        // A Quickfire save from before the disabled boxes - the old one roll mode - and a Hit List one without its targets.
        val solo = listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"))
        assertFailsWith<JsonParseException> { GameStateJson.decode(savedWithout(GameEngine.newGame(solo, GameMode.QUICKFIRE), "disabledCategories")) }
        assertFailsWith<JsonParseException> { GameStateJson.decode(savedWithout(GameEngine.newGame(solo, GameMode.HIT_LIST), "hitList")) }
    }
}

private fun String.toJsonObject(): JsonObject = parseJson(this) as JsonObject
