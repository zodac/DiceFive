package net.zodac.dicefive.data.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.data.orNullOnFailure
import net.zodac.dicefive.game.nowEpochMillis
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
 *
 * Every write, the clearing one too, is stamped with when it was written ([STAMP_KEY]): the cloud save keeps
 * whichever device's game is newest, and a finished game's clearing beats an older copy of it.
 */
class InProgressGameRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val now: () -> Long = ::nowEpochMillis,
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
                        is Pending.Save -> dataStore.edit {
                            it[STATE_KEY] = GameStateJson.encode(next.state)
                            it[STAMP_KEY] = now()
                        }
                        is Pending.Clear -> dataStore.edit {
                            it.remove(STATE_KEY)
                            it[STAMP_KEY] = now()
                        }
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

    /** The saved game (as `GameStateJson`) and when it was written, for the cloud save, once every queued write is done. */
    suspend fun cloudGame(): Stamped<String?> {
        pending.value?.let { queued -> written.first { it >= queued.sequence } }
        val prefs = dataStore.data.first()
        return Stamped(prefs[STATE_KEY], prefs[STAMP_KEY] ?: 0)
    }

    /**
     * Takes the cloud save's game in place of this device's if it was written later - one this app can't read is
     * skipped, rather than offering a Continue that can't load.
     */
    suspend fun mergeCloud(remote: Stamped<String?>) {
        if (remote.value != null && runCatching { GameStateJson.decode(remote.value) }.isFailure) return
        dataStore.edit { prefs ->
            if (remote.atEpochMillis <= (prefs[STAMP_KEY] ?: 0)) return@edit
            remote.value?.let { prefs[STATE_KEY] = it } ?: prefs.remove(STATE_KEY)
            prefs[STAMP_KEY] = remote.atEpochMillis
        }
    }

    /** Forgets the saved game and its stamp, as on a device that never had one - for another account signing in. */
    suspend fun wipe() {
        pending.value?.let { queued -> written.first { it >= queued.sequence } }
        dataStore.edit { it.clear() }
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
        val STAMP_KEY = longPreferencesKey("saved_at")
    }
}
