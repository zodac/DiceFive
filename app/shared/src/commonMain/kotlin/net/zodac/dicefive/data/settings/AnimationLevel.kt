package net.zodac.dicefive.data.settings

import net.zodac.dicefive.model.StoredId

/**
 * The player's "Animations" setting: how much of the app moves, and how fast it's drawn. A step down trades
 * smoothness and decoration for less work a frame (an older phone, a longer battery), never what the game does.
 *
 * Each level is a preset of [MotionSettings] ([motion]), which the Graphics page's Advanced graphics settings show and the
 * player can change group by group; settings matching no level leave none picked ([matching] is null).
 *
 * Saved by [id]. [HIGH] is the default; a player who had the old "Remove animations" switch on starts at [OFF].
 */
enum class AnimationLevel(override val id: String, val motion: MotionSettings) : StoredId {
    HIGH("HIGH", MotionSettings.of(decoration = FrameRate.MAX, gameplay = FrameRate.MAX, scoreGlow = FrameRate.MAX, cupShake = FrameRate.MAX, diceRoll = FrameRate.MAX, rollStyle = DiceRollStyle.TOSSED, idleTimeout = IdleTimeout.SECONDS_60)),
    MEDIUM("MEDIUM", MotionSettings.of(decoration = FrameRate.OFF, gameplay = FrameRate.FPS_60, scoreGlow = FrameRate.FPS_60, cupShake = FrameRate.FPS_60, diceRoll = FrameRate.FPS_60, rollStyle = DiceRollStyle.TOSSED, idleTimeout = IdleTimeout.SECONDS_60)),
    LOW("LOW", MotionSettings.of(decoration = FrameRate.OFF, gameplay = FrameRate.FPS_30, scoreGlow = FrameRate.OFF, cupShake = FrameRate.FPS_30, diceRoll = FrameRate.FPS_30, rollStyle = DiceRollStyle.IN_PLACE, idleTimeout = IdleTimeout.SECONDS_30)),
    // The dice's roll off by its style, rather than its rate, which is never off (MotionGroup.DICE_ROLL).
    OFF("OFF", MotionSettings.of(decoration = FrameRate.OFF, gameplay = FrameRate.OFF, scoreGlow = FrameRate.OFF, cupShake = FrameRate.OFF, diceRoll = FrameRate.FPS_30, rollStyle = DiceRollStyle.OFF, idleTimeout = IdleTimeout.SECONDS_30)),
    ;

    companion object {
        val default = HIGH

        /** The level whose preset [motion] is - the dice's roll's rate aside while the roll is off - or null for none. */
        fun matching(motion: MotionSettings): AnimationLevel? = entries.firstOrNull { it.motion.sameAs(motion) }
    }
}
