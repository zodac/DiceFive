package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * The label of a filter switch: [off] or [on] by the switch's state. It is always as wide as the longer of the two, so
 * the page's count beside it never moves when the switch is flipped, and the shorter word sits at the end, against the
 * switch. The other label is laid out unseen and unheard.
 */
@Composable
fun FilterSwitchLabel(checked: Boolean, off: String, on: String) {
    Box(contentAlignment = Alignment.CenterEnd) {
        FilterSwitchText(off, visible = !checked)
        FilterSwitchText(on, visible = checked)
    }
}

@Composable
private fun FilterSwitchText(text: String, visible: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.widthIn(max = 104.dp).then(if (visible) Modifier else Modifier.alpha(0f).clearAndSetSemantics { }),
    )
}
