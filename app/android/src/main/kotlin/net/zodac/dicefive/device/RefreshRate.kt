package net.zodac.dicefive.device

import android.view.Window
import androidx.core.content.ContextCompat

// A refresh rate a hair under 60 (59.94Hz panels) still counts as 60.
private const val LOWEST_SMOOTH_REFRESH_RATE = 59f

/**
 * The refresh rate to ask the screen for, from the [supportedRates] it offers at its current resolution, while frames
 * are capped at [maxFramesPerSecond]: the lowest rate from 60Hz up, so a 90 or 120Hz panel that can drop to 60 does
 * (saving its own power, and giving a 60 cap a true 60 frames a second), but never below 60 - scrolling and touch stay
 * smooth at a 30 cap, whose frames divide 60 evenly. 0 - no preference, the system's own choice - when uncapped or
 * when nothing from 60Hz up is offered.
 */
internal fun preferredRefreshRate(supportedRates: List<Float>, maxFramesPerSecond: Int?): Float {
    if (maxFramesPerSecond == null) return 0f
    return supportedRates.filter { it >= LOWEST_SMOOTH_REFRESH_RATE }.minOrNull() ?: 0f
}

/** Asks the screen this [Window] is on for the refresh rate [preferredRefreshRate] picks for [maxFramesPerSecond]. */
internal fun Window.preferRefreshRateFor(maxFramesPerSecond: Int?) {
    val display = ContextCompat.getDisplayOrDefault(context)
    val current = display.mode
    val rates = display.supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .map { it.refreshRate }
    val rate = preferredRefreshRate(rates, maxFramesPerSecond)
    if (attributes.preferredRefreshRate != rate) {
        attributes = attributes.apply { preferredRefreshRate = rate }
    }
}
