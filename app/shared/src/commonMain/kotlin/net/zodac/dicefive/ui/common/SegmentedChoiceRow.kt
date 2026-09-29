package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle

/**
 * A single-choice segmented button row for a small, mutually exclusive option set that fits one
 * line - theme, player count, AI difficulty, and anything similar in future. Every option's
 * default checkmark-on-select icon is dropped: at the widths this app uses this component,
 * reserving space for that icon crowded the label out instead (worst case, "Medium" in the AI
 * difficulty row - see `.claude/UI.md`). Selection is still clear from the segment's own
 * colour/border change, same as it is for a [androidx.compose.material3.FilterChip].
 *
 * [glyph] swaps an option's text for an icon, for an option a symbol says better than a word (the
 * turn timer's "no timer"). [label] is still required for that option: it becomes the icon's
 * content description, so a screen reader announces the same thing the text would have said.
 *
 * [enabled] false greys out every segment and ignores taps, while still showing which one is
 * picked - for a choice the rest of the form has overridden (the turn timer under a game mode that
 * sets its own).
 */
@Composable
fun <T> SegmentedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = LocalTextStyle.current,
    glyph: (T) -> ImageVector? = { null },
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            val optionGlyph = glyph(option)
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                label = {
                    if (optionGlyph != null) {
                        Icon(
                            imageVector = optionGlyph,
                            contentDescription = label(option),
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    } else {
                        Text(text = label(option), style = labelStyle)
                    }
                },
            )
        }
    }
}
