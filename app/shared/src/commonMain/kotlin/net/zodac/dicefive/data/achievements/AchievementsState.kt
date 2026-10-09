package net.zodac.dicefive.data.achievements

import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter

/**
 * Everything the app knows about this device's achievements: when each one was unlocked, and the
 * running totals the incremental ones are measured against.
 */
data class AchievementsState(
    val unlockedAt: Map<Achievement, Long> = emptyMap(),
    val counters: Map<AchievementCounter, Int> = emptyMap(),
) {

    fun isUnlocked(achievement: Achievement): Boolean = achievement in unlockedAt

    /**
     * How many achievements have been earned - the number a style's achievement-count lock is measured against.
     */
    val countedUnlocks: Int
        get() = unlockedAt.keys.count { it.countsTowardStyleLocks }

    fun counter(counter: AchievementCounter): Int = counters[counter] ?: 0

    /** How far along [achievement] is, capped at its target (a counter can run past it). */
    fun progress(achievement: Achievement): Int =
        achievement.counter?.let { counter(it).coerceAtMost(achievement.target) } ?: 0
}

/** Whether earning this achievement counts towards [AchievementsState.countedUnlocks]. */
val Achievement.countsTowardStyleLocks: Boolean
    @Suppress("UnusedReceiverParameter")
    get() = true
