package net.zodac.dicefive.ui.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory

class NearestShownAchievementTest {

    private val ids = Achievement.entries.take(6)

    private fun groups(shown: List<Achievement>) =
        listOf(AchievementGroup(AchievementCategory.MILESTONES, shown.map { AchievementItem(it) }))

    private fun nearest(shown: List<Achievement>, anchor: Achievement) =
        nearestShownAchievementId(groups(ids), groups(shown), anchor.id)

    @Test
    fun `the anchor itself stays when it is still shown`() {
        assertEquals(ids[2].id, nearest(listOf(ids[0], ids[2], ids[5]), ids[2]))
    }

    @Test
    fun `a hidden anchor goes to the nearest shown one before or after it`() {
        assertEquals(ids[1].id, nearest(listOf(ids[0], ids[1], ids[5]), ids[2]))
        assertEquals(ids[4].id, nearest(listOf(ids[0], ids[4]), ids[3]))
    }

    @Test
    fun `a hidden anchor equally near two shown ones goes to the later`() {
        assertEquals(ids[3].id, nearest(listOf(ids[1], ids[3]), ids[2]))
    }

    @Test
    fun `nothing shown gives nothing`() {
        assertNull(nearest(emptyList(), ids[2]))
    }
}
