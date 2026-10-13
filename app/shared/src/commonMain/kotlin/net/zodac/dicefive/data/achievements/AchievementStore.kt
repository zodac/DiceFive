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

    /**
     * Reverts a single achievement back to locked, leaving every other unlock and every counter
     * untouched. Debug-only - see `AchievementsViewModel`'s superuser mode - there is no player
     * facing way to re-lock one achievement without wiping the whole set via [resetAll].
     */
    suspend fun forceLock(achievement: Achievement)

    /**
     * Runs [block] - a read, a decision and a write - with no other [locked] block on this store running at once, so
     * two can't both read "not unlocked yet" and both announce it. A store used from one coroutine at a time needs no
     * lock, which is the default.
     */
    suspend fun <T> locked(block: suspend () -> T): T = block()
}
