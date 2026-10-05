package net.zodac.dicefive.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/*
 * The app's dropdown-and-modal pickers. Every colour, shape, size and text style of the closed field and
 * of the modal lives in the two shell composables here - [PickerField] and [PickerDialog] - and the
 * rows share [PickerRowText], so a re-theme is made in this one file. [ChoicePicker] (pick one) and
 * [ModifierPicker] (switch several on, each with an optional value) are only the contents they hold.
 */

/** The most the modal's list may grow before it scrolls - about five two-line options. */
private val PICKER_LIST_MAX_HEIGHT = 340.dp

/**
 * The closed state of every picker: an outlined field with a [headline], a [supporting] line under
 * it and a dropdown arrow. One merged node for a screen reader - a dropdown named [title] whose
 * state is the headline and supporting line.
 */
@Composable
private fun PickerField(
    title: String,
    headline: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.DropdownList
                contentDescription = title
                stateDescription = "$headline. $supporting"
            }
            .clickable(enabled = enabled, onClickLabel = "Choose $title", onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = headline, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The open state of every picker: a modal with a centred heading, a list capped at
 * [PICKER_LIST_MAX_HEIGHT] that scrolls (with the app's scrollbar) past that, and one closing button.
 * [items] fills the list; [collectionSize] is how many rows it will hold, for a screen reader's
 * "2 of 10".
 */
@Composable
private fun PickerDialog(
    title: String,
    collectionSize: Int,
    closeLabel: String,
    onDismissRequest: () -> Unit,
    listState: LazyListState,
    items: LazyListScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
        },
        text = {
            Box(modifier = Modifier.heightIn(max = PICKER_LIST_MAX_HEIGHT)) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.semantics { collectionInfo = CollectionInfo(rowCount = collectionSize, columnCount = 1) },
                    content = items,
                )
                LazyListScrollbar(listState)
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text(closeLabel) } },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    )
}

/** A row's name and its one-line description, as every picker row writes them. */
@Composable
private fun PickerRowText(label: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A single-choice picker for an option set that is too long, or has too much to say per option, for
 * a [SegmentedChoiceRow] - the setup screen's game mode.
 *
 * Closed, it is one field showing the current pick - its [label] with its [description] beneath -
 * so the form takes the same room however many options there are. Tapped, it opens a modal list of
 * every option, each with the same two lines and a radio dot, scrolling past a fixed height so ten
 * options or fifty cost the screen the same. Choosing an option closes the modal at once; backing
 * out or "Cancel" leaves the pick alone.
 *
 * [title] is both the modal's heading and what a screen reader calls the field.
 */
@Composable
fun <T> ChoicePicker(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    description: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(
        title = title,
        headline = label(selected),
        supporting = description(selected),
        onClick = { open = true },
        modifier = modifier,
        enabled = enabled,
    )
    if (open) {
        // Opens with the current pick in view, so a long list doesn't hide what's chosen below the fold.
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = options.indexOf(selected).coerceAtLeast(0))
        PickerDialog(
            title = title,
            collectionSize = options.size,
            closeLabel = "Cancel",
            onDismissRequest = { open = false },
            listState = listState,
        ) {
            itemsIndexed(options) { index, option ->
                // The whole row is the target, and Role.RadioButton says it is one of a group.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) }
                        .selectable(
                            selected = option == selected,
                            role = Role.RadioButton,
                            onClick = {
                                open = false
                                onSelect(option)
                            },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == selected, onClick = null)
                    PickerRowText(label(option), description(option), Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }
}

/**
 * One modifier for a [ModifierPicker]: an optional extra rule that works under any game mode.
 *
 * [valueLabels] is empty for a plain on/off modifier, or the choices of its value (the turn timer's
 * "30s", "60s", "120s"), of which [selectedValue] is the index in use. [lockedNote] is set when the
 * game mode has overridden the modifier: its controls are disabled and show the note instead of the
 * [description], while [enabled] and [selectedValue] say what is actually in force.
 */
class ModifierSetting(
    val title: String,
    val description: String,
    val enabled: Boolean,
    val onEnabledChange: (Boolean) -> Unit,
    val valueLabels: List<String> = emptyList(),
    val selectedValue: Int = 0,
    val onValueSelect: (Int) -> Unit = {},
    val lockedNote: String? = null,
) {
    /** "Turn timer 60s", or just the title for a modifier without a value. */
    val summary: String
        get() = if (valueLabels.isEmpty()) title else "$title ${valueLabels[selectedValue]}"
}

/**
 * The same dropdown-and-modal as [ChoicePicker], for a set of [modifiers] that are each switched on
 * or off independently - and, for those with a value, set. Closed, the field lists what is on
 * ("None" when nothing is) over [description]; open, each modifier is a switch row with its value
 * choices beneath, greyed while it is off so the modal never changes height. Changes apply as they are made, so the modal just closes ("Done").
 */
@Composable
fun ModifierPicker(
    title: String,
    description: String,
    modifiers: List<ModifierSetting>,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(
        title = title,
        headline = modifiers.filter { it.enabled }.joinToString(", ") { it.summary }.ifEmpty { "None" },
        supporting = description,
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) {
        PickerDialog(
            title = title,
            collectionSize = modifiers.size,
            closeLabel = "Done",
            onDismissRequest = { open = false },
            listState = rememberLazyListState(),
        ) {
            itemsIndexed(modifiers) { index, setting ->
                ModifierRow(setting, Modifier.semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) })
            }
        }
    }
}

@Composable
private fun ModifierRow(setting: ModifierSetting, modifier: Modifier = Modifier) {
    val unlocked = setting.lockedNote == null
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = setting.enabled, enabled = unlocked, role = Role.Switch, onValueChange = setting.onEnabledChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickerRowText(setting.title, setting.lockedNote ?: setting.description, Modifier.weight(1f).padding(end = 12.dp))
            Switch(checked = setting.enabled, onCheckedChange = null, enabled = unlocked)
        }
        // Always laid out, greyed while the modifier is off: showing it only when on made the modal grow
        // under a finger that had just tapped the switch, so whatever was beneath it moved.
        if (setting.valueLabels.isNotEmpty()) {
            SegmentedChoiceRow(
                options = setting.valueLabels.indices.toList(),
                selected = setting.selectedValue,
                onSelect = setting.onValueSelect,
                label = { setting.valueLabels[it] },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = unlocked && setting.enabled,
            )
        }
    }
}
