package net.zodac.dicefive.data.settings

import net.zodac.dicefive.model.StoredId

/**
 * The player's "Orientation" setting: whether the app turns with the device ([DEVICE]), or stays in [PORTRAIT] or
 * [LANDSCAPE] however the device is held. Saved by [id]; [DEVICE] is the default.
 */
enum class ScreenOrientation(override val id: String) : StoredId {
    DEVICE("DEVICE"),
    PORTRAIT("PORTRAIT"),
    LANDSCAPE("LANDSCAPE"),
    ;

    companion object {
        val default = DEVICE
    }
}
