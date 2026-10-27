package net.zodac.dicefive.game.style

import net.zodac.dicefive.game.style.StyleUnlock.AchievementCount
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.style_colour_amber
import net.zodac.dicefive.resources.style_colour_arabic
import net.zodac.dicefive.resources.style_colour_arabic_green
import net.zodac.dicefive.resources.style_colour_barrel
import net.zodac.dicefive.resources.style_colour_basalt
import net.zodac.dicefive.resources.style_colour_baubles
import net.zodac.dicefive.resources.style_colour_beaker_blue
import net.zodac.dicefive.resources.style_colour_beaker_green
import net.zodac.dicefive.resources.style_colour_black
import net.zodac.dicefive.resources.style_colour_black_marble
import net.zodac.dicefive.resources.style_colour_blue
import net.zodac.dicefive.resources.style_colour_blueprint
import net.zodac.dicefive.resources.style_colour_bronze
import net.zodac.dicefive.resources.style_colour_brown
import net.zodac.dicefive.resources.style_colour_bud
import net.zodac.dicefive.resources.style_colour_bulbs
import net.zodac.dicefive.resources.style_colour_chain
import net.zodac.dicefive.resources.style_colour_charcoal
import net.zodac.dicefive.resources.style_colour_chinese
import net.zodac.dicefive.resources.style_colour_chocolate
import net.zodac.dicefive.resources.style_colour_classic
import net.zodac.dicefive.resources.style_colour_clear
import net.zodac.dicefive.resources.style_colour_clownfish
import net.zodac.dicefive.resources.style_colour_constellation
import net.zodac.dicefive.resources.style_colour_crimson
import net.zodac.dicefive.resources.style_colour_cyan
import net.zodac.dicefive.resources.style_colour_daisy
import net.zodac.dicefive.resources.style_colour_deep
import net.zodac.dicefive.resources.style_colour_diamonds
import net.zodac.dicefive.resources.style_colour_digits
import net.zodac.dicefive.resources.style_colour_double
import net.zodac.dicefive.resources.style_colour_emerald
import net.zodac.dicefive.resources.style_colour_fan
import net.zodac.dicefive.resources.style_colour_flame
import net.zodac.dicefive.resources.style_colour_forestry
import net.zodac.dicefive.resources.style_colour_gift
import net.zodac.dicefive.resources.style_colour_gold
import net.zodac.dicefive.resources.style_colour_granite
import net.zodac.dicefive.resources.style_colour_green
import net.zodac.dicefive.resources.style_colour_green_and_purple
import net.zodac.dicefive.resources.style_colour_green_mono
import net.zodac.dicefive.resources.style_colour_grey
import net.zodac.dicefive.resources.style_colour_halo_rainbow
import net.zodac.dicefive.resources.style_colour_halo_wave
import net.zodac.dicefive.resources.style_colour_honey
import net.zodac.dicefive.resources.style_colour_honeycomb
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
import net.zodac.dicefive.resources.style_colour_lagoon
import net.zodac.dicefive.resources.style_colour_lantern_rainbow
import net.zodac.dicefive.resources.style_colour_lantern_wave
import net.zodac.dicefive.resources.style_colour_latin_american
import net.zodac.dicefive.resources.style_colour_laurel
import net.zodac.dicefive.resources.style_colour_lava
import net.zodac.dicefive.resources.style_colour_limestone
import net.zodac.dicefive.resources.style_colour_manzu
import net.zodac.dicefive.resources.style_colour_maple
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
import net.zodac.dicefive.resources.style_colour_orange_and_teal
import net.zodac.dicefive.resources.style_colour_oxblood
import net.zodac.dicefive.resources.style_colour_pencil
import net.zodac.dicefive.resources.style_colour_pink
import net.zodac.dicefive.resources.style_colour_pink_and_blue
import net.zodac.dicefive.resources.style_colour_pinzu
import net.zodac.dicefive.resources.style_colour_poker
import net.zodac.dicefive.resources.style_colour_purple
import net.zodac.dicefive.resources.style_colour_purple_script
import net.zodac.dicefive.resources.style_colour_rabbit
import net.zodac.dicefive.resources.style_colour_rainbow
import net.zodac.dicefive.resources.style_colour_red
import net.zodac.dicefive.resources.style_colour_red_block
import net.zodac.dicefive.resources.style_colour_retro
import net.zodac.dicefive.resources.style_colour_ring
import net.zodac.dicefive.resources.style_colour_ripple
import net.zodac.dicefive.resources.style_colour_roman
import net.zodac.dicefive.resources.style_colour_roman_blue
import net.zodac.dicefive.resources.style_colour_rope
import net.zodac.dicefive.resources.style_colour_rose
import net.zodac.dicefive.resources.style_colour_rose_gold
import net.zodac.dicefive.resources.style_colour_sandstone
import net.zodac.dicefive.resources.style_colour_screen
import net.zodac.dicefive.resources.style_colour_seedling
import net.zodac.dicefive.resources.style_colour_shipping
import net.zodac.dicefive.resources.style_colour_silver
import net.zodac.dicefive.resources.style_colour_single
import net.zodac.dicefive.resources.style_colour_slate
import net.zodac.dicefive.resources.style_colour_snowcap
import net.zodac.dicefive.resources.style_colour_soil
import net.zodac.dicefive.resources.style_colour_sozu
import net.zodac.dicefive.resources.style_colour_sparkles
import net.zodac.dicefive.resources.style_colour_starlight
import net.zodac.dicefive.resources.style_colour_stars
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
import net.zodac.dicefive.resources.style_colour_white_halo_rainbow
import net.zodac.dicefive.resources.style_colour_white_halo_wave
import net.zodac.dicefive.resources.style_colour_white_lantern_rainbow
import net.zodac.dicefive.resources.style_colour_white_lantern_wave
import net.zodac.dicefive.resources.style_colour_white_marble
import net.zodac.dicefive.resources.style_colour_white_rainbow
import net.zodac.dicefive.resources.style_colour_white_wave
import net.zodac.dicefive.resources.style_colour_wicker
import net.zodac.dicefive.resources.style_colour_yellow
import net.zodac.dicefive.resources.style_family_art_deco
import net.zodac.dicefive.resources.style_family_bestagon
import net.zodac.dicefive.resources.style_family_bestagons
import net.zodac.dicefive.resources.style_family_brick
import net.zodac.dicefive.resources.style_family_cake
import net.zodac.dicefive.resources.style_family_casino
import net.zodac.dicefive.resources.style_family_cauldron
import net.zodac.dicefive.resources.style_family_celtic
import net.zodac.dicefive.resources.style_family_chicken
import net.zodac.dicefive.resources.style_family_christmas
import net.zodac.dicefive.resources.style_family_circuit
import net.zodac.dicefive.resources.style_family_classic
import net.zodac.dicefive.resources.style_family_containers
import net.zodac.dicefive.resources.style_family_d20
import net.zodac.dicefive.resources.style_family_devil
import net.zodac.dicefive.resources.style_family_egg
import net.zodac.dicefive.resources.style_family_faceted
import net.zodac.dicefive.resources.style_family_fishbowl
import net.zodac.dicefive.resources.style_family_floating_dice
import net.zodac.dicefive.resources.style_family_floral
import net.zodac.dicefive.resources.style_family_flowerpot
import net.zodac.dicefive.resources.style_family_frosted
import net.zodac.dicefive.resources.style_family_games
import net.zodac.dicefive.resources.style_family_garden
import net.zodac.dicefive.resources.style_family_gems
import net.zodac.dicefive.resources.style_family_gingham
import net.zodac.dicefive.resources.style_family_glass
import net.zodac.dicefive.resources.style_family_glitch
import net.zodac.dicefive.resources.style_family_glitter
import net.zodac.dicefive.resources.style_family_googly
import net.zodac.dicefive.resources.style_family_greek
import net.zodac.dicefive.resources.style_family_lcd
import net.zodac.dicefive.resources.style_family_leather
import net.zodac.dicefive.resources.style_family_marquee
import net.zodac.dicefive.resources.style_family_martini
import net.zodac.dicefive.resources.style_family_maths
import net.zodac.dicefive.resources.style_family_meadow
import net.zodac.dicefive.resources.style_family_metal
import net.zodac.dicefive.resources.style_family_misprint
import net.zodac.dicefive.resources.style_family_multicolour
import net.zodac.dicefive.resources.style_family_nautical
import net.zodac.dicefive.resources.style_family_neon
import net.zodac.dicefive.resources.style_family_numeral
import net.zodac.dicefive.resources.style_family_obsidian
import net.zodac.dicefive.resources.style_family_ocean
import net.zodac.dicefive.resources.style_family_picnic
import net.zodac.dicefive.resources.style_family_pinstripe
import net.zodac.dicefive.resources.style_family_pixel
import net.zodac.dicefive.resources.style_family_planks
import net.zodac.dicefive.resources.style_family_retro
import net.zodac.dicefive.resources.style_family_rgb
import net.zodac.dicefive.resources.style_family_ribbon
import net.zodac.dicefive.resources.style_family_spotlight
import net.zodac.dicefive.resources.style_family_starry
import net.zodac.dicefive.resources.style_family_stone
import net.zodac.dicefive.resources.style_family_sunburst
import net.zodac.dicefive.resources.style_family_takeaway
import net.zodac.dicefive.resources.style_family_tally
import net.zodac.dicefive.resources.style_family_text
import net.zodac.dicefive.resources.style_family_top_hat
import net.zodac.dicefive.resources.style_family_treasure
import net.zodac.dicefive.resources.style_family_tribal
import net.zodac.dicefive.resources.style_family_urn
import net.zodac.dicefive.resources.style_family_volcano
import net.zodac.dicefive.resources.style_family_wireframe
import net.zodac.dicefive.resources.style_family_wood
import net.zodac.dicefive.resources.style_family_world
import net.zodac.dicefive.resources.style_family_wreath
import net.zodac.dicefive.resources.style_noun_background
import net.zodac.dicefive.resources.style_noun_dice
import net.zodac.dicefive.resources.style_noun_dice_cup
import net.zodac.dicefive.resources.style_noun_frame
import net.zodac.dicefive.resources.style_noun_mat
import net.zodac.dicefive.resources.style_variant_design

/** The achievement count at which every style of the water set opens, in each category that has one. */
private const val WATER_SET_UNLOCK = 15

/** The dice: every family, in the Styles screen's order, and what unlocks it. Their art is `ui.style.DiceStyles`. */
object DiceStyleRegistry : StyleRegistry(
    Res.string.style_noun_dice,
    listOf(
        StyleGroup(
            Res.string.style_family_classic,
            listOf(
                StyleVariant("classic_ivory", Res.string.style_colour_ivory),
                StyleVariant("classic_red", Res.string.style_colour_red),
                StyleVariant("classic_yellow", Res.string.style_colour_yellow),
                StyleVariant("classic_blue", Res.string.style_colour_blue),
                StyleVariant("classic_oak", Res.string.style_colour_oak),
            ),
        ),
        // Leather and Wood, like the cups, mats and backgrounds of the same names, open with them.
        StyleGroup(
            Res.string.style_family_leather,
            listOf(
                StyleVariant("leather_tan", Res.string.style_colour_tan),
                StyleVariant("leather_black", Res.string.style_colour_black),
                StyleVariant("leather_oxblood", Res.string.style_colour_oxblood),
            ),
            unlock = AchievementCount(5),
        ),
        StyleGroup(
            Res.string.style_family_wood,
            listOf(
                StyleVariant("wood_oak", Res.string.style_colour_oak),
                StyleVariant("wood_walnut", Res.string.style_colour_walnut),
                StyleVariant("wood_maple", Res.string.style_colour_maple),
            ),
            unlock = AchievementCount(10),
        ),
        // The water set, with the Fishbowl cup, the Ocean mat and backgrounds and the Nautical frame.
        StyleGroup(
            Res.string.style_family_ocean,
            listOf(
                StyleVariant("ocean_lagoon", Res.string.style_colour_lagoon),
                StyleVariant("ocean_deep", Res.string.style_colour_deep),
            ),
            unlock = AchievementCount(WATER_SET_UNLOCK),
        ),
        StyleGroup(
            Res.string.style_family_metal,
            listOf(
                StyleVariant("metal_gold", Res.string.style_colour_gold),
                StyleVariant("metal_silver", Res.string.style_colour_silver),
                StyleVariant("metal_bronze", Res.string.style_colour_bronze),
            ),
            unlock = AchievementCount(20),
        ),
        StyleGroup(
            Res.string.style_family_numeral,
            listOf(
                StyleVariant("numeral_digits", Res.string.style_colour_digits),
                StyleVariant("numeral_red", Res.string.style_colour_red),
                StyleVariant("numeral_blue", Res.string.style_colour_blue),
            ),
            unlock = AchievementCount(20),
        ),
        StyleGroup(
            Res.string.style_family_world,
            listOf(
                StyleVariant("world_roman", Res.string.style_colour_roman),
                StyleVariant("world_roman_blue", Res.string.style_colour_roman_blue),
                StyleVariant("world_arabic_green", Res.string.style_colour_arabic_green),
                StyleVariant("world_arabic", Res.string.style_colour_arabic),
                StyleVariant("world_japanese", Res.string.style_colour_japanese),
                StyleVariant("world_japanese_black", Res.string.style_colour_japanese_black),
            ),
            unlock = AchievementCount(25),
        ),
        StyleGroup(
            Res.string.style_family_frosted,
            listOf(
                StyleVariant("frosted_ice", Res.string.style_colour_ice),
                StyleVariant("frosted_white", Res.string.style_colour_white),
            ),
            unlock = AchievementCount(30),
        ),
        StyleGroup(
            Res.string.style_family_text,
            listOf(
                StyleVariant("text_ivory_serif", Res.string.style_colour_ivory_serif),
                StyleVariant("text_green_mono", Res.string.style_colour_green_mono),
                StyleVariant("text_purple_script", Res.string.style_colour_purple_script),
                StyleVariant("text_red_block", Res.string.style_colour_red_block),
                StyleVariant("text_teal_plain", Res.string.style_colour_teal_plain),
            ),
            unlock = AchievementCount(30),
        ),
        StyleGroup(
            Res.string.style_family_misprint,
            listOf(
                StyleVariant("misprint_pencil", Res.string.style_colour_pencil),
                StyleVariant("misprint_blueprint", Res.string.style_colour_blueprint),
            ),
            unlock = AchievementCount(35),
        ),
        StyleGroup(
            Res.string.style_family_obsidian,
            listOf(
                StyleVariant("obsidian_lava", Res.string.style_colour_lava),
                StyleVariant("obsidian_blue", Res.string.style_colour_blue),
            ),
            unlock = AchievementCount(35),
        ),
        StyleGroup(
            Res.string.style_family_multicolour,
            listOf(
                StyleVariant("multicolour_tricolour", Res.string.style_colour_tricolour),
                StyleVariant("multicolour_rainbow", Res.string.style_colour_rainbow),
                // Secret: only offered once Luck of the Irish is earned.
                StyleVariant("multicolour_irish", Res.string.style_colour_irish, Achievement.LUCK_OF_THE_IRISH),
            ),
            unlock = AchievementCount(40),
        ),
        StyleGroup(
            Res.string.style_family_games,
            listOf(
                StyleVariant("games_poker", Res.string.style_colour_poker),
                StyleVariant("games_pinzu", Res.string.style_colour_pinzu),
                StyleVariant("games_manzu", Res.string.style_colour_manzu),
                StyleVariant("games_sozu", Res.string.style_colour_sozu),
            ),
            unlock = AchievementCount(45),
        ),
        StyleGroup(
            Res.string.style_family_retro,
            listOf(
                StyleVariant("retro_green", Res.string.style_colour_green),
                StyleVariant("retro_amber", Res.string.style_colour_amber),
                StyleVariant("retro_blue", Res.string.style_colour_blue),
                StyleVariant("retro_red", Res.string.style_colour_red),
            ),
            unlock = AchievementCount(45),
        ),
        StyleGroup(
            Res.string.style_family_tally,
            listOf(
                StyleVariant("tally_western", Res.string.style_colour_western),
                StyleVariant("tally_chinese", Res.string.style_colour_chinese),
                StyleVariant("tally_latin_american", Res.string.style_colour_latin_american),
                StyleVariant("tally_forestry", Res.string.style_colour_forestry),
            ),
            unlock = AchievementCount(50),
        ),
        StyleGroup(
            Res.string.style_family_egg,
            listOf(
                StyleVariant("egg_white", Res.string.style_colour_white),
                StyleVariant("egg_brown", Res.string.style_colour_brown),
                StyleVariant("egg_gold", Res.string.style_colour_gold, Achievement.EGGCELLENT_DISCOVERY),
            ),
            unlock = AchievementCount(50),
        ),
        StyleGroup(
            Res.string.style_family_cake,
            listOf(
                StyleVariant("cake_strawberry", Res.string.style_colour_strawberry),
                StyleVariant("cake_vanilla", Res.string.style_colour_vanilla),
                StyleVariant("cake_chocolate", Res.string.style_colour_chocolate),
            ),
            unlock = AchievementCount(60),
        ),
        StyleGroup(
            Res.string.style_family_stone,
            listOf(
                StyleVariant("stone_granite", Res.string.style_colour_granite),
                StyleVariant("stone_slate", Res.string.style_colour_slate),
                StyleVariant("stone_sandstone", Res.string.style_colour_sandstone),
                StyleVariant("stone_limestone", Res.string.style_colour_limestone),
                StyleVariant("stone_basalt", Res.string.style_colour_basalt),
                StyleVariant("stone_white_marble", Res.string.style_colour_white_marble),
                StyleVariant("stone_black_marble", Res.string.style_colour_black_marble),
            ),
            unlock = AchievementCount(65),
        ),
        StyleGroup(
            Res.string.style_family_gems,
            listOf(
                StyleVariant("gems_ivory", Res.string.style_colour_ivory),
                StyleVariant("gems_black", Res.string.style_colour_black),
            ),
            unlock = AchievementCount(65),
        ),
        StyleGroup(
            Res.string.style_family_lcd,
            listOf(
                StyleVariant("lcd_neon", Res.string.style_colour_neon),
                StyleVariant("lcd_white", Res.string.style_colour_white),
            ),
            unlock = AchievementCount(70),
        ),
        // Opens with the Christmas cup, mat and background.
        StyleGroup(
            Res.string.style_family_christmas,
            listOf(
                StyleVariant("christmas_snowcap", Res.string.style_colour_snowcap),
                StyleVariant("christmas_gift", Res.string.style_colour_gift),
                StyleVariant("christmas_baubles", Res.string.style_colour_baubles),
            ),
            unlock = AchievementCount(70),
        ),
        StyleGroup(
            Res.string.style_family_meadow,
            listOf(
                StyleVariant("meadow_green", Res.string.style_colour_green),
            ),
            unlock = AchievementCount(75),
        ),
        StyleGroup(
            Res.string.style_family_ribbon,
            listOf(
                StyleVariant("ribbon_white", Res.string.style_colour_white),
                StyleVariant("ribbon_black", Res.string.style_colour_black),
            ),
            unlock = AchievementCount(80),
        ),
        StyleGroup(
            Res.string.style_family_bestagon,
            listOf(
                StyleVariant("bestagon_honey", Res.string.style_colour_honey),
                StyleVariant("bestagon_slate", Res.string.style_colour_slate),
            ),
            unlock = AchievementCount(85),
        ),
        StyleGroup(
            Res.string.style_family_glitter,
            listOf(
                StyleVariant("glitter_mixed", Res.string.style_colour_mixed),
            ),
            unlock = AchievementCount(90),
        ),
        StyleGroup(
            Res.string.style_family_garden,
            listOf(
                StyleVariant("garden_soil", Res.string.style_colour_soil),
            ),
            unlock = AchievementCount(95),
        ),
        StyleGroup(
            Res.string.style_family_neon,
            listOf(
                StyleVariant("neon_night", Res.string.style_colour_night),
            ),
            unlock = AchievementCount(100),
        ),
        StyleGroup(
            Res.string.style_family_rgb,
            listOf(
                StyleVariant("rgb_rainbow", Res.string.style_colour_rainbow),
                StyleVariant("rgb_wave", Res.string.style_colour_wave),
                StyleVariant("rgb_white_rainbow", Res.string.style_colour_white_rainbow),
                StyleVariant("rgb_white_wave", Res.string.style_colour_white_wave),
            ),
            unlock = AchievementCount(100),
        ),
        // Nat 20 opens white and black; Critical Success opens blue - the family opens with either.
        StyleGroup(
            Res.string.style_family_d20,
            listOf(
                StyleVariant("d20_white", Res.string.style_colour_white, Achievement.NAT_20),
                StyleVariant("d20_black", Res.string.style_colour_black, Achievement.NAT_20),
                StyleVariant("d20_blue", Res.string.style_colour_blue, Achievement.CRITICAL_SUCCESS),
            ),
            unlock = StyleUnlock.AnyAchievement(listOf(Achievement.NAT_20, Achievement.CRITICAL_SUCCESS)),
        ),
        StyleGroup(
            Res.string.style_family_googly,
            listOf(
                StyleVariant("googly_ivory", Res.string.style_colour_ivory),
                StyleVariant("googly_black", Res.string.style_colour_black),
                StyleVariant("googly_blue", Res.string.style_colour_blue),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.COMPLETIONIST),
        ),
        StyleGroup(
            Res.string.style_family_glitch,
            listOf(
                StyleVariant("glitch_black", Res.string.style_colour_black),
                StyleVariant("glitch_white", Res.string.style_colour_white),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.BIG_FAN),
        ),
        StyleGroup(
            Res.string.style_family_maths,
            listOf(
                StyleVariant("maths_white", Res.string.style_colour_white),
                StyleVariant("maths_black", Res.string.style_colour_black),
                StyleVariant("maths_green", Res.string.style_colour_green),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.THE_SOLUTION),
        ),
        StyleGroup(
            Res.string.style_family_wireframe,
            listOf(
                StyleVariant("wireframe_cyan", Res.string.style_colour_cyan),
                StyleVariant("wireframe_green", Res.string.style_colour_green),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.ARE_YOU_A_DEVELOPER),
        ),
        StyleGroup(
            Res.string.style_family_devil,
            listOf(
                StyleVariant("devil_crimson", Res.string.style_colour_crimson),
                StyleVariant("devil_black", Res.string.style_colour_black),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.BAD_OMEN),
        ),
        // To go with the Starry mat and background, and earned with them.
        StyleGroup(
            Res.string.style_family_starry,
            listOf(
                StyleVariant("starry_midnight", Res.string.style_colour_midnight),
                StyleVariant("starry_silver", Res.string.style_colour_silver),
                StyleVariant("starry_purple", Res.string.style_colour_purple),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.ROLLING_THE_MIDNIGHT_DICE),
        ),
    ),
)

/** The dice cups: every family, in the Styles screen's order, and what unlocks it. Their art is `ui.style.DiceCupStyles`. */
object DiceCupStyleRegistry : StyleRegistry(
    Res.string.style_noun_dice_cup,
    listOf(
        // The casino shaker, first so it's the default - "Classic", like every category's default.
        StyleGroup(
            Res.string.style_family_classic,
            listOf(
                StyleVariant("classic_gold", Res.string.style_colour_gold),
                StyleVariant("classic_black", Res.string.style_colour_black),
                StyleVariant("classic_green", Res.string.style_colour_green),
            ),
        ),
        StyleGroup(
            Res.string.style_family_leather,
            listOf(
                StyleVariant("leather_tan", Res.string.style_colour_tan),
                StyleVariant("leather_black", Res.string.style_colour_black),
                StyleVariant("leather_oxblood", Res.string.style_colour_oxblood),
            ),
            unlock = AchievementCount(5),
        ),
        StyleGroup(
            Res.string.style_family_containers,
            listOf(
                StyleVariant("containers_barrel", Res.string.style_colour_barrel),
                StyleVariant("containers_oil_drum", Res.string.style_colour_oil_drum),
                StyleVariant("containers_shipping", Res.string.style_colour_shipping),
            ),
            unlock = AchievementCount(10),
        ),
        StyleGroup(
            Res.string.style_family_fishbowl,
            listOf(
                StyleVariant("fishbowl_clownfish", Res.string.style_colour_clownfish),
            ),
            unlock = AchievementCount(WATER_SET_UNLOCK),
        ),
        StyleGroup(
            Res.string.style_family_faceted,
            listOf(
                StyleVariant("faceted_green", Res.string.style_colour_green),
                StyleVariant("faceted_black", Res.string.style_colour_black),
                StyleVariant("faceted_red", Res.string.style_colour_red),
            ),
            unlock = AchievementCount(20),
        ),
        StyleGroup(
            Res.string.style_family_flowerpot,
            listOf(
                StyleVariant("flowerpot_terracotta", Res.string.style_colour_terracotta),
                StyleVariant("flowerpot_seedling", Res.string.style_colour_seedling, Achievement.GREENFINGERS),
                StyleVariant("flowerpot_bud", Res.string.style_colour_bud, Achievement.GREENFINGERS),
                StyleVariant("flowerpot_opening", Res.string.style_colour_opening, Achievement.GREENFINGERS),
                StyleVariant("flowerpot_sunflower", Res.string.style_colour_sunflower, Achievement.GREENFINGERS),
            ),
            unlock = AchievementCount(25),
        ),
        StyleGroup(
            Res.string.style_family_glass,
            listOf(
                StyleVariant("glass_clear", Res.string.style_colour_clear),
                StyleVariant("glass_blue", Res.string.style_colour_blue),
                StyleVariant("glass_amber", Res.string.style_colour_amber),
                StyleVariant("beaker_blue", Res.string.style_colour_beaker_blue),
                StyleVariant("beaker_green", Res.string.style_colour_beaker_green),
            ),
            unlock = AchievementCount(30),
        ),
        StyleGroup(
            Res.string.style_family_volcano,
            listOf(
                StyleVariant("volcano_basalt", Res.string.style_colour_basalt),
            ),
            unlock = AchievementCount(35),
        ),
        StyleGroup(
            Res.string.style_family_top_hat,
            listOf(
                StyleVariant("top_hat_black", Res.string.style_colour_black),
                StyleVariant("top_hat_grey", Res.string.style_colour_grey),
                // Secret: only offered once The Magician's Secret is earned.
                StyleVariant("top_hat_rabbit", Res.string.style_colour_rabbit, Achievement.THE_MAGICIANS_SECRET),
            ),
            unlock = AchievementCount(40),
        ),
        StyleGroup(
            Res.string.style_family_takeaway,
            listOf(
                StyleVariant("takeaway_white", Res.string.style_colour_white),
                StyleVariant("takeaway_black", Res.string.style_colour_black),
            ),
            unlock = AchievementCount(50),
        ),
        StyleGroup(
            Res.string.style_family_chicken,
            listOf(
                StyleVariant("chicken_white", Res.string.style_colour_white),
                StyleVariant("chicken_brown", Res.string.style_colour_brown),
            ),
            unlock = AchievementCount(50),
        ),
        StyleGroup(
            Res.string.style_family_picnic,
            listOf(
                StyleVariant("picnic_wicker", Res.string.style_colour_wicker),
            ),
            unlock = AchievementCount(60),
        ),
        StyleGroup(
            Res.string.style_family_cauldron,
            listOf(
                StyleVariant("cauldron_green", Res.string.style_colour_green),
                StyleVariant("cauldron_purple", Res.string.style_colour_purple),
            ),
            unlock = AchievementCount(65),
        ),
        StyleGroup(
            Res.string.style_family_christmas,
            listOf(
                StyleVariant("christmas_red", Res.string.style_colour_red),
                StyleVariant("christmas_blue", Res.string.style_colour_blue),
            ),
            unlock = AchievementCount(70),
        ),
        StyleGroup(
            Res.string.style_family_urn,
            listOf(
                StyleVariant("urn_terracotta", Res.string.style_colour_terracotta),
                StyleVariant("urn_bronze", Res.string.style_colour_bronze),
            ),
            unlock = AchievementCount(75),
        ),
        StyleGroup(
            Res.string.style_family_treasure,
            listOf(
                StyleVariant("treasure_oak", Res.string.style_colour_oak),
            ),
            unlock = AchievementCount(80),
        ),
        StyleGroup(
            Res.string.style_family_glitter,
            listOf(
                StyleVariant("glitter_gold", Res.string.style_colour_gold),
                StyleVariant("glitter_pink", Res.string.style_colour_pink),
            ),
            unlock = AchievementCount(90),
        ),
        StyleGroup(
            Res.string.style_family_neon,
            listOf(
                StyleVariant("neon_pink_and_blue", Res.string.style_colour_pink_and_blue),
            ),
            unlock = AchievementCount(100),
        ),
        // The last of the styles to open, with the rest of the RGB set: a gift for earning them all.
        StyleGroup(
            Res.string.style_family_rgb,
            listOf(
                StyleVariant("rgb_halo_rainbow", Res.string.style_colour_halo_rainbow),
                StyleVariant("rgb_halo_wave", Res.string.style_colour_halo_wave),
                StyleVariant("rgb_white_halo_rainbow", Res.string.style_colour_white_halo_rainbow),
                StyleVariant("rgb_white_halo_wave", Res.string.style_colour_white_halo_wave),
                StyleVariant("rgb_lantern_rainbow", Res.string.style_colour_lantern_rainbow),
                StyleVariant("rgb_lantern_wave", Res.string.style_colour_lantern_wave),
                StyleVariant("rgb_white_lantern_rainbow", Res.string.style_colour_white_lantern_rainbow),
                StyleVariant("rgb_white_lantern_wave", Res.string.style_colour_white_lantern_wave),
            ),
            unlock = AchievementCount(100),
        ),
        StyleGroup(
            Res.string.style_family_martini,
            listOf(
                StyleVariant("martini_classic", Res.string.style_colour_classic),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.SHAKEN_NOT_TAPPED),
        ),
    ),
)

/** The dice mats: every family, in the Styles screen's order, and what unlocks it. Their art is `ui.style.DiceMats`. */
object DiceMatRegistry : StyleRegistry(
    Res.string.style_noun_mat,
    listOf(
        StyleGroup(
            Res.string.style_family_classic,
            listOf(
                StyleVariant("classic_blue", Res.string.style_colour_blue),
                StyleVariant("classic_red", Res.string.style_colour_red),
            ),
        ),
        StyleGroup(
            Res.string.style_family_leather,
            listOf(
                StyleVariant("leather_tan", Res.string.style_colour_tan),
                StyleVariant("leather_oxblood", Res.string.style_colour_oxblood),
                StyleVariant("leather_black", Res.string.style_colour_black),
            ),
            unlock = AchievementCount(5),
        ),
        StyleGroup(
            Res.string.style_family_wood,
            listOf(
                StyleVariant("wood_brown", Res.string.style_colour_brown),
                StyleVariant("wood_oak", Res.string.style_colour_oak),
                StyleVariant("wood_walnut", Res.string.style_colour_walnut),
                StyleVariant("wood_maple", Res.string.style_colour_maple),
            ),
            unlock = AchievementCount(10),
        ),
        StyleGroup(
            Res.string.style_family_ocean,
            listOf(
                StyleVariant("ocean_lagoon", Res.string.style_colour_lagoon),
                StyleVariant("ocean_deep", Res.string.style_colour_deep),
            ),
            unlock = AchievementCount(WATER_SET_UNLOCK),
        ),
        StyleGroup(
            Res.string.style_family_casino,
            listOf(
                StyleVariant("casino_green", Res.string.style_colour_green),
                StyleVariant("casino_purple", Res.string.style_colour_purple),
            ),
            unlock = AchievementCount(40),
        ),
        StyleGroup(
            Res.string.style_family_starry,
            listOf(
                StyleVariant("starry_midnight", Res.string.style_colour_midnight),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.ROLLING_THE_MIDNIGHT_DICE),
        ),
        StyleGroup(
            Res.string.style_family_gingham,
            listOf(
                StyleVariant("gingham_red", Res.string.style_colour_red),
                StyleVariant("gingham_blue", Res.string.style_colour_blue),
                StyleVariant("gingham_green", Res.string.style_colour_green),
            ),
            unlock = AchievementCount(60),
        ),
        StyleGroup(
            Res.string.style_family_stone,
            listOf(
                StyleVariant("stone_granite", Res.string.style_colour_granite),
                StyleVariant("stone_slate", Res.string.style_colour_slate),
                StyleVariant("stone_sandstone", Res.string.style_colour_sandstone),
                StyleVariant("stone_limestone", Res.string.style_colour_limestone),
                StyleVariant("stone_basalt", Res.string.style_colour_basalt),
                StyleVariant("stone_white_marble", Res.string.style_colour_white_marble),
                StyleVariant("stone_black_marble", Res.string.style_colour_black_marble),
            ),
            unlock = AchievementCount(65),
        ),
        StyleGroup(
            Res.string.style_family_christmas,
            listOf(
                StyleVariant("christmas_red", Res.string.style_colour_red),
                StyleVariant("christmas_kraft", Res.string.style_colour_kraft),
            ),
            unlock = AchievementCount(70),
        ),
        StyleGroup(
            Res.string.style_family_circuit,
            listOf(
                StyleVariant("circuit_green", Res.string.style_colour_green),
                StyleVariant("circuit_black", Res.string.style_colour_black),
            ),
            unlock = AchievementCount(80),
        ),
        StyleGroup(
            Res.string.style_family_bestagons,
            listOf(
                StyleVariant("bestagons_honeycomb", Res.string.style_colour_honeycomb),
                StyleVariant("bestagons_honey", Res.string.style_colour_honey),
                StyleVariant("bestagons_slate", Res.string.style_colour_slate),
            ),
            unlock = AchievementCount(85),
        ),
        StyleGroup(
            Res.string.style_family_glitter,
            listOf(
                StyleVariant("glitter_gold", Res.string.style_colour_gold),
                StyleVariant("glitter_silver", Res.string.style_colour_silver),
                StyleVariant("glitter_rose_gold", Res.string.style_colour_rose_gold),
                StyleVariant("glitter_pink", Res.string.style_colour_pink),
                StyleVariant("glitter_purple", Res.string.style_colour_purple),
                StyleVariant("glitter_blue", Res.string.style_colour_blue),
                StyleVariant("glitter_emerald", Res.string.style_colour_emerald),
            ),
            unlock = AchievementCount(90),
        ),
        StyleGroup(
            Res.string.style_family_neon,
            listOf(
                StyleVariant("neon_pink_and_blue", Res.string.style_colour_pink_and_blue),
                StyleVariant("neon_green_and_purple", Res.string.style_colour_green_and_purple),
                StyleVariant("neon_orange_and_teal", Res.string.style_colour_orange_and_teal),
            ),
            unlock = AchievementCount(100),
        ),
        StyleGroup(
            Res.string.style_family_rgb,
            listOf(
                StyleVariant("rgb_rainbow", Res.string.style_colour_rainbow),
                StyleVariant("rgb_wave", Res.string.style_colour_wave),
                StyleVariant("rgb_white_rainbow", Res.string.style_colour_white_rainbow),
                StyleVariant("rgb_white_wave", Res.string.style_colour_white_wave),
            ),
            unlock = AchievementCount(100),
        ),
        StyleGroup(
            Res.string.style_family_wireframe,
            listOf(
                StyleVariant("wireframe_cyan", Res.string.style_colour_cyan),
                StyleVariant("wireframe_green", Res.string.style_colour_green),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.ARE_YOU_A_DEVELOPER),
        ),
    ),
)

/** The table backgrounds: every family, in the Styles screen's order, and what unlocks it. Their art is `ui.style.TableBackgrounds`. */
object TableBackgroundRegistry : StyleRegistry(
    Res.string.style_noun_background,
    listOf(
        StyleGroup(
            Res.string.style_family_classic,
            listOf(
                StyleVariant("classic_navy", Res.string.style_colour_navy),
                StyleVariant("classic_red", Res.string.style_colour_red),
                StyleVariant("classic_brown", Res.string.style_colour_brown),
            ),
        ),
        StyleGroup(
            Res.string.style_family_leather,
            listOf(
                StyleVariant("leather_tan", Res.string.style_colour_tan),
                StyleVariant("leather_oxblood", Res.string.style_colour_oxblood),
                StyleVariant("leather_black", Res.string.style_colour_black),
            ),
            unlock = AchievementCount(5),
        ),
        StyleGroup(
            Res.string.style_family_planks,
            listOf(
                StyleVariant("planks_oak", Res.string.style_colour_oak),
                StyleVariant("planks_walnut", Res.string.style_colour_walnut),
            ),
            unlock = AchievementCount(10),
        ),
        StyleGroup(
            Res.string.style_family_ocean,
            listOf(
                StyleVariant("ocean_waves", Res.string.style_colour_waves),
                StyleVariant("ocean_lagoon", Res.string.style_colour_lagoon),
            ),
            unlock = AchievementCount(WATER_SET_UNLOCK),
        ),
        StyleGroup(
            Res.string.style_family_spotlight,
            listOf(
                StyleVariant("spotlight_purple", Res.string.style_colour_purple),
                StyleVariant("spotlight_navy", Res.string.style_colour_navy),
                StyleVariant("spotlight_green", Res.string.style_colour_green),
            ),
            unlock = AchievementCount(30),
        ),
        StyleGroup(
            Res.string.style_family_pinstripe,
            listOf(
                StyleVariant("pinstripe_charcoal", Res.string.style_colour_charcoal),
                StyleVariant("pinstripe_navy", Res.string.style_colour_navy),
            ),
            unlock = AchievementCount(35),
        ),
        StyleGroup(
            Res.string.style_family_brick,
            listOf(
                StyleVariant("brick_neon", Res.string.style_colour_neon),
            ),
            unlock = AchievementCount(40),
        ),
        StyleGroup(
            Res.string.style_family_sunburst,
            listOf(
                StyleVariant("sunburst_crimson", Res.string.style_colour_crimson),
                StyleVariant("sunburst_amber", Res.string.style_colour_amber),
            ),
            unlock = AchievementCount(45),
        ),
        StyleGroup(
            Res.string.style_family_starry,
            listOf(
                StyleVariant("starry_midnight", Res.string.style_colour_midnight),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.ROLLING_THE_MIDNIGHT_DICE),
        ),
        StyleGroup(
            Res.string.style_family_gingham,
            listOf(
                StyleVariant("gingham_red", Res.string.style_colour_red),
                StyleVariant("gingham_blue", Res.string.style_colour_blue),
                StyleVariant("gingham_green", Res.string.style_colour_green),
            ),
            unlock = AchievementCount(60),
        ),
        StyleGroup(
            Res.string.style_family_stone,
            listOf(
                StyleVariant("stone_slate", Res.string.style_colour_slate),
                StyleVariant("stone_granite", Res.string.style_colour_granite),
                StyleVariant("stone_black_marble", Res.string.style_colour_black_marble),
            ),
            unlock = AchievementCount(65),
        ),
        StyleGroup(
            Res.string.style_family_christmas,
            listOf(
                StyleVariant("christmas_green", Res.string.style_colour_green),
                StyleVariant("christmas_red", Res.string.style_colour_red),
            ),
            unlock = AchievementCount(70),
        ),
        StyleGroup(
            Res.string.style_family_meadow,
            listOf(
                StyleVariant("meadow_green", Res.string.style_colour_green),
            ),
            unlock = AchievementCount(75),
        ),
        StyleGroup(
            Res.string.style_family_circuit,
            listOf(
                StyleVariant("circuit_green", Res.string.style_colour_green),
                StyleVariant("circuit_black", Res.string.style_colour_black),
            ),
            unlock = AchievementCount(80),
        ),
        StyleGroup(
            Res.string.style_family_bestagons,
            listOf(
                StyleVariant("bestagons_honey", Res.string.style_colour_honey),
                StyleVariant("bestagons_charcoal", Res.string.style_colour_charcoal),
                StyleVariant("bestagons_indigo", Res.string.style_colour_indigo),
            ),
            unlock = AchievementCount(85),
        ),
        StyleGroup(
            Res.string.style_family_glitter,
            listOf(
                StyleVariant("glitter_gold", Res.string.style_colour_gold),
                StyleVariant("glitter_silver", Res.string.style_colour_silver),
                StyleVariant("glitter_rose_gold", Res.string.style_colour_rose_gold),
                StyleVariant("glitter_pink", Res.string.style_colour_pink),
                StyleVariant("glitter_purple", Res.string.style_colour_purple),
                StyleVariant("glitter_blue", Res.string.style_colour_blue),
                StyleVariant("glitter_emerald", Res.string.style_colour_emerald),
            ),
            unlock = AchievementCount(90),
        ),
        StyleGroup(
            Res.string.style_family_neon,
            listOf(
                StyleVariant("neon_pink_and_blue", Res.string.style_colour_pink_and_blue),
                StyleVariant("neon_green_and_purple", Res.string.style_colour_green_and_purple),
                StyleVariant("neon_orange_and_teal", Res.string.style_colour_orange_and_teal),
            ),
            unlock = AchievementCount(100),
        ),
        StyleGroup(
            Res.string.style_family_rgb,
            listOf(
                StyleVariant("rgb_rainbow", Res.string.style_colour_rainbow),
                StyleVariant("rgb_wave", Res.string.style_colour_wave),
                StyleVariant("rgb_ripple", Res.string.style_colour_ripple),
                StyleVariant("rgb_swirl", Res.string.style_colour_swirl),
                StyleVariant("rgb_starlight", Res.string.style_colour_starlight),
            ),
            unlock = AchievementCount(100),
        ),
        StyleGroup(
            Res.string.style_family_glitch,
            listOf(
                StyleVariant("glitch_screen", Res.string.style_colour_screen),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.BIG_FAN),
        ),
        StyleGroup(
            Res.string.style_family_floating_dice,
            listOf(
                StyleVariant("floating_dice_menu", Res.string.style_colour_menu),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.NOT_THOSE_DICE),
        ),
    ),
)

/** The score frames: every family, in the Styles screen's order, and what unlocks it. Their art is `ui.style.ScoreFrames`. */
object ScoreFrameRegistry : StyleRegistry(
    Res.string.style_noun_frame,
    listOf(
        StyleGroup(
            Res.string.style_family_classic,
            listOf(
                StyleVariant("classic_ring", Res.string.style_colour_ring),
            ),
        ),
        StyleGroup(
            Res.string.style_family_floral,
            listOf(
                StyleVariant("floral_rose", Res.string.style_colour_rose),
                StyleVariant("floral_daisy", Res.string.style_colour_daisy),
                StyleVariant("floral_vine", Res.string.style_colour_vine),
            ),
            unlock = AchievementCount(10),
        ),
        StyleGroup(
            Res.string.style_family_nautical,
            listOf(
                StyleVariant("nautical_chain", Res.string.style_colour_chain),
                StyleVariant("nautical_rope", Res.string.style_colour_rope),
            ),
            unlock = AchievementCount(WATER_SET_UNLOCK),
        ),
        StyleGroup(
            Res.string.style_family_art_deco,
            listOf(
                StyleVariant("art_deco_fan", Res.string.style_colour_fan),
                StyleVariant("art_deco_stepped", Res.string.style_colour_stepped),
            ),
            unlock = AchievementCount(25),
        ),
        StyleGroup(
            Res.string.style_family_tribal,
            listOf(
                StyleVariant("tribal_swirl", Res.string.style_colour_swirl),
                StyleVariant("tribal_flame", Res.string.style_colour_flame),
            ),
            unlock = AchievementCount(35),
        ),
        StyleGroup(
            Res.string.style_family_wreath,
            listOf(
                StyleVariant("wreath_laurel", Res.string.style_colour_laurel),
                StyleVariant("wreath_olive", Res.string.style_colour_olive),
            ),
            unlock = AchievementCount(50),
        ),
        StyleGroup(
            Res.string.style_family_greek,
            listOf(
                StyleVariant("greek_key", Res.string.style_colour_key),
                StyleVariant("greek_waves", Res.string.style_colour_waves),
            ),
            unlock = AchievementCount(50),
        ),
        StyleGroup(
            Res.string.style_family_celtic,
            listOf(
                StyleVariant("celtic_knot", Res.string.style_colour_knot),
            ),
            unlock = AchievementCount(65),
        ),
        StyleGroup(
            Res.string.style_family_pixel,
            listOf(
                StyleVariant("pixel_retro", Res.string.style_colour_retro),
            ),
            unlock = AchievementCount(75),
        ),
        StyleGroup(
            Res.string.style_family_neon,
            listOf(
                StyleVariant("neon_single", Res.string.style_colour_single),
                StyleVariant("neon_double", Res.string.style_colour_double),
            ),
            unlock = AchievementCount(85),
        ),
        StyleGroup(
            Res.string.style_family_marquee,
            listOf(
                StyleVariant("marquee_bulbs", Res.string.style_colour_bulbs),
                StyleVariant("marquee_diamonds", Res.string.style_colour_diamonds),
            ),
            unlock = AchievementCount(100),
        ),
        // Opens at 45 achievements rather than with the Starry dice, mat and background (the Easter egg).
        StyleGroup(
            Res.string.style_family_starry,
            listOf(
                StyleVariant("starry_stars", Res.string.style_colour_stars),
                StyleVariant("starry_sparkles", Res.string.style_colour_sparkles),
                StyleVariant("starry_constellation", Res.string.style_colour_constellation),
            ),
            unlock = AchievementCount(45),
        ),
    ),
    variantNoun = Res.string.style_variant_design,
)
