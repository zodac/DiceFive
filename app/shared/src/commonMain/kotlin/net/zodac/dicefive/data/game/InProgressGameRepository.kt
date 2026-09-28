package net.zodac.dicefive.data.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.zodac.dicefive.model.GameState

/**
 * Persists at most one in-progress [GameState] so Play -> Continue can resume it, including
 * across process death. Cleared once the game finishes or a new game overwrites it.
 */
class InProgressGameRepository(private val dataStore: DataStore<Preferences>) {

    val hasInProgressGame: Flow<Boolean> = dataStore.data.map { it[STATE_KEY] != null }

    suspend fun save(state: GameState) {
        dataStore.edit { it[STATE_KEY] = GameStateJson.encode(state) }
    }

    suspend fun load(): GameState? {
        val json = dataStore.data.first()[STATE_KEY] ?: return null
        return runCatching { GameStateJson.decode(json) }.getOrNull()
    }

    suspend fun clear() {
        dataStore.edit { it.remove(STATE_KEY) }
    }

    private companion object {
        val STATE_KEY = stringPreferencesKey("state")
    }
}
