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
    fun signedOutUntilSignedIn() = runTest {
        val repository = OnlineSettingsRepository(OnlinePreferencesStore())
        assertFalse(repository.signedIn.first())
        repository.setSignedIn(true)
        assertTrue(repository.signedIn.first())
        repository.setSignedIn(false)
        assertFalse(repository.signedIn.first())
    }
}
