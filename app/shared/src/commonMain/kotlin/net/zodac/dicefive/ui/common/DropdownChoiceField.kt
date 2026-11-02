package net.zodac.dicefive.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_dropdown_collapsed
import net.zodac.dicefive.resources.common_dropdown_expanded
import net.zodac.dicefive.resources.common_dropdown_selected
import net.zodac.dicefive.resources.common_picker_choose_action
import net.zodac.dicefive.ui.icons.Symbols

/** The field's height: over the 48dp touch target, and tall enough for a large font's line. */
private val FIELD_MIN_HEIGHT = 52.dp

private val OPTION_MIN_HEIGHT = 48.dp

/**
 * A drop-down for picking one of [options]: a field showing the [selected] one, opening a menu as wide as the field
 * that lists them all, the picked one marked. It is for a choice too long for a [SegmentedChoiceRow].
 *
 * [optionText] is an option's visible and spoken name. [label] names the field for a screen reader ("Leaderboard,
 * Standard, collapsed"). A divider is drawn under the first option when [dividerAfterFirst] is set, for an "all" entry
 * that stands apart from the rest.
 *
 * TalkBack: the field is one drop-down with its value and whether it is expanded; each option is one radio button,
 * "Selected" on the picked one, in a set of known size.
 */
@Composable
fun <T> DropdownChoiceField(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: String,
    optionText: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    dividerAfterFirst: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    var fieldWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val selectedText = optionText(selected)
    val stateText = stringResource(if (open) Res.string.common_dropdown_expanded else Res.string.common_dropdown_collapsed)
    val chooseLabel = stringResource(Res.string.common_picker_choose_action, label)
    // DropdownMenu positions itself against its parent, so the field and the menu share this Box.
    Box(modifier = modifier.onSizeChanged { fieldWidth = with(density) { it.width.toDp() } }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FIELD_MIN_HEIGHT)
                .clearAndSetSemantics {
                    contentDescription = "$label, $selectedText"
                    stateDescription = stateText
                    role = Role.DropdownList
                    onClick(label = chooseLabel) {
                        open = true
                        true
                    }
                }
                .clickable(onClickLabel = chooseLabel, role = Role.DropdownList) { open = true },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                )
                // Decorative: the drop-down's role and state are the field's own.
                Icon(
                    imageVector = if (open) Symbols.KeyboardArrowUp else Symbols.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(fieldWidth).semantics { collectionInfo = CollectionInfo(rowCount = options.size, columnCount = 1) },
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selected
                val text = optionText(option)
                val selectedState = stringResource(Res.string.common_dropdown_selected)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Symbols.Check,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        )
                    },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                    modifier = Modifier
                        .heightIn(min = OPTION_MIN_HEIGHT)
                        .clearAndSetSemantics {
                            contentDescription = text
                            role = Role.RadioButton
                            this.selected = isSelected
                            if (isSelected) stateDescription = selectedState
                            collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                            onClick(label = null) {
                                onSelect(option)
                                open = false
                                true
                            }
                        },
                )
                if (dividerAfterFirst && index == 0) HorizontalDivider()
            }
        }
    }
}
