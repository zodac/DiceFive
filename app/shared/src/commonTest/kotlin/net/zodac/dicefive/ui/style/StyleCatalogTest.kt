package net.zodac.dicefive.ui.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement

/** Ids of styles that were a family of their own when released, and are now colours of another: see [StyleCatalogTest]. */
private val IDS_KEPT_FROM_MERGED_FAMILIES = setOf("beaker_blue", "beaker_green")

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
            val colourKey = colour.name.key.removePrefix("style_colour_")
            // A style folded into another family since it was released keeps the id it had: ids are permanent.
            val expected = if (colour.style.id in IDS_KEPT_FROM_MERGED_FAMILIES) colourKey else family.name.key.removePrefix("style_family_") + "_" + colourKey
            assertEquals(expected, colour.style.id)
        }
    }

    @Test
    fun `the beakers are variants of Glass - open with it - under the ids they were released with`() {
        val glass = DiceCupStyles.families.single { it.name.key == "style_family_glass" }
        assertEquals(listOf("glass_clear", "glass_blue", "glass_amber", "beaker_blue", "beaker_green"), glass.colours.map { it.style.id })
        assertEquals(StyleUnlock.AchievementCount(30), glass.unlock)
        for (id in IDS_KEPT_FROM_MERGED_FAMILIES) assertEquals(glass, DiceCupStyles.familyOf(id), id)
        assertTrue(DiceCupStyles.families.none { it.name.key == "style_family_beaker" })
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
    fun `a style that goes with another of the same name is unlocked by the same thing - in dice - cups - mats and backgrounds`() {
        val unlocks = listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds)
            .flatMap { catalog -> catalog.families.map { it.name.key to it.unlock } }
            .groupBy({ it.first }, { it.second })
        for ((family, locks) in unlocks) assertEquals(1, locks.distinct().size, "$family is unlocked by ${locks.distinct()}")
        // The ones that go together by name, so a new background can't quietly miss its dice or mat.
        for (family in listOf("stone", "circuit", "neon", "meadow", "leather", "gingham", "christmas", "glitter", "rgb")) {
            assertTrue(TableBackgrounds.families.any { it.name.key == "style_family_$family" }, "no $family background")
        }
    }

    @Test
    fun `no count lock is beyond 100 achievements - and the water set opens at 15`() {
        for (catalog in catalogs) for (family in catalog.families) {
            val unlock = family.unlock
            if (unlock is StyleUnlock.AchievementCount) assertTrue(unlock.count <= 100, "${family.name.key} needs ${unlock.count}")
        }
        val water = listOf(
            DiceStyles to "style_family_ocean",
            DiceCupStyles to "style_family_fishbowl",
            DiceMats to "style_family_ocean",
            TableBackgrounds to "style_family_ocean",
            ScoreFrames to "style_family_nautical",
        )
        for ((catalog, family) in water) {
            assertEquals(StyleUnlock.AchievementCount(15), catalog.families.single { it.name.key == family }.unlock, family)
        }
    }

    @Test
    fun `the Nautical frame offers Chain first - and the Ocean background rolling Waves then still Lagoon`() {
        assertEquals(listOf("nautical_chain", "nautical_rope"), ScoreFrames.families.single { it.name.key == "style_family_nautical" }.colours.map { it.style.id })
        val ocean = TableBackgrounds.families.single { it.name.key == "style_family_ocean" }.colours.map { it.style }
        assertEquals(listOf("ocean_waves", "ocean_lagoon"), ocean.map { it.id })
        assertTrue(ocean[0] is OceanWavesBackground)
        assertTrue(ocean[1] is OceanLagoonBackground)
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

    @Test
    fun `the unlocked count counts every colour - leaving out a secret one until it's earned`() {
        val none = AchievementsState()
        val all = AchievementsState(Achievement.entries.associateWith { 0L })
        for (catalog in catalogs) {
            val secret = catalog.families.sumOf { family -> family.colours.count { it.secretAchievement != null } }
            val classic = catalog.families.single { it.unlock == StyleUnlock.Free }
            assertEquals(UnlockCount(classic.availableColours(none).size, catalog.all.size - secret), catalog.unlockCount(none), catalog.noun.key)
            assertEquals(UnlockCount(catalog.all.size, catalog.all.size), catalog.unlockCount(all), catalog.noun.key)
            assertTrue(catalog.unlockCount(all).complete, catalog.noun.key)
            assertTrue(!catalog.unlockCount(none).complete, catalog.noun.key)
        }
        // Nat 20 unlocks the D20 style and its white and black dice: two more counted, both unlocked.
        val nat20 = AchievementsState(mapOf(Achievement.NAT_20 to 0L))
        assertEquals(DiceStyles.unlockCount(none) + UnlockCount(2, 2), DiceStyles.unlockCount(nat20))
    }
}
