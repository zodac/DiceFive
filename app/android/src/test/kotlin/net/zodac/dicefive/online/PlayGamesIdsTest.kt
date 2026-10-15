package net.zodac.dicefive.online

import net.zodac.dicefive.model.Achievement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayGamesIdsTest {

    @Test
    fun `every achievement Play has is mapped once - to a Play id`() {
        assertEquals(Achievement.entries.filter { it.onPlay }.toSet(), playAchievementIds.keys)
        assertEquals(playAchievementIds.size, playAchievementIds.values.toSet().size)
        assertTrue(playAchievementIds.values.toString(), playAchievementIds.values.all { it.matches(Regex("CgkI[A-Za-z0-9_-]+")) })
    }
}
