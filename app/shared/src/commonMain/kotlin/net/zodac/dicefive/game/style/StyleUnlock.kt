package net.zodac.dicefive.game.style

import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.model.Achievement
import org.jetbrains.compose.resources.StringResource

/**
 * What a [StyleGroup] takes to be usable. The lock is on the style as a whole: once it's met, every
 * one of its variants is available.
 *
 * Owning the "Unlock all styles" purchase ([StyleAccess.ownsAllStyles]) meets every lock.
 *
 * A saved pick whose style is still locked isn't erased - [StyleRegistry.unlockedId] just draws the
 * category's default in its place. (Resetting the achievements is the exception: Settings also puts
 * every pick that reset locks back to the default, so it can't return on re-earning the style.)
 */
sealed interface StyleUnlock {

    fun isMet(access: StyleAccess): Boolean

    /**
     * Whether a style behind this lock is left off the Styles screen entirely until it's met, rather
     * than shown with a padlock - true for a [SpecificAchievement] that's an Easter Egg achievement,
     * so secret styles aren't hinted at before being earned.
     */
    val hiddenWhileLocked: Boolean get() = false

    /** Available from the start - every category's Classic style. */
    data object Free : StyleUnlock {
        override fun isMet(access: StyleAccess): Boolean = true
    }

    /** Earning at least [count] achievements. */
    data class AchievementCount(val count: Int) : StyleUnlock {
        init {
            require(count > 0) { "An achievement count of $count isn't a lock" }
        }

        override fun isMet(access: StyleAccess): Boolean = access.ownsAllStyles || access.achievements.countedUnlocks >= count
    }

    /** Earning any one of [achievements] - for a style whose colours each have an achievement of their own (see [StyleVariant.secretAchievement]). */
    data class AnyAchievement(val achievements: List<Achievement>) : StyleUnlock {
        init {
            require(achievements.size > 1) { "Any of ${achievements.size} achievements isn't a choice" }
        }

        override fun isMet(access: StyleAccess): Boolean = access.ownsAllStyles || achievements.any { access.achievements.isUnlocked(it) }
    }

    /** Earning one particular [achievement]. */
    data class SpecificAchievement(val achievement: Achievement) : StyleUnlock {
        override fun isMet(access: StyleAccess): Boolean = access.ownsAllStyles || access.achievements.isUnlocked(achievement)

        override val hiddenWhileLocked: Boolean get() = false
    }
}

/**
 * What the style locks are checked against: the [achievements] earned, and whether the "Unlock all styles" purchase is
 * owned ([net.zodac.dicefive.purchases.PurchasesRepository.ownsAllStyles], or pretended in a debug build), which meets
 * every lock - [StyleUnlock]s and secret variants ([StyleVariant.secretAchievement]) alike.
 */
data class StyleAccess(val achievements: AchievementsState, val ownsAllStyles: Boolean = false)

/** What these picks' locks are checked against. */
val SavedStyles.access: StyleAccess get() = StyleAccess(achievements, ownsAllStyles)

/** A style that earning one particular achievement unlocks, and what it's called: "the 'Irish' dice style" (`ui.style`'s `description`). With [hiddenColours], that many hidden variants of it. */
data class StyleReward(val styleName: StringResource, val categoryNoun: StringResource, val hiddenColours: Int = 0)

/** Every style that earning this achievement unlocks, in the Styles screen's category order. */
val Achievement.styleRewards: List<StyleReward>
    get() = styleRewardsByAchievement[this].orEmpty()

// Worked out once from the registries, which never change, rather than by searching every style each
// time an achievement row or banner asks.
private val styleRewardsByAchievement: Map<Achievement, List<StyleReward>> by lazy {
    StyleCategory.entries.map { it.registry }.flatMap { registry ->
        registry.groups.flatMap { group ->
            val groupReward = (group.unlock as? StyleUnlock.SpecificAchievement)?.let { it.achievement to StyleReward(group.name, registry.noun) }
            // Secret variants of a style that's otherwise available: one reward per achievement, counting its variants.
            val variantRewards = group.variants.mapNotNull { it.secretAchievement }.groupingBy { it }.eachCount()
                .map { (achievement, count) -> achievement to StyleReward(group.name, registry.noun, hiddenColours = count) }
            listOfNotNull(groupReward) + variantRewards
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
    return StyleCategory.entries.map { it.registry }.flatMap { registry ->
        registry.groups
            .filter { group -> (group.unlock as? StyleUnlock.AchievementCount)?.let { it.count in crossed } == true }
            .map { StyleReward(it.name, registry.noun) }
    }
}
