package net.zodac.dicefive.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.InMemoryPreferences

/** "Pause when idle", saved with the rest of the Advanced graphics settings, and each Animations level's preset of it. */
class IdleTimeoutSettingTest {

    // The saved name, written by hand: what's on a phone already must keep reading back.
    private val idleTimeoutKey = stringPreferencesKey("motion_idle_timeout")

    private fun fresh() = InMemoryPreferences().let { it to SettingsRepository(it) }

    @Test
    fun `each level's preset - 60s at High and Medium, 30s at Low and Off`() {
        assertEquals(
            listOf(IdleTimeout.SECONDS_60, IdleTimeout.SECONDS_60, IdleTimeout.SECONDS_30, IdleTimeout.SECONDS_30),
            listOf(AnimationLevel.HIGH, AnimationLevel.MEDIUM, AnimationLevel.LOW, AnimationLevel.OFF).map { it.motion.idleTimeout },
        )
    }

    @Test
    fun `it's the level's until changed - saved by its id - and picking a level puts it back`() = runTest {
        val (store, repository) = fresh()
        assertEquals(IdleTimeout.SECONDS_60, repository.motionSettings.first().idleTimeout)
        repository.setAnimationLevel(AnimationLevel.LOW)
        assertEquals(IdleTimeout.SECONDS_30, repository.motionSettings.first().idleTimeout)

        repository.setMotionSettings(AnimationLevel.LOW.motion.copy(idleTimeout = IdleTimeout.OFF))
        assertEquals(IdleTimeout.OFF, repository.motionSettings.first().idleTimeout)
        assertEquals("OFF", store.data.first()[idleTimeoutKey])
        assertNull(AnimationLevel.matching(repository.motionSettings.first()))

        repository.setAnimationLevel(AnimationLevel.HIGH)
        assertEquals(IdleTimeout.SECONDS_60, repository.motionSettings.first().idleTimeout)
        assertNull(store.data.first()[idleTimeoutKey])
    }

    @Test
    fun `an unknown saved one is the level's - and it isn't in the cloud save`() = runTest {
        val (store, repository) = fresh()
        repository.setAnimationLevel(AnimationLevel.OFF)
        store.edit { it[idleTimeoutKey] = "45" }
        assertEquals(IdleTimeout.SECONDS_30, repository.motionSettings.first().idleTimeout)

        repository.setMotionSettings(AnimationLevel.OFF.motion.copy(idleTimeout = IdleTimeout.SECONDS_90))
        repository.wipeSynced()
        assertEquals(emptyMap(), repository.cloudSettings())
        assertEquals(IdleTimeout.SECONDS_90, repository.motionSettings.first().idleTimeout)
    }

    @Test
    fun `each wait in milliseconds, least motion to most - off is none`() {
        assertEquals(listOf(30_000L, 60_000L, 90_000L, null), IdleTimeout.entries.map { it.millis })
    }
}
