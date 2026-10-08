package net.zodac.dicefive.ui.settings

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
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
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

@OptIn(ExperimentalCoroutinesApi::class)
class ResetStylesTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `resetting achievements puts a locked dice pick back to the default - and keeps one that needs no achievement`() = runTest {
        for ((picked, afterReset) in listOf("frosted_ice" to DiceStyles.default.id, "barrel" to "barrel")) {
            val settings = SettingsRepository(ResetFakePreferences())
            settings.setDiceStyleId(picked)
            val viewModel = SettingsViewModel(settingsRepository = settings, achievementsRepository = ResetFakeStore())
            viewModel.resetAchievements()
            advanceUntilIdle()
            assertEquals(afterReset, settings.diceStyleId.first(), picked)
        }
    }
}
