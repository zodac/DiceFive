package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlin.test.assertTrue
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.navigation.AchievementScrollRequest
import net.zodac.dicefive.navigation.AchievementScrollRequests
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.resetAchievementsScrollMemory
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import net.zodac.dicefive.ui.theme.GoldAccent
import org.jetbrains.compose.resources.getString

/** A banner's long press flashes its row gold, also when the screen was not open yet. */
class AchievementFlashTest : UiTest() {

    private fun goldPixels(title: String): Int {
        val bitmap = compose.onNodeWithText(title).captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        return pixels.count { it == GoldAccent.toArgb() }
    }

    @Test
    fun `a fresh arrival flashes its row gold`() = uiTest {
        resetAchievementsScrollMemory()
        val target = Achievement.entries.first { it.visibility == AchievementVisibility.NORMAL }
        val title = getString(target.title)
        val viewModel = achievementsViewModel()
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalAppContainer provides inMemoryAppContainer(), LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        AchievementScrollRequests.request(AchievementScrollRequest(target.id, animate = false))
        var flashed = false
        repeat(150) {
            mainClock.advanceTimeBy(16)
            if (!flashed && onAllNodes(hasText(title)).fetchSemanticsNodes().isNotEmpty()) flashed = goldPixels(title) > 0
        }
        assertTrue(flashed, "the row never flashed")
    }
}
