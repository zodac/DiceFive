package net.zodac.dicefive.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.zodac.dicefive.game.style.StyleCategory

/** Each level's preset, the window's frame rate from the groups', and what counts as a level rather than none. */
class MotionSettingsTest {

    @Test
    fun `each level's preset moves as the level always has`() {
        // Each style's decoration, then the game's own (Menus & pages, Banners, Scores, Celebrations), Score glow, Cup
        // shake, and the dice's roll's rate - and the roll's style.
        val decoration = MotionGroup.decoration.size
        val gameplay = MotionGroup.gameplay.size
        assertEquals(
            listOf(
                List(decoration) { FrameRate.MAX } + List(gameplay) { FrameRate.MAX } + listOf(FrameRate.MAX, FrameRate.MAX, FrameRate.MAX),
                List(decoration) { FrameRate.OFF } + List(gameplay) { FrameRate.FPS_60 } + listOf(FrameRate.FPS_60, FrameRate.FPS_60, FrameRate.FPS_60),
                List(decoration) { FrameRate.OFF } + List(gameplay) { FrameRate.FPS_30 } + listOf(FrameRate.OFF, FrameRate.FPS_30, FrameRate.FPS_30),
                List(decoration) { FrameRate.OFF } + List(gameplay) { FrameRate.OFF } + listOf(FrameRate.OFF, FrameRate.OFF, FrameRate.FPS_30),
            ),
            AnimationLevel.entries.map { level -> MotionGroup.entries.map(level.motion::rate) },
        )
        assertEquals(
            listOf(DiceRollStyle.TOSSED, DiceRollStyle.TOSSED, DiceRollStyle.IN_PLACE, DiceRollStyle.OFF),
            AnimationLevel.entries.map { it.motion.rollStyle },
        )
        // The window's frames: uncapped at High, then 60, 30 and 30, as the levels were.
        assertEquals(listOf(null, 60, 30, 30), AnimationLevel.entries.map { it.motion.maxFramesPerSecond })
    }

    @Test
    fun `the window runs at the fastest moving group's rate - uncapped if one is at max - and at 30 with everything still`() {
        val off = AnimationLevel.OFF.motion
        assertEquals(30, off.maxFramesPerSecond)
        assertEquals(60, off.with(MotionGroup.SCORE_GLOW, FrameRate.FPS_60).maxFramesPerSecond)
        assertEquals(60, off.with(MotionGroup.MAT, FrameRate.FPS_30).with(MotionGroup.DICE_CUP, FrameRate.FPS_60).maxFramesPerSecond)
        assertNull(off.with(MotionGroup.BACKGROUND, FrameRate.MAX).maxFramesPerSecond)
        // A rate for the dice's roll that's off doesn't count; a tossed one's does - and the cup's shake counts on its own.
        assertEquals(30, off.with(MotionGroup.DICE_ROLL, FrameRate.MAX).maxFramesPerSecond)
        assertNull(off.with(MotionGroup.DICE_ROLL, FrameRate.MAX).copy(rollStyle = DiceRollStyle.TOSSED).maxFramesPerSecond)
        assertEquals(60, off.with(MotionGroup.CUP_SHAKE, FrameRate.FPS_60).maxFramesPerSecond)
    }

    @Test
    fun `the decoration groups are the styles with moving art - in the Styles page's order`() {
        // Score frames have none.
        assertEquals(
            listOf(StyleCategory.DICE, StyleCategory.DICE_CUP, StyleCategory.MAT, StyleCategory.BACKGROUND, StyleCategory.BOX),
            StyleCategory.entries - StyleCategory.FRAME,
        )
        assertEquals(listOf(MotionGroup.DICE, MotionGroup.DICE_CUP, MotionGroup.MAT, MotionGroup.BACKGROUND, MotionGroup.SCORE_BOX), MotionGroup.decoration)
    }

    @Test
    fun `each style's decoration moves on its own`() {
        val matStill = AnimationLevel.HIGH.motion.with(MotionGroup.MAT, FrameRate.OFF)
        assertEquals(MotionGroup.decoration - MotionGroup.MAT, MotionGroup.decoration.filter(matStill::moves))
    }

    @Test
    fun `settings match a level only if every group does - the dice's roll's rate aside while the roll is off`() {
        AnimationLevel.entries.forEach { assertEquals(it, AnimationLevel.matching(it.motion)) }
        assertNull(AnimationLevel.matching(AnimationLevel.HIGH.motion.with(MotionGroup.BANNERS, FrameRate.FPS_60)))
        assertNull(AnimationLevel.matching(AnimationLevel.HIGH.motion.copy(rollStyle = DiceRollStyle.IN_PLACE)))
        assertEquals(AnimationLevel.OFF, AnimationLevel.matching(AnimationLevel.OFF.motion.with(MotionGroup.DICE_ROLL, FrameRate.MAX)))
    }

    @Test
    fun `the dice roll unless their roll is off - only in place do they flick - and the cup shakes on its own`() {
        assertEquals(true, AnimationLevel.HIGH.motion.copy(rollStyle = DiceRollStyle.IN_PLACE).simpleDiceRoll)
        val diceStill = AnimationLevel.HIGH.motion.copy(rollStyle = DiceRollStyle.OFF)
        assertEquals(false, diceStill.rollMotion)
        assertEquals(false, diceStill.simpleDiceRoll)
        assertEquals(true, diceStill.cupShakeMotion)
        assertEquals(false, AnimationLevel.HIGH.motion.with(MotionGroup.CUP_SHAKE, FrameRate.OFF).cupShakeMotion)
    }

    @Test
    fun `settings read back from their ids - and anything else is null`() {
        val custom = AnimationLevel.LOW.motion.with(MotionGroup.SCORE_GLOW, FrameRate.MAX).with(MotionGroup.MAT, FrameRate.FPS_60)
        assertEquals(listOf("OFF", "OFF", "60", "OFF", "OFF", "30", "30", "30", "30", "MAX", "30", "30", "IN_PLACE"), custom.ids())
        assertEquals(custom, MotionSettings.fromIds(custom.ids()))
        assertNull(MotionSettings.fromIds(custom.ids().drop(1)))
        assertNull(MotionSettings.fromIds(custom.ids().dropLast(1) + "BOUNCED"))
    }
}
