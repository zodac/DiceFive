package net.zodac.dicefive.ui.achievements

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
import net.zodac.dicefive.online.OnlineSettingsRepository

private class SuperuserPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

/** Superuser mode's cheated unlocks must never reach the online service: entering it blocks mirroring for good. */
@OptIn(ExperimentalCoroutinesApi::class)
class SuperuserMirroringTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun blockedAfter(taps: Int, isDebugBuild: Boolean): Boolean {
        val settings = OnlineSettingsRepository(SuperuserPreferences())
        val viewModel = AchievementsViewModel(isDebugBuild = isDebugBuild, onlineSettingsRepository = settings)
        var blocked = false
        runTest {
            repeat(taps) { viewModel.onUnlockedCountTapped() }
            advanceUntilIdle()
            blocked = settings.mirroringBlocked.first()
        }
        return blocked
    }

    @Test
    fun `entering superuser mode blocks mirroring - one tap short or a release build doesn't`() {
        assertTrue(blockedAfter(taps = 5, isDebugBuild = true))
        assertFalse(blockedAfter(taps = 4, isDebugBuild = true))
        assertFalse(blockedAfter(taps = 5, isDebugBuild = false))
    }
}
