package net.zodac.dicefive.data.achievements

import kotlinx.coroutines.flow.Flow
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter

/**
 * Where achievement unlocks and progress live. [AchievementsRepository] is the real, DataStore
 * backed implementation; this interface exists so the path from a finished game to a stored
 * unlock can be tested on a plain JVM, with no `Context` and no DataStore.
 */
interface AchievementStore {

    val state: Flow<AchievementsState>

    suspend fun current(): AchievementsState

    /** Writes newly unlocked achievements and updated counters in one atomic edit. */
    suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>)

    /** Wipes every unlock and counter - the Settings screen's "Reset achievements". */
    suspend fun resetAll()
}
