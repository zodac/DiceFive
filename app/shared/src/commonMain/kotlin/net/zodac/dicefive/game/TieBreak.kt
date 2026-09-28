package net.zodac.dicefive.game

import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection

/**
 * A house rule, not in the official rules (see `.claude/DESIGN.md`'s "Tie-break house rules"):
 * whoever matched the top score with more handicaps - less luck, more empty boxes - ranks above
 * the other(s). Declaration order is priority order; the first criterion that differs between two
 * equal scores decides it. [reasonText] is what a player row says was the deciding factor, e.g.
 * "Won with fewer 5x".
 */
enum class TieBreakCriterion(val reasonText: String) {
    FIVE_OF_A_KIND_COUNT("fewer 5x"),
    ZEROED_CATEGORIES("more zeroed categories"),
    TRICOLOUR_SCORED_COUNT("fewer tricolour scores"),
    UPPER_SECTION_TOTAL("a lower upper section"),
    CHANCE("a lower Chance score"),
    THREE_OF_A_KIND("a lower 3x"),
    FOUR_OF_A_KIND("a lower 4x"),
}

/**
 * One player's (or leaderboard row's) tie-break inputs. Only [tricolourScoredCount] can be null:
 * a Standard-mode game has no colour boxes to count (and the leaderboard never compares it - see
 * [LEADERBOARD_CRITERIA]).
 */
data class TieBreakStats(
    val score: Int,
    val fiveOfAKindCount: Int,
    val zeroedCategoryCount: Int,
    val tricolourScoredCount: Int?,
    val upperSectionTotal: Int,
    val chance: Int,
    val threeOfAKind: Int,
    val fourOfAKind: Int,
)

/**
 * [TieBreakStats] for a finished player's own scorecard. Only meaningful once every box is filled,
 * which is the only time anything ranks players (the results screen, the end-of-game achievements).
 */
fun PlayerState.toTieBreakStats(): TieBreakStats = TieBreakStats(
    score = totalScore,
    fiveOfAKindCount = fiveOfAKindCount,
    zeroedCategoryCount = scorecard.values.count { it == 0 },
    tricolourScoredCount = if (gameMode == GameMode.TRICOLOUR) {
        gameMode.categories.count { it.section == ScoreSection.COLOUR && (scorecard[it] ?: 0) > 0 }
    } else {
        null
    },
    upperSectionTotal = upperSectionTotal,
    chance = scorecard[ScoreCategory.CHANCE] ?: 0,
    threeOfAKind = scorecard[ScoreCategory.THREE_OF_A_KIND] ?: 0,
    fourOfAKind = scorecard[ScoreCategory.FOUR_OF_A_KIND] ?: 0,
)

private data class Criterion(
    val id: TieBreakCriterion,
    val lowerIsBetter: Boolean,
    val value: (TieBreakStats) -> Int?,
)

private val LIVE_GAME_CRITERIA = listOf(
    Criterion(TieBreakCriterion.FIVE_OF_A_KIND_COUNT, lowerIsBetter = true) { it.fiveOfAKindCount },
    Criterion(TieBreakCriterion.ZEROED_CATEGORIES, lowerIsBetter = false) { it.zeroedCategoryCount },
    Criterion(TieBreakCriterion.TRICOLOUR_SCORED_COUNT, lowerIsBetter = true) { it.tricolourScoredCount },
    Criterion(TieBreakCriterion.UPPER_SECTION_TOTAL, lowerIsBetter = true) { it.upperSectionTotal },
    Criterion(TieBreakCriterion.CHANCE, lowerIsBetter = true) { it.chance },
    Criterion(TieBreakCriterion.THREE_OF_A_KIND, lowerIsBetter = true) { it.threeOfAKind },
    Criterion(TieBreakCriterion.FOUR_OF_A_KIND, lowerIsBetter = true) { it.fourOfAKind },
)

/**
 * Excludes [TieBreakCriterion.TRICOLOUR_SCORED_COUNT] - the global leaderboard mixes Standard and
 * Tricolour games on one list (see `DESIGN.md`'s "Game modes"), and a Standard row has no colour
 * boxes to compare a Tricolour one against; treating a Standard row's null there as "worst" would
 * always sink it under any Tricolour row on the same score, which is the wrong reason to lose a
 * tie-break.
 */
private val LEADERBOARD_CRITERIA = LIVE_GAME_CRITERIA.filterNot { it.id == TieBreakCriterion.TRICOLOUR_SCORED_COUNT }

/**
 * One player's place in a finished game's results: [rank] already accounts for the tie-break house
 * rule (see [TieBreak.rank]), and [tieBreakReason] - only ever set when this player's raw score
 * matches the very next player down the list, yet the house rule still told them apart - is what
 * credits the deciding criterion, e.g. for `GameOverScreen`'s "Won on fewer 5x" captions and for
 * [Achievement.TIE_BREAK][net.zodac.dicefive.model.Achievement]. [originalIndex] is this player's
 * position in the list [TieBreak.rank] was given, not their sorted position - callers that care
 * which seat this was (player 1, say) look it up by this rather than by equality, since two
 * players can legitimately have identical [PlayerState] values (a true tie down to the last box).
 */
data class RankedPlayer(val player: PlayerState, val originalIndex: Int, val rank: Int, val tieBreakReason: TieBreakCriterion?)

object TieBreak {

    /**
     * A just-finished game's own players, best first: equal scores broken by the house rule, the
     * tricolour criterion skipped in a Standard-mode game (every player's is null there). A true tie -
     * every criterion also matches - compares equal, and stays a shared rank; see `GameOverScreen`.
     */
    val liveGameComparator: Comparator<TieBreakStats> = comparator(LIVE_GAME_CRITERIA)

    /**
     * Leaderboard rows, best first - the same criteria minus the tricolour one. Mirrors
     * `ScoreDao.pagedScores`'s own `ORDER BY`; keep the two in step.
     */
    val leaderboardComparator: Comparator<TieBreakStats> = comparator(LEADERBOARD_CRITERIA)

    /**
     * The first criterion that separates [a] and [b], or null if they're a true tie (every
     * available criterion also matches). Only meaningful when `a.score == b.score`; use
     * [forLeaderboard] to match whichever comparator produced their order.
     */
    fun decidingCriterion(a: TieBreakStats, b: TieBreakStats, forLeaderboard: Boolean = false): TieBreakCriterion? {
        val criteria = if (forLeaderboard) LEADERBOARD_CRITERIA else LIVE_GAME_CRITERIA
        for (criterion in criteria) {
            if (compare(criterion, a, b) != 0) return criterion.id
        }
        return null
    }

    /**
     * Ranks a just-finished game's [players] by [liveGameComparator], best first - a player only
     * shares a rank with another when every criterion also matches (a true tie). Shared by
     * `GameOverScreen` (which renders the sorted list as-is) and [Achievement.TIE_BREAK]'s own check
     * (which looks up `originalIndex == 0` for player 1, rather than sorted position).
     */
    fun rank(players: List<PlayerState>): List<RankedPlayer> {
        val indexed = players.mapIndexed { index, player -> Triple(index, player, player.toTieBreakStats()) }
        val sorted = indexed.sortedWith(compareBy(liveGameComparator) { it.third })

        val ranks = IntArray(sorted.size)
        var rank = 1
        for (index in sorted.indices) {
            if (index > 0 && liveGameComparator.compare(sorted[index - 1].third, sorted[index].third) != 0) {
                rank = index + 1
            }
            ranks[index] = rank
        }

        return sorted.mapIndexed { index, (originalIndex, player, stats) ->
            val next = sorted.getOrNull(index + 1)
            val reason = if (next != null && ranks[index] != ranks[index + 1] && stats.score == next.third.score) {
                decidingCriterion(stats, next.third)
            } else {
                null
            }
            RankedPlayer(player, originalIndex, ranks[index], reason)
        }
    }

    /** Zero - skipped - where either side has no value, which only the tricolour count ever lacks. */
    private fun compare(criterion: Criterion, a: TieBreakStats, b: TieBreakStats): Int {
        val av = criterion.value(a) ?: return 0
        val bv = criterion.value(b) ?: return 0
        return if (criterion.lowerIsBetter) av.compareTo(bv) else bv.compareTo(av)
    }

    private fun comparator(criteria: List<Criterion>): Comparator<TieBreakStats> =
        Comparator { a, b ->
            val scoreCompare = b.score.compareTo(a.score)
            if (scoreCompare != 0) {
                scoreCompare
            } else {
                criteria.firstNotNullOfOrNull { criterion ->
                    compare(criterion, a, b).takeIf { it != 0 }
                } ?: 0
            }
        }
}
