package net.zodac.dicefive.model

/**
 * A single six-sided die in the player's roll.
 */
data class Die(
    val value: Int = 1,
    val isHeld: Boolean = false,
)
