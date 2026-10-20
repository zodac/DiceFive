package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.resetAchievementsScrollMemory
import net.zodac.dicefive.ui.common.BACK_ARROW_OUTSET
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.WIDE_CONTENT_MAX_WIDTH
import net.zodac.dicefive.ui.game.landscapeGameContentWidth
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Where a page's app bar puts its back arrow, title and controls - lined up on the content's column in landscape - and how
 * wide a wide page's content is beside the game's. The
 * arrow is measured by its centre, as its button's bounds are smaller than its 48dp touch target.
 */
@RunWith(AndroidJUnit4::class)
class PageHeaderLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun openAtTheTop() = resetAchievementsScrollMemory()

    private fun showPage(wide: Boolean, scrollable: Boolean) = compose.setContent {
        DiceFiveTheme {
            ScreenScaffold(title = "Page", onBack = {}, wide = wide, scrollable = scrollable) {
                Box(Modifier.fillMaxWidth().height(10.dp).testTag(CONTENT))
            }
        }
    }

    /** The page with, alongside, the width a Standard game's content takes in the room the game screen has. */
    private fun showPageBesideGame(): () -> Dp {
        var gameWidth = 0.dp
        compose.setContent {
            DiceFiveTheme {
                Box(Modifier.fillMaxSize()) {
                    BoxWithConstraints(
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                    ) { gameWidth = landscapeGameContentWidth(maxWidth, maxHeight) }
                    ScreenScaffold(title = "Page", onBack = {}, wide = true) {
                        Box(Modifier.fillMaxWidth().height(10.dp).testTag(CONTENT))
                    }
                }
            }
        }
        compose.waitForIdle()
        return { gameWidth }
    }

    private fun assertNear(expected: Dp, actual: Dp, what: String) =
        assertEquals(what, expected.value, actual.value, 0.5f)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun `in landscape the back arrow lines up with a wide page's content and the title centres on it`() {
        showPage(wide = true, scrollable = false)
        val content = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        val title = compose.onNodeWithText("Page").getBoundsInRoot()
        assertNear(content.left + ARROW_HALF, (back.left + back.right) / 2, "back arrow's centre")
        assertNear((content.left + content.right) / 2, (title.left + title.right) / 2, "title's centre")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun `in landscape the back arrow lines up with a narrow scrolling page's content`() {
        showPage(wide = false, scrollable = true)
        val content = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        assertNear(content.left + ARROW_HALF, (back.left + back.right) / 2, "back arrow's centre")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun `on a landscape tablet a wide page's content is as wide as the game's`() {
        val gameWidth = showPageBesideGame()
        val content = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
        assertTrue("the game is wider than two panes here", gameWidth() > WIDE_CONTENT_MAX_WIDTH)
        assertNear(gameWidth(), content.right - content.left, "content's width")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun `on a phone on its side a wide page still fills the screen - wider than the height-bound game`() {
        val gameWidth = showPageBesideGame()
        val content = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
        assertTrue("the game is narrower than the page here", gameWidth() < content.right - content.left)
        // The screen less the column's 20dp padding each side, as before.
        assertNear(891.dp - 40.dp, content.right - content.left, "content's width")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `in portrait the back arrow's 48dp target stays 4dp from the screen's edge`() {
        showPage(wide = true, scrollable = false)
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        assertNear(4.dp + BACK_ARROW_OUTSET + ARROW_HALF, (back.left + back.right) / 2, "back arrow's centre")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun `in landscape Achievements has its count left of the title and Locked only right of it - both inside the list's column`() {
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext()),
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                DiceFiveTheme { AchievementsScreen(viewModel = achievementsViewModel(), onBack = {}) }
            }
        }
        compose.waitForIdle()
        val list = compose.onNode(hasScrollAction()).getBoundsInRoot()
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        val title = compose.onNodeWithText("Achievements").getBoundsInRoot()
        val count = compose.onNodeWithText(" unlocked", substring = true).getBoundsInRoot()
        val lockedOnly = compose.onNode(hasText("Locked only") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .getBoundsInRoot()
        assertNear(list.left + ARROW_HALF, (back.left + back.right) / 2, "back arrow's centre")
        assertTrue("the count follows the back arrow", count.left >= back.right)
        assertTrue("the count is left of the title", count.right < title.left)
        assertTrue("Locked only is right of the title", lockedOnly.left > title.right)
        assertNear(list.right, lockedOnly.right, "Locked only's end")
    }

    private companion object {
        const val CONTENT = "content"

        /** Half the 24dp arrow: lined up on the content's edge, its centre is this far inside it. */
        val ARROW_HALF = 12.dp
    }
}
