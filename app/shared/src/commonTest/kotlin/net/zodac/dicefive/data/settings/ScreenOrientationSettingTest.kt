package net.zodac.dicefive.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.InMemoryPreferences

/** The saved "Orientation". */
class ScreenOrientationSettingTest {

    // The saved name, written by hand: what's on a phone already must keep reading back.
    private val screenOrientationKey = stringPreferencesKey("screen_orientation")

    @Test
    fun `the orientation defaults to Device - a saved one reads back - and an unknown one is the default`() = runTest {
        fun fresh() = InMemoryPreferences().let { it to SettingsRepository(it) }

        // Nothing saved is the default - turning with the device.
        assertEquals(ScreenOrientation.DEVICE, fresh().second.screenOrientation.first())

        // A saved orientation reads back.
        val (_, savedRepository) = fresh()
        savedRepository.setScreenOrientation(ScreenOrientation.LANDSCAPE)
        assertEquals(ScreenOrientation.LANDSCAPE, savedRepository.screenOrientation.first())

        // An unknown saved orientation reads as the default.
        val (unknownStore, unknownRepository) = fresh()
        unknownStore.edit { it[screenOrientationKey] = "UPSIDE_DOWN" }
        assertEquals(ScreenOrientation.DEVICE, unknownRepository.screenOrientation.first())
    }
}
