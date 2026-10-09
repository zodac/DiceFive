package net.zodac.dicefive.ui.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.achievements.UnlockedStyle
import net.zodac.dicefive.data.achievements.countsTowardStyleLocks
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.style_family_frosted
import net.zodac.dicefive.resources.style_family_greek
import net.zodac.dicefive.resources.style_family_martini
import net.zodac.dicefive.resources.style_family_maths
import net.zodac.dicefive.resources.style_family_wreath
import net.zodac.dicefive.resources.style_noun_dice
import net.zodac.dicefive.resources.style_noun_frame
import net.zodac.dicefive.ui.game.style.StyleCatalogs
import net.zodac.dicefive.ui.game.style.StyleReward
import net.zodac.dicefive.ui.game.style.StyleUnlock
import net.zodac.dicefive.ui.game.style.stylesUnlockedByCount

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
        // One short or one past does not: each neighbour unlocks a style of its own (a frame), but never Frosted.
        assertEquals(listOf(StyleReward(Res.string.style_family_frosted, Res.string.style_noun_dice)), stylesUnlockedByCount(22, 23))
        assertEquals(listOf(StyleReward(Res.string.style_family_wreath, Res.string.style_noun_frame)), stylesUnlockedByCount(21, 22))
        assertEquals(listOf(StyleReward(Res.string.style_family_greek, Res.string.style_noun_frame)), stylesUnlockedByCount(23, 24))

        for ((count, reward) in countLocks) {
            assertEquals(listOf(reward), stylesUnlockedByCount(count - 1, count), "${reward.styleName} ${reward.categoryNoun}")
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
        assertEquals(30, styleBanners.single().achievementCount)
        assertEquals(AchievementEvent.StylesUnlocked::class, events.last { it !is AchievementEvent.Progressed }::class)
        assertEquals(10, events.count { it is AchievementEvent.Unlocked })

        // Even for an achievement that unlocks a style itself.
        val ownStyle = AchievementEngine.unlockNow(setOf(Achievement.THE_SOLUTION), stateWith(0), 0L)
        assertEquals(listOf<AchievementEvent>(AchievementEvent.Unlocked(Achievement.THE_SOLUTION)), events { announce(ownStyle) })
    }
}
