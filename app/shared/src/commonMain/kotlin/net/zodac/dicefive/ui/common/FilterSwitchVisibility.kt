package net.zodac.dicefive.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Whether a "Locked only" / "Unlocked only" switch is shown, and whether it can be changed. */
class FilterSwitchState(val shown: Boolean, val enabled: Boolean) {
    /** The filter as applied: a switch that isn't shown filters nothing, whatever was saved. */
    fun applied(filterOn: Boolean): Boolean = filterOn && shown
}

/**
 * [allUnlocked] is null until it's known. The switch is left off the page once everything is unlocked, as there's nothing to filter. It stays, [FilterSwitchState.enabled]
 * false, when the last unlock comes while the page is open with the filter on, so the list doesn't change under the player
 * with no switch to explain it; it goes on leaving the page. While some are still locked, it's always shown and enabled.
 */
@Composable
fun rememberFilterSwitchState(allUnlocked: Boolean?, filterOn: Boolean): FilterSwitchState {
    // Whether the page has been open while something was still locked: not state, as it only matters at the next composition.
    val seen = remember { booleanArrayOf(allUnlocked == false) }
    if (allUnlocked == false) seen[0] = true
    // Not known yet (still loading): nothing to hide or disable.
    if (allUnlocked == null) return FilterSwitchState(shown = true, enabled = true)
    return FilterSwitchState(shown = !allUnlocked || (seen[0] && filterOn), enabled = !allUnlocked)
}
