package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.graphics.Color
import net.zodac.dicefive.ui.theme.BarrelBackgroundTop
import net.zodac.dicefive.ui.theme.BarrelDiceTop
import net.zodac.dicefive.ui.theme.BarrelTrayTop
import net.zodac.dicefive.ui.theme.BarrelWood
import net.zodac.dicefive.ui.theme.FacetedCupLitFace
import net.zodac.dicefive.ui.theme.FeltNavyTop
import net.zodac.dicefive.ui.theme.FireBackgroundTop
import net.zodac.dicefive.ui.theme.FireCupLitFace
import net.zodac.dicefive.ui.theme.FireDiceTop
import net.zodac.dicefive.ui.theme.FireTrayTop
import net.zodac.dicefive.ui.theme.IvoryDiceTop
import net.zodac.dicefive.ui.theme.TrayBlueTop

/** One colour of a [StyleFamily]: the concrete piece of art it resolves to, and how it's named and shown. */
data class StyleColour<T : TableArt>(val name: String, val swatch: Color, val style: T)

/**
 * One style - a shape or pattern - offered on the Styles screen as a single tile, in one or more
 * [colours]. The first colour is the one the tile shows until the player picks another.
 */
data class StyleFamily<T : TableArt>(val name: String, val colours: List<StyleColour<T>>) {
    init {
        require(colours.isNotEmpty()) { "A style needs at least one colour" }
    }

    fun colourOf(id: String): StyleColour<T>? = colours.firstOrNull { it.style.id == id }
}

/**
 * Every [StyleFamily] in one category of table art, in the order they're offered on the Styles
 * screen. Every colour of every family is a separately saved pick, by its own [TableArt.id].
 */
open class StyleCatalog<T : TableArt>(val families: List<StyleFamily<T>>) {
    val all: List<T> = families.flatMap { family -> family.colours.map { it.style } }

    /** The first colour of the first family. */
    val default: T = all.first()

    init {
        require(all.map { it.id }.toSet().size == all.size) { "Style ids must be unique within a category" }
    }

    fun byId(id: String): T = all.firstOrNull { it.id == id } ?: default

    /** The family [id] belongs to - or the default's, for an id nothing recognises, as [byId] does. */
    fun familyOf(id: String): StyleFamily<T> = families.first { it.colourOf(byId(id).id) != null }
}

object DiceStyles : StyleCatalog<DiceStyle>(
    listOf(
        StyleFamily(
            "Classic",
            listOf(
                StyleColour("Ivory", IvoryDiceTop, IvoryDiceStyle),
                StyleColour("Red", FireDiceTop, FireDiceStyle),
                StyleColour("Oak", BarrelDiceTop, BarrelDiceStyle),
            ),
        ),
    ),
)

object DiceCupStyles : StyleCatalog<DiceCupStyle>(
    listOf(
        StyleFamily(
            "Faceted",
            listOf(
                StyleColour("Green", FacetedCupLitFace, FacetedDiceCupStyle),
                StyleColour("Red", FireCupLitFace, FireDiceCupStyle),
            ),
        ),
        StyleFamily("Barrel", listOf(StyleColour("Brown", BarrelWood, BarrelDiceCupStyle))),
    ),
)

object TableBackgrounds : StyleCatalog<TableBackground>(
    listOf(
        StyleFamily(
            "Classic",
            listOf(
                StyleColour("Navy", FeltNavyTop, MidnightFeltBackground),
                StyleColour("Red", FireBackgroundTop, FireTableBackground),
                StyleColour("Brown", BarrelBackgroundTop, BarrelTableBackground),
            ),
        ),
    ),
)

object DiceMats : StyleCatalog<DiceMat>(
    listOf(
        StyleFamily(
            "Felt",
            listOf(
                StyleColour("Blue", TrayBlueTop, TrayBlueMat),
                StyleColour("Red", FireTrayTop, FireDiceMat),
            ),
        ),
        StyleFamily("Barrel", listOf(StyleColour("Brown", BarrelTrayTop, BarrelDiceMat))),
    ),
)
