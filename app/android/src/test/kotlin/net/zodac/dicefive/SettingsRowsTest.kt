package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.ui.settings.SettingsScreen
import net.zodac.dicefive.ui.settings.SettingsViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** In-memory preferences, so a real [SettingsRepository] can back the screen. */
internal class InMemoryPreferences : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(_data.value).also { _data.value = it }
}

/**
 * Each on/off setting is one toggle the width of its row - its label and switch a single target, for
 * a tap and for a screen reader - rather than a lone switch beside an unconnected label; the Animations
 * level is a segmented row of its four levels.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp")
class SettingsRowsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every setting is named - an on-off one a single toggle - Animations four levels - each reset a button row confirming what it will do`() {
        val viewModel = SettingsViewModel(settingsRepository = SettingsRepository(InMemoryPreferences()))
        compose.setContent {
            // Only for the version line at the foot of the page.
            CompositionLocalProvider(LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext())) {
                DiceFiveTheme { SettingsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        // The page names every switch - and its version and two links.
        listOf("Settings", "Sound effects", "Vibration", "Animations", "Confirm leaving game", "About", "Licences").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNode(hasText("Version ", substring = true)).assertExists()

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

        // Each reset is one button row - one node carrying the name and what it does, a single TalkBack stop - below the
        // fold on the test's small screen.
        val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val achievements = compose.onNode(hasText("Reset achievements") and hasText("Lock every achievement again") and hasClickAction() and isButton)
        achievements.performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Reset leaderboard") and hasText("Delete all recorded scores") and hasClickAction() and isButton).performScrollTo().assertIsDisplayed()

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
