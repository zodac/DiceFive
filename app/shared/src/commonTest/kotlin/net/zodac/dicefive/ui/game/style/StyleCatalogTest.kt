package net.zodac.dicefive.ui.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Styles screen groups each category's colours into styles, but what's saved is still each
 * colour's own id - so every id a player could already have saved must still resolve to the same
 * art, and land in the style the screen should show as picked.
 */
class StyleCatalogTest {

    @Test
    fun everyShippedIdStillResolvesToItsOwnArt() {
        val shipped = mapOf(
            DiceStyles to listOf("ivory", "fire", "barrel"),
            DiceCupStyles to listOf("casino_black", "faceted", "fire", "barrel"),
            DiceMats to listOf("tray_blue", "fire", "barrel"),
            TableBackgrounds to listOf("midnight_felt", "fire", "barrel"),
        )
        for ((catalog, ids) in shipped) {
            for (id in ids) assertEquals(id, catalog.byId(id).id)
        }
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds)) {
            for (art in catalog.all) {
                assertEquals(art, catalog.byId(art.id))
            }
        }
    }

    @Test
    fun everyStyleComesInOneToThreeColours() {
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds)) {
            for (family in catalog.families) {
                assertTrue(family.colours.size in 1..3, "${family.name} has ${family.colours.size} colours")
            }
        }
    }

    @Test
    fun everyDefaultStyleIsCalledClassic() {
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds)) {
            assertEquals("Classic", catalog.familyOf(catalog.default.id).name)
        }
    }

    @Test
    fun defaultIsTheFirstColourOfTheFirstStyle() {
        assertEquals(IvoryDiceStyle, DiceStyles.default)
        assertEquals("casino_black", DiceCupStyles.default.id)
        assertEquals(listOf("Classic", "Faceted"), DiceCupStyles.families.take(2).map { it.name })
        assertEquals(TrayBlueMat, DiceMats.default)
        assertEquals(MidnightFeltBackground, TableBackgrounds.default)
    }

    @Test
    fun coloursOfOneShapeShareAStyle() {
        assertEquals("Faceted", DiceCupStyles.familyOf("fire").name)
        assertEquals("Barrel", DiceCupStyles.familyOf("barrel").name)
        assertEquals("Classic", DiceMats.familyOf("fire").name)
        assertEquals("Wood", DiceMats.familyOf("barrel").name)
        for (id in listOf("ivory", "fire", "barrel")) {
            assertEquals("Classic", DiceStyles.familyOf(id).name)
            assertEquals("Classic", TableBackgrounds.familyOf(if (id == "ivory") "midnight_felt" else id).name)
        }
    }

    @Test
    fun unknownIdFallsBackToTheDefaultAndItsStyle() {
        assertEquals("casino_black", DiceCupStyles.byId("leather").id)
        assertEquals("Classic", DiceCupStyles.familyOf("leather").name)
    }
}
