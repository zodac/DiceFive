package net.zodac.dicefive.ui.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.FLOWERPOT_FULL_BLOOM

/**
 * The Styles screen groups each category's colours into styles, but what's saved is still each
 * colour's own id - so every id a player could already have saved must still resolve to the same
 * art, and land in the style the screen should show as picked.
 */
class StyleCatalogTest {

    private val catalogs = listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds, ScoreFrames)

    /** Enough ordinary (not secret) achievements to meet [unlock]'s count, and nothing else. */
    private fun ordinaryFor(unlock: StyleUnlock) = AchievementsState(
        Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }.take((unlock as StyleUnlock.AchievementCount).count).associateWith { 0L },
    )

    @Test
    fun `every id resolves to its own art - in the style it belongs to - an unknown one to the Classic default`() {
        val shipped = mapOf(
            DiceStyles to listOf("ivory", "classic_red", "classic_yellow", "classic_blue", "barrel"),
            DiceCupStyles to listOf("casino_gold", "casino_black", "casino_green", "faceted", "fire", "barrel"),
            DiceMats to listOf("tray_blue", "fire", "barrel"),
            TableBackgrounds to listOf("midnight_felt", "fire", "barrel"),
            ScoreFrames to listOf("classic"),
        )
        for ((catalog, ids) in shipped) for (id in ids) assertEquals(id, catalog.byId(id).id)
        for (catalog in catalogs) {
            for (art in catalog.all) assertEquals(art, catalog.byId(art.id))
            for (family in catalog.families) assertTrue(family.colours.isNotEmpty(), "${family.name} has no colours")
            // Every default style is called Classic.
            assertEquals(familyKey("Classic"), catalog.familyOf(catalog.default.id).name.key)
        }

        // The default is the first colour of the first style.
        assertEquals(IvoryDiceStyle, DiceStyles.default)
        assertEquals("casino_gold", DiceCupStyles.default.id)
        assertEquals(listOf(familyKey("Classic"), familyKey("Faceted")), DiceCupStyles.families.take(2).map { it.name.key })
        assertEquals(TrayBlueMat, DiceMats.default)
        assertEquals(MidnightFeltBackground, TableBackgrounds.default)
        // SettingsRepository's and SavedStyles' literal default must be this id.
        assertEquals("classic", ScoreFrames.default.id)

        // Colours of one shape share a style.
        assertEquals(familyKey("Faceted"), DiceCupStyles.familyOf("fire").name.key)
        assertEquals(familyKey("Containers"), DiceCupStyles.familyOf("barrel").name.key)
        assertEquals(familyKey("Classic"), DiceMats.familyOf("fire").name.key)
        assertEquals(familyKey("Wood"), DiceMats.familyOf("barrel").name.key)
        for (id in listOf("ivory", "classic_red", "classic_yellow", "classic_blue", "barrel")) {
            assertEquals(familyKey("Classic"), DiceStyles.familyOf(id).name.key)
            assertEquals(familyKey("Classic"), TableBackgrounds.familyOf(if (id == "ivory") "midnight_felt" else id).name.key)
        }

        // An unknown id falls back to the default and its style. The Classic dice's old red skin is gone, so a saved pick
        // of it lands on the default too.
        assertEquals("casino_gold", DiceCupStyles.byId("leather").id)
        assertEquals("casino_gold", DiceCupStyles.byId("casino_burgundy").id)
        assertEquals(familyKey("Classic"), DiceCupStyles.familyOf("leather").name.key)
        assertEquals(DiceStyles.default, DiceStyles.byId("fire"))
    }

    @Test
    fun `only Classic is free - every other style waits on a distinct earnable count or for a secret achievement hidden`() {
        for (catalog in catalogs) for (family in catalog.families) {
            assertEquals(family.name.key == familyKey("Classic"), family.unlock == StyleUnlock.Free, "${family.name}'s unlock is ${family.unlock}")
        }

        val earnable = Achievement.entries.count { it.visibility != AchievementVisibility.SECRET }
        val counts = catalogs.flatMap { it.families }.mapNotNull { (it.unlock as? StyleUnlock.AchievementCount)?.count }
        assertEquals(counts.size, counts.toSet().size, "Two styles share an achievement count: $counts")
        for (count in counts) assertTrue(count in 1..earnable, "$count isn't in 1..$earnable")

        // Secret achievements don't count towards a lock.
        val secrets = AchievementsState(unlockedAt = Achievement.entries.filter { it.visibility == AchievementVisibility.SECRET }.associateWith { 0L })
        assertEquals(0, secrets.countedUnlocks)
        assertFalse(StyleUnlock.AchievementCount(1).isMet(secrets))

        // Only a secret achievement's style is hidden while locked.
        for (catalog in StyleCatalogs) for (family in catalog.families) {
            val unlock = family.unlock
            val secret = unlock is StyleUnlock.SpecificAchievement && unlock.achievement.visibility == AchievementVisibility.SECRET
            assertEquals(secret, unlock.hiddenWhileLocked, family.name.key)
        }
        assertFalse(Achievement.entries.first { it.visibility != AchievementVisibility.SECRET }.unlocksStyle)

        // A locked pick is drawn as the default until its style unlocks; every colour of a Classic style is there from the start.
        val retro = DiceStyles.familyOf("retro_amber")
        val needed = (retro.unlock as StyleUnlock.AchievementCount).count
        val earnableAchievements = Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }
        assertEquals(DiceStyles.default, DiceStyles.unlockedById("retro_amber", AchievementsState(unlockedAt = earnableAchievements.take(needed - 1).associateWith { 0L })))
        assertEquals("retro_amber", DiceStyles.unlockedById("retro_amber", AchievementsState(unlockedAt = earnableAchievements.take(needed).associateWith { 0L })).id)
        assertEquals("barrel", DiceStyles.unlockedById("barrel", AchievementsState()).id)

        // Every frame but the Classic one is locked.
        assertEquals(familyKey("Classic"), ScoreFrames.familyOf("classic").name.key)
        for (frame in ScoreFrames.all) assertEquals(frame.id == "classic", ScoreFrames.isUnlocked(frame.id, AchievementsState()), frame.id)
    }

    @Test
    fun `a secret colour is offered only once its secret achievement is earned - on top of its style's own count`() {
        // The Irish dice are a colour of Multicolour, unlocked by Luck of the Irish - which alone doesn't unlock the family.
        val multicolour = DiceStyles.familyOf(IrishFlagDiceStyle.id)
        assertEquals(familyKey("Multicolour"), multicolour.name.key)
        assertEquals(listOf(colourKey("Tricolour"), colourKey("Rainbow"), colourKey("Irish")), multicolour.colours.map { it.name.key })
        assertTrue(multicolour.unlock is StyleUnlock.AchievementCount)
        assertEquals(listOf(familyKey("Multicolour") to 1), Achievement.LUCK_OF_THE_IRISH.styleRewards.map { it.styleName.key to it.hiddenColours })
        val withoutIrish = ordinaryFor(multicolour.unlock)
        assertEquals(TricolourStripedDiceStyle, DiceStyles.unlockedById(TricolourStripedDiceStyle.id, withoutIrish))
        assertEquals(DiceStyles.default, DiceStyles.unlockedById(IrishFlagDiceStyle.id, withoutIrish))
        assertEquals(listOf(colourKey("Tricolour"), colourKey("Rainbow")), multicolour.availableColours(withoutIrish).map { it.name.key })
        val onlyIrish = AchievementsState(mapOf(Achievement.LUCK_OF_THE_IRISH to 0L))
        assertEquals(DiceStyles.default, DiceStyles.unlockedById(IrishFlagDiceStyle.id, onlyIrish))
        val both = AchievementsState(withoutIrish.unlockedAt + onlyIrish.unlockedAt)
        assertEquals(IrishFlagDiceStyle, DiceStyles.unlockedById(IrishFlagDiceStyle.id, both))
        assertEquals(3, multicolour.availableColours(both).size)

        // The blue Googly dice, unlocked by Big Fan.
        val googly = DiceStyles.familyOf("googly_blue")
        assertEquals(familyKey("Googly"), googly.name.key)
        assertEquals(listOf(colourKey("Ivory"), colourKey("Black"), colourKey("Blue")), googly.colours.map { it.name.key })
        assertTrue(Achievement.BIG_FAN.unlocksStyle)
        assertEquals(listOf(familyKey("Googly") to 1), Achievement.BIG_FAN.styleRewards.map { it.styleName.key to it.hiddenColours })
        val withoutFan = ordinaryFor(googly.unlock)
        assertEquals("googly_black", DiceStyles.unlockedById("googly_black", withoutFan).id)
        assertEquals(DiceStyles.default, DiceStyles.unlockedById("googly_blue", withoutFan))
        assertEquals(listOf(colourKey("Ivory"), colourKey("Black")), googly.availableColours(withoutFan).map { it.name.key })
        assertEquals("googly_blue", DiceStyles.unlockedById("googly_blue", AchievementsState(withoutFan.unlockedAt + (Achievement.BIG_FAN to 0L))).id)

        // The golden Egg, unlocked by Eggcellent Discovery: a colour of the ordinary Egg family, left off until earned.
        val egg = DiceStyles.familyOf("egg_gold")
        assertEquals(listOf(colourKey("White"), colourKey("Brown"), colourKey("Gold")), egg.colours.map { it.name.key })
        assertEquals(listOf(familyKey("Egg") to 1), Achievement.EGGCELLENT_DISCOVERY.styleRewards.map { it.styleName.key to it.hiddenColours })
        val withoutGold = ordinaryFor(egg.unlock)
        assertEquals(DiceStyles.default, DiceStyles.unlockedById("egg_gold", withoutGold))
        assertEquals(listOf(colourKey("White"), colourKey("Brown")), egg.availableColours(withoutGold).map { it.name.key })
        assertEquals("egg_gold", DiceStyles.unlockedById("egg_gold", AchievementsState(withoutGold.unlockedAt + (Achievement.EGGCELLENT_DISCOVERY to 0L))).id)

        // The Rabbit Top Hat, unlocked by The Magician's Secret.
        val topHat = DiceCupStyles.familyOf("top_hat_rabbit")
        assertEquals(familyKey("Top Hat"), topHat.name.key)
        assertEquals(listOf(colourKey("Black"), colourKey("Grey"), colourKey("Rabbit")), topHat.colours.map { it.name.key })
        assertEquals(listOf(familyKey("Top Hat") to 1), Achievement.MAGICIANS_SECRET.styleRewards.map { it.styleName.key to it.hiddenColours })
        val withoutMagic = ordinaryFor(topHat.unlock)
        assertEquals("top_hat_grey", DiceCupStyles.unlockedById("top_hat_grey", withoutMagic).id)
        assertEquals(DiceCupStyles.default, DiceCupStyles.unlockedById("top_hat_rabbit", withoutMagic))
        assertEquals(listOf(colourKey("Black"), colourKey("Grey")), topHat.availableColours(withoutMagic).map { it.name.key })
        assertEquals("top_hat_rabbit", DiceCupStyles.unlockedById("top_hat_rabbit", AchievementsState(withoutMagic.unlockedAt + (Achievement.MAGICIANS_SECRET to 0L))).id)

        // The Flowerpot's plant stages, unlocked by Greenfingers. The family itself is an ordinary count lock, not hidden:
        // only the plant stages are secret. The removed Slate pot and Sunflower family no longer resolve.
        val flowerpot = DiceCupStyles.familyOf("flowerpot_terracotta")
        assertEquals(familyKey("Flowerpot"), flowerpot.name.key)
        assertEquals(listOf("flowerpot_terracotta", "flowerpot_seedling", "flowerpot_bud", "flowerpot_opening", "sunflower_terracotta"), flowerpot.colours.map { it.style.id })
        assertFalse(flowerpot.unlock.hiddenWhileLocked)
        assertEquals(listOf(familyKey("Flowerpot") to 4), Achievement.GREENFINGERS.styleRewards.map { it.styleName.key to it.hiddenColours })
        assertEquals(DiceCupStyles.default, DiceCupStyles.byId("flowerpot_slate"))
        assertEquals(DiceCupStyles.default, DiceCupStyles.byId("sunflower_slate"))
        // Enough ordinary achievements unlock the pot, but not its plant stages, which are also left off the screen.
        val withoutGreen = ordinaryFor(flowerpot.unlock)
        assertEquals("flowerpot_terracotta", DiceCupStyles.unlockedById("flowerpot_terracotta", withoutGreen).id)
        assertEquals(DiceCupStyles.default, DiceCupStyles.unlockedById("sunflower_terracotta", withoutGreen))
        assertEquals(listOf(colourKey("Terracotta")), flowerpot.availableColours(withoutGreen).map { it.name.key })
        // Greenfingers alone doesn't unlock the pot's family, but with it every stage is offered.
        val earned = AchievementsState(withoutGreen.unlockedAt + (Achievement.GREENFINGERS to 0L))
        assertEquals("sunflower_terracotta", DiceCupStyles.unlockedById("sunflower_terracotta", earned).id)
        assertEquals(5, flowerpot.availableColours(earned).size)
        assertEquals(DiceCupStyles.default, DiceCupStyles.unlockedById("sunflower_terracotta", AchievementsState(mapOf(Achievement.GREENFINGERS to 0L))))

        // Only the empty flowerpot that has grown its sunflower shows off when spent. A pot held at one stage has nothing
        // growing, so it greys like any other cup.
        val bloomed = FlowerpotGrowth(stage = FLOWERPOT_FULL_BLOOM, grower = 0)
        assertTrue(DiceCupStyles.byId("flowerpot_terracotta").showsOffWhenSpent(bloomed))
        assertFalse(DiceCupStyles.byId("flowerpot_terracotta").showsOffWhenSpent(FlowerpotGrowth(stage = FLOWERPOT_FULL_BLOOM - 1, grower = 0)))
        for (id in listOf("flowerpot_seedling", "flowerpot_bud", "flowerpot_opening", "sunflower_terracotta")) {
            assertFalse(DiceCupStyles.byId(id).showsOffWhenSpent(bloomed), id)
        }
        assertFalse(DiceCupStyles.default.showsOffWhenSpent(bloomed))
    }

    @Test
    fun `a secret style is hidden until its secret achievement - and nothing else - unlocks it`() {
        // The Martini cup, by Shaken Not Tapped.
        val martini = DiceCupStyles.familyOf("martini")
        assertEquals(familyKey("Martini"), martini.name.key)
        assertTrue(martini.unlock.hiddenWhileLocked)
        assertEquals(listOf(familyKey("Martini") to 0), Achievement.SHAKEN_NOT_TAPPED.styleRewards.map { it.styleName.key to it.hiddenColours })
        assertEquals(DiceCupStyles.default, DiceCupStyles.unlockedById("martini", AchievementsState(Achievement.entries.filter { it != Achievement.SHAKEN_NOT_TAPPED }.associateWith { 0L })))
        assertEquals("martini", DiceCupStyles.unlockedById("martini", AchievementsState(mapOf(Achievement.SHAKEN_NOT_TAPPED to 0L))).id)

        // The Maths dice, by The Solution.
        val maths = DiceStyles.familyOf("maths_white")
        assertEquals(familyKey("Maths"), maths.name.key)
        assertEquals(listOf(colourKey("White"), colourKey("Black"), colourKey("Green")), maths.colours.map { it.name.key })
        assertTrue(maths.unlock.hiddenWhileLocked)
        assertEquals(listOf(familyKey("Maths") to 0), Achievement.THE_SOLUTION.styleRewards.map { it.styleName.key to it.hiddenColours })
        val everythingButTheSolution = AchievementsState(Achievement.entries.filter { it != Achievement.THE_SOLUTION }.associateWith { 0L })
        val solved = AchievementsState(mapOf(Achievement.THE_SOLUTION to 0L))
        for (id in listOf("maths_white", "maths_black", "maths_green")) {
            assertEquals(DiceStyles.default, DiceStyles.unlockedById(id, everythingButTheSolution), id)
            assertEquals(id, DiceStyles.unlockedById(id, solved).id)
        }

        // The Floating Dice background, by Not Those Dice.
        val floating = TableBackgrounds.familyOf("floating_dice")
        assertEquals(familyKey("Floating Dice"), floating.name.key)
        assertTrue(floating.unlock.hiddenWhileLocked)
        assertEquals(listOf(familyKey("Floating Dice") to 0), Achievement.NOT_THOSE_DICE.styleRewards.map { it.styleName.key to it.hiddenColours })
        assertEquals(TableBackgrounds.default, TableBackgrounds.unlockedById("floating_dice", AchievementsState(Achievement.entries.filter { it != Achievement.NOT_THOSE_DICE }.associateWith { 0L })))
        assertEquals(FloatingDiceBackground, TableBackgrounds.unlockedById("floating_dice", AchievementsState(mapOf(Achievement.NOT_THOSE_DICE to 0L))))
    }

    @Test
    fun `a coloured die keeps its dice style in the roll's colour - and a frame's variants are designs in one colour`() {
        for (style in DiceStyles.all) for (colour in DieColour.entries) for (irish in listOf(false, true)) {
            val recoloured = style.recoloured(colour.palette(irish))
            assertEquals(style.id, recoloured.id)
            assertEquals(style.tumblesItself, recoloured.tumblesItself, style.id)
            assertEquals(style.pupilTravel, recoloured.pupilTravel, style.id)
            assertEquals(style.topFace(1), recoloured.topFace(1), style.id)
            // Recolouring twice is the same as recolouring once in the second colour.
            assertEquals(style.id, recoloured.recoloured(DieColour.BLUE.palette).id)
        }

        assertEquals("style_variant_design", ScoreFrames.variantNoun.key)
        assertEquals("style_variant_colour", DiceStyles.variantNoun.key)
        // Every frame is drawn in the player's colour, so every variant's dot is the one colour.
        assertEquals(1, ScoreFrames.families.flatMap { family -> family.colours.map { it.swatch } }.toSet().size)
        assertTrue(ScoreFrames.families.drop(1).all { it.colours.size in 1..3 })
    }
}

/** The key a family's name has in strings.xml: `style_family_` and the English name, lower case and underscored. */
private fun familyKey(name: String) = "style_family_" + keySlug(name)

/** The key a colour's name has in strings.xml. */
private fun colourKey(name: String) = "style_colour_" + keySlug(name)

private fun keySlug(name: String) = name.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]+"), "_").trim('_')
