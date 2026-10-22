package net.zodac.dicefive.ui.styles

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.BuildInfo
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.data.settings.savedStylesFlow
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.purchases.PurchasesRepository

/** Whether every style is owned, as the app reads it: the purchase, or in a debug build the Styles page's hidden switch. */
@OptIn(ExperimentalCoroutinesApi::class)
class PretendOwnsAllStylesTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the purchase owns every style in any build`() = runTest {
        for (debug in listOf(false, true)) {
            val container = container(debug)
            assertFalse(container.ownsAllStyles.first())
            container.purchasesRepository.setOwnsAllStyles(true)
            assertTrue(container.ownsAllStyles.first(), "debug: $debug")
        }
    }

    @Test
    fun `pretending owns every style in a debug build - and never in a release one`() = runTest {
        assertTrue(container(debug = true).apply { pretendOwnsAllStyles.value = true }.ownsAllStyles.first())
        assertFalse(container(debug = false).apply { pretendOwnsAllStyles.value = true }.ownsAllStyles.first())
    }

    @Test
    fun `the saved picks carry it - so every screen draws an owner's picks`() = runTest {
        val settings = SettingsRepository(InMemoryPreferences())
        assertTrue(savedStylesFlow(settings, InMemoryAchievementStore(), flowOf(true)).first().ownsAllStyles)
        assertFalse(savedStylesFlow(settings, InMemoryAchievementStore(), flowOf(false)).first().ownsAllStyles)
    }

    @Test
    fun `five taps on the unlocked count turn pretending on in a debug build - five more turn it off - each said in a toast`() = runTest {
        val container = container(debug = true)
        val viewModel = stylesViewModel(container)
        val toasts = toasts(viewModel)
        repeat(StylesViewModel.PRETEND_TAP_TARGET - 1) { viewModel.onUnlockedCountTapped() }
        advanceUntilIdle()
        assertFalse(container.pretendOwnsAllStyles.value)
        assertEquals(emptyList(), toasts)

        viewModel.onUnlockedCountTapped()
        advanceUntilIdle()
        assertTrue(container.pretendOwnsAllStyles.value)
        assertTrue(container.ownsAllStyles.first())
        // Nothing earned with a pretended style can reach an online service.
        assertTrue(container.onlineSettingsRepository.mirroringBlocked.first())

        repeat(StylesViewModel.PRETEND_TAP_TARGET) { viewModel.onUnlockedCountTapped() }
        advanceUntilIdle()
        assertFalse(container.pretendOwnsAllStyles.value)
        assertEquals(listOf("Pretending all styles are owned", "No longer pretending all styles are owned"), toasts)
    }

    @Test
    fun `taps on the unlocked count do nothing in a release build`() = runTest {
        val container = container(debug = false)
        val viewModel = stylesViewModel(container)
        val toasts = toasts(viewModel)
        repeat(StylesViewModel.PRETEND_TAP_TARGET * 2) { viewModel.onUnlockedCountTapped() }
        advanceUntilIdle()
        assertFalse(container.pretendOwnsAllStyles.value)
        assertFalse(container.onlineSettingsRepository.mirroringBlocked.first())
        assertEquals(emptyList(), toasts)
    }

    private fun TestScope.toasts(viewModel: StylesViewModel): List<String> =
        mutableListOf<String>().also { toasts -> backgroundScope.launch { viewModel.toastMessages.collect { toasts += it } } }

    private fun stylesViewModel(container: AppContainer) = StylesViewModel(
        container.settingsRepository,
        container.achievementsRepository,
        ownsAllStyles = container.ownsAllStyles,
        isDebugBuild = container.buildInfo.isDebug,
        pretendOwnsAllStyles = container.pretendOwnsAllStyles,
        onlineSettingsRepository = container.onlineSettingsRepository,
    )

    private fun TestScope.container(debug: Boolean) = AppContainer(
        scoreRepository = ScoreRepository(InMemoryScoreDao()),
        settingsRepository = SettingsRepository(InMemoryPreferences()),
        achievementsRepository = AchievementsRepository(InMemoryPreferences()),
        inProgressGameRepository = InProgressGameRepository(InMemoryPreferences()),
        onlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()),
        purchasesRepository = PurchasesRepository(InMemoryPreferences()),
        buildInfo = BuildInfo(versionName = "test", isDebug = debug),
        scope = backgroundScope,
    )
}
