package net.zodac.dicefive.ui.common

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the system asks apps for less motion (Android's "Remove animations", iOS's Reduce Motion),
 * provided once at the root from [net.zodac.dicefive.platform.PlatformServices.reduceMotion]. When it's true,
 * decoration stops moving (looping glows, drifting dice, twinkling stars, the cup's sway, fireworks) and
 * the dice roll becomes the "simple" one; what the game does, and when, doesn't change - see
 * `cupShakeMillis` for the one delay that does, and why.
 */
val LocalReduceMotion = compositionLocalOf { false }
