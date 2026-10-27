package net.zodac.dicefive.data.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType

private fun game(dieValue: Int, id: Uuid? = null, rolls: Int = 0) = GameState(
    id = id,
    players = listOf(PlayerState(name = "Player 1", type = PlayerType.HUMAN, rollCount = rolls)),
    dice = List(5) { Die(value = dieValue) },
)

private val ONE_GAME = Uuid.parse("00000000-0000-0000-0000-000000000001")
private val ANOTHER_GAME = Uuid.parse("00000000-0000-0000-0000-000000000002")

/** The game in progress in the cloud save: every save and clear is stamped, and the newest copy wins. */
@OptIn(ExperimentalCoroutinesApi::class)
class InProgressGameRepositoryCloudTest {

    private var clock = 100L

    @Test
    fun `a save and a clear are each stamped`() = runTest {
        val repository = InProgressGameRepository(InMemoryPreferences(), backgroundScope, now = { clock })
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
        val repository = InProgressGameRepository(InMemoryPreferences(), backgroundScope, now = { clock })
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
    fun `a stamp is always later than the last - with the clock set back too`() = runTest {
        val repository = InProgressGameRepository(InMemoryPreferences(), backgroundScope, now = { clock })
        repository.save(game(3))
        runCurrent()
        clock = 40L
        repository.save(game(4))
        runCurrent()
        assertEquals(101L, repository.cloudGame().atEpochMillis)
        repository.clear()
        runCurrent()
        assertEquals(102L, repository.cloudGame().atEpochMillis)
    }

    @Test
    fun `the clock set back can't bring an earlier copy of the game back from the cloud - so a roll is never taken back`() = runTest {
        val repository = InProgressGameRepository(InMemoryPreferences(), backgroundScope, now = { clock })
        val beforeTheRoll = game(3, ONE_GAME, rolls = 4)
        repository.save(beforeTheRoll)
        runCurrent()
        val uploaded = repository.cloudGame()
        clock = 10L
        repository.save(game(1, ONE_GAME, rolls = 5))
        runCurrent()
        repository.mergeCloud(uploaded)
        assertEquals(game(1, ONE_GAME, rolls = 5), repository.load())
    }

    @Test
    fun `of two copies of the same game the further on wins - whatever their stamps - and another game goes by its stamp`() = runTest {
        val repository = InProgressGameRepository(InMemoryPreferences(), backgroundScope, now = { clock })
        repository.save(game(3, ONE_GAME, rolls = 5))
        runCurrent()
        // Another device's copy from before a roll, with a later stamp from that device's clock.
        repository.mergeCloud(Stamped(GameStateJson.encode(game(6, ONE_GAME, rolls = 4)), 900L))
        assertEquals(game(3, ONE_GAME, rolls = 5), repository.load())
        // A copy further on, with an earlier stamp: taken, and the stamp here doesn't go back.
        repository.mergeCloud(Stamped(GameStateJson.encode(game(2, ONE_GAME, rolls = 6)), 50L))
        assertEquals(game(2, ONE_GAME, rolls = 6), repository.load())
        assertEquals(100L, repository.cloudGame().atEpochMillis)
        // The same rolls - an undo, or a hold - go by the stamp.
        repository.mergeCloud(Stamped(GameStateJson.encode(game(4, ONE_GAME, rolls = 6)), 150L))
        assertEquals(game(4, ONE_GAME, rolls = 6), repository.load())
        // Another game, with fewer rolls, by its stamp too.
        repository.mergeCloud(Stamped(GameStateJson.encode(game(5, ANOTHER_GAME)), 200L))
        assertEquals(game(5, ANOTHER_GAME), repository.load())
    }

    @Test
    fun `a game finished here is never taken from the cloud again - however late its stamp`() = runTest {
        val repository = InProgressGameRepository(InMemoryPreferences(), backgroundScope, now = { clock })
        val playing = game(3, ONE_GAME, rolls = 2)
        repository.save(playing)
        runCurrent()
        repository.clear(finished = playing.copy(isGameOver = true))
        runCurrent()
        repository.mergeCloud(Stamped(GameStateJson.encode(playing), 5_000L))
        assertFalse(repository.hasInProgressGame.first())
        // Another game still comes across.
        repository.mergeCloud(Stamped(GameStateJson.encode(game(4, ANOTHER_GAME)), 5_000L))
        assertTrue(repository.hasInProgressGame.first())
    }

    @Test
    fun `a game this app can't read is never taken - so Continue never offers it`() = runTest {
        val repository = InProgressGameRepository(InMemoryPreferences(), backgroundScope, now = { clock })
        repository.mergeCloud(Stamped("{\"from\":\"a newer app\"}", 500L))
        assertFalse(repository.hasInProgressGame.first())
    }

    @Test
    fun `wiping forgets the game and its stamp`() = runTest {
        val repository = InProgressGameRepository(InMemoryPreferences(), backgroundScope, now = { clock })
        repository.save(game(3))
        runCurrent()
        repository.wipe()
        assertEquals(Stamped<String?>(null, 0), repository.cloudGame())
    }
}
