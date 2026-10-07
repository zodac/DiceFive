package net.zodac.dicefive.ui.achievements

import androidx.compose.runtime.Composable
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.data.achievements.UnlockedStyle
import net.zodac.dicefive.game.AchievementUpdate
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.achievements_styles_unlocked_announce_join
import net.zodac.dicefive.resources.achievements_styles_unlocked_announcement
import net.zodac.dicefive.resources.achievements_styles_unlocked_earned
import net.zodac.dicefive.resources.achievements_styles_unlocked_more
import net.zodac.dicefive.resources.achievements_styles_unlocked_named
import net.zodac.dicefive.resources.achievements_styles_unlocked_one
import net.zodac.dicefive.resources.achievements_styles_unlocked_spoken_style
import net.zodac.dicefive.resources.achievements_styles_unlocked_title
import net.zodac.dicefive.resources.achievements_styles_unlocked_with
import net.zodac.dicefive.ui.common.joinClauses
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.spokenList
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.game.style.stylesUnlockedByCount

/**
 * Puts everything a recorded [update] earned up as banners: each unlock, then one banner for any styles
 * its unlocks brought within reach by count (see [stylesUnlockedByCount]) - however many, and from
 * however many categories - then each progress nudge. Call it only once [update] is stored, so a
 * banner can never outlive what it announces.
 */
fun announce(update: AchievementUpdate) {
    update.newlyUnlocked.forEach { AchievementEvents.emit(AchievementEvent.Unlocked(it)) }
    val styles = stylesUnlockedByCount(update.countedUnlocksBefore, update.countedUnlocksAfter)
    if (styles.isNotEmpty()) {
        AchievementEvents.emit(
            AchievementEvent.StylesUnlocked(styles.map { UnlockedStyle(it.styleName, it.categoryNoun) }, update.countedUnlocksAfter),
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
 * A styles banner's description, written to fit its two lines: the count that unlocked them, then the
 * styles - "the 'Frosted' dice style", "'Frosted' dice and 'Velvet' mat", or the first two "and 3 more".
 */
@Composable
fun stylesUnlockedDescription(event: AchievementEvent.StylesUnlocked): String {
    val styles = event.styles
    val named = styles.take(STYLES_NAMED_ON_BANNER).map { stringResource(Res.string.achievements_styles_unlocked_named, stringResource(it.name), stringResource(it.categoryNoun)) }
    val earned = pluralStringResource(Res.plurals.achievements_styles_unlocked_earned, event.achievementCount, event.achievementCount)
    val which = when {
        styles.size == 1 -> stringResource(Res.string.achievements_styles_unlocked_one, named.single())
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
    val earned = pluralStringResource(Res.plurals.achievements_styles_unlocked_earned, event.achievementCount, event.achievementCount)
    return stringResource(Res.string.achievements_styles_unlocked_announce_join, unlocked, earned)
}
