package net.zodac.dicefive.data.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private class CloudGamePreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

private fun game(dieValue: Int) = GameState(
    players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN)),
    dice = List(5) { Die(value = dieValue) },
)

/** The game in progress in the cloud save: every save and clear is stamped, and the newest copy wins. */
@OptIn(ExperimentalCoroutinesApi::class)
class InProgressGameRepositoryCloudTest {

    private var clock = 100L

    @Test
    fun `a save and a clear are each stamped`() = runTest {
        val repository = InProgressGameRepository(CloudGamePreferences(), backgroundScope, now = { clock })
        assertEquals(Stamped<String?>(null, 0), repository.cloudGame())
        repository.save(game(3))
        runCurrent()
        assertEquals(Stamped<String?>(GameStateJson.encode(game(3)), 100L), repository.cloudGame())
        clock = 200L
        repository.clear()
        runCurrent()
        assertEquals(Stamped<String?>(null, 200L), repository.cloudGame())
    }

    @Test
    fun `a newer game from the cloud replaces this one - an older one or a clearing older than it doesn't`() = runTest {
        val repository = InProgressGameRepository(CloudGamePreferences(), backgroundScope, now = { clock })
        repository.save(game(3))
        runCurrent()
        repository.mergeCloud(Stamped(GameStateJson.encode(game(5)), 50L))
        repository.mergeCloud(Stamped(null, 99L))
        assertEquals(game(3), repository.load())
        repository.mergeCloud(Stamped(GameStateJson.encode(game(5)), 150L))
        assertEquals(game(5), repository.load())
        // A newer clearing - the game finished on the other device - removes it.
        repository.mergeCloud(Stamped(null, 250L))
        assertNull(repository.load())
        assertFalse(repository.hasInProgressGame.first())
    }

    @Test
    fun `a game this app can't read is never taken - so Continue never offers it`() = runTest {
        val repository = InProgressGameRepository(CloudGamePreferences(), backgroundScope, now = { clock })
        repository.mergeCloud(Stamped("{\"from\":\"a newer app\"}", 500L))
        assertFalse(repository.hasInProgressGame.first())
    }

    @Test
    fun `wiping forgets the game and its stamp`() = runTest {
        val repository = InProgressGameRepository(CloudGamePreferences(), backgroundScope, now = { clock })
        repository.save(game(3))
        runCurrent()
        repository.wipe()
        assertEquals(Stamped<String?>(null, 0), repository.cloudGame())
    }
}
