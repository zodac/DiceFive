package net.zodac.dicefive.online

import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayGamesIdsTest {

    private val playId = Regex("CgkI[A-Za-z0-9_-]+")

    @Test
    fun `every achievement Play has is mapped once - to a Play id`() {
        assertEquals(Achievement.entries.toSet(), playAchievementIds.keys)
        assertEquals(playAchievementIds.size, playAchievementIds.values.toSet().size)
        assertTrue(playAchievementIds.values.toString(), playAchievementIds.values.all { it.matches(playId) })
    }

    @Test
    fun `every mode has a leaderboard of its own - not an achievement's id`() {
        assertEquals(GameMode.entries.toSet(), playLeaderboardIds.keys)
        assertEquals(playLeaderboardIds.size, playLeaderboardIds.values.toSet().size)
        assertTrue(playLeaderboardIds.values.toString(), playLeaderboardIds.values.all { it.matches(playId) })
        assertTrue(playLeaderboardIds.values.none { it in playAchievementIds.values })
    }
}
