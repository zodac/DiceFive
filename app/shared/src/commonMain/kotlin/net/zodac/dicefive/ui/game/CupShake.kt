package net.zodac.dicefive.ui.game

/** How long the cup "shakes" with reduced motion on and nothing to hear or feel: just long enough for the
 * roll's state (`isRolling`, the roll tracker) to be seen changing, since the game's own logic keys on it. */
internal const val REDUCED_MOTION_CUP_SHAKE_MILLIS = 100L

/**
 * How long a roll waits between the cup being tapped (or a CPU's turn reaching its roll) and the roll
 * landing - the one delay [CUP_SHAKE_MILLIS] sets, for a human's tap and a CPU's turn alike.
 *
 * It is a shake *animation* window, but the shake sound (about 400ms) and the shake buzz (336ms) are
 * timed to it: both start as the window opens and finish just as the dice land, where the landing sound
 * (about 490ms) begins. Cutting the window short while either can play would have the buzz still
 * running as the dice are revealed and the shake's rattle overlapping the landing clunk - and cost a
 * screen-reader user, for whom sound and vibration ARE the roll's feedback, the very cue they use.
 * Reduced motion is about what moves, not what's heard or felt, so:
 *
 * - **With sound or vibration on, the window is unchanged** ([CUP_SHAKE_MILLIS]); only the drawn shake
 *   is dropped (see `LocalReduceMotion`).
 * - **With both off there is nothing to keep in step with**, and the window shrinks to
 *   [REDUCED_MOTION_CUP_SHAKE_MILLIS].
 *
 * Every other delay is left alone: the CPU's pauses between rolls and before it scores exist so a player
 * can follow what it did, not to wait for an animation.
 */
internal fun cupShakeMillis(reduceMotion: Boolean, soundEnabled: Boolean, vibrationEnabled: Boolean): Long =
    if (reduceMotion && !soundEnabled && !vibrationEnabled) REDUCED_MOTION_CUP_SHAKE_MILLIS else CUP_SHAKE_MILLIS
