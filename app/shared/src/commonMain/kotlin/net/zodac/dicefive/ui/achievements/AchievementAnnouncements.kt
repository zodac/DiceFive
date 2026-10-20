package net.zodac.dicefive.ui.achievements

import androidx.compose.runtime.Composable
import net.zodac.dicefive.game.AchievementUpdate
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.achievements_styles_unlocked_announce_join
import net.zodac.dicefive.resources.achievements_styles_unlocked_announcement
import net.zodac.dicefive.resources.achievements_styles_unlocked_earned
import net.zodac.dicefive.resources.achievements_styles_unlocked_earned_achievement
import net.zodac.dicefive.resources.achievements_styles_unlocked_earned_achievement_spoken
import net.zodac.dicefive.resources.achievements_styles_unlocked_more
import net.zodac.dicefive.resources.achievements_styles_unlocked_named
import net.zodac.dicefive.resources.achievements_styles_unlocked_one
import net.zodac.dicefive.resources.achievements_styles_unlocked_spoken_style
import net.zodac.dicefive.resources.achievements_styles_unlocked_title
import net.zodac.dicefive.resources.achievements_styles_unlocked_with
import net.zodac.dicefive.resources.styles_reward_hidden_colours
import net.zodac.dicefive.ui.common.joinClauses
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.spokenList
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.game.style.styleRewards
import net.zodac.dicefive.ui.game.style.stylesUnlockedByCount

/**
 * Puts everything a recorded [update] earned up as banners: each unlock, followed by a styles banner for whatever
 * styles are locked behind that achievement ([styleRewards]); then one banner for any styles its unlocks brought
 * within reach by count (see [stylesUnlockedByCount]) - however many, and from however many categories - then each
 * progress nudge. Call it only once [update] is stored, so a banner can never outlive what it announces.
 */
fun announce(update: AchievementUpdate) {
    update.newlyUnlocked.forEach { achievement ->
        AchievementEvents.emit(AchievementEvent.Unlocked(achievement))
        val rewards = achievement.styleRewards
        if (rewards.isNotEmpty()) {
            AchievementEvents.emit(
                AchievementEvent.StylesUnlocked(
                    rewards.map { UnlockedStyle(it.styleName, it.categoryNoun, it.hiddenColours) },
                    StylesEarnedBy.OneAchievement(achievement),
                ),
            )
        }
    }
    val styles = stylesUnlockedByCount(update.countedUnlocksBefore, update.countedUnlocksAfter)
    if (styles.isNotEmpty()) {
        AchievementEvents.emit(
            AchievementEvent.StylesUnlocked(
                styles.map { UnlockedStyle(it.styleName, it.categoryNoun) },
                StylesEarnedBy.Count(update.countedUnlocksAfter),
            ),
        )
    }
    update.progressed.forEach { AchievementEvents.emit(AchievementEvent.Progressed(it.achievement, it.previous, it.current)) }
}

/** How many styles a styles banner names before it just counts the rest - two fit its two lines. */
private const val STYLES_NAMED_ON_BANNER = 2

/** A styles banner's title: "Style Unlocked", or "Styles Unlocked" for more than one. */
@Composable
fun stylesUnlockedTitle(event: AchievementEvent.StylesUnlocked): String {
    val count = event.styles.size
    return pluralStringResource(Res.plurals.achievements_styles_unlocked_title, count)
}

/**
 * A styles banner's description, written to fit its two lines: what unlocked them (the count, or the achievement),
 * then the styles - "the 'Frosted' dice style", "the hidden 'Irish' dice colour", "'Frosted' dice and 'Velvet' mat",
 * "'Starry' dice, mat and background" (one achievement's, of one name), or the first two "and 3 more".
 */
@Composable
fun stylesUnlockedDescription(event: AchievementEvent.StylesUnlocked): String {
    val styles = event.styles
    val named = styles.take(STYLES_NAMED_ON_BANNER).map { stringResource(Res.string.achievements_styles_unlocked_named, stringResource(it.name), stringResource(it.categoryNoun)) }
    val earned = when (val earnedBy = event.earnedBy) {
        is StylesEarnedBy.Count -> pluralStringResource(Res.plurals.achievements_styles_unlocked_earned, earnedBy.achievementCount, earnedBy.achievementCount)
        is StylesEarnedBy.OneAchievement -> stringResource(Res.string.achievements_styles_unlocked_earned_achievement, stringResource(earnedBy.achievement.title))
    }
    val which = when {
        styles.size == 1 && styles.single().hiddenColours > 0 -> styles.single().let {
            pluralStringResource(Res.plurals.styles_reward_hidden_colours, it.hiddenColours, stringResource(it.name), stringResource(it.categoryNoun))
        }
        styles.size == 1 -> stringResource(Res.string.achievements_styles_unlocked_one, named.single())
        // One achievement's styles of one name are named once: "'Starry' dice, mat and background".
        event.earnedBy is StylesEarnedBy.OneAchievement && styles.all { it.name == styles[0].name && it.hiddenColours == 0 } ->
            stringResource(Res.string.achievements_styles_unlocked_named, stringResource(styles[0].name), spokenList(styles.map { stringResource(it.categoryNoun) }))
        styles.size <= STYLES_NAMED_ON_BANNER -> spokenList(named)
        else -> {
            val more = styles.size - STYLES_NAMED_ON_BANNER
            pluralStringResource(Res.plurals.achievements_styles_unlocked_more, more, joinClauses(named), more)
        }
    }
    return stringResource(Res.string.achievements_styles_unlocked_with, earned, which)
}

/**
 * What TalkBack says for a styles banner: every style, not just the two the banner has room for, and
 * without the quote marks it draws round each name - "Styles unlocked: Frosted dice, Velvet mat.
 * Earned 23 achievements".
 */
@Composable
fun stylesUnlockedAnnouncement(event: AchievementEvent.StylesUnlocked): String {
    val styles = joinClauses(event.styles.map { stringResource(Res.string.achievements_styles_unlocked_spoken_style, stringResource(it.name), stringResource(it.categoryNoun)) })
    val unlocked = pluralStringResource(Res.plurals.achievements_styles_unlocked_announcement, event.styles.size, styles)
    val earned = when (val earnedBy = event.earnedBy) {
        is StylesEarnedBy.Count -> pluralStringResource(Res.plurals.achievements_styles_unlocked_earned, earnedBy.achievementCount, earnedBy.achievementCount)
        is StylesEarnedBy.OneAchievement -> stringResource(Res.string.achievements_styles_unlocked_earned_achievement_spoken, stringResource(earnedBy.achievement.title))
    }
    return stringResource(Res.string.achievements_styles_unlocked_announce_join, unlocked, earned)
}
