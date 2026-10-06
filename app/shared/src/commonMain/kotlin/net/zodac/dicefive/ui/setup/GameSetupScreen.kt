package net.zodac.dicefive.ui.setup

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.ui.common.ChoicePicker
import net.zodac.dicefive.ui.common.ModifierNumberField
import net.zodac.dicefive.ui.common.ModifierPicker
import net.zodac.dicefive.ui.common.ModifierSetting
import net.zodac.dicefive.ui.common.ModifierStepper
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.SegmentedChoiceRow
import net.zodac.dicefive.ui.game.GameSetupState
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.game.PlayerSetupSlot

/**
 * Fixed width for the per-row User/CPU control, so the name fields above and below it all end at
 * the same place instead of stepping in and out with the label lengths.
 */
private val TYPE_CONTROL_WIDTH = 76.dp

/** Above this system font scale a player row stacks its controls (see [PlayerRow]). At 1.0 - and up to a
 * hair over it - the row is the one it has always been. */
private const val STACKED_PLAYER_ROW_FONT_SCALE = 1.05f

/** Shown under the form, and said by a screen reader on each clashing name field. */
private const val NAMES_MUST_BE_UNIQUE = "Names must be unique"

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
    val duplicateNameSlots = duplicateHumanNameSlots(activeSlots)
    val focusManager = LocalFocusManager.current

    ScreenScaffold(title = "New Game", onBack = onBack, modifier = modifier) {
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
            SetupForm(setup = setup, activeSlots = activeSlots, duplicateNameSlots = duplicateNameSlots, viewModel = viewModel)
        }

        if (duplicateNameSlots.isNotEmpty()) {
            Text(
                text = NAMES_MUST_BE_UNIQUE,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = {
                // A name field still focused would keep blinking its cursor on the fading-out page, and the
                // keyboard up, for the frames the next screen takes to arrive.
                focusManager.clearFocus()
                viewModel.startGame()
                onStartGame()
            },
            enabled = duplicateNameSlots.isEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(text = "Start Game", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Every Human seat whose name (leading/trailing whitespace ignored, case ignored) collides with
 * another Human seat's - an AI's name is generated, never typed, so it can't collide with anything
 * a player entered. A blank name isn't a collision with another blank one: both fall back to their
 * own distinct "Player N" default at [GameViewModel.startGame].
 */
private fun duplicateHumanNameSlots(slots: List<PlayerSetupSlot>): Set<Int> =
    slots.filter { it.type == PlayerType.HUMAN }
        .groupBy { it.name.trim().lowercase() }
        .filterKeys { it.isNotEmpty() }
        .values
        .filter { it.size > 1 }
        .flatten()
        .mapTo(mutableSetOf()) { it.slot }

@Composable
private fun SetupForm(
    setup: GameSetupState,
    activeSlots: List<PlayerSetupSlot>,
    duplicateNameSlots: Set<Int>,
    viewModel: GameViewModel,
) {
    SetupCard(title = "Players") {
        PlayerCountSelector(count = setup.playerCount, onCountChange = viewModel::setPlayerCount)
    }

    // Every active seat gets a row, including player 1 (always Human, at this device) - one card
    // holding all of them rather than a card per player: stacked cards, each with its own title,
    // padding and controls, is what pushed the form off the screen. No card title here - every
    // row already names its own player.
    SetupCard(rowSpacing = 2.dp) {
        activeSlots.forEachIndexed { index, slot ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            PlayerRow(
                slot = slot,
                isTypeLocked = slot.slot == 1,
                isNameDuplicate = slot.slot in duplicateNameSlots,
                onTypeChange = { type -> viewModel.setPlayerType(slot.slot, type) },
                onNameChange = { name -> viewModel.setPlayerName(slot.slot, name) },
                onDifficultyChange = { difficulty -> viewModel.setPlayerDifficulty(slot.slot, difficulty) },
            )
        }
    }

    SetupCard(title = "Game Mode") {
        GameModeSelector(selected = setup.gameMode, onSelect = viewModel::setGameMode)
    }

    SetupCard(title = "Modifiers") {
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
        label = Int::toString,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A player on one line: their name (editable for a User, automatic for a CPU) and the control
 * that switches between the two. No separate "Player N" label - the name field's own value (or,
 * for a CPU, the difficulty picker itself) already identifies the row.
 *
 * [isTypeLocked] is true only for player 1, who is always Human (this device's own player) - their
 * row shows a fixed "You" label in place of the [FilterChip] the other seats get, at the same
 * width so every row's name field still lines up at the same right edge.
 */
@Composable
private fun PlayerRow(
    slot: PlayerSetupSlot,
    isTypeLocked: Boolean,
    isNameDuplicate: Boolean,
    onTypeChange: (PlayerType) -> Unit,
    onNameChange: (String) -> Unit,
    onDifficultyChange: (Difficulty) -> Unit,
) {
    // The name (or difficulty) and the User/CPU switch share a row at the normal font size. At a larger one
    // the difficulty's three labels no longer fit beside the switch, so it drops beneath - the controls
    // keep the width their labels need instead of being squeezed or cut off.
    val fontScale = LocalDensity.current.fontScale
    val stacked = fontScale > STACKED_PLAYER_ROW_FONT_SCALE
    val nameOrDifficulty: @Composable (Modifier) -> Unit = { controlModifier ->
        when (slot.type) {
            PlayerType.HUMAN -> CompactNameField(
                value = slot.name,
                onValueChange = onNameChange,
                isError = isNameDuplicate,
                // The field has no visible label (its value names the row), so a screen reader is
                // given one - and told why it's red, which the outline alone only shows.
                modifier = controlModifier.semantics {
                    contentDescription = "Player ${slot.slot} name"
                    if (isNameDuplicate) error(NAMES_MUST_BE_UNIQUE)
                },
            )

            PlayerType.AI -> DifficultySelector(
                selected = slot.difficulty,
                onSelect = onDifficultyChange,
                modifier = controlModifier,
            )
        }
    }
    // Grows with the font so "User" and "CPU" fit; the same for "You", so every row's control still lines up.
    val typeControlWidth = TYPE_CONTROL_WIDTH * fontScale.coerceAtLeast(1f)
    val typeControl: @Composable () -> Unit = {
        if (isTypeLocked) {
            Box(modifier = Modifier.width(typeControlWidth), contentAlignment = Alignment.Center) {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                        Text(if (slot.type == PlayerType.AI) "CPU" else "User")
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
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { typeControl() }
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
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
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
            interactionSource = interactionSource,
            colors = colors,
            contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 6.dp, bottom = 6.dp),
        )
    }
}

/**
 * Compact Easy/Medium/Hard picker for one AI slot - a segmented row rather than a full row of
 * chips, since it has to fit inside the player row alongside the name column.
 */
@Composable
private fun DifficultySelector(selected: Difficulty, onSelect: (Difficulty) -> Unit, modifier: Modifier = Modifier) {
    SegmentedChoiceRow(
        options = Difficulty.entries,
        selected = selected,
        onSelect = onSelect,
        label = { it.label },
        modifier = modifier,
        labelStyle = MaterialTheme.typography.labelSmall,
    )
}

private val Difficulty.label: String
    get() = when (this) {
        Difficulty.EASY -> "Easy"
        Difficulty.MEDIUM -> "Medium"
        Difficulty.HARD -> "Hard"
    }

/**
 * The mode picker: one field showing the current mode and its one-line description, opening a
 * scrollable list of every [GameMode] - the list is long enough to outgrow the form, so it isn't
 * laid out inline. See [ChoicePicker].
 */
@Composable
private fun GameModeSelector(selected: GameMode, onSelect: (GameMode) -> Unit) {
    ChoicePicker(
        title = "Game Mode",
        options = GameMode.entries,
        selected = selected,
        onSelect = onSelect,
        label = { it.displayName },
        description = { it.description },
    )
}

/**
 * The setup screen's modifiers. The turn timer is off when [TurnTimer.NONE] and
 * otherwise one of the lengths. The length last chosen is kept in the setup state (and saved with it) while
 * the timer is off, so switching it back on - even next game - restores it. A mode with its own timer ([GameMode.turnTimerSeconds]) overrides
 * the setting, so it's shown locked at the mode's length; the player's pick is kept for other modes.
 *
 * Number of Rolls and Stored Rolls (see [RollModifiers]) work the same way: a mode that doesn't allow
 * them ([GameMode.allowsRollModifiers]) shows them locked and off, and the player's pick is kept. Stored
 * Rolls' cap is a typed number, empty for none.
 *
 * Extended Scores is a plain switch with nothing to set, and applies in every mode. Unlucky Dice
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
    val rollsLocked = !setup.gameMode.allowsRollModifiers
    val rollsLockedNote = if (rollsLocked) "Not used in ${setup.gameMode.displayName}" else null

    val modeSeconds = setup.gameMode.turnTimerSeconds
    ModifierPicker(
        title = "Modifiers",
        description = "Optional extra rules for any game mode",
        activeNote = "Scores won't go on the Leaderboard",
        modifiers = listOf(
            ModifierSetting(
                title = "Turn timer",
                description = "A time limit for each whole turn",
                enabled = modeSeconds != null || setup.turnTimer != TurnTimer.NONE,
                onEnabledChange = { on -> onSelect(if (on) setup.turnTimerLength else TurnTimer.NONE) },
                valueLabels = if (modeSeconds != null) listOf("${modeSeconds}s") else lengths.map { it.label },
                selectedValue = if (modeSeconds != null) 0 else lengths.indexOf(setup.turnTimerLength),
                onValueSelect = { onSelect(lengths[it]) },
                lockedNote = if (modeSeconds != null) "Set by ${setup.gameMode.displayName}: ${modeSeconds}s" else null,
            ),
            ModifierSetting(
                title = "Number of Rolls",
                description = "How many rolls each turn gets",
                enabled = !rollsLocked && rolls.rollsPerTurn != null,
                onEnabledChange = { on -> onRollsPerTurn(if (on) setup.rollsPerTurnLength else null) },
                lockedNote = rollsLockedNote,
                steppers = listOf(
                    ModifierStepper(
                        value = setup.rollsPerTurnLength,
                        range = RollModifiers.MIN_ROLLS..RollModifiers.MAX_ROLLS,
                        onValueChange = onRollsPerTurn,
                        label = "Rolls per turn",
                        unit = "rolls",
                        unitSingular = "roll",
                    ),
                ),
            ),
            ModifierSetting(
                title = "Stored Rolls",
                description = "Rolls you don't use carry over to your next turn",
                enabled = !rollsLocked && rolls.storedRolls,
                onEnabledChange = onStoredRolls,
                lockedNote = rollsLockedNote,
                numberField = ModifierNumberField(
                    label = "Most rolls you can store",
                    hint = "No max",
                    initial = rolls.storedRollsMax?.toString().orEmpty(),
                    maxDigits = RollModifiers.MAX_CAP_DIGITS,
                    onValueChange = onStoredRollsMax,
                ),
            ),
            ModifierSetting(
                title = "Extended Scores",
                description = "Adds Two Pair, Evens and Odds to the scorecard",
                enabled = setup.extendedScores,
                onEnabledChange = onExtendedScores,
            ),
            ModifierSetting(
                title = "Unlucky Dice",
                description = "Rolled dice can be locked in chains: they can't be held or scored",
                enabled = setup.unluckyDiceEnabled,
                onEnabledChange = onUnluckyDiceEnabled,
                steppers = listOf(
                    ModifierStepper(
                        value = setup.unluckyDice.oddsPercent,
                        range = UnluckyDice.MIN_ODDS_PERCENT..UnluckyDice.MAX_ODDS_PERCENT,
                        onValueChange = onUnluckyOdds,
                        label = "Odds a rolled die is locked",
                        unit = "%",
                        step = UnluckyDice.ODDS_STEP_PERCENT,
                        unitSeparator = "",
                    ),
                    ModifierStepper(
                        value = setup.unluckyDice.maxDice,
                        range = UnluckyDice.MIN_MAX_DICE..UnluckyDice.MAX_MAX_DICE,
                        onValueChange = onUnluckyMaxDice,
                        label = "Most dice locked per roll",
                        unit = "dice",
                        unitSingular = "die",
                    ),
                ),
            ),
        ),
    )
}

private val TurnTimer.label: String
    get() = when (this) {
        TurnTimer.NONE -> "No timer"
        TurnTimer.SECONDS_30 -> "30s"
        TurnTimer.SECONDS_60 -> "60s"
        TurnTimer.SECONDS_120 -> "120s"
    }
