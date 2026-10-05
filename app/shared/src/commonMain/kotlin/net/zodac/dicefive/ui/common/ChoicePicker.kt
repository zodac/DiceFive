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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
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

/** The most the picker's list may grow before it scrolls - about five two-line options. */
private val CHOICE_LIST_MAX_HEIGHT = 340.dp

/**
 * A single-choice picker for an option set that is too long, or has too much to say per option, for
 * a [SegmentedChoiceRow] - the setup screen's game mode, and later its modifiers.
 *
 * Closed, it is one field showing the current pick - its [label] with its [description] beneath -
 * so the form takes the same room however many options there are. Tapped, it opens a modal list of
 * every option, each with the same two lines and a radio dot. The list is capped at
 * [CHOICE_LIST_MAX_HEIGHT] and scrolls (with the app's usual scrollbar) beyond that, so ten options
 * or fifty cost the screen the same. Choosing an option closes the modal at once; backing out or
 * "Cancel" leaves the pick alone.
 *
 * [title] is both the modal's heading and what a screen reader calls the field, so it should name
 * the thing being chosen ("Game Mode"). The field's own state is the pick, spoken as its value.
 *
 * [enabled] false greys the field and ignores taps, as for [SegmentedChoiceRow].
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
    val spokenValue = "${label(selected)}. ${description(selected)}"

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.DropdownList
                contentDescription = title
                stateDescription = spokenValue
            }
            .clickable(enabled = enabled, onClickLabel = "Choose $title") { open = true },
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label(selected),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description(selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (open) {
        ChoicePickerDialog(
            title = title,
            options = options,
            selected = selected,
            label = label,
            description = description,
            onSelect = {
                open = false
                onSelect(it)
            },
            onDismissRequest = { open = false },
        )
    }
}

@Composable
private fun <T> ChoicePickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: (T) -> String,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // Opens with the current pick in view, so a long list doesn't hide what's chosen below the fold.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = options.indexOf(selected).coerceAtLeast(0))
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
            Box(modifier = Modifier.heightIn(max = CHOICE_LIST_MAX_HEIGHT)) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.semantics { collectionInfo = CollectionInfo(rowCount = options.size, columnCount = 1) },
                ) {
                    itemsIndexed(options) { index, option ->
                        ChoiceRow(
                            label = label(option),
                            description = description(option),
                            selected = option == selected,
                            onSelect = { onSelect(option) },
                            modifier = Modifier.semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) },
                        )
                    }
                }
                LazyListScrollbar(listState)
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("Cancel") } },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    )
}

/** The whole row is the target, and `selectable` with [Role.RadioButton] says it is one of a group. */
@Composable
private fun ChoiceRow(label: String, description: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
