package net.zodac.dicefive.data.achievements

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter

private class CloudPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

/** The achievements' side of the cloud save: this device's shares out, other devices' merged in, nothing announced. */
class AchievementsRepositoryCloudTest {

    private var clock = 1_000L
    private val repository = AchievementsRepository(CloudPreferences(), now = { clock })

    /** This file's own share id, as its save names it - the one share not in [others]. */
    private suspend fun ownShare(others: Set<String> = emptySet()): String = (repository.cloudAchievements().deviceCounters.keys - others).single()

    @Test
    fun `this device's save holds its unlocks - its counters as its own share - flags - and a stamped streak`() = runTest {
        repository.record(
            mapOf(Achievement.FIVE_X to 500L),
            mapOf(AchievementCounter.GAMES_PLAYED to 4, AchievementCounter.FIVE_X_NUMBERS to 0b11, AchievementCounter.WIN_STREAK to 2),
        )
        val own = ownShare()
        assertEquals(
            CloudAchievements(
                unlockedAt = mapOf(Achievement.FIVE_X.id to 500L),
                deviceCounters = mapOf(own to mapOf(AchievementCounter.GAMES_PLAYED.id to 4)),
                flagCounters = mapOf(AchievementCounter.FIVE_X_NUMBERS.id to 0b11),
                latestCounters = mapOf(AchievementCounter.WIN_STREAK.id to Stamped(2, 1_000L)),
            ),
            repository.cloudAchievements(),
        )
        // The same id every time, until a reset.
        assertEquals(own, ownShare())
    }

    @Test
    fun `another device's games add to this one's - and this one's share is what's left`() = runTest {
        repository.record(emptyMap(), mapOf(AchievementCounter.GAMES_PLAYED to 4))
        val own = ownShare()
        repository.mergeCloud(
            CloudAchievements(deviceCounters = mapOf("tablet" to mapOf(AchievementCounter.GAMES_PLAYED.id to 3), own to mapOf(AchievementCounter.GAMES_PLAYED.id to 2))),
        )
        assertEquals(7, repository.current().counter(AchievementCounter.GAMES_PLAYED))
        // A game here then counts 8, all but the tablet's 3 being this phone's.
        repository.record(emptyMap(), mapOf(AchievementCounter.GAMES_PLAYED to 8))
        assertEquals(
            mapOf("tablet" to mapOf(AchievementCounter.GAMES_PLAYED.id to 3), own to mapOf(AchievementCounter.GAMES_PLAYED.id to 5)),
            repository.cloudAchievements().deviceCounters,
        )
        // The same save merged in again changes nothing.
        repository.mergeCloud(CloudAchievements(deviceCounters = mapOf("tablet" to mapOf(AchievementCounter.GAMES_PLAYED.id to 3))))
        assertEquals(8, repository.current().counter(AchievementCounter.GAMES_PLAYED))
    }

    @Test
    fun `merged unlocks are quiet - at their earliest time - flags are joined - and a newer streak replaces this one`() = runTest {
        repository.record(mapOf(Achievement.FIVE_X to 500L), mapOf(AchievementCounter.FIVE_X_NUMBERS to 0b01, AchievementCounter.WIN_STREAK to 4))
        repository.mergeCloud(
            CloudAchievements(
                unlockedAt = mapOf(Achievement.FIVE_X.id to 300L, Achievement.BONUS_ROUND.id to 900L, "NOT_AN_ACHIEVEMENT" to 1L),
                flagCounters = mapOf(AchievementCounter.FIVE_X_NUMBERS.id to 0b10),
                latestCounters = mapOf(AchievementCounter.WIN_STREAK.id to Stamped(0, 2_000L)),
            ),
        )
        val state = repository.current()
        assertEquals(mapOf(Achievement.FIVE_X to 300L, Achievement.BONUS_ROUND to 900L), state.unlockedAt)
        assertEquals(0b11, state.counter(AchievementCounter.FIVE_X_NUMBERS))
        assertEquals(0, state.counter(AchievementCounter.WIN_STREAK))
        // An older streak doesn't.
        repository.mergeCloud(CloudAchievements(latestCounters = mapOf(AchievementCounter.WIN_STREAK.id to Stamped(9, 1_500L))))
        assertEquals(0, repository.current().counter(AchievementCounter.WIN_STREAK))
    }

    @Test
    fun `a reset here isn't marked - the same save merged in brings everything back`() = runTest {
        repository.record(mapOf(Achievement.FIVE_X to 500L), mapOf(AchievementCounter.GAMES_PLAYED to 4))
        repository.mergeCloud(CloudAchievements(deviceCounters = mapOf("tablet" to mapOf(AchievementCounter.GAMES_PLAYED.id to 3))))
        val saved = repository.cloudAchievements()
        repository.resetAll()
        assertEquals(AchievementsState(), repository.current())
        repository.mergeCloud(saved)
        assertEquals(AchievementsState(mapOf(Achievement.FIVE_X to 500L), mapOf(AchievementCounter.GAMES_PLAYED to 7)), repository.current())
    }

    @Test
    fun `games counted after a reset are kept when the save brings the old totals back`() = runTest {
        repository.record(emptyMap(), mapOf(AchievementCounter.GAMES_PLAYED to 10))
        val before = ownShare()
        val saved = repository.cloudAchievements()
        repository.resetAll()
        // Two games signed out, then the next sync.
        repository.record(emptyMap(), mapOf(AchievementCounter.GAMES_PLAYED to 2))
        val after = ownShare()
        repository.mergeCloud(saved)
        assertEquals(12, repository.current().counter(AchievementCounter.GAMES_PLAYED))
        assertEquals(
            mapOf(before to mapOf(AchievementCounter.GAMES_PLAYED.id to 10), after to mapOf(AchievementCounter.GAMES_PLAYED.id to 2)),
            repository.cloudAchievements().deviceCounters,
        )
    }
}
