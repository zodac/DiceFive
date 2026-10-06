package net.zodac.dicefive.model

/**
 * The colours a player can be given on the setup screen - a curated set rather than a free picker, so
 * every one reads against the dark table and no two look alike. The drawn value lives in `ui/theme`
 * (`PlayerColour.color`); this stays Compose-free like the rest of the model.
 *
 * The first four, in order, are each seat's default ([defaultFor]).
 */
enum class PlayerColour(val label: String) {
    CYAN("Cyan"),
    GREEN("Green"),
    PURPLE("Purple"),
    AMBER("Amber"),
    PINK("Pink"),
    RED("Red"),
    BLUE("Blue"),
    LIME("Lime"),
    ;

    companion object {
        /** The default colour of the player in [seat] (0 for player 1): distinct for every seat up to four. */
        fun defaultFor(seat: Int): PlayerColour = entries[(seat % entries.size + entries.size) % entries.size]
    }
}
