package net.zodac.dicefive.ui.statistics

import net.zodac.dicefive.data.scores.PlayerStatistics
import net.zodac.dicefive.model.ModePage

/** The cards on this page, of every player's [players]: their numbers on this page, those with no game there left out. */
fun ModePage.cards(players: List<PlayerStatistics>): List<PlayerStatistics> = when (this) {
    is ModePage.Mode -> players.mapNotNull { it.forMode(mode) }
    ModePage.Combined -> players
}

/** Every page's sort, from the sorts [stored] by page id; a page with none stored has the default. */
internal fun parseStatisticsSorts(stored: Map<String, String>): Map<ModePage, StatisticsSortOrder> =
    ModePage.all.associateWith { StatisticsSortOrder.parse(stored[it.stored]) }
