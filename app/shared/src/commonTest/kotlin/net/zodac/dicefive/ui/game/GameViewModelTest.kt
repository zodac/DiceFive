package net.zodac.dicefive.ui.game

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startGame plays the game mode picked on the setup form - Standard by default`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        assertEquals(GameMode.STANDARD, viewModel.game.value?.gameMode)

        viewModel.setGameMode(GameMode.TRICOLOUR)
        viewModel.startGame()
        viewModel.rollDice()

        val state = viewModel.game.value!!
        assertEquals(GameMode.TRICOLOUR, state.gameMode)
        assertEquals(GameMode.TRICOLOUR.categories, state.players.single().scorecard.keys.toList())
        assertTrue(state.dice.all { it.colour != null })
    }

    @Test
    fun `Quickfire plays one roll a turn on its own 10 second timer - whatever the form's timer says`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.setTurnTimer(TurnTimer.SECONDS_60)
        viewModel.setGameMode(GameMode.QUICKFIRE)
        viewModel.startGame()

        assertEquals(10, viewModel.turnSecondsRemaining.value)
        // The game doesn't carry the overridden pick, but the form still remembers it.
        assertEquals(TurnTimer.NONE, viewModel.game.value!!.turnTimer)
        assertEquals(TurnTimer.SECONDS_60, viewModel.setup.value.turnTimer)

        viewModel.rollDice()
        assertEquals(TurnPhase.ROLLED, viewModel.game.value!!.phase)
        assertEquals(0, viewModel.game.value!!.rollsRemaining)
    }

    @Test
    fun `a Quickfire turn left to run out is forfeited after 10 seconds`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.setGameMode(GameMode.QUICKFIRE)
        viewModel.startGame()

        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(1, viewModel.game.value!!.players.single().scorecard.values.count { it != null })
        assertEquals(10, viewModel.turnSecondsRemaining.value)
    }

    @Test
    fun `a Quickfire timeout scores the lowest-scoring category - not the first open one`() = runTest(testDispatcher) {
        // Five 1s: Ones would score 5, Twos is the first box they score 0 in.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, random = FixedValueRandom(1))
        viewModel.setPlayerCount(1)
        viewModel.setGameMode(GameMode.QUICKFIRE)
        viewModel.startGame()

        advanceTimeBy(10_000)
        runCurrent()

        val scorecard = viewModel.game.value!!.players.single().scorecard
        assertEquals(0, scorecard[ScoreCategory.TWOS])
        assertNull(scorecard[ScoreCategory.ONES])
    }

    @Test
    fun `a roll that lands after Undo took the turn back to one with no rolls left is ignored`() {
        // The cup shakes before a roll lands; Undo in that window restores the previous turn. In
        // Quickfire the cup is tapped for the player right after they score, so this is likely there.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.setGameMode(GameMode.QUICKFIRE)
        viewModel.startGame()
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        viewModel.undo()
        val restored = viewModel.game.value!!

        viewModel.rollDice()

        assertEquals(restored, viewModel.game.value)
    }

    @Test
    fun `startGame builds a game with the configured players`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)

        viewModel.startGame()

        val state = viewModel.game.value
        assertNotNull(state)
        assertEquals(2, state.players.size)
        assertEquals(PlayerType.HUMAN, state.players[0].type)
        assertEquals(PlayerType.AI, state.players[1].type)
    }

    @Test
    fun `human actions before a game has started are ignored`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)

        viewModel.rollDice()

        assertNull(viewModel.game.value)
    }

    @Test
    fun `rolling and scoring as the human player updates the game state`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
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
    fun `no turn timer means no countdown is shown`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        assertNull(viewModel.turnSecondsRemaining.value)
    }

    @Test
    fun `turn timer counts down once a game with one starts`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.setTurnTimer(TurnTimer.SECONDS_30)
        viewModel.startGame()

        assertEquals(30, viewModel.turnSecondsRemaining.value)
    }

    @Test
    fun `letting the turn timer expire forfeits the turn and auto-scores it`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.setTurnTimer(TurnTimer.SECONDS_30)
        viewModel.startGame()

        // Not advanceUntilIdle: a single-player game with a timer times out its own next turn too,
        // forever - idling here would run the whole game to completion rather than just this timeout.
        advanceTimeBy(30_000)
        runCurrent()

        val state = viewModel.game.value!!
        assertEquals(1, state.players.single().scorecard.values.count { it != null })
        // The very next turn's timer (same lone player) is already ticking again by this point.
        assertEquals(30, viewModel.turnSecondsRemaining.value)
    }

    @Test
    fun `committing a score before the timer expires restarts it for the next player`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(2)
        viewModel.setTurnTimer(TurnTimer.SECONDS_30)
        viewModel.startGame()

        advanceTimeBy(20_000)
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)

        assertEquals(1, viewModel.game.value!!.currentPlayerIndex)
        assertEquals(30, viewModel.turnSecondsRemaining.value)
    }

    @Test
    fun `turn timer keeps running through an AI turn too - so its badge never disappears mid-game`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.setTurnTimer(TurnTimer.SECONDS_30)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)

        // Mid AI-turn, nothing advanced yet - the countdown should already be running for it, the
        // same as it would for a human, rather than sitting null until control returns.
        assertEquals(30, viewModel.turnSecondsRemaining.value)
    }

    @Test
    fun `an AI that takes too long deciding is timed out and its turn forfeited - same as a human's`() = runTest(testDispatcher) {
        // A separate dispatcher for AiTurnPlayer's hold/category work that this test never advances,
        // standing in for a decision that never comes back in time - the only realistic way an AI
        // seat could ever actually trip the turn timer.
        val stuckAiDispatcher = StandardTestDispatcher()
        val viewModel = GameViewModel(aiDispatcher = stuckAiDispatcher)
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.setTurnTimer(TurnTimer.SECONDS_30)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)

        advanceTimeBy(30_000)
        runCurrent()

        val state = viewModel.game.value!!
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(1, state.players[1].scorecard.values.count { it != null })
    }

    @Test
    fun `AI players complete their turn automatically`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
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
    fun `only a Hard CPU playing Standard is given the perfect-play table`() = runTest(testDispatcher) {
        for ((difficulty, mode, expectAsked) in listOf(
            Triple(Difficulty.HARD, GameMode.STANDARD, true),
            Triple(Difficulty.MEDIUM, GameMode.STANDARD, false),
            Triple(Difficulty.HARD, GameMode.TRICOLOUR, false),
            Triple(Difficulty.HARD, GameMode.QUICKFIRE, false),
        )) {
            var asked = 0
            val viewModel = GameViewModel(aiDispatcher = testDispatcher, standardPerfectPlay = { asked++; null })
            viewModel.setPlayerCount(2)
            viewModel.setPlayerType(2, PlayerType.AI)
            viewModel.setPlayerDifficulty(2, difficulty)
            viewModel.setGameMode(mode)
            viewModel.startGame()

            viewModel.rollDice()
            viewModel.commitScore(viewModel.game.value!!.players[0].scorecard.keys.first())
            advanceUntilIdle()

            // At least one: Quickfire's turn timer plays the human's turns out too, so its game runs on.
            assertTrue(viewModel.game.value!!.players[1].scorecard.values.any { it != null }, "$difficulty $mode CPU didn't play")
            assertEquals(expectAsked, asked > 0, "$difficulty $mode")
        }
    }

    @Test
    fun `a Stud CPU of every difficulty holds a five-dice hand before it scores`() = runTest(testDispatcher) {
        for (difficulty in Difficulty.entries) {
            val viewModel = GameViewModel(aiDispatcher = testDispatcher)
            viewModel.setPlayerCount(2)
            viewModel.setPlayerType(2, PlayerType.AI)
            viewModel.setPlayerDifficulty(2, difficulty)
            viewModel.setGameMode(GameMode.STUD)
            viewModel.startGame()

            viewModel.rollDice()
            for (index in 0 until 5) viewModel.toggleHold(index)
            viewModel.commitScore(ScoreCategory.CHANCE)
            advanceUntilIdle()

            val cpu = viewModel.game.value!!.players[1]
            assertEquals(1, cpu.scorecard.values.count { it != null }, "$difficulty CPU didn't play")
            assertEquals(5, cpu.lastRoll!!.count { it.isHeld }, "$difficulty")
            assertEquals(7, cpu.lastRoll.size, "$difficulty")
        }
    }

    @Test
    fun `a Stud score can't be committed until all five hold slots are full`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.setGameMode(GameMode.STUD)
        viewModel.startGame()
        viewModel.rollDice()
        for (index in 0 until 4) viewModel.toggleHold(index)

        viewModel.commitScore(ScoreCategory.CHANCE)
        assertNull(viewModel.game.value!!.players.single().scorecard[ScoreCategory.CHANCE])

        viewModel.toggleHold(4)
        // A sixth die can't be held: the slots are full.
        viewModel.toggleHold(5)
        assertEquals(5, viewModel.game.value!!.dice.count { it.isHeld })
        viewModel.commitScore(ScoreCategory.CHANCE)
        assertNotNull(viewModel.game.value!!.players.single().scorecard[ScoreCategory.CHANCE])
    }

    @Test
    fun `three consecutive AI players all take their turn without further human input`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
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
        assertTrue(state.players[1].scorecard.values.any { it != null }, "Player 2 (AI) never took a turn")
        assertTrue(state.players[2].scorecard.values.any { it != null }, "Player 3 (AI) never took a turn")
        assertTrue(state.players[3].scorecard.values.any { it != null }, "Player 4 (AI) never took a turn")
    }

    @Test
    fun `AI turns alternating with human turns each hand back correctly`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
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
        assertTrue(afterPlayerTwo.players[1].scorecard.values.any { it != null }, "Player 2 (AI) never took a turn")
        assertTrue(afterPlayerTwo.rollsRemaining == 3, "Player 3 (human) should be waiting for input, not mid-AI-turn")

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        advanceUntilIdle()

        val afterPlayerFour = viewModel.game.value!!
        assertEquals(0, afterPlayerFour.currentPlayerIndex)
        assertTrue(afterPlayerFour.players[3].scorecard.values.any { it != null }, "Player 4 (AI) never took a turn")
    }

    @Test
    fun `two consecutive AI players hand back to the next human correctly`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
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
        assertTrue(afterPlayerThree.players[1].scorecard.values.any { it != null }, "Player 2 (AI) never took a turn")
        assertTrue(afterPlayerThree.players[2].scorecard.values.any { it != null }, "Player 3 (AI) never took a turn")
        assertEquals(3, afterPlayerThree.rollsRemaining, "Player 4 (human) should be waiting for input")

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
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, random = FixedValueRandom(6))
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.setPlayerDifficulty(2, Difficulty.HARD)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        val timeBeforeAiTurn = testDispatcher.scheduler.currentTime
        advanceUntilIdle()

        // Every roll lands all sixes, so Hard's very first hold decision already keeps all five
        // dice (a 5x can't be improved by rerolling). The two "remaining" rolls that would
        // otherwise follow are pure no-ops - GameEngine.rollDice skips held dice - and should be
        // skipped rather than sitting through their delay for nothing: a full 3-roll AI turn takes
        // 3 shakes plus the pause before scoring; this one should take only 1 shake, the dice's toss and that pause.
        val aiStepDelayMs = 250L // matches GameViewModel.AI_STEP_DELAY_MS, which is private to it
        assertEquals(CUP_SHAKE_MILLIS + DICE_TOSS_MILLIS + aiStepDelayMs, testDispatcher.scheduler.currentTime - timeBeforeAiTurn)
        assertEquals(50, viewModel.game.value!!.players[1].scorecard[ScoreCategory.FIVE_OF_A_KIND])
    }

    @Test
    fun `a shorter shake window shortens only the shake - the AI still pauses before it scores`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, random = FixedValueRandom(6))
        viewModel.cupShakeMillis = REDUCED_MOTION_CUP_SHAKE_MILLIS
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.setPlayerDifficulty(2, Difficulty.HARD)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        val timeBeforeAiTurn = testDispatcher.scheduler.currentTime
        advanceUntilIdle()

        // The same one-shake turn as above, but the shake is the short window; the pause before scoring is
        // there so a player can follow the CPU, not to wait for an animation, so it stays.
        val aiStepDelayMs = 250L
        assertEquals(REDUCED_MOTION_CUP_SHAKE_MILLIS + DICE_TOSS_MILLIS + aiStepDelayMs, testDispatcher.scheduler.currentTime - timeBeforeAiTurn)
    }

    @Test
    fun `with the dice not animated the AI doesn't wait for them to settle`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, random = FixedValueRandom(6))
        viewModel.diceAnimated = false
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.setPlayerDifficulty(2, Difficulty.HARD)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        val timeBeforeAiTurn = testDispatcher.scheduler.currentTime
        advanceUntilIdle()

        // The same one-shake turn as above, with no toss to wait out: just the shake and the pause before scoring.
        val aiStepDelayMs = 250L
        assertEquals(0L, viewModel.diceTossMillis)
        assertEquals(CUP_SHAKE_MILLIS + aiStepDelayMs, testDispatcher.scheduler.currentTime - timeBeforeAiTurn)
    }

    @Test
    fun `the turn timer stops counting while the game is in the background`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.setTurnTimer(TurnTimer.SECONDS_30)
        viewModel.startGame()

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(25, viewModel.turnSecondsRemaining.value)

        viewModel.setForeground(false)
        advanceTimeBy(120_000)
        runCurrent()
        // Nothing ran down, and the turn wasn't forfeited behind the player's back.
        assertEquals(25, viewModel.turnSecondsRemaining.value)
        assertEquals(0, viewModel.game.value!!.players.single().scorecard.values.count { it != null })

        viewModel.setForeground(true)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(23, viewModel.turnSecondsRemaining.value)
    }

    @Test
    fun `a CPU turn waits in the background and plays on when the game is back`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, random = FixedValueRandom(6))
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.setPlayerDifficulty(2, Difficulty.HARD)
        viewModel.startGame()
        viewModel.rollDice()

        viewModel.setForeground(false)
        viewModel.commitScore(ScoreCategory.CHANCE)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(0, viewModel.game.value!!.players[1].scorecard.values.count { it != null })

        viewModel.setForeground(true)
        advanceUntilIdle()
        assertEquals(1, viewModel.game.value!!.players[1].scorecard.values.count { it != null })
    }

    @Test
    fun `undo is unavailable until a human action has happened`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `rolling is not undoable`() {
        // Rolling has no scoring consequence of its own - only committing a category does (see
        // the test below) - so there's nothing for Undo to do about a roll.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()

        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `holding a die is not undoable`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        viewModel.rollDice()

        viewModel.toggleHold(0)

        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `committing a score is undoable`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
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
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
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
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)

        assertFalse(viewModel.resumeGame())
        assertNull(viewModel.game.value)
    }

    @Test
    fun `holding and unholding all five dice in order activates superuser mode`() = runTest(testDispatcher) {
        // The whole feature is gated on a debug build (never available in a release build - see
        // GameViewModel.trackSuperuserSequence), so this is one; the release side is covered below.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, isDebugBuild = true)
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
    fun `the superuser sequence never activates superuser mode in a release build`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, isDebugBuild = false)
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        viewModel.rollDice()

        for (dieIndex in 0..4) {
            viewModel.toggleHold(dieIndex)
            viewModel.toggleHold(dieIndex)
        }

        assertFalse(viewModel.superuserModeActive.value)
    }

    @Test
    fun `toggling dice out of order does not activate superuser mode`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
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
    fun `superuser sequence activates on a later turn - not just the first`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, isDebugBuild = true)
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
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
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
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, isDebugBuild = true)
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
