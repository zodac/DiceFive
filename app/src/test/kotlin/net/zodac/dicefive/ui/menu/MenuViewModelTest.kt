package net.zodac.dicefive.ui.menu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** In-memory [AchievementStore], mirroring `GameAchievementsWiringTest`'s fake. */
private class FakeAchievementStore : AchievementStore {

    private val _state = MutableStateFlow(AchievementsState())
    override val state: Flow<AchievementsState> = _state

    override suspend fun current(): AchievementsState = _state.value

    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) {
        _state.value = AchievementsState(
            unlockedAt = _state.value.unlockedAt + unlockedAt.filterKeys { it !in _state.value.unlockedAt },
            counters = _state.value.counters + counters,
        )
    }

    override suspend fun resetAll() {
        _state.value = AchievementsState()
    }

    val unlocked: Set<Achievement> get() = _state.value.unlockedAt.keys
}

@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tapping the logo dice unlocks Not Those Dice`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = MenuViewModel(store)

        viewModel.onDiceTapped()
        advanceUntilIdle()

        assertTrue("NOT_THOSE_DICE should pop, got ${store.unlocked}", Achievement.NOT_THOSE_DICE in store.unlocked)
    }

    @Test
    fun `tapping with no repository is a no-op, not a crash`() {
        val viewModel = MenuViewModel(achievementsRepository = null)

        viewModel.onDiceTapped()
    }
}
