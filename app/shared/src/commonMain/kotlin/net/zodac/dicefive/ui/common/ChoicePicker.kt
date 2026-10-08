package net.zodac.dicefive.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_cancel
import net.zodac.dicefive.resources.common_done
import net.zodac.dicefive.resources.common_modifiers_enabled
import net.zodac.dicefive.resources.common_modifiers_none
import net.zodac.dicefive.resources.common_picker_choose_action
import net.zodac.dicefive.resources.common_picker_state_spoken
import net.zodac.dicefive.resources.common_stepper_value_spoken

/*
 * The app's dropdown-and-modal pickers. Every colour, shape, size and text style of the closed field and
 * of the modal lives in the two shell composables here - [PickerField] and [PickerDialog] - and the
 * rows share [PickerRowText], so a re-theme is made in this one file. [ChoicePicker] (pick one) and
 * [ModifierPicker] (switch several on, each with an optional value) are only the contents they hold.
 */

/** Room kept at the right of the modal's list for the scrollbar drawn over it. */
private val PICKER_SCROLLBAR_CLEARANCE = 12.dp

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
    val state = stringResource(Res.string.common_picker_state_spoken, headline, supporting)
    OutlinedCard(
        // The blue of a chosen segment (the player count beside it on the setup screen), so a picker reads as a choice made.
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.DropdownList
                contentDescription = title
                stateDescription = state
            }
            .clickable(enabled = enabled, onClickLabel = stringResource(Res.string.common_picker_choose_action, title), onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // The pick in the brand face, as the picker's own rows have it.
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/**
 * The open state of every picker: a full-screen page over the form (a dialog window, so the setup
 * screen and its state stay where they are and Back returns to them), titled like any other page,
 * with the list filling the room and one closing button under it. The list scrolls (with the app's
 * scrollbar) once it outgrows the screen. [items] fills the list; [collectionSize] is how many rows
 * it will hold, for a screen reader's "2 of 10". [itemSpacing] is the gap between rows - wider where each is a card.
 */
@Composable
private fun PickerDialog(
    title: String,
    collectionSize: Int,
    closeLabel: String,
    onDismissRequest: () -> Unit,
    listState: LazyListState,
    itemSpacing: Dp = 4.dp,
    closeEnabled: Boolean = true,
    items: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = fullScreenDialogProperties(),
    ) {
        // No back arrow while the picker can't close (see ModifierPicker).
        ScreenScaffold(title = title, onBack = onDismissRequest.takeIf { closeEnabled }) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    // Clear of the scrollbar, which is drawn over the list's right edge.
                    contentPadding = PaddingValues(end = PICKER_SCROLLBAR_CLEARANCE),
                    modifier = Modifier.semantics { collectionInfo = CollectionInfo(rowCount = collectionSize, columnCount = 1) },
                    content = items,
                )
                LazyListScrollbar(listState)
            }
            Button(onClick = onDismissRequest, enabled = closeEnabled, modifier = Modifier.fillMaxWidth()) { Text(closeLabel) }
        }
    }
}

/** A row's name and its one-line description, as every picker row writes them. */
@Composable
private fun PickerRowText(label: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        // The name in the brand face, like a heading; the description stays in the system font, quieter beneath it.
        Text(text = label, style = MaterialTheme.typography.bodyLarge, fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
    label: @Composable (T) -> String,
    description: @Composable (T) -> String,
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
            closeLabel = stringResource(Res.string.common_cancel),
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
    /** Values counted up and down in steps instead of picked from [valueLabels], for more choices than a row of segments holds - one row each, in order. */
    val steppers: List<ModifierStepper> = emptyList(),
    /** A value typed in, for one that has no short list of choices. */
    val numberField: ModifierNumberField? = null,
    /** A value typed in beside [valueLabels] on their row, for a length the choices don't hold. */
    val customValue: ModifierCustomValue? = null,
)

/**
 * A number typed in on the same row as a [ModifierSetting]'s [valueLabels]. [active] is whether the typed number is the value in use (no
 * label is then selected); [text] is what the field starts with, empty while it isn't. Typing something makes it the value, so
 * [onValueChange] is handed the number, or null once the field is empty or zero - the caller then returns to a label. Choosing a
 * label clears the field. A number typed under [min] is left in the field, outlined in red, and not passed on: what was in use stays in use, and the picker will not close until it is changed. Only digits are taken, up to [maxDigits]; [unit] follows them, and [label] is what a screen reader calls the field.
 */
class ModifierCustomValue(
    val active: Boolean,
    val text: String,
    val label: String,
    val unit: String,
    val maxDigits: Int,
    val onValueChange: (Int?) -> Unit,
    /** The least a value can be: a smaller number typed is marked in the field and not passed on. */
    val min: Int = 1,
    /** What a screen reader is told while the number typed is under [min]. */
    val belowMinMessage: String = "",
)

/**
 * A [ModifierSetting]'s stepped value: [value] within [range], moved [step] at a time, shown as [valueText] says
 * ("3 rolls", "50%") and spoken the same way after [label]. [valueText] is a whole phrase, number included, so
 * a language can inflect the unit by the number. [decreaseLabel] and [increaseLabel] name the buttons for TalkBack
 * ("Decrease rolls per turn"): whole phrases too, since lower-casing [label] in code is wrong for some languages
 * (German capitalises its nouns).
 */
class ModifierStepper(
    val value: Int,
    val range: IntRange,
    val onValueChange: (Int) -> Unit,
    val label: String,
    val decreaseLabel: String,
    val increaseLabel: String,
    val valueText: @Composable (Int) -> String,
    val step: Int = 1,
)

/**
 * A [ModifierSetting]'s typed number: [initial] is the starting text (empty for no value), and
 * [onValueChange] gets the number typed, or null while the field is empty. Only digits are accepted, up to [maxDigits].
 */
class ModifierNumberField(
    val label: String,
    val hint: String,
    val initial: String,
    val maxDigits: Int,
    val onValueChange: (Int?) -> Unit,
)

/**
 * The same dropdown-and-modal as [ChoicePicker], for a set of [modifiers] that are each switched on
 * or off independently - and, for those with a value, set. Closed, the field counts what is on
 * ("None" when nothing is) over [description], or [activeNote] in its place while any is on; open, each
 * modifier is a card of its own (no heading - its switch row names it) holding a switch row with its
 * value beneath, which opens out only while it is on. Changes apply as they are made, so the modal
 * just closes ("Done").
 */
@Composable
fun ModifierPicker(
    title: String,
    description: String,
    modifiers: List<ModifierSetting>,
    modifier: Modifier = Modifier,
    activeNote: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // A modifier locked by the game mode isn't the player's choice, so isn't counted.
    val enabledCount = modifiers.count { it.enabled && it.lockedNote == null }
    PickerField(
        title = title,
        headline = if (enabledCount == 0) {
            stringResource(Res.string.common_modifiers_none)
        } else {
            pluralStringResource(Res.plurals.common_modifiers_enabled, enabledCount, enabledCount)
        },
        supporting = if (enabledCount == 0) description else activeNote ?: description,
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) {
        // The modifiers whose typed value is not one it can take: the picker stays open (Done, the back arrow, the back
        // gesture) until each is mended or its modifier is switched off.
        val invalid = remember { mutableStateMapOf<Int, Boolean>() }
        val blocked = modifiers.withIndex().any { (index, setting) -> setting.enabled && invalid[index] == true }
        PickerDialog(
            title = title,
            collectionSize = modifiers.size,
            closeLabel = stringResource(Res.string.common_done),
            onDismissRequest = { if (!blocked) open = false },
            listState = rememberLazyListState(),
            // The New Game form's gap between its cards.
            itemSpacing = 10.dp,
            closeEnabled = !blocked,
        ) {
            itemsIndexed(modifiers) { index, setting ->
                ModifierRow(setting, onInvalidChange = { invalid[index] = it }, modifier = Modifier.semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) })
            }
        }
    }
}

@Composable
private fun ModifierRow(setting: ModifierSetting, onInvalidChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val unlocked = setting.lockedNote == null
    val reduceMotion = LocalReduceMotion.current
    // Counts taps on a label, which empty the typed field beside them.
    var labelTaps by remember { mutableIntStateOf(0) }
    // A card per modifier, padded like the New Game screen's cards.
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = setting.enabled, enabled = unlocked, role = Role.Switch, onValueChange = setting.onEnabledChange),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PickerRowText(setting.title, setting.lockedNote ?: setting.description, Modifier.weight(1f).padding(end = 12.dp))
                Switch(checked = setting.enabled, onCheckedChange = null, enabled = unlocked)
            }
            // Shown only while the modifier is on, opening beneath its switch rather than popping in. The switch
            // above never moves; only the cards below it slide down.
            AnimatedVisibility(
                visible = setting.enabled,
                enter = if (reduceMotion) EnterTransition.None else expandVertically(tween(MODIFIER_VALUES_MILLIS)) + fadeIn(tween(MODIFIER_VALUES_MILLIS)),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically(tween(MODIFIER_VALUES_MILLIS)) + fadeOut(tween(MODIFIER_VALUES_MILLIS)),
            ) {
                Column {
                    setting.steppers.forEach { stepper ->
                        ModifierStepperRow(stepper, enabled = unlocked, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    setting.numberField?.let { field ->
                        ModifierNumberFieldRow(field, enabled = unlocked, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    if (setting.valueLabels.isNotEmpty()) {
                        val custom = setting.customValue
                        val choices = @Composable { choiceModifier: Modifier ->
                            SegmentedChoiceRow(
                                options = setting.valueLabels.indices.toList(),
                                // None selected while the typed number is the value.
                                selected = if (custom?.active == true) -1 else setting.selectedValue,
                                onSelect = { labelTaps++; setting.onValueSelect(it) },
                                label = { setting.valueLabels[it] },
                                modifier = choiceModifier,
                                enabled = unlocked,
                                brandFont = true,
                            )
                        }
                        if (custom == null) {
                            choices(Modifier.fillMaxWidth().padding(top = 8.dp))
                        } else {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                choices(Modifier.weight(CUSTOM_CHOICES_WEIGHT))
                                ModifierCustomValueField(custom, labelTaps = labelTaps, onInvalidChange = onInvalidChange, enabled = unlocked, modifier = Modifier.weight(CUSTOM_FIELD_WEIGHT))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModifierStepperRow(stepper: ModifierStepper, enabled: Boolean, modifier: Modifier = Modifier) {
    val valueText = stepper.valueText(stepper.value)
    val spokenValue = stringResource(Res.string.common_stepper_value_spoken, stepper.label, valueText)
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { stepper.onValueChange(stepper.value - stepper.step) }, enabled = enabled && stepper.value - stepper.step >= stepper.range.first) {
            Icon(Icons.Filled.Remove, contentDescription = stepper.decreaseLabel)
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
            textAlign = TextAlign.Center,
            // Polite live region: the new count is spoken after a tap on either button.
            modifier = Modifier.widthIn(min = 96.dp).semantics {
                contentDescription = spokenValue
                liveRegion = LiveRegionMode.Polite
            },
        )
        IconButton(onClick = { stepper.onValueChange(stepper.value + stepper.step) }, enabled = enabled && stepper.value + stepper.step <= stepper.range.last) {
            Icon(Icons.Filled.Add, contentDescription = stepper.increaseLabel)
        }
    }
}

@Composable
private fun ModifierNumberFieldRow(field: ModifierNumberField, enabled: Boolean, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf(field.initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            // Any script's digits are taken (a keyboard in Arabic types "٣"), and read as the number they are.
            val digits = typed.filter { it.isDigit() }.take(field.maxDigits)
            text = digits
            field.onValueChange(digits.map { it.digitToInt() }.joinToString("").toIntOrNull())
        },
        label = { Text(field.label) },
        placeholder = { Text(field.hint) },
        // The typed number in the brand face, like the other modifiers' values; its label and hint stay plain.
        textStyle = LocalTextStyle.current.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold),
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModifierCustomValueField(custom: ModifierCustomValue, labelTaps: Int, onInvalidChange: (Boolean) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf(custom.text) }
    // Choosing a label (or the caller going back to one) empties the field; it is the value in use or it is blank.
    LaunchedEffect(custom.active, labelTaps) { if (!custom.active && text.isNotEmpty()) text = "" }
    // The value is set like the preset buttons' labels beside it; only the hint is smaller.
    val valueStyle = MaterialTheme.typography.labelLarge.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, color = LocalContentColor.current)
    val interactionSource = remember { MutableInteractionSource() }
    // Under the least a value can be: in use as the least, shown as typed, and marked.
    val belowMin = text.map { it.digitToInt() }.joinToString("").toIntOrNull()?.let { it < custom.min } == true
    // Told up so the picker can stay open while it is, and not once this field has gone (its modifier switched off).
    val currentOnInvalidChange by rememberUpdatedState(onInvalidChange)
    LaunchedEffect(belowMin) { currentOnInvalidChange(belowMin) }
    DisposableEffect(Unit) { onDispose { currentOnInvalidChange(false) } }
    // Built on the basic field so it can be as tall as the preset buttons beside it (an outlined field is 56dp at least).
    BasicTextField(
        value = text,
        onValueChange = { typed ->
            // Any script's digits are taken, and read as the number they are.
            val digits = typed.filter { it.isDigit() }.take(custom.maxDigits)
            val number = digits.map { it.digitToInt() }.joinToString("").toIntOrNull()?.takeIf { it > 0 }
            // Nothing usable typed (blank, or only zeros): the field is blank and the caller is back on a label.
            text = if (number == null) "" else digits
            // Under the least, it stays as typed and marked, and what was in use stays in use: nothing is taken from it.
            if (number == null || number >= custom.min) custom.onValueChange(number)
        },
        enabled = enabled,
        singleLine = true,
        textStyle = valueStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        interactionSource = interactionSource,
        modifier = modifier
            .height(CUSTOM_FIELD_HEIGHT)
            .semantics {
                contentDescription = custom.label
                if (belowMin) error(custom.belowMinMessage)
            },
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = text,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                isError = belowMin,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                
                suffix = { if (text.isNotEmpty()) Text(custom.unit, style = valueStyle) },
                contentPadding = PaddingValues(horizontal = 12.dp),
                container = { OutlinedTextFieldDefaults.Container(enabled = enabled, isError = belowMin, interactionSource = interactionSource) },
            )
        },
    )
}

/** As tall as the preset buttons beside it. */
private val CUSTOM_FIELD_HEIGHT = 40.dp

/** The shares of the turn timer's row taken by its preset buttons and by the field beside them. */
private const val CUSTOM_CHOICES_WEIGHT = 3f
private const val CUSTOM_FIELD_WEIGHT = 1.6f

/** How long a modifier's values take to open beneath its switch, or close again. */
private const val MODIFIER_VALUES_MILLIS = 220

/** Material's own alpha for disabled content. */
private const val DISABLED_ALPHA = 0.38f
