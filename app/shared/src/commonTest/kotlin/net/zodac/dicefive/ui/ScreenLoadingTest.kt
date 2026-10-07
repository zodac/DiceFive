package net.zodac.dicefive.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CompletableDeferred
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
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.scores.PlayerGame
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.settings.SettingsToggles
import net.zodac.dicefive.ui.settings.SettingsViewModel
import net.zodac.dicefive.ui.statistics.StatisticsViewModel

/** In-memory preferences, so a real [SettingsRepository] can hold the saved switches. */
private class LoadingPreferencesStore : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(_data.value).also { _data.value = it }
}

/** No scores at all - and every read held until [gate] opens, standing in for a slow database. */
private class GatedEmptyScoreDao : ScoreDao {

    val gate = CompletableDeferred<Unit>()

    override suspend fun insert(entry: ScoreEntry) = Unit
    override suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry> = gate.await().let { emptyList() }
    override suspend fun pagedScoresForMode(gameModeId: String, includeOffBoard: Boolean, limit: Int, offset: Int): List<ScoreEntry> =
        gate.await().let { emptyList() }
    override suspend fun count(): Int = gate.await().let { 0 }
    override suspend fun countForMode(gameModeId: String, includeOffBoard: Boolean): Int = gate.await().let { 0 }
    override suspend fun bestScoreForPlayer(playerName: String): Int? = gate.await().let { null }
    override suspend fun distinctScores(): List<Int> = gate.await().let { emptyList() }
    override suspend fun primaryPlayerTotalPoints(): Int? = gate.await().let { null }
    override suspend fun playerGames(): List<PlayerGame> = gate.await().let { emptyList() }
    override suspend fun dismissPlayer(playerName: String) = Unit
    override suspend fun clearDismissal(playerName: String) = Unit
    override suspend fun clearAllScores() = Unit
    override suspend fun clearAllDismissals() = Unit
}

private class LoadingAchievementStore : AchievementStore {

    private val _state = MutableStateFlow(AchievementsState())
    override val state: Flow<AchievementsState> = _state

    override suspend fun current(): AchievementsState = _state.value
    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) = Unit
    override suspend fun resetAll() = Unit
    override suspend fun forceLock(achievement: Achievement) = Unit
}

/**
 * Each screen that shows saved data holds it back until it has loaded, rather than drawing a
 * default (a switch's default position, "No scores yet", "0 of 0 unlocked") and then switching.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenLoadingTest {

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
    fun `settings switches arrive together with their saved values and never as defaults`() = runTest(testDispatcher) {
        val repository = SettingsRepository(LoadingPreferencesStore())
        repository.setSoundEnabled(false)
        repository.setAnimationLevel(AnimationLevel.LOW)

        val viewModel = SettingsViewModel(settingsRepository = repository)
        assertNull(viewModel.toggles.value)

        val shown = mutableListOf<SettingsToggles>()
        backgroundScope.launch(testDispatcher) {
            viewModel.toggles.collect { toggles -> if (toggles != null) shown += toggles }
        }
        advanceUntilIdle()
        // advanceUntilIdle stops once only background work is left - the collector above included.
        runCurrent()

        val expected = SettingsToggles(soundEnabled = false, vibrationEnabled = true, animationLevel = AnimationLevel.LOW, confirmBeforeLeavingGame = true)
        assertEquals(listOf(expected), shown)
    }

    @Test
    fun `settings switches show straight away with no saved settings to load`() {
        assertEquals(SettingsToggles(), SettingsViewModel().toggles.value)
    }

    @Test
    fun `an empty leaderboard only counts as empty once it has been read`() = runTest(testDispatcher) {
        val dao = GatedEmptyScoreDao()
        val viewModel = ScoresViewModel(ScoreRepository(dao))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoaded)

        dao.gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoaded)
        assertTrue(viewModel.uiState.value.entries.isEmpty())
    }

    @Test
    fun `empty statistics only count as empty once they have been read`() = runTest(testDispatcher) {
        val dao = GatedEmptyScoreDao()
        val viewModel = StatisticsViewModel(ScoreRepository(dao))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoaded)

        dao.gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoaded)
        assertTrue(viewModel.uiState.value.players.isEmpty())
    }

    @Test
    fun `achievements wait for the leaderboard as well as the unlocks before showing`() = runTest(testDispatcher) {
        val dao = GatedEmptyScoreDao()
        val viewModel = AchievementsViewModel(achievementsRepository = LoadingAchievementStore(), scoreRepository = ScoreRepository(dao))
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        // The unlocks are in, but the score-collection achievements' progress isn't known yet.
        assertFalse(viewModel.uiState.value.isLoaded)

        dao.gate.complete(Unit)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.isLoaded)
        assertTrue(state.groups.isNotEmpty())
        assertTrue(state.totalCount > 0)
    }
}
