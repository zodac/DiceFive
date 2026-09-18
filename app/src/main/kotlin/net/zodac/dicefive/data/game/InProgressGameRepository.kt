package net.zodac.dicefive.data.game

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.zodac.dicefive.model.GameState

private val Context.inProgressGameDataStore by preferencesDataStore(name = "in_progress_game")

/**
 * Persists at most one in-progress [GameState] so Play -> Continue can resume it, including
 * across process death. Cleared once the game finishes or a new game overwrites it.
 */
class InProgressGameRepository(private val context: Context) {

    val hasInProgressGame: Flow<Boolean> = context.inProgressGameDataStore.data.map { it[STATE_KEY] != null }

    suspend fun save(state: GameState) {
        context.inProgressGameDataStore.edit { it[STATE_KEY] = GameStateJson.encode(state) }
    }

    suspend fun load(): GameState? {
        val json = context.inProgressGameDataStore.data.first()[STATE_KEY] ?: return null
        return runCatching { GameStateJson.decode(json) }.getOrNull()
    }

    suspend fun clear() {
        context.inProgressGameDataStore.edit { it.remove(STATE_KEY) }
    }

    private companion object {
        val STATE_KEY = stringPreferencesKey("state")
    }
}
