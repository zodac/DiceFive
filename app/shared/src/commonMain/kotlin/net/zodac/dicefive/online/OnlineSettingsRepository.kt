package net.zodac.dicefive.online

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What this device has chosen about the online service, in a file of its own
 * ([net.zodac.dicefive.data.PreferencesFile.ONLINE]) so no reset button touches it: which account this
 * device last synced with, and whether its achievements may be mirrored at all.
 */
class OnlineSettingsRepository(private val dataStore: DataStore<Preferences>) {

    /**
     * The account ([OnlineAccount.accountKey], a hash) this device last synced with, or null if it never has. A
     * different one signing in replaces the device's achievements with its own, so accounts' data never mix.
     */
    val syncedAccount: Flow<String?> = dataStore.data.map { it[SYNCED_ACCOUNT_KEY] }

    suspend fun setSyncedAccount(accountKey: String) {
        dataStore.edit { it[SYNCED_ACCOUNT_KEY] = accountKey }
    }

    /** True once superuser mode has been used on this install: its cheated unlocks are never mirrored, then or later. */
    val mirroringBlocked: Flow<Boolean> = dataStore.data.map { it[MIRRORING_BLOCKED_KEY] ?: false }

    suspend fun blockMirroring() {
        dataStore.edit { it[MIRRORING_BLOCKED_KEY] = true }
    }

    private companion object {
        val MIRRORING_BLOCKED_KEY = booleanPreferencesKey("mirroring_blocked")
        val SYNCED_ACCOUNT_KEY = stringPreferencesKey("synced_account")
    }
}
