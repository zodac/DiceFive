package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.ui.common.LocalAnimationLevel
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.ambientMotion
import net.zodac.dicefive.ui.common.scorePulse

/**
 * What each "Animations" level lets move: decoration on its own only at High, the score pulse at High and Medium -
 * and neither under a [LocalReduceMotion] provided further down (how a Styles tile off screen is held still), whatever
 * the level.
 */
class AnimationLevelMotionTest : UiTest() {

    private data class Motion(val ambient: Boolean, val pulse: Boolean)

    private val showcase = Showcase { compose }

    private fun motionAt(level: AnimationLevel, reduceMotion: Boolean = !level.gameplayMotion): Motion {
        var seen: Motion? = null
        showcase.show {
            CompositionLocalProvider(LocalAnimationLevel provides level, LocalReduceMotion provides reduceMotion) {
                seen = Motion(ambientMotion, scorePulse)
            }
        }
        return checkNotNull(seen)
    }

    @Test
    fun `high moves everything - medium keeps only the score pulse - low and off still both - as does reduced motion further down`() = uiTest {
        assertEquals(Motion(ambient = true, pulse = true), motionAt(AnimationLevel.HIGH))
        assertEquals(Motion(ambient = false, pulse = true), motionAt(AnimationLevel.MEDIUM))
        assertEquals(Motion(ambient = false, pulse = false), motionAt(AnimationLevel.LOW))
        assertEquals(Motion(ambient = false, pulse = false), motionAt(AnimationLevel.OFF))
        assertEquals(Motion(ambient = false, pulse = false), motionAt(AnimationLevel.HIGH, reduceMotion = true), "reduced motion further down stills a high level too")
        // Only off stops the gameplay's own motion.
        assertEquals(listOf(AnimationLevel.OFF), AnimationLevel.entries.filterNot { it.gameplayMotion })
    }

    @Test
    fun `only low rolls the dice as the simple flick through faces`() = uiTest {
        assertEquals(listOf(AnimationLevel.LOW), AnimationLevel.entries.filter { it.simpleDiceRoll })
    }
}
