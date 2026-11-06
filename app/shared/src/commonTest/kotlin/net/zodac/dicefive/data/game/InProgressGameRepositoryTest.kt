package net.zodac.dicefive.data.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType

/** In-memory preferences that count how many times they've been written. */
private class CountingPreferencesStore : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data
    var writes = 0

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        writes++
        return transform(_data.value).also { _data.value = it }
    }
}

/** In-memory preferences whose writes fail while [failing] is set, and wait for [gate] while it's set. */
private class FlakyPreferencesStore : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data
    var failing = false
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        gate?.await()
        if (failing) throw IllegalStateException("disk full")
        return transform(_data.value).also { _data.value = it }
    }
}

private fun gameWithDie(value: Int) = GameState(
    players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)),
    dice = List(5) { Die(value = value) },
)

// runCurrent, not advanceUntilIdle: the write queue runs in backgroundScope (it never finishes, so
// it can't hold the test open), and advanceUntilIdle stops as soon as only background work is left.
@OptIn(ExperimentalCoroutinesApi::class)
class InProgressGameRepositoryTest {

    @Test
    fun `saves faster than they can be written collapse into the latest - and the last of a save and a clear wins`() = runTest {
        val store = CountingPreferencesStore()
        val repository = InProgressGameRepository(store, backgroundScope)
        repository.save(gameWithDie(1))
        repository.save(gameWithDie(2))
        repository.save(gameWithDie(3))
        runCurrent()
        assertEquals(1, store.writes)
        assertEquals(gameWithDie(3), repository.load())

        // A clear after a save wins...
        repository.save(gameWithDie(4))
        runCurrent()
        repository.clear()
        runCurrent()
        assertNull(repository.load())
        assertFalse(repository.hasInProgressGame.first())
        // ...and a save after a clear.
        repository.clear()
        repository.save(gameWithDie(5))
        runCurrent()
        assertEquals(gameWithDie(5), repository.load())
    }

    @Test
    fun `a write that fails is dropped - the queue carries on with the next and a load isn't left waiting for it`() = runTest {
        val store = FlakyPreferencesStore()
        val repository = InProgressGameRepository(store, backgroundScope)
        store.failing = true
        repository.save(gameWithDie(1))
        runCurrent()
        assertNull(repository.load(), "nothing was written")

        store.failing = false
        repository.save(gameWithDie(2))
        runCurrent()
        assertEquals(gameWithDie(2), repository.load())
    }

    @Test
    fun `a load waits for the save already queued - so it never reads an older move`() = runTest {
        val store = FlakyPreferencesStore()
        val repository = InProgressGameRepository(store, backgroundScope)
        repository.save(gameWithDie(1))
        runCurrent()

        val gate = CompletableDeferred<Unit>()
        store.gate = gate
        repository.save(gameWithDie(2))
        val loaded = async { repository.load() }
        runCurrent()
        assertFalse(loaded.isCompleted, "the load should wait for the queued save")

        gate.complete(Unit)
        runCurrent()
        assertTrue(loaded.isCompleted)
        assertEquals(gameWithDie(2), loaded.await())
    }

    @Test
    fun `a saved game that can't be read is nothing to warm up - but Continue says so rather than taking it for no game`() = runTest {
        val store = CountingPreferencesStore()
        val repository = InProgressGameRepository(store, backgroundScope)
        assertNull(repository.loadToResume())

        store.edit { it[stringPreferencesKey("state")] = "not a game" }

        assertNull(repository.load())
        val failure = assertFailsWith<UnresumableGameException> { repository.loadToResume() }
        assertEquals("The saved game can't be resumed", failure.message)
        assertTrue(repository.hasInProgressGame.first(), "still saved until the crash handler has it removed")
    }

    @Test
    fun `a removal the crash handler asked for comes before the menu sees the game - and no copy of it comes back from the cloud`() = runTest {
        val game = gameWithDie(4).copy(id = Uuid.random())
        val store = CountingPreferencesStore()
        InProgressGameRepository(store, backgroundScope).run {
            save(game)
            runCurrent()
        }
        val discard = RecordingDiscard(requested = true)

        val repository = InProgressGameRepository(store, backgroundScope, discard = discard)
        val saved = async { repository.hasInProgressGame.first() }
        runCurrent()

        assertFalse(saved.await())
        assertNull(repository.loadToResume())
        assertFalse(discard.requested, "the request is over once the game has gone")
        repository.mergeCloud(Stamped(GameStateJson.encode(game), Long.MAX_VALUE))
        assertNull(repository.load())
    }

    @Test
    fun `with no removal asked for the saved game stays - and a removal that can't be written stays asked for`() = runTest {
        val store = FlakyPreferencesStore()
        InProgressGameRepository(store, backgroundScope).run {
            save(gameWithDie(5))
            runCurrent()
        }

        val untouched = InProgressGameRepository(store, backgroundScope, discard = RecordingDiscard(requested = false))
        val kept = async { untouched.load() }
        runCurrent()
        assertEquals(gameWithDie(5), kept.await())

        store.failing = true
        val discard = RecordingDiscard(requested = true)
        val failed = InProgressGameRepository(store, backgroundScope, discard = discard)
        runCurrent()
        assertTrue(discard.requested)
        assertEquals(gameWithDie(5), failed.load())
    }
}

private class RecordingDiscard(var requested: Boolean) : SavedGameDiscard {
    override fun isRequested(): Boolean = requested

    override fun done() {
        requested = false
    }
}
