package net.zodac.dicefive.data.settings

import net.zodac.dicefive.model.StoredId

/**
 * "Pause when idle", one of the Advanced graphics settings ([MotionSettings.idleTimeout]): how long after the last touch
 * every infinite animation freezes (`ScreenIdle`), or never ([OFF]). In order of least motion to most: [OFF] freezes
 * nothing. Saved by [id]; [SECONDS_60] is the default.
 */
enum class IdleTimeout(override val id: String, val seconds: Int?) : StoredId {
    SECONDS_30("30", 30),
    SECONDS_60("60", 60),
    SECONDS_90("90", 90),
    OFF("OFF", null),
    ;

    /** How long an untouched screen waits before it freezes, or null for never. */
    val millis: Long? get() = seconds?.let { it * MILLIS_PER_SECOND }

    companion object {
        val default = SECONDS_60

        private const val MILLIS_PER_SECOND = 1_000L
    }
}
