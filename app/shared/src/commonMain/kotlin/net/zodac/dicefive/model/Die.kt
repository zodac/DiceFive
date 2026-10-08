package net.zodac.dicefive.model

/**
 * A single six-sided die in the player's roll. [colour] is null in a [GameMode] whose dice have no
 * colour of their own (drawn in the player's chosen dice style instead) - see [GameMode.dieColours].
 *
 * [heldSlot] is which hold slot a held die sits in, in a mode that rolls more dice than it scores
 * ([GameMode.scoresHeldDiceOnly]), where the slots are fewer than the dice: the free one nearest
 * its column when it was held, kept until it's let go (see GameEngine.toggleHold). Null for an unheld die, and for every die in a mode with a slot per die (where
 * a held die simply sits in its own column's slot).
 *
 * [isUnlucky] is set by the Unlucky Dice modifier (see [UnluckyDice]) on a die that landed locked in chains:
 * it can't be held, and is left out of the hand that's scored. Never true of a held die.
 *
 * [isGolden] marks the rare golden egg (see [Achievement.EGGCELLENT_DISCOVERY]): purely how the die is
 * drawn, with no effect on its value or the score. Set when it's rolled, and kept while it's held.
 */
data class Die(
    val value: Int = 1,
    val isHeld: Boolean = false,
    val colour: DieColour? = null,
    val heldSlot: Int? = null,
    val isUnlucky: Boolean = false,
    val isGolden: Boolean = false,
)
