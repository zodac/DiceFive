package net.zodac.dicefive.data.achievements

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter

private val Context.achievementsDataStore by preferencesDataStore(name = "achievements")

/**
 * Unlock timestamps and progress counters, on their **own** DataStore file rather than sharing
 * `settings` - "Reset achievements" is a `clear()` on this file, and must not take the settings or
 * the remembered player names with it.
 *
 * Keys are built from [Achievement.id] / [AchievementCounter.name], so storage survives the enum
 * being reordered or having entries inserted. An id that no longer matches an [Achievement]
 * (something renamed or removed in a later version) is ignored on read.
 */
class AchievementsRepository(private val context: Context) : AchievementStore {

    override val state: Flow<AchievementsState> = context.achievementsDataStore.data.map { prefs ->
        AchievementsState(
            unlockedAt = Achievement.entries.mapNotNull { achievement ->
                prefs[unlockedKey(achievement)]?.let { achievement to it }
            }.toMap(),
            counters = AchievementCounter.entries.mapNotNull { counter ->
                prefs[counterKey(counter)]?.let { counter to it }
            }.toMap(),
        )
    }

    override suspend fun current(): AchievementsState = state.first()

    /**
     * Writes newly unlocked achievements and updated counters in one edit, so a game that unlocks
     * several can't be half-persisted if the process dies mid-write.
     */
    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) {
        if (unlockedAt.isEmpty() && counters.isEmpty()) return
        context.achievementsDataStore.edit { prefs ->
            for ((achievement, timestamp) in unlockedAt) {
                // Never overwrite an existing timestamp: the unlock date is the first time it
                // happened, and the achievements list sorts on it.
                if (prefs[unlockedKey(achievement)] == null) prefs[unlockedKey(achievement)] = timestamp
            }
            for ((counter, value) in counters) {
                prefs[counterKey(counter)] = value
            }
        }
    }

    /** Wipes every unlock and counter - the Settings screen's "Reset achievements". */
    override suspend fun resetAll() {
        context.achievementsDataStore.edit { it.clear() }
    }

    override suspend fun forceLock(achievement: Achievement) {
        context.achievementsDataStore.edit { prefs -> prefs.remove(unlockedKey(achievement)) }
    }

    private companion object {
        fun unlockedKey(achievement: Achievement) = longPreferencesKey("unlocked_${achievement.id}")
        fun counterKey(counter: AchievementCounter) = intPreferencesKey("counter_${counter.name}")
    }
}
