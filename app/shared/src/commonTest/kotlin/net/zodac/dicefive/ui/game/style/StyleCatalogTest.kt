package net.zodac.dicefive.ui.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementVisibility

/**
 * The Styles screen groups each category's colours into styles, but what's saved is still each
 * colour's own id - so every id a player could already have saved must still resolve to the same
 * art, and land in the style the screen should show as picked.
 */
class StyleCatalogTest {

    @Test
    fun everyShippedIdStillResolvesToItsOwnArt() {
        val shipped = mapOf(
            DiceStyles to listOf("ivory", "fire", "barrel"),
            DiceCupStyles to listOf("casino_gold", "casino_black", "casino_green", "faceted", "fire", "barrel"),
            DiceMats to listOf("tray_blue", "fire", "barrel"),
            TableBackgrounds to listOf("midnight_felt", "fire", "barrel"),
        )
        for ((catalog, ids) in shipped) {
            for (id in ids) assertEquals(id, catalog.byId(id).id)
        }
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds)) {
            for (art in catalog.all) {
                assertEquals(art, catalog.byId(art.id))
            }
        }
    }

    @Test
    fun everyStyleComesInOneToThreeColours() {
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds)) {
            for (family in catalog.families) {
                assertTrue(family.colours.size in 1..3, "${family.name} has ${family.colours.size} colours")
            }
        }
    }

    @Test
    fun everyDefaultStyleIsCalledClassic() {
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds)) {
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
        assertEquals("Barrel", DiceCupStyles.familyOf("barrel").name)
        assertEquals("Classic", DiceMats.familyOf("fire").name)
        assertEquals("Wood", DiceMats.familyOf("barrel").name)
        for (id in listOf("ivory", "fire", "barrel")) {
            assertEquals("Classic", DiceStyles.familyOf(id).name)
            assertEquals("Classic", TableBackgrounds.familyOf(if (id == "ivory") "midnight_felt" else id).name)
        }
    }

    @Test
    fun unknownIdFallsBackToTheDefaultAndItsStyle() {
        assertEquals("casino_gold", DiceCupStyles.byId("leather").id)
        assertEquals("casino_gold", DiceCupStyles.byId("casino_burgundy").id)
        assertEquals("Classic", DiceCupStyles.familyOf("leather").name)
    }

    @Test
    fun onlyTheClassicStylesAreFree() {
        for (catalog in listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds)) {
            for (family in catalog.families) {
                assertEquals(family.name == "Classic", family.unlock == StyleUnlock.Free, "${family.name}'s unlock is ${family.unlock}")
            }
        }
    }

    @Test
    fun everyAchievementCountLockIsDistinctAndEarnable() {
        val earnable = Achievement.entries.count { it.visibility != AchievementVisibility.SECRET }
        val counts = listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds)
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
    fun theIrishDiceAreASecretStyleUnlockedByLuckOfTheIrish() {
        val irish = DiceStyles.familyOf(IrishFlagDiceStyle.id)
        assertEquals("Irish", irish.name)
        assertTrue(irish.unlock.hiddenWhileLocked)
        assertTrue(Achievement.LUCK_OF_THE_IRISH.unlocksStyle)
        assertEquals(listOf("the 'Irish' dice style"), Achievement.LUCK_OF_THE_IRISH.styleRewards.map { it.description })

        // No number of ordinary achievements unlocks it - only the one.
        val everythingElse = Achievement.entries.filter { it != Achievement.LUCK_OF_THE_IRISH }
        assertEquals(DiceStyles.default, DiceStyles.unlockedById(IrishFlagDiceStyle.id, AchievementsState(everythingElse.associateWith { 0L })))
        val earned = AchievementsState(mapOf(Achievement.LUCK_OF_THE_IRISH to 0L))
        assertEquals(IrishFlagDiceStyle, DiceStyles.unlockedById(IrishFlagDiceStyle.id, earned))
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
        assertFalse(Achievement.BIG_FAN.unlocksStyle)
    }
}
