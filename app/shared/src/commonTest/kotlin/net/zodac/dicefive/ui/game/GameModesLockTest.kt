package net.zodac.dicefive.ui.game

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.TurnTimer

/** The New Game form's game mode and modifiers stay locked until a first game is finished. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameModesLockTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A form over [repository] that has finished restoring what it saved. */
    private fun TestScope.restored(repository: SettingsRepository): GameViewModel {
        val viewModel = gameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        return viewModel
    }

    /** Plays [turns] turns (every turn of the game by default): roll once, then take the first open box. */
    private fun GameViewModel.play(turns: Int = Int.MAX_VALUE) {
        var played = 0
        while (played < turns) {
            val state = game.value ?: return
            if (state.isGameOver) return
            rollDice()
            val player = state.currentPlayer ?: return
            commitScore(state.gameMode.categoriesWith(state.extendedScores).first { player.isOpen(it) })
            played++
        }
    }

    /** Saved picks a fresh install wouldn't have - as a cloud save could bring. */
    private suspend fun SettingsRepository.saveNonDefaultPicks() {
        setGameMode(GameMode.TRICOLOUR)
        setTurnTimer(TurnTimer.SECONDS_30)
        setRollModifiers(RollModifiers(rollsPerTurn = 5, storedRolls = true), rollsPerTurnLength = 5)
        setExtendedScores(true)
    }

    @Test
    fun `a fresh install is locked to Standard with no modifiers`() = runTest(testDispatcher) {
        val setup = restored(SettingsRepository(InMemoryPreferences())).setupForm.setup.value

        assertFalse(setup.modesUnlocked)
        assertEquals(GameMode.STANDARD, setup.gameMode)
        assertEquals(TurnTimer.NONE, setup.turnTimer)
        assertEquals(RollModifiers(), setup.rollModifiers)
        assertFalse(setup.extendedScores)
        assertFalse(setup.unluckyDiceEnabled)
    }

    @Test
    fun `while locked the saved picks are neither restored nor overwritten - and come back once unlocked`() = runTest(testDispatcher) {
        val repository = SettingsRepository(InMemoryPreferences())
        repository.saveNonDefaultPicks()

        val locked = restored(repository)
        assertEquals(GameMode.STANDARD, locked.setupForm.setup.value.gameMode)
        assertEquals(TurnTimer.NONE, locked.setupForm.setup.value.turnTimer)
        locked.startGame()
        advanceUntilIdle()
        assertEquals(GameMode.STANDARD, locked.game.value?.gameMode)
        assertEquals(GameMode.TRICOLOUR, repository.gameMode.first())
        assertEquals(TurnTimer.SECONDS_30, repository.turnTimer.first())

        repository.setFirstGameCompleted()
        val unlocked = restored(repository).setupForm.setup.value
        assertTrue(unlocked.modesUnlocked)
        assertEquals(GameMode.TRICOLOUR, unlocked.gameMode)
        assertEquals(TurnTimer.SECONDS_30, unlocked.turnTimer)
        assertEquals(RollModifiers(rollsPerTurn = 5, storedRolls = true), unlocked.rollModifiers)
        assertTrue(unlocked.extendedScores)
    }

    @Test
    fun `finishing a game unlocks the form at once and for good - solo or with two players`() = runTest(testDispatcher) {
        for (players in listOf(1, 2)) {
            val repository = SettingsRepository(InMemoryPreferences())
            val viewModel = restored(repository)
            viewModel.setupForm.setPlayerCount(players)
            viewModel.startGame()
            viewModel.play()
            advanceUntilIdle()

            assertTrue(viewModel.game.value?.isGameOver == true, "$players players")
            assertTrue(viewModel.setupForm.setup.value.modesUnlocked, "$players players")
            assertTrue(repository.firstGameCompleted.first(), "$players players")
            assertTrue(restored(repository).setupForm.setup.value.modesUnlocked, "$players players")
        }
    }

    @Test
    fun `a game left unfinished leaves the form locked`() = runTest(testDispatcher) {
        val repository = SettingsRepository(InMemoryPreferences())
        val viewModel = restored(repository)
        viewModel.startGame()
        viewModel.play(turns = GameMode.STANDARD.turnsPerGame - 1)
        advanceUntilIdle()

        assertFalse(viewModel.game.value?.isGameOver == true)
        assertFalse(viewModel.setupForm.setup.value.modesUnlocked)
        assertFalse(repository.firstGameCompleted.first())
        assertFalse(restored(repository).setupForm.setup.value.modesUnlocked)
    }
}
