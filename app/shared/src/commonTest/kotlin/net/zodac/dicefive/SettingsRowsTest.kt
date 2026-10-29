package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import kotlin.test.Test
import kotlin.test.assertTrue
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.settings.SettingsScreen
import net.zodac.dicefive.ui.settings.SettingsViewModel
import net.zodac.dicefive.ui.settings.settingsViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(360, 800)

/**
 * Each on/off setting is one toggle the width of its row - its label and switch a single target, for
 * a tap and for a screen reader - rather than a lone switch beside an unconnected label; the Animations
 * level is a segmented row of its four levels, and the Orientation one of its three.
 */
class SettingsRowsTest : UiTest() {

    @Test
    fun `every setting is named - an on-off one a single toggle - Animations four levels - Orientation three - each reset a button row confirming what it will do`() = uiTest(SCREEN) {
        val viewModel = settingsViewModel()
        compose.setContent {
            // Only for the version line at the foot of the page.
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                DiceFiveTheme { SettingsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        // The page names every switch - and its version and two links.
        listOf("Settings", "Sound effects", "Vibration", "Confirm leaving game", "About", "Licences").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNode(hasText("Version ", substring = true)).assertExists()
        // The two choice rows have no visible title: each icon carries its name for a screen reader.
        listOf("Animations", "Orientation").forEach { compose.onNodeWithContentDescription(it).assertExists() }

        // Compact rows: a switch row is the 48dp touch target's height, a reset (label and a line under it) under M3's
        // 56dp one-line list item, where a ListItem would make it 72dp.
        compose.onNode(hasText("Sound effects") and isToggleable()).assertHeightIsEqualTo(48.dp)
        val resetHeight = compose.onNode(hasText("Reset leaderboard") and hasClickAction()).getUnclippedBoundsInRoot().height
        assertTrue(resetHeight < 56.dp, "Reset row $resetHeight tall")

        // Tapping a setting's label flips it - label and switch are one toggle.
        val vibration = compose.onNode(hasText("Vibration") and isToggleable())
        vibration.assertIsOn()
        vibration.performClick()
        vibration.assertIsOff()

        // Animations is a row of four levels, the current one selected.
        val level = { name: String -> compose.onNode(hasText(name) and isSelectable()) }
        level("High").assertIsSelected()
        listOf("Medium", "Low", "Off").forEach { level(it).assertIsNotSelected() }
        level("Low").performClick()
        level("Low").assertIsSelected()
        level("High").assertIsNotSelected()

        // Orientation is a row of three, turning with the device until another is picked - shown selected once it's saved.
        val orientation = { name: String -> compose.onNode(hasText(name) and isSelectable()) }
        orientation("Device").performScrollTo().assertIsSelected()
        listOf("Portrait", "Landscape").forEach { orientation(it).assertIsNotSelected() }
        orientation("Landscape").performClick()
        orientation("Landscape").assertIsSelected()
        orientation("Device").assertIsNotSelected()

        // Each reset is one button row - one node carrying the name and what it does, a single TalkBack stop - below the
        // fold on the test's small screen.
        val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        // TalkBack says what a tap does: "double-tap to reset".
        val saysReset = SemanticsMatcher("double-tap to reset") { it.config[SemanticsActions.OnClick].label == "Reset" }
        val achievements = compose.onNode(hasText("Reset achievements") and hasText("Lock every achievement again") and hasClickAction() and isButton and saysReset)
        achievements.performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Reset leaderboard") and hasText("Delete all recorded scores") and hasClickAction() and isButton and saysReset).performScrollTo().assertIsDisplayed()

        // Its confirmation says what it will do - and can be cancelled.
        compose.onNodeWithText("Reset leaderboard").performScrollTo().performClick()
        compose.onNodeWithText("Reset Leaderboard?").assertIsDisplayed()
        compose.onNodeWithText(
            "Every recorded score will be deleted, clearing the Leaderboard screen - and Statistics with it, since it's calculated from the same scores. " +
                "This can't be undone. Achievements and settings are not affected, though any achievement progress measured against the leaderboard will start over.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Reset").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Reset Leaderboard?").assertDoesNotExist()

        achievements.performClick()
        compose.onNodeWithText("Reset Achievements?").assertIsDisplayed()
        compose.onNodeWithText("Every achievement will be locked again and all progress towards them lost. This can't be undone. Your scores and settings are not affected.").assertIsDisplayed()
    }
}
