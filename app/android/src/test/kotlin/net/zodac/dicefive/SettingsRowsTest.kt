package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
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
private class InMemoryPreferences : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(_data.value).also { _data.value = it }
}

/**
 * Each on/off setting is one toggle the width of its row - its label and switch a single target, for
 * a tap and for a screen reader - rather than a lone switch beside an unconnected label.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsRowsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping a setting's label flips it - label and switch are one toggle`() {
        val viewModel = SettingsViewModel(settingsRepository = SettingsRepository(InMemoryPreferences()))
        compose.setContent {
            // Only for the version line at the foot of the page.
            CompositionLocalProvider(LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext())) {
                DiceFiveTheme { SettingsScreen(viewModel = viewModel, onBack = {}) }
            }
        }

        val vibration = compose.onNode(hasText("Vibration") and isToggleable())
        vibration.assertIsOn()
        vibration.performClick()
        vibration.assertIsOff()
    }
}
