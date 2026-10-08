package net.zodac.dicefive.model

/**
 * An optional limit on how long a player has to complete their whole turn (not each individual
 * roll) - chosen on the setup screen and carried on [GameState] for the life of the game. Either
 * [NONE], one of the [PRESETS], or any whole number of seconds up to [MAX_SECONDS] typed in.
 */
data class TurnTimer(val seconds: Int?) {
    init {
        require(seconds == null || seconds in MIN_SECONDS..MAX_SECONDS) { "A turn timer is $MIN_SECONDS to $MAX_SECONDS seconds, not $seconds" }
    }

    /** The form this is stored in: "NONE", or "SECONDS_" and the length - what the fixed lengths were once saved as, so older saves still read. */
    val name: String
        get() = if (seconds == null) "NONE" else "SECONDS_$seconds"

    /** Whether this is one of the [PRESETS], rather than a length typed in. */
    val isPreset: Boolean
        get() = this in PRESETS

    companion object {
        const val MIN_SECONDS = 5
        const val MAX_SECONDS = 999

        /** The longest a typed length can be, in digits. */
        const val MAX_DIGITS = 3

        val NONE = TurnTimer(null)
        val SECONDS_30 = TurnTimer(30)
        val SECONDS_60 = TurnTimer(60)
        val SECONDS_120 = TurnTimer(120)

        /** The lengths offered as buttons, shortest first. */
        val PRESETS = listOf(SECONDS_30, SECONDS_60, SECONDS_120)

        /** The timer saved as [raw] (see [name]), or null if it isn't one - a value from a newer or damaged save. */
        fun parse(raw: String): TurnTimer? = when {
            raw == "NONE" -> NONE
            raw.startsWith("SECONDS_") -> raw.removePrefix("SECONDS_").toIntOrNull()?.takeIf { it in MIN_SECONDS..MAX_SECONDS }?.let(::TurnTimer)
            else -> null
        }
    }
}
