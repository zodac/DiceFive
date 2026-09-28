package net.zodac.dicefive.ui.game.style

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Styles screen groups each category's colours into styles, but what's saved is still each
 * colour's own id - so every id a player could already have saved must still resolve to the same
 * art, and land in the style the screen should show as picked.
 */
class StyleCatalogTest {

    @Test
    fun everyShippedIdStillResolvesToItsOwnArt() {
        assertEquals(listOf("ivory", "fire", "barrel"), DiceStyles.all.map { it.id })
        assertEquals(listOf("faceted", "fire", "barrel"), DiceCupStyles.all.map { it.id })
        assertEquals(listOf("tray_blue", "fire", "barrel"), DiceMats.all.map { it.id })
        assertEquals(listOf("midnight_felt", "fire", "barrel"), TableBackgrounds.all.map { it.id })
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds)) {
            for (art in catalog.all) {
                assertEquals(art, catalog.byId(art.id))
            }
        }
    }

    @Test
    fun defaultIsTheFirstColourOfTheFirstStyle() {
        assertEquals(IvoryDiceStyle, DiceStyles.default)
        assertEquals(FacetedDiceCupStyle, DiceCupStyles.default)
        assertEquals(TrayBlueMat, DiceMats.default)
        assertEquals(MidnightFeltBackground, TableBackgrounds.default)
    }

    @Test
    fun coloursOfOneShapeShareAStyle() {
        assertEquals("Faceted", DiceCupStyles.familyOf("fire").name)
        assertEquals("Barrel", DiceCupStyles.familyOf("barrel").name)
        assertEquals("Felt", DiceMats.familyOf("fire").name)
        assertEquals("Barrel", DiceMats.familyOf("barrel").name)
        assertEquals(listOf("Classic"), DiceStyles.families.map { it.name })
        assertEquals(listOf("Classic"), TableBackgrounds.families.map { it.name })
    }

    @Test
    fun unknownIdFallsBackToTheDefaultAndItsStyle() {
        assertEquals(FacetedDiceCupStyle, DiceCupStyles.byId("leather"))
        assertEquals("Faceted", DiceCupStyles.familyOf("leather").name)
    }
}
