package net.zodac.dicefive.data.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.GameState

/**
 * Persists at most one in-progress [GameState] so Play -> Continue can resume it, including
 * across process death. Cleared once the game finishes or a new game overwrites it.
 *
 * Writes are queued rather than awaited, and conflated: the game changes on every die held or
 * released, faster than a write can finish, so while one write is under way only the latest
 * state waiting behind it is kept - the states in between are never written, since the next one
 * supersedes them anyway. The queue runs in [scope], which lives as long as this repository (the
 * process), not the screen that asked, so leaving the game can't strand its last move unwritten.
 */
class InProgressGameRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    val hasInProgressGame: Flow<Boolean> = dataStore.data.map { it[STATE_KEY] != null }

    /** What should be on disk next - a game to save, or [Clear] - or null before anything's asked. */
    private val pending = MutableStateFlow<Any?>(null)

    init {
        scope.launch {
            // A StateFlow's collector only ever sees the latest value, which is the conflation.
            pending.filterNotNull().collect { next ->
                when (next) {
                    is GameState -> dataStore.edit { it[STATE_KEY] = GameStateJson.encode(next) }
                    else -> dataStore.edit { it.remove(STATE_KEY) }
                }
            }
        }
    }

    /** Queues [state] to be saved, replacing whatever was still waiting to be. */
    fun save(state: GameState) {
        pending.value = state
    }

    suspend fun load(): GameState? {
        val json = dataStore.data.first()[STATE_KEY] ?: return null
        return runCatching { GameStateJson.decode(json) }.getOrNull()
    }

    /** Queues the saved game to be removed, replacing whatever was still waiting to be saved. */
    fun clear() {
        pending.value = Clear
    }

    private object Clear

    private companion object {
        val STATE_KEY = stringPreferencesKey("state")
    }
}
