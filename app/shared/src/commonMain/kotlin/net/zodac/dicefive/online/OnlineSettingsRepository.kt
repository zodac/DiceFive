package net.zodac.dicefive.online

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What this device has chosen about the online service, in a file of its own
 * ([net.zodac.dicefive.data.PreferencesFile.ONLINE]) so no reset button touches it: whether the player has signed
 * in here.
 */
class OnlineSettingsRepository(private val dataStore: DataStore<Preferences>) {

    /**
     * Whether the player signed in from Settings on this device, and hasn't signed out since. Until then the service
     * isn't started at all, so nothing signs in, or asks to, at launch.
     */
    val signedIn: Flow<Boolean> = dataStore.data.map { it[SIGNED_IN_KEY] ?: false }

    suspend fun setSignedIn(signedIn: Boolean) {
        dataStore.edit { it[SIGNED_IN_KEY] = signedIn }
    }

    private companion object {
        val SIGNED_IN_KEY = booleanPreferencesKey("signed_in")
    }
}
