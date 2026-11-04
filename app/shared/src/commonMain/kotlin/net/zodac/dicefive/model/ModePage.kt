package net.zodac.dicefive.model

/**
 * A page of the Leaderboard or Statistics screen, picked from the drop-down at its top: one [Mode]'s games, or [Combined],
 * every mode's together.
 */
sealed interface ModePage {
    /** What the page is stored as (the `leaderboard_choice` and `statistics_choice` preferences): the mode's id, or `combined`. */
    val stored: String

    data class Mode(val mode: GameMode) : ModePage {
        override val stored: String get() = mode.id
    }

    data object Combined : ModePage {
        override val stored = COMBINED_ID
    }

    companion object {
        /** [Combined]'s stored id. */
        const val COMBINED_ID = "combined"

        /** Standard's page: where a screen opens until another is picked. */
        val STANDARD: ModePage = Mode(GameMode.STANDARD)

        /** Every page, in the drop-down's order: each mode's, then Combined. */
        val all: List<ModePage> = GameMode.entries.map(::Mode) + Combined

        /** The page [stored] names; Standard's where nothing was picked yet or the mode is no longer known. */
        fun parse(stored: String?): ModePage = when (stored) {
            COMBINED_ID -> Combined
            else -> stored?.let(GameMode::fromId)?.let(::Mode) ?: STANDARD
        }
    }
}
