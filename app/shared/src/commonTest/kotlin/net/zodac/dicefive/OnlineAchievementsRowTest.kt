package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.online.LocalGameServices
import net.zodac.dicefive.online.NoGameServices
import net.zodac.dicefive.online.OnlineAccount
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.settings.SettingsScreen
import net.zodac.dicefive.ui.settings.SettingsViewModel
import net.zodac.dicefive.ui.settings.settingsViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme

private class AchievementsUiServices(state: GameServicesState) : GameServices by NoGameServices {
    override val serviceName = "Test Games"
    override val state: StateFlow<GameServicesState> = MutableStateFlow(state)
    override val canShowAchievementsUi = state is GameServicesState.SignedIn
    var shown = 0
    override fun showAchievementsUi() {
        shown++
    }
}

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(360, 800)

/**
 * While signed in, Reset achievements (which the service would undo) gives its place to a link to the service's
 * achievements, and Reset leaderboard's confirmation says the service's boards keep their scores.
 */
class OnlineAchievementsRowTest : UiTest() {

    private fun show(services: GameServices) {
        val viewModel = settingsViewModel()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalGameServices provides services,
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                DiceFiveTheme { SettingsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
    }

    @Test
    fun `signed in - the link is a button row in Reset achievements' place - and opens the service's screen`() = uiTest(SCREEN) {
        val services = AchievementsUiServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "k")))
        show(services)
        compose.onNodeWithText("Reset achievements").assertDoesNotExist()
        val link = compose.onNode(hasText("View in Test Games") and hasClickAction(), useUnmergedTree = false)
        link.performScrollTo()
        link.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        // TalkBack: "double-tap to open Test Games".
        link.assert(SemanticsMatcher("double-tap to open the service") { it.config[SemanticsActions.OnClick].label == "Open Test Games" })
        // The row is its label alone: no line under it.
        assertEquals(listOf("View in Test Games"), link.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
        link.performClick()
        assertEquals(1, services.shown)
        compose.onNodeWithText("Reset leaderboard").assertExists()
    }

    @Test
    fun `signed out - Reset achievements as before - and no link`() = uiTest(SCREEN) {
        show(AchievementsUiServices(GameServicesState.SignedOut()))
        compose.onNodeWithText("Reset achievements").assertExists()
        compose.onNodeWithText("View in Test Games").assertDoesNotExist()
    }

    private val resetMessage = "Every recorded score will be deleted, clearing the Leaderboard screen - and Statistics with it, since it's " +
        "calculated from the same scores. This can't be undone. Achievements and settings are not affected, though any achievement " +
        "progress measured against the leaderboard will start over."

    private fun resetLeaderboardMessage(services: GameServices): String {
        show(services)
        compose.onNodeWithText("Reset leaderboard").performScrollTo().performClick()
        compose.waitForIdle()
        return compose.onNodeWithText("Every recorded score", substring = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
    }

    @Test
    fun `signed in - Reset leaderboard's confirmation says the service's boards keep their scores`() = uiTest(SCREEN) {
        val message = resetLeaderboardMessage(AchievementsUiServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "k"))))
        assertEquals("$resetMessage Scores already sent to Test Games stay there.", message)
    }

    @Test
    fun `signed out - Reset leaderboard's confirmation as before`() = uiTest(SCREEN) {
        assertEquals(resetMessage, resetLeaderboardMessage(AchievementsUiServices(GameServicesState.SignedOut())))
    }
}
