package net.zodac.dicefive.model

/**
 * The two roll modifiers, chosen on the setup screen and carried on [GameState] for the life of the
 * game. Both are off by default, which leaves the mode's own rolls untouched.
 *
 * [rollsPerTurn] is the Number of Rolls modifier: when set, every turn gets this many rolls instead of
 * [GameMode.rollsPerTurn]. [storedRolls] is Stored Rolls: the rolls a player doesn't use before scoring are
 * kept for their own next turn, on top of that turn's normal allowance, and so on. [storedRollsMax]
 * caps how many can be kept at the end of a turn; null means no cap.
 */
data class RollModifiers(
    val rollsPerTurn: Int? = null,
    val storedRolls: Boolean = false,
    val storedRollsMax: Int? = null,
) {
    init {
        require(rollsPerTurn == null || rollsPerTurn in MIN_ROLLS..MAX_ROLLS) { "Rolls per turn must be $MIN_ROLLS-$MAX_ROLLS" }
        require(storedRollsMax == null || storedRollsMax >= 0) { "The stored rolls cap can't be negative" }
    }

    /** Whether either modifier is on. */
    val isActive: Boolean
        get() = rollsPerTurn != null || storedRolls

    /** How many of a turn's [unused] rolls the player keeps for next turn. */
    fun stored(unused: Int): Int = if (storedRolls) minOf(unused, storedRollsMax ?: unused) else 0

    companion object {
        const val MIN_ROLLS = 1
        const val MAX_ROLLS = 9
        const val DEFAULT_ROLLS = 3

        /** The most digits the stored rolls cap's text field takes. */
        const val MAX_CAP_DIGITS = 3
    }
}
