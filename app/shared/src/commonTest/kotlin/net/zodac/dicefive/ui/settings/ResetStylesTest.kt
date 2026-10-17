package net.zodac.dicefive.ui.settings

import androidx.room.RoomRawQuery
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.data.scores.DismissedPlayerStats
import net.zodac.dicefive.data.scores.PlayerGame
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.scores.UnpostedScore
import net.zodac.dicefive.data.settings.SettingValue
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.ui.game.style.DiceStyles

private class ResetFakeStore : AchievementStore {
    private val _state = MutableStateFlow(AchievementsState())
    override val state: Flow<AchievementsState> = _state
    override suspend fun current(): AchievementsState = _state.value
    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) = Unit
    override suspend fun resetAll() {
        _state.value = AchievementsState()
    }
    override suspend fun forceLock(achievement: Achievement) = Unit
}

private class ResetFakePreferences : DataStore<Preferences> {
    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(_data.value).also { _data.value = it }
}

/** No scores, and every write ignored. */
private object EmptyScoreDao : ScoreDao {
    override suspend fun insert(entry: ScoreEntry) = Unit
    override suspend fun rawPagedScores(query: RoomRawQuery): List<ScoreEntry> = error("Only the real DAO runs the SQL")
    override suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry> = emptyList()
    override suspend fun pagedScoresForMode(gameModeId: String, limit: Int, offset: Int): List<ScoreEntry> = emptyList()
    override suspend fun count(): Int = 0
    override suspend fun countForMode(gameModeId: String): Int = 0
    override suspend fun bestScoreForPlayer(playerName: String): Int? = null
    override suspend fun distinctScores(): List<Int> = emptyList()
    override suspend fun primaryPlayerTotalPoints(): Int? = null
    override suspend fun primaryPlayerGameModeIds(): List<String> = emptyList()
    override suspend fun bestUnpostedScore(gameModeId: String, afterEpochMillis: Long, maxScore: Int): UnpostedScore? = null
    override suspend fun playerGames(): List<PlayerGame> = emptyList()
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

@OptIn(ExperimentalCoroutinesApi::class)
class ResetStylesTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `resetting achievements puts a locked dice pick back to the default - and keeps one that needs no achievement`() = runTest {
        for ((picked, afterReset) in listOf("frosted_ice" to DiceStyles.default.id, "classic_oak" to "classic_oak")) {
            val settings = SettingsRepository(ResetFakePreferences())
            settings.setDiceStyleId(picked)
            val viewModel = SettingsViewModel(settingsRepository = settings, achievementsRepository = ResetFakeStore())
            viewModel.resetAchievements()
            advanceUntilIdle()
            assertEquals(afterReset, settings.diceStyleId.first(), picked)
        }
    }

    @Test
    fun `the picks a reset puts back are this device's only - the cloud save's copy wins over them`() = runTest {
        val settings = SettingsRepository(ResetFakePreferences(), now = { 50L })
        settings.setDiceStyleId("frosted_ice")
        SettingsViewModel(settingsRepository = settings, achievementsRepository = ResetFakeStore()).resetAchievements()
        advanceUntilIdle()
        assertEquals(Stamped<SettingValue?>(SettingValue.Text(DiceStyles.default.id), 0L), settings.cloudSettings()["dice_style_id"])
    }

    @Test
    fun `resetting the leaderboard marks when - for every device of the synced account`() = runTest {
        val online = OnlineSettingsRepository(ResetFakePreferences())
        val viewModel = SettingsViewModel(
            scoreRepository = ScoreRepository(EmptyScoreDao),
            onlineSettingsRepository = online,
            now = { 1_234L },
        )
        viewModel.resetLeaderboard()
        advanceUntilIdle()
        assertEquals(1_234L, online.scoresResetAt())
    }
}
