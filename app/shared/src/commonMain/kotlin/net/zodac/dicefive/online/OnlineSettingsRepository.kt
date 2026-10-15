package net.zodac.dicefive.online

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * What this device has chosen about the online service, in a file of its own
 * ([net.zodac.dicefive.data.PreferencesFile.ONLINE]) so no reset button touches it: which account this
 * device last synced with, whether its achievements may be mirrored at all, and how far each account's scores have
 * been posted.
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

    /**
     * When the newest game posted to [accountKey]'s leaderboard for [leaderboardKey] was played (its
     * [net.zodac.dicefive.data.scores.ScoreEntry.timestampEpochMillis]), or null if that account has had none from this
     * device. Kept per account, so each one gets this device's best scores once, whichever signed in last.
     */
    suspend fun scoresPostedThrough(accountKey: String, leaderboardKey: String): Long? =
        dataStore.data.first()[scoresPostedKey(accountKey, leaderboardKey)]

    suspend fun setScoresPostedThrough(accountKey: String, leaderboardKey: String, timestampEpochMillis: Long) {
        dataStore.edit { it[scoresPostedKey(accountKey, leaderboardKey)] = timestampEpochMillis }
    }

    private companion object {
        fun scoresPostedKey(accountKey: String, leaderboardKey: String) = longPreferencesKey("scores_posted:$accountKey:$leaderboardKey")

        val MIRRORING_BLOCKED_KEY = booleanPreferencesKey("mirroring_blocked")
        val SYNCED_ACCOUNT_KEY = stringPreferencesKey("synced_account")
    }
}
