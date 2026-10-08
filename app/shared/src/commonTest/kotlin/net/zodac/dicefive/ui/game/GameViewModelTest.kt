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
import kotlinx.coroutines.CoroutineDispatcher
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

    /**
     * A game started with [players] seats, the ones in [cpus] played by the computer at [difficulty], set up further by
     * [setUp] - player 1 is always human.
     */
    private fun started(
        players: Int = 1,
        cpus: List<Int> = emptyList(),
        difficulty: Difficulty = Difficulty.MEDIUM,
        random: Random = Random.Default,
        aiDispatcher: CoroutineDispatcher = testDispatcher,
        isDebugBuild: Boolean = false,
        setUp: GameViewModel.() -> Unit = {},
    ): GameViewModel {
        val viewModel = GameViewModel(aiDispatcher = aiDispatcher, random = random, isDebugBuild = isDebugBuild)
        viewModel.setPlayerCount(players)
        for (slot in cpus) {
            viewModel.setPlayerType(slot, PlayerType.AI)
            viewModel.setPlayerDifficulty(slot, difficulty)
        }
        viewModel.setUp()
        viewModel.startGame()
        return viewModel
    }

    /** Rolls and scores player 1's turn in Chance - handing over to whoever is next. */
    private fun GameViewModel.takeATurn() {
        rollDice()
        commitScore(ScoreCategory.CHANCE)
    }

    /** Holds then releases every die, in order - the hidden sequence that turns superuser mode on. */
    private fun GameViewModel.holdSequence() {
        for (dieIndex in 0..4) {
            toggleHold(dieIndex)
            toggleHold(dieIndex)
        }
    }

    @Test
    fun `startGame plays the players and mode picked on the setup form - and nothing happens before it`() {
        val untouched = GameViewModel(aiDispatcher = testDispatcher)
        untouched.rollDice()
        assertNull(untouched.game.value)

        // Standard by default.
        val viewModel = started()
        assertEquals(GameMode.STANDARD, viewModel.game.value?.gameMode)
        viewModel.setGameMode(GameMode.TRICOLOUR)
        viewModel.startGame()
        viewModel.rollDice()
        val tricolour = viewModel.game.value!!
        assertEquals(GameMode.TRICOLOUR, tricolour.gameMode)
        assertEquals(GameMode.TRICOLOUR.categories, tricolour.players.single().scorecard.keys.toList())
        assertTrue(tricolour.dice.all { it.colour != null })

        val twoPlayers = started(players = 2, cpus = listOf(2)).game.value
        assertNotNull(twoPlayers)
        assertEquals(listOf(PlayerType.HUMAN, PlayerType.AI), twoPlayers.players.map { it.type })

        // A seat's first 5x flash is claimed once per game, and again after a new game.
        val flashes = started(players = 2)
        assertTrue(flashes.claimFiveOfAKindFlash(0))
        assertFalse(flashes.claimFiveOfAKindFlash(0))
        assertTrue(flashes.claimFiveOfAKindFlash(1))
        assertTrue(flashes.game.value!!.players.all { it.fiveOfAKindFlashed })
        flashes.startGame()
        assertTrue(flashes.claimFiveOfAKindFlash(0))
    }

    @Test
    fun `a human turn - rolling and scoring and Undo taking back a score but never a roll - a hold or a CPU's move`() = runTest(testDispatcher) {
        val viewModel = started()
        assertFalse(viewModel.canUndo.value, "undo is unavailable until a human action has happened")
        viewModel.rollDice()
        val rolled = viewModel.game.value!!
        assertEquals(TurnPhase.ROLLED, rolled.phase)
        assertEquals(2, rolled.rollsRemaining)
        // Rolling has no scoring consequence of its own - only committing a category does - so there's nothing for Undo to
        // do about a roll, or a hold.
        assertFalse(viewModel.canUndo.value, "a roll is not undoable")
        viewModel.toggleHold(0)
        assertFalse(viewModel.canUndo.value, "a hold is not undoable")

        viewModel.commitScore(ScoreCategory.CHANCE)
        assertEquals(1, viewModel.game.value!!.players.single().turnsTaken)
        assertTrue(viewModel.canUndo.value)
        viewModel.undo()
        val undone = viewModel.game.value!!
        assertEquals(TurnPhase.ROLLED, undone.phase)
        assertEquals(0, undone.players.single().turnsTaken)
        assertFalse(viewModel.canUndo.value)

        // A roll that lands after Undo took the turn back to one with no rolls left is ignored. The cup shakes before a
        // roll lands; Undo in that window restores the previous turn - with one roll a turn (Number of Rolls), it has none left.
        val oneRoll = started { setRollsPerTurn(1) }
        oneRoll.takeATurn()
        oneRoll.undo()
        val restored = oneRoll.game.value!!
        oneRoll.rollDice()
        assertEquals(restored, oneRoll.game.value)

        val againstCpu = started(players = 2, cpus = listOf(2))
        againstCpu.takeATurn()
        advanceUntilIdle()
        assertFalse(againstCpu.canUndo.value, "undo is unavailable once an AI player has acted")

        // resumeGame returns false when no repository is configured.
        val noRepository = GameViewModel(aiDispatcher = testDispatcher)
        assertFalse(noRepository.resumeGame())
        assertNull(noRepository.game.value)
    }

    @Test
    fun `the turn timer counts down every turn - a human's or a CPU's and a turn it runs out on is forfeited and scored`() = runTest(testDispatcher) {
        assertNull(started().turnSecondsRemaining.value, "no turn timer means no countdown is shown")
        val timed: GameViewModel.() -> Unit = { setTurnTimer(TurnTimer.SECONDS_30) }
        assertEquals(30, started(setUp = timed).turnSecondsRemaining.value)

        // Letting it expire forfeits the turn and auto-scores it. Not advanceUntilIdle: a single-player game with a timer
        // times out its own next turn too - idling would run the whole game to completion rather than just this timeout.
        val expired = started(setUp = timed)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, expired.game.value!!.players.single().turnsTaken)
        // The very next turn's timer (same lone player) is already ticking again by this point.
        assertEquals(30, expired.turnSecondsRemaining.value)

        // Committing a score before it expires restarts it for the next player.
        val beatIt = started(players = 2, setUp = timed)
        advanceTimeBy(20_000)
        beatIt.takeATurn()
        assertEquals(1, beatIt.game.value!!.currentPlayerIndex)
        assertEquals(30, beatIt.turnSecondsRemaining.value)

        // It keeps running through an AI turn too - so its badge never disappears mid-game: mid AI-turn, nothing advanced
        // yet, the countdown is already running for it, the same as it would for a human.
        val cpuTurn = started(players = 2, cpus = listOf(2), setUp = timed)
        cpuTurn.takeATurn()
        assertEquals(30, cpuTurn.turnSecondsRemaining.value)

        // An AI that takes too long deciding is timed out and its turn forfeited - same as a human's. A separate dispatcher
        // for AiTurnPlayer's work that this test never advances stands in for a decision that never comes back in time -
        // the only realistic way an AI seat could ever actually trip the turn timer.
        val stuck = started(players = 2, cpus = listOf(2), aiDispatcher = StandardTestDispatcher(), setUp = timed)
        stuck.takeATurn()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(0, stuck.game.value!!.currentPlayerIndex)
        assertEquals(1, stuck.game.value!!.players[1].turnsTaken)
    }

    @Test
    fun `in the background the turn timer stops and a CPU turn waits - both carrying on when the game is back`() = runTest(testDispatcher) {
        val timed = started { setTurnTimer(TurnTimer.SECONDS_30) }
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(25, timed.turnSecondsRemaining.value)
        timed.setForeground(false)
        advanceTimeBy(120_000)
        runCurrent()
        // Nothing ran down, and the turn wasn't forfeited behind the player's back.
        assertEquals(25, timed.turnSecondsRemaining.value)
        assertEquals(0, timed.game.value!!.players.single().turnsTaken)
        timed.setForeground(true)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(23, timed.turnSecondsRemaining.value)

        val cpu = started(players = 2, cpus = listOf(2), difficulty = Difficulty.HARD, random = FixedValueRandom(6))
        cpu.rollDice()
        cpu.setForeground(false)
        cpu.commitScore(ScoreCategory.CHANCE)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(0, cpu.game.value!!.players[1].turnsTaken)
        cpu.setForeground(true)
        advanceUntilIdle()
        assertEquals(1, cpu.game.value!!.players[1].turnsTaken)
    }

    @Test
    fun `CPU players take their turns without further human input - one after another - and hand back to the next human`() = runTest(testDispatcher) {
        val one = started(players = 2, cpus = listOf(2))
        one.takeATurn()
        advanceUntilIdle()
        assertEquals(0, one.game.value!!.currentPlayerIndex)
        assertTrue(one.game.value!!.players[1].turnsTaken > 0)

        val three = started(players = 4, cpus = listOf(2, 3, 4))
        three.takeATurn()
        advanceUntilIdle()
        val afterThree = three.game.value!!
        assertEquals(0, afterThree.currentPlayerIndex)
        for (seat in 1..3) assertTrue(afterThree.players[seat].turnsTaken > 0, "Player ${seat + 1} (AI) never took a turn")

        // Alternating with human turns (slots 1 and 3 human), each hands back correctly.
        val alternating = started(players = 4, cpus = listOf(2, 4))
        alternating.takeATurn()
        advanceUntilIdle()
        val afterTwo = alternating.game.value!!
        assertEquals(2, afterTwo.currentPlayerIndex)
        assertTrue(afterTwo.players[1].turnsTaken > 0, "Player 2 (AI) never took a turn")
        assertTrue(afterTwo.rollsRemaining == 3, "Player 3 (human) should be waiting for input, not mid-AI-turn")
        alternating.takeATurn()
        advanceUntilIdle()
        assertEquals(0, alternating.game.value!!.currentPlayerIndex)
        assertTrue(alternating.game.value!!.players[3].turnsTaken > 0, "Player 4 (AI) never took a turn")

        // Two in a row (slots 1 and 4 human) hand back to the next human; player 4's turn wraps to player 1 and no further.
        val twoInARow = started(players = 4, cpus = listOf(2, 3))
        twoInARow.takeATurn()
        advanceUntilIdle()
        val afterPlayerThree = twoInARow.game.value!!
        assertEquals(3, afterPlayerThree.currentPlayerIndex)
        assertTrue(afterPlayerThree.players[1].turnsTaken > 0, "Player 2 (AI) never took a turn")
        assertTrue(afterPlayerThree.players[2].turnsTaken > 0, "Player 3 (AI) never took a turn")
        assertEquals(3, afterPlayerThree.rollsRemaining, "Player 4 (human) should be waiting for input")
        twoInARow.takeATurn()
        advanceUntilIdle()
        assertEquals(0, twoInARow.game.value!!.currentPlayerIndex)
        assertEquals(3, twoInARow.game.value!!.rollsRemaining)
    }

    @Test
    fun `only a Hard CPU playing Standard is given the perfect-play table - and a Stud CPU holds five dice before it scores`() = runTest(testDispatcher) {
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
            // The first box still open: Quickfire switches some off.
            val human = viewModel.game.value!!.players[0]
            viewModel.commitScore(human.categories.first { human.isOpen(it) })
            advanceUntilIdle()

            assertTrue(viewModel.game.value!!.players[1].turnsTaken > 0, "$difficulty $mode CPU didn't play")
            assertEquals(expectAsked, asked > 0, "$difficulty $mode")
        }

        for (difficulty in Difficulty.entries) {
            val viewModel = started(players = 2, cpus = listOf(2), difficulty = difficulty) { setGameMode(GameMode.STUD) }
            viewModel.rollDice()
            for (index in 0 until 5) viewModel.toggleHold(index)
            viewModel.commitScore(ScoreCategory.CHANCE)
            advanceUntilIdle()

            val cpu = viewModel.game.value!!.players[1]
            assertEquals(1, cpu.turnsTaken, "$difficulty CPU didn't play")
            assertEquals(5, cpu.lastRoll!!.count { it.isHeld }, "$difficulty")
            assertEquals(7, cpu.lastRoll.size, "$difficulty")
        }
    }

    @Test
    fun `a CPU turn's pace - spare rolls skipped once it keeps every die - only the shake shortened - no settling for still dice`() = runTest(testDispatcher) {
        // Every roll lands all sixes, so Hard's very first hold decision already keeps all five dice (a 5x can't be improved
        // by rerolling). The two "remaining" rolls that would otherwise follow are pure no-ops - GameEngine.rollDice skips
        // held dice - and are skipped rather than sat through: a full 3-roll AI turn takes 3 shakes plus the pause before
        // scoring; this one takes only 1 shake, the dice's toss and that pause.
        val aiStepDelayMs = 250L // matches GameViewModel.AI_STEP_DELAY_MS, which is private to it
        fun cpuTurnMillis(setUp: GameViewModel.() -> Unit = {}): Pair<Long, GameViewModel> {
            val viewModel = started(players = 2, cpus = listOf(2), difficulty = Difficulty.HARD, random = FixedValueRandom(6), setUp = setUp)
            viewModel.takeATurn()
            val before = testDispatcher.scheduler.currentTime
            advanceUntilIdle()
            return testDispatcher.scheduler.currentTime - before to viewModel
        }

        val (full, sixes) = cpuTurnMillis()
        assertEquals(CUP_SHAKE_MILLIS + DICE_TOSS_MILLIS + aiStepDelayMs, full)
        assertEquals(50, sixes.game.value!!.players[1].scoresIn(ScoreCategory.FIVE_OF_A_KIND).singleOrNull())

        // A shorter shake window shortens only the shake: the pause before scoring is there so a player can follow the
        // CPU, not to wait for an animation, so it stays.
        assertEquals(REDUCED_MOTION_CUP_SHAKE_MILLIS + DICE_TOSS_MILLIS + aiStepDelayMs, cpuTurnMillis { cupShakeMillis = REDUCED_MOTION_CUP_SHAKE_MILLIS }.first)

        // With the dice not animated there's no toss to wait out: just the shake and the pause before scoring.
        val (still, stillDice) = cpuTurnMillis { diceAnimated = false }
        assertEquals(0L, stillDice.diceTossMillis)
        assertEquals(CUP_SHAKE_MILLIS + aiStepDelayMs, still)
    }

    @Test
    fun `a Stud box scores with fewer than five held in the same tap - and Undo returns to the dice as the player held them`() {
        val viewModel = started { setGameMode(GameMode.STUD) }
        viewModel.rollDice()
        for (index in 0 until 3) viewModel.toggleHold(index)
        viewModel.commitScore(ScoreCategory.CHANCE)
        assertNotNull(viewModel.game.value!!.players.single().scoresIn(ScoreCategory.CHANCE).singleOrNull())

        val undone = started { setGameMode(GameMode.STUD) }
        undone.rollDice()
        undone.toggleHold(1)
        undone.commitScore(ScoreCategory.CHANCE)
        undone.undo()
        assertEquals(listOf(false, true, false, false, false, false, false), undone.game.value!!.dice.map { it.isHeld })
    }

    @Test
    fun `holding and releasing all five dice in order turns superuser mode on - debug builds only - and lets a held die's face be cycled`() = runTest(testDispatcher) {
        // The whole feature is gated on a debug build (never available in a release build - see
        // GameViewModel.trackSuperuserSequence).
        val inactive = started()
        inactive.rollDice()
        inactive.toggleHold(0)
        val beforeActive = inactive.game.value!!
        // cycleHeldDieValue does nothing before superuser mode is activated.
        inactive.cycleHeldDieValue(0)
        assertEquals(beforeActive, inactive.game.value)

        val viewModel = started(isDebugBuild = true)
        viewModel.rollDice()
        var toastMessage: String? = null
        val collectJob = launch { toastMessage = viewModel.toastMessages.first() }
        viewModel.holdSequence()
        advanceUntilIdle()
        assertTrue(viewModel.superuserModeActive.value)
        assertEquals("Superuser mode activated!", toastMessage)
        collectJob.cancel()

        // Once active, it advances a held die's face.
        viewModel.toggleHold(0)
        val before = viewModel.game.value!!.dice[0].value
        viewModel.cycleHeldDieValue(0)
        val after = viewModel.game.value!!.dice[0]
        assertEquals(if (before >= 6) 1 else before + 1, after.value)
        assertTrue(after.isHeld)

        val release = started(isDebugBuild = false)
        release.rollDice()
        release.holdSequence()
        assertFalse(release.superuserModeActive.value, "never in a release build")

        val outOfOrder = started(isDebugBuild = true)
        outOfOrder.rollDice()
        // Skips die 1 - breaks the required order.
        for (dieIndex in listOf(0, 2, 1, 3, 4)) {
            outOfOrder.toggleHold(dieIndex)
            outOfOrder.toggleHold(dieIndex)
        }
        assertFalse(outOfOrder.superuserModeActive.value, "toggling dice out of order")

        // It activates on a later turn too - not just the first: the same (only) player's second turn.
        val laterTurn = started(isDebugBuild = true)
        laterTurn.takeATurn()
        laterTurn.rollDice()
        laterTurn.holdSequence()
        assertTrue(laterTurn.superuserModeActive.value)
    }
}

/** Dice that always land on [value] - lets a test force a specific roll deterministically. */
private class FixedValueRandom(private val value: Int) : Random() {
    override fun nextBits(bitCount: Int): Int = 0
    override fun nextInt(from: Int, until: Int): Int = value
}
