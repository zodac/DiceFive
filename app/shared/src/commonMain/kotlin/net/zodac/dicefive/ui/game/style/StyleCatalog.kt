package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.FLOWERPOT_FULL_BLOOM
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.style_colour_amber
import net.zodac.dicefive.resources.style_colour_arabic
import net.zodac.dicefive.resources.style_colour_arabic_green
import net.zodac.dicefive.resources.style_colour_barrel
import net.zodac.dicefive.resources.style_colour_basalt
import net.zodac.dicefive.resources.style_colour_black
import net.zodac.dicefive.resources.style_colour_black_marble
import net.zodac.dicefive.resources.style_colour_blue
import net.zodac.dicefive.resources.style_colour_blueprint
import net.zodac.dicefive.resources.style_colour_bronze
import net.zodac.dicefive.resources.style_colour_brown
import net.zodac.dicefive.resources.style_colour_bud
import net.zodac.dicefive.resources.style_colour_charcoal
import net.zodac.dicefive.resources.style_colour_chinese
import net.zodac.dicefive.resources.style_colour_chocolate
import net.zodac.dicefive.resources.style_colour_classic
import net.zodac.dicefive.resources.style_colour_clear
import net.zodac.dicefive.resources.style_colour_copper
import net.zodac.dicefive.resources.style_colour_crimson
import net.zodac.dicefive.resources.style_colour_daisy
import net.zodac.dicefive.resources.style_colour_digits
import net.zodac.dicefive.resources.style_colour_double
import net.zodac.dicefive.resources.style_colour_dunes
import net.zodac.dicefive.resources.style_colour_emerald
import net.zodac.dicefive.resources.style_colour_fan
import net.zodac.dicefive.resources.style_colour_flame
import net.zodac.dicefive.resources.style_colour_forestry
import net.zodac.dicefive.resources.style_colour_gold
import net.zodac.dicefive.resources.style_colour_granite
import net.zodac.dicefive.resources.style_colour_green
import net.zodac.dicefive.resources.style_colour_green_mono
import net.zodac.dicefive.resources.style_colour_grey
import net.zodac.dicefive.resources.style_colour_honey
import net.zodac.dicefive.resources.style_colour_ice
import net.zodac.dicefive.resources.style_colour_indigo
import net.zodac.dicefive.resources.style_colour_irish
import net.zodac.dicefive.resources.style_colour_ivory
import net.zodac.dicefive.resources.style_colour_ivory_serif
import net.zodac.dicefive.resources.style_colour_japanese
import net.zodac.dicefive.resources.style_colour_japanese_black
import net.zodac.dicefive.resources.style_colour_key
import net.zodac.dicefive.resources.style_colour_knot
import net.zodac.dicefive.resources.style_colour_kraft
import net.zodac.dicefive.resources.style_colour_lapis
import net.zodac.dicefive.resources.style_colour_latin_american
import net.zodac.dicefive.resources.style_colour_laurel
import net.zodac.dicefive.resources.style_colour_lava
import net.zodac.dicefive.resources.style_colour_limestone
import net.zodac.dicefive.resources.style_colour_manzu
import net.zodac.dicefive.resources.style_colour_menu
import net.zodac.dicefive.resources.style_colour_midnight
import net.zodac.dicefive.resources.style_colour_mixed
import net.zodac.dicefive.resources.style_colour_navy
import net.zodac.dicefive.resources.style_colour_neon
import net.zodac.dicefive.resources.style_colour_night
import net.zodac.dicefive.resources.style_colour_oak
import net.zodac.dicefive.resources.style_colour_oil_drum
import net.zodac.dicefive.resources.style_colour_olive
import net.zodac.dicefive.resources.style_colour_opening
import net.zodac.dicefive.resources.style_colour_oxblood
import net.zodac.dicefive.resources.style_colour_pencil
import net.zodac.dicefive.resources.style_colour_pewter
import net.zodac.dicefive.resources.style_colour_pink
import net.zodac.dicefive.resources.style_colour_pink_and_blue
import net.zodac.dicefive.resources.style_colour_pinzu
import net.zodac.dicefive.resources.style_colour_purple
import net.zodac.dicefive.resources.style_colour_purple_script
import net.zodac.dicefive.resources.style_colour_rabbit
import net.zodac.dicefive.resources.style_colour_rainbow
import net.zodac.dicefive.resources.style_colour_red
import net.zodac.dicefive.resources.style_colour_red_block
import net.zodac.dicefive.resources.style_colour_retro
import net.zodac.dicefive.resources.style_colour_ring
import net.zodac.dicefive.resources.style_colour_roman
import net.zodac.dicefive.resources.style_colour_roman_blue
import net.zodac.dicefive.resources.style_colour_rose
import net.zodac.dicefive.resources.style_colour_rose_gold
import net.zodac.dicefive.resources.style_colour_sandstone
import net.zodac.dicefive.resources.style_colour_screen
import net.zodac.dicefive.resources.style_colour_seedling
import net.zodac.dicefive.resources.style_colour_shipping
import net.zodac.dicefive.resources.style_colour_silver
import net.zodac.dicefive.resources.style_colour_single
import net.zodac.dicefive.resources.style_colour_slate
import net.zodac.dicefive.resources.style_colour_soil
import net.zodac.dicefive.resources.style_colour_sozu
import net.zodac.dicefive.resources.style_colour_stepped
import net.zodac.dicefive.resources.style_colour_strawberry
import net.zodac.dicefive.resources.style_colour_sunflower
import net.zodac.dicefive.resources.style_colour_swirl
import net.zodac.dicefive.resources.style_colour_tan
import net.zodac.dicefive.resources.style_colour_teal_plain
import net.zodac.dicefive.resources.style_colour_terracotta
import net.zodac.dicefive.resources.style_colour_tricolour
import net.zodac.dicefive.resources.style_colour_vanilla
import net.zodac.dicefive.resources.style_colour_vine
import net.zodac.dicefive.resources.style_colour_walnut
import net.zodac.dicefive.resources.style_colour_wave
import net.zodac.dicefive.resources.style_colour_waves
import net.zodac.dicefive.resources.style_colour_western
import net.zodac.dicefive.resources.style_colour_white
import net.zodac.dicefive.resources.style_colour_white_marble
import net.zodac.dicefive.resources.style_colour_white_rainbow
import net.zodac.dicefive.resources.style_colour_white_wave
import net.zodac.dicefive.resources.style_colour_wicker
import net.zodac.dicefive.resources.style_colour_yellow
import net.zodac.dicefive.resources.style_family_art_deco
import net.zodac.dicefive.resources.style_family_beaker
import net.zodac.dicefive.resources.style_family_bestagon
import net.zodac.dicefive.resources.style_family_brick
import net.zodac.dicefive.resources.style_family_cake
import net.zodac.dicefive.resources.style_family_casino
import net.zodac.dicefive.resources.style_family_cauldron
import net.zodac.dicefive.resources.style_family_celtic
import net.zodac.dicefive.resources.style_family_chicken
import net.zodac.dicefive.resources.style_family_circuit
import net.zodac.dicefive.resources.style_family_classic
import net.zodac.dicefive.resources.style_family_containers
import net.zodac.dicefive.resources.style_family_d20
import net.zodac.dicefive.resources.style_family_desert
import net.zodac.dicefive.resources.style_family_egg
import net.zodac.dicefive.resources.style_family_faceted
import net.zodac.dicefive.resources.style_family_floating_dice
import net.zodac.dicefive.resources.style_family_floral
import net.zodac.dicefive.resources.style_family_flowerpot
import net.zodac.dicefive.resources.style_family_frosted
import net.zodac.dicefive.resources.style_family_garden
import net.zodac.dicefive.resources.style_family_gems
import net.zodac.dicefive.resources.style_family_gift_box
import net.zodac.dicefive.resources.style_family_gift_wrap
import net.zodac.dicefive.resources.style_family_gingham
import net.zodac.dicefive.resources.style_family_glass
import net.zodac.dicefive.resources.style_family_glitch
import net.zodac.dicefive.resources.style_family_glitter
import net.zodac.dicefive.resources.style_family_googly
import net.zodac.dicefive.resources.style_family_greek
import net.zodac.dicefive.resources.style_family_hex_tiles
import net.zodac.dicefive.resources.style_family_honeycomb
import net.zodac.dicefive.resources.style_family_lcd
import net.zodac.dicefive.resources.style_family_leather
import net.zodac.dicefive.resources.style_family_mahjong
import net.zodac.dicefive.resources.style_family_martini
import net.zodac.dicefive.resources.style_family_maths
import net.zodac.dicefive.resources.style_family_meadow
import net.zodac.dicefive.resources.style_family_metal
import net.zodac.dicefive.resources.style_family_misprint
import net.zodac.dicefive.resources.style_family_multicolour
import net.zodac.dicefive.resources.style_family_neon
import net.zodac.dicefive.resources.style_family_non_english
import net.zodac.dicefive.resources.style_family_numeral
import net.zodac.dicefive.resources.style_family_obsidian
import net.zodac.dicefive.resources.style_family_picnic
import net.zodac.dicefive.resources.style_family_pinstripe
import net.zodac.dicefive.resources.style_family_pixel
import net.zodac.dicefive.resources.style_family_planks
import net.zodac.dicefive.resources.style_family_poker
import net.zodac.dicefive.resources.style_family_pyramid
import net.zodac.dicefive.resources.style_family_retro
import net.zodac.dicefive.resources.style_family_rgb
import net.zodac.dicefive.resources.style_family_ribbon
import net.zodac.dicefive.resources.style_family_sand
import net.zodac.dicefive.resources.style_family_spotlight
import net.zodac.dicefive.resources.style_family_starry
import net.zodac.dicefive.resources.style_family_stone
import net.zodac.dicefive.resources.style_family_sunburst
import net.zodac.dicefive.resources.style_family_takeaway
import net.zodac.dicefive.resources.style_family_tally
import net.zodac.dicefive.resources.style_family_tankard
import net.zodac.dicefive.resources.style_family_text
import net.zodac.dicefive.resources.style_family_top_hat
import net.zodac.dicefive.resources.style_family_treasure
import net.zodac.dicefive.resources.style_family_tribal
import net.zodac.dicefive.resources.style_family_urn
import net.zodac.dicefive.resources.style_family_volcano
import net.zodac.dicefive.resources.style_family_wood
import net.zodac.dicefive.resources.style_family_wreath
import net.zodac.dicefive.resources.style_noun_background
import net.zodac.dicefive.resources.style_noun_dice
import net.zodac.dicefive.resources.style_noun_dice_cup
import net.zodac.dicefive.resources.style_noun_frame
import net.zodac.dicefive.resources.style_noun_mat
import net.zodac.dicefive.resources.style_variant_colour
import net.zodac.dicefive.resources.style_variant_design
import net.zodac.dicefive.ui.game.style.StyleUnlock.AchievementCount
import net.zodac.dicefive.ui.theme.BarrelBackgroundTop
import net.zodac.dicefive.ui.theme.BarrelDiceTop
import net.zodac.dicefive.ui.theme.BarrelTrayTop
import net.zodac.dicefive.ui.theme.BarrelWood
import net.zodac.dicefive.ui.theme.CupRimGold
import net.zodac.dicefive.ui.theme.DicePipColor
import net.zodac.dicefive.ui.theme.FacetedCupLitFace
import net.zodac.dicefive.ui.theme.FeltNavyBottom
import net.zodac.dicefive.ui.theme.FeltNavyTop
import net.zodac.dicefive.ui.theme.FireBackgroundTop
import net.zodac.dicefive.ui.theme.FireCupLitFace
import net.zodac.dicefive.ui.theme.FireTrayTop
import net.zodac.dicefive.ui.theme.FlowerpotLeaf
import net.zodac.dicefive.ui.theme.FlowerpotStem
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.IvoryDiceBottom
import net.zodac.dicefive.ui.theme.IvoryDiceTop
import net.zodac.dicefive.ui.theme.RabbitFur
import net.zodac.dicefive.ui.theme.SunflowerPetal
import net.zodac.dicefive.ui.theme.SunflowerPetalShade
import net.zodac.dicefive.ui.theme.SurfaceContainerHigh
import net.zodac.dicefive.ui.theme.TrayBlueTop
import org.jetbrains.compose.resources.StringResource

/** Table art that knows its own most representative colour - its dot on the Styles screen. */
interface Swatched {
    val swatch: Color
}

/** One colour of a [StyleFamily]: the concrete piece of art it resolves to, and how it's named and shown. */
data class StyleColour<out T : TableArt>(
    val name: StringResource,
    val swatch: Color,
    val style: T,
    /**
     * A secret achievement that alone unlocks this colour, on top of its family's own lock: until it's
     * earned the colour isn't offered on the Styles screen at all, and a saved pick of it draws the default.
     */
    val secretAchievement: Achievement? = null,
) {
    fun isAvailable(achievements: AchievementsState): Boolean = secretAchievement == null || achievements.isUnlocked(secretAchievement)
}

/**
 * One style - a shape or pattern - offered on the Styles screen as a single tile, in one or more
 * [colours]. The first colour is the one the tile shows until the player picks another. [unlock] is
 * what it takes to use any of them - see [StyleUnlock].
 */
data class StyleFamily<T : TableArt>(
    val name: StringResource,
    val colours: List<StyleColour<T>>,
    val unlock: StyleUnlock = StyleUnlock.Free,
) {
    init {
        require(colours.isNotEmpty()) { "A style needs at least one colour" }
    }

    fun colourOf(id: String): StyleColour<T>? = colours.firstOrNull { it.style.id == id }

    /** The [colours] to offer: all but any secret one not yet earned. */
    fun availableColours(achievements: AchievementsState): List<StyleColour<T>> = colours.filter { it.isAvailable(achievements) }
}

/**
 * Every [StyleFamily] in one category of table art, in the order they're offered on the Styles
 * screen. Every colour of every family is a separately saved pick, by its own [TableArt.id].
 * [noun] is what one of the category is called in a sentence - "the 'Irish' dice style" - and
 * [variantNoun] what one of a family's [StyleFamily.colours] is: a colour, except where they're shapes.
 */
open class StyleCatalog<T : TableArt>(
    val noun: StringResource,
    val families: List<StyleFamily<T>>,
    val variantNoun: StringResource = Res.string.style_variant_colour,
) {
    val all: List<T> = families.flatMap { family -> family.colours.map { it.style } }

    /** The first colour of the first family. */
    val default: T = all.first()

    init {
        require(all.map { it.id }.toSet().size == all.size) { "Style ids must be unique within a category" }
    }

    fun byId(id: String): T = all.firstOrNull { it.id == id } ?: default

    /** The family [id] belongs to - or the default's, for an id nothing recognises, as [byId] does. */
    fun familyOf(id: String): StyleFamily<T> = families.first { it.colourOf(byId(id).id) != null }

    /** Whether the style [id] belongs to has been unlocked - see [StyleUnlock]. */
    fun isUnlocked(id: String, achievements: AchievementsState): Boolean {
        val family = familyOf(id)
        return family.unlock.isMet(achievements) && family.colourOf(byId(id).id)?.isAvailable(achievements) != false
    }

    /**
     * What to actually draw for the saved pick [id]: [byId], unless its style is still locked, in
     * which case the [default] - the saved pick itself is left alone, see [StyleUnlock].
     */
    fun unlockedById(id: String, achievements: AchievementsState): T = if (isUnlocked(id, achievements)) byId(id) else default
}

/** A Classic die in one of Tricolour mode's colours, from the same palette that colours its dice in that mode. */
private fun classicDie(id: String, colour: DieColour): StyleColour<DiceStyle> =
    StyleColour(
        when (colour) {
            DieColour.RED -> Res.string.style_colour_red
            DieColour.YELLOW -> Res.string.style_colour_yellow
            DieColour.BLUE -> Res.string.style_colour_blue
        },
        colour.palette.swatch, ColouredClassicDiceStyle(id, colour.palette))

/** Every category's catalog, in the Styles screen's order. */
val StyleCatalogs: List<StyleCatalog<*>> by lazy { listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds, ScoreFrames) }

object DiceStyles : StyleCatalog<DiceStyle>(
    Res.string.style_noun_dice,
    listOf(
        StyleFamily(
            Res.string.style_family_classic,
            listOf(
                StyleColour(Res.string.style_colour_ivory, IvoryDiceTop, IvoryDiceStyle),
                classicDie("classic_red", DieColour.RED),
                classicDie("classic_yellow", DieColour.YELLOW),
                classicDie("classic_blue", DieColour.BLUE),
                StyleColour(Res.string.style_colour_oak, BarrelDiceTop, BarrelDiceStyle),
            ),
        ),
        StyleFamily(
            Res.string.style_family_frosted,
            listOf(
                colour(Res.string.style_colour_ice, FrostedDiceStyle("frosted_ice", Color(0xFFE3F4FF), Color(0xFFA9D3EE), Color(0xFF1D4E6E))),
                colour(Res.string.style_colour_white, FrostedDiceStyle("frosted_white", Color(0xFFFAFBFC), Color(0xFFD5DADF), Color(0xFF3A4550))),
            ),
            unlock = AchievementCount(23),
        ),
        StyleFamily(
            Res.string.style_family_metal,
            listOf(
                // Gold and bronze dice would swallow the usual gold held ring, so theirs is white.
                colour(
                    Res.string.style_colour_gold,
                    MetalDiceStyle(
                        "metal_gold",
                        listOf(Color(0xFFFFE9A3), Color(0xFFD4A437), Color(0xFFFFE08A), Color(0xFF9C7219)),
                        Color(0xFF5C420B),
                        heldRing = Color.White,
                    ),
                ),
                colour(
                    Res.string.style_colour_silver,
                    MetalDiceStyle(
                        "metal_silver",
                        listOf(Color(0xFFF2F4F6), Color(0xFFAEB5BB), Color(0xFFE6EAED), Color(0xFF7C848B)),
                        Color(0xFF33393E),
                    ),
                ),
                colour(
                    Res.string.style_colour_bronze,
                    MetalDiceStyle(
                        "metal_bronze",
                        listOf(Color(0xFFF0B884), Color(0xFFB06A34), Color(0xFFE3A56C), Color(0xFF7A4418)),
                        Color(0xFF3F2008),
                        heldRing = Color.White,
                    ),
                ),
            ),
            unlock = AchievementCount(19),
        ),
        StyleFamily(
            Res.string.style_family_retro,
            listOf(
                colour(Res.string.style_colour_green, RetroDiceStyle("retro_green", Color(0xFF9BBC0F), Color(0xFF306230), Color(0xFF0F380F))),
                colour(
                    Res.string.style_colour_amber,
                    RetroDiceStyle("retro_amber", Color(0xFFFFB000), Color(0xFF9A5B00), Color(0xFF3A2400), heldRing = Color.White),
                ),
                colour(Res.string.style_colour_blue, RetroDiceStyle("retro_blue", Color(0xFF9CC8F5), Color(0xFF2E5C99), Color(0xFF0D2547))),
                colour(Res.string.style_colour_red, RetroDiceStyle("retro_red", Color(0xFFF29C94), Color(0xFF9E2A24), Color(0xFF3D0B08))),
            ),
            unlock = AchievementCount(2),
        ),
        StyleFamily(
            Res.string.style_family_numeral,
            listOf(
                colour(Res.string.style_colour_digits, NumeralDiceStyle("numeral_white", Color(0xFFFBFBFB), Color(0xFFD6D6D6), Color(0xFF1E1E1E))),
                colour(Res.string.style_colour_red, NumeralDiceStyle("numeral_red", Color(0xFFE53935), Color(0xFF8E0E0E), Color.White)),
                colour(Res.string.style_colour_blue, NumeralDiceStyle("numeral_blue", Color(0xFF2F7FE0), Color(0xFF0B3A80), Color.White)),
            ),
            unlock = AchievementCount(43),
        ),
        StyleFamily(
            Res.string.style_family_non_english,
            listOf(
                colour(
                    Res.string.style_colour_roman,
                    NumeralDiceStyle("numeral_roman", Color(0xFFF3E9D2), Color(0xFFD8C8A0), Color(0xFF7A1F1F), NumeralSystem.ROMAN),
                ),
                colour(
                    Res.string.style_colour_roman_blue,
                    NumeralDiceStyle("numeral_roman_blue", Color(0xFF2F4A73), Color(0xFF17263F), Color(0xFFF3E9D2), NumeralSystem.ROMAN),
                ),
                colour(
                    Res.string.style_colour_arabic_green,
                    NumeralDiceStyle("numeral_arabic_green", Color(0xFF006C35), Color(0xFF00471F), Color.White, NumeralSystem.EASTERN_ARABIC),
                ),
                colour(
                    Res.string.style_colour_arabic,
                    NumeralDiceStyle("numeral_arabic", Color(0xFF3A3A3A), Color(0xFF121212), Color(0xFFE8C66A), NumeralSystem.EASTERN_ARABIC),
                ),
                colour(
                    Res.string.style_colour_japanese,
                    NumeralDiceStyle("numeral_japanese", Color(0xFFFBFBFB), Color(0xFFE3E3E3), Color(0xFFBC002D), NumeralSystem.JAPANESE),
                ),
                colour(
                    // Black urushi lacquer with maki-e gold.
                    Res.string.style_colour_japanese_black,
                    NumeralDiceStyle("numeral_japanese_black", Color(0xFF1A1714), Color(0xFF050403), Color(0xFFD4AF37), NumeralSystem.JAPANESE),
                ),
            ),
            unlock = AchievementCount(50),
        ),
        StyleFamily(
            Res.string.style_family_text,
            listOf(
                colour(
                    Res.string.style_colour_ivory_serif,
                    NumeralDiceStyle(
                        "text_serif", Color(0xFFF7F0DC), Color(0xFFDCCFA8), Color(0xFF1F2F5C), NumeralSystem.ENGLISH,
                        font = FontFamily.Serif, size = 0.26f,
                    ),
                ),
                colour(
                    Res.string.style_colour_green_mono,
                    NumeralDiceStyle(
                        "text_mono", Color(0xFF1A1F1A), Color(0xFF080B08), Color(0xFF4CE07A), NumeralSystem.ENGLISH,
                        font = FontFamily.Monospace, size = 0.2f,
                    ),
                ),
                colour(
                    Res.string.style_colour_purple_script,
                    NumeralDiceStyle(
                        "text_script", Color(0xFF6A3FA0), Color(0xFF34195C), Color(0xFFFFD86B), NumeralSystem.ENGLISH,
                        font = FontFamily.Cursive, size = 0.32f,
                    ),
                ),
                colour(
                    Res.string.style_colour_red_block,
                    NumeralDiceStyle(
                        "text_block", Color(0xFFE53935), Color(0xFF8E0E0E), Color.White, NumeralSystem.ENGLISH_CAPS,
                        font = FontFamily.SansSerif,
                    ),
                ),
                colour(
                    Res.string.style_colour_teal_plain,
                    NumeralDiceStyle(
                        "text_plain", Color(0xFF1FA6A0), Color(0xFF0B5A57), Color.White, NumeralSystem.ENGLISH,
                        font = FontFamily.SansSerif, size = 0.27f,
                    ),
                ),
            ),
            unlock = AchievementCount(15),
        ),
        // Secret: not on the Styles screen at all until The Solution is earned.
        StyleFamily(
            Res.string.style_family_maths,
            listOf(
                colour(Res.string.style_colour_white, MathsDiceStyle("maths_white", IvoryDiceTop, IvoryDiceBottom, Color.Black)),
                colour(Res.string.style_colour_black, MathsDiceStyle("maths_black", Color(0xFF2E2E31), Color(0xFF0E0E0F), Color.White)),
                // A chalkboard.
                colour(Res.string.style_colour_green, MathsDiceStyle("maths_green", Color(0xFF2F6B45), Color(0xFF173D26), Color.White)),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.THE_SOLUTION),
        ),
        StyleFamily(
            Res.string.style_family_gems,
            listOf(
                colour(Res.string.style_colour_ivory, GemDiceStyle("gems_ivory", IvoryDiceTop, IvoryDiceBottom)),
                colour(Res.string.style_colour_black, GemDiceStyle("gems_black", Color(0xFF2E2E31), Color(0xFF0E0E0F))),
            ),
            unlock = AchievementCount(38),
        ),
        StyleFamily(
            Res.string.style_family_lcd,
            listOf(
                colour(Res.string.style_colour_neon, LcdDiceStyle("lcd_neon", Color(0xFF15181D), Color(0xFF07090D), Color(0xFF3FD7FF), glow = true)),
                colour(Res.string.style_colour_white, LcdDiceStyle("lcd_white", Color(0xFFF4F4F0), Color(0xFFE2E4DE), Color(0xFF151515), glow = false)),
            ),
            unlock = AchievementCount(49),
        ),
        StyleFamily(
            Res.string.style_family_rgb,
            listOf(
                colour(Res.string.style_colour_rainbow, RgbDiceStyle("rgb_rainbow", Color(0xFF0E0E11))),
                colour(Res.string.style_colour_wave, RgbDiceStyle("rgb_wave", Color(0xFF0E0E11), wave = true)),
                colour(Res.string.style_colour_white_rainbow, RgbDiceStyle("rgb_white_rainbow", IvoryDiceTop, IvoryDiceBottom)),
                colour(Res.string.style_colour_white_wave, RgbDiceStyle("rgb_white_wave", IvoryDiceTop, IvoryDiceBottom, wave = true)),
            ),
            unlock = AchievementCount(78),
        ),
        StyleFamily(
            Res.string.style_family_d20,
            listOf(
                colour(Res.string.style_colour_white, D20DiceStyle("d20_white", Color(0xFFFBFBF8), Color(0xFFB9BCC2), Color(0xFF1A1A1A))),
                // The brand's own pairing: the felt blue with the gold the board uses for "press this" -
                // so the held ring is white instead, not lost against gold numbers.
                colour(Res.string.style_colour_blue, D20DiceStyle("d20_blue", Color(0xFF3A6BB0), Color(0xFF14315C), GoldAccent, heldRing = Color.White)),
            ),
            unlock = AchievementCount(29),
        ),
        StyleFamily(
            Res.string.style_family_googly,
            listOf(
                colour(Res.string.style_colour_ivory, GooglyDiceStyle("googly_ivory", IvoryDiceTop, IvoryDiceBottom, DicePipColor)),
                colour(
                    Res.string.style_colour_black,
                    GooglyDiceStyle("googly_black", Color(0xFF3A3A3E), Color(0xFF141416), Color.White),
                ),
                // Secret: only offered once Big Fan is earned.
                GooglyDiceStyle("googly_blue", Color(0xFF3A6BB0), Color(0xFF14315C), GoldAccent, socket = GoldAccent).let {
                    StyleColour(Res.string.style_colour_blue, it.swatch, it, secretAchievement = Achievement.BIG_FAN)
                },
            ),
            unlock = AchievementCount(31),
        ),
        StyleFamily(
            Res.string.style_family_misprint,
            listOf(
                colour(Res.string.style_colour_pencil, MisprintDiceStyle("misprint_pencil", Color(0xFFFAF7F0), Color(0xFF3A3A3A), seed = 1)),
                colour(Res.string.style_colour_blueprint, MisprintDiceStyle("misprint_blueprint", Color(0xFF1F4E8C), Color(0xFFEAF2FF), seed = 2)),
            ),
            unlock = AchievementCount(48),
        ),
        StyleFamily(
            Res.string.style_family_multicolour,
            listOf(
                colour(Res.string.style_colour_tricolour, TricolourStripedDiceStyle),
                colour(Res.string.style_colour_rainbow, RainbowStripedDiceStyle),
                // Secret: only offered once Luck of the Irish is earned.
                StyleColour(Res.string.style_colour_irish, IrishFlagDiceStyle.swatch, IrishFlagDiceStyle, secretAchievement = Achievement.LUCK_OF_THE_IRISH),
            ),
            unlock = AchievementCount(36),
        ),
        StyleFamily(
            Res.string.style_family_cake,
            listOf(
                colour(Res.string.style_colour_strawberry, CakeDiceStyle("cake_pink", Color(0xFFFFD3DE), Color(0xFFEFA9BA))),
                colour(Res.string.style_colour_vanilla, CakeDiceStyle("cake_vanilla", Color(0xFFFFF6E6), Color(0xFFEBD9BC))),
                // Milk chocolate, light enough for the strawberries to stand out, each on a dollop of cream.
                colour(Res.string.style_colour_chocolate, CakeDiceStyle("cake_chocolate", Color(0xFFA06A45), Color(0xFF70452B), dollop = Color(0xFFFFF6E6))),
            ),
            unlock = AchievementCount(7),
        ),
        StyleFamily(
            Res.string.style_family_meadow,
            listOf(colour(Res.string.style_colour_green, MeadowDiceStyle("meadow_green", Color(0xFF7CB342), Color(0xFF4E8A2A)))),
            unlock = AchievementCount(12),
        ),
        StyleFamily(
            Res.string.style_family_poker,
            listOf(colour(Res.string.style_colour_white, PokerDiceStyle("poker_white", Color.White, Color(0xFFE6E6EA)))),
            unlock = AchievementCount(21),
        ),
        StyleFamily(
            Res.string.style_family_obsidian,
            listOf(
                colour(
                    Res.string.style_colour_lava,
                    ObsidianDiceStyle(
                        "obsidian_lava", Color(0xFF2A2830), Color(0xFF060508),
                        Lava(Color(0xFF1A0A04), Color(0xFFB02A00), Color(0xFFFF6A00), Color(0xFFFFF2A0)),
                    ),
                ),
                colour(
                    Res.string.style_colour_blue,
                    ObsidianDiceStyle(
                        "obsidian_blue", Color(0xFF2A2830), Color(0xFF060508),
                        Lava(Color(0xFF040A1A), Color(0xFF0040B0), Color(0xFF2E9BFF), Color(0xFFD8F4FF)),
                    ),
                ),
            ),
            unlock = AchievementCount(45),
        ),
        StyleFamily(
            Res.string.style_family_mahjong,
            listOf(
                colour(Res.string.style_colour_pinzu, MahjongDiceStyle("mahjong_pinzu", MahjongSuit.PINZU, MahjongFace, MahjongFaceShade, MahjongBack)),
                colour(Res.string.style_colour_manzu, MahjongDiceStyle("mahjong_manzu", MahjongSuit.MANZU, MahjongFace, MahjongFaceShade, MahjongBack)),
                colour(Res.string.style_colour_sozu, MahjongDiceStyle("mahjong_sozu", MahjongSuit.SOZU, MahjongFace, MahjongFaceShade, MahjongBack)),
            ),
            unlock = AchievementCount(33),
        ),
        StyleFamily(
            Res.string.style_family_tally,
            listOf(
                colour(
                    Res.string.style_colour_western,
                    TallyDiceStyle(
                        "tally_western", TallySystem.GATE, TallyPen.CHALK, TallySurface.SLATE,
                        Color(0xFF3A4440), Color(0xFF232A27), Color(0xFFF2F2EA), Color.White,
                    ),
                ),
                colour(
                    Res.string.style_colour_chinese,
                    TallyDiceStyle(
                        "tally_chinese", TallySystem.ZHENG, TallyPen.BRUSH, TallySurface.RICE_PAPER,
                        Color(0xFFF6EFDC), Color(0xFFE5D9B9), Color(0xFF1A1A1A), Color(0xFFB8A57A),
                    ),
                ),
                colour(
                    Res.string.style_colour_latin_american,
                    TallyDiceStyle(
                        "tally_latin", TallySystem.SQUARE, TallyPen.PENCIL, TallySurface.NOTEBOOK,
                        Color.White, Color(0xFFECEFF3), Color(0xFF3A3A3E), Color(0xFF8FB4E0),
                    ),
                ),
                colour(
                    Res.string.style_colour_forestry,
                    TallyDiceStyle(
                        "tally_forestry", TallySystem.DOTS, TallyPen.PEN, TallySurface.FIELD_BOOK,
                        Color(0xFFF7E8A4), Color(0xFFE8D47E), Color(0xFF1E2A55), Color(0xFF6A9A6A),
                    ),
                ),
            ),
            unlock = AchievementCount(25),
        ),
        StyleFamily(
            Res.string.style_family_stone,
            listOf(
                colour(Res.string.style_colour_granite, StoneDiceStyle("stone_granite", StoneKind.GRANITE, Color(0xFFB8AEA8), Color(0xFF7A716D), Color(0xFF26221F))),
                colour(Res.string.style_colour_slate, StoneDiceStyle("stone_slate", StoneKind.SLATE, Color(0xFF5A6470), Color(0xFF2E353D), Color(0xFF9AA2AC))),
                colour(Res.string.style_colour_sandstone, StoneDiceStyle("stone_sandstone", StoneKind.SANDSTONE, Color(0xFFE2BE8C), Color(0xFFB0824E), Color(0xFF5A3A1E))),
                colour(Res.string.style_colour_limestone, StoneDiceStyle("stone_limestone", StoneKind.LIMESTONE, Color(0xFFEDE6D3), Color(0xFFC9BFA4), Color(0xFF5E5646))),
                colour(Res.string.style_colour_basalt, StoneDiceStyle("stone_basalt", StoneKind.BASALT, Color(0xFF45464A), Color(0xFF1A1B1E), Color(0xFF8E9096))),
                // Marble, once a style of its own: its ids are saved picks, so they stay as they were.
                colour(
                    Res.string.style_colour_white_marble,
                    MarbleDiceStyle("marble_white", Color(0xFFF4F2EE), Color(0xFFD9D5CE), Color(0xFF8E8A84), Color(0xFF222222), seed = 1),
                ),
                colour(
                    Res.string.style_colour_black_marble,
                    MarbleDiceStyle("marble_black", Color(0xFF3A3A3E), Color(0xFF141416), Color(0xFFD8D8D8), Color(0xFFC8C8CC), seed = 2),
                ),
            ),
            unlock = AchievementCount(47),
        ),
        StyleFamily(
            Res.string.style_family_garden,
            listOf(
                colour(Res.string.style_colour_soil, GardenDiceStyle("garden_soil", Color(0xFF6A4A30), Color(0xFF3E2A1A), soil = true)),
            ),
            unlock = AchievementCount(9),
        ),
        StyleFamily(
            Res.string.style_family_egg,
            listOf(
                // Each egg's pips are the other's shell colour.
                colour(Res.string.style_colour_white, EggDiceStyle("egg_white", EggWhiteShell, Color(0xFFD9D2C3), EggBrownShell)),
                colour(Res.string.style_colour_brown, EggDiceStyle("egg_brown", EggBrownShell, Color(0xFFA8683F), EggWhiteShell)),
                // The golden egg that rolls one time in a thousand with the Chicken cup, kept for good.
                colour(Res.string.style_colour_gold, GoldenEggDiceStyle).copy(secretAchievement = Achievement.EGGCELLENT_DISCOVERY),
            ),
            unlock = AchievementCount(28),
        ),
        StyleFamily(
            Res.string.style_family_bestagon,
            listOf(
                colour(Res.string.style_colour_honey, BestagonDiceStyle("bestagon_honey", Color(0xFFF5BE45), Color(0xFFC4801A), Color(0xFF4A2A08))),
                colour(Res.string.style_colour_slate, BestagonDiceStyle("bestagon_slate", Color(0xFF6E7F8F), Color(0xFF39444F), Color(0xFFF2F4F6))),
            ),
            unlock = AchievementCount(75),
        ),
        StyleFamily(
            Res.string.style_family_pyramid,
            listOf(
                colour(Res.string.style_colour_sandstone, PyramidDiceStyle("pyramid_sandstone", Color(0xFFEACB8E), Color(0xFFB0824E), Color(0xFF4A2C12))),
                // Lapis lazuli and gold - the gold pips would swallow the usual gold held ring, so theirs is white.
                colour(Res.string.style_colour_lapis, PyramidDiceStyle("pyramid_lapis", Color(0xFF3C63B8), Color(0xFF162E6A), Color(0xFFE8C66A), heldRing = Color.White)),
            ),
            unlock = AchievementCount(76),
        ),
        StyleFamily(
            Res.string.style_family_glitch,
            listOf(
                colour(Res.string.style_colour_black, GlitchDiceStyle("glitch_black", Color(0xFF101018), Color(0xFFF4F6FF), Color(0xFF2A2A3A))),
                colour(Res.string.style_colour_white, GlitchDiceStyle("glitch_white", Color(0xFFF2F2F4), Color(0xFF15151C), Color(0xFFD0D0D8))),
            ),
            unlock = AchievementCount(35),
        ),
        StyleFamily(
            Res.string.style_family_ribbon,
            listOf(
                colour(Res.string.style_colour_white, RibbonDiceStyle("ribbon_white", Color(0xFFFCFBF7), Color(0xFFE4E1D8), RibbonColours)),
                colour(Res.string.style_colour_black, RibbonDiceStyle("ribbon_black", Color(0xFF2E2E33), Color(0xFF161618), RibbonColours)),
            ),
            unlock = AchievementCount(37),
        ),
        StyleFamily(
            Res.string.style_family_neon,
            listOf(colour(Res.string.style_colour_night, NeonDiceStyle("neon_night", Color(0xFF141418), NeonColours))),
            unlock = AchievementCount(40),
        ),
        StyleFamily(
            Res.string.style_family_glitter,
            listOf(colour(Res.string.style_colour_mixed, GlitterDiceStyle("glitter_mixed", GlitterColours, Color(0xFFFFFBF2)))),
            unlock = AchievementCount(46),
        ),
    ),
)

/**
 * The Classic cup in Gold - the default cup, and the one the launcher icon draws as a silhouette
 * (the same gold body, dark mouth, lighter rim and navy band - `ic_launcher_foreground.xml`), so a
 * new player's first cup is the one on the icon.
 */
val ClassicGoldDiceCupStyle: DiceCupStyle = CasinoDiceCupStyle(
    "casino_gold",
    CupPalette(dark = Color(0xFF7A5A26), light = Color(0xFFEBC77F), mid = CupRimGold, accent = Color(0xFFE6C278), interior = DicePipColor),
    band = FeltNavyBottom,
)

object DiceCupStyles : StyleCatalog<DiceCupStyle>(
    Res.string.style_noun_dice_cup,
    listOf(
        // The casino shaker, first so it's the default - "Classic", like every category's default.
        // Its ids still say "casino": they're saved picks, so they stay put if the default moves.
        StyleFamily(
            Res.string.style_family_classic,
            listOf(
                StyleColour(Res.string.style_colour_gold, CupRimGold, ClassicGoldDiceCupStyle),
                cup(Res.string.style_colour_black, ::CasinoDiceCupStyle, "casino_black", 0xFF0F0F10, 0xFF4A4A4E, 0xFF26262A, 0xFFD4AF37, 0xFF050505),
                cup(Res.string.style_colour_green, ::CasinoDiceCupStyle, "casino_green", 0xFF0B2A12, 0xFF2F7A45, 0xFF1B5227, 0xFFD4AF37, 0xFF04120A),
            ),
        ),
        StyleFamily(
            Res.string.style_family_faceted,
            listOf(
                StyleColour(Res.string.style_colour_green, FacetedCupLitFace, FacetedDiceCupStyle),
                StyleColour(Res.string.style_colour_black, Color(0xFF3A3A3F), BlackFacetedDiceCupStyle),
                StyleColour(Res.string.style_colour_red, FireCupLitFace, FireDiceCupStyle),
            ),
            unlock = AchievementCount(26),
        ),
        // Things for holding things. The wooden barrel was once a style of its own, and its unlock
        // count is the family's, so no one loses it.
        StyleFamily(
            Res.string.style_family_containers,
            listOf(
                StyleColour(Res.string.style_colour_barrel, BarrelWood, BarrelDiceCupStyle),
                // Glossy blue enamel, the hoops and rims a shade darker.
                cup(Res.string.style_colour_oil_drum, ::OilDrumDiceCupStyle, "oil_drum_blue", 0xFF0A1250, 0xFF4462D2, 0xFF1A2C9E, 0xFF13217C, 0xFF070D3A),
                // Rust-red steel with bare dark-grey fittings.
                cup(Res.string.style_colour_shipping, ::ShippingContainerDiceCupStyle, "shipping_container_red", 0xFF5E1A12, 0xFFD4604A, 0xFFA83A28, 0xFF3A3A3C, 0xFF1A1A1A),
            ),
            unlock = AchievementCount(57),
        ),
        StyleFamily(
            Res.string.style_family_leather,
            listOf(
                cup(Res.string.style_colour_tan, ::LeatherDiceCupStyle, "leather_tan", 0xFF5C3A1C, 0xFFB98553, 0xFF8A5A2E, 0xFFF0DDB8, 0xFF1E1209),
                cup(Res.string.style_colour_black, ::LeatherDiceCupStyle, "leather_black", 0xFF111111, 0xFF4A4A4A, 0xFF262626, 0xFFBDBDBD, 0xFF050505),
                cup(Res.string.style_colour_oxblood, ::LeatherDiceCupStyle, "leather_oxblood", 0xFF3A0A0D, 0xFF92323A, 0xFF641A20, 0xFFE8C9A0, 0xFF160405),
            ),
            unlock = AchievementCount(65),
        ),
        StyleFamily(
            Res.string.style_family_glass,
            listOf(
                colour(Res.string.style_colour_clear, GlassDiceCupStyle("glass_clear", Color(0xFFD6ECF7), Color(0xFF8FD3F4))),
                colour(Res.string.style_colour_blue, GlassDiceCupStyle("glass_blue", Color(0xFF5AA8E8), Color(0xFF2F7FE0))),
                colour(Res.string.style_colour_amber, GlassDiceCupStyle("glass_amber", Color(0xFFE8C98A), Color(0xFFE8A030))),
            ),
            unlock = AchievementCount(3),
        ),
        StyleFamily(
            Res.string.style_family_tankard,
            listOf(
                cup(Res.string.style_colour_pewter, ::TankardDiceCupStyle, "tankard_pewter", 0xFF4E555A, 0xFFC9D0D4, 0xFF8C959B, 0xFF5E676D, 0xFF1C2023),
                cup(Res.string.style_colour_copper, ::TankardDiceCupStyle, "tankard_copper", 0xFF6B3417, 0xFFE0A07A, 0xFFB8683D, 0xFF7A3C1B, 0xFF2A1308),
            ),
            unlock = AchievementCount(61),
        ),
        StyleFamily(
            Res.string.style_family_top_hat,
            listOf(
                cup(Res.string.style_colour_black, ::TopHatDiceCupStyle, "top_hat_black", 0xFF0B0B0C, 0xFF3C3C40, 0xFF1E1E21, 0xFFB71C1C, 0xFF030303),
                cup(Res.string.style_colour_grey, ::TopHatDiceCupStyle, "top_hat_grey", 0xFF3A3A3D, 0xFF9A9AA0, 0xFF6A6A70, 0xFF1A1A1C, 0xFF121214),
                // Secret: only offered once The Magician's Secret is earned. The black hat, with its rabbit always out.
                cup(
                    Res.string.style_colour_rabbit,
                    { id, palette -> TopHatDiceCupStyle(id, palette, rabbitAlwaysOut = true) },
                    "top_hat_rabbit",
                    0xFF0B0B0C, 0xFF3C3C40, 0xFF1E1E21, 0xFFB71C1C, 0xFF030303,
                ).copy(swatch = RabbitFur, secretAchievement = Achievement.MAGICIANS_SECRET),
            ),
            unlock = AchievementCount(14),
        ),
        // Secret: not on the Styles screen at all until Shaken, Not Tapped is earned.
        StyleFamily(
            Res.string.style_family_martini,
            listOf(colour(Res.string.style_colour_classic, MartiniDiceCupStyle("martini"))),
            unlock = StyleUnlock.SpecificAchievement(Achievement.SHAKEN_NOT_TAPPED),
        ),
        StyleFamily(
            Res.string.style_family_takeaway,
            listOf(
                cup(Res.string.style_colour_white, ::TakeawayDiceCupStyle, "takeaway_white", 0xFFBDB6AA, 0xFFFFFFFF, 0xFFECE7DE, 0xFFA87A4E, 0xFF2A1A10),
                cup(Res.string.style_colour_black, ::TakeawayDiceCupStyle, "takeaway_black", 0xFF111111, 0xFF4A4A4A, 0xFF262626, 0xFFC08A55, 0xFF2A1A10),
            ),
            unlock = AchievementCount(44),
        ),
        StyleFamily(
            Res.string.style_family_flowerpot,
            flowerpotColours(),
            unlock = AchievementCount(18),
        ),
        StyleFamily(
            Res.string.style_family_cauldron,
            listOf(
                cauldron(Res.string.style_colour_green, "cauldron_green", 0xFF5BE36A, 0xFF1E7A2B),
                cauldron(Res.string.style_colour_purple, "cauldron_purple", 0xFFB06CF0, 0xFF5A2A8A),
            ),
            unlock = AchievementCount(34),
        ),
        StyleFamily(
            Res.string.style_family_treasure,
            listOf(
                colour(
                    Res.string.style_colour_oak,
                    TreasureChestDiceCupStyle(
                        "treasure_chest_oak",
                        ChestPalette(
                            woodLight = Color(0xFF9A5B2E), woodDark = Color(0xFF5E3317), seam = Color(0xFF3E200D),
                            goldLight = Color(0xFFFFE08A), gold = Color(0xFFD4A437), goldDark = Color(0xFF8C6414),
                            interior = Color(0xFF1E0F05), lining = Color(0xFF7A1424),
                        ),
                    ),
                ),
            ),
            unlock = AchievementCount(53),
        ),
        StyleFamily(
            Res.string.style_family_beaker,
            listOf(
                colour(Res.string.style_colour_blue, BeakerDiceCupStyle("beaker_blue", Color(0xFFD6ECF7), Color(0xFF4FC3F7))),
                colour(Res.string.style_colour_green, BeakerDiceCupStyle("beaker_green", Color(0xFFD6ECF7), Color(0xFF7CE08A))),
            ),
            unlock = AchievementCount(67),
        ),
        StyleFamily(
            Res.string.style_family_urn,
            listOf(
                colour(
                    Res.string.style_colour_terracotta,
                    UrnDiceCupStyle("urn_terracotta", UrnPalette(Color(0xFFC4673A), Color(0xFFE8956A), Color(0xFF6E2E14), Color(0xFF1E1410), Color(0xFF1A0D07))),
                ),
                colour(
                    Res.string.style_colour_bronze,
                    UrnDiceCupStyle("urn_bronze", UrnPalette(Color(0xFF9A6B32), Color(0xFFE0B070), Color(0xFF4A3010), Color(0xFF2E6B5A), Color(0xFF140C04))),
                ),
            ),
            unlock = AchievementCount(17),
        ),
        StyleFamily(
            Res.string.style_family_volcano,
            listOf(
                colour(
                    Res.string.style_colour_basalt,
                    VolcanoDiceCupStyle(
                        "volcano_basalt",
                        VolcanoPalette(
                            rockLight = Color(0xFF8A7A6E), rock = Color(0xFF5A4C44), rockDark = Color(0xFF2A221E),
                            lava = Lava(Color(0xFF2A0E04), Color(0xFFB02A00), Color(0xFFFF6A00), Color(0xFFFFF2A0)),
                            ash = Color(0xFF4A4644),
                        ),
                    ),
                ),
            ),
            unlock = AchievementCount(55),
        ),
        StyleFamily(
            Res.string.style_family_picnic,
            listOf(
                colour(
                    Res.string.style_colour_wicker,
                    PicnicBasketDiceCupStyle(
                        "picnic_wicker",
                        BasketPalette(
                            wickerLight = Color(0xFFE8C88A), wicker = Color(0xFFC8985A), wickerDark = Color(0xFF7A5428), gap = Color(0xFF4A2E14),
                            cloth = Color(0xFFF7F2EA), check = Color(0xFFD0282E), leather = Color(0xFF6A3A1E),
                        ),
                    ),
                ),
            ),
            unlock = AchievementCount(42),
        ),
        StyleFamily(
            Res.string.style_family_chicken,
            listOf(
                colour(
                    Res.string.style_colour_white,
                    ChickenDiceCupStyle(
                        "chicken_white",
                        HenPalette(feather = Color(0xFFF7F4EE), light = Color.White, dark = Color(0xFFB9B0A2), tail = Color(0xFFE6E0D4)),
                    ),
                ),
                colour(
                    Res.string.style_colour_brown,
                    ChickenDiceCupStyle(
                        "chicken_brown",
                        HenPalette(feather = Color(0xFFB5652B), light = Color(0xFFE0955A), dark = Color(0xFF6E3412), tail = Color(0xFF3A2A1E)),
                    ),
                ),
            ),
            unlock = AchievementCount(32),
        ),
        StyleFamily(
            Res.string.style_family_glitter,
            listOf(
                colour(Res.string.style_colour_gold, GlitterDiceCupStyle("glitter_gold", Color(0xFFD9A030), Color(0xFFFFE08A))),
                colour(Res.string.style_colour_pink, GlitterDiceCupStyle("glitter_pink", Color(0xFFE0458F), Color(0xFFF2F2F6))),
            ),
            unlock = AchievementCount(71),
        ),
        StyleFamily(
            Res.string.style_family_neon,
            listOf(
                colour(
                    Res.string.style_colour_pink_and_blue,
                    NeonDiceCupStyle(
                        "neon_pink_blue",
                        CupPalette(Color(0xFF050506), Color(0xFF3A3A40), Color(0xFF1A1A1E), Color(0xFF3A3A40), Color(0xFF020203)),
                        upper = Color(0xFFFF3FA4),
                        lower = Color(0xFF3FE6FF),
                    ),
                ),
            ),
            unlock = AchievementCount(73),
        ),
        StyleFamily(
            Res.string.style_family_gift_box,
            listOf(
                colour(
                    Res.string.style_colour_red,
                    GiftBoxDiceCupStyle("gift_box_red", CupPalette(Color(0xFF7A1418), Color(0xFFE85A5E), Color(0xFFC8282E), Color(0xFFFFFFFF), Color(0xFF2A0608)), Color(0xFFF2C14E)),
                ),
                colour(
                    Res.string.style_colour_blue,
                    GiftBoxDiceCupStyle("gift_box_blue", CupPalette(Color(0xFF173A70), Color(0xFF5A8AD8), Color(0xFF2C5CA8), Color(0xFFBFD6F7), Color(0xFF081428)), Color(0xFFE8E8EE)),
                ),
            ),
            unlock = AchievementCount(74),
        ),
    ),
)

object TableBackgrounds : StyleCatalog<TableBackground>(
    Res.string.style_noun_background,
    listOf(
        StyleFamily(
            Res.string.style_family_classic,
            listOf(
                StyleColour(Res.string.style_colour_navy, FeltNavyTop, MidnightFeltBackground),
                StyleColour(Res.string.style_colour_red, FireBackgroundTop, FireTableBackground),
                StyleColour(Res.string.style_colour_brown, BarrelBackgroundTop, BarrelTableBackground),
            ),
        ),
        StyleFamily(
            Res.string.style_family_spotlight,
            listOf(
                background(Res.string.style_colour_purple, ::SpotlightBackground, "spotlight_purple", 0xFF5A3290, 0xFF1A0A33),
                background(Res.string.style_colour_navy, ::SpotlightBackground, "spotlight_navy", 0xFF2A5590, 0xFF0A1A33),
                background(Res.string.style_colour_green, ::SpotlightBackground, "spotlight_green", 0xFF2E7A48, 0xFF0A2A16),
            ),
            unlock = AchievementCount(10),
        ),
        StyleFamily(
            Res.string.style_family_pinstripe,
            listOf(
                background(Res.string.style_colour_charcoal, ::PinstripeBackground, "pinstripe_charcoal", 0xFF2E3136, 0xFF1C1E22),
                background(Res.string.style_colour_navy, ::PinstripeBackground, "pinstripe_navy", 0xFF1E2E4C, 0xFF0F1A30),
            ),
            unlock = AchievementCount(41),
        ),
        StyleFamily(
            Res.string.style_family_planks,
            listOf(
                background(Res.string.style_colour_oak, ::PlanksBackground, "planks_oak", 0xFF7A5534, 0xFF5A3C22),
                background(Res.string.style_colour_walnut, ::PlanksBackground, "planks_walnut", 0xFF4A3322, 0xFF2C1D12),
            ),
            unlock = AchievementCount(5),
        ),
        StyleFamily(
            Res.string.style_family_gingham,
            listOf(
                background(Res.string.style_colour_red, ::GinghamBackground, "gingham_red", 0xFF8E2A26, 0xFF6A1C19),
                background(Res.string.style_colour_blue, ::GinghamBackground, "gingham_blue", 0xFF264C78, 0xFF183352),
                background(Res.string.style_colour_green, ::GinghamBackground, "gingham_green", 0xFF33693D, 0xFF214A28),
            ),
            unlock = AchievementCount(30),
        ),
        StyleFamily(
            Res.string.style_family_starry,
            listOf(
                background(Res.string.style_colour_midnight, ::StarryBackground, "starry_midnight", 0xFF12224A, 0xFF060C22),
            ),
            unlock = AchievementCount(27),
        ),
        StyleFamily(
            Res.string.style_family_honeycomb,
            listOf(
                background(Res.string.style_colour_charcoal, ::HoneycombBackground, "honeycomb_charcoal", 0xFF2A2D33, 0xFF16181C),
                background(Res.string.style_colour_indigo, ::HoneycombBackground, "honeycomb_indigo", 0xFF262A5A, 0xFF12142E),
                // Real comb, wax and honey, dimmed well back - to go with the Bestagon dice and the Honeycomb mat.
                StyleColour(
                    Res.string.style_colour_honey,
                    Color(0xFFD9A040),
                    HoneycombCombBackground("honeycomb_honey", BackgroundPalette(Color(0xFF3A2406), Color(0xFF1E1203), Color(0xFFD9A040))),
                ),
            ),
            unlock = AchievementCount(6),
        ),
        StyleFamily(
            Res.string.style_family_sunburst,
            listOf(
                background(Res.string.style_colour_crimson, ::SunburstBackground, "sunburst_crimson", 0xFF6A1218, 0xFF2E0508),
                background(Res.string.style_colour_amber, ::SunburstBackground, "sunburst_amber", 0xFF7A4A0E, 0xFF331E04),
            ),
            unlock = AchievementCount(39),
        ),
        StyleFamily(
            Res.string.style_family_brick,
            listOf(StyleColour(Res.string.style_colour_neon, Color(0xFF2A1A1C), BrickWallBackground("brick_neon", BackgroundPalette(Color(0xFF2A1A1C), Color(0xFF140C0E), Color(0xFFFF3FA4))))),
            unlock = AchievementCount(62),
        ),
        StyleFamily(
            Res.string.style_family_glitch,
            listOf(StyleColour(Res.string.style_colour_screen, Color(0xFF14141E), GlitchBackground("glitch_screen", BackgroundPalette(Color(0xFF14141E), Color(0xFF07070C), Color(0xFF9FB4FF))))),
            unlock = AchievementCount(63),
        ),
        StyleFamily(
            Res.string.style_family_desert,
            listOf(StyleColour(Res.string.style_colour_night, Color(0xFF4A3020), DesertBackground("desert_night", BackgroundPalette(Color(0xFF1C1838), Color(0xFF4A3020), Color(0xFFF2C88A))))),
            unlock = AchievementCount(64),
        ),
        StyleFamily(
            Res.string.style_family_gift_wrap,
            listOf(
                background(Res.string.style_colour_green, ::GiftWrapBackground, "gift_wrap_green", 0xFF1E5A34, 0xFF0E3A1E),
                background(Res.string.style_colour_red, ::GiftWrapBackground, "gift_wrap_red", 0xFF7A1A20, 0xFF4E0E12),
            ),
            unlock = AchievementCount(66),
        ),
        StyleFamily(
            Res.string.style_family_glitter,
            listOf(
                glitterBackground(Res.string.style_colour_gold, "glitter_gold", 0xFF241C2C, 0xFF0E0A12, 0xFFE8C66A),
                glitterBackground(Res.string.style_colour_silver, "glitter_silver", 0xFF2A2D32, 0xFF111316, 0xFFE8ECF2),
                glitterBackground(Res.string.style_colour_rose_gold, "glitter_rose_gold", 0xFF3A2024, 0xFF1A0C0E, 0xFFF6C8B8),
                glitterBackground(Res.string.style_colour_pink, "glitter_pink", 0xFF4E1636, 0xFF22081A, 0xFFFFB8E0),
                glitterBackground(Res.string.style_colour_purple, "glitter_purple", 0xFF2E1650, 0xFF12082A, 0xFFD8B8FF),
                glitterBackground(Res.string.style_colour_blue, "glitter_blue", 0xFF122650, 0xFF060E26, 0xFFB8DCFF),
                glitterBackground(Res.string.style_colour_emerald, "glitter_emerald", 0xFF0C3A2A, 0xFF041A12, 0xFFA8F6D0),
            ),
            unlock = AchievementCount(68),
        ),
        // Secret: not on the Styles screen at all until Not Those Dice! is earned.
        StyleFamily(
            Res.string.style_family_floating_dice,
            listOf(StyleColour(Res.string.style_colour_menu, SurfaceContainerHigh, FloatingDiceBackground)),
            unlock = StyleUnlock.SpecificAchievement(Achievement.NOT_THOSE_DICE),
        ),
    ),
)

object DiceMats : StyleCatalog<DiceMat>(
    Res.string.style_noun_mat,
    listOf(
        StyleFamily(
            Res.string.style_family_classic,
            listOf(
                StyleColour(Res.string.style_colour_blue, TrayBlueTop, TrayBlueMat),
                StyleColour(Res.string.style_colour_red, FireTrayTop, FireDiceMat),
            ),
        ),
        StyleFamily(
            Res.string.style_family_wood,
            listOf(
                StyleColour(Res.string.style_colour_brown, BarrelTrayTop, BarrelDiceMat),
            ),
            unlock = AchievementCount(16),
        ),
        StyleFamily(
            Res.string.style_family_leather,
            listOf(
                mat(Res.string.style_colour_tan, ::LeatherDiceMat, "leather_tan", 0xFF9A6A3C, 0xFF6B4424, 0xFF3E2612, 0xFF2A190B, 0xFFE0C08E, 0xFFE8D2A8),
                mat(Res.string.style_colour_oxblood, ::LeatherDiceMat, "leather_oxblood", 0xFF7A2328, 0xFF4E1216, 0xFF2E080B, 0xFF1E0507, 0xFFE0A89A, 0xFFE8C9A0),
                mat(Res.string.style_colour_black, ::LeatherDiceMat, "leather_black", 0xFF3A3A3A, 0xFF1C1C1C, 0xFF111111, 0xFF0A0A0A, 0xFF9E9E9E, 0xFFBDBDBD),
            ),
            unlock = AchievementCount(83),
        ),
        StyleFamily(
            Res.string.style_family_casino,
            listOf(
                mat(Res.string.style_colour_green, ::CasinoDiceMat, "casino_green", 0xFF1E6B3A, 0xFF0E3F22, 0xFF0A2E18, 0xFF061F10, 0xFFE0C45A, 0xFFD4AF37),
                mat(Res.string.style_colour_purple, ::CasinoDiceMat, "casino_purple", 0xFF4A2266, 0xFF2A1040, 0xFF1E0A30, 0xFF12061E, 0xFFE0C45A, 0xFFD4AF37),
            ),
            unlock = AchievementCount(11),
        ),
        StyleFamily(
            Res.string.style_family_gingham,
            listOf(
                mat(Res.string.style_colour_red, ::GinghamDiceMat, "gingham_red", 0xFFA8322D, 0xFF7E211D, 0xFF4A1310, 0xFF330B09, 0xFFF2B8B0, 0xFFFFFFFF),
                mat(Res.string.style_colour_blue, ::GinghamDiceMat, "gingham_blue", 0xFF2F5C8F, 0xFF1E3E63, 0xFF122640, 0xFF0B182B, 0xFFB8D0F0, 0xFFFFFFFF),
                mat(Res.string.style_colour_green, ::GinghamDiceMat, "gingham_green", 0xFF3E7D4A, 0xFF27562F, 0xFF16331C, 0xFF0E2312, 0xFFBFE3C6, 0xFFFFFFFF),
            ),
            unlock = AchievementCount(20),
        ),
        StyleFamily(
            Res.string.style_family_starry,
            listOf(
                mat(Res.string.style_colour_midnight, ::StarryDiceMat, "starry_midnight", 0xFF152550, 0xFF070E24, 0xFF0A1330, 0xFF050A1C, 0xFF8FA8E8, 0xFFFFFFFF),
            ),
            unlock = AchievementCount(69),
        ),
        StyleFamily(
            Res.string.style_family_honeycomb,
            listOf(mat(Res.string.style_colour_honey, ::HoneycombDiceMat, "honeycomb_honey", 0xFFF2A81E, 0xFF9A5A06, 0xFF5A3A0E, 0xFF3E2806, 0xFFFFD27A, 0xFFE8B850)),
            unlock = AchievementCount(60),
        ),
        StyleFamily(
            Res.string.style_family_hex_tiles,
            listOf(
                mat(Res.string.style_colour_honey, ::HexTileDiceMat, "hex_tiles_honey", 0xFFE6A53A, 0xFFB87818, 0xFF5A3A0E, 0xFF3E2806, 0xFFFFD27A, 0xFF3E2806),
                mat(Res.string.style_colour_slate, ::HexTileDiceMat, "hex_tiles_slate", 0xFF5C6B78, 0xFF3A4550, 0xFF232A31, 0xFF151A1F, 0xFFAEBBC6, 0xFF1C2228),
            ),
            unlock = AchievementCount(51),
        ),
        StyleFamily(
            Res.string.style_family_sand,
            listOf(mat(Res.string.style_colour_dunes, ::SandDiceMat, "sand_dunes", 0xFFE2C084, 0xFFC9A062, 0xFF8A6430, 0xFF6A4A20, 0xFFF7E2B5, 0xFF8A6430)),
            unlock = AchievementCount(52),
        ),
        StyleFamily(
            Res.string.style_family_circuit,
            listOf(
                mat(Res.string.style_colour_green, ::CircuitDiceMat, "circuit_green", 0xFF16503A, 0xFF0B3325, 0xFF07231A, 0xFF041710, 0xFF6BE0A6, 0xFFD9A84A),
                mat(Res.string.style_colour_black, ::CircuitDiceMat, "circuit_black", 0xFF1E2024, 0xFF111215, 0xFF0A0B0D, 0xFF050506, 0xFF6A6E78, 0xFF3FE6FF),
            ),
            unlock = AchievementCount(54),
        ),
        StyleFamily(
            Res.string.style_family_neon,
            listOf(mat(Res.string.style_colour_pink_and_blue, ::NeonDiceMat, "neon_pink_blue", 0xFF2A1C24, 0xFF140C12, 0xFF0E080C, 0xFF060305, 0xFF3FE6FF, 0xFFFF3FA4)),
            unlock = AchievementCount(56),
        ),
        StyleFamily(
            Res.string.style_family_gift_wrap,
            listOf(
                mat(Res.string.style_colour_red, ::GiftWrapDiceMat, "gift_wrap_red", 0xFFB8282E, 0xFF8E1A20, 0xFF5A0E12, 0xFF3E080B, 0xFFF2C14E, 0xFFFFFFFF),
                mat(Res.string.style_colour_kraft, ::GiftWrapDiceMat, "gift_wrap_kraft", 0xFFC49A6C, 0xFFA77D50, 0xFF6A4A2A, 0xFF4A321C, 0xFFD32F2F, 0xFFF7EDE0),
            ),
            unlock = AchievementCount(58),
        ),
        StyleFamily(
            Res.string.style_family_glitter,
            listOf(
                glitterMat(Res.string.style_colour_gold, "glitter_gold", 0xFF2A2230, 0xFF141018, 0xFFE8C66A, 0xFFF2D27A),
                glitterMat(Res.string.style_colour_silver, "glitter_silver", 0xFF34383E, 0xFF1A1C20, 0xFFD8DDE3, 0xFFE8ECF2),
                glitterMat(Res.string.style_colour_rose_gold, "glitter_rose_gold", 0xFF4A2A2E, 0xFF261417, 0xFFF0B8A8, 0xFFF6C8B8),
                glitterMat(Res.string.style_colour_pink, "glitter_pink", 0xFF6A1E48, 0xFF3E0E2A, 0xFFFF9AD0, 0xFFFFB8E0),
                glitterMat(Res.string.style_colour_purple, "glitter_purple", 0xFF3E1E6A, 0xFF1E0E3A, 0xFFC8A0FF, 0xFFD8B8FF),
                glitterMat(Res.string.style_colour_blue, "glitter_blue", 0xFF16306A, 0xFF0A1838, 0xFF9AC8FF, 0xFFB8DCFF),
                glitterMat(Res.string.style_colour_emerald, "glitter_emerald", 0xFF0E4A34, 0xFF06261A, 0xFF8AF0C0, 0xFFA8F6D0),
            ),
            unlock = AchievementCount(59),
        ),
    ),
)

/**
 * The frame round the player whose turn it is. Every one is drawn in the player's own colour, so a
 * family's "colours" here are its variants - different shapes, not different colours - each shown on
 * the Styles screen in player 1's default colour.
 */
object ScoreFrames : StyleCatalog<ScoreFrame>(
    Res.string.style_noun_frame,
    listOf(
        StyleFamily(Res.string.style_family_classic, listOf(frame(Res.string.style_colour_ring, ScoreFrameArt.classic))),
        StyleFamily(
            Res.string.style_family_floral,
            listOf(frame(Res.string.style_colour_rose, ScoreFrameArt.rose), frame(Res.string.style_colour_daisy, ScoreFrameArt.daisy), frame(Res.string.style_colour_vine, ScoreFrameArt.vine)),
            unlock = AchievementCount(4),
        ),
        StyleFamily(
            Res.string.style_family_art_deco,
            listOf(frame(Res.string.style_colour_fan, ScoreFrameArt.decoFan), frame(Res.string.style_colour_stepped, ScoreFrameArt.decoStepped)),
            unlock = AchievementCount(8),
        ),
        StyleFamily(
            Res.string.style_family_tribal,
            listOf(frame(Res.string.style_colour_swirl, ScoreFrameArt.tribalSwirl), frame(Res.string.style_colour_flame, ScoreFrameArt.tribalFlame)),
            unlock = AchievementCount(13),
        ),
        StyleFamily(
            Res.string.style_family_wreath,
            listOf(frame(Res.string.style_colour_laurel, ScoreFrameArt.laurel), frame(Res.string.style_colour_olive, ScoreFrameArt.olive)),
            unlock = AchievementCount(22),
        ),
        StyleFamily(
            Res.string.style_family_greek,
            listOf(frame(Res.string.style_colour_key, ScoreFrameArt.greekKey), frame(Res.string.style_colour_waves, ScoreFrameArt.greekWaves)),
            unlock = AchievementCount(24),
        ),
        StyleFamily(Res.string.style_family_celtic, listOf(frame(Res.string.style_colour_knot, ScoreFrameArt.celticKnot)), unlock = AchievementCount(70)),
        StyleFamily(
            Res.string.style_family_neon,
            listOf(frame(Res.string.style_colour_single, ScoreFrameArt.neon), frame(Res.string.style_colour_double, ScoreFrameArt.neonDouble)),
            unlock = AchievementCount(72),
        ),
        StyleFamily(Res.string.style_family_pixel, listOf(frame(Res.string.style_colour_retro, ScoreFrameArt.pixel)), unlock = AchievementCount(77)),
    ),
    variantNoun = Res.string.style_variant_design,
)

/** A frame's variant: its swatch is the colour its tile shows it in, as every variant is drawn in the player's colour. */
private fun frame(name: StringResource, style: ScoreFrame): StyleColour<ScoreFrame> = StyleColour(name, ScoreFramePreviewColour, style)

// The Egg dice's shells - each also the other's pips.
private val EggWhiteShell = Color(0xFFFBF8F1)
private val EggBrownShell = Color(0xFFD9A27A)

// The Mahjong dice's tiles: an ivory face over a jade back.
private val MahjongFace = Color(0xFFFBF7EC)
private val MahjongFaceShade = Color(0xFFE6DFCB)
private val MahjongBack = Color(0xFF2E7D4F)

// Shorthands for the entries above. A colour's swatch - its dot on the Styles screen - is the most
// representative colour of its palette.

private fun <T> colour(name: StringResource, style: T): StyleColour<T> where T : TableArt, T : Swatched = StyleColour(name, style.swatch, style)

private fun <T : DiceCupStyle> cup(
    name: StringResource,
    create: (String, CupPalette) -> T,
    id: String,
    dark: Long,
    light: Long,
    mid: Long,
    accent: Long,
    interior: Long,
): StyleColour<DiceCupStyle> =
    StyleColour(name, Color(mid), create(id, CupPalette(Color(dark), Color(light), Color(mid), Color(accent), Color(interior))))

private val FlowerpotTerracotta = CupPalette(
    Color(0xFF7A3418), Color(0xFFE08A5C), Color(0xFFC0643A), Color(0xFFC0643A), Color(0xFF2E1E14),
)

/**
 * The Flowerpot's colours: the pot that grows its plant through a game (the one everyone has), then
 * one pot for each stage of the plant, held there - secret, all of them, until Greenfingers is
 * earned. Each shows the plant's own colour on the Styles screen, since the pot is the same. The
 * full bloom's id is the old Sunflower cup's, so a saved pick of it still resolves.
 */
private fun flowerpotColours(): List<StyleColour<DiceCupStyle>> {
    fun stage(name: StringResource, id: String, swatch: Color, stage: Int) =
        StyleColour(name, swatch, FlowerpotDiceCupStyle(id, FlowerpotTerracotta, fixedStage = stage), Achievement.GREENFINGERS)
    return listOf(
        StyleColour(Res.string.style_colour_terracotta, FlowerpotTerracotta.mid, FlowerpotDiceCupStyle("flowerpot_terracotta", FlowerpotTerracotta)),
        stage(Res.string.style_colour_seedling, "flowerpot_seedling", FlowerpotLeaf, 1),
        stage(Res.string.style_colour_bud, "flowerpot_bud", FlowerpotStem, 2),
        stage(Res.string.style_colour_opening, "flowerpot_opening", SunflowerPetalShade, 3),
        stage(Res.string.style_colour_sunflower, "sunflower_terracotta", SunflowerPetal, FLOWERPOT_FULL_BLOOM),
    )
}

// Cauldrons share their iron and differ only in their brew, which is also the colour the Styles
// screen shows for them.
private fun cauldron(name: StringResource, id: String, brew: Long, deepBrew: Long): StyleColour<DiceCupStyle> =
    StyleColour(
        name,
        Color(brew),
        CauldronDiceCupStyle(id, CupPalette(Color(0xFF151618), Color(0xFF5A5E63), Color(0xFF2C2F33), Color(brew), Color(deepBrew))),
    )

private fun matPalette(top: Long, bottom: Long, slotTop: Long, slotBottom: Long, slotBorder: Long, detail: Long) =
    MatPalette(Color(top), Color(bottom), Color(slotTop), Color(slotBottom), Color(slotBorder), Color(detail))

private fun mat(
    name: StringResource,
    create: (String, MatPalette) -> DiceMat,
    id: String,
    top: Long,
    bottom: Long,
    slotTop: Long,
    slotBottom: Long,
    slotBorder: Long,
    detail: Long,
): StyleColour<DiceMat> = StyleColour(name, Color(top), create(id, matPalette(top, bottom, slotTop, slotBottom, slotBorder, detail)))

/** A Glitter mat: [flakes] of glitter over a [top]-to-[bottom] tray, its slots sunk darker and ringed in [border]. */
private fun glitterMat(name: StringResource, id: String, top: Long, bottom: Long, border: Long, flakes: Long): StyleColour<DiceMat> =
    StyleColour(
        name,
        Color(flakes),
        GlitterDiceMat(id, MatPalette(Color(top), Color(bottom), lerp(Color(bottom), Color.Black, 0.45f), lerp(Color(bottom), Color.Black, 0.7f), Color(border), Color(flakes))),
    )

/** A Glitter background: [flakes] of glitter over a [top]-to-[bottom] gradient. */
private fun glitterBackground(name: StringResource, id: String, top: Long, bottom: Long, flakes: Long): StyleColour<TableBackground> =
    StyleColour(name, Color(flakes), GlitterBackground(id, BackgroundPalette(Color(top), Color(bottom), Color(flakes))))

private fun background(
    name: StringResource,
    create: (String, BackgroundPalette) -> TableBackground,
    id: String,
    top: Long,
    bottom: Long,
): StyleColour<TableBackground> = StyleColour(name, Color(top), create(id, BackgroundPalette(Color(top), Color(bottom))))
