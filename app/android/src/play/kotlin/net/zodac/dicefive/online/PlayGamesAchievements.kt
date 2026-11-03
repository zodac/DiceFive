package net.zodac.dicefive.online

import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.model.ProgressStyle

/** How an [Achievement] is set up on Play Games. */
internal sealed interface PlayAchievementType {

    /** Unlocked in one go. Includes [ProgressStyle.STREAK] ones, as Play's steps can't go back. */
    data object Standard : PlayAchievementType

    /**
     * [steps] to unlock, each worth [perStep] of the achievement's own target - or, [unlockOnly], none filled until it's
     * earned here, then all of them.
     */
    data class Incremental(val steps: Int, val perStep: Int, val unlockOnly: Boolean = false) : PlayAchievementType
}

/** The most steps Play allows an incremental achievement. */
internal const val PLAY_MAX_STEPS = 10_000

/**
 * Incremental achievements whose target changed after Play published their steps, which Play never lets change: the
 * steps published. Play is sent no progress for these, only the unlock (Full Houses went from 2 hands to all 30).
 */
private val PUBLISHED_PLAY_STEPS: Map<Achievement, Int> = mapOf(Achievement.FULL_HOUSES to 2)

/**
 * A climbing total ([ProgressStyle.CUMULATIVE]) is incremental, at its own target; one over [PLAY_MAX_STEPS]
 * (Professional Roller's 100,000 points) is scaled down to fit. One in [PUBLISHED_PLAY_STEPS] keeps the steps Play
 * published, and is only unlocked there. Everything else is standard.
 */
internal val Achievement.playType: PlayAchievementType
    get() = when {
        this in PUBLISHED_PLAY_STEPS -> {
            val steps = PUBLISHED_PLAY_STEPS.getValue(this)
            PlayAchievementType.Incremental(steps = steps, perStep = (target + steps - 1) / steps, unlockOnly = true)
        }
        progressStyle == ProgressStyle.CUMULATIVE -> {
            val perStep = (target + PLAY_MAX_STEPS - 1) / PLAY_MAX_STEPS
            PlayAchievementType.Incremental(steps = (target + perStep - 1) / perStep, perStep = perStep)
        }
        else -> PlayAchievementType.Standard
    }

/** Whether Play starts this achievement hidden: details unseen until earned, as [AchievementVisibility.HIDDEN] is here. */
internal val Achievement.playHidden: Boolean
    get() = visibility == AchievementVisibility.HIDDEN
