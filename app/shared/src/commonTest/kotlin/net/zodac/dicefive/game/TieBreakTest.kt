package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.oneScoreEach

private val BASE_STATS = TieBreakStats(
    score = 100,
    fiveOfAKindCount = 2,
    zeroedCategoryCount = 3,
    tricolourScoredCount = null,
    upperSectionTotal = 20,
    chance = 10,
    threeOfAKind = 10,
    fourOfAKind = 10,
)

class TieBreakTest {

    @Test
    fun `a tie is broken by score - then fewest 5x - most zeroes - fewest colour boxes - lowest upper - Chance - 3x - 4x`() {
        // A higher score always wins outright - regardless of every other criterion.
        assertTrue(TieBreak.liveGameComparator.compare(BASE_STATS.copy(score = 100, fiveOfAKindCount = 9), BASE_STATS.copy(score = 90, fiveOfAKindCount = 0)) < 0)

        // Then each criterion in turn: the first stats win, and it's that criterion that decided it.
        for ((winner, loser, criterion) in listOf(
            Triple(BASE_STATS.copy(fiveOfAKindCount = 0), BASE_STATS.copy(fiveOfAKindCount = 1), TieBreakCriterion.FIVE_OF_A_KIND_COUNT),
            Triple(BASE_STATS.copy(zeroedCategoryCount = 4), BASE_STATS.copy(zeroedCategoryCount = 3), TieBreakCriterion.ZEROED_CATEGORIES),
            Triple(BASE_STATS.copy(tricolourScoredCount = 1), BASE_STATS.copy(tricolourScoredCount = 2), TieBreakCriterion.TRICOLOUR_SCORED_COUNT),
            Triple(BASE_STATS.copy(upperSectionTotal = 20), BASE_STATS.copy(upperSectionTotal = 26), TieBreakCriterion.UPPER_SECTION_TOTAL),
            Triple(BASE_STATS.copy(chance = 5), BASE_STATS.copy(chance = 15), TieBreakCriterion.CHANCE),
            Triple(BASE_STATS.copy(threeOfAKind = 9), BASE_STATS.copy(threeOfAKind = 18), TieBreakCriterion.THREE_OF_A_KIND),
            Triple(BASE_STATS.copy(fourOfAKind = 12), BASE_STATS.copy(fourOfAKind = 24), TieBreakCriterion.FOUR_OF_A_KIND),
        )) {
            assertTrue(TieBreak.liveGameComparator.compare(winner, loser) < 0, "$criterion")
            assertEquals(criterion, TieBreak.decidingCriterion(winner, loser))
        }

        // Both sides null on the colour boxes - as in any Standard-mode game - skips straight to the next criterion.
        assertEquals(
            TieBreakCriterion.UPPER_SECTION_TOTAL,
            TieBreak.decidingCriterion(BASE_STATS.copy(tricolourScoredCount = null, upperSectionTotal = 20), BASE_STATS.copy(tricolourScoredCount = null, upperSectionTotal = 25)),
        )

        // Identical stats are a true tie - no deciding criterion.
        assertEquals(0, TieBreak.liveGameComparator.compare(BASE_STATS, BASE_STATS.copy()))
        assertNull(TieBreak.decidingCriterion(BASE_STATS, BASE_STATS.copy()))

        // The leaderboard comparator ignores the colour boxes entirely - even when only one side has them.
        val standardRow = BASE_STATS.copy(tricolourScoredCount = null)
        val tricolourRow = BASE_STATS.copy(tricolourScoredCount = 0)
        assertEquals(0, TieBreak.leaderboardComparator.compare(standardRow, tricolourRow))
        assertNull(TieBreak.decidingCriterion(standardRow, tricolourRow, forLeaderboard = true))
    }

    @Test
    fun `toTieBreakStats reads every field off the real scorecard - the colour boxes only in Tricolour`() {
        val gameMode = GameMode.TRICOLOUR
        val player = PlayerState(
            name = "Player",
            type = PlayerType.HUMAN,
            gameMode = gameMode,
            scorecard = oneScoreEach(
                gameMode.categories.associateWith { 0 } + mapOf(
                    ScoreCategory.ONES to 3,
                    ScoreCategory.CHANCE to 12,
                    ScoreCategory.THREE_OF_A_KIND to 15,
                    ScoreCategory.FOUR_OF_A_KIND to 20,
                    ScoreCategory.REDS to 40,
                ),
            ),
            fiveOfAKindBonusCount = 2,
        )
        val stats = player.toTieBreakStats()
        assertEquals(player.totalScore, stats.score)
        assertEquals(player.fiveOfAKindCount, stats.fiveOfAKindCount)
        // Zero in every category except the five just set - a full Tricolour card has 17 boxes.
        assertEquals(gameMode.categories.size - 5, stats.zeroedCategoryCount)
        assertEquals(1, stats.tricolourScoredCount) // only REDS scored non-zero
        assertEquals(3, stats.upperSectionTotal)
        assertEquals(12, stats.chance)
        assertEquals(15, stats.threeOfAKind)
        assertEquals(20, stats.fourOfAKind)

        val standard = PlayerState(name = "Player", type = PlayerType.HUMAN, gameMode = GameMode.STANDARD, scorecard = GameMode.STANDARD.categories.associateWith { listOf(0) })
        assertNull(standard.toTieBreakStats().tricolourScoredCount)
    }
}
