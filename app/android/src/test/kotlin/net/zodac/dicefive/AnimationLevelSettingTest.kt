package net.zodac.dicefive

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.data.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The saved "Animations" level, and how a player's old "Remove animations" switch carries over to it. */
class AnimationLevelSettingTest {

    // The saved names, written by hand: what's on a phone already must keep reading back.
    private val removeAnimationsKey = booleanPreferencesKey("remove_animations")
    private val animationLevelKey = stringPreferencesKey("animation_level")

    @Test
    fun `the level defaults to High - the old switch carries over - a saved level wins and drops it - and an unknown one is the default`() = runBlocking {
        fun fresh() = InMemoryPreferences().let { it to SettingsRepository(it) }

        // Nothing saved is the default - High.
        assertEquals(AnimationLevel.HIGH, fresh().second.animationLevel.first())

        // The old switch on is Off, and off is the default.
        val (oldStore, oldRepository) = fresh()
        oldStore.edit { it[removeAnimationsKey] = true }
        assertEquals(AnimationLevel.OFF, oldRepository.animationLevel.first())
        oldStore.edit { it[removeAnimationsKey] = false }
        assertEquals(AnimationLevel.HIGH, oldRepository.animationLevel.first())

        // A saved level wins over the old switch, which is dropped on saving.
        val (savedStore, savedRepository) = fresh()
        savedStore.edit { it[removeAnimationsKey] = true }
        savedRepository.setAnimationLevel(AnimationLevel.MEDIUM)
        assertEquals(AnimationLevel.MEDIUM, savedRepository.animationLevel.first())
        assertNull(savedStore.data.first()[removeAnimationsKey])

        // An unknown saved level reads as the default.
        val (unknownStore, unknownRepository) = fresh()
        unknownStore.edit { it[animationLevelKey] = "ULTRA" }
        assertEquals(AnimationLevel.HIGH, unknownRepository.animationLevel.first())
    }
}
