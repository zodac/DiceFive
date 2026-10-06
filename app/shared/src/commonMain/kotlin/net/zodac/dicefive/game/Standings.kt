package net.zodac.dicefive.game

import net.zodac.dicefive.model.PlayerState

/** A player's place in the game so far: [place] counts from 1, and [tied] when they share it with another player. */
data class Standing(val place: Int, val tied: Boolean)

/**
 * Every player's place right now, in [players]' own order - or null when a place would say nothing:
 * a solo game, or the start, before anyone has scored a turn. Later on, level totals are a shared
 * place like any other - "=1st" for everyone.
 *
 * Mid-game, places go by total score, and equal totals share their place - the tie-break house rule
 * ([TieBreak]) only means anything on a finished scorecard (fewest 5x, most zeroed boxes...), so it
 * isn't applied to a game still being played. Once every scorecard is complete, places come from
 * [TieBreak.rank], so they agree with the results screen.
 */
fun standings(players: List<PlayerState>): List<Standing>? {
    if (players.size < 2) return null
    val places = if (players.all { it.isScorecardComplete }) {
        IntArray(players.size).also { places -> TieBreak.rank(players).forEach { places[it.originalIndex] = it.rank } }
    } else {
        if (players.all { it.turnsTaken == 0 }) return null
        IntArray(players.size) { index -> 1 + players.count { it.totalScore > players[index].totalScore } }
    }
    return places.map { place -> Standing(place, tied = places.count { it == place } > 1) }
}

/** As the header shows it: "1st", "2nd", "=3rd" - an "=" for a shared place, as the results screen marks one. */
fun Standing.label(): String = (if (tied) "=" else "") + ordinal(place)

/** As a screen reader says it: "1st place", "tied 2nd place" - the "=" would be read as "equals". */
fun Standing.spoken(): String = (if (tied) "tied " else "") + ordinal(place) + " place"

private fun ordinal(place: Int): String {
    val suffix = if (place % 100 in 11..13) {
        "th"
    } else {
        when (place % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
    return "$place$suffix"
}
