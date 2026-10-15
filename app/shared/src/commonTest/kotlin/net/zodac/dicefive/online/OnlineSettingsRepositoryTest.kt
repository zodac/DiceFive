package net.zodac.dicefive.online

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun noAccountSyncedAndMirroringAllowedUntilSet() = runTest {
        val repository = OnlineSettingsRepository(OnlinePreferencesStore())
        assertNull(repository.syncedAccount.first())
        assertFalse(repository.mirroringBlocked.first())
        repository.setSyncedAccount("ann")
        repository.blockMirroring()
        assertEquals("ann", repository.syncedAccount.first())
        assertTrue(repository.mirroringBlocked.first())
    }
}
