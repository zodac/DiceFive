package net.zodac.dicefive.ui.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement

class StyleCatalogTest {

    private val catalogs = listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds, ScoreFrames)

    @Test
    fun `every id resolves to its own art`() {
        for (catalog in catalogs) {
            for (art in catalog.all) assertEquals(art, catalog.byId(art.id))
            for (family in catalog.families) assertTrue(family.colours.isNotEmpty(), "${family.name.key} has no colours")
        }
    }

    @Test
    fun `only Classic is free`() {
        for (catalog in catalogs) for (family in catalog.families) {
            val isClassic = family.name.key.contains("classic")
            assertEquals(isClassic, family.unlock == StyleUnlock.Free, "${family.name.key}'s unlock is ${family.unlock}")
        }
    }


    @Test
    fun `a secret style is shown locked until its secret achievement unlocks it`() {
        assertTrue(Achievement.BIG_FAN.unlocksStyle)
        val everythingButBigFan = AchievementsState(Achievement.entries.filter { it != Achievement.BIG_FAN }.associateWith { 0L })
        val bigFanEarned = AchievementsState(mapOf(Achievement.BIG_FAN to 0L))
        assertEquals(TableBackgrounds.default, TableBackgrounds.unlockedById("glitch_screen", everythingButBigFan))
        assertEquals("glitch_screen", TableBackgrounds.unlockedById("glitch_screen", bigFanEarned).id)
    }
}
