package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.FLOWERPOT_FULL_BLOOM
import net.zodac.dicefive.model.DieColour
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
import net.zodac.dicefive.ui.theme.RabbitFur
import net.zodac.dicefive.ui.theme.SunflowerPetal
import net.zodac.dicefive.ui.theme.SurfaceContainerHigh
import net.zodac.dicefive.ui.theme.SunflowerPetalShade
import net.zodac.dicefive.ui.theme.IvoryDiceBottom
import net.zodac.dicefive.ui.theme.IvoryDiceTop
import net.zodac.dicefive.ui.theme.TrayBlueTop

/** Table art that knows its own most representative colour - its dot on the Styles screen. */
interface Swatched {
    val swatch: Color
}

/** One colour of a [StyleFamily]: the concrete piece of art it resolves to, and how it's named and shown. */
data class StyleColour<out T : TableArt>(
    val name: String,
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
    val name: String,
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
 * [noun] is what one of the category is called in a sentence - "the 'Irish' dice style".
 */
open class StyleCatalog<T : TableArt>(val noun: String, val families: List<StyleFamily<T>>) {
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
    StyleColour(colour.name.lowercase().replaceFirstChar { it.uppercase() }, colour.palette.swatch, ColouredClassicDiceStyle(id, colour.palette))

/** Every category's catalog, in the Styles screen's order. */
val StyleCatalogs: List<StyleCatalog<*>> by lazy { listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds) }

object DiceStyles : StyleCatalog<DiceStyle>(
    "dice",
    listOf(
        StyleFamily(
            "Classic",
            listOf(
                StyleColour("Ivory", IvoryDiceTop, IvoryDiceStyle),
                classicDie("classic_red", DieColour.RED),
                classicDie("classic_yellow", DieColour.YELLOW),
                classicDie("classic_blue", DieColour.BLUE),
                StyleColour("Oak", BarrelDiceTop, BarrelDiceStyle),
            ),
        ),
        StyleFamily(
            "Frosted",
            listOf(
                colour("Ice", FrostedDiceStyle("frosted_ice", Color(0xFFE3F4FF), Color(0xFFA9D3EE), Color(0xFF1D4E6E))),
                colour("White", FrostedDiceStyle("frosted_white", Color(0xFFFAFBFC), Color(0xFFD5DADF), Color(0xFF3A4550))),
            ),
            unlock = AchievementCount(23),
        ),
        StyleFamily(
            "Metal",
            listOf(
                // Gold and bronze dice would swallow the usual gold held ring, so theirs is white.
                colour(
                    "Gold",
                    MetalDiceStyle(
                        "metal_gold",
                        listOf(Color(0xFFFFE9A3), Color(0xFFD4A437), Color(0xFFFFE08A), Color(0xFF9C7219)),
                        Color(0xFF5C420B),
                        heldRing = Color.White,
                    ),
                ),
                colour(
                    "Silver",
                    MetalDiceStyle(
                        "metal_silver",
                        listOf(Color(0xFFF2F4F6), Color(0xFFAEB5BB), Color(0xFFE6EAED), Color(0xFF7C848B)),
                        Color(0xFF33393E),
                    ),
                ),
                colour(
                    "Bronze",
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
            "Retro",
            listOf(
                colour("Green", RetroDiceStyle("retro_green", Color(0xFF9BBC0F), Color(0xFF306230), Color(0xFF0F380F))),
                colour(
                    "Amber",
                    RetroDiceStyle("retro_amber", Color(0xFFFFB000), Color(0xFF9A5B00), Color(0xFF3A2400), heldRing = Color.White),
                ),
                colour("Blue", RetroDiceStyle("retro_blue", Color(0xFF9CC8F5), Color(0xFF2E5C99), Color(0xFF0D2547))),
                colour("Red", RetroDiceStyle("retro_red", Color(0xFFF29C94), Color(0xFF9E2A24), Color(0xFF3D0B08))),
            ),
            unlock = AchievementCount(2),
        ),
        StyleFamily(
            "Numeral",
            listOf(
                colour("Digits", NumeralDiceStyle("numeral_white", Color(0xFFFBFBFB), Color(0xFFD6D6D6), Color(0xFF1E1E1E))),
                colour("Red", NumeralDiceStyle("numeral_red", Color(0xFFE53935), Color(0xFF8E0E0E), Color.White)),
                colour("Blue", NumeralDiceStyle("numeral_blue", Color(0xFF2F7FE0), Color(0xFF0B3A80), Color.White)),
            ),
            unlock = AchievementCount(43),
        ),
        StyleFamily(
            "Non-English",
            listOf(
                colour(
                    "Roman",
                    NumeralDiceStyle("numeral_roman", Color(0xFFF3E9D2), Color(0xFFD8C8A0), Color(0xFF7A1F1F), NumeralSystem.ROMAN),
                ),
                colour(
                    "Roman Blue",
                    NumeralDiceStyle("numeral_roman_blue", Color(0xFF2F4A73), Color(0xFF17263F), Color(0xFFF3E9D2), NumeralSystem.ROMAN),
                ),
                colour(
                    "Arabic Green",
                    NumeralDiceStyle("numeral_arabic_green", Color(0xFF1F5E3A), Color(0xFF0F3320), Color(0xFFF3EFE0), NumeralSystem.EASTERN_ARABIC),
                ),
                colour(
                    "Arabic",
                    NumeralDiceStyle("numeral_arabic", Color(0xFF3A3A3A), Color(0xFF121212), Color(0xFFE8C66A), NumeralSystem.EASTERN_ARABIC),
                ),
                colour(
                    "Japanese",
                    NumeralDiceStyle("numeral_japanese", Color(0xFFFBFBFB), Color(0xFFE3E3E3), Color(0xFFBC002D), NumeralSystem.JAPANESE),
                ),
                colour(
                    // Black urushi lacquer with maki-e gold.
                    "Japanese Black",
                    NumeralDiceStyle("numeral_japanese_black", Color(0xFF1A1714), Color(0xFF050403), Color(0xFFD4AF37), NumeralSystem.JAPANESE),
                ),
            ),
            unlock = AchievementCount(50),
        ),
        StyleFamily(
            "Text",
            listOf(
                colour(
                    "Ivory Serif",
                    NumeralDiceStyle(
                        "text_serif", Color(0xFFF7F0DC), Color(0xFFDCCFA8), Color(0xFF1F2F5C), NumeralSystem.ENGLISH,
                        font = FontFamily.Serif, size = 0.26f,
                    ),
                ),
                colour(
                    "Green Mono",
                    NumeralDiceStyle(
                        "text_mono", Color(0xFF1A1F1A), Color(0xFF080B08), Color(0xFF4CE07A), NumeralSystem.ENGLISH,
                        font = FontFamily.Monospace, size = 0.2f,
                    ),
                ),
                colour(
                    "Purple Script",
                    NumeralDiceStyle(
                        "text_script", Color(0xFF6A3FA0), Color(0xFF34195C), Color(0xFFFFD86B), NumeralSystem.ENGLISH,
                        font = FontFamily.Cursive, size = 0.32f,
                    ),
                ),
                colour(
                    "Red Block",
                    NumeralDiceStyle(
                        "text_block", Color(0xFFE53935), Color(0xFF8E0E0E), Color.White, NumeralSystem.ENGLISH_CAPS,
                        font = FontFamily.SansSerif,
                    ),
                ),
                colour(
                    "Teal Plain",
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
            "Maths",
            listOf(
                colour("White", MathsDiceStyle("maths_white", IvoryDiceTop, IvoryDiceBottom, Color.Black)),
                colour("Black", MathsDiceStyle("maths_black", Color(0xFF2E2E31), Color(0xFF0E0E0F), Color.White)),
                // A chalkboard.
                colour("Green", MathsDiceStyle("maths_green", Color(0xFF2F6B45), Color(0xFF173D26), Color.White)),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.THE_SOLUTION),
        ),
        StyleFamily(
            "Gems",
            listOf(
                colour("Ivory", GemDiceStyle("gems_ivory", IvoryDiceTop, IvoryDiceBottom)),
                colour("Black", GemDiceStyle("gems_black", Color(0xFF2E2E31), Color(0xFF0E0E0F))),
            ),
            unlock = AchievementCount(38),
        ),
        StyleFamily(
            "LCD",
            listOf(
                colour("Neon", LcdDiceStyle("lcd_neon", Color(0xFF15181D), Color(0xFF07090D), Color(0xFF3FD7FF), glow = true)),
                colour("White", LcdDiceStyle("lcd_white", Color(0xFFF4F4F0), Color(0xFFE2E4DE), Color(0xFF151515), glow = false)),
            ),
            unlock = AchievementCount(49),
        ),
        StyleFamily(
            "D20",
            listOf(
                colour("White", D20DiceStyle("d20_white", Color(0xFFFBFBF8), Color(0xFFB9BCC2), Color(0xFF1A1A1A))),
                // The brand's own pairing: the felt blue with the gold the board uses for "press this" -
                // so the held ring is white instead, not lost against gold numbers.
                colour("Blue", D20DiceStyle("d20_blue", Color(0xFF3A6BB0), Color(0xFF14315C), GoldAccent, heldRing = Color.White)),
            ),
            unlock = AchievementCount(29),
        ),
        StyleFamily(
            "Googly",
            listOf(
                colour("Ivory", GooglyDiceStyle("googly_ivory", IvoryDiceTop, IvoryDiceBottom, DicePipColor)),
                colour(
                    "Black",
                    GooglyDiceStyle("googly_black", Color(0xFF3A3A3E), Color(0xFF141416), Color.White),
                ),
                // Secret: only offered once Big Fan is earned.
                GooglyDiceStyle("googly_blue", Color(0xFF3A6BB0), Color(0xFF14315C), GoldAccent, socket = GoldAccent).let {
                    StyleColour("Blue", it.swatch, it, secretAchievement = Achievement.BIG_FAN)
                },
            ),
            unlock = AchievementCount(31),
        ),
        StyleFamily(
            "Misprint",
            listOf(
                colour("Pencil", MisprintDiceStyle("misprint_pencil", Color(0xFFFAF7F0), Color(0xFF3A3A3A), seed = 1)),
                colour("Blueprint", MisprintDiceStyle("misprint_blueprint", Color(0xFF1F4E8C), Color(0xFFEAF2FF), seed = 2)),
            ),
            unlock = AchievementCount(48),
        ),
        StyleFamily(
            "Multicolour",
            listOf(
                colour("Tricolour", TricolourStripedDiceStyle),
                colour("Rainbow", RainbowStripedDiceStyle),
                // Secret: only offered once Luck of the Irish is earned.
                StyleColour("Irish", IrishFlagDiceStyle.swatch, IrishFlagDiceStyle, secretAchievement = Achievement.LUCK_OF_THE_IRISH),
            ),
            unlock = AchievementCount(36),
        ),
        StyleFamily(
            "Cake",
            listOf(
                colour("Vanilla", CakeDiceStyle("cake_vanilla", Color(0xFFFFF6E6), Color(0xFFEBD9BC))),
                // Milk chocolate, light enough for the strawberries to stand out, each on a dollop of cream.
                colour("Chocolate", CakeDiceStyle("cake_chocolate", Color(0xFFA06A45), Color(0xFF70452B), dollop = Color(0xFFFFF6E6))),
                colour("Pink", CakeDiceStyle("cake_pink", Color(0xFFFFD3DE), Color(0xFFEFA9BA))),
            ),
            unlock = AchievementCount(7),
        ),
        StyleFamily(
            "Meadow",
            listOf(colour("Green", MeadowDiceStyle("meadow_green", Color(0xFF7CB342), Color(0xFF4E8A2A)))),
            unlock = AchievementCount(12),
        ),
        StyleFamily(
            "Poker",
            listOf(colour("White", PokerDiceStyle("poker_white", Color.White, Color(0xFFE6E6EA)))),
            unlock = AchievementCount(21),
        ),
        StyleFamily(
            "Obsidian",
            listOf(
                colour(
                    "Lava",
                    ObsidianDiceStyle(
                        "obsidian_lava", Color(0xFF2A2830), Color(0xFF060508),
                        Lava(Color(0xFF1A0A04), Color(0xFFB02A00), Color(0xFFFF6A00), Color(0xFFFFF2A0)),
                    ),
                ),
                colour(
                    "Blue",
                    ObsidianDiceStyle(
                        "obsidian_blue", Color(0xFF2A2830), Color(0xFF060508),
                        Lava(Color(0xFF040A1A), Color(0xFF0040B0), Color(0xFF2E9BFF), Color(0xFFD8F4FF)),
                    ),
                ),
            ),
            unlock = AchievementCount(45),
        ),
        StyleFamily(
            "Mahjong",
            listOf(
                colour("Pinzu", MahjongDiceStyle("mahjong_pinzu", MahjongSuit.PINZU, MahjongFace, MahjongFaceShade, MahjongBack)),
                colour("Manzu", MahjongDiceStyle("mahjong_manzu", MahjongSuit.MANZU, MahjongFace, MahjongFaceShade, MahjongBack)),
                colour("Sozu", MahjongDiceStyle("mahjong_sozu", MahjongSuit.SOZU, MahjongFace, MahjongFaceShade, MahjongBack)),
            ),
            unlock = AchievementCount(33),
        ),
        StyleFamily(
            "Tally",
            listOf(
                colour(
                    "Western",
                    TallyDiceStyle(
                        "tally_western", TallySystem.GATE, TallyPen.CHALK, TallySurface.SLATE,
                        Color(0xFF3A4440), Color(0xFF232A27), Color(0xFFF2F2EA), Color.White,
                    ),
                ),
                colour(
                    "Chinese",
                    TallyDiceStyle(
                        "tally_chinese", TallySystem.ZHENG, TallyPen.BRUSH, TallySurface.RICE_PAPER,
                        Color(0xFFF6EFDC), Color(0xFFE5D9B9), Color(0xFF1A1A1A), Color(0xFFB8A57A),
                    ),
                ),
                colour(
                    "Latin American",
                    TallyDiceStyle(
                        "tally_latin", TallySystem.SQUARE, TallyPen.PENCIL, TallySurface.NOTEBOOK,
                        Color.White, Color(0xFFECEFF3), Color(0xFF3A3A3E), Color(0xFF8FB4E0),
                    ),
                ),
                colour(
                    "Forestry",
                    TallyDiceStyle(
                        "tally_forestry", TallySystem.DOTS, TallyPen.PEN, TallySurface.FIELD_BOOK,
                        Color(0xFFF7E8A4), Color(0xFFE8D47E), Color(0xFF1E2A55), Color(0xFF6A9A6A),
                    ),
                ),
            ),
            unlock = AchievementCount(25),
        ),
        StyleFamily(
            "Stone",
            listOf(
                colour("Granite", StoneDiceStyle("stone_granite", StoneKind.GRANITE, Color(0xFFB8AEA8), Color(0xFF7A716D), Color(0xFF26221F))),
                colour("Slate", StoneDiceStyle("stone_slate", StoneKind.SLATE, Color(0xFF5A6470), Color(0xFF2E353D), Color(0xFF9AA2AC))),
                colour("Sandstone", StoneDiceStyle("stone_sandstone", StoneKind.SANDSTONE, Color(0xFFE2BE8C), Color(0xFFB0824E), Color(0xFF5A3A1E))),
                colour("Limestone", StoneDiceStyle("stone_limestone", StoneKind.LIMESTONE, Color(0xFFEDE6D3), Color(0xFFC9BFA4), Color(0xFF5E5646))),
                colour("Basalt", StoneDiceStyle("stone_basalt", StoneKind.BASALT, Color(0xFF45464A), Color(0xFF1A1B1E), Color(0xFF8E9096))),
                // Marble, once a style of its own: its ids are saved picks, so they stay as they were.
                colour(
                    "White Marble",
                    MarbleDiceStyle("marble_white", Color(0xFFF4F2EE), Color(0xFFD9D5CE), Color(0xFF8E8A84), Color(0xFF222222), seed = 1),
                ),
                colour(
                    "Black Marble",
                    MarbleDiceStyle("marble_black", Color(0xFF3A3A3E), Color(0xFF141416), Color(0xFFD8D8D8), Color(0xFFC8C8CC), seed = 2),
                ),
            ),
            unlock = AchievementCount(47),
        ),
        StyleFamily(
            "Garden",
            listOf(
                colour("Soil", GardenDiceStyle("garden_soil", Color(0xFF6A4A30), Color(0xFF3E2A1A), soil = true)),
            ),
            unlock = AchievementCount(9),
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
    "dice cup",
    listOf(
        // The casino shaker, first so it's the default - "Classic", like every category's default.
        // Its ids still say "casino": they're saved picks, so they stay put if the default moves.
        StyleFamily(
            "Classic",
            listOf(
                StyleColour("Gold", CupRimGold, ClassicGoldDiceCupStyle),
                cup("Black", ::CasinoDiceCupStyle, "casino_black", 0xFF0F0F10, 0xFF4A4A4E, 0xFF26262A, 0xFFD4AF37, 0xFF050505),
                cup("Green", ::CasinoDiceCupStyle, "casino_green", 0xFF0B2A12, 0xFF2F7A45, 0xFF1B5227, 0xFFD4AF37, 0xFF04120A),
            ),
        ),
        StyleFamily(
            "Faceted",
            listOf(
                StyleColour("Green", FacetedCupLitFace, FacetedDiceCupStyle),
                StyleColour("Black", Color(0xFF3A3A3F), BlackFacetedDiceCupStyle),
                StyleColour("Red", FireCupLitFace, FireDiceCupStyle),
            ),
            unlock = AchievementCount(26),
        ),
        // Things for holding things. The wooden barrel was once a style of its own, and its unlock
        // count is the family's, so no one loses it.
        StyleFamily(
            "Containers",
            listOf(
                StyleColour("Barrel", BarrelWood, BarrelDiceCupStyle),
                // Glossy blue enamel, the hoops and rims a shade darker.
                cup("Oil Drum", ::OilDrumDiceCupStyle, "oil_drum_blue", 0xFF0A1250, 0xFF4462D2, 0xFF1A2C9E, 0xFF13217C, 0xFF070D3A),
                // Rust-red steel with bare dark-grey fittings.
                cup("Shipping", ::ShippingContainerDiceCupStyle, "shipping_container_red", 0xFF5E1A12, 0xFFD4604A, 0xFFA83A28, 0xFF3A3A3C, 0xFF1A1A1A),
            ),
            unlock = AchievementCount(57),
        ),
        StyleFamily(
            "Leather",
            listOf(
                cup("Tan", ::LeatherDiceCupStyle, "leather_tan", 0xFF5C3A1C, 0xFFB98553, 0xFF8A5A2E, 0xFFF0DDB8, 0xFF1E1209),
                cup("Black", ::LeatherDiceCupStyle, "leather_black", 0xFF111111, 0xFF4A4A4A, 0xFF262626, 0xFFBDBDBD, 0xFF050505),
                cup("Oxblood", ::LeatherDiceCupStyle, "leather_oxblood", 0xFF3A0A0D, 0xFF92323A, 0xFF641A20, 0xFFE8C9A0, 0xFF160405),
            ),
            unlock = AchievementCount(65),
        ),
        StyleFamily(
            "Glass",
            listOf(
                colour("Clear", GlassDiceCupStyle("glass_clear", Color(0xFFD6ECF7), Color(0xFF8FD3F4))),
                colour("Blue", GlassDiceCupStyle("glass_blue", Color(0xFF5AA8E8), Color(0xFF2F7FE0))),
                colour("Amber", GlassDiceCupStyle("glass_amber", Color(0xFFE8C98A), Color(0xFFE8A030))),
            ),
            unlock = AchievementCount(3),
        ),
        StyleFamily(
            "Tankard",
            listOf(
                cup("Pewter", ::TankardDiceCupStyle, "tankard_pewter", 0xFF4E555A, 0xFFC9D0D4, 0xFF8C959B, 0xFF5E676D, 0xFF1C2023),
                cup("Copper", ::TankardDiceCupStyle, "tankard_copper", 0xFF6B3417, 0xFFE0A07A, 0xFFB8683D, 0xFF7A3C1B, 0xFF2A1308),
            ),
            unlock = AchievementCount(61),
        ),
        StyleFamily(
            "Top Hat",
            listOf(
                cup("Black", ::TopHatDiceCupStyle, "top_hat_black", 0xFF0B0B0C, 0xFF3C3C40, 0xFF1E1E21, 0xFFB71C1C, 0xFF030303),
                cup("Grey", ::TopHatDiceCupStyle, "top_hat_grey", 0xFF3A3A3D, 0xFF9A9AA0, 0xFF6A6A70, 0xFF1A1A1C, 0xFF121214),
                // Secret: only offered once The Magician's Secret is earned. The black hat, with its rabbit always out.
                cup(
                    "Rabbit",
                    { id, palette -> TopHatDiceCupStyle(id, palette, rabbitAlwaysOut = true) },
                    "top_hat_rabbit",
                    0xFF0B0B0C, 0xFF3C3C40, 0xFF1E1E21, 0xFFB71C1C, 0xFF030303,
                ).copy(swatch = RabbitFur, secretAchievement = Achievement.MAGICIANS_SECRET),
            ),
            unlock = AchievementCount(14),
        ),
        // Secret: not on the Styles screen at all until Shaken, Not Tapped is earned.
        StyleFamily(
            "Martini",
            listOf(colour("Classic", MartiniDiceCupStyle("martini"))),
            unlock = StyleUnlock.SpecificAchievement(Achievement.SHAKEN_NOT_TAPPED),
        ),
        StyleFamily(
            "Takeaway",
            listOf(
                cup("White", ::TakeawayDiceCupStyle, "takeaway_white", 0xFFBDB6AA, 0xFFFFFFFF, 0xFFECE7DE, 0xFFA87A4E, 0xFF2A1A10),
                cup("Black", ::TakeawayDiceCupStyle, "takeaway_black", 0xFF111111, 0xFF4A4A4A, 0xFF262626, 0xFFC08A55, 0xFF2A1A10),
            ),
            unlock = AchievementCount(44),
        ),
        StyleFamily(
            "Flowerpot",
            flowerpotColours(),
            unlock = AchievementCount(18),
        ),
        StyleFamily(
            "Cauldron",
            listOf(
                cauldron("Green", "cauldron_green", 0xFF5BE36A, 0xFF1E7A2B),
                cauldron("Purple", "cauldron_purple", 0xFFB06CF0, 0xFF5A2A8A),
            ),
            unlock = AchievementCount(34),
        ),
        StyleFamily(
            "Treasure",
            listOf(
                colour(
                    "Oak",
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
            "Beaker",
            listOf(
                colour("Blue", BeakerDiceCupStyle("beaker_blue", Color(0xFFD6ECF7), Color(0xFF4FC3F7))),
                colour("Green", BeakerDiceCupStyle("beaker_green", Color(0xFFD6ECF7), Color(0xFF7CE08A))),
            ),
            unlock = AchievementCount(67),
        ),
        StyleFamily(
            "Urn",
            listOf(
                colour(
                    "Terracotta",
                    UrnDiceCupStyle("urn_terracotta", UrnPalette(Color(0xFFC4673A), Color(0xFFE8956A), Color(0xFF6E2E14), Color(0xFF1E1410), Color(0xFF1A0D07))),
                ),
                colour(
                    "Bronze",
                    UrnDiceCupStyle("urn_bronze", UrnPalette(Color(0xFF9A6B32), Color(0xFFE0B070), Color(0xFF4A3010), Color(0xFF2E6B5A), Color(0xFF140C04))),
                ),
            ),
            unlock = AchievementCount(17),
        ),
        StyleFamily(
            "Volcano",
            listOf(
                colour(
                    "Basalt",
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
            "Picnic",
            listOf(
                colour(
                    "Wicker",
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
    ),
)

object TableBackgrounds : StyleCatalog<TableBackground>(
    "background",
    listOf(
        StyleFamily(
            "Classic",
            listOf(
                StyleColour("Navy", FeltNavyTop, MidnightFeltBackground),
                StyleColour("Red", FireBackgroundTop, FireTableBackground),
                StyleColour("Brown", BarrelBackgroundTop, BarrelTableBackground),
            ),
        ),
        StyleFamily(
            "Spotlight",
            listOf(
                background("Purple", ::SpotlightBackground, "spotlight_purple", 0xFF5A3290, 0xFF1A0A33),
                background("Navy", ::SpotlightBackground, "spotlight_navy", 0xFF2A5590, 0xFF0A1A33),
                background("Green", ::SpotlightBackground, "spotlight_green", 0xFF2E7A48, 0xFF0A2A16),
            ),
            unlock = AchievementCount(10),
        ),
        StyleFamily(
            "Pinstripe",
            listOf(
                background("Charcoal", ::PinstripeBackground, "pinstripe_charcoal", 0xFF2E3136, 0xFF1C1E22),
                background("Navy", ::PinstripeBackground, "pinstripe_navy", 0xFF1E2E4C, 0xFF0F1A30),
            ),
            unlock = AchievementCount(41),
        ),
        StyleFamily(
            "Planks",
            listOf(
                background("Oak", ::PlanksBackground, "planks_oak", 0xFF7A5534, 0xFF5A3C22),
                background("Walnut", ::PlanksBackground, "planks_walnut", 0xFF4A3322, 0xFF2C1D12),
            ),
            unlock = AchievementCount(5),
        ),
        StyleFamily(
            "Gingham",
            listOf(
                background("Red", ::GinghamBackground, "gingham_red", 0xFF8E2A26, 0xFF6A1C19),
                background("Blue", ::GinghamBackground, "gingham_blue", 0xFF264C78, 0xFF183352),
                background("Green", ::GinghamBackground, "gingham_green", 0xFF33693D, 0xFF214A28),
            ),
            unlock = AchievementCount(30),
        ),
        StyleFamily(
            "Starry",
            listOf(
                background("Midnight", ::StarryBackground, "starry_midnight", 0xFF12224A, 0xFF060C22),
            ),
            unlock = AchievementCount(27),
        ),
        StyleFamily(
            "Honeycomb",
            listOf(
                background("Charcoal", ::HoneycombBackground, "honeycomb_charcoal", 0xFF2A2D33, 0xFF16181C),
                background("Indigo", ::HoneycombBackground, "honeycomb_indigo", 0xFF262A5A, 0xFF12142E),
            ),
            unlock = AchievementCount(6),
        ),
        StyleFamily(
            "Sunburst",
            listOf(
                background("Crimson", ::SunburstBackground, "sunburst_crimson", 0xFF6A1218, 0xFF2E0508),
                background("Amber", ::SunburstBackground, "sunburst_amber", 0xFF7A4A0E, 0xFF331E04),
            ),
            unlock = AchievementCount(39),
        ),
        // Secret: not on the Styles screen at all until Not Those Dice! is earned.
        StyleFamily(
            "Floating Dice",
            listOf(StyleColour("Menu", SurfaceContainerHigh, FloatingDiceBackground)),
            unlock = StyleUnlock.SpecificAchievement(Achievement.NOT_THOSE_DICE),
        ),
    ),
)

object DiceMats : StyleCatalog<DiceMat>(
    "mat",
    listOf(
        StyleFamily(
            "Classic",
            listOf(
                StyleColour("Blue", TrayBlueTop, TrayBlueMat),
                StyleColour("Red", FireTrayTop, FireDiceMat),
            ),
        ),
        StyleFamily(
            "Wood",
            listOf(
                StyleColour("Brown", BarrelTrayTop, BarrelDiceMat),
                StyleColour("Hardwood", Color(0xFFA8703F), HardwoodDiceMat),
            ),
            unlock = AchievementCount(16),
        ),
        StyleFamily(
            "Leather",
            listOf(
                mat("Tan", ::LeatherDiceMat, "leather_tan", 0xFF9A6A3C, 0xFF6B4424, 0xFF3E2612, 0xFF2A190B, 0xFFE0C08E, 0xFFE8D2A8),
                mat("Oxblood", ::LeatherDiceMat, "leather_oxblood", 0xFF7A2328, 0xFF4E1216, 0xFF2E080B, 0xFF1E0507, 0xFFE0A89A, 0xFFE8C9A0),
                mat("Black", ::LeatherDiceMat, "leather_black", 0xFF3A3A3A, 0xFF1C1C1C, 0xFF111111, 0xFF0A0A0A, 0xFF9E9E9E, 0xFFBDBDBD),
            ),
            unlock = AchievementCount(83),
        ),
        StyleFamily(
            "Casino",
            listOf(
                mat("Green", ::CasinoDiceMat, "casino_green", 0xFF1E6B3A, 0xFF0E3F22, 0xFF0A2E18, 0xFF061F10, 0xFFE0C45A, 0xFFD4AF37),
                mat("Purple", ::CasinoDiceMat, "casino_purple", 0xFF4A2266, 0xFF2A1040, 0xFF1E0A30, 0xFF12061E, 0xFFE0C45A, 0xFFD4AF37),
            ),
            unlock = AchievementCount(11),
        ),
        StyleFamily(
            "Gingham",
            listOf(
                mat("Red", ::GinghamDiceMat, "gingham_red", 0xFFA8322D, 0xFF7E211D, 0xFF4A1310, 0xFF330B09, 0xFFF2B8B0, 0xFFFFFFFF),
                mat("Blue", ::GinghamDiceMat, "gingham_blue", 0xFF2F5C8F, 0xFF1E3E63, 0xFF122640, 0xFF0B182B, 0xFFB8D0F0, 0xFFFFFFFF),
                mat("Green", ::GinghamDiceMat, "gingham_green", 0xFF3E7D4A, 0xFF27562F, 0xFF16331C, 0xFF0E2312, 0xFFBFE3C6, 0xFFFFFFFF),
            ),
            unlock = AchievementCount(20),
        ),
        StyleFamily(
            "Starry",
            listOf(
                mat("Midnight", ::StarryDiceMat, "starry_midnight", 0xFF152550, 0xFF070E24, 0xFF0A1330, 0xFF050A1C, 0xFF8FA8E8, 0xFFFFFFFF),
            ),
            unlock = AchievementCount(69),
        ),
        StyleFamily("Marble", listOf(StyleColour("White", Color(0xFFF2F1EE), MarbleDiceMat)), unlock = AchievementCount(72)),
    ),
)

// The Mahjong dice's tiles: an ivory face over a jade back.
private val MahjongFace = Color(0xFFFBF7EC)
private val MahjongFaceShade = Color(0xFFE6DFCB)
private val MahjongBack = Color(0xFF2E7D4F)

// Shorthands for the entries above. A colour's swatch - its dot on the Styles screen - is the most
// representative colour of its palette.

private fun <T> colour(name: String, style: T): StyleColour<T> where T : TableArt, T : Swatched = StyleColour(name, style.swatch, style)

private fun <T : DiceCupStyle> cup(
    name: String,
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
    fun stage(name: String, id: String, swatch: Color, stage: Int) =
        StyleColour(name, swatch, FlowerpotDiceCupStyle(id, FlowerpotTerracotta, fixedStage = stage), Achievement.GREENFINGERS)
    return listOf(
        StyleColour("Terracotta", FlowerpotTerracotta.mid, FlowerpotDiceCupStyle("flowerpot_terracotta", FlowerpotTerracotta)),
        stage("Seedling", "flowerpot_seedling", FlowerpotLeaf, 1),
        stage("Bud", "flowerpot_bud", FlowerpotStem, 2),
        stage("Opening", "flowerpot_opening", SunflowerPetalShade, 3),
        stage("Sunflower", "sunflower_terracotta", SunflowerPetal, FLOWERPOT_FULL_BLOOM),
    )
}

// Cauldrons share their iron and differ only in their brew, which is also the colour the Styles
// screen shows for them.
private fun cauldron(name: String, id: String, brew: Long, deepBrew: Long): StyleColour<DiceCupStyle> =
    StyleColour(
        name,
        Color(brew),
        CauldronDiceCupStyle(id, CupPalette(Color(0xFF151618), Color(0xFF5A5E63), Color(0xFF2C2F33), Color(brew), Color(deepBrew))),
    )

private fun matPalette(top: Long, bottom: Long, slotTop: Long, slotBottom: Long, slotBorder: Long, detail: Long) =
    MatPalette(Color(top), Color(bottom), Color(slotTop), Color(slotBottom), Color(slotBorder), Color(detail))

private fun mat(
    name: String,
    create: (String, MatPalette) -> DiceMat,
    id: String,
    top: Long,
    bottom: Long,
    slotTop: Long,
    slotBottom: Long,
    slotBorder: Long,
    detail: Long,
): StyleColour<DiceMat> = StyleColour(name, Color(top), create(id, matPalette(top, bottom, slotTop, slotBottom, slotBorder, detail)))

private fun background(
    name: String,
    create: (String, BackgroundPalette) -> TableBackground,
    id: String,
    top: Long,
    bottom: Long,
): StyleColour<TableBackground> = StyleColour(name, Color(top), create(id, BackgroundPalette(Color(top), Color(bottom))))
