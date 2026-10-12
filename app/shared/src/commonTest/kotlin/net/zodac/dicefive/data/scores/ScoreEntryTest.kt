package net.zodac.dicefive.data.scores

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.model.GameMode

class ScoreEntryTest {

    private fun entry(mode: GameMode, onLeaderboard: Boolean, playedUnmodified: Boolean?) = ScoreEntry(
        playerName = "A", score = 100, timestampEpochMillis = 0, won = null, isPrimaryPlayer = true,
        fiveOfAKindCount = 0, zeroedCategoryCount = 0, upperSectionTotal = 0, chanceScore = 0,
        threeOfAKindScore = 0, fourOfAKindScore = 0,
        onLeaderboard = onLeaderboard, gameModeId = mode.id, playedUnmodified = playedUnmodified,
    )

    @Test
    fun aRecordedAnswerIsUsedWhateverTheLeaderboardFlagSays() {
        assertTrue(entry(GameMode.QUICKFIRE, onLeaderboard = false, playedUnmodified = true).ranUnderOwnRules)
        assertFalse(entry(GameMode.STANDARD, onLeaderboard = false, playedUnmodified = false).ranUnderOwnRules)
    }

    @Test
    fun anOlderStandardOrTricolourRowReadsAsItsLeaderboardFlag() {
        assertTrue(entry(GameMode.STANDARD, onLeaderboard = true, playedUnmodified = null).ranUnderOwnRules)
        assertFalse(entry(GameMode.TRICOLOUR, onLeaderboard = false, playedUnmodified = null).ranUnderOwnRules)
    }

    @Test
    fun anOlderRowOfAModeAlwaysOffTheBoardIsUnknownSoNotCounted() {
        GameMode.entries.filter { !it.countsOnLeaderboard }.forEach {
            assertFalse(entry(it, onLeaderboard = false, playedUnmodified = null).ranUnderOwnRules, it.name)
        }
    }
}
