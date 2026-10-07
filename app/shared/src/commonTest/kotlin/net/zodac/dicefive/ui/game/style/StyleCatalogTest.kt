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

    @Test
    fun everyShippedIdStillResolvesToItsOwnArt() {
        val shipped = mapOf(
            DiceStyles to listOf("ivory", "classic_red", "classic_yellow", "classic_blue", "barrel"),
            DiceCupStyles to listOf("casino_gold", "casino_black", "casino_green", "faceted", "fire", "barrel"),
            DiceMats to listOf("tray_blue", "fire", "barrel"),
            TableBackgrounds to listOf("midnight_felt", "fire", "barrel"),
            ScoreFrames to listOf("classic"),
        )
        for ((catalog, ids) in shipped) {
            for (id in ids) assertEquals(id, catalog.byId(id).id)
        }
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds, ScoreFrames)) {
            for (art in catalog.all) {
                assertEquals(art, catalog.byId(art.id))
            }
        }
    }

    @Test
    fun everyStyleComesInAtLeastOneColour() {
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds, ScoreFrames)) {
            for (family in catalog.families) {
                assertTrue(family.colours.isNotEmpty(), "${family.name} has no colours")
            }
        }
    }

    @Test
    fun everyDefaultStyleIsCalledClassic() {
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds, ScoreFrames)) {
            assertEquals(familyKey("Classic"), catalog.familyOf(catalog.default.id).name.key)
        }
    }

    @Test
    fun defaultIsTheFirstColourOfTheFirstStyle() {
        assertEquals(IvoryDiceStyle, DiceStyles.default)
        assertEquals("casino_gold", DiceCupStyles.default.id)
        assertEquals(listOf(familyKey("Classic"), familyKey("Faceted")), DiceCupStyles.families.take(2).map { it.name.key })
        assertEquals(TrayBlueMat, DiceMats.default)
        assertEquals(MidnightFeltBackground, TableBackgrounds.default)
    }

    @Test
    fun coloursOfOneShapeShareAStyle() {
        assertEquals(familyKey("Faceted"), DiceCupStyles.familyOf("fire").name.key)
        assertEquals(familyKey("Containers"), DiceCupStyles.familyOf("barrel").name.key)
        assertEquals(familyKey("Classic"), DiceMats.familyOf("fire").name.key)
        assertEquals(familyKey("Wood"), DiceMats.familyOf("barrel").name.key)
        for (id in listOf("ivory", "classic_red", "classic_yellow", "classic_blue", "barrel")) {
            assertEquals(familyKey("Classic"), DiceStyles.familyOf(id).name.key)
            assertEquals(familyKey("Classic"), TableBackgrounds.familyOf(if (id == "ivory") "midnight_felt" else id).name.key)
        }
    }

    @Test
    fun unknownIdFallsBackToTheDefaultAndItsStyle() {
        assertEquals("casino_gold", DiceCupStyles.byId("leather").id)
        assertEquals("casino_gold", DiceCupStyles.byId("casino_burgundy").id)
        assertEquals(familyKey("Classic"), DiceCupStyles.familyOf("leather").name.key)
        // The Classic dice's old red skin is gone, so a saved pick of it lands on the default.
        assertEquals(DiceStyles.default, DiceStyles.byId("fire"))
    }

    @Test
    fun onlyTheClassicStylesAreFree() {
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds, ScoreFrames)) {
            for (family in catalog.families) {
                assertEquals(family.name.key == familyKey("Classic"), family.unlock == StyleUnlock.Free, "${family.name}'s unlock is ${family.unlock}")
            }
        }
    }

    @Test
    fun everyAchievementCountLockIsDistinctAndEarnable() {
        val earnable = Achievement.entries.count { it.visibility != AchievementVisibility.SECRET }
        val counts = listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds, ScoreFrames)
            .flatMap { it.families }
            .mapNotNull { (it.unlock as? StyleUnlock.AchievementCount)?.count }
        assertEquals(counts.size, counts.toSet().size, "Two styles share an achievement count: $counts")
        for (count in counts) assertTrue(count in 1..earnable, "$count isn't in 1..$earnable")
    }

    @Test
    fun secretAchievementsDontCountTowardsALock() {
        val secrets = Achievement.entries.filter { it.visibility == AchievementVisibility.SECRET }
        val state = AchievementsState(unlockedAt = secrets.associateWith { 0L })
        assertEquals(0, state.countedUnlocks)
        assertFalse(StyleUnlock.AchievementCount(1).isMet(state))
    }

    @Test
    fun aLockedPickIsDrawnAsTheDefaultUntilItsStyleUnlocks() {
        val retro = DiceStyles.familyOf("retro_amber")
        val needed = (retro.unlock as StyleUnlock.AchievementCount).count
        val earnable = Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }
        val justShort = AchievementsState(unlockedAt = earnable.take(needed - 1).associateWith { 0L })
        val enough = AchievementsState(unlockedAt = earnable.take(needed).associateWith { 0L })

        assertEquals(DiceStyles.default, DiceStyles.unlockedById("retro_amber", justShort))
        assertEquals("retro_amber", DiceStyles.unlockedById("retro_amber", enough).id)
        // Every colour of a Classic style is available from the start.
        assertEquals("barrel", DiceStyles.unlockedById("barrel", AchievementsState()).id)
    }

    @Test
    fun theIrishDiceAreASecretColourOfMulticolourUnlockedByLuckOfTheIrish() {
        val multicolour = DiceStyles.familyOf(IrishFlagDiceStyle.id)
        assertEquals(familyKey("Multicolour"), multicolour.name.key)
        assertEquals(listOf(colourKey("Tricolour"), colourKey("Rainbow"), colourKey("Irish")), multicolour.colours.map { it.name.key })
        assertTrue(multicolour.unlock is StyleUnlock.AchievementCount)
        assertEquals(listOf(familyKey("Multicolour") to 1), Achievement.LUCK_OF_THE_IRISH.styleRewards.map { it.styleName.key to it.hiddenColours })

        val count = multicolour.unlock.count
        val ordinary = Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }.take(count)
        val without = AchievementsState(ordinary.associateWith { 0L })
        assertEquals(TricolourStripedDiceStyle, DiceStyles.unlockedById(TricolourStripedDiceStyle.id, without))
        assertEquals(DiceStyles.default, DiceStyles.unlockedById(IrishFlagDiceStyle.id, without))
        assertEquals(listOf(colourKey("Tricolour"), colourKey("Rainbow")), multicolour.availableColours(without).map { it.name.key })

        // Luck of the Irish alone doesn't unlock the family.
        val onlyIrish = AchievementsState(mapOf(Achievement.LUCK_OF_THE_IRISH to 0L))
        assertEquals(DiceStyles.default, DiceStyles.unlockedById(IrishFlagDiceStyle.id, onlyIrish))
        val both = AchievementsState(without.unlockedAt + onlyIrish.unlockedAt)
        assertEquals(IrishFlagDiceStyle, DiceStyles.unlockedById(IrishFlagDiceStyle.id, both))
        assertEquals(3, multicolour.availableColours(both).size)
    }

    @Test
    fun theBlueGooglyDiceAreASecretColourUnlockedByBigFan() {
        val googly = DiceStyles.familyOf("googly_blue")
        assertEquals(familyKey("Googly"), googly.name.key)
        assertEquals(listOf(colourKey("Ivory"), colourKey("Black"), colourKey("Blue")), googly.colours.map { it.name.key })
        assertTrue(Achievement.BIG_FAN.unlocksStyle)
        assertEquals(listOf(familyKey("Googly") to 1), Achievement.BIG_FAN.styleRewards.map { it.styleName.key to it.hiddenColours })

        val count = (googly.unlock as StyleUnlock.AchievementCount).count
        val ordinary = Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }.take(count)
        val without = AchievementsState(ordinary.associateWith { 0L })
        assertEquals("googly_black", DiceStyles.unlockedById("googly_black", without).id)
        assertEquals(DiceStyles.default, DiceStyles.unlockedById("googly_blue", without))
        assertEquals(listOf(colourKey("Ivory"), colourKey("Black")), googly.availableColours(without).map { it.name.key })
        assertEquals("googly_blue", DiceStyles.unlockedById("googly_blue", AchievementsState(without.unlockedAt + (Achievement.BIG_FAN to 0L))).id)
    }

    @Test
    fun theFlowerpotsPlantStagesAreSecretColoursUnlockedByGreenfingers() {
        val flowerpot = DiceCupStyles.familyOf("flowerpot_terracotta")
        assertEquals(familyKey("Flowerpot"), flowerpot.name.key)
        assertEquals(
            listOf("flowerpot_terracotta", "flowerpot_seedling", "flowerpot_bud", "flowerpot_opening", "sunflower_terracotta"),
            flowerpot.colours.map { it.style.id },
        )
        // The family itself is an ordinary count lock, not hidden: only the plant stages are secret.
        assertFalse(flowerpot.unlock.hiddenWhileLocked)
        assertEquals(listOf(familyKey("Flowerpot") to 4), Achievement.GREENFINGERS.styleRewards.map { it.styleName.key to it.hiddenColours })
        // The removed Slate pot and Sunflower family no longer resolve.
        assertEquals(DiceCupStyles.default, DiceCupStyles.byId("flowerpot_slate"))
        assertEquals(DiceCupStyles.default, DiceCupStyles.byId("sunflower_slate"))

        // Enough ordinary achievements unlock the pot, but not its plant stages, which are also left off the screen.
        val count = (flowerpot.unlock as StyleUnlock.AchievementCount).count
        val ordinary = Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }.take(count)
        val without = AchievementsState(ordinary.associateWith { 0L })
        assertEquals("flowerpot_terracotta", DiceCupStyles.unlockedById("flowerpot_terracotta", without).id)
        assertEquals(DiceCupStyles.default, DiceCupStyles.unlockedById("sunflower_terracotta", without))
        assertEquals(listOf(colourKey("Terracotta")), flowerpot.availableColours(without).map { it.name.key })

        // Greenfingers alone doesn't unlock the pot's family, but with it every stage is offered.
        val earned = AchievementsState(without.unlockedAt + (Achievement.GREENFINGERS to 0L))
        assertEquals("sunflower_terracotta", DiceCupStyles.unlockedById("sunflower_terracotta", earned).id)
        assertEquals(5, flowerpot.availableColours(earned).size)
        assertEquals(
            DiceCupStyles.default,
            DiceCupStyles.unlockedById("sunflower_terracotta", AchievementsState(mapOf(Achievement.GREENFINGERS to 0L))),
        )
    }

    @Test
    fun theRabbitTopHatIsASecretColourUnlockedByTheMagiciansSecret() {
        val topHat = DiceCupStyles.familyOf("top_hat_rabbit")
        assertEquals(familyKey("Top Hat"), topHat.name.key)
        assertEquals(listOf(colourKey("Black"), colourKey("Grey"), colourKey("Rabbit")), topHat.colours.map { it.name.key })
        assertEquals(listOf(familyKey("Top Hat") to 1), Achievement.MAGICIANS_SECRET.styleRewards.map { it.styleName.key to it.hiddenColours })

        val ordinary = Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }.take((topHat.unlock as StyleUnlock.AchievementCount).count)
        val without = AchievementsState(ordinary.associateWith { 0L })
        assertEquals("top_hat_grey", DiceCupStyles.unlockedById("top_hat_grey", without).id)
        assertEquals(DiceCupStyles.default, DiceCupStyles.unlockedById("top_hat_rabbit", without))
        assertEquals(listOf(colourKey("Black"), colourKey("Grey")), topHat.availableColours(without).map { it.name.key })
        assertEquals("top_hat_rabbit", DiceCupStyles.unlockedById("top_hat_rabbit", AchievementsState(without.unlockedAt + (Achievement.MAGICIANS_SECRET to 0L))).id)
    }

    @Test
    fun theMartiniCupIsASecretStyleUnlockedByShakenNotTapped() {
        val martini = DiceCupStyles.familyOf("martini")
        assertEquals(familyKey("Martini"), martini.name.key)
        assertTrue(martini.unlock.hiddenWhileLocked)
        assertEquals(listOf(familyKey("Martini") to 0), Achievement.SHAKEN_NOT_TAPPED.styleRewards.map { it.styleName.key to it.hiddenColours })

        val everythingElse = Achievement.entries.filter { it != Achievement.SHAKEN_NOT_TAPPED }
        assertEquals(DiceCupStyles.default, DiceCupStyles.unlockedById("martini", AchievementsState(everythingElse.associateWith { 0L })))
        assertEquals("martini", DiceCupStyles.unlockedById("martini", AchievementsState(mapOf(Achievement.SHAKEN_NOT_TAPPED to 0L))).id)
    }

    @Test
    fun theMathsDiceAreASecretStyleUnlockedByTheSolution() {
        val maths = DiceStyles.familyOf("maths_white")
        assertEquals(familyKey("Maths"), maths.name.key)
        assertEquals(listOf(colourKey("White"), colourKey("Black"), colourKey("Green")), maths.colours.map { it.name.key })
        assertTrue(maths.unlock.hiddenWhileLocked)
        assertEquals(listOf(familyKey("Maths") to 0), Achievement.THE_SOLUTION.styleRewards.map { it.styleName.key to it.hiddenColours })

        val everythingElse = AchievementsState(Achievement.entries.filter { it != Achievement.THE_SOLUTION }.associateWith { 0L })
        val solved = AchievementsState(mapOf(Achievement.THE_SOLUTION to 0L))
        for (id in listOf("maths_white", "maths_black", "maths_green")) {
            assertEquals(DiceStyles.default, DiceStyles.unlockedById(id, everythingElse), id)
            assertEquals(id, DiceStyles.unlockedById(id, solved).id)
        }
    }

    @Test
    fun theFloatingDiceBackgroundIsASecretStyleUnlockedByNotThoseDice() {
        val floating = TableBackgrounds.familyOf("floating_dice")
        assertEquals(familyKey("Floating Dice"), floating.name.key)
        assertTrue(floating.unlock.hiddenWhileLocked)
        assertEquals(listOf(familyKey("Floating Dice") to 0), Achievement.NOT_THOSE_DICE.styleRewards.map { it.styleName.key to it.hiddenColours })

        val everythingElse = Achievement.entries.filter { it != Achievement.NOT_THOSE_DICE }
        assertEquals(TableBackgrounds.default, TableBackgrounds.unlockedById("floating_dice", AchievementsState(everythingElse.associateWith { 0L })))
        assertEquals(FloatingDiceBackground, TableBackgrounds.unlockedById("floating_dice", AchievementsState(mapOf(Achievement.NOT_THOSE_DICE to 0L))))
    }

    @Test
    fun onlyTheEmptyFlowerpotThatHasGrownItsSunflowerShowsOffWhenSpent() {
        val flowerpot = DiceCupStyles.byId("flowerpot_terracotta")
        val bloomed = FlowerpotGrowth(stage = FLOWERPOT_FULL_BLOOM, grower = 0)

        assertTrue(flowerpot.showsOffWhenSpent(bloomed))
        assertFalse(flowerpot.showsOffWhenSpent(FlowerpotGrowth(stage = FLOWERPOT_FULL_BLOOM - 1, grower = 0)))
        // A pot held at one stage has nothing growing, so it greys like any other cup.
        for (id in listOf("flowerpot_seedling", "flowerpot_bud", "flowerpot_opening", "sunflower_terracotta")) {
            assertFalse(DiceCupStyles.byId(id).showsOffWhenSpent(bloomed), id)
        }
        assertFalse(DiceCupStyles.default.showsOffWhenSpent(bloomed))
    }

    @Test
    fun onlyASecretAchievementsStyleIsHiddenWhileLocked() {
        for (catalog in StyleCatalogs) {
            for (family in catalog.families) {
                val unlock = family.unlock
                val secret = unlock is StyleUnlock.SpecificAchievement && unlock.achievement.visibility == AchievementVisibility.SECRET
                assertEquals(secret, unlock.hiddenWhileLocked, family.name.key)
            }
        }
        assertFalse(Achievement.entries.first { it.visibility != AchievementVisibility.SECRET }.unlocksStyle)
    }

    @Test
    fun aColouredDieKeepsItsDiceStyleOnlyInTheRollsColour() {
        for (style in DiceStyles.all) {
            for (colour in DieColour.entries) {
                for (irish in listOf(false, true)) {
                    val palette = colour.palette(irish)
                    val recoloured = style.recoloured(palette)
                    assertEquals(style.id, recoloured.id)
                    assertEquals(style.tumblesItself, recoloured.tumblesItself, style.id)
                    assertEquals(style.pupilTravel, recoloured.pupilTravel, style.id)
                    assertEquals(style.topFace(1), recoloured.topFace(1), style.id)
                    // Recolouring twice is the same as recolouring once in the second colour.
                    assertEquals(style.id, recoloured.recoloured(DieColour.BLUE.palette).id)
                }
            }
        }
    }

    @Test
    fun theClassicFrameIsTheDefaultAndEveryOtherFrameIsLocked() {
        // SettingsRepository's and SavedStyles' literal default must be this id.
        assertEquals("classic", ScoreFrames.default.id)
        assertEquals(familyKey("Classic"), ScoreFrames.familyOf("classic").name.key)
        val none = AchievementsState()
        for (frame in ScoreFrames.all) {
            assertEquals(frame.id == "classic", ScoreFrames.isUnlocked(frame.id, none), frame.id)
        }
    }

    @Test
    fun aFramesVariantsAreDesignsAllShownInTheSameColour() {
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
