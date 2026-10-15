package net.zodac.dicefive.ui.achievements

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.game.AchievementUpdate
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.online.OnlineSyncRequests

/**
 * The one way an achievement is earned: read [store], decide with [AchievementEngine], record the result, and only
 * then announce it ([announce]), so a banner can never outlive its unlock.
 *
 * Every read-decide-write runs one at a time - this unlocker's own, and inside [AchievementStore.locked] every
 * screen's: two overlapping would each read "not unlocked yet" and both announce the same achievement.
 */
class AchievementUnlocker(private val store: AchievementStore) {

    private val lock = Mutex()

    /** Unlocks whichever of [achievements] aren't yet, now. Returns what was recorded - empty if nothing was new. */
    suspend fun unlock(achievements: Set<Achievement>): AchievementUpdate =
        apply { state -> AchievementEngine.unlockNow(achievements, state, nowEpochMillis()) }

    /** Records and announces what [decide] makes of the stored achievements. Returns what was recorded. */
    suspend fun apply(decide: (AchievementsState) -> AchievementUpdate): AchievementUpdate = lock.withLock {
        store.locked {
            val update = decide(store.current())
            if (!update.isEmpty) {
                store.record(update.unlockedAt(), update.counters)
                announce(update)
                OnlineSyncRequests.request()
            }
            update
        }
    }
}
