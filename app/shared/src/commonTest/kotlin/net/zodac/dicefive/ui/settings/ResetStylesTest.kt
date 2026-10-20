package net.zodac.dicefive.ui.settings

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.data.settings.SettingValue
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.ui.style.DiceStyles

@OptIn(ExperimentalCoroutinesApi::class)
class ResetStylesTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `resetting achievements puts a locked dice pick back to the default - and keeps one that needs no achievement`() = runTest {
        for ((picked, afterReset) in listOf("frosted_ice" to DiceStyles.default.id, "classic_oak" to "classic_oak")) {
            val settings = SettingsRepository(InMemoryPreferences())
            settings.setDiceStyleId(picked)
            val viewModel = settingsViewModel(settings = settings)
            viewModel.resetAchievements()
            advanceUntilIdle()
            assertEquals(afterReset, settings.diceStyleId.first(), picked)
        }
    }

    @Test
    fun `the picks a reset puts back are this device's only - the cloud save's copy wins over them`() = runTest {
        val settings = SettingsRepository(InMemoryPreferences(), now = { 50L })
        settings.setDiceStyleId("frosted_ice")
        settingsViewModel(settings = settings).resetAchievements()
        advanceUntilIdle()
        assertEquals(Stamped<SettingValue?>(SettingValue.Text(DiceStyles.default.id), 0L), settings.cloudSettings()["dice_style_id"])
    }

    @Test
    fun `resetting the leaderboard marks when - for every device of the synced account`() = runTest {
        val online = OnlineSettingsRepository(InMemoryPreferences())
        val viewModel = settingsViewModel(online = online, now = { 1_234L })
        viewModel.resetLeaderboard()
        advanceUntilIdle()
        assertEquals(1_234L, online.scoresResetAt())
    }
}
