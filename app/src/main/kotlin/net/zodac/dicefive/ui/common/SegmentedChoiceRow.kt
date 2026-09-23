package net.zodac.dicefive.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * A single-choice segmented button row for a small, mutually exclusive option set that fits one
 * line - theme, player count, AI difficulty, and anything similar in future. Every option's
 * default checkmark-on-select icon is dropped: at the widths this app uses this component,
 * reserving space for that icon crowded the label out instead (worst case, "Medium" in the AI
 * difficulty row - see `.claude/UI.md`). Selection is still clear from the segment's own
 * colour/border change, same as it is for a [androidx.compose.material3.FilterChip].
 */
@Composable
fun <T> SegmentedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = LocalTextStyle.current,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                label = { Text(text = label(option), style = labelStyle) },
            )
        }
    }
}
