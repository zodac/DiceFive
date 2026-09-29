package net.zodac.dicefive.data.achievements

import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.AchievementVisibility

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
     * How many achievements have been earned, leaving out [AchievementVisibility.SECRET] ones - the
     * number a style's achievement-count lock is measured against. A secret one is an easter egg, not
     * a checklist item, so it can't be what stands between a player and a style.
     */
    val countedUnlocks: Int
        get() = unlockedAt.keys.count { it.visibility != AchievementVisibility.SECRET }

    fun counter(counter: AchievementCounter): Int = counters[counter] ?: 0

    /** How far along [achievement] is, capped at its target (a counter can run past it). */
    fun progress(achievement: Achievement): Int =
        achievement.counter?.let { counter(it).coerceAtMost(achievement.target) } ?: 0
}
