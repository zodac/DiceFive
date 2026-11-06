package net.zodac.dicefive.data.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
 * whichever device's game is newest, and a finished game's clearing beats an older copy of it. A stamp is always later
 * than the one before it, even with the device's clock set back, and of two copies of the same game the one further on
 * wins whatever their stamps ([furtherOn]); a game finished here ([FINISHED_KEY]) is never taken from the cloud again.
 *
 * A game the crash handler asked to be removed ([SavedGameDiscard]) goes before anything here reads or writes the save,
 * so the menu never offers it, and like a finished game no copy of it is taken from the cloud again.
 */
class InProgressGameRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val now: () -> Long = ::nowEpochMillis,
    private val discard: SavedGameDiscard = SavedGameDiscard.None,
) {

    /** Done once a removal the crash handler asked for has been made, or there was none to make. */
    private val discardChecked: Deferred<Unit> =
        if (discard === SavedGameDiscard.None) CompletableDeferred(Unit) else scope.async { discardIfAsked() }

    /** Whether a game is saved - only when that changes, not on every move saved. */
    val hasInProgressGame: Flow<Boolean> = flow {
        discardChecked.await()
        emitAll(dataStore.data.map { it[STATE_KEY] != null }.distinctUntilChanged())
    }

    /** What should be on disk next - or null before anything's asked. */
    private val pending = MutableStateFlow<Pending?>(null)

    /** The [Pending.sequence] of the last write finished (or dropped), 0 before any. */
    private val written = MutableStateFlow(0)

    init {
        scope.launch {
            discardChecked.await()
            // A StateFlow's collector only ever sees the latest value, which is the conflation.
            pending.filterNotNull().collect { next ->
                // One lost save costs Continue a move or two, not the app: the queue stays alive for the next.
                orNullOnFailure {
                    when (next) {
                        is Pending.Save -> dataStore.edit {
                            it[STATE_KEY] = GameStateJson.encode(next.state)
                            it[STAMP_KEY] = nextStamp(it)
                        }
                        is Pending.Clear -> dataStore.edit {
                            it.remove(STATE_KEY)
                            it[STAMP_KEY] = nextStamp(it)
                            next.finished?.let { id -> it[FINISHED_KEY] = id.toHexString() }
                        }
                    }
                }
                written.value = next.sequence
            }
        }
    }

    /** A removal that fails leaves the request standing, for the next start to try again. */
    private suspend fun discardIfAsked() {
        orNullOnFailure {
            if (!discard.isRequested()) return
            dataStore.edit { prefs ->
                prefs[STATE_KEY]?.let(::readableGame)?.id?.let { prefs[FINISHED_KEY] = it.toHexString() }
                prefs.remove(STATE_KEY)
                prefs[STAMP_KEY] = nextStamp(prefs)
            }
            discard.done()
        }
    }

    /** Now, or just after the last stamp if the clock says earlier: a later write never looks older than the one before. */
    private fun nextStamp(prefs: Preferences): Long = maxOf(now(), (prefs[STAMP_KEY] ?: 0) + 1)

    /** Queues [state] to be saved, replacing whatever was still waiting to be. */
    fun save(state: GameState) {
        pending.update { current -> if (current is Pending.Save && current.state == state) current else Pending.Save(state, current.nextSequence()) }
    }

    /** The saved game, or null with none or one that can't be read: for what can do without it. Continue uses [loadToResume]. */
    suspend fun load(): GameState? = savedJson()?.let(::readableGame)

    /**
     * The saved game for Continue, or null with none.
     *
     * @throws UnresumableGameException if there's one that can't be read
     */
    suspend fun loadToResume(): GameState? {
        val json = savedJson() ?: return null
        return runCatching { GameStateJson.decode(json) }.getOrElse { throw UnresumableGameException(it) }
    }

    private suspend fun savedJson(): String? {
        discardChecked.await()
        pending.value?.let { queued -> written.first { it >= queued.sequence } }
        return dataStore.data.first()[STATE_KEY]
    }

    /** The saved game (as `GameStateJson`) and when it was written, for the cloud save, once every queued write is done. */
    suspend fun cloudGame(): Stamped<String?> {
        discardChecked.await()
        pending.value?.let { queued -> written.first { it >= queued.sequence } }
        val prefs = dataStore.data.first()
        return Stamped(prefs[STATE_KEY], prefs[STAMP_KEY] ?: 0)
    }

    /**
     * Takes the cloud save's game in place of this device's if it's a copy of the same game further on ([furtherOn]), or
     * else was written later - but never a copy of a game finished here, and never one this app can't read, rather than
     * offering a Continue that can't load.
     */
    suspend fun mergeCloud(remote: Stamped<String?>) {
        discardChecked.await()
        val remoteGame = remote.value?.let { runCatching { GameStateJson.decode(it) }.getOrNull() ?: return }
        dataStore.edit { prefs ->
            if (remoteGame?.id != null && remoteGame.id.toHexString() == prefs[FINISHED_KEY]) return@edit
            val local = Stamped(prefs[STATE_KEY], prefs[STAMP_KEY] ?: 0)
            if (!furtherOn(remote, local) && (remote.atEpochMillis <= local.atEpochMillis || furtherOn(local, remote))) return@edit
            remote.value?.let { prefs[STATE_KEY] = it } ?: prefs.remove(STATE_KEY)
            prefs[STAMP_KEY] = maxOf(remote.atEpochMillis, local.atEpochMillis)
        }
    }

    /** Forgets the saved game and its stamp, as on a device that never had one - for another account signing in. */
    suspend fun wipe() {
        discardChecked.await()
        pending.value?.let { queued -> written.first { it >= queued.sequence } }
        dataStore.edit { it.clear() }
    }

    /**
     * Queues the saved game to be removed, replacing whatever was still waiting to be saved. With [finished], the game
     * just over, no copy of that game is taken from the cloud again.
     */
    fun clear(finished: GameState? = null) {
        pending.update { current ->
            if (current is Pending.Clear && current.finished == finished?.id) current else Pending.Clear(finished?.id, current.nextSequence())
        }
    }

    /** One queued write, numbered in the order asked for. */
    private sealed interface Pending {
        val sequence: Int

        data class Save(val state: GameState, override val sequence: Int) : Pending

        data class Clear(val finished: Uuid?, override val sequence: Int) : Pending
    }

    private fun Pending?.nextSequence(): Int = (this?.sequence ?: 0) + 1

    private companion object {
        val STATE_KEY = stringPreferencesKey("state")
        val STAMP_KEY = longPreferencesKey("saved_at")

        /** The [GameState.id] of the last game finished here. */
        val FINISHED_KEY = stringPreferencesKey("finished_id")
    }
}

/** A saved game Continue can't read. Its message is developer text, and goes in a crash report. */
class UnresumableGameException(cause: Throwable) : IllegalStateException("The saved game can't be resumed", cause)

/**
 * The crash handler's request, which outlives the process it was made in, to remove a saved game that can't be resumed
 * ([net.zodac.dicefive.app.ResumeAttempt]).
 */
interface SavedGameDiscard {

    fun isRequested(): Boolean

    /** The saved game has gone: the request is over. */
    fun done()

    /** No such request is ever made. */
    object None : SavedGameDiscard {
        override fun isRequested(): Boolean = false

        override fun done() = Unit
    }
}

/**
 * Whether [a] is a copy of the same game as [b] (the same [GameState.id]) further on: more rolls made
 * ([GameState.rollsMade]). Of two copies of one game the further one wins whatever their stamps, so a copy set back by
 * a device's clock, or kept on another device, never takes back a roll. False when either is cleared, can't be read
 * or has no id.
 */
internal fun furtherOn(a: Stamped<String?>, b: Stamped<String?>): Boolean {
    val ours = a.value?.let(::readableGame) ?: return false
    val theirs = b.value?.let(::readableGame) ?: return false
    return ours.id != null && ours.id == theirs.id && ours.rollsMade > theirs.rollsMade
}

private fun readableGame(json: String): GameState? = runCatching { GameStateJson.decode(json) }.getOrNull()
