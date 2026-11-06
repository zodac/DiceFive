package net.zodac.dicefive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
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
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
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

    private fun show(services: GameServices, viewModel: AchievementsViewModel = achievementsViewModel(), direction: LayoutDirection = LayoutDirection.Ltr) {
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalGameServices provides services,
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                DiceFiveTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        AchievementsScreen(viewModel = viewModel, onBack = {})
                    }
                }
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
    fun `signed in - the mark is centred between the count's end and the banner's`() = uiTest(SCREEN) {
        assertMarkCentred(earned = 0)
    }

    @Test
    fun `signed in - the mark stays centred as the count grows`() = uiTest(SCREEN) {
        assertMarkCentred(earned = Achievement.entries.size)
    }

    @Test
    fun `signed in - the mark is centred on the banner's other side right to left`() = uiTest(SCREEN) {
        assertMarkCentred(earned = 0, direction = LayoutDirection.Rtl)
    }

    @Test
    fun `signed in - the mark is centred in landscape's compact banner`() = uiTest(TestScreen(800, 360)) {
        assertMarkCentred(earned = 0)
    }

    private suspend fun assertMarkCentred(earned: Int, direction: LayoutDirection = LayoutDirection.Ltr) {
        val store = InMemoryAchievementStore()
        store.record(Achievement.entries.take(earned).associateWith { 1L }, emptyMap())
        show(BannerServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "k"))), achievementsViewModel(store = store), direction)
        val total = Achievement.entries.count { it.category != AchievementCategory.EASTER_EGGS }
        val description = "$earned of $total earned"
        val banner = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
        val count = compose.onNodeWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val row = banner.center.y.toInt()
        val mark = (0 until pixels.width).filter { pixels[it, row] == Color.Green }
        val rtl = direction == LayoutDirection.Rtl
        val before = if (rtl) count.left - mark.last() - 1 else mark.first() - count.right
        val after = if (rtl) mark.first() - banner.left else banner.right - mark.last() - 1
        assertTrue(abs(before - after) <= 1f, "$before before the mark, $after after it")
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
