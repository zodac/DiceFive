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
    fun `every id is its family's name then its colour's - as the Styles screen shows them`() {
        for (catalog in catalogs) for (family in catalog.families) for (colour in family.colours) {
            val expected = family.name.key.removePrefix("style_family_") + "_" + colour.name.key.removePrefix("style_colour_")
            assertEquals(expected, colour.style.id)
        }
    }

    @Test
    fun `Nat 20 opens D20 white and black - Critical Success opens blue - and a locked pick draws the default elsewhere`() {
        val d20 = DiceStyles.all.filterIsInstance<D20DiceStyle>().associateBy { it.id }
        assertEquals(listOf("d20_white", "d20_black", "d20_blue"), d20.keys.toList())
        val none = AchievementsState()
        val nat20 = AchievementsState(mapOf(Achievement.NAT_20 to 0L))
        val critical = AchievementsState(mapOf(Achievement.CRITICAL_SUCCESS to 0L))
        val both = AchievementsState(mapOf(Achievement.NAT_20 to 0L, Achievement.CRITICAL_SUCCESS to 0L))

        for ((id, style) in d20) {
            assertTrue(!DiceStyles.isUnlocked(id, none), "$id is open before either achievement")
            assertEquals(DiceStyles.default, DiceStyles.unlockedById(id, none))
            assertTrue(DiceStyles.isUnlocked(id, both), "$id is locked with both earned")
            assertEquals(style, DiceStyles.unlockedById(id, both))
        }
        for (id in listOf("d20_white", "d20_black")) {
            assertTrue(DiceStyles.isUnlocked(id, nat20), "$id needs Nat 20")
            assertTrue(!DiceStyles.isUnlocked(id, critical), "$id is open with only Critical Success")
        }
        assertTrue(DiceStyles.isUnlocked("d20_blue", critical), "blue needs Critical Success")
        assertTrue(!DiceStyles.isUnlocked("d20_blue", nat20), "blue is open with only Nat 20")
        assertEquals(DiceStyles.default, DiceStyles.unlockedById("d20_blue", nat20))
        // Each achievement carries the star that says it unlocks a style.
        assertTrue(Achievement.NAT_20.unlocksStyle)
        assertTrue(Achievement.CRITICAL_SUCCESS.unlocksStyle)
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
