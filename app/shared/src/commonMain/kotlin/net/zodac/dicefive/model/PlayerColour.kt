package net.zodac.dicefive.model

import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.player_colour_amber
import net.zodac.dicefive.resources.player_colour_blue
import net.zodac.dicefive.resources.player_colour_cyan
import net.zodac.dicefive.resources.player_colour_green
import net.zodac.dicefive.resources.player_colour_lime
import net.zodac.dicefive.resources.player_colour_pink
import net.zodac.dicefive.resources.player_colour_purple
import net.zodac.dicefive.resources.player_colour_red
import org.jetbrains.compose.resources.StringResource

/**
 * The colours a player can be given on the setup screen - a curated set rather than a free picker, so
 * every one reads against the dark table and no two look alike. The drawn value lives in `ui/theme`
 * (`PlayerColour.color`); this stays Compose-free like the rest of the model.
 *
 * The first four, in order, are each seat's default ([defaultFor]).
 */
enum class PlayerColour(val label: StringResource) {
    CYAN(Res.string.player_colour_cyan),
    GREEN(Res.string.player_colour_green),
    PURPLE(Res.string.player_colour_purple),
    AMBER(Res.string.player_colour_amber),
    PINK(Res.string.player_colour_pink),
    RED(Res.string.player_colour_red),
    BLUE(Res.string.player_colour_blue),
    LIME(Res.string.player_colour_lime),
    ;

    companion object {
        /** The default colour of the player in [seat] (0 for player 1): distinct for every seat up to four. */
        fun defaultFor(seat: Int): PlayerColour = entries[(seat % entries.size + entries.size) % entries.size]
    }
}
