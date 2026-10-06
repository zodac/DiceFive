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
            assertEquals("Classic", catalog.familyOf(catalog.default.id).name)
        }
    }

    @Test
    fun defaultIsTheFirstColourOfTheFirstStyle() {
        assertEquals(IvoryDiceStyle, DiceStyles.default)
        assertEquals("casino_gold", DiceCupStyles.default.id)
        assertEquals(listOf("Classic", "Faceted"), DiceCupStyles.families.take(2).map { it.name })
        assertEquals(TrayBlueMat, DiceMats.default)
        assertEquals(MidnightFeltBackground, TableBackgrounds.default)
    }

    @Test
    fun coloursOfOneShapeShareAStyle() {
        assertEquals("Faceted", DiceCupStyles.familyOf("fire").name)
        assertEquals("Containers", DiceCupStyles.familyOf("barrel").name)
        assertEquals("Classic", DiceMats.familyOf("fire").name)
        assertEquals("Wood", DiceMats.familyOf("barrel").name)
        for (id in listOf("ivory", "classic_red", "classic_yellow", "classic_blue", "barrel")) {
            assertEquals("Classic", DiceStyles.familyOf(id).name)
            assertEquals("Classic", TableBackgrounds.familyOf(if (id == "ivory") "midnight_felt" else id).name)
        }
    }

    @Test
    fun unknownIdFallsBackToTheDefaultAndItsStyle() {
        assertEquals("casino_gold", DiceCupStyles.byId("leather").id)
        assertEquals("casino_gold", DiceCupStyles.byId("casino_burgundy").id)
        assertEquals("Classic", DiceCupStyles.familyOf("leather").name)
        // The Classic dice's old red skin is gone, so a saved pick of it lands on the default.
        assertEquals(DiceStyles.default, DiceStyles.byId("fire"))
    }

    @Test
    fun onlyTheClassicStylesAreFree() {
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds, ScoreFrames)) {
            for (family in catalog.families) {
                assertEquals(family.name == "Classic", family.unlock == StyleUnlock.Free, "${family.name}'s unlock is ${family.unlock}")
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
        assertEquals("Multicolour", multicolour.name)
        assertEquals(listOf("Tricolour", "Rainbow", "Irish"), multicolour.colours.map { it.name })
        assertTrue(multicolour.unlock is StyleUnlock.AchievementCount)
        assertEquals(listOf("the hidden 'Multicolour' dice colour"), Achievement.LUCK_OF_THE_IRISH.styleRewards.map { it.description })

        val count = multicolour.unlock.count
        val ordinary = Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }.take(count)
        val without = AchievementsState(ordinary.associateWith { 0L })
        assertEquals(TricolourStripedDiceStyle, DiceStyles.unlockedById(TricolourStripedDiceStyle.id, without))
        assertEquals(DiceStyles.default, DiceStyles.unlockedById(IrishFlagDiceStyle.id, without))
        assertEquals(listOf("Tricolour", "Rainbow"), multicolour.availableColours(without).map { it.name })

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
        assertEquals("Googly", googly.name)
        assertEquals(listOf("Ivory", "Black", "Blue"), googly.colours.map { it.name })
        assertTrue(Achievement.BIG_FAN.unlocksStyle)
        assertEquals(listOf("the hidden 'Googly' dice colour"), Achievement.BIG_FAN.styleRewards.map { it.description })

        val count = (googly.unlock as StyleUnlock.AchievementCount).count
        val ordinary = Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }.take(count)
        val without = AchievementsState(ordinary.associateWith { 0L })
        assertEquals("googly_black", DiceStyles.unlockedById("googly_black", without).id)
        assertEquals(DiceStyles.default, DiceStyles.unlockedById("googly_blue", without))
        assertEquals(listOf("Ivory", "Black"), googly.availableColours(without).map { it.name })
        assertEquals("googly_blue", DiceStyles.unlockedById("googly_blue", AchievementsState(without.unlockedAt + (Achievement.BIG_FAN to 0L))).id)
    }

    @Test
    fun theFlowerpotsPlantStagesAreSecretColoursUnlockedByGreenfingers() {
        val flowerpot = DiceCupStyles.familyOf("flowerpot_terracotta")
        assertEquals("Flowerpot", flowerpot.name)
        assertEquals(
            listOf("flowerpot_terracotta", "flowerpot_seedling", "flowerpot_bud", "flowerpot_opening", "sunflower_terracotta"),
            flowerpot.colours.map { it.style.id },
        )
        // The family itself is an ordinary count lock, not hidden: only the plant stages are secret.
        assertFalse(flowerpot.unlock.hiddenWhileLocked)
        assertEquals(listOf("the hidden 'Flowerpot' dice cup colours"), Achievement.GREENFINGERS.styleRewards.map { it.description })
        // The removed Slate pot and Sunflower family no longer resolve.
        assertEquals(DiceCupStyles.default, DiceCupStyles.byId("flowerpot_slate"))
        assertEquals(DiceCupStyles.default, DiceCupStyles.byId("sunflower_slate"))

        // Enough ordinary achievements unlock the pot, but not its plant stages, which are also left off the screen.
        val count = (flowerpot.unlock as StyleUnlock.AchievementCount).count
        val ordinary = Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }.take(count)
        val without = AchievementsState(ordinary.associateWith { 0L })
        assertEquals("flowerpot_terracotta", DiceCupStyles.unlockedById("flowerpot_terracotta", without).id)
        assertEquals(DiceCupStyles.default, DiceCupStyles.unlockedById("sunflower_terracotta", without))
        assertEquals(listOf("Terracotta"), flowerpot.availableColours(without).map { it.name })

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
        assertEquals("Top Hat", topHat.name)
        assertEquals(listOf("Black", "Grey", "Rabbit"), topHat.colours.map { it.name })
        assertEquals(listOf("the hidden 'Top Hat' dice cup colour"), Achievement.MAGICIANS_SECRET.styleRewards.map { it.description })

        val ordinary = Achievement.entries.filter { it.visibility != AchievementVisibility.SECRET }.take((topHat.unlock as StyleUnlock.AchievementCount).count)
        val without = AchievementsState(ordinary.associateWith { 0L })
        assertEquals("top_hat_grey", DiceCupStyles.unlockedById("top_hat_grey", without).id)
        assertEquals(DiceCupStyles.default, DiceCupStyles.unlockedById("top_hat_rabbit", without))
        assertEquals(listOf("Black", "Grey"), topHat.availableColours(without).map { it.name })
        assertEquals("top_hat_rabbit", DiceCupStyles.unlockedById("top_hat_rabbit", AchievementsState(without.unlockedAt + (Achievement.MAGICIANS_SECRET to 0L))).id)
    }

    @Test
    fun theMartiniCupIsASecretStyleUnlockedByShakenNotTapped() {
        val martini = DiceCupStyles.familyOf("martini")
        assertEquals("Martini", martini.name)
        assertTrue(martini.unlock.hiddenWhileLocked)
        assertEquals(listOf("the 'Martini' dice cup style"), Achievement.SHAKEN_NOT_TAPPED.styleRewards.map { it.description })

        val everythingElse = Achievement.entries.filter { it != Achievement.SHAKEN_NOT_TAPPED }
        assertEquals(DiceCupStyles.default, DiceCupStyles.unlockedById("martini", AchievementsState(everythingElse.associateWith { 0L })))
        assertEquals("martini", DiceCupStyles.unlockedById("martini", AchievementsState(mapOf(Achievement.SHAKEN_NOT_TAPPED to 0L))).id)
    }

    @Test
    fun theMathsDiceAreASecretStyleUnlockedByTheSolution() {
        val maths = DiceStyles.familyOf("maths_white")
        assertEquals("Maths", maths.name)
        assertEquals(listOf("White", "Black", "Green"), maths.colours.map { it.name })
        assertTrue(maths.unlock.hiddenWhileLocked)
        assertEquals(listOf("the 'Maths' dice style"), Achievement.THE_SOLUTION.styleRewards.map { it.description })

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
        assertEquals("Floating Dice", floating.name)
        assertTrue(floating.unlock.hiddenWhileLocked)
        assertEquals(listOf("the 'Floating Dice' background style"), Achievement.NOT_THOSE_DICE.styleRewards.map { it.description })

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
                assertEquals(secret, unlock.hiddenWhileLocked, family.name)
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
        assertEquals("Classic", ScoreFrames.familyOf("classic").name)
        val none = AchievementsState()
        for (frame in ScoreFrames.all) {
            assertEquals(frame.id == "classic", ScoreFrames.isUnlocked(frame.id, none), frame.id)
        }
    }

    @Test
    fun aFramesVariantsAreDesignsAllShownInTheSameColour() {
        assertEquals("design", ScoreFrames.variantNoun)
        assertEquals("colour", DiceStyles.variantNoun)
        // Every frame is drawn in the player's colour, so every variant's dot is the one colour.
        assertEquals(1, ScoreFrames.families.flatMap { family -> family.colours.map { it.swatch } }.toSet().size)
        assertTrue(ScoreFrames.families.drop(1).all { it.colours.size in 1..3 })
    }
}
