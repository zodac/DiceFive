package net.zodac.dicefive.ui.statistics

import net.zodac.dicefive.data.scores.PlayerStatistics
import net.zodac.dicefive.model.GameMode

/**
 * A page of the Statistics screen, picked from the drop-down at its top: one [Mode]'s games, or [Combined], every mode's
 * together. Each page has its own sort, and a player is on a page only with a game there.
 */
sealed interface StatisticsPage {
    /** What the page is stored as (the `statistics_choice` preference, and its sort's key): the mode's id, or `combined`. */
    val stored: String

    /** The cards on this page, of every player's [players]: their numbers on this page, those with no game there left out. */
    fun cards(players: List<PlayerStatistics>): List<PlayerStatistics>

    data class Mode(val mode: GameMode) : StatisticsPage {
        override val stored: String get() = mode.id

        override fun cards(players: List<PlayerStatistics>) = players.mapNotNull { it.forMode(mode) }
    }

    data object Combined : StatisticsPage {
        override val stored = "combined"

        override fun cards(players: List<PlayerStatistics>) = players
    }

    companion object {
        /** Standard's page: where the screen opens until another is picked. */
        val STANDARD: StatisticsPage = Mode(GameMode.STANDARD)

        /** Every page, in the drop-down's order: each mode's, then Combined. */
        val all: List<StatisticsPage> = GameMode.entries.map(::Mode) + Combined

        /** The page [stored] names; Standard's where nothing was picked yet or the mode is no longer known. */
        fun parse(stored: String?): StatisticsPage = when (stored) {
            Combined.stored -> Combined
            else -> stored?.let(GameMode::fromId)?.let(::Mode) ?: STANDARD
        }
    }
}
