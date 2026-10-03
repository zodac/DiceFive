package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer

class GameEngineTest {

    private val onePlayer = listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"))
    private val twoPlayers = listOf(
        PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"),
        PlayerConfig(slot = 2, type = PlayerType.AI, name = "Bot"),
    )

    @Test
    fun `newGame rejects an empty player list`() {
        assertFailsWith<IllegalArgumentException> { GameEngine.newGame(emptyList()) }
    }

    @Test
    fun `newGame defaults to no turn timer but carries a chosen one`() {
        assertEquals(TurnTimer.NONE, GameEngine.newGame(onePlayer).turnTimer)
        assertEquals(TurnTimer.SECONDS_30, GameEngine.newGame(onePlayer, turnTimer = TurnTimer.SECONDS_30).turnTimer)
    }

    @Test
    fun `rollDice decrements rolls remaining and marks the turn rolled`() {
        val state = GameEngine.newGame(onePlayer)

        val rolled = GameEngine.rollDice(state)

        assertEquals(2, rolled.rollsRemaining)
        assertEquals(TurnPhase.ROLLED, rolled.phase)
    }

    @Test
    fun `rollDice fails once no rolls remain`() {
        var state = GameEngine.newGame(onePlayer)
        repeat(3) { state = GameEngine.rollDice(state) }

        assertFailsWith<IllegalStateException> { GameEngine.rollDice(state) }
    }

    @Test
    fun `held dice keep their value across a re-roll`() {
        var state = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        state = GameEngine.toggleHold(state, dieIndex = 0)
        val heldValue = state.dice[0].value

        state = GameEngine.rollDice(state)

        assertEquals(heldValue, state.dice[0].value)
        assertTrue(state.dice[0].isHeld)
    }

    @Test
    fun `toggleHold before any roll fails`() {
        val state = GameEngine.newGame(onePlayer)

        assertFailsWith<IllegalStateException> { GameEngine.toggleHold(state, dieIndex = 0) }
    }

    @Test
    fun `toggleHold still works after the final roll`() {
        // Holding has no effect on a roll that won't happen after the last one, but there's no
        // reason to actually forbid it - see the comment on GameEngine.toggleHold.
        var state = GameEngine.newGame(onePlayer)
        repeat(3) { state = GameEngine.rollDice(state) }

        val result = GameEngine.toggleHold(state, dieIndex = 0)

        assertTrue(result.dice[0].isHeld)
    }

    @Test
    fun `commitScore before rolling fails`() {
        val state = GameEngine.newGame(onePlayer)

        assertFailsWith<IllegalStateException> { GameEngine.commitScore(state, ScoreCategory.CHANCE) }
    }

    @Test
    fun `commitScore rejects an already-filled category`() {
        var state = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        state = GameEngine.commitScore(state, ScoreCategory.CHANCE)
        // Second player's turn now (there is only one, so it's back to the same player) with a fresh scorecard slot.
        state = GameEngine.rollDice(state)

        assertFailsWith<IllegalStateException> { GameEngine.commitScore(state, ScoreCategory.CHANCE) }
    }

    @Test
    fun `commitScore advances to the next player and resets the turn`() {
        var state = GameEngine.rollDice(GameEngine.newGame(twoPlayers))

        state = GameEngine.commitScore(state, ScoreCategory.CHANCE)

        assertEquals(1, state.currentPlayerIndex)
        assertEquals(3, state.rollsRemaining)
        assertEquals(TurnPhase.AWAITING_ROLL, state.phase)
        assertTrue(state.dice.none { it.isHeld })
    }

    @Test
    fun `commitScore records the dice it was scored with as the player's lastRoll`() {
        var state = GameEngine.rollDice(GameEngine.newGame(twoPlayers))
        state = GameEngine.toggleHold(state, dieIndex = 0)
        val diceUsedToScore = state.dice

        state = GameEngine.commitScore(state, ScoreCategory.CHANCE)

        assertEquals(diceUsedToScore, state.players[0].lastRoll)
        // The next player's own dice are unaffected - and, having not yet finished a turn, still null.
        assertEquals(null, state.players[1].lastRoll)
    }

    @Test
    fun `commitScore records the box it scored in as the player's lastScoredCategory`() {
        var state = GameEngine.commitScore(GameEngine.rollDice(GameEngine.newGame(twoPlayers)), ScoreCategory.CHANCE)
        assertEquals(ScoreCategory.CHANCE, state.players[0].lastScoredCategory)
        assertEquals(null, state.players[1].lastScoredCategory)

        // Each turn replaces the last.
        state = GameEngine.commitScore(GameEngine.rollDice(state), ScoreCategory.CHANCE)
        state = GameEngine.commitScore(GameEngine.rollDice(state), ScoreCategory.FOUR_OF_A_KIND)
        assertEquals(ScoreCategory.FOUR_OF_A_KIND, state.players[0].lastScoredCategory)
    }

    @Test
    fun `rollDice counts each roll towards the rolling player only - across turns`() {
        var state = GameEngine.rollDice(GameEngine.newGame(twoPlayers))
        state = GameEngine.rollDice(state)
        state = GameEngine.commitScore(state, ScoreCategory.CHANCE)
        state = GameEngine.rollDice(state)

        assertEquals(listOf(2, 1), state.players.map { it.rollCount })

        state = GameEngine.commitScore(state, ScoreCategory.CHANCE)
        state = GameEngine.rollDice(state)

        assertEquals(listOf(3, 1), state.players.map { it.rollCount })
    }

    @Test
    fun `cycleDieValue advances only the target die to the next face`() {
        var state = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        state = GameEngine.toggleHold(state, dieIndex = 0)
        state = state.copy(dice = state.dice.mapIndexed { i, die -> if (i == 0) die.copy(value = 3) else die })
        val otherDiceBefore = state.dice.drop(1)
        val rollsBefore = state.rollsRemaining
        val phaseBefore = state.phase

        val result = GameEngine.cycleDieValue(state, dieIndex = 0)

        assertEquals(4, result.dice[0].value)
        assertTrue(result.dice[0].isHeld)
        assertEquals(otherDiceBefore, result.dice.drop(1))
        assertEquals(rollsBefore, result.rollsRemaining)
        assertEquals(phaseBefore, result.phase)
    }

    @Test
    fun `cycleDieValue wraps 6 back to 1`() {
        var state = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        state = state.copy(dice = state.dice.mapIndexed { i, die -> if (i == 0) die.copy(value = 6) else die })

        val result = GameEngine.cycleDieValue(state, dieIndex = 0)

        assertEquals(1, result.dice[0].value)
    }

    @Test
    fun `cycleDieValue ignores the normal rolls-remaining rule`() {
        var state = GameEngine.newGame(onePlayer)
        repeat(3) { state = GameEngine.rollDice(state) }
        val before = state.dice[0].value

        val result = GameEngine.cycleDieValue(state, dieIndex = 0)

        assertEquals(if (before >= 6) 1 else before + 1, result.dice[0].value)
    }

    @Test
    fun `game ends once every player's scorecard is full`() {
        val almostFullScorecard: Map<ScoreCategory, Int?> = GameMode.STANDARD.categories
            .associateWith { category -> if (category == ScoreCategory.CHANCE) null else 0 }

        val state = GameState(
            players = listOf(
                PlayerState(name = "Player 1", type = PlayerType.HUMAN, scorecard = almostFullScorecard),
            ),
            dice = List(5) { Die(value = 4) },
            rollsRemaining = 1,
            phase = TurnPhase.ROLLED,
        )

        val result = GameEngine.commitScore(state, ScoreCategory.CHANCE)

        assertTrue(result.isGameOver)
    }

    // ---- Game modes ------------------------------------------------------------------------------

    @Test
    fun `newGame carries the chosen mode to the game and every player's scorecard`() {
        val state = GameEngine.newGame(twoPlayers, GameMode.TRICOLOUR)

        assertEquals(GameMode.TRICOLOUR, state.gameMode)
        assertTrue(state.players.all { it.gameMode == GameMode.TRICOLOUR })
        assertTrue(state.players.all { it.scorecard.keys.toList() == GameMode.TRICOLOUR.categories })
        assertEquals(GameMode.TRICOLOUR.diceCount, state.dice.size)
        assertEquals(GameMode.TRICOLOUR.rollsPerTurn, state.rollsRemaining)
    }

    @Test
    fun `Tricolour rolls give every rolled die a colour - and Standard rolls never do`() {
        val random = kotlin.random.Random(42)
        repeat(20) {
            val tricolour = GameEngine.rollDice(GameEngine.newGame(onePlayer, GameMode.TRICOLOUR), random)
            val standard = GameEngine.rollDice(GameEngine.newGame(onePlayer, GameMode.STANDARD), random)

            assertTrue(tricolour.dice.all { it.colour in GameMode.TRICOLOUR.dieColours && it.value in 1..6 })
            assertTrue(standard.dice.all { it.colour == null })
        }
    }

    @Test
    fun `a held die keeps its colour through a reroll`() {
        val state = GameEngine.newGame(onePlayer, GameMode.TRICOLOUR).copy(
            dice = List(5) { Die(value = 2, colour = DieColour.BLUE, isHeld = it == 0) },
            phase = TurnPhase.ROLLED,
        )

        val rolled = GameEngine.rollDice(state, kotlin.random.Random(7))

        assertEquals(Die(value = 2, colour = DieColour.BLUE, isHeld = true), rolled.dice[0])
    }

    @Test
    fun `superuser cycling in Tricolour runs 1 to 6 within a colour - then on to the next colour`() {
        var state = GameEngine.newGame(onePlayer, GameMode.TRICOLOUR).copy(
            dice = List(5) { Die(value = 1, colour = DieColour.RED, isHeld = true) },
            phase = TurnPhase.ROLLED,
        )
        val seen = mutableListOf(state.dice[0].value to state.dice[0].colour)
        repeat(18) {
            state = GameEngine.cycleDieValue(state, dieIndex = 0)
            seen += state.dice[0].value to state.dice[0].colour
        }

        val expected = listOf(DieColour.RED, DieColour.YELLOW, DieColour.BLUE)
            .flatMap { colour -> (1..6).map { it to colour } } + (1 to DieColour.RED)
        assertEquals<List<Pair<Int, DieColour?>>>(expected, seen)
        assertTrue(state.dice.drop(1).all { it == Die(value = 1, colour = DieColour.RED, isHeld = true) })
    }

    @Test
    fun `a Tricolour game ends once all 17 of its boxes are filled`() {
        val mode = GameMode.TRICOLOUR
        val almostFull = mode.categories.associateWith { category -> if (category == ScoreCategory.COLOURED_HOUSE) null else 0 }
        val state = GameEngine.newGame(onePlayer, mode).let {
            it.copy(
                players = listOf(it.players.single().copy(scorecard = almostFull)),
                dice = List(5) { index -> Die(value = index + 1, colour = DieColour.RED) },
                phase = TurnPhase.ROLLED,
            )
        }

        assertTrue(GameEngine.commitScore(state, ScoreCategory.COLOURED_HOUSE).isGameOver)
    }
}
