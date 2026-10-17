package net.zodac.dicefive.ui.menu

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceStyles

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
        val store = InMemoryAchievementStore()
        val viewModel = MenuViewModel(store, SettingsRepository(InMemoryPreferences()))
        viewModel.onDiceTapped()
        advanceUntilIdle()
        assertTrue(Achievement.NOT_THOSE_DICE in store.unlocked, "NOT_THOSE_DICE should pop, got ${store.unlocked}")
    }

    @Test
    fun `the logo follows the player's unlocked dice and cup picks - never flashing the defaults while they load`() = runTest {
        // No styles until the saved picks load - never a flash of the defaults.
        val saved = SettingsRepository(InMemoryPreferences())
        saved.setDiceStyleId("classic_oak")
        val loading = MenuViewModel(InMemoryAchievementStore(), saved)
        assertNull(loading.logoStyles.value)
        advanceUntilIdle()
        assertEquals("classic_oak", loading.logoStyles.value?.dice?.id)

        // There from the start when the app's copy of the picks has already loaded - and following it.
        val loaded = MutableStateFlow<SavedStyles?>(SavedStyles("classic_oak", "classic_black", "classic_navy", "classic_blue", AchievementsState()))
        val preloaded = MenuViewModel(InMemoryAchievementStore(), SettingsRepository(InMemoryPreferences()), savedStyles = loaded)
        assertEquals("classic_oak", preloaded.logoStyles.value?.dice?.id)
        assertEquals("classic_black", preloaded.logoStyles.value?.cup?.id)
        loaded.value = loaded.value?.copy(diceStyleId = "classic_ivory")
        advanceUntilIdle()
        assertEquals("classic_ivory", preloaded.logoStyles.value?.dice?.id)

        // It follows the player's dice and cup picks.
        val settings = SettingsRepository(InMemoryPreferences())
        val following = MenuViewModel(InMemoryAchievementStore(), settings)
        advanceUntilIdle()
        assertEquals(LogoStyles(DiceStyles.default, DiceCupStyles.default), following.logoStyles.value)
        settings.setDiceStyleId("classic_oak")
        settings.setDiceCupStyleId("classic_black")
        advanceUntilIdle()
        assertEquals("classic_oak", following.logoStyles.value?.dice?.id)
        assertEquals("classic_black", following.logoStyles.value?.cup?.id)

        // A pick whose style is locked is drawn as the default until it unlocks.
        val lockedSettings = SettingsRepository(InMemoryPreferences())
        val store = InMemoryAchievementStore()
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
