package net.zodac.dicefive

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput

/**
 * Taps a Rules tab as a finger would: on its visible label, toward the middle of the screen. A tab cut off at an end
 * of its row lies partly under that end's fade and scroll chevron, so a tap in the middle of what's visible of it (as
 * `performClick` and a bare `click()` do) can land on the chevron, which scrolls the row instead of picking the tab.
 */
internal fun SemanticsNodeInteractionsProvider.tapTab(tab: SemanticsNodeInteraction) {
    val centre = tab.fetchSemanticsNode().boundsInRoot.center.x
    val middle = onRoot().fetchSemanticsNode().size.width / 2f
    tab.performTouchInput { click(Offset(if (centre > middle) width * 0.2f else width * 0.8f, centerY)) }
}
