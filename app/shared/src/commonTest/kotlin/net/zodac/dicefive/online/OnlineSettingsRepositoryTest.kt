package net.zodac.dicefive.online

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

private class OnlinePreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class OnlineSettingsRepositoryTest {

    @Test
    fun bothSwitchesAreOnUntilChanged() = runTest {
        val repository = OnlineSettingsRepository(OnlinePreferencesStore())
        assertTrue(repository.useOnDevice.first())
        assertTrue(repository.cloudSaveEnabled.first())
    }

    @Test
    fun eachSwitchIsKeptOnItsOwn() = runTest {
        val repository = OnlineSettingsRepository(OnlinePreferencesStore())
        repository.setUseOnDevice(false)
        assertFalse(repository.useOnDevice.first())
        assertTrue(repository.cloudSaveEnabled.first())
        repository.setCloudSaveEnabled(false)
        assertFalse(repository.cloudSaveEnabled.first())
    }
}
