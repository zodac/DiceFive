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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.zodac.dicefive.data.orNullOnFailure
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
 * A write that fails is dropped and the queue carries on; [load] waits for every write already
 * queued, so it never reads a move older than the last one asked for.
 */
class InProgressGameRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /** Whether a game is saved - only when that changes, not on every move saved. */
    val hasInProgressGame: Flow<Boolean> = dataStore.data.map { it[STATE_KEY] != null }.distinctUntilChanged()

    /** What should be on disk next - or null before anything's asked. */
    private val pending = MutableStateFlow<Pending?>(null)

    /** The [Pending.sequence] of the last write finished (or dropped), 0 before any. */
    private val written = MutableStateFlow(0)

    init {
        scope.launch {
            // A StateFlow's collector only ever sees the latest value, which is the conflation.
            pending.filterNotNull().collect { next ->
                // One lost save costs Continue a move or two, not the app: the queue stays alive for the next.
                orNullOnFailure {
                    when (next) {
                        is Pending.Save -> dataStore.edit { it[STATE_KEY] = GameStateJson.encode(next.state) }
                        is Pending.Clear -> dataStore.edit { it.remove(STATE_KEY) }
                    }
                }
                written.value = next.sequence
            }
        }
    }

    /** Queues [state] to be saved, replacing whatever was still waiting to be. */
    fun save(state: GameState) {
        pending.update { current -> if (current is Pending.Save && current.state == state) current else Pending.Save(state, current.nextSequence()) }
    }

    suspend fun load(): GameState? {
        pending.value?.let { queued -> written.first { it >= queued.sequence } }
        val json = dataStore.data.first()[STATE_KEY] ?: return null
        return runCatching { GameStateJson.decode(json) }.getOrNull()
    }

    /** Queues the saved game to be removed, replacing whatever was still waiting to be saved. */
    fun clear() {
        pending.update { current -> current as? Pending.Clear ?: Pending.Clear(current.nextSequence()) }
    }

    /** One queued write, numbered in the order asked for. */
    private sealed interface Pending {
        val sequence: Int

        data class Save(val state: GameState, override val sequence: Int) : Pending

        data class Clear(override val sequence: Int) : Pending
    }

    private fun Pending?.nextSequence(): Int = (this?.sequence ?: 0) + 1

    private companion object {
        val STATE_KEY = stringPreferencesKey("state")
    }
}
