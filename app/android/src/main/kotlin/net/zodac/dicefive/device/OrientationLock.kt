package net.zodac.dicefive.device

import android.content.pm.ActivityInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import net.zodac.dicefive.data.settings.ScreenOrientation

/**
 * The orientation the player's "Orientation" setting asks for (see `AndroidPlatformServices.lockOrientation`), for
 * `MainActivity` to lock its window to. Held here rather than on an Activity, so a window recreated by the turn it
 * causes picks it up at once. Null until the setting is first read: the window is left as the manifest has it till then.
 */
internal object OrientationLock {
    val requested = MutableStateFlow<ScreenOrientation?>(null)

    /** Every orientation asked for, once there's one. */
    val changes = requested.filterNotNull()
}

/**
 * The window orientation for this setting: [ScreenOrientation.DEVICE] follows the device as the system's own rotation
 * setting allows, and the locks still turn the right way up either way round ("sensor" portrait and landscape).
 */
internal val ScreenOrientation.activityOrientation: Int
    get() = when (this) {
        ScreenOrientation.DEVICE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
