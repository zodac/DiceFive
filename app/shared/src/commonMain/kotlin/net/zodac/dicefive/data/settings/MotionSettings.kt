package net.zodac.dicefive.data.settings

import net.zodac.dicefive.model.StoredId
import net.zodac.dicefive.model.storedIdOf

/**
 * How fast one [MotionGroup] is drawn: not at all ([OFF]), at most 30 or 60 frames a second, or at whatever the screen
 * does ([MAX]: 90 or 120Hz on many phones, 60 on others). Saved by [id].
 */
enum class FrameRate(override val id: String, val framesPerSecond: Int?) : StoredId {
    OFF("OFF", null),
    FPS_30("30", 30),
    FPS_60("60", 60),
    MAX("MAX", null),
    ;

    /** Whether anything in the group moves at all. */
    val moves: Boolean get() = this != OFF
}

/**
 * How the dice roll, least to most: not at all ([OFF]: they snap to their results), flicking through faces where they
 * lie while the cup shakes ([IN_PLACE]), or swept into the cup and tossed back ([TOSSED]). Saved by [id].
 */
enum class DiceRollStyle(override val id: String) : StoredId {
    OFF("OFF"),
    IN_PLACE("IN_PLACE"),
    TOSSED("TOSSED"),
}

/**
 * The groups of the app's motion, each with a [FrameRate] of its own in [MotionSettings]. [key] names its saved setting
 * (`motion_<key>`), so it's permanent.
 *
 * The first five ([decoration]) are decoration that moves on its own while nothing is happening, by the style it's part
 * of: [DICE] (the RGB, Neon, Glitch, and Glitter dice, the googly dice's pupils, the golden egg's sparkle), [DICE_CUP]
 * (a cup's steam, bubbles, or swimming fish, the RGB cups), [MAT] (a mat's stars), [BACKGROUND] (a table background's
 * floating dice, stars, or waves, and the menu's drifting dice), and [SCORE_BOX] (the RGB and Neon boxes). Off, each
 * holds the still pose it has under reduced motion. These are what keep a frame drawn every refresh of an idle screen.
 */
enum class MotionGroup(val key: String) {
    DICE("dice"),
    DICE_CUP("cup"),
    MAT("mat"),
    BACKGROUND("background"),
    SCORE_BOX("score_box"),

    /**
     * Everything that moves without a group of its own: a page's content fading in, a list gliding to a spot, a picker
     * opening, the turn timer's flash, the tutorial's pulse, the sync bar filling. Off is the app's reduced motion
     * (`LocalReduceMotion`).
     */
    MENUS("menus"),

    /** The achievement banners: their slide and fade, and a progress count's climb. */
    BANNERS("banners"),

    /** The player tabs' scores counting up to a new total. */
    SCORES("scores"),

    /** The Game Over fireworks. */
    CELEBRATIONS("celebrations"),

    /** A scoring tile's gold glow: pulsing, or steady when off. */
    SCORE_GLOW("score_glow"),

    /** The cup's shake and pour. Off, it stands still through a roll, then is simply tipped. */
    CUP_SHAKE("cup_shake"),

    /**
     * The dice's toss (or flicker, [DiceRollStyle.IN_PLACE]). Never off: the dice stop with [DiceRollStyle.OFF]
     * instead.
     */
    DICE_ROLL("dice_roll"),
    ;

    companion object {
        /** The decoration groups, one for each style that has moving art, in the Styles page's order. */
        val decoration: List<MotionGroup> = listOf(DICE, DICE_CUP, MAT, BACKGROUND, SCORE_BOX)

        /** The game's own motion beside the roll and the score glow: what the "Animations" levels set together. */
        val gameplay: List<MotionGroup> = listOf(MENUS, BANNERS, SCORES, CELEBRATIONS)
    }
}

/**
 * How the app moves: a [FrameRate] for each [MotionGroup] ([rates]), the [rollStyle], and how long an untouched screen
 * waits before its infinite animations freeze ([idleTimeout]). Each [AnimationLevel] is one of these; the player's own
 * come from the Graphics page's Advanced graphics settings.
 *
 * Each group runs at its own rate, and the window at the fastest of those that move ([maxFramesPerSecond]).
 */
data class MotionSettings(
    val rates: Map<MotionGroup, FrameRate>,
    val rollStyle: DiceRollStyle,
    val idleTimeout: IdleTimeout,
) {
    init {
        require(rates.keys == MotionGroup.entries.toSet()) { "a rate for every group" }
    }

    /** [group]'s rate. */
    fun rate(group: MotionGroup): FrameRate = rates.getValue(group)

    /** These settings with [group] at [rate]. */
    fun with(group: MotionGroup, rate: FrameRate): MotionSettings = copy(rates = rates + (group to rate))

    /** Whether [group] moves: its rate isn't off, and for the dice's roll, its style isn't either. */
    fun moves(group: MotionGroup): Boolean = rate(group).moves && (group != MotionGroup.DICE_ROLL || rollStyle != DiceRollStyle.OFF)

    /** Whether menus and pages move - see [MotionGroup.MENUS]. Off is the app's reduced motion. */
    val menusMotion: Boolean get() = moves(MotionGroup.MENUS)

    /** Whether a scoring tile's gold glow pulses, rather than being steady. */
    val scorePulse: Boolean get() = moves(MotionGroup.SCORE_GLOW)

    /** Whether the dice roll at all: off, they snap to their results, as under reduced motion. */
    val rollMotion: Boolean get() = moves(MotionGroup.DICE_ROLL)

    /** Whether the cup shakes and pours: off, it stands still through a roll, then is simply tipped. */
    val cupShakeMotion: Boolean get() = moves(MotionGroup.CUP_SHAKE)

    /**
     * Whether the dice stay where they lie and flick through faces while the cup shakes, instead of being swept into it
     * and tossed back. The cup still shakes; the dice are at rest the moment it lands.
     */
    val simpleDiceRoll: Boolean get() = rollMotion && rollStyle == DiceRollStyle.IN_PLACE

    /**
     * The window's frame rate: that of the fastest group that moves, or null for whatever the screen does where one is
     * at [FrameRate.MAX]. With nothing moving, [STILL_FRAMES_PER_SECOND], for what's left (touch ripples, a switch's
     * thumb, a scroll's fling).
     */
    val maxFramesPerSecond: Int?
        get() {
            val moving = MotionGroup.entries.filter(::moves).map(::rate)
            return when {
                moving.isEmpty() -> STILL_FRAMES_PER_SECOND
                moving.any { it == FrameRate.MAX } -> null
                else -> moving.maxOf { checkNotNull(it.framesPerSecond) }
            }
        }

    /** Whether these are the same settings as [other], the dice's roll's rate aside where the roll is off (it isn't seen). */
    fun sameAs(other: MotionSettings): Boolean = normalised() == other.normalised()

    private fun normalised(): MotionSettings =
        if (rollMotion) this else copy(rates = rates + (MotionGroup.DICE_ROLL to FrameRate.FPS_30), rollStyle = DiceRollStyle.OFF)

    /** Each group's rate's id, then the roll style's, then the idle timeout's: what [fromIds] reads back. */
    fun ids(): List<String> = MotionGroup.entries.map { rate(it).id } + rollStyle.id + idleTimeout.id

    companion object {
        /** The window's frame rate with every group off. */
        const val STILL_FRAMES_PER_SECOND = 30

        /** Settings with every decoration group at [decoration], every [MotionGroup.gameplay] one at [gameplay], and the others as given. */
        fun of(
            decoration: FrameRate,
            gameplay: FrameRate,
            scoreGlow: FrameRate,
            cupShake: FrameRate,
            diceRoll: FrameRate,
            rollStyle: DiceRollStyle,
            idleTimeout: IdleTimeout,
        ): MotionSettings = MotionSettings(
            MotionGroup.decoration.associateWith { decoration } + MotionGroup.gameplay.associateWith { gameplay } + mapOf(
                MotionGroup.SCORE_GLOW to scoreGlow,
                MotionGroup.CUP_SHAKE to cupShake,
                MotionGroup.DICE_ROLL to diceRoll,
            ),
            rollStyle,
            idleTimeout,
        )

        /** The settings [ids] gave, or null for anything else. */
        fun fromIds(ids: List<String>): MotionSettings? {
            if (ids.size != MotionGroup.entries.size + 2) return null
            val rates = MotionGroup.entries.zip(ids).associate { (group, id) -> group to (storedIdOf<FrameRate>(id) ?: return null) }
            return MotionSettings(
                rates,
                storedIdOf<DiceRollStyle>(ids[ids.size - 2]) ?: return null,
                storedIdOf<IdleTimeout>(ids.last()) ?: return null,
            )
        }
    }
}
