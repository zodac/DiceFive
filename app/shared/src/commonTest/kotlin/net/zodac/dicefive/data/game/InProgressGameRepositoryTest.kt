package net.zodac.dicefive.data.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
}
