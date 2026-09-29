package net.zodac.dicefive.ui.game.style

import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementVisibility

/**
 * What a [StyleFamily] takes to be usable. The lock is on the style as a whole: once it's met, every
 * one of its colours is available.
 *
 * A saved pick whose style is still locked isn't erased - [StyleCatalog.unlockedById] just draws the
 * category's default in its place - so re-locking (a reset of the achievements) and unlocking again
 * brings the player's own pick back rather than losing it.
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
data class StyleReward(val styleName: String, val categoryNoun: String) {
    val description: String get() = "the '$styleName' $categoryNoun style"
}

/** Every style that earning this achievement unlocks, in the Styles screen's category order. */
val Achievement.styleRewards: List<StyleReward>
    get() = styleRewardsByAchievement[this].orEmpty()

// Worked out once from the catalogs, which never change, rather than by searching every style each
// time an achievement row or banner asks.
private val styleRewardsByAchievement: Map<Achievement, List<StyleReward>> by lazy {
    StyleCatalogs.flatMap { catalog ->
        catalog.families.mapNotNull { family ->
            (family.unlock as? StyleUnlock.SpecificAchievement)?.let { it.achievement to StyleReward(family.name, catalog.noun) }
        }
    }.groupBy({ it.first }, { it.second })
}

/**
 * Whether earning this achievement is what unlocks some style - its row and unlock banner carry a
 * star to say there's something extra to go and find on the Styles screen.
 */
val Achievement.unlocksStyle: Boolean
    get() = styleRewards.isNotEmpty()
