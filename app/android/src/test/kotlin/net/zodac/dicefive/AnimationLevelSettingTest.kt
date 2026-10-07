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

    private val store = InMemoryPreferences()
    private val repository = SettingsRepository(store)

    // The saved names, written by hand: what's on a phone already must keep reading back.
    private val removeAnimationsKey = booleanPreferencesKey("remove_animations")
    private val animationLevelKey = stringPreferencesKey("animation_level")

    @Test
    fun `nothing saved is the default - High`() = runBlocking {
        assertEquals(AnimationLevel.HIGH, repository.animationLevel.first())
    }

    @Test
    fun `the old switch on is Off, and off is the default`() = runBlocking {
        store.edit { it[removeAnimationsKey] = true }
        assertEquals(AnimationLevel.OFF, repository.animationLevel.first())
        store.edit { it[removeAnimationsKey] = false }
        assertEquals(AnimationLevel.HIGH, repository.animationLevel.first())
    }

    @Test
    fun `a saved level wins over the old switch, which is dropped on saving`() = runBlocking {
        store.edit { it[removeAnimationsKey] = true }
        repository.setAnimationLevel(AnimationLevel.MEDIUM)
        assertEquals(AnimationLevel.MEDIUM, repository.animationLevel.first())
        assertNull(store.data.first()[removeAnimationsKey])
    }

    @Test
    fun `an unknown saved level reads as the default`() = runBlocking {
        store.edit { it[animationLevelKey] = "ULTRA" }
        assertEquals(AnimationLevel.HIGH, repository.animationLevel.first())
    }
}
