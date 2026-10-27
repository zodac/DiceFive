package net.zodac.dicefive.ui.settings

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.BuildInfo
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingValue
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.data.settings.savedStylesFlow
import net.zodac.dicefive.game.style.StyleCategory
import net.zodac.dicefive.game.style.access
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.purchases.PurchasesRepository
import net.zodac.dicefive.ui.style.DiceStyles

@OptIn(ExperimentalCoroutinesApi::class)
class ResetStylesTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `resetting achievements puts a locked dice pick back to the default - and keeps one that needs no achievement`() = runTest {
        for ((picked, afterReset) in listOf("frosted_ice" to DiceStyles.default.id, "classic_oak" to "classic_oak")) {
            val settings = SettingsRepository(InMemoryPreferences())
            settings.setDiceStyleId(picked)
            val viewModel = settingsViewModel(settings = settings)
            viewModel.resetAchievements()
            advanceUntilIdle()
            assertEquals(afterReset, settings.diceStyleId.first(), picked)
        }
    }

    @Test
    fun `resetting achievements keeps every pick of a player who owns every style - the purchase isn't reset`() = runTest {
        val settings = SettingsRepository(InMemoryPreferences())
        settings.setDiceStyleId("frosted_ice")
        settings.setTableBackgroundId("glitch_screen")
        settingsViewModel(settings = settings, ownsAllStyles = true).resetAchievements()
        advanceUntilIdle()
        assertEquals("frosted_ice", settings.diceStyleId.first())
        assertEquals("glitch_screen", settings.tableBackgroundId.first())
    }

    @Test
    fun `resetting achievements relocks nothing for an owner - the purchase survives, every style and colour stays unlocked`() = runTest {
        val container = AppContainer(
            scoreRepository = ScoreRepository(InMemoryScoreDao()),
            settingsRepository = SettingsRepository(InMemoryPreferences()),
            achievementsRepository = AchievementsRepository(InMemoryPreferences()),
            inProgressGameRepository = InProgressGameRepository(InMemoryPreferences()),
            onlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()),
            purchasesRepository = PurchasesRepository(InMemoryPreferences()),
            buildInfo = BuildInfo(versionName = "test", isDebug = false),
            scope = backgroundScope,
        )
        container.purchasesRepository.setOwnsAllStyles(true)
        container.achievementsRepository.record(Achievement.entries.associateWith { 0L }, emptyMap())
        // A secret colour, and a style behind an Easter egg.
        val secret = DiceStyles.families.flatMap { it.colours }.first { it.secretAchievement != null }.style.id
        container.settingsRepository.setDiceStyleId(secret)
        container.settingsRepository.setTableBackgroundId("glitch_screen")

        SettingsViewModel(
            settingsRepository = container.settingsRepository,
            achievementsRepository = container.achievementsRepository,
            scoreRepository = container.scoreRepository,
            onlineSettingsRepository = container.onlineSettingsRepository,
            ownsAllStyles = container.ownsAllStyles,
        ).resetAchievements()
        advanceUntilIdle()

        assertTrue(container.achievementsRepository.current().unlockedAt.isEmpty(), "the reset didn't happen")
        assertTrue(container.purchasesRepository.ownsAllStyles.first())
        val saved = savedStylesFlow(container.settingsRepository, container.achievementsRepository, container.ownsAllStyles).first()
        for (category in StyleCategory.entries) {
            for (id in category.registry.ids) assertTrue(category.registry.isUnlocked(id, saved.access), "$id is locked again")
        }
        assertEquals(secret, saved.diceStyleId)
        assertEquals("glitch_screen", saved.tableBackgroundId)
    }

    @Test
    fun `the picks a reset puts back are this device's only - the cloud save's copy wins over them`() = runTest {
        val settings = SettingsRepository(InMemoryPreferences(), now = { 50L })
        settings.setDiceStyleId("frosted_ice")
        settingsViewModel(settings = settings).resetAchievements()
        advanceUntilIdle()
        assertEquals(Stamped<SettingValue?>(SettingValue.Text(DiceStyles.default.id), 0L), settings.cloudSettings()["dice_style_id"])
    }

    @Test
    fun `resetting the leaderboard marks when - for every device of the synced account`() = runTest {
        val online = OnlineSettingsRepository(InMemoryPreferences())
        val viewModel = settingsViewModel(online = online, now = { 1_234L })
        viewModel.resetLeaderboard()
        advanceUntilIdle()
        assertEquals(1_234L, online.scoresResetAt())
    }
}
