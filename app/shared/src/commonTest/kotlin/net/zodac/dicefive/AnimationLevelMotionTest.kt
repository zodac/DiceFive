package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.data.settings.DiceRollStyle
import net.zodac.dicefive.data.settings.FrameRate
import net.zodac.dicefive.data.settings.MotionGroup
import net.zodac.dicefive.data.settings.MotionSettings
import net.zodac.dicefive.ui.common.LocalHoldStill
import net.zodac.dicefive.ui.common.LocalMotionSettings
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.ambientMotion
import net.zodac.dicefive.ui.common.groupReduceMotion
import net.zodac.dicefive.ui.common.rollReduceMotion
import net.zodac.dicefive.ui.common.scorePulse

/**
 * What each "Animations" level, and each group of the Advanced settings, lets move: a style's decoration (the dice's here) only at
 * High, the score pulse at High and Medium - and none of it (the roll included) under a [LocalReduceMotion] provided
 * further down, or a [LocalHoldStill] (how a Styles tile off screen is held still), whatever the settings. The
 * Menus & pages group's own Off leaves the other groups to theirs.
 */
class AnimationLevelMotionTest : UiTest() {

    private data class Motion(val ambient: Boolean, val pulse: Boolean, val rollStill: Boolean)

    private val showcase = Showcase { compose }

    private fun motionAt(motion: MotionSettings, reduceMotion: Boolean = !motion.menusMotion, holdStill: Boolean = false): Motion {
        var seen: Motion? = null
        showcase.show {
            CompositionLocalProvider(LocalMotionSettings provides motion, LocalReduceMotion provides reduceMotion, LocalHoldStill provides holdStill) {
                seen = Motion(ambientMotion(MotionGroup.DICE), scorePulse, rollReduceMotion)
            }
        }
        return checkNotNull(seen)
    }

    @Test
    fun `high moves everything - medium keeps only the score pulse - low and off still both - as does reduced motion further down`() = uiTest {
        assertEquals(Motion(ambient = true, pulse = true, rollStill = false), motionAt(AnimationLevel.HIGH.motion))
        assertEquals(Motion(ambient = false, pulse = true, rollStill = false), motionAt(AnimationLevel.MEDIUM.motion))
        assertEquals(Motion(ambient = false, pulse = false, rollStill = false), motionAt(AnimationLevel.LOW.motion))
        assertEquals(Motion(ambient = false, pulse = false, rollStill = true), motionAt(AnimationLevel.OFF.motion))
        assertEquals(Motion(ambient = false, pulse = false, rollStill = true), motionAt(AnimationLevel.HIGH.motion, reduceMotion = true), "reduced motion further down stills a high level too")
        assertEquals(Motion(ambient = false, pulse = false, rollStill = true), motionAt(AnimationLevel.HIGH.motion, holdStill = true), "a held-still tile")
        // Only off stops the gameplay's own motion.
        assertEquals(listOf(AnimationLevel.OFF), AnimationLevel.entries.filterNot { it.motion.menusMotion })
    }

    @Test
    fun `menus and pages off leaves the background - the glow and the roll to their own settings`() = uiTest {
        val gameplayOff = AnimationLevel.HIGH.motion.with(MotionGroup.MENUS, FrameRate.OFF)
        assertEquals(Motion(ambient = true, pulse = true, rollStill = false), motionAt(gameplayOff))
        // And the other way round: each group off on its own.
        assertEquals(Motion(ambient = false, pulse = true, rollStill = false), motionAt(AnimationLevel.HIGH.motion.with(MotionGroup.DICE, FrameRate.OFF)))
        assertEquals(Motion(ambient = true, pulse = false, rollStill = false), motionAt(AnimationLevel.HIGH.motion.with(MotionGroup.SCORE_GLOW, FrameRate.OFF)))
        assertEquals(Motion(ambient = true, pulse = true, rollStill = true), motionAt(AnimationLevel.HIGH.motion.copy(rollStyle = DiceRollStyle.OFF)))
    }

    @Test
    fun `banners - scores - and celebrations each still only by their own setting - not by menus and pages`() = uiTest {
        val menusOff = AnimationLevel.HIGH.motion.with(MotionGroup.MENUS, FrameRate.OFF).with(MotionGroup.SCORES, FrameRate.OFF)
        var stilled: Map<MotionGroup, Boolean>? = null
        showcase.show {
            CompositionLocalProvider(LocalMotionSettings provides menusOff, LocalReduceMotion provides !menusOff.menusMotion) {
                stilled = listOf(MotionGroup.BANNERS, MotionGroup.SCORES, MotionGroup.CELEBRATIONS).associateWith { groupReduceMotion(it) }
            }
        }
        assertEquals(mapOf(MotionGroup.BANNERS to false, MotionGroup.SCORES to true, MotionGroup.CELEBRATIONS to false), stilled)
    }

    @Test
    fun `only low rolls the dice as the simple flick through faces`() = uiTest {
        assertEquals(listOf(AnimationLevel.LOW), AnimationLevel.entries.filter { it.motion.simpleDiceRoll })
    }
}
