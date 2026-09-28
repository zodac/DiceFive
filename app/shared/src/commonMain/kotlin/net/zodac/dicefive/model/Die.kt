package net.zodac.dicefive.model

/**
 * A single six-sided die in the player's roll. [colour] is null in a [GameMode] whose dice have no
 * colour of their own (drawn in the player's chosen dice style instead) - see [GameMode.dieColours].
 */
data class Die(
    val value: Int = 1,
    val isHeld: Boolean = false,
    val colour: DieColour? = null,
)
