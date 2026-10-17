package net.zodac.dicefive.ui

import androidx.room.RoomRawQuery
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
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.scores.DismissedPlayerStats
import net.zodac.dicefive.data.scores.PlayerGame
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.UnpostedScore
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.settings.SettingsToggles
import net.zodac.dicefive.ui.settings.SettingsViewModel
import net.zodac.dicefive.ui.settings.settingsViewModel
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
    override suspend fun rawPagedScores(query: RoomRawQuery): List<ScoreEntry> = error("Only the real DAO runs the SQL")
    override suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry> = gate.await().let { emptyList() }
    override suspend fun pagedScoresForMode(gameModeId: String, limit: Int, offset: Int): List<ScoreEntry> =
        gate.await().let { emptyList() }
    override suspend fun count(): Int = gate.await().let { 0 }
    override suspend fun countForMode(gameModeId: String): Int = gate.await().let { 0 }
    override suspend fun bestScoreForPlayer(playerName: String): Int? = gate.await().let { null }
    override suspend fun distinctScores(): List<Int> = gate.await().let { emptyList() }
    override suspend fun primaryPlayerTotalPoints(): Int? = gate.await().let { null }
    override suspend fun primaryPlayerGameModeIds(): List<String> = gate.await().let { emptyList() }
    override suspend fun bestUnpostedScore(gameModeId: String, afterEpochMillis: Long, maxScore: Int): UnpostedScore? = null
    override suspend fun playerGames(): List<PlayerGame> = gate.await().let { emptyList() }
    override suspend fun dismissPlayer(playerName: String, atEpochMillis: Long) = Unit
    override suspend fun clearDismissal(playerName: String) = Unit
    override suspend fun clearAllScores() = Unit
    override suspend fun clearAllDismissals() = Unit
    override suspend fun allScores(): List<ScoreEntry> = emptyList()
    override suspend fun allDismissals(): List<DismissedPlayerStats> = emptyList()
    override suspend fun insertMissing(entries: List<ScoreEntry>) = Unit
    override suspend fun putDismissals(dismissals: List<DismissedPlayerStats>) = Unit
    override suspend fun deleteScoresThrough(epochMillis: Long) = Unit
    override suspend fun deleteDismissalsThrough(epochMillis: Long) = Unit
    override suspend fun deleteUndoneDismissals() = Unit
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
        val viewModel = settingsViewModel(settings = repository)
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
    fun `the Leaderboard - Statistics and Achievements only count as empty once their scores have been read`() = runTest(testDispatcher) {
        val scoresDao = GatedEmptyScoreDao()
        val scores = ScoresViewModel(ScoreRepository(scoresDao))
        val statisticsDao = GatedEmptyScoreDao()
        val statistics = StatisticsViewModel(ScoreRepository(statisticsDao))
        // Achievements wait for the leaderboard as well as the unlocks before showing.
        val achievementsDao = GatedEmptyScoreDao()
        val achievements = AchievementsViewModel(achievementsRepository = InMemoryAchievementStore(), scoreRepository = ScoreRepository(achievementsDao), onlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()))
        backgroundScope.launch(testDispatcher) { achievements.uiState.collect {} }
        advanceUntilIdle()
        assertFalse(scores.uiState.value.isLoaded)
        assertFalse(statistics.uiState.value.isLoaded)
        // The unlocks are in, but the score-collection achievements' progress isn't known yet.
        assertFalse(achievements.uiState.value.isLoaded)

        for (dao in listOf(scoresDao, statisticsDao, achievementsDao)) dao.gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(scores.uiState.value.isLoaded)
        assertTrue(scores.uiState.value.combined.entries.isEmpty())
        assertTrue(statistics.uiState.value.isLoaded)
        assertTrue(statistics.uiState.value.players.isEmpty())
        val state = achievements.uiState.value
        assertTrue(state.isLoaded)
        assertTrue(state.groups.isNotEmpty())
        assertTrue(state.totalCount > 0)
    }
}
