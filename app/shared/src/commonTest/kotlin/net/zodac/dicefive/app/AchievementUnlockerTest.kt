package net.zodac.dicefive.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.model.Achievement

/** In-memory preferences whose writes wait for [gate] while it's set - so a write can be caught half-way. */
private class GatedPreferences : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        gate?.await()
        return transform(_data.value).also { _data.value = it }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementUnlockerTest {

    @Test
    fun `two screens unlocking the same achievement at once record and announce it once`() = runTest {
        val preferences = GatedPreferences()
        val repository = AchievementsRepository(preferences)
        // The menu's and the game's: each screen has its own unlocker, over the one store.
        val first = AchievementUnlocker(repository, flowOf(false))
        val second = AchievementUnlocker(repository, flowOf(false))

        val gate = CompletableDeferred<Unit>()
        preferences.gate = gate
        val firstUpdate = async { first.unlock(setOf(Achievement.NOT_THOSE_DICE)) }
        val secondUpdate = async { second.unlock(setOf(Achievement.NOT_THOSE_DICE)) }
        // The first is now mid-write; without the store's lock the second would read "not unlocked yet" here too.
        runCurrent()
        gate.complete(Unit)

        val updates = listOf(firstUpdate.await(), secondUpdate.await())
        assertEquals(1, updates.count { !it.isEmpty }, "recorded once, got $updates")
        assertTrue(repository.current().isUnlocked(Achievement.NOT_THOSE_DICE))
    }

    @Test
    fun `unlocking what is already unlocked records nothing`() = runTest {
        val repository = AchievementsRepository(GatedPreferences())
        val unlocker = AchievementUnlocker(repository, flowOf(false))
        assertTrue(!unlocker.unlock(setOf(Achievement.WHO_MADE_THIS)).isEmpty)
        assertTrue(unlocker.unlock(setOf(Achievement.WHO_MADE_THIS)).isEmpty)
    }
}
