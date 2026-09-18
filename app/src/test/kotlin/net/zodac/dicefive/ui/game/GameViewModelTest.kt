package net.zodac.dicefive.ui.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startGame builds a game with the configured players`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)

        viewModel.startGame()

        val state = viewModel.game.value
        assertNotNull(state)
        assertEquals(2, state!!.players.size)
        assertEquals(PlayerType.HUMAN, state.players[0].type)
        assertEquals(PlayerType.AI, state.players[1].type)
    }

    @Test
    fun `human actions before a game has started are ignored`() {
        val viewModel = GameViewModel()

        viewModel.rollDice()

        assertNull(viewModel.game.value)
    }

    @Test
    fun `rolling and scoring as the human player updates the game state`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        val rolled = viewModel.game.value!!
        assertEquals(TurnPhase.ROLLED, rolled.phase)
        assertEquals(2, rolled.rollsRemaining)

        viewModel.commitScore(ScoreCategory.CHANCE)

        val afterScore = viewModel.game.value!!
        assertEquals(1, afterScore.players.single().scorecard.values.count { it != null })
    }

    @Test
    fun `AI players complete their turn automatically`() = runTest(testDispatcher) {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)

        advanceUntilIdle()

        val state = viewModel.game.value!!
        assertEquals(0, state.currentPlayerIndex)
        assertTrue(state.players[1].scorecard.values.any { it != null })
    }

    @Test
    fun `undo is unavailable until a human action has happened`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `undo reverts the most recent roll`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        assertTrue(viewModel.canUndo.value)

        viewModel.undo()

        val state = viewModel.game.value!!
        assertEquals(TurnPhase.AWAITING_ROLL, state.phase)
        assertEquals(3, state.rollsRemaining)
        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `undo reverts a scored category`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        viewModel.rollDice()

        viewModel.commitScore(ScoreCategory.CHANCE)
        assertEquals(1, viewModel.game.value!!.players.single().scorecard.values.count { it != null })

        viewModel.undo()

        val state = viewModel.game.value!!
        assertEquals(TurnPhase.ROLLED, state.phase)
        assertEquals(0, state.players.single().scorecard.values.count { it != null })
    }

    @Test
    fun `undo is unavailable once an AI player has acted`() = runTest(testDispatcher) {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        advanceUntilIdle()

        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `resumeGame returns false when no repository is configured`() = runTest(testDispatcher) {
        val viewModel = GameViewModel()

        assertFalse(viewModel.resumeGame())
        assertNull(viewModel.game.value)
    }
}
