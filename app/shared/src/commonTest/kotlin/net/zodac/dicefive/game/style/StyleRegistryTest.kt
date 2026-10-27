package net.zodac.dicefive.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.DefaultStyleIds
import net.zodac.dicefive.model.Achievement

/** Ids of styles that were a family of their own when released, and are now variants of another: see [StyleRegistryTest]. */
private val IDS_KEPT_FROM_MERGED_FAMILIES = setOf("beaker_blue", "beaker_green")

class StyleRegistryTest {

    private val registries = StyleCategory.entries.map { it.registry }

    @Test
    fun `every id is its family's name then its variant's - as the Styles screen shows them`() {
        for (registry in registries) for (group in registry.groups) for (variant in group.variants) {
            val variantKey = variant.name.key.removePrefix("style_colour_")
            // A style folded into another family since it was released keeps the id it had: ids are permanent.
            val expected = if (variant.id in IDS_KEPT_FROM_MERGED_FAMILIES) variantKey else group.name.key.removePrefix("style_family_") + "_" + variantKey
            assertEquals(expected, variant.id)
        }
    }

    @Test
    fun `each category's default is the id a new player starts with - and an unknown id draws it`() {
        assertEquals(
            listOf(DefaultStyleIds.DICE, DefaultStyleIds.DICE_CUP, DefaultStyleIds.DICE_MAT, DefaultStyleIds.TABLE_BACKGROUND, DefaultStyleIds.SCORE_FRAME),
            registries.map { it.defaultId },
        )
        val everything = StyleAccess(AchievementsState(Achievement.entries.associateWith { 0L }))
        for (registry in registries) assertEquals(registry.defaultId, registry.unlockedId("no_such_style", everything))
    }

    @Test
    fun `the beakers are variants of Glass - open with it - under the ids they were released with`() {
        val glass = DiceCupStyleRegistry.groups.single { it.name.key == "style_family_glass" }
        assertEquals(listOf("glass_clear", "glass_blue", "glass_amber", "beaker_blue", "beaker_green"), glass.variants.map { it.id })
        assertEquals(StyleUnlock.AchievementCount(30), glass.unlock)
        for (id in IDS_KEPT_FROM_MERGED_FAMILIES) assertEquals(glass, DiceCupStyleRegistry.groupOf(id), id)
        assertTrue(DiceCupStyleRegistry.groups.none { it.name.key == "style_family_beaker" })
    }

    @Test
    fun `Nat 20 opens D20 white and black - Critical Success opens blue - and a locked pick draws the default elsewhere`() {
        val d20 = DiceStyleRegistry.ids.filter { it.startsWith("d20_") }
        assertEquals(listOf("d20_white", "d20_black", "d20_blue"), d20)
        val none = StyleAccess(AchievementsState())
        val nat20 = StyleAccess(AchievementsState(mapOf(Achievement.NAT_20 to 0L)))
        val critical = StyleAccess(AchievementsState(mapOf(Achievement.CRITICAL_SUCCESS to 0L)))
        val both = StyleAccess(AchievementsState(mapOf(Achievement.NAT_20 to 0L, Achievement.CRITICAL_SUCCESS to 0L)))

        for (id in d20) {
            assertTrue(!DiceStyleRegistry.isUnlocked(id, none), "$id is open before either achievement")
            assertEquals(DiceStyleRegistry.defaultId, DiceStyleRegistry.unlockedId(id, none))
            assertTrue(DiceStyleRegistry.isUnlocked(id, both), "$id is locked with both earned")
            assertEquals(id, DiceStyleRegistry.unlockedId(id, both))
        }
        for (id in listOf("d20_white", "d20_black")) {
            assertTrue(DiceStyleRegistry.isUnlocked(id, nat20), "$id needs Nat 20")
            assertTrue(!DiceStyleRegistry.isUnlocked(id, critical), "$id is open with only Critical Success")
        }
        assertTrue(DiceStyleRegistry.isUnlocked("d20_blue", critical), "blue needs Critical Success")
        assertTrue(!DiceStyleRegistry.isUnlocked("d20_blue", nat20), "blue is open with only Nat 20")
        assertEquals(DiceStyleRegistry.defaultId, DiceStyleRegistry.unlockedId("d20_blue", nat20))
        // Each achievement carries the star that says it unlocks a style.
        assertTrue(Achievement.NAT_20.unlocksStyle)
        assertTrue(Achievement.CRITICAL_SUCCESS.unlocksStyle)
    }

    @Test
    fun `a style that goes with another of the same name is unlocked by the same thing - in dice - cups - mats and backgrounds`() {
        val unlocks = listOf(DiceStyleRegistry, DiceCupStyleRegistry, DiceMatRegistry, TableBackgroundRegistry)
            .flatMap { registry -> registry.groups.map { it.name.key to it.unlock } }
            .groupBy({ it.first }, { it.second })
        for ((family, locks) in unlocks) assertEquals(1, locks.distinct().size, "$family is unlocked by ${locks.distinct()}")
        // The ones that go together by name, so a new background can't quietly miss its dice or mat.
        for (family in listOf("stone", "circuit", "neon", "meadow", "leather", "gingham", "christmas", "glitter", "rgb")) {
            assertTrue(TableBackgroundRegistry.groups.any { it.name.key == "style_family_$family" }, "no $family background")
        }
    }

    @Test
    fun `no count lock is beyond 100 achievements - and the water set opens at 15`() {
        for (registry in registries) for (group in registry.groups) {
            val unlock = group.unlock
            if (unlock is StyleUnlock.AchievementCount) assertTrue(unlock.count <= 100, "${group.name.key} needs ${unlock.count}")
        }
        val water = listOf(
            DiceStyleRegistry to "style_family_ocean",
            DiceCupStyleRegistry to "style_family_fishbowl",
            DiceMatRegistry to "style_family_ocean",
            TableBackgroundRegistry to "style_family_ocean",
            ScoreFrameRegistry to "style_family_nautical",
        )
        for ((registry, family) in water) {
            assertEquals(StyleUnlock.AchievementCount(15), registry.groups.single { it.name.key == family }.unlock, family)
        }
    }

    @Test
    fun `the Nautical frame offers Chain first - and the Ocean background rolling Waves then still Lagoon`() {
        assertEquals(listOf("nautical_chain", "nautical_rope"), ScoreFrameRegistry.groups.single { it.name.key == "style_family_nautical" }.variants.map { it.id })
        assertEquals(listOf("ocean_waves", "ocean_lagoon"), TableBackgroundRegistry.groups.single { it.name.key == "style_family_ocean" }.variants.map { it.id })
    }

    @Test
    fun `only Classic is free`() {
        for (registry in registries) for (group in registry.groups) {
            val isClassic = group.name.key.contains("classic")
            assertEquals(isClassic, group.unlock == StyleUnlock.Free, "${group.name.key}'s unlock is ${group.unlock}")
        }
    }

    @Test
    fun `a secret style is shown locked until its secret achievement unlocks it`() {
        assertTrue(Achievement.BIG_FAN.unlocksStyle)
        val everythingButBigFan = StyleAccess(AchievementsState(Achievement.entries.filter { it != Achievement.BIG_FAN }.associateWith { 0L }))
        val bigFanEarned = StyleAccess(AchievementsState(mapOf(Achievement.BIG_FAN to 0L)))
        assertEquals(TableBackgroundRegistry.defaultId, TableBackgroundRegistry.unlockedId("glitch_screen", everythingButBigFan))
        assertEquals("glitch_screen", TableBackgroundRegistry.unlockedId("glitch_screen", bigFanEarned))
    }

    @Test
    fun `the unlocked count counts every variant - leaving out a secret one until it's earned`() {
        val none = StyleAccess(AchievementsState())
        val all = StyleAccess(AchievementsState(Achievement.entries.associateWith { 0L }))
        for (registry in registries) {
            val secret = registry.groups.sumOf { group -> group.variants.count { it.secretAchievement != null } }
            val classic = registry.groups.single { it.unlock == StyleUnlock.Free }
            assertEquals(UnlockCount(classic.availableVariants(none).size, registry.ids.size - secret), registry.unlockCount(none), registry.noun.key)
            assertEquals(UnlockCount(registry.ids.size, registry.ids.size), registry.unlockCount(all), registry.noun.key)
            assertTrue(registry.unlockCount(all).complete, registry.noun.key)
            assertTrue(!registry.unlockCount(none).complete, registry.noun.key)
        }
        // Nat 20 unlocks the D20 style and its white and black dice: two more counted, both unlocked.
        val nat20 = StyleAccess(AchievementsState(mapOf(Achievement.NAT_20 to 0L)))
        assertEquals(DiceStyleRegistry.unlockCount(none) + UnlockCount(2, 2), DiceStyleRegistry.unlockCount(nat20))
    }

    @Test
    fun `owning every style meets every lock with nothing earned - secret variants included - so every pick is drawn`() {
        val owner = StyleAccess(AchievementsState(), ownsAllStyles = true)
        for (registry in registries) {
            for (group in registry.groups) {
                assertTrue(group.unlock.isMet(owner), "${group.name.key} is locked for an owner")
                assertEquals(group.variants, group.availableVariants(owner), group.name.key)
            }
            for (id in registry.ids) {
                assertTrue(registry.isUnlocked(id, owner), "$id is locked for an owner")
                assertEquals(id, registry.unlockedId(id, owner))
            }
            assertEquals(UnlockCount(registry.ids.size, registry.ids.size), registry.unlockCount(owner), registry.noun.key)
        }
        // The kinds of lock there are, each met: a count, any one of two achievements, one achievement, an Easter egg's.
        val locks = registries.flatMap { registry -> registry.groups.map { it.unlock } }
        assertTrue(locks.any { it is StyleUnlock.AchievementCount })
        assertTrue(locks.any { it is StyleUnlock.AnyAchievement })
        assertTrue(locks.any { it is StyleUnlock.SpecificAchievement })
        assertTrue(registries.any { registry -> registry.groups.any { group -> group.variants.any { it.secretAchievement != null } } })
    }

    @Test
    fun `without the purchase the locks are the achievements' alone`() {
        val notOwned = StyleAccess(AchievementsState(), ownsAllStyles = false)
        assertEquals(StyleAccess(AchievementsState()), notOwned)
        for (registry in registries) {
            assertTrue(registry.groups.any { !it.unlock.isMet(notOwned) }, registry.noun.key)
            assertTrue(!registry.unlockCount(notOwned).complete, registry.noun.key)
        }
        assertTrue(!DiceStyleRegistry.isUnlocked("d20_white", notOwned))
    }
}
