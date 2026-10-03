package net.zodac.dicefive.ui.common

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether to show as little motion as possible: the system asks apps for less (Android's "Remove animations",
 * iOS's Reduce Motion - [net.zodac.dicefive.platform.PlatformServices.reduceMotion]) or the player has turned on
 * the app's own "Remove animations" setting. Provided once at the root. When it's true, decoration stops moving
 * (looping glows, drifting dice, twinkling stars, the cup's sway, fireworks, page fades), the cup doesn't shake
 * but snaps tipped as the roll lands, and the dice snap straight to their result, so neither scoring nor a CPU
 * waits for a toss. What the game does doesn't change - see `cupShakeMillis` for the one delay that can.
 */
val LocalReduceMotion = compositionLocalOf { false }
