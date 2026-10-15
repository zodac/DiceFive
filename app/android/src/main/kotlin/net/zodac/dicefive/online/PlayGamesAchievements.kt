package net.zodac.dicefive.online

import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.model.ProgressStyle

/** How an [Achievement] is set up on Play Games. */
internal sealed interface PlayAchievementType {

    /** Unlocked in one go. Includes [ProgressStyle.STREAK] ones, as Play's steps can't go back. */
    data object Standard : PlayAchievementType

    /** [steps] to unlock, each worth [perStep] of the achievement's own target. */
    data class Incremental(val steps: Int, val perStep: Int) : PlayAchievementType
}

/** The most steps Play allows an incremental achievement. */
internal const val PLAY_MAX_STEPS = 10_000

/**
 * A climbing total ([ProgressStyle.CUMULATIVE]) is incremental, at its own target; one over [PLAY_MAX_STEPS]
 * (Professional Roller's 100,000 points) is scaled down to fit. Everything else is standard.
 */
internal val Achievement.playType: PlayAchievementType
    get() = when (progressStyle) {
        ProgressStyle.CUMULATIVE -> {
            val perStep = (target + PLAY_MAX_STEPS - 1) / PLAY_MAX_STEPS
            PlayAchievementType.Incremental(steps = (target + perStep - 1) / perStep, perStep = perStep)
        }
        else -> PlayAchievementType.Standard
    }

/** Whether Play starts this achievement hidden: details unseen until earned, as [AchievementVisibility.HIDDEN] is here. */
internal val Achievement.playHidden: Boolean
    get() = visibility == AchievementVisibility.HIDDEN

/** Whether this achievement goes to Play at all: [AchievementVisibility.SECRET] ones stay local. */
internal val Achievement.onPlay: Boolean
    get() = visibility != AchievementVisibility.SECRET
