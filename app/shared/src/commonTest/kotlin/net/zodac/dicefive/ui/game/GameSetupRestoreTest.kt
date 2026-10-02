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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerType
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
}
