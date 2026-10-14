package net.zodac.dicefive.ui.settings

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter

private class VersionTapStore : AchievementStore {
    private val _state = MutableStateFlow(AchievementsState())
    override val state: Flow<AchievementsState> = _state
    override suspend fun current(): AchievementsState = _state.value
    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) {
        _state.value = _state.value.copy(unlockedAt = _state.value.unlockedAt + unlockedAt)
    }
    override suspend fun resetAll() = Unit
    override suspend fun forceLock(achievement: Achievement) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class VersionTapTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Whether taps on the version number at each of [times] earn Are You A Developer?. */
    private fun earnedBy(vararg times: Long): Boolean {
        val store = VersionTapStore()
        val viewModel = SettingsViewModel(achievementsRepository = store)
        var earned = false
        runTest {
            times.forEach { viewModel.onVersionTapped(it) }
            advanceUntilIdle()
            earned = store.current().isUnlocked(Achievement.ARE_YOU_A_DEVELOPER)
        }
        return earned
    }

    @Test
    fun `seven quick taps on the version number - not six - earn Are You A Developer`() {
        assertTrue(earnedBy(*LongArray(7) { it * 300L }))
        assertFalse(earnedBy(*LongArray(6) { it * 300L }))
    }

    @Test
    fun `a slow tap starts the count again`() {
        val gap = SettingsViewModel.VERSION_TAP_GAP_MILLIS
        // Six quick, a pause, then one: one in a row, not seven.
        assertFalse(earnedBy(*(LongArray(6) { it * 300L } + (5 * 300L + gap + 1))))
        // The pause just within the gap still counts.
        assertTrue(earnedBy(*(LongArray(6) { it * 300L } + (5 * 300L + gap))))
        // Seven more after a pause earn it, counted from the pause.
        assertTrue(earnedBy(*(LongArray(3) { it * 300L } + LongArray(7) { 10_000L + it * 300L })))
    }
}
