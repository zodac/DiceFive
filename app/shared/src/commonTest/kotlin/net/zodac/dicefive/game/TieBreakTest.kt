package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory

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
    fun `higher score always wins outright - regardless of every other criterion`() {
        val lowerScoreButFewerHandicaps = BASE_STATS.copy(score = 90, fiveOfAKindCount = 0)
        val higherScoreButMoreHandicaps = BASE_STATS.copy(score = 100, fiveOfAKindCount = 9)

        assertTrue(TieBreak.liveGameComparator.compare(higherScoreButMoreHandicaps, lowerScoreButFewerHandicaps) < 0)
    }

    @Test
    fun `fewest 5x wins first - once scores are equal`() {
        val fewer = BASE_STATS.copy(fiveOfAKindCount = 0)
        val more = BASE_STATS.copy(fiveOfAKindCount = 1)

        assertTrue(TieBreak.liveGameComparator.compare(fewer, more) < 0)
        assertEquals(TieBreakCriterion.FIVE_OF_A_KIND_COUNT, TieBreak.decidingCriterion(fewer, more))
    }

    @Test
    fun `more zeroed categories wins once 5x count is equal`() {
        val moreZeroed = BASE_STATS.copy(zeroedCategoryCount = 4)
        val fewerZeroed = BASE_STATS.copy(zeroedCategoryCount = 3)

        assertTrue(TieBreak.liveGameComparator.compare(moreZeroed, fewerZeroed) < 0)
        assertEquals(TieBreakCriterion.ZEROED_CATEGORIES, TieBreak.decidingCriterion(moreZeroed, fewerZeroed))
    }

    @Test
    fun `fewer tricolour scores wins once 5x and zeroed categories match`() {
        val fewer = BASE_STATS.copy(tricolourScoredCount = 1)
        val more = BASE_STATS.copy(tricolourScoredCount = 2)

        assertTrue(TieBreak.liveGameComparator.compare(fewer, more) < 0)
        assertEquals(TieBreakCriterion.TRICOLOUR_SCORED_COUNT, TieBreak.decidingCriterion(fewer, more))
    }

    @Test
    fun `both sides null on tricolour scored count - as in any Standard-mode game - skips straight to the next criterion`() {
        val higherUpper = BASE_STATS.copy(tricolourScoredCount = null, upperSectionTotal = 25)
        val lowerUpper = BASE_STATS.copy(tricolourScoredCount = null, upperSectionTotal = 20)

        assertEquals(TieBreakCriterion.UPPER_SECTION_TOTAL, TieBreak.decidingCriterion(lowerUpper, higherUpper))
    }

    @Test
    fun `lower upper section wins once 5x - zeroed and tricolour all match`() {
        val lowerUpper = BASE_STATS.copy(upperSectionTotal = 20)
        val higherUpper = BASE_STATS.copy(upperSectionTotal = 26)

        assertTrue(TieBreak.liveGameComparator.compare(lowerUpper, higherUpper) < 0)
        assertEquals(TieBreakCriterion.UPPER_SECTION_TOTAL, TieBreak.decidingCriterion(lowerUpper, higherUpper))
    }

    @Test
    fun `lower chance - then lower 3x - then lower 4x break the remaining tie in priority order`() {
        val lowerChance = BASE_STATS.copy(chance = 5)
        val higherChance = BASE_STATS.copy(chance = 15)
        assertEquals(TieBreakCriterion.CHANCE, TieBreak.decidingCriterion(lowerChance, higherChance))

        val lowerThreeOfAKind = BASE_STATS.copy(threeOfAKind = 9)
        val higherThreeOfAKind = BASE_STATS.copy(threeOfAKind = 18)
        assertEquals(TieBreakCriterion.THREE_OF_A_KIND, TieBreak.decidingCriterion(lowerThreeOfAKind, higherThreeOfAKind))

        val lowerFourOfAKind = BASE_STATS.copy(fourOfAKind = 12)
        val higherFourOfAKind = BASE_STATS.copy(fourOfAKind = 24)
        assertEquals(TieBreakCriterion.FOUR_OF_A_KIND, TieBreak.decidingCriterion(lowerFourOfAKind, higherFourOfAKind))
    }

    @Test
    fun `identical stats are a true tie - no deciding criterion`() {
        assertEquals(0, TieBreak.liveGameComparator.compare(BASE_STATS, BASE_STATS.copy()))
        assertNull(TieBreak.decidingCriterion(BASE_STATS, BASE_STATS.copy()))
    }

    @Test
    fun `leaderboard comparator ignores tricolour scored count entirely - even when only one side has it`() {
        val standardRow = BASE_STATS.copy(tricolourScoredCount = null)
        val tricolourRow = BASE_STATS.copy(tricolourScoredCount = 0)

        assertEquals(0, TieBreak.leaderboardComparator.compare(standardRow, tricolourRow))
        assertNull(TieBreak.decidingCriterion(standardRow, tricolourRow, forLeaderboard = true))
    }

    @Test
    fun `PlayerState toTieBreakStats reads every field off the real scorecard`() {
        val gameMode = GameMode.TRICOLOUR
        val player = PlayerState(
            name = "Player",
            type = PlayerType.HUMAN,
            gameMode = gameMode,
            scorecard = gameMode.categories.associateWith { 0 } + mapOf(
                ScoreCategory.ONES to 3,
                ScoreCategory.CHANCE to 12,
                ScoreCategory.THREE_OF_A_KIND to 15,
                ScoreCategory.FOUR_OF_A_KIND to 20,
                ScoreCategory.REDS to 40,
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
    }

    @Test
    fun `PlayerState toTieBreakStats leaves tricolour scored count null in Standard mode`() {
        val player = PlayerState(
            name = "Player",
            type = PlayerType.HUMAN,
            gameMode = GameMode.STANDARD,
            scorecard = GameMode.STANDARD.categories.associateWith { 0 },
        )

        assertNull(player.toTieBreakStats().tricolourScoredCount)
    }
}
