package net.zodac.dicefive.data.scores

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import net.zodac.dicefive.model.GameMode

class ScoreEntryTest {

    private fun entry(gameModeId: String, playedUnmodified: Boolean) = ScoreEntry(
        playerName = "Ann",
        score = 200,
        timestampEpochMillis = 0L,
        won = null,
        isPrimaryPlayer = true,
        fiveOfAKindCount = 0,
        zeroedCategoryCount = 0,
        upperSectionTotal = 0,
        chanceScore = 0,
        threeOfAKindScore = 0,
        fourOfAKindScore = 0,
        gameModeId = gameModeId,
        playedUnmodified = playedUnmodified,
    )

    @Test
    fun `a row is on the Leaderboard only for a mode that counts there - played with no modifier on`() {
        for (mode in GameMode.entries) {
            assertEquals(mode.countsOnLeaderboard, entry(mode.id, playedUnmodified = true).onLeaderboard, mode.id)
            assertFalse(entry(mode.id, playedUnmodified = false).onLeaderboard, mode.id)
        }
        // A mode this build doesn't know is off the board.
        assertFalse(entry("retired_mode", playedUnmodified = true).onLeaderboard)
    }

    @Test
    fun `a copy works out whether it is on the Leaderboard again`() {
        val standard = entry(GameMode.STANDARD.id, playedUnmodified = true)
        assertTrue(standard.onLeaderboard)
        assertFalse(standard.copy(playedUnmodified = false).onLeaderboard)
        assertFalse(standard.copy(gameModeId = GameMode.QUICKFIRE.id).onLeaderboard)
    }

    @Test
    fun `a uuid is stored as its 16 bytes and read back the same`() {
        val uuid = Uuid.parseHex("0123456789abcdef0123456789abcdef")
        val bytes = UuidConverters().toBytes(uuid)
        assertEquals(16, bytes.size)
        assertEquals(uuid, UuidConverters().fromBytes(bytes))
    }
}
