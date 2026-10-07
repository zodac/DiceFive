package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.ui.common.LocalAnimationLevel
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.ambientMotion
import net.zodac.dicefive.ui.common.scorePulse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What each "Animations" level lets move: decoration on its own only at High, the score pulse at High and Medium -
 * and neither under a [LocalReduceMotion] provided further down (how a Styles tile off screen is held still), whatever
 * the level.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AnimationLevelMotionTest {

    @get:Rule
    val compose = createComposeRule()

    private data class Motion(val ambient: Boolean, val pulse: Boolean)

    private fun motionAt(level: AnimationLevel, reduceMotion: Boolean = !level.gameplayMotion): Motion {
        var seen: Motion? = null
        compose.setContent {
            CompositionLocalProvider(LocalAnimationLevel provides level, LocalReduceMotion provides reduceMotion) {
                seen = Motion(ambientMotion, scorePulse)
            }
        }
        compose.waitForIdle()
        return checkNotNull(seen)
    }

    @Test
    fun `high moves everything`() = assertEquals(Motion(ambient = true, pulse = true), motionAt(AnimationLevel.HIGH))

    @Test
    fun `medium stills decoration but keeps the score pulse`() = assertEquals(Motion(ambient = false, pulse = true), motionAt(AnimationLevel.MEDIUM))

    @Test
    fun `low stills decoration and the score pulse`() = assertEquals(Motion(ambient = false, pulse = false), motionAt(AnimationLevel.LOW))

    @Test
    fun `off stills everything`() = assertEquals(Motion(ambient = false, pulse = false), motionAt(AnimationLevel.OFF))

    @Test
    fun `reduced motion further down stills a high level too`() =
        assertEquals(Motion(ambient = false, pulse = false), motionAt(AnimationLevel.HIGH, reduceMotion = true))

    @Test
    fun `only off stops the gameplay's own motion`() {
        assertEquals(listOf(AnimationLevel.OFF), AnimationLevel.entries.filterNot { it.gameplayMotion })
    }
}
