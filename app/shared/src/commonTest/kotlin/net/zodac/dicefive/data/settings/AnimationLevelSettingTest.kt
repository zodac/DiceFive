package net.zodac.dicefive.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.InMemoryPreferences

/** The saved "Animations" level, and its Advanced settings. */
class AnimationLevelSettingTest {

    // The saved names, written by hand: what's on a phone already must keep reading back.
    private val animationLevelKey = stringPreferencesKey("animation_level")
    private val backgroundKey = stringPreferencesKey("motion_background")
    private val rollStyleKey = stringPreferencesKey("motion_roll_style")

    private fun fresh() = InMemoryPreferences().let { it to SettingsRepository(it) }

    @Test
    fun `the level defaults to High - a saved level reads back - and an unknown one is the default`() = runTest {
        // Nothing saved is the default - High.
        assertEquals(AnimationLevel.HIGH, fresh().second.animationLevel.first())

        // A saved level reads back.
        val (_, savedRepository) = fresh()
        savedRepository.setAnimationLevel(AnimationLevel.MEDIUM)
        assertEquals(AnimationLevel.MEDIUM, savedRepository.animationLevel.first())

        // An unknown saved level reads as the default.
        val (unknownStore, unknownRepository) = fresh()
        unknownStore.edit { it[animationLevelKey] = "ULTRA" }
        assertEquals(AnimationLevel.HIGH, unknownRepository.animationLevel.first())
    }

    @Test
    fun `the motion is the level's preset until a group is changed - and picking a level puts every group back`() = runTest {
        val (store, repository) = fresh()
        assertEquals(AnimationLevel.HIGH.motion, repository.motionSettings.first())
        repository.setAnimationLevel(AnimationLevel.LOW)
        assertEquals(AnimationLevel.LOW.motion, repository.motionSettings.first())

        // A group changed is saved with the rest as they were, and matches no level.
        val custom = AnimationLevel.LOW.motion.with(MotionGroup.BACKGROUND, FrameRate.FPS_60)
        repository.setMotionSettings(custom)
        assertEquals(custom, repository.motionSettings.first())
        assertEquals(null, AnimationLevel.matching(custom))
        assertEquals("60", store.data.first()[backgroundKey])
        // The level saved under it stays, for the groups to fall back to.
        assertEquals(AnimationLevel.LOW, repository.animationLevel.first())

        // Picking a level clears them: every group is its preset again.
        repository.setAnimationLevel(AnimationLevel.MEDIUM)
        assertEquals(AnimationLevel.MEDIUM.motion, repository.motionSettings.first())
        assertEquals(null, store.data.first()[backgroundKey])
    }

    @Test
    fun `a group saved with an unknown id is the level's`() = runTest {
        val (store, repository) = fresh()
        repository.setAnimationLevel(AnimationLevel.MEDIUM)
        store.edit {
            it[backgroundKey] = "240"
            it[rollStyleKey] = "BOUNCED"
        }
        assertEquals(AnimationLevel.MEDIUM.motion, repository.motionSettings.first())
    }
}
