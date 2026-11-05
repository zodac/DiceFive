package net.zodac.dicefive.ui.settings

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.online.OnlineSettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ResetLeaderboardTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `resetting the leaderboard marks when - for every device of the synced account`() = runTest {
        val online = OnlineSettingsRepository(InMemoryPreferences())
        val viewModel = settingsViewModel(online = online, now = { 1_234L })
        viewModel.resetLeaderboard()
        advanceUntilIdle()
        assertEquals(1_234L, online.scoresResetAt())
    }
}
