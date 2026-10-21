package net.zodac.dicefive.ui.statistics

import net.zodac.dicefive.data.scores.PlayerStatistics
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.stats_sort_first_played
import net.zodac.dicefive.resources.stats_sort_highest_average
import net.zodac.dicefive.resources.stats_sort_highest_score
import net.zodac.dicefive.resources.stats_sort_most_games
import net.zodac.dicefive.resources.stats_sort_name
import org.jetbrains.compose.resources.StringResource

/**
 * What the Statistics cards can be ordered by. [ascendingFirst] is the direction a pick starts in: A to Z and oldest
 * first for [NAME] and [FIRST_PLAYED], the most first for the rest. Ties fall back to the name, A to Z.
 */
enum class StatisticsSort(val label: StringResource, val ascendingFirst: Boolean, private val key: (PlayerStatistics) -> Comparable<*>) {
    NAME(Res.string.stats_sort_name, true, { it.playerName.lowercase() }),
    FIRST_PLAYED(Res.string.stats_sort_first_played, true, { it.firstPlayedEpochMillis }),
    HIGHEST_SCORE(Res.string.stats_sort_highest_score, false, { it.maxScore }),
    HIGHEST_AVERAGE(Res.string.stats_sort_highest_average, false, { it.averageScore }),
    MOST_GAMES(Res.string.stats_sort_most_games, false, { it.gamesPlayed }),
    ;

    /** [players] in this order, lowest value first if [ascending], else highest first. */
    fun order(players: List<PlayerStatistics>, ascending: Boolean): List<PlayerStatistics> {
        @Suppress("UNCHECKED_CAST")
        val byKey = compareBy<PlayerStatistics> { key(it) as Comparable<Any> }
        return players.sortedWith((if (ascending) byKey else byKey.reversed()).thenBy { it.playerName.lowercase() })
    }
}
