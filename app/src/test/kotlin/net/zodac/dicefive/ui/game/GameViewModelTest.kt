package net.zodac.dicefive.ui.game

import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.BuildConfig
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
    fun `three consecutive AI players all take their turn without further human input`() = runTest(testDispatcher) {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(4)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.setPlayerType(3, PlayerType.AI)
        viewModel.setPlayerType(4, PlayerType.AI)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)

        advanceUntilIdle()

        val state = viewModel.game.value!!
        assertEquals(0, state.currentPlayerIndex)
        assertTrue("Player 2 (AI) never took a turn", state.players[1].scorecard.values.any { it != null })
        assertTrue("Player 3 (AI) never took a turn", state.players[2].scorecard.values.any { it != null })
        assertTrue("Player 4 (AI) never took a turn", state.players[3].scorecard.values.any { it != null })
    }

    @Test
    fun `AI turns alternating with human turns each hand back correctly`() = runTest(testDispatcher) {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(4)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.setPlayerType(4, PlayerType.AI)
        // Slots 1 and 3 default to Human.
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        advanceUntilIdle()

        val afterPlayerTwo = viewModel.game.value!!
        assertEquals(2, afterPlayerTwo.currentPlayerIndex)
        assertTrue("Player 2 (AI) never took a turn", afterPlayerTwo.players[1].scorecard.values.any { it != null })
        assertTrue("Player 3 (human) should be waiting for input, not mid-AI-turn", afterPlayerTwo.rollsRemaining == 3)

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        advanceUntilIdle()

        val afterPlayerFour = viewModel.game.value!!
        assertEquals(0, afterPlayerFour.currentPlayerIndex)
        assertTrue("Player 4 (AI) never took a turn", afterPlayerFour.players[3].scorecard.values.any { it != null })
    }

    @Test
    fun `two consecutive AI players hand back to the next human correctly`() = runTest(testDispatcher) {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(4)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.setPlayerType(3, PlayerType.AI)
        // Slots 1 and 4 default to Human.
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        advanceUntilIdle()

        val afterPlayerThree = viewModel.game.value!!
        assertEquals(3, afterPlayerThree.currentPlayerIndex)
        assertTrue("Player 2 (AI) never took a turn", afterPlayerThree.players[1].scorecard.values.any { it != null })
        assertTrue("Player 3 (AI) never took a turn", afterPlayerThree.players[2].scorecard.values.any { it != null })
        assertEquals("Player 4 (human) should be waiting for input", 3, afterPlayerThree.rollsRemaining)

        // Player 4 (human) takes their turn; nothing should auto-advance beyond wrapping to Player 1.
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        advanceUntilIdle()

        val afterPlayerFour = viewModel.game.value!!
        assertEquals(0, afterPlayerFour.currentPlayerIndex)
        assertEquals(3, afterPlayerFour.rollsRemaining)
    }

    @Test
    fun `AI skips the remaining rolls once a hold decision already keeps every die`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(random = FixedValueRandom(6))
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.setPlayerDifficulty(2, Difficulty.HARD)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        val timeBeforeAiTurn = testDispatcher.scheduler.currentTime
        advanceUntilIdle()

        // Every roll lands all sixes, so Hard's very first hold decision already keeps all five
        // dice (a Yahtzee can't be improved by rerolling). The two "remaining" rolls that would
        // otherwise follow are pure no-ops - GameEngine.rollDice skips held dice - and should be
        // skipped rather than sitting through their delay for nothing: a full 3-roll AI turn takes
        // 4 delay steps (roll, roll, roll, score); this one should take only 2 (roll, score).
        val aiStepDelayMs = 600L // matches GameViewModel.AI_STEP_DELAY_MS, which is private to it
        assertEquals(2 * aiStepDelayMs, testDispatcher.scheduler.currentTime - timeBeforeAiTurn)
        assertEquals(50, viewModel.game.value!!.players[1].scorecard[ScoreCategory.FIVE_OF_A_KIND])
    }

    @Test
    fun `undo is unavailable until a human action has happened`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `rolling is not undoable`() {
        // Rolling has no scoring consequence of its own - only committing a category does (see
        // the test below) - so there's nothing for Undo to do about a roll.
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()

        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `holding a die is not undoable`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        viewModel.rollDice()

        viewModel.toggleHold(0)

        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `committing a score is undoable`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        viewModel.rollDice()

        viewModel.commitScore(ScoreCategory.CHANCE)

        assertEquals(1, viewModel.game.value!!.players.single().scorecard.values.count { it != null })
        assertTrue(viewModel.canUndo.value)

        viewModel.undo()

        val state = viewModel.game.value!!
        assertEquals(TurnPhase.ROLLED, state.phase)
        assertEquals(0, state.players.single().scorecard.values.count { it != null })
        assertFalse(viewModel.canUndo.value)
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

    @Test
    fun `holding and unholding all five dice in order activates superuser mode`() = runTest(testDispatcher) {
        // The whole feature is gated on BuildConfig.DEBUG (never available in a release build -
        // see GameViewModel.trackSuperuserSequence); skip rather than fail under a variant where
        // that's false, since a release variant correctly refusing to activate isn't a test failure.
        assumeTrue(BuildConfig.DEBUG)
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        viewModel.rollDice()

        var toastMessage: String? = null
        val collectJob = launch { toastMessage = viewModel.toastMessages.first() }

        for (dieIndex in 0..4) {
            viewModel.toggleHold(dieIndex)
            viewModel.toggleHold(dieIndex)
        }
        advanceUntilIdle()

        assertTrue(viewModel.superuserModeActive.value)
        assertEquals("Superuser mode activated!", toastMessage)
        collectJob.cancel()
    }

    @Test
    fun `toggling dice out of order does not activate superuser mode`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        viewModel.rollDice()

        viewModel.toggleHold(0)
        viewModel.toggleHold(0)
        viewModel.toggleHold(2) // skips die 1 - breaks the required order
        viewModel.toggleHold(2)
        viewModel.toggleHold(1)
        viewModel.toggleHold(1)
        viewModel.toggleHold(3)
        viewModel.toggleHold(3)
        viewModel.toggleHold(4)
        viewModel.toggleHold(4)

        assertFalse(viewModel.superuserModeActive.value)
    }

    @Test
    fun `superuser sequence activates on a later turn, not just the first`() {
        assumeTrue(BuildConfig.DEBUG)
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        viewModel.rollDice() // the same (only) player's second turn

        for (dieIndex in 0..4) {
            viewModel.toggleHold(dieIndex)
            viewModel.toggleHold(dieIndex)
        }

        assertTrue(viewModel.superuserModeActive.value)
    }

    @Test
    fun `cycleHeldDieValue does nothing before superuser mode is activated`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        viewModel.rollDice()
        viewModel.toggleHold(0)
        val before = viewModel.game.value!!

        viewModel.cycleHeldDieValue(0)

        assertEquals(before, viewModel.game.value)
    }

    @Test
    fun `cycleHeldDieValue advances the die's face once superuser mode is active`() = runTest(testDispatcher) {
        // See the comment on the activation test above - this needs the feature reachable at all.
        assumeTrue(BuildConfig.DEBUG)
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        viewModel.rollDice()

        for (dieIndex in 0..4) {
            viewModel.toggleHold(dieIndex)
            viewModel.toggleHold(dieIndex)
        }
        assertTrue(viewModel.superuserModeActive.value)

        viewModel.toggleHold(0) // hold die 0 so it's eligible to cycle
        val before = viewModel.game.value!!.dice[0].value

        viewModel.cycleHeldDieValue(0)

        val after = viewModel.game.value!!.dice[0]
        assertEquals(if (before >= 6) 1 else before + 1, after.value)
        assertTrue(after.isHeld)
    }
}

/** Dice that always land on [value] - lets a test force a specific roll deterministically. */
private class FixedValueRandom(private val value: Int) : Random() {
    override fun nextBits(bitCount: Int): Int = 0
    override fun nextInt(from: Int, until: Int): Int = value
}
