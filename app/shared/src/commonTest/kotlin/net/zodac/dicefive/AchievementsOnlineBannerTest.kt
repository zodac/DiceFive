package net.zodac.dicefive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.online.LocalGameServices
import net.zodac.dicefive.online.NoGameServices
import net.zodac.dicefive.online.OnlineAccount
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.resetAchievementsScrollMemory
import net.zodac.dicefive.ui.theme.DiceFiveTheme

private class BannerServices(state: GameServicesState, override val canShowAchievementsUi: Boolean = state is GameServicesState.SignedIn) : GameServices by NoGameServices {
    override val serviceName = "Test Games"
    override val state: StateFlow<GameServicesState> = MutableStateFlow(state)
    var shown = 0
    var iconDrawn = false
    override fun showAchievementsUi() {
        shown++
    }

    @Composable
    override fun serviceIcon(): Painter {
        iconDrawn = true
        return ColorPainter(Color.Green)
    }
}

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(360, 800)

/** While signed in, the count banner is the way to the service's achievements, and carries the service's mark. */
class AchievementsOnlineBannerTest : UiTest() {

    private val spoken = "0 of ${Achievement.entries.count { it.category != AchievementCategory.EASTER_EGGS }} earned"

    @BeforeTest
    fun openAtTheTop() = resetAchievementsScrollMemory()

    private fun show(services: GameServices) {
        val viewModel = achievementsViewModel()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalGameServices provides services,
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `signed in - the banner is a button that opens the service's achievements - with its mark`() = uiTest(SCREEN) {
        val services = BannerServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "k")))
        show(services)
        val banner = compose.onNodeWithContentDescription(spoken)
        banner.assert(hasClickAction())
        banner.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        // TalkBack: "0 of 100 earned, button, double-tap to open Test Games", and no long-press action.
        banner.assert(SemanticsMatcher("double-tap to open the service") { it.config[SemanticsActions.OnClick].label == "Open Test Games" })
        banner.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        assertEquals(true, services.iconDrawn)
        banner.performClick()
        assertEquals(1, services.shown)
    }

    @Test
    fun `signed out - the banner offers nothing and has no mark`() = uiTest(SCREEN) {
        assertBannerIsPlain(BannerServices(GameServicesState.SignedOut()))
    }

    @Test
    fun `a service with no achievements screen - the banner offers nothing and has no mark`() = uiTest(SCREEN) {
        assertBannerIsPlain(BannerServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "k")), canShowAchievementsUi = false))
    }

    private fun assertBannerIsPlain(services: BannerServices) {
        show(services)
        val banner = compose.onNodeWithContentDescription(spoken)
        banner.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        banner.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        assertEquals(false, services.iconDrawn)
        banner.performClick()
        assertEquals(0, services.shown)
    }
}
