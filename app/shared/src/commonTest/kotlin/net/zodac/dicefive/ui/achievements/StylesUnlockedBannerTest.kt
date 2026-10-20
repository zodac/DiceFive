package net.zodac.dicefive.ui.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.achievements.countsTowardStyleLocks
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.style_family_fishbowl
import net.zodac.dicefive.resources.style_family_garden
import net.zodac.dicefive.resources.style_family_martini
import net.zodac.dicefive.resources.style_family_maths
import net.zodac.dicefive.resources.style_family_nautical
import net.zodac.dicefive.resources.style_family_ocean
import net.zodac.dicefive.resources.style_noun_background
import net.zodac.dicefive.resources.style_noun_dice
import net.zodac.dicefive.resources.style_noun_dice_cup
import net.zodac.dicefive.resources.style_noun_frame
import net.zodac.dicefive.resources.style_noun_mat
import net.zodac.dicefive.ui.style.StyleCatalogs
import net.zodac.dicefive.ui.style.StyleReward
import net.zodac.dicefive.ui.style.StyleUnlock
import net.zodac.dicefive.ui.style.styleRewards
import net.zodac.dicefive.ui.style.stylesUnlockedByCount

@OptIn(ExperimentalCoroutinesApi::class)
class StylesUnlockedBannerTest {

    private val countedAchievements = Achievement.entries.filter { it.countsTowardStyleLocks }

    /** Every count-locked style, with the count that unlocks it. */
    private val countLocks: List<Pair<Int, StyleReward>> = StyleCatalogs.flatMap { catalog ->
        catalog.families.mapNotNull { family ->
            (family.unlock as? StyleUnlock.AchievementCount)?.let { it.count to StyleReward(family.name, catalog.noun) }
        }
    }

    private fun stateWith(counted: Int, vararg extra: Achievement): AchievementsState =
        AchievementsState((countedAchievements.take(counted) + extra).associateWith { 0L })

    private fun events(block: () -> Unit): List<AchievementEvent> {
        val events = mutableListOf<AchievementEvent>()
        runTest(UnconfinedTestDispatcher()) {
            val collector = launch { AchievementEvents.events.collect { events += it } }
            block()
            collector.cancel()
        }
        return events
    }

    @Test
    fun `each count-locked style is unlocked by the step onto its count and no other - a jump over several unlocking each on the way`() {
        // One short or one past does not: Garden dice are unlocked at 95, but no styles are unlocked at 94 or 96.
        assertEquals(listOf(StyleReward(Res.string.style_family_garden, Res.string.style_noun_dice)), stylesUnlockedByCount(94, 95))
        assertEquals(emptyList(), stylesUnlockedByCount(93, 94))
        assertEquals(emptyList(), stylesUnlockedByCount(95, 96))
        // The water set opens together, at one count: dice, cup, mat, background and frame, in the Styles screen's order.
        assertEquals(
            listOf(
                StyleReward(Res.string.style_family_ocean, Res.string.style_noun_dice),
                StyleReward(Res.string.style_family_fishbowl, Res.string.style_noun_dice_cup),
                StyleReward(Res.string.style_family_ocean, Res.string.style_noun_mat),
                StyleReward(Res.string.style_family_ocean, Res.string.style_noun_background),
                StyleReward(Res.string.style_family_nautical, Res.string.style_noun_frame),
            ),
            stylesUnlockedByCount(14, 15),
        )

        for ((count, reward) in countLocks) {
            assertTrue(stylesUnlockedByCount(count - 1, count).contains(reward), "${reward.styleName} ${reward.categoryNoun} missing at $count")
        }
        val counts = countLocks.map { it.first }.toSet()
        for (count in 1..countedAchievements.size) {
            if (count !in counts) assertEquals(emptyList(), stylesUnlockedByCount(count - 1, count), "at $count")
        }

        // Across categories - and no specifically locked one.
        val all = stylesUnlockedByCount(0, countedAchievements.size)
        assertEquals(countLocks.map { it.second }.toSet(), all.toSet())
        assertTrue(all.map { it.categoryNoun }.toSet().size > 1, "$all")
        assertTrue(all.none { it.styleName == Res.string.style_family_maths || it.styleName == Res.string.style_family_martini }, "$all")
    }

    @Test
    fun `an update crossing counts announces one styles banner after its unlocks - and none when no count is reached`() {
        // An update knows the counted achievements before and after it.
        val update = AchievementEngine.unlockNow(setOf(countedAchievements[22]), stateWith(22), 0L)
        assertEquals(22, update.countedUnlocksBefore)
        assertEquals(23, update.countedUnlocksAfter)

        val crossing = AchievementEngine.unlockNow(countedAchievements.subList(20, 30).toSet(), stateWith(20), 0L)
        val expectedStyles = stylesUnlockedByCount(20, 30)
        assertTrue(expectedStyles.size > 1, "the test needs several styles between 20 and 30, got $expectedStyles")
        val events = events { announce(crossing) }
        val styleBanners = events.filterIsInstance<AchievementEvent.StylesUnlocked>()
        assertEquals(1, styleBanners.size, "$events")
        assertEquals(expectedStyles.map { UnlockedStyle(it.styleName, it.categoryNoun) }, styleBanners.single().styles)
        assertEquals(StylesEarnedBy.Count(30), styleBanners.single().earnedBy)
        assertEquals(AchievementEvent.StylesUnlocked::class, events.last { it !is AchievementEvent.Progressed }::class)
        assertEquals(10, events.count { it is AchievementEvent.Unlocked })
    }

    @Test
    fun `an achievement with styles behind it is followed by their own banner - before the count's and the progress`() {
        val ownStyle = AchievementEngine.unlockNow(setOf(Achievement.THE_SOLUTION), stateWith(0), 0L)
        assertEquals(
            listOf(
                AchievementEvent.Unlocked(Achievement.THE_SOLUTION),
                AchievementEvent.StylesUnlocked(
                    listOf(UnlockedStyle(Res.string.style_family_maths, Res.string.style_noun_dice)),
                    StylesEarnedBy.OneAchievement(Achievement.THE_SOLUTION),
                ),
            ),
            events { announce(ownStyle) },
        )

        // With a count crossed too (Garden dice at 95): the achievement's banner, its styles, then the count's styles.
        val crossing = AchievementEngine.unlockNow(setOf(countedAchievements[94], Achievement.BIG_FAN), stateWith(94), 0L)
        val banners = events { announce(crossing) }.filter { it !is AchievementEvent.Progressed }
        val bigFan = banners.indexOf(AchievementEvent.Unlocked(Achievement.BIG_FAN))
        val ownBanner = banners[bigFan + 1] as AchievementEvent.StylesUnlocked
        assertEquals(StylesEarnedBy.OneAchievement(Achievement.BIG_FAN), ownBanner.earnedBy)
        assertEquals(2, ownBanner.styles.size)
        assertTrue((banners.last() as AchievementEvent.StylesUnlocked).earnedBy is StylesEarnedBy.Count, "$banners")

        // An achievement with no style behind it gets no styles banner of its own.
        val plainAchievement = countedAchievements.first { it.styleRewards.isEmpty() }
        val plain = AchievementEngine.unlockNow(setOf(plainAchievement), stateWith(0), 0L)
        assertTrue(events { announce(plain) }.none { it is AchievementEvent.StylesUnlocked && it.earnedBy is StylesEarnedBy.OneAchievement })
    }
}
