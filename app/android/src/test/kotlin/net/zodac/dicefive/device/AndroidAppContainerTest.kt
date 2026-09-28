package net.zodac.dicefive.device

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.game.TieBreakStats
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The real Android storage - Room's multiplatform build on the framework's SQLite, and the
 * multiplatform DataStore on its files - opened exactly as the app opens it, under Robolectric (which
 * runs real SQLite). The repositories' own logic is covered in :app:shared with fakes; this is what's
 * left: that the platform wiring actually opens, writes and reads back, and that the DAO's SQL runs.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AndroidAppContainerTest {

    private val container = AndroidAppContainer.get(ApplicationProvider.getApplicationContext())

    @Before
    fun clearScores() = runTest {
        container.scoreRepository.resetLeaderboard()
    }

    @Test
    fun `a recorded score is read back from the database`() = runTest {
        container.scoreRepository.recordScore("Tester", stats(score = 250), won = true, isPrimaryPlayer = true)

        assertEquals(1, container.scoreRepository.totalCount())
        assertEquals(250, container.scoreRepository.page(0).single().score)
        assertEquals(250, container.scoreRepository.primaryPlayerTotalPoints())
        assertEquals(1, container.scoreRepository.playerStatistics().single().gamesWon)
    }

    @Test
    fun `equal scores are ordered by the tie-break - fewer 5x ranks higher`() = runTest {
        container.scoreRepository.recordScore("More", stats(score = 200, fiveOfAKindCount = 2), won = null, isPrimaryPlayer = false)
        container.scoreRepository.recordScore("Fewer", stats(score = 200, fiveOfAKindCount = 0), won = null, isPrimaryPlayer = false)

        assertEquals(listOf("Fewer", "More"), container.scoreRepository.page(0).map { it.playerName })
    }

    @Test
    fun `settings are written to and read back from their preferences file`() = runTest {
        container.settingsRepository.setPlayerName(slot = 1, name = "Tester")

        assertEquals("Tester", container.settingsRepository.playerNameFor(1).first())
    }

    private fun stats(score: Int, fiveOfAKindCount: Int = 0) = TieBreakStats(
        score = score,
        fiveOfAKindCount = fiveOfAKindCount,
        zeroedCategoryCount = 0,
        tricolourScoredCount = null,
        upperSectionTotal = 0,
        chance = 0,
        threeOfAKind = 0,
        fourOfAKind = 0,
    )
}
