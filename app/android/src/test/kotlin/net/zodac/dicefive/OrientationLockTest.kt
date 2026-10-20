package net.zodac.dicefive

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.data.settings.ScreenOrientation
import net.zodac.dicefive.device.AndroidAppContainer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The saved "Orientation" reaches the window: the real app, through [MainActivity], locks to it and lets go of it. */
@RunWith(AndroidJUnit4::class)
class OrientationLockTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        init {
            // The rule's own recomposer and test clock, not the Activity's capped one - see MainActivity.useCappedFrameClock.
            MainActivity.useCappedFrameClock = false
        }
    }

    private val settings = AndroidAppContainer.get(ApplicationProvider.getApplicationContext()).settingsRepository

    @After
    fun turnWithTheDeviceAgain() = runBlocking { settings.setScreenOrientation(ScreenOrientation.DEVICE) }

    private fun lockedTo(expected: Int) = compose.waitUntil(timeoutMillis = 10_000) { compose.activity.requestedOrientation == expected }

    @Test
    fun `each orientation locks the window - and Device lets it turn again`() {
        lockedTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        runBlocking { settings.setScreenOrientation(ScreenOrientation.LANDSCAPE) }
        lockedTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        runBlocking { settings.setScreenOrientation(ScreenOrientation.PORTRAIT) }
        lockedTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)
        runBlocking { settings.setScreenOrientation(ScreenOrientation.DEVICE) }
        lockedTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }
}
