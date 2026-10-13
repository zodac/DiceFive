package net.zodac.dicefive.data.achievements

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter

/**
 * Unlock timestamps and progress counters, on their **own** DataStore file rather than sharing
 * `settings` - "Reset achievements" is a `clear()` on this file, and must not take the settings or
 * the remembered player names with it.
 *
 * Keys are built from [Achievement.id] / [AchievementCounter.name], so storage survives the enum
 * being reordered or having entries inserted. An id that no longer matches an [Achievement]
 * (something renamed or removed in a later version) is ignored on read.
 */
class AchievementsRepository(private val dataStore: DataStore<Preferences>) : AchievementStore {

    /** Emits only when an unlock or a counter changes, not on every write to the file. */
    override val state: Flow<AchievementsState> = dataStore.data.map { prefs ->
        AchievementsState(
            unlockedAt = UNLOCKED_KEYS.mapNotNull { (achievement, key) -> prefs[key]?.let { achievement to it } }.toMap(),
            counters = COUNTER_KEYS.mapNotNull { (counter, key) -> prefs[key]?.let { counter to it } }.toMap(),
        )
    }.distinctUntilChanged()

    override suspend fun current(): AchievementsState = state.first()

    /**
     * Writes newly unlocked achievements and updated counters in one edit, so a game that unlocks
     * several can't be half-persisted if the process dies mid-write.
     */
    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) {
        if (unlockedAt.isEmpty() && counters.isEmpty()) return
        dataStore.edit { prefs ->
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
        dataStore.edit { it.clear() }
    }

    override suspend fun forceLock(achievement: Achievement) {
        dataStore.edit { prefs -> prefs.remove(unlockedKey(achievement)) }
    }

    private companion object {
        /** Every achievement's and counter's key, built once rather than on every read of [state]. */
        val UNLOCKED_KEYS = Achievement.entries.map { it to longPreferencesKey("unlocked_${it.id}") }
        val COUNTER_KEYS = AchievementCounter.entries.map { it to intPreferencesKey("counter_${it.name}") }

        fun unlockedKey(achievement: Achievement) = UNLOCKED_KEYS[achievement.ordinal].second
        fun counterKey(counter: AchievementCounter) = COUNTER_KEYS[counter.ordinal].second
    }
}
