package net.zodac.dicefive.online

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What this device has chosen about the online service, in a file of its own
 * ([net.zodac.dicefive.data.PreferencesFile.ONLINE]) so no reset button touches it: whether the service is used
 * at all (the only "off" Play Games allows, as it has no sign-out) and whether progress is saved to the cloud.
 */
class OnlineSettingsRepository(private val dataStore: DataStore<Preferences>) {

    /** Whether the service is used on this device. Off stops every call, without touching the account. */
    val useOnDevice: Flow<Boolean> = dataStore.data.map { it[USE_ON_DEVICE_KEY] ?: true }

    suspend fun setUseOnDevice(use: Boolean) {
        dataStore.edit { it[USE_ON_DEVICE_KEY] = use }
    }

    /** Whether progress is saved to the cloud (on by default once signed in). */
    val cloudSaveEnabled: Flow<Boolean> = dataStore.data.map { it[CLOUD_SAVE_KEY] ?: true }

    suspend fun setCloudSaveEnabled(enabled: Boolean) {
        dataStore.edit { it[CLOUD_SAVE_KEY] = enabled }
    }

    private companion object {
        val USE_ON_DEVICE_KEY = booleanPreferencesKey("use_on_device")
        val CLOUD_SAVE_KEY = booleanPreferencesKey("cloud_save_enabled")
    }
}
