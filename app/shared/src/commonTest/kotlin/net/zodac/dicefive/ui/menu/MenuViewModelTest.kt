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
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
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
    fun `tapping the logo dice unlocks Not Those Dice - and with no repository is a no-op rather than a crash`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = MenuViewModel(store)
        viewModel.onDiceTapped()
        advanceUntilIdle()
        assertTrue(Achievement.NOT_THOSE_DICE in store.unlocked, "NOT_THOSE_DICE should pop, got ${store.unlocked}")

        MenuViewModel(achievementsRepository = null).onDiceTapped()
    }

    @Test
    fun `the logo follows the player's unlocked dice and cup picks - never flashing the defaults while they load`() = runTest {
        // The defaults when there is no repository.
        assertEquals(LogoStyles(DiceStyles.default, DiceCupStyles.default), MenuViewModel(settingsRepository = null).logoStyles.value)

        // No styles until the saved picks load - never a flash of the defaults.
        val saved = SettingsRepository(FakePreferencesStore())
        saved.setDiceStyleId("barrel")
        val loading = MenuViewModel(settingsRepository = saved)
        assertNull(loading.logoStyles.value)
        advanceUntilIdle()
        assertEquals("barrel", loading.logoStyles.value?.dice?.id)

        // There from the start when the app's copy of the picks has already loaded - and following it.
        val loaded = MutableStateFlow<SavedStyles?>(SavedStyles("barrel", "casino_black", "midnight_felt", "tray_blue", AchievementsState()))
        val preloaded = MenuViewModel(settingsRepository = SettingsRepository(FakePreferencesStore()), savedStyles = loaded)
        assertEquals("barrel", preloaded.logoStyles.value?.dice?.id)
        assertEquals("casino_black", preloaded.logoStyles.value?.cup?.id)
        loaded.value = loaded.value?.copy(diceStyleId = "ivory")
        advanceUntilIdle()
        assertEquals("ivory", preloaded.logoStyles.value?.dice?.id)

        // It follows the player's dice and cup picks.
        val settings = SettingsRepository(FakePreferencesStore())
        val following = MenuViewModel(settingsRepository = settings)
        advanceUntilIdle()
        assertEquals(LogoStyles(DiceStyles.default, DiceCupStyles.default), following.logoStyles.value)
        settings.setDiceStyleId("barrel")
        settings.setDiceCupStyleId("casino_black")
        advanceUntilIdle()
        assertEquals("barrel", following.logoStyles.value?.dice?.id)
        assertEquals("casino_black", following.logoStyles.value?.cup?.id)

        // A pick whose style is locked is drawn as the default until it unlocks.
        val lockedSettings = SettingsRepository(FakePreferencesStore())
        val store = FakeAchievementStore()
        lockedSettings.setDiceStyleId("googly_ivory")
        lockedSettings.setDiceCupStyleId("top_hat_black")
        val locked = MenuViewModel(store, lockedSettings)
        advanceUntilIdle()
        assertEquals(LogoStyles(DiceStyles.default, DiceCupStyles.default), locked.logoStyles.value)
        store.record(Achievement.entries.associateWith { 0L }, emptyMap())
        advanceUntilIdle()
        assertEquals("googly_ivory", locked.logoStyles.value?.dice?.id)
        assertEquals("top_hat_black", locked.logoStyles.value?.cup?.id)
    }
}
