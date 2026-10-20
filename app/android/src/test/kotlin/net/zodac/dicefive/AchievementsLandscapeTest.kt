package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.navigation.AchievementScrollRequest
import net.zodac.dicefive.navigation.AchievementScrollRequests
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** On a landscape page the list has two achievements to a row - and a banner's long press still finds its row. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w891dp-h411dp")
class AchievementsLandscapeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showAchievements() {
        val viewModel = achievementsViewModel()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext()),
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        compose.waitUntil(10_000) { shows("unlocked") }
        // The screen remembers where it was scrolled to for the whole process: start every test at the top.
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.waitForIdle()
    }

    private fun shows(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the first two achievements share a row - side by side`() {
        showAchievements()
        val first = compose.onNodeWithText("The Journey Begins").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithText("Practice Makes Perfect").getUnclippedBoundsInRoot()
        assertEquals(first.top, second.top)
        assertTrue("The second is beside the first, not under it", second.left > first.right)
    }

    @Test
    fun `a banner's long press scrolls to its achievement - counted in rows of two`() {
        // The last achievement shown by name from the start, far down the list.
        val target = Achievement.entries.last { it.visibility == AchievementVisibility.NORMAL }
        val title = englishText(target.title)
        showAchievements()
        compose.onNodeWithText(title).assertDoesNotExist()
        AchievementScrollRequests.request(AchievementScrollRequest(target.id, animate = false))
        compose.waitUntil(10_000) { shows(title) }
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
