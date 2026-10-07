package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Achievements page's and its banners' words: the count, categories, a hidden row, and what each banner says and is called. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AchievementsTextTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showScreen() {
        val viewModel = AchievementsViewModel()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext()),
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        compose.waitForIdle()
    }

    private fun showBanners() {
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext())) {
                DiceFiveTheme {
                    AchievementBannerHost(isOnGameScreen = { false }, onAchievementSelected = {}, onStylesSelected = {}) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the page is titled and counts what is unlocked`() {
        showScreen()

        compose.onNodeWithText("Achievements").assertExists()
        compose.onNodeWithText("0 of ", substring = true).assertExists()
        compose.onNodeWithText(" unlocked", substring = true).assertExists()
        compose.onNodeWithContentDescription("Milestones").assertExists()
    }

    @Test
    fun `a hidden achievement's description is three question marks until it is earned`() {
        showScreen()

        // Hidden ones are all in Miscellaneous, further down the list: scroll to the first.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("???"))
        compose.onAllNodesWithText("???").onFirst().assertExists()
    }

    @Test
    fun `an unlock banner is read as the achievement unlocked and its description`() {
        showBanners()
        AchievementEvents.emit(AchievementEvent.Unlocked(Achievement.THE_JOURNEY_BEGINS))
        compose.waitForIdle()

        compose.onNode(hasContentDescription("Achievement unlocked: The Journey Begins. Start your first game")).assertExists()
    }

    @Test
    fun `a progress banner is read as its title and how far along`() {
        showBanners()
        AchievementEvents.emit(AchievementEvent.Progressed(Achievement.GAMES_10, 4, 5))
        compose.waitForIdle()

        compose.onNode(hasContentDescription("Getting Comfortable: 5 of 10")).assertExists()
        val config = compose.onAllNodesWithContentDescription("Getting Comfortable: 5 of 10").onFirst().fetchSemanticsNode().config
        assertEquals(true, SemanticsActions.OnClick in config)
    }
}
