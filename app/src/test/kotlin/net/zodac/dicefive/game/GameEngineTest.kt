package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private val onePlayer = listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"))
    private val twoPlayers = listOf(
        PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"),
        PlayerConfig(slot = 2, type = PlayerType.AI, name = "Bot"),
    )

    @Test
    fun `newGame rejects an empty player list`() {
        assertThrows(IllegalArgumentException::class.java) { GameEngine.newGame(emptyList()) }
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

        assertThrows(IllegalStateException::class.java) { GameEngine.rollDice(state) }
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

        assertThrows(IllegalStateException::class.java) { GameEngine.toggleHold(state, dieIndex = 0) }
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

        assertThrows(IllegalStateException::class.java) { GameEngine.commitScore(state, ScoreCategory.CHANCE) }
    }

    @Test
    fun `commitScore rejects an already-filled category`() {
        var state = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        state = GameEngine.commitScore(state, ScoreCategory.CHANCE)
        // Second player's turn now (there is only one, so it's back to the same player) with a fresh scorecard slot.
        state = GameEngine.rollDice(state)

        assertThrows(IllegalStateException::class.java) { GameEngine.commitScore(state, ScoreCategory.CHANCE) }
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
        val almostFullScorecard: Map<ScoreCategory, Int?> = ScoreCategory.entries
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
}
