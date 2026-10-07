package net.zodac.dicefive.ui.setup

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_cpu_cd
import net.zodac.dicefive.resources.common_default_player_name
import net.zodac.dicefive.resources.setup_difficulty_easy
import net.zodac.dicefive.resources.setup_difficulty_hard
import net.zodac.dicefive.resources.setup_difficulty_medium
import net.zodac.dicefive.resources.setup_extended_description
import net.zodac.dicefive.resources.setup_extended_locked
import net.zodac.dicefive.resources.setup_extended_title
import net.zodac.dicefive.resources.setup_game_mode
import net.zodac.dicefive.resources.setup_modifiers
import net.zodac.dicefive.resources.setup_modifiers_active_note
import net.zodac.dicefive.resources.setup_modifiers_description
import net.zodac.dicefive.resources.setup_names_unique
import net.zodac.dicefive.resources.setup_player_name_cd
import net.zodac.dicefive.resources.setup_players
import net.zodac.dicefive.resources.setup_rolls_decrease_cd
import net.zodac.dicefive.resources.setup_rolls_description
import net.zodac.dicefive.resources.setup_rolls_increase_cd
import net.zodac.dicefive.resources.setup_rolls_per_turn_value
import net.zodac.dicefive.resources.setup_rolls_stepper
import net.zodac.dicefive.resources.setup_rolls_title
import net.zodac.dicefive.resources.setup_start
import net.zodac.dicefive.resources.setup_stored_description
import net.zodac.dicefive.resources.setup_stored_field
import net.zodac.dicefive.resources.setup_stored_hint
import net.zodac.dicefive.resources.setup_stored_title
import net.zodac.dicefive.resources.setup_timer_description
import net.zodac.dicefive.resources.setup_timer_none
import net.zodac.dicefive.resources.setup_timer_seconds
import net.zodac.dicefive.resources.setup_timer_title
import net.zodac.dicefive.resources.setup_title
import net.zodac.dicefive.resources.setup_type_user
import net.zodac.dicefive.resources.setup_unlucky_description
import net.zodac.dicefive.resources.setup_unlucky_max
import net.zodac.dicefive.resources.setup_unlucky_max_decrease_cd
import net.zodac.dicefive.resources.setup_unlucky_max_dice_value
import net.zodac.dicefive.resources.setup_unlucky_max_increase_cd
import net.zodac.dicefive.resources.setup_unlucky_odds
import net.zodac.dicefive.resources.setup_unlucky_odds_decrease_cd
import net.zodac.dicefive.resources.setup_unlucky_odds_increase_cd
import net.zodac.dicefive.resources.setup_unlucky_odds_value
import net.zodac.dicefive.resources.setup_unlucky_title
import net.zodac.dicefive.ui.common.ChoicePicker
import net.zodac.dicefive.ui.common.FontFit
import net.zodac.dicefive.ui.common.MIN_READABLE_FONT_SIZE
import net.zodac.dicefive.ui.common.ModifierNumberField
import net.zodac.dicefive.ui.common.ModifierPicker
import net.zodac.dicefive.ui.common.ModifierSetting
import net.zodac.dicefive.ui.common.ModifierStepper
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.SegmentedChoiceRow
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.fitFontSize
import net.zodac.dicefive.ui.common.localised
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.game.GameSetupState
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.game.PlayerSetupSlot

/**
 * Fixed width for the per-row User/CPU control, so the name fields above and below it all end at
 * the same place instead of stepping in and out with the label lengths.
 */
private val TYPE_CONTROL_WIDTH = 76.dp

/** What a chip adds round its label: its own padding on each side, and a little to spare. */
private val TYPE_CHIP_PADDING = 32.dp

/** Above this system font scale a player row stacks its controls (see [PlayerRow]). At 1.0 - and up to a
 * hair over it - the row is the one it has always been. */
private const val STACKED_PLAYER_ROW_FONT_SCALE = 1.05f

/** Shown under the form, and said by a screen reader on each clashing name field. */
@Composable
fun GameSetupScreen(
    viewModel: GameViewModel,
    onStartGame: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val setupRestored by viewModel.setupRestored.collectAsStateWithLifecycle()
    val activeSlots = setup.playerSlots.take(setup.playerCount)
    val focusManager = LocalFocusManager.current

    val defaultNames = (1..GameSetupState.MAX_PLAYERS).map { stringResource(Res.string.common_default_player_name, it) }
    val defaultName: (Int) -> String = { defaultNames[it - 1] }
    val duplicateNameSlots = duplicateHumanNameSlots(activeSlots, defaultName)
    val namesMustBeUnique = stringResource(Res.string.setup_names_unique)

    ScreenScaffold(title = stringResource(Res.string.setup_title), onBack = onBack, modifier = modifier) {
        // Nothing but the title bar until the saved choices are back: drawing the form any sooner
        // shows the defaults (Standard mode, 2 players) for a few frames before they switch to
        // the last game's - and a Start Game tapped in that window would play the defaults.
        if (!setupRestored) return@ScreenScaffold

        // The form scrolls on its own, and the Start Game button sits directly after it rather than
        // pinned to the foot of the screen, where an achievement banner can cover it. weight(1f,
        // fill = false) gives the form at most the height left over once the button is placed, and
        // lets it shrink to its own height when it needs less. A short form keeps the button right
        // underneath it; a form too tall for the screen scrolls, with the button held at the bottom.
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SetupForm(setup = setup, activeSlots = activeSlots, duplicateNameSlots = duplicateNameSlots, namesMustBeUnique = namesMustBeUnique, defaultName = defaultName, viewModel = viewModel)
        }

        if (duplicateNameSlots.isNotEmpty()) {
            Text(
                text = namesMustBeUnique,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = {
                // A name field still focused would keep blinking its cursor on the fading-out page, and the
                // keyboard up, for the frames the next screen takes to arrive.
                focusManager.clearFocus()
                viewModel.startGame(defaultName)
                onStartGame()
            },
            enabled = duplicateNameSlots.isEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(text = stringResource(Res.string.setup_start), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Every Human seat whose name (leading/trailing whitespace ignored, case ignored) collides with
 * another Human seat's - an AI's name is generated, never typed, so it can't collide with anything
 * a player entered. A blank name counts as its slot's [defaultName] ("Player 2"), which is what it
 * becomes at [GameViewModel.startGame] - so two blank ones never collide (their defaults differ), but a
 * blank one does against another seat that typed the same words.
 */
private fun duplicateHumanNameSlots(slots: List<PlayerSetupSlot>, defaultName: (Int) -> String): Set<Int> =
    slots.filter { it.type == PlayerType.HUMAN }
        .groupBy { it.name.trim().ifBlank { defaultName(it.slot) }.lowercase() }
        .values
        .filter { it.size > 1 }
        .flatten()
        .mapTo(mutableSetOf()) { it.slot }

@Composable
private fun SetupForm(
    setup: GameSetupState,
    activeSlots: List<PlayerSetupSlot>,
    duplicateNameSlots: Set<Int>,
    namesMustBeUnique: String,
    defaultName: (Int) -> String,
    viewModel: GameViewModel,
) {
    SetupCard(title = stringResource(Res.string.setup_players)) {
        PlayerCountSelector(count = setup.playerCount, onCountChange = viewModel::setPlayerCount)
    }

    // Every active seat gets a row, including player 1 (always Human, at this device) - one card
    // holding all of them rather than a card per player: stacked cards, each with its own title,
    // padding and controls, is what pushed the form off the screen. No card title here - every
    // row already names its own player.
    val colourHolders = activeSlots.associate { it.colour to it.slot }
    SetupCard(rowSpacing = 2.dp) {
        activeSlots.forEachIndexed { index, slot ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            PlayerRow(
                slot = slot,
                isTypeLocked = slot.slot == 1,
                isNameDuplicate = slot.slot in duplicateNameSlots,
                namesMustBeUnique = namesMustBeUnique,
                defaultName = defaultName(slot.slot),
                colourHolders = colourHolders,
                onColourChange = { colour -> viewModel.setPlayerColour(slot.slot, colour) },
                onTypeChange = { type -> viewModel.setPlayerType(slot.slot, type) },
                onNameChange = { name -> viewModel.setPlayerName(slot.slot, name) },
                onDifficultyChange = { difficulty -> viewModel.setPlayerDifficulty(slot.slot, difficulty) },
            )
        }
    }

    SetupCard(title = stringResource(Res.string.setup_game_mode)) {
        GameModeSelector(selected = setup.gameMode, onSelect = viewModel::setGameMode)
    }

    SetupCard(title = stringResource(Res.string.setup_modifiers)) {
        SetupModifierPicker(
            setup = setup,
            onTurnTimer = viewModel::setTurnTimer,
            onRollsPerTurn = viewModel::setRollsPerTurn,
            onStoredRolls = viewModel::setStoredRolls,
            onStoredRollsMax = viewModel::setStoredRollsMax,
            onExtendedScores = viewModel::setExtendedScores,
            onUnluckyDiceEnabled = viewModel::setUnluckyDiceEnabled,
            onUnluckyOdds = viewModel::setUnluckyOdds,
            onUnluckyMaxDice = viewModel::setUnluckyMaxDice,
        )
    }
}

/**
 * One section of the form - a card is M3's grouping surface for exactly this.
 *
 * [title] is optional: a section whose contents already label themselves (the player rows) doesn't
 * need a heading repeating it. [rowSpacing] is tightened further still for the player list, where
 * up to four rows (plus their dividers) need to fit on screen at once alongside every other card.
 */
@Composable
private fun SetupCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    rowSpacing: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/**
 * Player count as four fixed choices rather than a -/+ stepper: the range is 1..4, so every option
 * fits on one line, and picking 4 is one tap instead of three.
 */
@Composable
private fun PlayerCountSelector(count: Int, onCountChange: (Int) -> Unit) {
    SegmentedChoiceRow(
        options = (GameSetupState.MIN_PLAYERS..GameSetupState.MAX_PLAYERS).toList(),
        selected = count,
        onSelect = onCountChange,
        label = { it.localised() },
        modifier = Modifier.fillMaxWidth(),
        brandFont = true,
    )
}

/**
 * A player on one line: their name (editable for a User, automatic for a CPU) and the control
 * that switches between the two. No separate "Player N" label - the name field's own value (or,
 * for a CPU, the difficulty picker itself) already identifies the row.
 *
 * [isTypeLocked] is true only for player 1, who is always Human (this device's own player) - their
 * row has no [FilterChip], just an empty space the chip's width, so every row's name field still
 * ends at the same right edge. (It used to say "You"; the row needs no label to be player 1's.)
 */
@Composable
private fun PlayerRow(
    slot: PlayerSetupSlot,
    isTypeLocked: Boolean,
    isNameDuplicate: Boolean,
    namesMustBeUnique: String,
    defaultName: String,
    colourHolders: Map<PlayerColour, Int>,
    onColourChange: (PlayerColour) -> Unit,
    onTypeChange: (PlayerType) -> Unit,
    onNameChange: (String) -> Unit,
    onDifficultyChange: (Difficulty) -> Unit,
) {
    // The name (or difficulty) and the User/CPU switch share a row at the normal font size. At a larger one
    // the difficulty's three labels no longer fit beside the switch, so it drops beneath - the controls
    // keep the width their labels need instead of being squeezed or cut off.
    val fontScale = LocalDensity.current.fontScale
    val nameFieldDescription = stringResource(Res.string.setup_player_name_cd, slot.slot)
    val userLabel = stringResource(Res.string.setup_type_user)
    val cpuLabel = stringResource(Res.string.common_cpu_cd)
    val stacked = fontScale > STACKED_PLAYER_ROW_FONT_SCALE
    val nameOrDifficulty: @Composable (Modifier) -> Unit = { controlModifier ->
        // The colour circle leads the name (or difficulty), whichever the row shows, so both seat types line up.
        Row(modifier = controlModifier, verticalAlignment = Alignment.CenterVertically) {
            PlayerColourPicker(slot = slot.slot, colour = slot.colour, holders = colourHolders, onColourChange = onColourChange)
            when (slot.type) {
                PlayerType.HUMAN -> CompactNameField(
                    value = slot.name,
                    onValueChange = onNameChange,
                    isError = isNameDuplicate,
                    placeholder = defaultName,
                    // The field has no visible label (its value names the row), so a screen reader is
                    // given one - and told why it's red, which the outline alone only shows.
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = nameFieldDescription
                        if (isNameDuplicate) error(namesMustBeUnique)
                    },
                )

                PlayerType.AI -> DifficultySelector(
                    selected = slot.difficulty,
                    onSelect = onDifficultyChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    // Grows with the font so "User" and "CPU" fit; player 1's empty space grows with it, so every row still lines up.
    // ...and with the words: "Usuario" is wider than "User", and a label that doesn't fit its chip would break mid-word, so
    // the chip is as wide as the longer of its two labels needs when that's more than the usual width.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)
    val labelWidth = with(density) { maxOf(measurer.measure(userLabel, labelStyle).size.width, measurer.measure(cpuLabel, labelStyle).size.width).toDp() }
    val typeControlWidth = maxOf(TYPE_CONTROL_WIDTH * fontScale.coerceAtLeast(1f), labelWidth + TYPE_CHIP_PADDING)
    val typeControl: @Composable () -> Unit = {
        if (isTypeLocked) {
            Spacer(modifier = Modifier.width(typeControlWidth))
        } else {
            FilterChip(
                selected = slot.type == PlayerType.AI,
                onClick = { onTypeChange(if (slot.type == PlayerType.AI) PlayerType.HUMAN else PlayerType.AI) },
                label = {
                    // FilterChip's own Row left-aligns its label rather than centering it, so at a
                    // fixed chip width the leftover space all landed on one side - most visible as
                    // "CPU" and "User" sitting at different horizontal positions. A label that fills
                    // the whole slot and centers its own text isn't subject to that.
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(if (slot.type == PlayerType.AI) cpuLabel else userLabel, fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                },
                modifier = Modifier.width(typeControlWidth),
            )
        }
    }

    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            nameOrDifficulty(Modifier.fillMaxWidth())
            // Player 1 has no switch, and on a line of its own its empty space would only leave a gap.
            if (!isTypeLocked) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { typeControl() }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            nameOrDifficulty(Modifier.weight(1f))
            typeControl()
        }
    }
}

/**
 * A single-line name field shrunk close to [DifficultySelector]'s own height so toggling User/CPU
 * doesn't resize the whole row - the public `OutlinedTextField` composable enforces a 56dp minimum
 * height that isn't reachable through its own modifier/parameters, so this builds the same look
 * from [BasicTextField] plus [OutlinedTextFieldDefaults.DecorationBox], which takes an explicit
 * [contentPadding][OutlinedTextFieldDefaults.contentPadding] instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactNameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    placeholder: String = "",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focusState ->
            // Trimmed only once the player has moved on, not on every keystroke - trimming a
            // trailing space the instant it's typed would make it impossible to type a second
            // word, and leading/trailing whitespace can't matter until the value is final anyway.
            if (!focusState.isFocused) {
                val trimmed = value.trim()
                if (trimmed != value) onValueChange(trimmed)
            }
        },
        // Content: a name typed in a right-to-left script runs and aligns that way, whatever the app's own language.
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, textDirection = TextDirection.Content),
        singleLine = true,
        // Names read as Capitalized Words, not lowercase - and this keeps the keyboard's own
        // shift state in sync with that after the field is cleared back to empty, which a plain
        // default keyboard doesn't do on its own (it just keeps whatever case the last edit left
        // it in).
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
    ) { innerTextField ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = innerTextField,
            enabled = true,
            singleLine = true,
            isError = isError,
            visualTransformation = VisualTransformation.None,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            interactionSource = interactionSource,
            colors = colors,
            contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 6.dp, bottom = 6.dp),
        )
    }
}

/**
 * Compact Easy/Medium/Hard picker for one AI slot - a segmented row rather than a full row of
 * chips, since it has to fit inside the player row alongside the name column.
 *
 * The labels are sized together, so the three always match: [DIFFICULTY_LABEL_MAX_SIZE] when the widest
 * ("Medium") fits its segment, stepping down to [MIN_READABLE_FONT_SIZE] when it doesn't, and only if
 * even that is too wide - a narrow screen - all three become their initials (E / M / H) at that size.
 * A screen reader still hears the full word.
 */
@Composable
fun DifficultySelector(selected: Difficulty, onSelect: (Difficulty) -> Unit, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labels = Difficulty.entries.associateWith { it.label() }
    BoxWithConstraints(modifier = modifier) {
        val textStyle = MaterialTheme.typography.labelLarge
        val fit = remember(constraints.maxWidth, textStyle, density, labels) {
            // A segment's width less its side padding and the 1dp outline each side; the row is infinite
            // only in a measuring pass, where the largest size will do.
            val room = with(density) { (constraints.maxWidth / Difficulty.entries.size) - (DIFFICULTY_LABEL_PADDING * 2 + 2.dp).roundToPx() }
            if (constraints.maxWidth == Constraints.Infinity) {
                FontFit(DIFFICULTY_LABEL_MAX_SIZE, wraps = false)
            } else {
                fitFontSize(DIFFICULTY_LABEL_MAX_SIZE, MIN_READABLE_FONT_SIZE, 0.5.sp) { size ->
                    Difficulty.entries.all {
                        measurer.measure(text = labels.getValue(it), style = textStyle.copy(fontSize = size), maxLines = 1, softWrap = false).size.width <= room
                    }
                }
            }
        }
        SegmentedChoiceRow(
            options = Difficulty.entries,
            selected = selected,
            onSelect = onSelect,
            label = { if (fit.wraps) labels.getValue(it).take(1) else labels.getValue(it) },
            spokenLabel = { labels.getValue(it) },
            modifier = Modifier.fillMaxWidth(),
            labelStyle = textStyle.copy(fontSize = fit.size),
            // Tighter than M3's 12dp a side: with the colour circle beside it each segment is narrow.
            contentPadding = PaddingValues(horizontal = DIFFICULTY_LABEL_PADDING),
        )
    }
}

private val DIFFICULTY_LABEL_PADDING = 4.dp
private val DIFFICULTY_LABEL_MAX_SIZE = 14.sp

@Composable
private fun Difficulty.label(): String = stringResource(
    when (this) {
        Difficulty.EASY -> Res.string.setup_difficulty_easy
        Difficulty.MEDIUM -> Res.string.setup_difficulty_medium
        Difficulty.HARD -> Res.string.setup_difficulty_hard
    },
)

/**
 * The mode picker: one field showing the current mode and its one-line description, opening a
 * scrollable list of every [GameMode] - the list is long enough to outgrow the form, so it isn't
 * laid out inline. See [ChoicePicker].
 */
@Composable
private fun GameModeSelector(selected: GameMode, onSelect: (GameMode) -> Unit) {
    ChoicePicker(
        title = stringResource(Res.string.setup_game_mode),
        options = GameMode.entries,
        selected = selected,
        onSelect = onSelect,
        label = { stringResource(it.displayName) },
        description = { stringResource(it.description) },
    )
}

/**
 * The setup screen's modifiers. The turn timer is off when [TurnTimer.NONE] and
 * otherwise one of the lengths. The length last chosen is kept in the setup state (and saved with it) while
 * the timer is off, so switching it back on - even next game - restores it.
 *
 * Number of Rolls and Stored Rolls (see [RollModifiers]) apply in every mode. Stored
 * Rolls' cap is a typed number, empty for none.
 *
 * Extended Scores is a plain switch with nothing to set, and applies in every mode that allows it
 * ([GameMode.allowsExtendedScores]) - locked off, with the player's pick kept, in one that doesn't. Unlucky Dice
 * ([UnluckyDice]) is a switch with two steppers, the odds and the most dice locked a roll, and applies in every mode too;
 * both are kept while it's off.
 */
@Composable
private fun SetupModifierPicker(
    setup: GameSetupState,
    onTurnTimer: (TurnTimer) -> Unit,
    onRollsPerTurn: (Int?) -> Unit,
    onStoredRolls: (Boolean) -> Unit,
    onStoredRollsMax: (Int?) -> Unit,
    onExtendedScores: (Boolean) -> Unit,
    onUnluckyDiceEnabled: (Boolean) -> Unit,
    onUnluckyOdds: (Int) -> Unit,
    onUnluckyMaxDice: (Int) -> Unit,
) {
    val onSelect = onTurnTimer
    val lengths = TurnTimer.entries.filter { it != TurnTimer.NONE }
    val rolls = setup.rollModifiers
    ModifierPicker(
        title = stringResource(Res.string.setup_modifiers),
        description = stringResource(Res.string.setup_modifiers_description),
        activeNote = stringResource(Res.string.setup_modifiers_active_note),
        modifiers = listOf(
            ModifierSetting(
                title = stringResource(Res.string.setup_timer_title),
                description = stringResource(Res.string.setup_timer_description),
                enabled = setup.turnTimer != TurnTimer.NONE,
                onEnabledChange = { on -> onSelect(if (on) setup.turnTimerLength else TurnTimer.NONE) },
                valueLabels = lengths.map { it.label() },
                selectedValue = lengths.indexOf(setup.turnTimerLength),
                onValueSelect = { onSelect(lengths[it]) },
            ),
            ModifierSetting(
                title = stringResource(Res.string.setup_rolls_title),
                description = stringResource(Res.string.setup_rolls_description),
                enabled = rolls.rollsPerTurn != null,
                onEnabledChange = { on -> onRollsPerTurn(if (on) setup.rollsPerTurnLength else null) },
                steppers = listOf(
                    ModifierStepper(
                        value = setup.rollsPerTurnLength,
                        range = RollModifiers.MIN_ROLLS..RollModifiers.MAX_ROLLS,
                        onValueChange = onRollsPerTurn,
                        label = stringResource(Res.string.setup_rolls_stepper),
                        decreaseLabel = stringResource(Res.string.setup_rolls_decrease_cd),
                        increaseLabel = stringResource(Res.string.setup_rolls_increase_cd),
                        valueText = { pluralStringResource(Res.plurals.setup_rolls_per_turn_value, it, it) },
                    ),
                ),
            ),
            ModifierSetting(
                title = stringResource(Res.string.setup_stored_title),
                description = stringResource(Res.string.setup_stored_description),
                enabled = rolls.storedRolls,
                onEnabledChange = onStoredRolls,
                numberField = ModifierNumberField(
                    label = stringResource(Res.string.setup_stored_field),
                    hint = stringResource(Res.string.setup_stored_hint),
                    initial = rolls.storedRollsMax?.localised().orEmpty(),
                    maxDigits = RollModifiers.MAX_CAP_DIGITS,
                    onValueChange = onStoredRollsMax,
                ),
            ),
            ModifierSetting(
                title = stringResource(Res.string.setup_extended_title),
                description = stringResource(Res.string.setup_extended_description),
                // Locked off in a mode whose card it doesn't fit - the player's own pick is kept for the next mode.
                enabled = setup.extendedScores && setup.gameMode.allowsExtendedScores,
                onEnabledChange = onExtendedScores,
                lockedNote = if (setup.gameMode.allowsExtendedScores) null else stringResource(Res.string.setup_extended_locked, stringResource(setup.gameMode.displayName)),
            ),
            ModifierSetting(
                title = stringResource(Res.string.setup_unlucky_title),
                description = stringResource(Res.string.setup_unlucky_description),
                enabled = setup.unluckyDiceEnabled,
                onEnabledChange = onUnluckyDiceEnabled,
                steppers = listOf(
                    ModifierStepper(
                        value = setup.unluckyDice.oddsPercent,
                        range = UnluckyDice.MIN_ODDS_PERCENT..UnluckyDice.MAX_ODDS_PERCENT,
                        onValueChange = onUnluckyOdds,
                        label = stringResource(Res.string.setup_unlucky_odds),
                        decreaseLabel = stringResource(Res.string.setup_unlucky_odds_decrease_cd),
                        increaseLabel = stringResource(Res.string.setup_unlucky_odds_increase_cd),
                        valueText = { stringResource(Res.string.setup_unlucky_odds_value, it) },
                        step = UnluckyDice.ODDS_STEP_PERCENT,
                    ),
                    ModifierStepper(
                        value = setup.unluckyDice.maxDice,
                        range = UnluckyDice.MIN_MAX_DICE..UnluckyDice.MAX_MAX_DICE,
                        onValueChange = onUnluckyMaxDice,
                        label = stringResource(Res.string.setup_unlucky_max),
                        decreaseLabel = stringResource(Res.string.setup_unlucky_max_decrease_cd),
                        increaseLabel = stringResource(Res.string.setup_unlucky_max_increase_cd),
                        valueText = { pluralStringResource(Res.plurals.setup_unlucky_max_dice_value, it, it) },
                    ),
                ),
            ),
        ),
    )
}

@Composable
private fun TurnTimer.label(): String = seconds?.let { stringResource(Res.string.setup_timer_seconds, it) } ?: stringResource(Res.string.setup_timer_none)
