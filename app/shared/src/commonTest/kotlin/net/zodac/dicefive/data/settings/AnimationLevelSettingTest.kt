package net.zodac.dicefive.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.InMemoryPreferences

/** The saved "Animations" level. */
class AnimationLevelSettingTest {

    // The saved name, written by hand: what's on a phone already must keep reading back.
    private val animationLevelKey = stringPreferencesKey("animation_level")

    @Test
    fun `the level defaults to High - a saved level reads back - and an unknown one is the default`() = runTest {
        fun fresh() = InMemoryPreferences().let { it to SettingsRepository(it) }

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
}
