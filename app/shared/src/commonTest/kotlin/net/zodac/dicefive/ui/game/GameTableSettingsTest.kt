package net.zodac.dicefive.ui.game

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.ui.game.style.DiceStyles

/** In-memory preferences, so a real [SettingsRepository] can back the table's settings. */
private class TablePreferencesStore : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(_data.value).also { _data.value = it }
}

/** Just enough of an [AchievementStore] to set which achievements are unlocked. */
private class TableAchievementStore : AchievementStore {

    private val _state = MutableStateFlow(AchievementsState())
    override val state: Flow<AchievementsState> = _state

    override suspend fun current(): AchievementsState = _state.value

    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) {
        _state.value = _state.value.copy(unlockedAt = _state.value.unlockedAt + unlockedAt)
    }

    override suspend fun resetAll() {
        _state.value = AchievementsState()
    }

    override suspend fun forceLock(achievement: Achievement) {
        _state.value = _state.value.copy(unlockedAt = _state.value.unlockedAt - achievement)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameTableSettingsTest {

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
    fun `the table has no settings until they load - never a flash of the defaults - and draws a locked pick as the default until it unlocks`() = runTest {
        // With no repository the table is the defaults straight away.
        assertEquals(TableSettings(), GameViewModel().tableSettings.value)

        val settings = SettingsRepository(TablePreferencesStore())
        settings.setDiceStyleId("barrel")
        settings.setSoundEnabled(false)
        val viewModel = GameViewModel(settingsRepository = settings, achievementsRepository = TableAchievementStore())
        assertNull(viewModel.tableSettings.value)
        advanceUntilIdle()
        assertEquals("barrel", viewModel.tableSettings.value?.visualTheme?.diceStyle?.id)
        assertEquals(false, viewModel.tableSettings.value?.soundEnabled)

        val lockedSettings = SettingsRepository(TablePreferencesStore())
        val achievements = TableAchievementStore()
        lockedSettings.setDiceStyleId("googly_ivory")
        val locked = GameViewModel(settingsRepository = lockedSettings, achievementsRepository = achievements)
        advanceUntilIdle()
        assertEquals(DiceStyles.default, locked.tableSettings.value?.visualTheme?.diceStyle)
        achievements.record(Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }.associateWith { 0L }, emptyMap())
        advanceUntilIdle()
        assertEquals("googly_ivory", locked.tableSettings.value?.visualTheme?.diceStyle?.id)
    }
}
