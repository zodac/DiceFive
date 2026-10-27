package net.zodac.dicefive.ui.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import net.zodac.dicefive.data.settings.DefaultStyleIds
import net.zodac.dicefive.game.style.DiceCupStyleRegistry
import net.zodac.dicefive.game.style.DiceMatRegistry
import net.zodac.dicefive.game.style.DiceStyleRegistry
import net.zodac.dicefive.game.style.ScoreFrameRegistry
import net.zodac.dicefive.game.style.StyleAccess
import net.zodac.dicefive.game.style.StyleCategory
import net.zodac.dicefive.game.style.StyleGroup
import net.zodac.dicefive.game.style.StyleRegistry
import net.zodac.dicefive.game.style.StyleUnlock
import net.zodac.dicefive.game.style.StyleVariant
import net.zodac.dicefive.game.style.TableBackgroundRegistry
import net.zodac.dicefive.game.style.UnlockCount
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.FLOWERPOT_FULL_BLOOM
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

/** One variant's art: the [style] it's drawn as, and its [swatch] - its dot on the Styles screen. */
data class StyleArt<out T : TableArt>(val swatch: Color, val style: T)

/** One colour (or design) of a [StyleFamily]: its [variant] - name, id and any secret lock - and the art it's drawn as. */
data class StyleColour<out T : TableArt>(val variant: StyleVariant, val swatch: Color, val style: T) {
    val name: StringResource get() = variant.name
    val secretAchievement: Achievement? get() = variant.secretAchievement

    fun isAvailable(access: StyleAccess): Boolean = variant.isAvailable(access)
}

/** A [StyleGroup] as the Styles screen shows it: one tile, its [colours] drawn. */
data class StyleFamily<T : TableArt>(val group: StyleGroup, val colours: List<StyleColour<T>>) {
    val name: StringResource get() = group.name
    val unlock: StyleUnlock get() = group.unlock

    fun colourOf(id: String): StyleColour<T>? = colours.firstOrNull { it.style.id == id }

    /** The [colours] to offer: all but any secret one not yet earned. */
    fun availableColours(access: StyleAccess): List<StyleColour<T>> = colours.filter { it.isAvailable(access) }
}

/**
 * One category of table art: its [registry]'s groups and locks, each variant joined to its [art] by id - [art] holds
 * every group's, in the registry's order, and the two must match one for one. Which picks are usable is the
 * registry's to say; this only draws them.
 */
open class StyleCatalog<T : TableArt>(val registry: StyleRegistry, art: List<List<StyleArt<T>>>) {

    val families: List<StyleFamily<T>>

    init {
        require(art.size == registry.groups.size) { "${registry.groups.size} style groups, but art for ${art.size}" }
        families = registry.groups.zip(art) { group, groupArt ->
            require(groupArt.map { it.style.id } == group.variants.map { it.id }) { "Art ${groupArt.map { it.style.id }} for ${group.variants.map { it.id }}" }
            StyleFamily(group, group.variants.zip(groupArt) { variant, piece -> StyleColour(variant, piece.swatch, piece.style) })
        }
    }

    val noun: StringResource get() = registry.noun

    val variantNoun: StringResource get() = registry.variantNoun

    val all: List<T> = families.flatMap { family -> family.colours.map { it.style } }

    /** The first colour of the first family. */
    val default: T = all.first()

    fun byId(id: String): T = all.firstOrNull { it.id == id } ?: default

    /** The family [id] belongs to - or the default's, for an id nothing recognises, as [byId] does. */
    fun familyOf(id: String): StyleFamily<T> = families.first { it.colourOf(byId(id).id) != null }

    /** What to actually draw for the saved pick [id]: its art, or the [default]'s while it's locked - see [StyleRegistry.unlockedId]. */
    fun unlockedById(id: String, access: StyleAccess): T = byId(registry.unlockedId(id, access))

    fun unlockCount(access: StyleAccess): UnlockCount = registry.unlockCount(access)
}

/** A Classic die in one of Tricolour mode's colours, from the same palette that colours its dice in that mode. */
private fun classicDie(id: String, colour: DieColour): StyleArt<DiceStyle> =
    StyleArt(colour.palette.swatch, ColouredClassicDiceStyle(id, colour.palette))

/** Every category's catalog, in the Styles screen's order. */
val StyleCatalogs: List<StyleCatalog<*>> by lazy { StyleCategory.entries.map { it.catalog } }

object DiceStyles : StyleCatalog<DiceStyle>(
    DiceStyleRegistry,
    listOf(
        listOf(
            StyleArt(IvoryDiceTop, IvoryDiceStyle),
            classicDie("classic_red", DieColour.RED),
            classicDie("classic_yellow", DieColour.YELLOW),
            classicDie("classic_blue", DieColour.BLUE),
            StyleArt(BarrelDiceTop, BarrelDiceStyle),
        ),
        listOf(
            colour(LeatherDiceStyle("leather_tan", Color(0xFFB98553), Color(0xFF8A5A2E), Color(0xFFF0DDB8), Color(0xFF4A2E14))),
            colour(LeatherDiceStyle("leather_black", Color(0xFF4A4A4A), Color(0xFF262626), Color(0xFFBDBDBD), Color(0xFF0A0A0A))),
            colour(LeatherDiceStyle("leather_oxblood", Color(0xFF92323A), Color(0xFF641A20), Color(0xFFE8C9A0), Color(0xFF2A0809))),
        ),
        listOf(
            // Each with pips of a contrasting wood, inlaid.
            colour(WoodDiceStyle("wood_oak", Color(0xFFC9935A), Color(0xFF94612F), Color(0xFF5A3A1A), Color(0xFF3E2614))),
            colour(WoodDiceStyle("wood_walnut", Color(0xFF7A5234), Color(0xFF4A2E1A), Color(0xFF2A180C), Color(0xFFF0DCB0))),
            colour(WoodDiceStyle("wood_maple", Color(0xFFF2DDB0), Color(0xFFD9B97E), Color(0xFFA07A48), Color(0xFF4A2E1A))),
        ),
        listOf(
            colour(OceanDiceStyle("ocean_lagoon", Color(0xFF1A9DB5), Color(0xFF0A5C7A), Color(0xFFD8FBFF), Color(0xFFF7F3EA))),
            colour(OceanDiceStyle("ocean_deep", Color(0xFF235EA8), Color(0xFF0B2350), Color(0xFFB8DCFF), Color(0xFFF7F3EA))),
        ),
        listOf(
            // Gold and bronze dice would swallow the usual gold held ring, so theirs is white.
            colour(
                MetalDiceStyle(
                    "metal_gold",
                    listOf(Color(0xFFFFE9A3), Color(0xFFD4A437), Color(0xFFFFE08A), Color(0xFF9C7219)),
                    Color(0xFF5C420B),
                    heldRing = Color.White,
                ),
            ),
            colour(
                MetalDiceStyle(
                    "metal_silver",
                    listOf(Color(0xFFF2F4F6), Color(0xFFAEB5BB), Color(0xFFE6EAED), Color(0xFF7C848B)),
                    Color(0xFF33393E),
                ),
            ),
            colour(
                MetalDiceStyle(
                    "metal_bronze",
                    listOf(Color(0xFFF0B884), Color(0xFFB06A34), Color(0xFFE3A56C), Color(0xFF7A4418)),
                    Color(0xFF3F2008),
                    heldRing = Color.White,
                ),
            ),
        ),
        listOf(
            colour(NumeralDiceStyle("numeral_digits", Color(0xFFFBFBFB), Color(0xFFD6D6D6), Color(0xFF1E1E1E))),
            colour(NumeralDiceStyle("numeral_red", Color(0xFFE53935), Color(0xFF8E0E0E), Color.White)),
            colour(NumeralDiceStyle("numeral_blue", Color(0xFF2F7FE0), Color(0xFF0B3A80), Color.White)),
        ),
        listOf(
            colour(
                NumeralDiceStyle("world_roman", Color(0xFFF3E9D2), Color(0xFFD8C8A0), Color(0xFF7A1F1F), NumeralSystem.ROMAN),
            ),
            colour(
                NumeralDiceStyle("world_roman_blue", Color(0xFF2F4A73), Color(0xFF17263F), Color(0xFFF3E9D2), NumeralSystem.ROMAN),
            ),
            colour(
                NumeralDiceStyle("world_arabic_green", Color(0xFF006C35), Color(0xFF00471F), Color.White, NumeralSystem.EASTERN_ARABIC),
            ),
            colour(
                NumeralDiceStyle("world_arabic", Color(0xFF3A3A3A), Color(0xFF121212), Color(0xFFE8C66A), NumeralSystem.EASTERN_ARABIC),
            ),
            colour(
                NumeralDiceStyle("world_japanese", Color(0xFFFBFBFB), Color(0xFFE3E3E3), Color(0xFFBC002D), NumeralSystem.JAPANESE),
            ),
            colour(
                NumeralDiceStyle("world_japanese_black", Color(0xFF1A1714), Color(0xFF050403), Color(0xFFD4AF37), NumeralSystem.JAPANESE),
            ),
        ),
        listOf(
            colour(FrostedDiceStyle("frosted_ice", Color(0xFFE3F4FF), Color(0xFFA9D3EE), Color(0xFF1D4E6E))),
            colour(FrostedDiceStyle("frosted_white", Color(0xFFFAFBFC), Color(0xFFD5DADF), Color(0xFF3A4550))),
        ),
        listOf(
            colour(
                NumeralDiceStyle(
                    "text_ivory_serif", Color(0xFFF7F0DC), Color(0xFFDCCFA8), Color(0xFF1F2F5C), NumeralSystem.ENGLISH,
                    font = FontFamily.Serif, size = 0.26f,
                ),
            ),
            colour(
                NumeralDiceStyle(
                    "text_green_mono", Color(0xFF1A1F1A), Color(0xFF080B08), Color(0xFF4CE07A), NumeralSystem.ENGLISH,
                    font = FontFamily.Monospace, size = 0.2f,
                ),
            ),
            colour(
                NumeralDiceStyle(
                    "text_purple_script", Color(0xFF6A3FA0), Color(0xFF34195C), Color(0xFFFFD86B), NumeralSystem.ENGLISH,
                    font = FontFamily.Cursive, size = 0.32f,
                ),
            ),
            colour(
                NumeralDiceStyle(
                    "text_red_block", Color(0xFFE53935), Color(0xFF8E0E0E), Color.White, NumeralSystem.ENGLISH_CAPS,
                    font = FontFamily.SansSerif,
                ),
            ),
            colour(
                NumeralDiceStyle(
                    "text_teal_plain", Color(0xFF1FA6A0), Color(0xFF0B5A57), Color.White, NumeralSystem.ENGLISH,
                    font = FontFamily.SansSerif, size = 0.27f,
                ),
            ),
        ),
        listOf(
            colour(MisprintDiceStyle("misprint_pencil", Color(0xFFFAF7F0), Color(0xFF3A3A3A), seed = 1)),
            colour(MisprintDiceStyle("misprint_blueprint", Color(0xFF1F4E8C), Color(0xFFEAF2FF), seed = 2)),
        ),
        listOf(
            colour(
                ObsidianDiceStyle(
                    "obsidian_lava", Color(0xFF2A2830), Color(0xFF060508),
                    Lava(Color(0xFF1A0A04), Color(0xFFB02A00), Color(0xFFFF6A00), Color(0xFFFFF2A0)),
                ),
            ),
            colour(
                ObsidianDiceStyle(
                    "obsidian_blue", Color(0xFF2A2830), Color(0xFF060508),
                    Lava(Color(0xFF040A1A), Color(0xFF0040B0), Color(0xFF2E9BFF), Color(0xFFD8F4FF)),
                ),
            ),
        ),
        listOf(
            colour(TricolourStripedDiceStyle),
            colour(RainbowStripedDiceStyle),
            StyleArt(IrishFlagDiceStyle.swatch, IrishFlagDiceStyle),
        ),
        listOf(
            colour(PokerDiceStyle("games_poker", Color.White, Color(0xFFE6E6EA))),
            colour(MahjongDiceStyle("games_pinzu", MahjongSuit.PINZU, MahjongFace, MahjongFaceShade, MahjongBack)),
            colour(MahjongDiceStyle("games_manzu", MahjongSuit.MANZU, MahjongFace, MahjongFaceShade, MahjongBack)),
            colour(MahjongDiceStyle("games_sozu", MahjongSuit.SOZU, MahjongFace, MahjongFaceShade, MahjongBack)),
        ),
        listOf(
            colour(RetroDiceStyle("retro_green", Color(0xFF9BBC0F), Color(0xFF306230), Color(0xFF0F380F))),
            colour(
                RetroDiceStyle("retro_amber", Color(0xFFFFB000), Color(0xFF9A5B00), Color(0xFF3A2400), heldRing = Color.White),
            ),
            colour(RetroDiceStyle("retro_blue", Color(0xFF9CC8F5), Color(0xFF2E5C99), Color(0xFF0D2547))),
            colour(RetroDiceStyle("retro_red", Color(0xFFF29C94), Color(0xFF9E2A24), Color(0xFF3D0B08))),
        ),
        listOf(
            colour(
                TallyDiceStyle(
                    "tally_western", TallySystem.GATE, TallyPen.CHALK, TallySurface.SLATE,
                    Color(0xFF3A4440), Color(0xFF232A27), Color(0xFFF2F2EA), Color.White,
                ),
            ),
            colour(
                TallyDiceStyle(
                    "tally_chinese", TallySystem.ZHENG, TallyPen.BRUSH, TallySurface.RICE_PAPER,
                    Color(0xFFF6EFDC), Color(0xFFE5D9B9), Color(0xFF1A1A1A), Color(0xFFB8A57A),
                ),
            ),
            colour(
                TallyDiceStyle(
                    "tally_latin_american", TallySystem.SQUARE, TallyPen.PENCIL, TallySurface.NOTEBOOK,
                    Color.White, Color(0xFFECEFF3), Color(0xFF3A3A3E), Color(0xFF8FB4E0),
                ),
            ),
            colour(
                TallyDiceStyle(
                    "tally_forestry", TallySystem.DOTS, TallyPen.PEN, TallySurface.FIELD_BOOK,
                    Color(0xFFF7E8A4), Color(0xFFE8D47E), Color(0xFF1E2A55), Color(0xFF6A9A6A),
                ),
            ),
        ),
        listOf(
            // Each egg's pips are the other's shell colour.
            colour(EggDiceStyle("egg_white", EggWhiteShell, Color(0xFFD9D2C3), EggBrownShell)),
            colour(EggDiceStyle("egg_brown", EggBrownShell, Color(0xFFA8683F), EggWhiteShell)),
            // The golden egg that rolls one time in 500 with the Chicken cup, kept for good.
            colour(GoldenEggDiceStyle),
        ),
        listOf(
            colour(CakeDiceStyle("cake_strawberry", Color(0xFFFFD3DE), Color(0xFFEFA9BA))),
            colour(CakeDiceStyle("cake_vanilla", Color(0xFFFFF6E6), Color(0xFFEBD9BC))),
            // Milk chocolate, light enough for the strawberries to stand out, each on a dollop of cream.
            colour(CakeDiceStyle("cake_chocolate", Color(0xFFA06A45), Color(0xFF70452B), dollop = Color(0xFFFFF6E6))),
        ),
        listOf(
            colour(StoneDiceStyle("stone_granite", StoneKind.GRANITE, Color(0xFFB8AEA8), Color(0xFF7A716D), Color(0xFF26221F))),
            colour(StoneDiceStyle("stone_slate", StoneKind.SLATE, Color(0xFF5A6470), Color(0xFF2E353D), Color(0xFF9AA2AC))),
            colour(StoneDiceStyle("stone_sandstone", StoneKind.SANDSTONE, Color(0xFFE2BE8C), Color(0xFFB0824E), Color(0xFF5A3A1E))),
            colour(StoneDiceStyle("stone_limestone", StoneKind.LIMESTONE, Color(0xFFEDE6D3), Color(0xFFC9BFA4), Color(0xFF5E5646))),
            colour(StoneDiceStyle("stone_basalt", StoneKind.BASALT, Color(0xFF45464A), Color(0xFF1A1B1E), Color(0xFF8E9096))),
            colour(
                MarbleDiceStyle("stone_white_marble", Color(0xFFF4F2EE), Color(0xFFD9D5CE), Color(0xFF8E8A84), Color(0xFF222222), seed = 1),
            ),
            colour(
                MarbleDiceStyle("stone_black_marble", Color(0xFF3A3A3E), Color(0xFF141416), Color(0xFFD8D8D8), Color(0xFFC8C8CC), seed = 2),
            ),
        ),
        listOf(
            colour(GemDiceStyle("gems_ivory", IvoryDiceTop, IvoryDiceBottom)),
            colour(GemDiceStyle("gems_black", Color(0xFF2E2E31), Color(0xFF0E0E0F))),
        ),
        listOf(
            colour(LcdDiceStyle("lcd_neon", Color(0xFF15181D), Color(0xFF07090D), Color(0xFF3FD7FF), glow = true)),
            colour(LcdDiceStyle("lcd_white", Color(0xFFF4F4F0), Color(0xFFE2E4DE), Color(0xFF151515), glow = false)),
        ),
        listOf(
            colour(SnowcapDiceStyle("christmas_snowcap", Color(0xFFD32F2F), Color(0xFF8E1B1B))),
            colour(GiftDiceStyle("christmas_gift", ChristmasRedPaper, ChristmasGoldRibbon)),
            colour(BaubleDiceStyle("christmas_baubles", Color(0xFF2F7A45), Color(0xFF15452A), Color(0xFFD32F2F), Color(0xFFF2C14E))),
        ),
        listOf(colour(MeadowDiceStyle("meadow_green", Color(0xFF7CB342), Color(0xFF4E8A2A)))),
        listOf(
            colour(RibbonDiceStyle("ribbon_white", Color(0xFFFCFBF7), Color(0xFFE4E1D8), RibbonColours)),
            colour(RibbonDiceStyle("ribbon_black", Color(0xFF2E2E33), Color(0xFF161618), RibbonColours)),
        ),
        listOf(
            colour(BestagonDiceStyle("bestagon_honey", Color(0xFFF5BE45), Color(0xFFC4801A), Color(0xFF4A2A08))),
            colour(BestagonDiceStyle("bestagon_slate", Color(0xFF6E7F8F), Color(0xFF39444F), Color(0xFFF2F4F6))),
        ),
        listOf(colour(GlitterDiceStyle("glitter_mixed", GlitterColours, Color(0xFFFFFBF2)))),
        listOf(
            colour(GardenDiceStyle("garden_soil", Color(0xFF6A4A30), Color(0xFF3E2A1A), soil = true)),
        ),
        listOf(colour(NeonDiceStyle("neon_night", Color(0xFF141418), NeonColours))),
        listOf(
            colour(RgbDiceStyle("rgb_rainbow", Color(0xFF0E0E11))),
            colour(RgbDiceStyle("rgb_wave", Color(0xFF0E0E11), wave = true)),
            colour(RgbDiceStyle("rgb_white_rainbow", IvoryDiceTop, IvoryDiceBottom)),
            colour(RgbDiceStyle("rgb_white_wave", IvoryDiceTop, IvoryDiceBottom, wave = true)),
        ),
        listOf(
            colour(D20WhiteDiceStyle),
            colour(D20BlackDiceStyle),
            // The brand's own pairing: the felt blue with the gold the board uses for "press this" -
            // so the held ring is white instead, not lost against gold numbers.
            colour(D20DiceStyle("d20_blue", Color(0xFF3A6BB0), Color(0xFF14315C), GoldAccent, heldRing = Color.White)),
        ),
        listOf(
            colour(GooglyDiceStyle("googly_ivory", IvoryDiceTop, IvoryDiceBottom, DicePipColor)),
            colour(
                GooglyDiceStyle("googly_black", Color(0xFF3A3A3E), Color(0xFF141416), Color.White),
            ),
            colour(
                GooglyDiceStyle("googly_blue", Color(0xFF3A6BB0), Color(0xFF14315C), GoldAccent, socket = GoldAccent),
            ),
        ),
        listOf(
            colour(GlitchDiceStyle("glitch_black", Color(0xFF101018), Color(0xFFF4F6FF), Color(0xFF2A2A3A))),
            colour(GlitchDiceStyle("glitch_white", Color(0xFFF2F2F4), Color(0xFF15151C), Color(0xFFD0D0D8))),
        ),
        listOf(
            colour(MathsDiceStyle("maths_white", IvoryDiceTop, IvoryDiceBottom, Color.Black)),
            colour(MathsDiceStyle("maths_black", Color(0xFF2E2E31), Color(0xFF0E0E0F), Color.White)),
            // A chalkboard.
            colour(MathsDiceStyle("maths_green", Color(0xFF2F6B45), Color(0xFF173D26), Color.White)),
        ),
        listOf(
            colour(WireframeDiceStyle("wireframe_cyan", Color(0xFF071418), Color(0xFF3FE6FF))),
            colour(WireframeDiceStyle("wireframe_green", Color(0xFF07140B), Color(0xFF4CFF7A))),
        ),
        listOf(
            colour(
                DevilDiceStyle(
                    "devil_crimson",
                    Color(0xFFC62828),
                    Color(0xFF5A0A0E),
                    flame = Color(0xFFFF9A1F),
                    flameCore = Color(0xFFFFE27A),
                    horn = Color(0xFF1A0A0A),
                    hornLight = Color(0xFF5A2A2A),
                ),
            ),
            colour(
                DevilDiceStyle(
                    "devil_black",
                    Color(0xFF3A3A3E),
                    Color(0xFF0E0E10),
                    flame = Color(0xFFFF4A1F),
                    flameCore = Color(0xFFFFB04A),
                    horn = Color(0xFF7A1212),
                    hornLight = Color(0xFFD04040),
                ),
            ),
        ),
        listOf(
            colour(StarryDiceStyle("starry_midnight", Color(0xFF1C2A5E), Color(0xFF0B1230), Color(0xFFFFD54A))),
            colour(StarryDiceStyle("starry_silver", Color(0xFF2A2A33), Color(0xFF0C0C10), Color(0xFFE8ECF4))),
            colour(StarryDiceStyle("starry_purple", Color(0xFF3A1F5E), Color(0xFF170A2E), Color(0xFFFFC46A))),
        ),
    ),
)

/**
 * The Classic cup in Gold - the default cup, and the one the launcher icon draws as a silhouette
 * (the same gold body, dark mouth, lighter rim and navy band - `ic_launcher_foreground.xml`), so a
 * new player's first cup is the one on the icon.
 */
val ClassicGoldDiceCupStyle: DiceCupStyle = CasinoDiceCupStyle(
    DefaultStyleIds.DICE_CUP,
    CupPalette(dark = Color(0xFF7A5A26), light = Color(0xFFEBC77F), mid = CupRimGold, accent = Color(0xFFE6C278), interior = DicePipColor),
    band = FeltNavyBottom,
)

object DiceCupStyles : StyleCatalog<DiceCupStyle>(
    DiceCupStyleRegistry,
    listOf(
        listOf(
            StyleArt(CupRimGold, ClassicGoldDiceCupStyle),
            cup(::CasinoDiceCupStyle, "classic_black", 0xFF0F0F10, 0xFF4A4A4E, 0xFF26262A, 0xFFD4AF37, 0xFF050505),
            cup(::CasinoDiceCupStyle, "classic_green", 0xFF0B2A12, 0xFF2F7A45, 0xFF1B5227, 0xFFD4AF37, 0xFF04120A),
        ),
        listOf(
            cup(::LeatherDiceCupStyle, "leather_tan", 0xFF5C3A1C, 0xFFB98553, 0xFF8A5A2E, 0xFFF0DDB8, 0xFF1E1209),
            cup(::LeatherDiceCupStyle, "leather_black", 0xFF111111, 0xFF4A4A4A, 0xFF262626, 0xFFBDBDBD, 0xFF050505),
            cup(::LeatherDiceCupStyle, "leather_oxblood", 0xFF3A0A0D, 0xFF92323A, 0xFF641A20, 0xFFE8C9A0, 0xFF160405),
        ),
        // Things for holding things.
        listOf(
            StyleArt(BarrelWood, BarrelDiceCupStyle),
            // Glossy blue enamel, the hoops and rims a shade darker.
            cup(::OilDrumDiceCupStyle, "containers_oil_drum", 0xFF0A1250, 0xFF4462D2, 0xFF1A2C9E, 0xFF13217C, 0xFF070D3A),
            // Rust-red steel with bare dark-grey fittings.
            cup(::ShippingContainerDiceCupStyle, "containers_shipping", 0xFF5E1A12, 0xFFD4604A, 0xFFA83A28, 0xFF3A3A3C, 0xFF1A1A1A),
        ),
        listOf(colour(FishbowlDiceCupStyle("fishbowl_clownfish", Color(0xFFD6F0F7), Color(0xFF8FD8EC)))),
        listOf(
            StyleArt(FacetedCupLitFace, FacetedDiceCupStyle),
            StyleArt(Color(0xFF3A3A3F), BlackFacetedDiceCupStyle),
            StyleArt(FireCupLitFace, FireDiceCupStyle),
        ),
        flowerpotColours(),
        listOf(
            colour(GlassDiceCupStyle("glass_clear", Color(0xFFD6ECF7), Color(0xFF8FD3F4))),
            colour(GlassDiceCupStyle("glass_blue", Color(0xFF5AA8E8), Color(0xFF2F7FE0))),
            colour(GlassDiceCupStyle("glass_amber", Color(0xFFE8C98A), Color(0xFFE8A030))),
            // The laboratory beaker, once a style of its own: its ids stay as they were released (StyleCatalogTest).
            colour(BeakerDiceCupStyle("beaker_blue", Color(0xFFD6ECF7), Color(0xFF4FC3F7))),
            colour(BeakerDiceCupStyle("beaker_green", Color(0xFFD6ECF7), Color(0xFF7CE08A))),
        ),
        listOf(
            colour(
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
        listOf(
            cup(::TopHatDiceCupStyle, "top_hat_black", 0xFF0B0B0C, 0xFF3C3C40, 0xFF1E1E21, 0xFFB71C1C, 0xFF030303),
            cup(::TopHatDiceCupStyle, "top_hat_grey", 0xFF3A3A3D, 0xFF9A9AA0, 0xFF6A6A70, 0xFF1A1A1C, 0xFF121214),
            // The black hat, with its rabbit always out.
            cup(
                { id, palette -> TopHatDiceCupStyle(id, palette, rabbitAlwaysOut = true) },
                "top_hat_rabbit",
                0xFF0B0B0C, 0xFF3C3C40, 0xFF1E1E21, 0xFFB71C1C, 0xFF030303,
            ).copy(swatch = RabbitFur),
        ),
        listOf(
            cup(::TakeawayDiceCupStyle, "takeaway_white", 0xFFBDB6AA, 0xFFFFFFFF, 0xFFECE7DE, 0xFFA87A4E, 0xFF2A1A10),
            cup(::TakeawayDiceCupStyle, "takeaway_black", 0xFF111111, 0xFF4A4A4A, 0xFF262626, 0xFFC08A55, 0xFF2A1A10),
        ),
        listOf(
            colour(
                ChickenDiceCupStyle(
                    "chicken_white",
                    HenPalette(feather = Color(0xFFF7F4EE), light = Color.White, dark = Color(0xFFB9B0A2), tail = Color(0xFFE6E0D4)),
                ),
            ),
            colour(
                ChickenDiceCupStyle(
                    "chicken_brown",
                    HenPalette(feather = Color(0xFFB5652B), light = Color(0xFFE0955A), dark = Color(0xFF6E3412), tail = Color(0xFF3A2A1E)),
                ),
            ),
        ),
        listOf(
            colour(
                PicnicBasketDiceCupStyle(
                    "picnic_wicker",
                    BasketPalette(
                        wickerLight = Color(0xFFE8C88A), wicker = Color(0xFFC8985A), wickerDark = Color(0xFF7A5428), gap = Color(0xFF4A2E14),
                        cloth = Color(0xFFF7F2EA), check = Color(0xFFD0282E), leather = Color(0xFF6A3A1E),
                    ),
                ),
            ),
        ),
        listOf(
            cauldron("cauldron_green", 0xFF5BE36A, 0xFF1E7A2B),
            cauldron("cauldron_purple", 0xFFB06CF0, 0xFF5A2A8A),
        ),
        listOf(
            colour(GiftBoxDiceCupStyle("christmas_red", ChristmasRedPaper, ChristmasGoldRibbon)),
            colour(
                GiftBoxDiceCupStyle("christmas_blue", CupPalette(Color(0xFF173A70), Color(0xFF5A8AD8), Color(0xFF2C5CA8), Color(0xFFBFD6F7), Color(0xFF081428)), Color(0xFFE8E8EE)),
            ),
        ),
        listOf(
            colour(
                UrnDiceCupStyle("urn_terracotta", UrnPalette(Color(0xFFC4673A), Color(0xFFE8956A), Color(0xFF6E2E14), Color(0xFF1E1410), Color(0xFF1A0D07))),
            ),
            colour(
                UrnDiceCupStyle("urn_bronze", UrnPalette(Color(0xFF9A6B32), Color(0xFFE0B070), Color(0xFF4A3010), Color(0xFF2E6B5A), Color(0xFF140C04))),
            ),
        ),
        listOf(
            colour(
                TreasureChestDiceCupStyle(
                    "treasure_oak",
                    ChestPalette(
                        woodLight = Color(0xFF9A5B2E), woodDark = Color(0xFF5E3317), seam = Color(0xFF3E200D),
                        goldLight = Color(0xFFFFE08A), gold = Color(0xFFD4A437), goldDark = Color(0xFF8C6414),
                        interior = Color(0xFF1E0F05), lining = Color(0xFF7A1424),
                    ),
                ),
            ),
        ),
        listOf(
            colour(GlitterDiceCupStyle("glitter_gold", Color(0xFFD9A030), Color(0xFFFFE08A))),
            colour(GlitterDiceCupStyle("glitter_pink", Color(0xFFE0458F), Color(0xFFF2F2F6))),
        ),
        listOf(
            colour(
                NeonDiceCupStyle(
                    "neon_pink_and_blue",
                    CupPalette(Color(0xFF050506), Color(0xFF3A3A40), Color(0xFF1A1A1E), Color(0xFF3A3A40), Color(0xFF020203)),
                    upper = Color(0xFFFF3FA4),
                    lower = Color(0xFF3FE6FF),
                ),
            ),
        ),
        listOf(
            colour(RgbHaloDiceCupStyle("rgb_halo_rainbow", RgbBlackCup, wave = false)),
            colour(RgbHaloDiceCupStyle("rgb_halo_wave", RgbBlackCup, wave = true)),
            colour(RgbHaloDiceCupStyle("rgb_white_halo_rainbow", RgbWhiteCup, wave = false)),
            colour(RgbHaloDiceCupStyle("rgb_white_halo_wave", RgbWhiteCup, wave = true)),
            colour(RgbLanternDiceCupStyle("rgb_lantern_rainbow", RgbBlackCup, wave = false)),
            colour(RgbLanternDiceCupStyle("rgb_lantern_wave", RgbBlackCup, wave = true)),
            colour(RgbLanternDiceCupStyle("rgb_white_lantern_rainbow", RgbWhiteCup, wave = false)),
            colour(RgbLanternDiceCupStyle("rgb_white_lantern_wave", RgbWhiteCup, wave = true)),
        ),
        listOf(colour(MartiniDiceCupStyle("martini_classic"))),
    ),
)

object DiceMats : StyleCatalog<DiceMat>(
    DiceMatRegistry,
    listOf(
        listOf(
            StyleArt(TrayBlueTop, TrayBlueMat),
            StyleArt(FireTrayTop, FireDiceMat),
        ),
        listOf(
            mat(::LeatherDiceMat, "leather_tan", 0xFF9A6A3C, 0xFF6B4424, 0xFF3E2612, 0xFF2A190B, 0xFFE0C08E, 0xFFE8D2A8),
            mat(::LeatherDiceMat, "leather_oxblood", 0xFF7A2328, 0xFF4E1216, 0xFF2E080B, 0xFF1E0507, 0xFFE0A89A, 0xFFE8C9A0),
            mat(::LeatherDiceMat, "leather_black", 0xFF3A3A3A, 0xFF1C1C1C, 0xFF111111, 0xFF0A0A0A, 0xFF9E9E9E, 0xFFBDBDBD),
        ),
        listOf(
            StyleArt(BarrelTrayTop, BarrelDiceMat),
            // Floorboards in the Wood dice's woods.
            mat(::WoodPlankDiceMat, "wood_oak", 0xFFB07A44, 0xFF7A5028, 0xFF3E2814, 0xFF26180A, 0xFFE0B880, 0xFF4A2E14),
            mat(::WoodPlankDiceMat, "wood_walnut", 0xFF5E3E26, 0xFF3A2414, 0xFF22140A, 0xFF140A04, 0xFFC8A070, 0xFF1E1008),
            mat(::WoodPlankDiceMat, "wood_maple", 0xFFE6CC98, 0xFFC4A46A, 0xFF6A4E2A, 0xFF4A3418, 0xFFFFF0D0, 0xFF9A7444),
        ),
        listOf(
            mat(::OceanDiceMat, "ocean_lagoon", 0xFF1C8FA3, 0xFF0D5A6E, 0xFF0A4757, 0xFF06323E, 0xFF7FD6E6, 0xFFCFF8FF),
            mat(::OceanDiceMat, "ocean_deep", 0xFF1E4F8C, 0xFF0C2652, 0xFF0A1E40, 0xFF06142C, 0xFF6E9CD6, 0xFFA8CCFF),
        ),
        listOf(
            mat(::CasinoDiceMat, "casino_green", 0xFF1E6B3A, 0xFF0E3F22, 0xFF0A2E18, 0xFF061F10, 0xFFE0C45A, 0xFFD4AF37),
            mat(::CasinoDiceMat, "casino_purple", 0xFF4A2266, 0xFF2A1040, 0xFF1E0A30, 0xFF12061E, 0xFFE0C45A, 0xFFD4AF37),
        ),
        listOf(
            mat(::StarryDiceMat, "starry_midnight", 0xFF152550, 0xFF070E24, 0xFF0A1330, 0xFF050A1C, 0xFF8FA8E8, 0xFFFFFFFF),
        ),
        listOf(
            mat(::GinghamDiceMat, "gingham_red", 0xFFA8322D, 0xFF7E211D, 0xFF4A1310, 0xFF330B09, 0xFFF2B8B0, 0xFFFFFFFF),
            mat(::GinghamDiceMat, "gingham_blue", 0xFF2F5C8F, 0xFF1E3E63, 0xFF122640, 0xFF0B182B, 0xFFB8D0F0, 0xFFFFFFFF),
            mat(::GinghamDiceMat, "gingham_green", 0xFF3E7D4A, 0xFF27562F, 0xFF16331C, 0xFF0E2312, 0xFFBFE3C6, 0xFFFFFFFF),
        ),
        listOf(
            stoneMat("stone_granite", StoneKind.GRANITE, 0xFFB8AEA8, 0xFF7A716D, 0xFF3A3532),
            stoneMat("stone_slate", StoneKind.SLATE, 0xFF5A6470, 0xFF2E353D, 0xFF1A1E22),
            stoneMat("stone_sandstone", StoneKind.SANDSTONE, 0xFFE2BE8C, 0xFFB0824E, 0xFF6A4A2A),
            stoneMat("stone_limestone", StoneKind.LIMESTONE, 0xFFEDE6D3, 0xFFC9BFA4, 0xFF7A7262),
            stoneMat("stone_basalt", StoneKind.BASALT, 0xFF45464A, 0xFF1A1B1E, 0xFF0A0A0C),
            stoneMat("stone_white_marble", null, 0xFFF4F2EE, 0xFFD9D5CE, 0xFF8E8A84, vein = 0xFF8E8A84),
            stoneMat("stone_black_marble", null, 0xFF3A3A3E, 0xFF141416, 0xFF050506, vein = 0xFFD8D8D8),
        ),
        listOf(
            mat(::GiftWrapDiceMat, "christmas_red", 0xFFB8282E, 0xFF8E1A20, 0xFF5A0E12, 0xFF3E080B, 0xFFF2C14E, 0xFFFFFFFF),
            mat(::GiftWrapDiceMat, "christmas_kraft", 0xFFC49A6C, 0xFFA77D50, 0xFF6A4A2A, 0xFF4A321C, 0xFFD32F2F, 0xFFF7EDE0),
        ),
        listOf(
            mat(::CircuitDiceMat, "circuit_green", 0xFF16503A, 0xFF0B3325, 0xFF07231A, 0xFF041710, 0xFF6BE0A6, 0xFFD9A84A),
            mat(::CircuitDiceMat, "circuit_black", 0xFF1E2024, 0xFF111215, 0xFF0A0B0D, 0xFF050506, 0xFF6A6E78, 0xFF3FE6FF),
        ),
        listOf(
            mat(::HoneycombDiceMat, "bestagons_honeycomb", 0xFFF2A81E, 0xFF9A5A06, 0xFF5A3A0E, 0xFF3E2806, 0xFFFFD27A, 0xFFE8B850),
            mat(::HexTileDiceMat, "bestagons_honey", 0xFFE6A53A, 0xFFB87818, 0xFF5A3A0E, 0xFF3E2806, 0xFFFFD27A, 0xFF3E2806),
            mat(::HexTileDiceMat, "bestagons_slate", 0xFF5C6B78, 0xFF3A4550, 0xFF232A31, 0xFF151A1F, 0xFFAEBBC6, 0xFF1C2228),
        ),
        listOf(
            glitterMat("glitter_gold", 0xFF2A2230, 0xFF141018, 0xFFE8C66A, 0xFFF2D27A),
            glitterMat("glitter_silver", 0xFF34383E, 0xFF1A1C20, 0xFFD8DDE3, 0xFFE8ECF2),
            glitterMat("glitter_rose_gold", 0xFF4A2A2E, 0xFF261417, 0xFFF0B8A8, 0xFFF6C8B8),
            glitterMat("glitter_pink", 0xFF6A1E48, 0xFF3E0E2A, 0xFFFF9AD0, 0xFFFFB8E0),
            glitterMat("glitter_purple", 0xFF3E1E6A, 0xFF1E0E3A, 0xFFC8A0FF, 0xFFD8B8FF),
            glitterMat("glitter_blue", 0xFF16306A, 0xFF0A1838, 0xFF9AC8FF, 0xFFB8DCFF),
            glitterMat("glitter_emerald", 0xFF0E4A34, 0xFF06261A, 0xFF8AF0C0, 0xFFA8F6D0),
        ),
        listOf(
            neonMat("neon_pink_and_blue", 0xFF2A1C24, 0xFF140C12, outer = 0xFFFF3FA4, inner = 0xFF3FE6FF),
            neonMat("neon_green_and_purple", 0xFF141A3A, 0xFF080B1E, outer = 0xFF39FF88, inner = 0xFFB45CFF),
            neonMat("neon_orange_and_teal", 0xFF2A1640, 0xFF140A22, outer = 0xFFFF8A1F, inner = 0xFF2EE6D0),
        ),
        listOf(
            rgbMat("rgb_rainbow", RgbBlackMat, wave = false),
            rgbMat("rgb_wave", RgbBlackMat, wave = true),
            rgbMat("rgb_white_rainbow", RgbWhiteMat, wave = false),
            rgbMat("rgb_white_wave", RgbWhiteMat, wave = true),
        ),
        listOf(
            mat(::WireframeDiceMat, "wireframe_cyan", 0xFF0C1A20, 0xFF060E12, 0xFF03080A, 0xFF010304, 0xFF3FE6FF, 0xFF3FE6FF),
            mat(::WireframeDiceMat, "wireframe_green", 0xFF0C1A10, 0xFF060E08, 0xFF030804, 0xFF010301, 0xFF4CFF7A, 0xFF4CFF7A),
        ),
    ),
)

object TableBackgrounds : StyleCatalog<TableBackground>(
    TableBackgroundRegistry,
    listOf(
        listOf(
            StyleArt(FeltNavyTop, MidnightFeltBackground),
            StyleArt(FireBackgroundTop, FireTableBackground),
            StyleArt(BarrelBackgroundTop, BarrelTableBackground),
        ),
        listOf(
            leatherBackground("leather_tan", 0xFF6B4424, 0xFF3E2612, 0xFFE0C08E),
            leatherBackground("leather_oxblood", 0xFF5A1A1E, 0xFF2E080B, 0xFFE0A89A),
            leatherBackground("leather_black", 0xFF2A2A2A, 0xFF111111, 0xFF9E9E9E),
        ),
        listOf(
            background(::PlanksBackground, "planks_oak", 0xFF7A5534, 0xFF5A3C22),
            background(::PlanksBackground, "planks_walnut", 0xFF4A3322, 0xFF2C1D12),
        ),
        // The waves roll; the lagoon is still.
        listOf(
            StyleArt(Color(0xFF1A4A7A), OceanWavesBackground("ocean_waves", BackgroundPalette(Color(0xFF1A4A7A), Color(0xFF0A1E3C), Color(0xFFBFE6FF)))),
            StyleArt(Color(0xFF156E80), OceanLagoonBackground("ocean_lagoon", BackgroundPalette(Color(0xFF156E80), Color(0xFF083A48), Color(0xFFCFF8FF)))),
        ),
        listOf(
            background(::SpotlightBackground, "spotlight_purple", 0xFF5A3290, 0xFF1A0A33),
            background(::SpotlightBackground, "spotlight_navy", 0xFF2A5590, 0xFF0A1A33),
            background(::SpotlightBackground, "spotlight_green", 0xFF2E7A48, 0xFF0A2A16),
        ),
        listOf(
            background(::PinstripeBackground, "pinstripe_charcoal", 0xFF2E3136, 0xFF1C1E22),
            background(::PinstripeBackground, "pinstripe_navy", 0xFF1E2E4C, 0xFF0F1A30),
        ),
        listOf(StyleArt(Color(0xFF2A1A1C), BrickWallBackground("brick_neon", BackgroundPalette(Color(0xFF2A1A1C), Color(0xFF140C0E), Color(0xFFFF3FA4))))),
        listOf(
            background(::SunburstBackground, "sunburst_crimson", 0xFF6A1218, 0xFF2E0508),
            background(::SunburstBackground, "sunburst_amber", 0xFF7A4A0E, 0xFF331E04),
        ),
        listOf(
            background(::StarryBackground, "starry_midnight", 0xFF12224A, 0xFF060C22),
        ),
        listOf(
            background(::GinghamBackground, "gingham_red", 0xFF8E2A26, 0xFF6A1C19),
            background(::GinghamBackground, "gingham_blue", 0xFF264C78, 0xFF183352),
            background(::GinghamBackground, "gingham_green", 0xFF33693D, 0xFF214A28),
        ),
        listOf(
            stoneBackground("stone_slate", StoneKind.SLATE, 0xFF3C4650, 0xFF1C2228, 0xFF0E1114),
            stoneBackground("stone_granite", StoneKind.GRANITE, 0xFF6E6560, 0xFF3A3532, 0xFF1E1B19),
            stoneBackground("stone_black_marble", null, 0xFF3A3A3E, 0xFF141416, 0xFF050506, vein = 0xFFD8D8D8),
        ),
        listOf(
            background(::GiftWrapBackground, "christmas_green", 0xFF1E5A34, 0xFF0E3A1E),
            background(::GiftWrapBackground, "christmas_red", 0xFF7A1A20, 0xFF4E0E12),
        ),
        // The Meadow dice's grass, wildflowers and river, across the whole table.
        listOf(
            StyleArt(
                Color(0xFF4E8A2A),
                MeadowBackground("meadow_green", BackgroundPalette(Color(0xFF3E7A2A), Color(0xFF1F4A1B))),
            ),
        ),
        listOf(
            circuitBackground("circuit_green", 0xFF0F3A2B, 0xFF06201A, 0xFFD9A84A),
            circuitBackground("circuit_black", 0xFF16181B, 0xFF08090B, 0xFF3FE6FF),
        ),
        listOf(
            // Real comb, wax and honey, dimmed well back - to go with the Bestagon dice and the Honeycomb mat.
            StyleArt(
                Color(0xFFD9A040),
                HoneycombCombBackground("bestagons_honey", BackgroundPalette(Color(0xFF3A2406), Color(0xFF1E1203), Color(0xFFD9A040))),
            ),
            background(::HoneycombBackground, "bestagons_charcoal", 0xFF2A2D33, 0xFF16181C),
            background(::HoneycombBackground, "bestagons_indigo", 0xFF262A5A, 0xFF12142E),
        ),
        listOf(
            glitterBackground("glitter_gold", 0xFF241C2C, 0xFF0E0A12, 0xFFE8C66A),
            glitterBackground("glitter_silver", 0xFF2A2D32, 0xFF111316, 0xFFE8ECF2),
            glitterBackground("glitter_rose_gold", 0xFF3A2024, 0xFF1A0C0E, 0xFFF6C8B8),
            glitterBackground("glitter_pink", 0xFF4E1636, 0xFF22081A, 0xFFFFB8E0),
            glitterBackground("glitter_purple", 0xFF2E1650, 0xFF12082A, 0xFFD8B8FF),
            glitterBackground("glitter_blue", 0xFF122650, 0xFF060E26, 0xFFB8DCFF),
            glitterBackground("glitter_emerald", 0xFF0C3A2A, 0xFF041A12, 0xFFA8F6D0),
        ),
        listOf(
            neonBackground("neon_pink_and_blue", 0xFF1E1030, 0xFF0A0614, outer = 0xFFFF3FA4, inner = 0xFF3FE6FF),
            neonBackground("neon_green_and_purple", 0xFF0E1838, 0xFF050A1C, outer = 0xFF39FF88, inner = 0xFFB45CFF),
            neonBackground("neon_orange_and_teal", 0xFF1E1236, 0xFF0A0618, outer = 0xFFFF8A1F, inner = 0xFF2EE6D0),
        ),
        listOf(
            rgbLights("rgb_rainbow", RgbEffect.RAINBOW),
            rgbLights("rgb_wave", RgbEffect.WAVE),
            rgbLights("rgb_ripple", RgbEffect.RIPPLE),
            rgbLights("rgb_swirl", RgbEffect.SWIRL),
            rgbLights("rgb_starlight", RgbEffect.STARLIGHT),
        ),
        listOf(StyleArt(Color(0xFF14141E), GlitchBackground("glitch_screen", BackgroundPalette(Color(0xFF14141E), Color(0xFF07070C), Color(0xFF9FB4FF))))),
        listOf(StyleArt(SurfaceContainerHigh, FloatingDiceBackground)),
    ),
)

/**
 * The frame round the player whose turn it is. Every one is drawn in the player's own colour, so a
 * family's "colours" here are its variants - different shapes, not different colours - each shown on
 * the Styles screen in player 1's default colour.
 */
object ScoreFrames : StyleCatalog<ScoreFrame>(
    ScoreFrameRegistry,
    listOf(
        listOf(frame(ScoreFrameArt.classic)),
        listOf(frame(ScoreFrameArt.rose), frame(ScoreFrameArt.daisy), frame(ScoreFrameArt.vine)),
        listOf(frame(ScoreFrameArt.chain), frame(ScoreFrameArt.rope)),
        listOf(frame(ScoreFrameArt.decoFan), frame(ScoreFrameArt.decoStepped)),
        listOf(frame(ScoreFrameArt.tribalSwirl), frame(ScoreFrameArt.tribalFlame)),
        listOf(frame(ScoreFrameArt.laurel), frame(ScoreFrameArt.olive)),
        listOf(frame(ScoreFrameArt.greekKey), frame(ScoreFrameArt.greekWaves)),
        listOf(frame(ScoreFrameArt.celticKnot)),
        listOf(frame(ScoreFrameArt.pixel)),
        listOf(frame(ScoreFrameArt.neon), frame(ScoreFrameArt.neonDouble)),
        listOf(frame(ScoreFrameArt.bulbs), frame(ScoreFrameArt.diamonds)),
        listOf(
            frame(ScoreFrameArt.stars),
            frame(ScoreFrameArt.sparkles),
            frame(ScoreFrameArt.constellation),
        ),
    ),
)

/** A frame's variant: its swatch is the colour its tile shows it in, as every variant is drawn in the player's colour. */
private fun frame(style: ScoreFrame): StyleArt<ScoreFrame> = StyleArt(ScoreFramePreviewColour, style)

// The Egg dice's shells - each also the other's pips.
private val EggWhiteShell = Color(0xFFFBF8F1)
private val EggBrownShell = Color(0xFFD9A27A)

// The Mahjong dice's tiles: an ivory face over a jade back.
private val MahjongFace = Color(0xFFFBF7EC)
private val MahjongFaceShade = Color(0xFFE6DFCB)
private val MahjongBack = Color(0xFF2E7D4F)

// The Christmas cup's red paper and gold ribbon, which the Gift dice are wrapped in too.
private val ChristmasRedPaper = CupPalette(Color(0xFF7A1418), Color(0xFFE85A5E), Color(0xFFC8282E), Color(0xFFFFFFFF), Color(0xFF2A0608))
private val ChristmasGoldRibbon = Color(0xFFF2C14E)

// Shorthands for the entries above. A colour's swatch - its dot on the Styles screen - is the most
// representative colour of its palette.

private fun <T> colour(style: T): StyleArt<T> where T : TableArt, T : Swatched = StyleArt(style.swatch, style)

private fun <T : DiceCupStyle> cup(
    create: (String, CupPalette) -> T,
    id: String,
    dark: Long,
    light: Long,
    mid: Long,
    accent: Long,
    interior: Long,
): StyleArt<DiceCupStyle> =
    StyleArt(Color(mid), create(id, CupPalette(Color(dark), Color(light), Color(mid), Color(accent), Color(interior))))

private val FlowerpotTerracotta = CupPalette(
    Color(0xFF7A3418), Color(0xFFE08A5C), Color(0xFFC0643A), Color(0xFFC0643A), Color(0xFF2E1E14),
)

/**
 * The Flowerpot's colours: the pot that grows its plant through a game (the one everyone has), then
 * one pot for each stage of the plant, held there - secret, all of them, until Greenfingers is
 * earned. Each shows the plant's own colour on the Styles screen, since the pot is the same.
 */
private fun flowerpotColours(): List<StyleArt<DiceCupStyle>> {
    fun stage(id: String, swatch: Color, stage: Int) =
        StyleArt(swatch, FlowerpotDiceCupStyle(id, FlowerpotTerracotta, fixedStage = stage))
    return listOf(
        StyleArt(FlowerpotTerracotta.mid, FlowerpotDiceCupStyle("flowerpot_terracotta", FlowerpotTerracotta)),
        stage("flowerpot_seedling", FlowerpotLeaf, 1),
        stage("flowerpot_bud", FlowerpotStem, 2),
        stage("flowerpot_opening", SunflowerPetalShade, 3),
        stage("flowerpot_sunflower", SunflowerPetal, FLOWERPOT_FULL_BLOOM),
    )
}

// Cauldrons share their iron and differ only in their brew, which is also the colour the Styles
// screen shows for them.
private fun cauldron(id: String, brew: Long, deepBrew: Long): StyleArt<DiceCupStyle> =
    StyleArt(
        Color(brew),
        CauldronDiceCupStyle(id, CupPalette(Color(0xFF151618), Color(0xFF5A5E63), Color(0xFF2C2F33), Color(brew), Color(deepBrew))),
    )

private fun matPalette(top: Long, bottom: Long, slotTop: Long, slotBottom: Long, slotBorder: Long, detail: Long) =
    MatPalette(Color(top), Color(bottom), Color(slotTop), Color(slotBottom), Color(slotBorder), Color(detail))

private fun mat(
    create: (String, MatPalette) -> DiceMat,
    id: String,
    top: Long,
    bottom: Long,
    slotTop: Long,
    slotBottom: Long,
    slotBorder: Long,
    detail: Long,
): StyleArt<DiceMat> = StyleArt(Color(top), create(id, matPalette(top, bottom, slotTop, slotBottom, slotBorder, detail)))

/** A Glitter mat: [flakes] of glitter over a [top]-to-[bottom] tray, its slots sunk darker and ringed in [border]. */
private fun glitterMat(id: String, top: Long, bottom: Long, border: Long, flakes: Long): StyleArt<DiceMat> =
    StyleArt(
        Color(flakes),
        GlitterDiceMat(id, MatPalette(Color(top), Color(bottom), lerp(Color(bottom), Color.Black, 0.45f), lerp(Color(bottom), Color.Black, 0.7f), Color(border), Color(flakes))),
    )

/**
 * A Neon mat: a plain [top]-to-[bottom] tray framed by an [outer] and an [inner] tube - its slots sunk
 * darker and ringed in the inner tube's colour. Shown by its outer tube's colour.
 */
private fun neonMat(id: String, top: Long, bottom: Long, outer: Long, inner: Long): StyleArt<DiceMat> =
    StyleArt(
        Color(outer),
        NeonDiceMat(id, MatPalette(Color(top), Color(bottom), lerp(Color(bottom), Color.Black, 0.35f), lerp(Color(bottom), Color.Black, 0.65f), Color(inner), Color(outer))),
    )

/**
 * A Stone mat: flagstones of [kind] (or marble veined in [vein], with no [kind]) from [light] to
 * [dark], set in [grout] - its slots sunk darker and ringed a touch lighter than the stone.
 */
private fun stoneMat(id: String, kind: StoneKind?, light: Long, dark: Long, grout: Long, vein: Long = grout): StyleArt<DiceMat> =
    StyleArt(
        Color(light),
        StoneDiceMat(
            id,
            MatPalette(Color(light), Color(dark), lerp(Color(dark), Color.Black, 0.45f), lerp(Color(dark), Color.Black, 0.7f), lerp(Color(light), Color.White, 0.3f), Color(grout)),
            kind,
            Color(vein),
        ),
    )

/** A Stone background: flags of [kind]'s stone (or marble veined in [vein], with no [kind]) from [top] to [bottom], set in [grout]. */
private fun stoneBackground(id: String, kind: StoneKind?, top: Long, bottom: Long, grout: Long, vein: Long = grout): StyleArt<TableBackground> =
    StyleArt(Color(top), StoneBackground(id, BackgroundPalette(Color(top), Color(bottom), Color(grout)), kind, Color(vein)))

/** A Circuit background: a board from [top] to [bottom], its traces in [traces]. */
private fun circuitBackground(id: String, top: Long, bottom: Long, traces: Long): StyleArt<TableBackground> =
    StyleArt(Color(traces), CircuitBackground(id, BackgroundPalette(Color(top), Color(bottom), Color(traces))))

/** A Neon background: a [top]-to-[bottom] night, its grid and glow in [outer] and the tube along the horizon in [inner]. Shown by its grid's colour. */
private fun neonBackground(id: String, top: Long, bottom: Long, outer: Long, inner: Long): StyleArt<TableBackground> =
    StyleArt(Color(outer), NeonGridBackground(id, BackgroundPalette(Color(top), Color(bottom), Color(outer)), Color(inner)))

/** A Leather background: a [top]-to-[bottom] hide stitched in [thread]. */
private fun leatherBackground(id: String, top: Long, bottom: Long, thread: Long): StyleArt<TableBackground> =
    StyleArt(Color(top), LeatherBackground(id, BackgroundPalette(Color(top), Color(bottom), Color(thread))))

/** A Glitter background: [flakes] of glitter over a [top]-to-[bottom] gradient. */
private fun glitterBackground(id: String, top: Long, bottom: Long, flakes: Long): StyleArt<TableBackground> =
    StyleArt(Color(flakes), GlitterBackground(id, BackgroundPalette(Color(top), Color(bottom), Color(flakes))))

private fun background(
    create: (String, BackgroundPalette) -> TableBackground,
    id: String,
    top: Long,
    bottom: Long,
): StyleArt<TableBackground> = StyleArt(Color(top), create(id, BackgroundPalette(Color(top), Color(bottom))))

// The RGB cup, mat and background: anodised black or white, lit by LEDs in the RGB dice's colours.
private val RgbBlackCup = CupPalette(Color(0xFF050507), Color(0xFF3A3D46), Color(0xFF17181C), Color(0xFF17181C), Color(0xFF020203))
private val RgbWhiteCup = CupPalette(Color(0xFF9EA1A8), Color(0xFFFFFFFF), Color(0xFFE3E4E8), Color(0xFFE3E4E8), Color(0xFF1C1C22))
private val RgbBlackMat = MatPalette(Color(0xFF15151A), Color(0xFF0A0A0D), Color(0xFF060608), Color(0xFF020203), Color(0xFF3A3C44), Color.White)
private val RgbWhiteMat = MatPalette(Color(0xFFF2F2F4), Color(0xFFD9DADF), Color(0xFFC6C7CC), Color(0xFFB2B3B8), Color(0xFFFFFFFF), Color.White)
private val RgbPanel = BackgroundPalette(Color(0xFF0E0E12), Color(0xFF050507))

/** An RGB mat: lit a colour at a time, or (a [wave]) with the colours chasing round it. Shown in the RGB dice's swatch, as they all are. */
private fun rgbMat(id: String, palette: MatPalette, wave: Boolean): StyleArt<DiceMat> =
    StyleArt(Color(0xFFFF3B6B), RgbDiceMat(id, palette, wave))

/** An RGB light panel, lit with a keyboard's lighting [effect]. */
private fun rgbLights(id: String, effect: RgbEffect): StyleArt<TableBackground> =
    StyleArt(Color(0xFFFF3B6B), RgbLightBackground(id, RgbPanel, effect))
