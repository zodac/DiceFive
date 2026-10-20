package net.zodac.dicefive

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.data.settings.ScreenOrientation
import net.zodac.dicefive.data.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/** The saved "Orientation". */
class ScreenOrientationSettingTest {

    // The saved name, written by hand: what's on a phone already must keep reading back.
    private val screenOrientationKey = stringPreferencesKey("screen_orientation")

    @Test
    fun `the orientation defaults to Device - a saved one reads back - and an unknown one is the default`() = runBlocking {
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
