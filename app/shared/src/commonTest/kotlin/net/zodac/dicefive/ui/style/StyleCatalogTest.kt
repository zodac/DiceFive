package net.zodac.dicefive.ui.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.game.style.StyleAccess
import net.zodac.dicefive.game.style.StyleCategory
import net.zodac.dicefive.model.Achievement

class StyleCatalogTest {

    private val catalogs = listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds, ScoreFrames)

    @Test
    fun `every category's art matches its registry one for one - and every id resolves to its own art`() {
        assertEquals(catalogs, StyleCategory.entries.map { it.catalog })
        for (catalog in catalogs) {
            assertEquals(catalog.registry.ids, catalog.all.map { it.id })
            assertEquals(catalog.registry.groups, catalog.families.map { it.group })
            for (art in catalog.all) assertEquals(art, catalog.byId(art.id))
            assertEquals(catalog.registry.defaultId, catalog.default.id)
        }
    }

    @Test
    fun `a pick is drawn as its art once unlocked - and as the default while it's locked`() {
        val none = StyleAccess(AchievementsState())
        val all = StyleAccess(AchievementsState(Achievement.entries.associateWith { 0L }))
        for (catalog in catalogs) for (art in catalog.all) {
            assertEquals(if (catalog.registry.isUnlocked(art.id, none)) art else catalog.default, catalog.unlockedById(art.id, none))
            assertEquals(art, catalog.unlockedById(art.id, all))
        }
    }

    @Test
    fun `golden eggs roll only from the white and brown Egg dice and the Chicken cups - and only the empty Flowerpot grows`() {
        assertEquals(listOf("egg_white", "egg_brown"), DiceStyles.all.filter { it.rollsGoldenEggs }.map { it.id })
        assertEquals(listOf("chicken_white", "chicken_brown"), DiceCupStyles.all.filter { it.laysGoldenEggs }.map { it.id })
        assertEquals(listOf("flowerpot_terracotta"), DiceCupStyles.all.filter { it.growsPlant }.map { it.id })
    }

    @Test
    fun `the Ocean background rolls its Waves then lies still as Lagoon`() {
        val ocean = TableBackgrounds.families.single { it.name.key == "style_family_ocean" }.colours.map { it.style }
        assertTrue(ocean[0] is OceanWavesBackground)
        assertTrue(ocean[1] is OceanLagoonBackground)
    }
}
