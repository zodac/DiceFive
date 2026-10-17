package net.zodac.dicefive.data.achievements

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter

/**
 * An [AchievementStore] kept in memory, for any test that needs one: an unlock keeps its first time, as the real store's
 * does, and [unlocked] reads what has been unlocked.
 */
class InMemoryAchievementStore(initial: AchievementsState = AchievementsState()) : AchievementStore {

    val mutableState = MutableStateFlow(initial)
    override val state: Flow<AchievementsState> = mutableState

    override suspend fun current(): AchievementsState = mutableState.value

    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) {
        mutableState.value = AchievementsState(
            unlockedAt = mutableState.value.unlockedAt + unlockedAt.filterKeys { it !in mutableState.value.unlockedAt },
            counters = mutableState.value.counters + counters,
        )
    }

    override suspend fun resetAll() {
        mutableState.value = AchievementsState()
    }

    override suspend fun forceLock(achievement: Achievement) {
        mutableState.value = mutableState.value.copy(unlockedAt = mutableState.value.unlockedAt - achievement)
    }

    val unlocked: Set<Achievement> get() = mutableState.value.unlockedAt.keys
}
