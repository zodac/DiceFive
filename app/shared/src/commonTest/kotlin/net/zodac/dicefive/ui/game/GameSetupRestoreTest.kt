package net.zodac.dicefive.ui.game

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.TurnTimer

/** In-memory preferences, so a real [SettingsRepository] can hold the saved setup. */
private class FakePreferencesStore : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(_data.value).also { _data.value = it }
}

/** The setup form comes back with the last game's choices, all at once - never the defaults first. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameSetupRestoreTest {

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
    fun `saved setup is published in one update and only then flagged restored`() = runTest(testDispatcher) {
        val repository = SettingsRepository(FakePreferencesStore())
        repository.setPlayerCount(3)
        repository.setPlayerName(1, "Alexandra the Great")
        repository.setPlayerType(2, PlayerType.AI)
        repository.setPlayerDifficulty(2, Difficulty.HARD)
        repository.setTurnTimer(TurnTimer.SECONDS_60)
        repository.setGameMode(GameMode.TRICOLOUR)

        val viewModel = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        assertFalse(viewModel.setupRestored.value)

        // Every state the form could be drawn with once it's shown - there must be exactly one.
        val shownStates = mutableListOf<GameSetupState>()
        backgroundScope.launch(testDispatcher) {
            viewModel.setupRestored.collect { restored -> if (restored) shownStates += viewModel.setup.value }
        }
        advanceUntilIdle()
        // advanceUntilIdle stops once only background work is left - the collector above included.
        runCurrent()

        assertTrue(viewModel.setupRestored.value)
        val restored = shownStates.single()
        assertEquals(GameMode.TRICOLOUR, restored.gameMode)
        assertEquals(TurnTimer.SECONDS_60, restored.turnTimer)
        assertEquals(3, restored.playerCount)
        assertEquals("Alexandra the Great".take(GameSetupState.maxPlayerNameLength(3)), restored.playerSlots[0].name)
        assertEquals(PlayerType.AI, restored.playerSlots[1].type)
        assertEquals(Difficulty.HARD, restored.playerSlots[1].difficulty)
        assertEquals(restored, viewModel.setup.value)
    }

    @Test
    fun `with no saved settings to restore the form is shown straight away`() {
        assertTrue(GameViewModel(aiDispatcher = testDispatcher).setupRestored.value)
    }

    @Test
    fun `turn timer length is remembered while the timer is off`() = runTest(testDispatcher) {
        val repository = SettingsRepository(FakePreferencesStore())
        val first = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        first.setTurnTimer(TurnTimer.SECONDS_120)
        first.setTurnTimer(TurnTimer.NONE)
        assertEquals(TurnTimer.SECONDS_120, first.setup.value.turnTimerLength)
        first.startGame()
        advanceUntilIdle()

        val second = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        assertEquals(TurnTimer.NONE, second.setup.value.turnTimer)
        assertEquals(TurnTimer.SECONDS_120, second.setup.value.turnTimerLength)
    }

    @Test
    fun `roll modifiers are remembered and the rolls value is kept while off`() = runTest(testDispatcher) {
        val repository = SettingsRepository(FakePreferencesStore())
        val first = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        first.setRollsPerTurn(7)
        first.setRollsPerTurn(null)
        first.setStoredRolls(true)
        first.setStoredRollsMax(12)
        first.startGame()
        advanceUntilIdle()

        val second = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        assertEquals(RollModifiers(rollsPerTurn = null, storedRolls = true, storedRollsMax = 12), second.setup.value.rollModifiers)
        assertEquals(7, second.setup.value.rollsPerTurnLength)
    }

    @Test
    fun `a game starts with the chosen roll modifiers`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setRollsPerTurn(5)
        viewModel.setStoredRolls(true)
        viewModel.startGame()

        val game = checkNotNull(viewModel.game.value)
        assertEquals(RollModifiers(rollsPerTurn = 5, storedRolls = true), game.rollModifiers)
        assertEquals(5, game.rollsRemaining)
    }

    @Test
    fun `a mode that doesn't allow roll modifiers starts without them but keeps the pick`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setRollsPerTurn(5)
        viewModel.setGameMode(GameMode.QUICKFIRE)
        viewModel.startGame()

        assertEquals(RollModifiers(), checkNotNull(viewModel.game.value).rollModifiers)
        assertEquals(5, viewModel.setup.value.rollModifiers.rollsPerTurn)
    }
}
