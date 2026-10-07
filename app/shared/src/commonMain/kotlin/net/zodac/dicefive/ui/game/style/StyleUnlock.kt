package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.styles_reward_hidden_colours
import net.zodac.dicefive.resources.styles_reward_style
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.stringResource
import org.jetbrains.compose.resources.StringResource

/**
 * What a [StyleFamily] takes to be usable. The lock is on the style as a whole: once it's met, every
 * one of its colours is available.
 *
 * A saved pick whose style is still locked isn't erased - [StyleCatalog.unlockedById] just draws the
 * category's default in its place. (Resetting the achievements is the exception: Settings also puts
 * every pick that reset locks back to the default, so it can't return on re-earning the style.)
 */
sealed interface StyleUnlock {

    fun isMet(achievements: AchievementsState): Boolean

    /**
     * Whether a style behind this lock is left off the Styles screen entirely until it's met, rather
     * than shown with a padlock - true for a [SpecificAchievement] that's itself
     * [AchievementVisibility.SECRET], since a secret achievement is never hinted at before it's earned.
     */
    val hiddenWhileLocked: Boolean get() = false

    /** Available from the start - every category's Classic style. */
    data object Free : StyleUnlock {
        override fun isMet(achievements: AchievementsState): Boolean = true
    }

    /** Earning at least [count] achievements, of any kind but [AchievementVisibility.SECRET]. */
    data class AchievementCount(val count: Int) : StyleUnlock {
        init {
            require(count > 0) { "An achievement count of $count isn't a lock" }
        }

        override fun isMet(achievements: AchievementsState): Boolean = achievements.countedUnlocks >= count
    }

    /** Earning one particular [achievement]. */
    data class SpecificAchievement(val achievement: Achievement) : StyleUnlock {
        override fun isMet(achievements: AchievementsState): Boolean = achievements.isUnlocked(achievement)

        override val hiddenWhileLocked: Boolean get() = achievement.visibility == AchievementVisibility.SECRET
    }
}

/** A style that earning one particular achievement unlocks, and what it's called: "the 'Irish' dice style". */
data class StyleReward(val styleName: StringResource, val categoryNoun: StringResource, val hiddenColours: Int = 0) {
    /** What it's called in a sentence, in the player's language. */
    @Composable
    fun description(): String {
        val name = stringResource(styleName)
        val noun = stringResource(categoryNoun)
        return if (hiddenColours == 0) {
            stringResource(Res.string.styles_reward_style, name, noun)
        } else {
            pluralStringResource(Res.plurals.styles_reward_hidden_colours, hiddenColours, name, noun)
        }
    }
}

/** Every style that earning this achievement unlocks, in the Styles screen's category order. */
val Achievement.styleRewards: List<StyleReward>
    get() = styleRewardsByAchievement[this].orEmpty()

// Worked out once from the catalogs, which never change, rather than by searching every style each
// time an achievement row or banner asks.
private val styleRewardsByAchievement: Map<Achievement, List<StyleReward>> by lazy {
    StyleCatalogs.flatMap { catalog ->
        catalog.families.flatMap { family ->
            val familyReward = (family.unlock as? StyleUnlock.SpecificAchievement)?.let { it.achievement to StyleReward(family.name, catalog.noun) }
            // Secret colours of a style that's otherwise available: one reward per achievement, counting its colours.
            val colourRewards = family.colours.mapNotNull { it.secretAchievement }.groupingBy { it }.eachCount()
                .map { (achievement, count) -> achievement to StyleReward(family.name, catalog.noun, hiddenColours = count) }
            listOfNotNull(familyReward) + colourRewards
        }
    }.groupBy({ it.first }, { it.second })
}

/**
 * Whether earning this achievement is what unlocks some style - its row and unlock banner carry a
 * star to say there's something extra to go and find on the Styles screen.
 */
val Achievement.unlocksStyle: Boolean
    get() = styleRewards.isNotEmpty()

/**
 * Every style whose [StyleUnlock.AchievementCount] lock is met at [countAfter] counted achievements
 * but wasn't at [countBefore], in the Styles screen's category order - what one update's "style
 * unlocked" banner names. A style locked behind one particular achievement is never here.
 */
fun stylesUnlockedByCount(countBefore: Int, countAfter: Int): List<StyleReward> {
    if (countAfter <= countBefore) return emptyList()
    val crossed = (countBefore + 1)..countAfter
    return StyleCatalogs.flatMap { catalog ->
        catalog.families
            .filter { family -> (family.unlock as? StyleUnlock.AchievementCount)?.let { it.count in crossed } == true }
            .map { StyleReward(it.name, catalog.noun) }
    }
}
