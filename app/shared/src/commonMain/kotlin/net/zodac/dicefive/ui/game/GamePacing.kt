package net.zodac.dicefive.ui.game

/**
 * What a CPU's turn waits on so the player can follow it, which depends on the animations the screen is playing.
 */
data class GamePacing(
    /**
     * How long a CPU's roll waits before landing - a human's tap waits the same, in `GameScreen`. From
     * `cupShakeMillis`, which shortens it only when reduced motion is on and there is no sound or vibration
     * to keep in step with; the full [CUP_SHAKE_MILLIS] otherwise.
     */
    val cupShakeMillis: Long = CUP_SHAKE_MILLIS,
    /**
     * Whether the screen animates the dice - tossing them onto the mat, dropping a released one back onto it.
     * False under reduced motion (the player's "Animations" at Off, or the system's) or the Low level, where they
     * just snap to where they end up, so a CPU has nothing to wait for.
     */
    val diceAnimated: Boolean = true,
    /**
     * How long a CPU waits after a roll lands for the dice to finish tossing onto the mat before it holds,
     * rerolls or scores - `DICE_TOSS_MILLIS`, or 0 when the dice aren't animated ([diceAnimated]).
     */
    val diceTossMillis: Long = if (diceAnimated) DICE_TOSS_MILLIS.toLong() else 0L,
)
