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
 * What the Statistics cards can be ordered by. The [id]s are stored, so they never change. [ascendingFirst] is the direction a pick starts in: A to Z and oldest
 * first for [NAME] and [FIRST_PLAYED], the most first for the rest. Ties fall back to the name, A to Z.
 */
enum class StatisticsSort(val id: String, val label: StringResource, val ascendingFirst: Boolean, private val key: (PlayerStatistics) -> Comparable<*>) {
    NAME("name", Res.string.stats_sort_name, true, { it.playerName.lowercase() }),
    FIRST_PLAYED("first_played", Res.string.stats_sort_first_played, true, { it.firstPlayedEpochMillis }),
    HIGHEST_SCORE("highest_score", Res.string.stats_sort_highest_score, false, { it.maxScore }),
    HIGHEST_AVERAGE("highest_average", Res.string.stats_sort_highest_average, false, { it.averageScore }),
    MOST_GAMES("most_games", Res.string.stats_sort_most_games, false, { it.gamesPlayed }),
    ;

    /** [players] in this order, lowest value first if [ascending], else highest first. */
    fun order(players: List<PlayerStatistics>, ascending: Boolean): List<PlayerStatistics> {
        @Suppress("UNCHECKED_CAST")
        val byKey = compareBy<PlayerStatistics> { key(it) as Comparable<Any> }
        return players.sortedWith((if (ascending) byKey else byKey.reversed()).thenBy { it.playerName.lowercase() })
    }
}

/**
 * A [sort] and its direction: ascending is lowest first (A to Z, oldest first), else highest first. Stored, and so
 * carried in the cloud save, as `<id>:asc` or `<id>:desc` - one value, so two devices' picks never mix.
 */
data class StatisticsSortOrder(val sort: StatisticsSort = StatisticsSort.NAME, val ascending: Boolean = sort.ascendingFirst) {

    val stored: String get() = "${sort.id}:${if (ascending) ASCENDING else DESCENDING}"

    /** What picking [picked] gives: this one reversed if it's the current sort, else [picked] in its own first direction. */
    fun pick(picked: StatisticsSort) = if (picked == sort) copy(ascending = !ascending) else StatisticsSortOrder(picked)

    fun order(players: List<PlayerStatistics>) = sort.order(players, ascending)

    companion object {
        private const val ASCENDING = "asc"
        private const val DESCENDING = "desc"

        /** [stored] read back; the default for nothing stored or anything this build doesn't know. */
        fun parse(stored: String?): StatisticsSortOrder {
            val (id, direction) = (stored ?: return StatisticsSortOrder()).split(':', limit = 2).let { it[0] to it.getOrNull(1) }
            val sort = StatisticsSort.entries.firstOrNull { it.id == id } ?: return StatisticsSortOrder()
            return StatisticsSortOrder(sort, ascending = when (direction) {
                ASCENDING -> true
                DESCENDING -> false
                else -> sort.ascendingFirst
            })
        }
    }
}
