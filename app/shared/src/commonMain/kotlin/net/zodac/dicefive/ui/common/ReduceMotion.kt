package net.zodac.dicefive.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import net.zodac.dicefive.data.settings.AnimationLevel

/**
 * Whether to show as little motion as possible: the system asks apps for less (Android's "Remove animations",
 * iOS's Reduce Motion - [net.zodac.dicefive.platform.PlatformServices.reduceMotion]) or the player has set the
 * app's own "Animations" to Off. Provided once at the root. When it's true, decoration stops moving
 * (looping glows, drifting dice, twinkling stars, the cup's sway, fireworks, page fades), the cup doesn't shake
 * but snaps tipped as the roll lands, and the dice snap straight to their result, so neither scoring nor a CPU
 * waits for a toss. What the game does doesn't change - see `cupShakeMillis` for the one delay that can.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * The player's "Animations" level, as [AnimationLevel.OFF] while the system asks for reduced motion. Provided once
 * at the root, beside [LocalReduceMotion]. Read it through [ambientMotion] and [scorePulse], which also honour a
 * [LocalReduceMotion] provided further down (a Styles tile off screen holds still that way).
 */
val LocalAnimationLevel = compositionLocalOf { AnimationLevel.default }

/**
 * Whether decoration that moves on its own while nothing is happening may move - drifting and floating dice,
 * twinkles, sparkles, a cup's steam or bubbles, the Neon, RGB and Glitch art, the googly dice's pupils. Only at
 * [AnimationLevel.HIGH]; below it each holds the still pose it has under [LocalReduceMotion].
 */
val ambientMotion: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalAnimationLevel.current.ambientMotion && !LocalReduceMotion.current

/** Whether a scoring tile's gold glow pulses, rather than being steady - see [AnimationLevel.scorePulse]. */
val scorePulse: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalAnimationLevel.current.scorePulse && !LocalReduceMotion.current
