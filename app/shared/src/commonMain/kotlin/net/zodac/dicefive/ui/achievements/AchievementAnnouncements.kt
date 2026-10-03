package net.zodac.dicefive.ui.achievements

import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.data.achievements.UnlockedStyle
import net.zodac.dicefive.game.AchievementUpdate
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
fun stylesUnlockedTitle(event: AchievementEvent.StylesUnlocked): String =
    if (event.styles.size == 1) "Style Unlocked" else "Styles Unlocked"

/**
 * A styles banner's description, written to fit its two lines: the count that unlocked them, then the
 * styles - "the 'Frosted' dice style", "'Frosted' dice and 'Velvet' mat", or the first two "and 3 more".
 */
fun stylesUnlockedDescription(event: AchievementEvent.StylesUnlocked): String {
    val styles = event.styles
    val named = styles.take(STYLES_NAMED_ON_BANNER).map { "'${it.name}' ${it.categoryNoun}" }
    val list = when {
        styles.size == 1 -> "the ${named.single()} style"
        styles.size <= STYLES_NAMED_ON_BANNER -> named.joinToString(" and ")
        else -> "${named.joinToString(", ")} and ${styles.size - STYLES_NAMED_ON_BANNER} more"
    }
    return "Earned ${event.achievementCount} achievements: $list"
}

/**
 * What TalkBack says for a styles banner: every style, not just the two the banner has room for, and
 * without the quote marks it draws round each name - "Styles unlocked: Frosted dice, Velvet mat.
 * Earned 23 achievements".
 */
fun stylesUnlockedAnnouncement(event: AchievementEvent.StylesUnlocked): String {
    val styles = event.styles.joinToString(", ") { "${it.name} ${it.categoryNoun}" }
    return "${stylesUnlockedTitle(event).lowercase().replaceFirstChar { it.uppercase() }}: $styles. Earned ${event.achievementCount} achievements"
}
