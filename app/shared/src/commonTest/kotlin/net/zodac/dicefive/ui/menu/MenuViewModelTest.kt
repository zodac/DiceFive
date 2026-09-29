package net.zodac.dicefive.ui.menu

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceStyles

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

    override suspend fun forceLock(achievement: Achievement) {
        _state.value = _state.value.copy(unlockedAt = _state.value.unlockedAt - achievement)
    }

    val unlocked: Set<Achievement> get() = _state.value.unlockedAt.keys
}

/** In-memory preferences, so a real [SettingsRepository] can back the menu's logo styles. */
private class FakePreferencesStore : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(_data.value).also { _data.value = it }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tapping the logo dice unlocks Not Those Dice`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = MenuViewModel(store)

        viewModel.onDiceTapped()
        advanceUntilIdle()

        assertTrue(Achievement.NOT_THOSE_DICE in store.unlocked, "NOT_THOSE_DICE should pop, got ${store.unlocked}")
    }

    @Test
    fun `tapping with no repository is a no-op - not a crash`() {
        val viewModel = MenuViewModel(achievementsRepository = null)

        viewModel.onDiceTapped()
    }

    @Test
    fun `the logo is drawn in the defaults when there is no repository`() {
        val viewModel = MenuViewModel(settingsRepository = null)

        assertEquals(LogoStyles(DiceStyles.default, DiceCupStyles.default), viewModel.logoStyles.value)
    }

    @Test
    fun `the logo has no styles until the saved picks load - never a flash of the defaults`() = runTest {
        val settings = SettingsRepository(FakePreferencesStore())
        settings.setDiceStyleId("fire")
        val viewModel = MenuViewModel(settingsRepository = settings)

        assertNull(viewModel.logoStyles.value)
        advanceUntilIdle()
        assertEquals("fire", viewModel.logoStyles.value?.dice?.id)
    }

    @Test
    fun `the logo follows the player's dice and cup picks`() = runTest {
        val settings = SettingsRepository(FakePreferencesStore())
        val viewModel = MenuViewModel(settingsRepository = settings)
        advanceUntilIdle()
        assertEquals(LogoStyles(DiceStyles.default, DiceCupStyles.default), viewModel.logoStyles.value)

        settings.setDiceStyleId("barrel")
        settings.setDiceCupStyleId("casino_black")
        advanceUntilIdle()

        assertEquals("barrel", viewModel.logoStyles.value?.dice?.id)
        assertEquals("casino_black", viewModel.logoStyles.value?.cup?.id)
    }

    @Test
    fun `a pick whose style is locked is drawn as the default until it unlocks`() = runTest {
        val settings = SettingsRepository(FakePreferencesStore())
        val store = FakeAchievementStore()
        settings.setDiceStyleId("googly_ivory")
        settings.setDiceCupStyleId("top_hat_black")
        val viewModel = MenuViewModel(store, settings)
        advanceUntilIdle()
        assertEquals(LogoStyles(DiceStyles.default, DiceCupStyles.default), viewModel.logoStyles.value)

        val everything = Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }
        store.record(everything.associateWith { 0L }, emptyMap())
        advanceUntilIdle()

        assertEquals("googly_ivory", viewModel.logoStyles.value?.dice?.id)
        assertEquals("top_hat_black", viewModel.logoStyles.value?.cup?.id)
    }
}
