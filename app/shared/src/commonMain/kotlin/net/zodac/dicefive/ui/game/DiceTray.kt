package net.zodac.dicefive.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.game_die_coloured_spoken
import net.zodac.dicefive.resources.game_die_held_spoken
import net.zodac.dicefive.resources.game_die_hold_action
import net.zodac.dicefive.resources.game_die_locked_spoken
import net.zodac.dicefive.resources.game_die_not_held_slots_full_spoken
import net.zodac.dicefive.resources.game_die_not_held_spoken
import net.zodac.dicefive.resources.game_die_release_action
import net.zodac.dicefive.resources.game_die_spoken
import net.zodac.dicefive.resources.game_slot_empty_spoken
import net.zodac.dicefive.resources.game_slot_held_spoken
import net.zodac.dicefive.resources.game_slot_spoken
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.game.style.DiceMat
import net.zodac.dicefive.ui.game.style.DiceStyle
import net.zodac.dicefive.ui.game.style.DieMotion
import net.zodac.dicefive.ui.game.style.LocalDieCastsShadow
import net.zodac.dicefive.ui.game.style.LocalDieIndex
import net.zodac.dicefive.ui.game.style.LocalDieMotion
import net.zodac.dicefive.ui.game.style.LocalDieTumbleMillis
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.game.style.LocalIrishTricolour
import net.zodac.dicefive.ui.game.style.PickUpPath
import net.zodac.dicefive.ui.game.style.TossPath
import net.zodac.dicefive.ui.game.style.TossPose
import net.zodac.dicefive.ui.game.style.palette
import org.jetbrains.compose.resources.stringResource

private data class ScatterOffset(val xOffset: Dp, val yOffset: Dp, val rotationDegrees: Float)

/** Fixed, hand-tuned "just tumbled out of the cup" positions within a die's own column, keyed by
 * die index so a die never jumps to a different spot just because a neighbour was held or
 * released. Small values on purpose - each die now scatters only within its own column's width
 * (see [DiceTray]'s doc comment), not the whole tray. */
private val SCATTER_OFFSETS = listOf(
    ScatterOffset((-6).dp, 28.dp, -18f),
    ScatterOffset(4.dp, 4.dp, 11f),
    ScatterOffset((-3).dp, 20.dp, -9f),
    ScatterOffset(5.dp, 2.dp, 17f),
    ScatterOffset((-4).dp, 26.dp, -23f),
    ScatterOffset(3.dp, 12.dp, 14f),
    ScatterOffset((-5).dp, 24.dp, -12f),
)

private val SCATTERED_DIE_SIZE = 44.dp
private val MAX_SLOT_DIE_SIZE = 52.dp
// How far a held die sits inside its slot's edge.
private val HELD_DIE_INSET = 3.dp
private val SCATTER_AREA_HEIGHT = 96.dp
// Round the tray's contents, inside its rounded edge - which clips anything past it.
private val TRAY_PADDING = 16.dp
private val DICE_COLUMN_GAP = 14.dp

// Where more dice are rolled than held (GameMode.scoresHeldDiceOnly): the mat's columns are closer,
// and a die on it is at most this much of its column's width, so all of them fit side by side.
private val SLOTTED_MAT_COLUMN_GAP = 6.dp
private const val SLOTTED_MAT_DIE_FRACTION = 0.86f

// How much wider than it is square a die tipping over can look, nearest edge looming (TossedCube).
private const val TIPPED_FOOTPRINT = 1.06f

// The one light the dice on the mat cast their shadows from: above and to the left of the tray's
// top-left corner, in dp from that corner of the dice row. A shadow falls away from it by this much
// of the die's distance from it - so dice further from the light cast longer shadows.
private val LIGHT_X = (-48).dp
private val LIGHT_Y = (-140).dp
private const val SHADOW_LENGTH = 0.022f
private const val CYCLE_INTERVAL_MILLIS = 1_000L

/**
 * The dice area: five columns, each always representing the same die index end to end - a fixed
 * "held" slot at the top, and - once the dice have been rolled this turn, or while [rolling] is
 * shaking the cup - the same die's scattered (unheld) position further down the column, as if
 * just poured from the cup. While [rolling], scattered (unheld) dice flicker through random faces
 * to read as "still tumbling" before settling on the real roll.
 *
 * A die's clickable area is its ENTIRE column, top to bottom - not just the small slot or
 * scattered die graphic - so a tap anywhere from the hold spot down to the bottom of the tray
 * toggles that die's hold state, and (once [superuserModeActive]) holding a finger anywhere in an
 * already-held die's column cycles its face - see [columnPresses]. Uses [LocalGameVisualTheme] for
 * both the die art and the mat - in a [gameMode] whose dice carry their own colour, the dice style
 * recoloured in each die's colour (see [net.zodac.dicefive.ui.game.style.DiceStyle.recoloured]).
 *
 * A [gameMode] that rolls more dice than it holds ([GameMode.scoresHeldDiceOnly]) has fewer slots
 * than columns, so it's laid out differently - see [SlottedDice].
 */
@Composable
fun DiceTray(
    dice: List<Die>,
    gameMode: GameMode,
    enabled: Boolean,
    showDice: Boolean,
    rolling: Boolean,
    onToggleHold: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // Superuser mode (a hidden cheat - see GameViewModel.trackSuperuserSequence): once unlocked,
    // holding a finger anywhere in an already-held die's column cycles its face once a second,
    // until released or the finger slides into a different column. onCycleValue is always wired;
    // it's a no-op in the ViewModel until superuserModeActive is true, same as it being false (or
    // a given die not being held) here just means that column is never eligible to cycle.
    superuserModeActive: Boolean = false,
    onCycleValue: (Int) -> Unit = {},
) {
    val visualTheme = LocalGameVisualTheme.current

    // rememberUpdatedState, not the raw parameters: `dice` (including whichever die is currently
    // cycling) recomposes this composable every tick, which would otherwise hand the gesture
    // handler a stale closure over `dice`/`onToggleHold`/`onCycleValue` from whenever it started.
    // The handler itself is keyed only on `enabled` (see columnPresses) so a
    // value-only recomposition never restarts a press already in progress - reading everything
    // through these keeps it current regardless.
    val currentDice by rememberUpdatedState(dice)
    val currentOnToggleHold by rememberUpdatedState(onToggleHold)
    val currentOnCycleValue by rememberUpdatedState(onCycleValue)
    val currentSuperuserModeActive by rememberUpdatedState(superuserModeActive)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(visualTheme.mat.diceTrayBrush),
    ) {
        // The mat's own decoration, if it has one, sits between the brush and
        // the dice - matchParentSize so it fills whatever height the Row below ends up with.
        visualTheme.mat.DiceTrayDecoration(modifier = Modifier.matchParentSize())

        val irish = LocalIrishTricolour.current
        // Built once for the game: none of the style, the mode's colours or Luck of the Irish
        // changes mid-game, so every roll reuses the same recoloured styles.
        val diceStyles = remember(visualTheme.diceStyle, gameMode.dieColours, irish) {
            TrayDiceStyles(visualTheme.diceStyle, gameMode.dieColours, irish)
        }

        if (gameMode.scoresHeldDiceOnly) {
            SlottedDice(
                dice = dice,
                slotCount = gameMode.scoringDiceCount,
                enabled = enabled,
                showDice = showDice,
                rolling = rolling,
                diceStyles = diceStyles,
                mat = visualTheme.mat,
                onToggleHold = { currentOnToggleHold(it) },
                canCycle = { index -> currentSuperuserModeActive && currentDice.getOrNull(index)?.isHeld == true },
                onCycleValue = { currentOnCycleValue(it) },
                currentDice = { currentDice },
            )
        } else {
            DiceColumns(
                dice = dice,
                enabled = enabled,
                showDice = showDice,
                rolling = rolling,
                diceStyles = diceStyles,
                mat = visualTheme.mat,
                irish = irish,
                canCycle = { index -> currentSuperuserModeActive && currentDice.getOrNull(index)?.isHeld == true },
                onCycleValue = { currentOnCycleValue(it) },
                onToggleHold = { currentOnToggleHold(it) },
            )
        }
    }
}

/** [DiceTray]'s usual layout: a column per die, its hold slot at the top and the mat below. */
@Composable
private fun DiceColumns(
    dice: List<Die>,
    enabled: Boolean,
    showDice: Boolean,
    rolling: Boolean,
    diceStyles: TrayDiceStyles,
    mat: DiceMat,
    irish: Boolean,
    canCycle: (Int) -> Boolean,
    onCycleValue: (Int) -> Unit,
    onToggleHold: (Int) -> Unit,
) {
        val heldText = stringResource(Res.string.game_die_held_spoken)
        val notHeldText = stringResource(Res.string.game_die_not_held_spoken)
        val lockedText = stringResource(Res.string.game_die_locked_spoken)
        val holdLabel = stringResource(Res.string.game_die_hold_action)
        val releaseLabel = stringResource(Res.string.game_die_release_action)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TRAY_PADDING)
                .columnPresses(
                    enabled = enabled,
                    columnCount = dice.size,
                    canCycle = canCycle,
                    onCycle = onCycleValue,
                    onTap = onToggleHold,
                ),
            horizontalArrangement = Arrangement.spacedBy(DICE_COLUMN_GAP),
        ) {
            dice.forEachIndexed { index, die ->
                // The tray's own touch handling is one hand-rolled gesture over the whole row, which a
                // screen reader can't see into - so each die is its own node, named by its face (and
                // colour), saying whether it's held, and offering hold/release as its click action.
                val dieName = spokenDie(index, die, irish)
                val dieSemantics = if (showDice) {
                    Modifier.semantics {
                        contentDescription = dieName
                        stateDescription = if (die.isHeld) heldText else if (die.isUnlucky) lockedText else notHeldText
                        if (enabled && !die.isUnlucky) {
                            onClick(label = if (die.isHeld) releaseLabel else holdLabel) {
                                onToggleHold(index)
                                true
                            }
                        }
                    }
                } else {
                    Modifier
                }
                DiceColumn(
                    die = die,
                    show = showDice,
                    rolling = rolling,
                    scatter = SCATTER_OFFSETS[index % SCATTER_OFFSETS.size],
                    seed = index,
                    diceStyles = diceStyles,
                    mat = mat,
                    modifier = Modifier.weight(1f).then(dieSemantics),
                )
            }
        }
}

/** What TalkBack says of a die locked by Unlucky Dice: the chains drawn over it, in words. */
/** "Die 2, 5" - or "Die 2, red 5" with coloured dice - what TalkBack names a die by. */
@Composable
private fun spokenDie(index: Int, die: Die, irish: Boolean): String {
    val colour = die.colour
    return if (colour == null) {
        stringResource(Res.string.game_die_spoken, index + 1, die.value)
    } else {
        stringResource(Res.string.game_die_coloured_spoken, index + 1, colour.spokenName(irish), die.value)
    }
}

/**
 * The tray's touch handling for one row of [columnCount] equal-width columns: a tap anywhere in a
 * column - top to bottom, not just on the die drawn there - calls [onTap] with it, and (where
 * [canCycle] says a column may, for superuser mode) holding a finger in it calls [onCycle] once a
 * second instead, until released or the finger slides into a different column.
 *
 * The touch tracking is owned by the whole row, not each column individually: Compose locks a
 * pointer's move/up events to whichever node first hit-tested its down event, so a per-column handler
 * could never see a finger that started on a sibling column and slid over - dragging across the mat
 * has to be handled at the one shared level that's under the finger the whole time. Sliding into
 * another column cancels whatever the previous column was doing (a pending tap, or cycling) and starts
 * fresh on the new one; only the column the finger is actually released over can register a tap or
 * leave cycling in effect.
 *
 * The lambdas are read when a press happens, not when this is first applied - the handler is keyed only
 * on [enabled] and [columnCount], so a value-only recomposition never restarts a press in progress -
 * so they should read what they need through `rememberUpdatedState`.
 */
@Composable
private fun Modifier.columnPresses(
    enabled: Boolean,
    columnCount: Int,
    canCycle: (Int) -> Boolean,
    onCycle: (Int) -> Unit,
    onTap: (Int) -> Unit,
): Modifier {
    if (!enabled) return this
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // No indication/ripple here on purpose: at the size of a whole column it painted as an obvious
    // translucent rectangle over the entire clickable area, not a per-die press effect.
    //
    // Hand-rolled instead of Modifier.clickable: a plain clickable's gesture recognizer treats enough
    // drag as a cancel, which handed off to this screen's enclosing verticalScroll on the slightest
    // finger movement - even movement that stayed well inside one column - cancelling the press (and
    // the superuser cycling with it). Consuming every pointer change for as long as any pointer here
    // stays down denies the scroll container that drag delta, so it never has grounds to steal the
    // gesture.
    return pointerInput(enabled, columnCount) {
        // coroutineScope for a real CoroutineScope to launch the concurrent cycle-ticking coroutine
        // on (PointerInputScope itself isn't one). The whole press is one awaitEachGesture, so no
        // pointer event can slip through between reading the down and tracking what follows; launch
        // and cancel aren't suspending calls, so the restricted gesture scope can still make them
        // against the outer coroutineScope.
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false).also { it.consume() }
                var activeIndex = columnIndexForX(down.position.x, size.width, columnCount)
                var cycled = false

                fun startCycling() =
                    if (canCycle(activeIndex)) {
                        val index = activeIndex
                        launch {
                            while (isActive) {
                                lifecycle.delayWhileResumed(CYCLE_INTERVAL_MILLIS)
                                cycled = true
                                onCycle(index)
                            }
                        }
                    } else {
                        null
                    }

                var cycleJob = startCycling()

                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                    val pointer = event.changes.firstOrNull { it.id == down.id }
                    val newIndex = pointer?.let { columnIndexForX(it.position.x, size.width, columnCount) }
                    if (newIndex != null && newIndex != activeIndex) {
                        // Crossed into a different column: whatever the previous one was doing (a
                        // pending tap, or cycling) is abandoned, not completed - only the column the
                        // finger actually settles on and releases over acts.
                        cycleJob?.cancel()
                        activeIndex = newIndex
                        cycled = false
                        cycleJob = startCycling()
                    }
                } while (event.changes.any { it.pressed })
                cycleJob?.cancel()

                // Only a press that lasted long enough to actually change the die's face suppresses
                // the tap - a quick tap (released before the first 1s cycle tick) still acts as
                // normal, and releasing right after cycling doesn't ALSO immediately act on the value
                // just picked.
                if (!cycled) onTap(activeIndex)
            }
        }
    }
}

/**
 * The dice area where more dice are rolled than held ([GameMode.scoresHeldDiceOnly]): a row of
 * [slotCount] hold slots across the top, and below it the mat, split into a narrower column per die,
 * each die rolling and lying in its own (so they never overlap) and drawn smaller to fit. Holding and
 * letting go are separate targets: a tap in a die's mat column holds it, into the lowest free slot
 * (nothing happens with every slot full), and a tap on a slot lets its die go back to the mat. In
 * superuser mode, a held die's face is cycled by holding a finger on its slot.
 *
 * Every die and every slot is its own screen-reader node: a die on the mat says it isn't held and
 * offers Hold while there's a free slot; a slot names its position, and the die in it (offering
 * Release) or that it's empty.
 */
@Composable
private fun SlottedDice(
    dice: List<Die>,
    slotCount: Int,
    enabled: Boolean,
    showDice: Boolean,
    rolling: Boolean,
    diceStyles: TrayDiceStyles,
    mat: DiceMat,
    onToggleHold: (Int) -> Unit,
    canCycle: (Int) -> Boolean,
    onCycleValue: (Int) -> Unit,
    currentDice: () -> List<Die>,
) {
    val irish = LocalIrishTricolour.current
    val heldText = stringResource(Res.string.game_die_held_spoken)
    val notHeldText = stringResource(Res.string.game_die_not_held_spoken)
    val lockedText = stringResource(Res.string.game_die_locked_spoken)
    val holdLabel = stringResource(Res.string.game_die_hold_action)
    val releaseLabel = stringResource(Res.string.game_die_release_action)
    val emptyText = stringResource(Res.string.game_slot_empty_spoken)
    val slotsFullText = stringResource(Res.string.game_die_not_held_slots_full_spoken)
    // Each die keeps one DieMotion (see DiceColumn) whether it's in a slot or on the mat.
    val motions = dice.indices.map { rememberDieMotion(it, diceStyles) }
    fun dieInSlot(dice: List<Die>, slot: Int): Int? = dice.indexOfFirst { it.isHeld && it.heldSlot == slot }.takeIf { it >= 0 }
    val slotsFull = dice.count { it.isHeld } >= slotCount

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(TRAY_PADDING)) {
        // A slot is no bigger than a mat column, so a die is the same size held as it is on the mat; the
        // row of them is as tall as a mode that holds every die has it, so the tray is too.
        val matColumnWidth = (maxWidth - SLOTTED_MAT_COLUMN_GAP * (dice.size - 1)) / dice.size
        val slotRowHeight = minOf(MAX_SLOT_DIE_SIZE, (maxWidth - DICE_COLUMN_GAP * (slotCount - 1)) / slotCount)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotRowHeight)
                    .columnPresses(
                        enabled = enabled,
                        columnCount = slotCount,
                        canCycle = { slot -> dieInSlot(currentDice(), slot)?.let(canCycle) == true },
                        onCycle = { slot -> dieInSlot(currentDice(), slot)?.let(onCycleValue) },
                        onTap = { slot -> dieInSlot(currentDice(), slot)?.let(onToggleHold) },
                    ),
                horizontalArrangement = Arrangement.spacedBy(DICE_COLUMN_GAP),
            ) {
                for (slot in 0 until slotCount) {
                    val index = dieInSlot(dice, slot)
                    val die = index?.let { dice[it] }
                    val slotName = stringResource(Res.string.game_slot_spoken, slot + 1, slotCount)
                    val heldSlotName = if (index != null && die != null) {
                        stringResource(Res.string.game_slot_held_spoken, slot + 1, slotCount, spokenDie(index, die, irish))
                    } else {
                        slotName
                    }
                    val slotSemantics = if (showDice) {
                        Modifier.semantics {
                            if (index != null && die != null) {
                                contentDescription = heldSlotName
                                stateDescription = heldText
                                if (enabled) {
                                    onClick(label = releaseLabel) {
                                        onToggleHold(index)
                                        true
                                    }
                                }
                            } else {
                                contentDescription = slotName
                                stateDescription = emptyText
                            }
                        }
                    } else {
                        Modifier
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().then(slotSemantics), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalDieIndex provides (index ?: 0), LocalDieMotion provides index?.let { motions[it] }) {
                            HoldSlot(die = die?.takeIf { showDice }, diceStyles = diceStyles, mat = mat, matColumnWidth = matColumnWidth)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(SLOT_TO_MAT_GAP))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .columnPresses(
                        enabled = enabled,
                        columnCount = dice.size,
                        canCycle = { false },
                        onCycle = {},
                        onTap = { index ->
                            val now = currentDice()
                            val tapped = now.getOrNull(index)
                            if (tapped?.isHeld == false && !tapped.isUnlucky && now.count { it.isHeld } < slotCount) onToggleHold(index)
                        },
                    ),
                horizontalArrangement = Arrangement.spacedBy(SLOTTED_MAT_COLUMN_GAP),
            ) {
                dice.forEachIndexed { index, die ->
                    val dieName = spokenDie(index, die, irish)
                    val dieSemantics = if (showDice && !die.isHeld) {
                        Modifier.semantics {
                            contentDescription = dieName
                            stateDescription = when {
                                die.isUnlucky -> lockedText
                                slotsFull -> slotsFullText
                                else -> notHeldText
                            }
                            if (enabled && !slotsFull && !die.isUnlucky) {
                                onClick(label = holdLabel) {
                                    onToggleHold(index)
                                    true
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                    CompositionLocalProvider(LocalDieIndex provides index, LocalDieMotion provides motions[index]) {
                        ScatterArea(
                            die = die,
                            show = showDice,
                            rolling = rolling,
                            scatter = SCATTER_OFFSETS[index % SCATTER_OFFSETS.size],
                            seed = index,
                            diceStyles = diceStyles,
                            columnGap = SLOTTED_MAT_COLUMN_GAP,
                            fitToColumn = true,
                            modifier = Modifier.weight(1f).then(dieSemantics),
                        )
                    }
                }
            }
        }
    }
}

/** Which of [columnCount] equal-width columns a touch at local x-position [x] (within a row of
 * [totalWidthPx] pixels) falls in. Approximate - it divides the row's full width evenly rather
 * than accounting for the small gaps Arrangement.spacedBy leaves between columns - which only
 * shifts a column boundary by a couple of dp right where the gap itself already is, not somewhere
 * that reads as a wrong die. */
private fun columnIndexForX(x: Float, totalWidthPx: Int, columnCount: Int): Int =
    (x / totalWidthPx.toFloat() * columnCount).toInt().coerceIn(0, columnCount - 1)

/**
 * One die's full column: the slot (top) and its scattered position (below), both driven by the
 * same [die]. Purely visual - [DiceTray] owns all touch handling for the whole row (see its doc
 * comment for why a per-column handler can't support sliding between dice).
 */
@Composable
private fun DiceColumn(
    die: Die,
    show: Boolean,
    rolling: Boolean,
    scatter: ScatterOffset,
    seed: Int,
    diceStyles: TrayDiceStyles,
    mat: DiceMat,
    modifier: Modifier = Modifier,
) {
    val motion = rememberDieMotion(seed, diceStyles)
    // Which physical die this column is, so a natural-looking style can give each its own pattern.
    CompositionLocalProvider(LocalDieIndex provides seed, LocalDieMotion provides motion) {
        DiceColumnContent(die, show, rolling, scatter, seed, diceStyles, mat, modifier)
    }
}

/**
 * For a style whose faces the die's movement throws about (googly eyes): die [seed]'s movement, kept
 * for as long as the die is, so it keeps its looks as it's held and released. Moved on every frame
 * only while the die or its pupils are moving. Null for any other style, and under reduced motion:
 * pupils sliding about as the die moves are motion too, so they stay put.
 */
@Composable
private fun rememberDieMotion(seed: Int, diceStyles: TrayDiceStyles): DieMotion? {
    val motion = diceStyles.plain.pupilTravel?.takeIf { !LocalReduceMotion.current }
        ?.let { travel -> remember(travel) { DieMotion(seed, travel) } }
    if (motion != null) {
        LaunchedEffect(motion, motion.awake) {
            if (motion.awake) motion.follow()
        }
    }
    return motion
}

@Composable
private fun DiceColumnContent(
    die: Die,
    show: Boolean,
    rolling: Boolean,
    scatter: ScatterOffset,
    seed: Int,
    diceStyles: TrayDiceStyles,
    mat: DiceMat,
    modifier: Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HoldSlot(die = die.takeIf { show && it.isHeld }, diceStyles = diceStyles, mat = mat)

        Spacer(modifier = Modifier.height(SLOT_TO_MAT_GAP))

        ScatterArea(die, show, rolling, scatter, seed, diceStyles, columnGap = DICE_COLUMN_GAP)
    }
}

private val SLOT_TO_MAT_GAP = 18.dp

/** How tall the usual tray is, [width] wide: its padding, the row of hold slots (each a column wide) and the mat. */
internal fun diceTrayHeight(width: Dp): Dp {
    val slot = (width - TRAY_PADDING * 2 - DICE_COLUMN_GAP * (DICE_COUNT - 1)) / DICE_COUNT
    return TRAY_PADDING * 2 + slot + SLOT_TO_MAT_GAP + SCATTER_AREA_HEIGHT
}

private const val DICE_COUNT = 5

/**
 * A hold slot, square, with [die] in it, if any. As wide as its column, its die just inside its edge -
 * or, given the [matColumnWidth] of a mat of more dice than slots, as wide as one of those columns, its
 * die drawn the size it is on that mat (see SlottedDice). It once also had a 52dp cap, placed after the
 * size, where it could never take effect - so every phone's slots have always been their column's
 * width, and that's kept. The game screen's widest layout (see gameLayout) is what bounds them now.
 */
@Composable
private fun HoldSlot(die: Die?, diceStyles: TrayDiceStyles, mat: DiceMat, matColumnWidth: Dp? = null) {
    val shape = RoundedCornerShape(10.dp)
    val size = if (matColumnWidth == null) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(matColumnWidth)
    Box(
        modifier = size
            .clip(shape)
            .background(mat.slotSocketBrush)
            .border(1.5.dp, mat.slotSocketBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (die != null) {
            val dieModifier = if (matColumnWidth == null) {
                Modifier.fillMaxSize().padding(HELD_DIE_INSET)
            } else {
                Modifier.size(matDieSize(diceStyles.forDie(die), matColumnWidth, fitToColumn = true))
            }
            DieFace(die = die, held = true, diceStyles = diceStyles, modifier = dieModifier)
        }
    }
}

/**
 * How big a die of [style] is drawn on the mat, in a column [columnWidth] wide: the size it is held -
 * its slot is a column wide, the die just inside it - so a die is the same size held or let go, and the
 * two scale together with the screen (see gameLayout). Where it's [fitToColumn] - a mat of more, narrower
 * columns than usual (see SlottedDice) - it's no more than [SCATTERED_DIE_SIZE] or
 * [SLOTTED_MAT_DIE_FRACTION] of the column, and its slot draws it the same. A die that stands up (the
 * Egg) isn't a cube seen from above, so it always spans the column, as it spans its slot when held.
 */
private fun matDieSize(style: DiceStyle, columnWidth: Dp, fitToColumn: Boolean): Dp = when {
    fitToColumn && !style.standsUpright -> minOf(SCATTERED_DIE_SIZE, columnWidth * SLOTTED_MAT_DIE_FRACTION)
    else -> columnWidth - HELD_DIE_INSET * 2
}

/**
 * Counts a roll's two moments - the cup starting to shake ([starts]) and the roll landing
 * ([landings]) - as the very composition they happen in, not a frame later from an effect. Keying
 * an animation on these counts is what lets it be under way in that same frame: a toss starts the
 * instant new dice land, so they never flash up at rest, showing the result, for a frame first.
 */
internal class RollTracker(private var wasRolling: Boolean) {
    var starts = 0
        private set
    var landings = 0
        private set

    /** Notes [rolling] for this composition. Safe to call more than once for the same value. */
    fun update(rolling: Boolean) {
        if (rolling && !wasRolling) starts++
        if (!rolling && wasRolling) landings++
        wasRolling = rolling
    }
}

/** How long a tossed die takes from leaving the cup to coming to rest - scoring waits for it. */
internal const val DICE_TOSS_MILLIS = 900

// How long an unheld die takes to be swept off the mat once the cup starts shaking - well inside
// the shake, so the mat is clear for the rest of it.
private const val PICK_UP_MILLIS = 200

/**
 * Where an unheld die lies on the mat, and how it gets there.
 *
 * With the full roll (the default): as the cup starts shaking, the die is swept off the mat past its
 * near edge, out of sight for the rest of the shake ([PickUpPath]) - it's in the cup; the moment the
 * roll lands (with the landing sound), it's thrown back on from there - up its column into the far
 * wall, bouncing and tumbling back to rest on its result ([TossPath]). Walls either side
 * keep it in its own column. Under reduced motion (the player's "Remove animations", or the
 * system's) it doesn't move at all: it stays where it lies while rolling and snaps to its result.
 */
@Composable
private fun ScatterArea(
    die: Die,
    show: Boolean,
    rolling: Boolean,
    scatter: ScatterOffset,
    seed: Int,
    diceStyles: TrayDiceStyles,
    columnGap: Dp,
    modifier: Modifier = Modifier,
    // Draws the die no wider than SLOTTED_MAT_DIE_FRACTION of its column, for a mat of more, narrower
    // columns than usual (see SlottedDice).
    fitToColumn: Boolean = false,
) {
    // Under reduced motion there's no pick-up or toss: the die stays where it lies until it snaps to its result.
    val simple = LocalReduceMotion.current
    val tracker = remember { RollTracker(rolling) }
    tracker.update(rolling)
    val pickUp = remember(tracker.starts) { Animatable(if (tracker.starts == 0 || simple) 1f else 0f) }
    LaunchedEffect(tracker.starts) {
        if (pickUp.value < 1f) pickUp.animateTo(1f, tween(PICK_UP_MILLIS, easing = LinearEasing))
    }
    val toss = remember(tracker.landings) { Animatable(if (tracker.landings == 0 || simple) 1f else 0f) }
    LaunchedEffect(tracker.landings) {
        if (toss.value < 1f) toss.animateTo(1f, tween(DICE_TOSS_MILLIS, easing = LinearEasing))
    }
    // The face the die was picked up showing, for the throw to start from - noted while it's being
    // picked up, since by the time the roll lands the die already holds its new value.
    val cupFace = remember { intArrayOf(0) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(SCATTER_AREA_HEIGHT)) {
        val style = diceStyles.forDie(die)
        val dieSize = matDieSize(style, maxWidth, fitToColumn)
        // Past the tray's near edge, the whole die clipped off by it: off the mat entirely - a third
        // of a die further than its own height, since a die lying at an angle pokes its corners up
        // past its square outline.
        val startY = (SCATTER_AREA_HEIGHT + TRAY_PADDING + dieSize / 3).value
        val restY = scatter.yOffset.value
        val size = dieSize.value
        if (rolling && !(show && !die.isHeld)) cupFace[0] = 0
        // A turn's first dice aren't on the mat while the cup shakes, but they're built waiting off
        // its near edge all the same - where a later roll's dice have been swept to by then - so the
        // throw carries on in them instead of building them on the frame the cup tips.
        val offMat = !show && rolling && !simple
        if ((!show && !offMat) || die.isHeld) return@BoxWithConstraints
        val rest = Modifier.align(Alignment.TopCenter).size(dieSize)
        val selfTumbling = style.tumblesItself
        // How the die lies at rest: at its own angle, or straight up for one that stands (the Egg).
        val restYaw = if (style.standsUpright) 0f else scatter.rotationDegrees

        // A die's rotated footprint stays inside its own column plus half the gap to the next, so
        // neighbours can never overlap, however they're turned - pushed back in from the edge as if
        // off a wall. A little extra for a cube tipping over, whose near edge looms a touch wider.
        val halfSlot = (maxWidth + columnGap) / 2
        fun keptIn(x: Dp, yawDegrees: Float): Dp {
            val radians = yawDegrees * PI.toFloat() / 180f
            val half = dieSize / 2 * (abs(cos(radians)) + abs(sin(radians))) * TIPPED_FOOTPRINT
            val room = (halfSlot - half).coerceAtLeast(0.dp)
            return x.coerceIn(-room, room)
        }

        // Where the die sits in the whole dice row, for its shadow: this column's place in the row
        // plus where the die is within it.
        val columnLeft = (maxWidth + columnGap) * seed

        // Where the die is, for a style whose faces its movement throws about (DieMotion).
        val motion = LocalDieMotion.current

        /**
         * Tells [motion] the die is at [x]/[y] in its column (measured in die sizes), turned
         * [yawDegrees], and [roll] quarter-turns into a tumble.
         */
        @Composable
        fun Track(x: Dp, y: Dp, yawDegrees: Float, roll: Float = 0f) {
            if (motion == null) return
            val centre = Offset((columnLeft + maxWidth / 2 + x) / dieSize, (y + dieSize / 2) / dieSize)
            // The face mostly in view: tipping away over its top edge for the first half of each
            // quarter-turn, then the next one tipping up to take its place.
            val tipped = roll - floor(roll)
            val tip = if (tipped < 0.5f) tipped * 90f else (tipped - 1f) * 90f
            SideEffect { motion.moveTo(centre, yawDegrees, tip) }
        }

        /**
         * The die's ground shadow, cast from the one light, [lift] (0..1) off the mat, in the outline
         * of the die itself - its style's. [tumbleMillis] is how long a die that turns itself has been tumbling mid-toss, for a shadow
         * that turns with it.
         */
        @Composable
        fun Shadow(x: Dp, y: Dp, yawDegrees: Float, lift: Float, tumbleMillis: Float? = null) {
            val shape = style.shadowShape(die.value, seed, tumbleMillis)
            val centreX = columnLeft + maxWidth / 2 + x
            val centreY = y + dieSize / 2
            // Away from the light, further the further the die is from it - and further again, and
            // softer, the higher the die is off the mat.
            val reach = SHADOW_LENGTH * (1f + lift * 2f)
            GroundShadow(
                shape = shape,
                lift = lift,
                modifier = rest
                    .offset(x = x + (centreX - LIGHT_X) * reach, y = y + (centreY - LIGHT_Y) * reach)
                    .graphicsLayer { rotationZ = yawDegrees },
            )
        }

        @Composable
        fun Moving(pose: TossPose, ring: List<Int>, finalTurns: Int, tossMillis: Float?) {
            val yaw = restYaw + pose.yawDegrees
            val x = keptIn(scatter.xOffset + pose.dx.dp, yaw)
            val y = scatter.yOffset + pose.dy.dp
            // A cube tipping over an edge rises off the mat, highest halfway over.
            val lift = if (selfTumbling) 0f else sin((pose.roll - floor(pose.roll)) * PI.toFloat()) * 0.2f
            val tumbleMillis = tossMillis.takeIf { selfTumbling }
            Shadow(x, y, yaw, lift, tumbleMillis)
            Track(x, y, yaw, roll = if (selfTumbling) 0f else pose.roll)
            val placed = rest
                .offset(x = x, y = y)
                .graphicsLayer { rotationZ = yaw }
            if (selfTumbling) {
                // A D20 turns itself as it goes, landing on its face as it stops.
                CompositionLocalProvider(LocalDieTumbleMillis provides tumbleMillis) {
                    // Chains only once it has landed, not on a die still tumbling.
                    DieFace(die = die.copy(isUnlucky = false), held = false, diceStyles = diceStyles, modifier = placed)
                }
            } else {
                style.TossedDie(roll = pose.roll, finalTurns = finalTurns, ring = ring, modifier = placed)
            }
        }

        // Every die on the mat takes its shadow from the one light (Shadow, above), not its own.
        CompositionLocalProvider(LocalDieCastsShadow provides false) {
            val pickingUp = rolling && !simple
            when {
                pickingUp || (toss.value < 1f && !simple) -> {
                    // Swept off the mat while the cup shakes, still showing the last roll...
                    val pickUpPath = remember(tracker.starts, die.value) { PickUpPath(die.value, startY, restY) }
                    // ...then thrown back on once it lands.
                    val sideRoom = ((maxWidth - dieSize) / 2 - abs(scatter.xOffset.value).dp).value.coerceAtLeast(0f)
                    val tossPath = remember(tracker.landings) {
                        TossPath(
                            seed = tracker.landings * 7 + seed,
                            result = die.value,
                            startFace = cupFace[0].takeIf { it != 0 },
                            restTop = style.topFace(die.value),
                            startY = startY,
                            restY = restY,
                            dieSize = size,
                            sideRoom = sideRoom,
                        )
                    }
                    // Both moves through the one Moving call, so the moment the roll lands the die
                    // carries on in the nodes it already has. A call for each move had every die's
                    // nodes torn down and built again on that very frame - the one the cup starts to
                    // tip over on, which it visibly hitched.
                    if (pickingUp && !offMat) cupFace[0] = pickUpPath.value
                    Moving(
                        pose = if (pickingUp) pickUpPath.pose(if (offMat) 1f else pickUp.value) else tossPath.pose(toss.value),
                        ring = if (pickingUp) listOf(die.value) else tossPath.ring,
                        finalTurns = if (pickingUp) 0 else tossPath.finalTurns,
                        tossMillis = if (pickingUp) null else toss.value * DICE_TOSS_MILLIS,
                    )
                }

                else -> {
                    // At rest - or, under reduced motion, lying still on its last face while the cup "shakes".
                    val restX = keptIn(scatter.xOffset, restYaw)
                    Shadow(restX, scatter.yOffset, restYaw, lift = 0f)
                    Track(restX, scatter.yOffset, restYaw)
                    // Landed: the pupils stop with the dice, rather than sloshing on once scoring is open.
                    if (motion != null && !rolling) SideEffect { motion.settle() }
                    DieFace(
                        die = die,
                        held = false,
                        diceStyles = diceStyles,
                        modifier = rest
                            .offset(x = restX, y = scatter.yOffset)
                            .graphicsLayer { rotationZ = restYaw },
                    )
                }
            }
        }
    }
}

/**
 * A soft shadow the size of a die lying flat, in its [shape], built up from a few faint copies of
 * growing size so its edge fades out rather than stopping hard. [lift] (0..1) - how far the die is off the
 * mat - spreads and fades it.
 */
@Composable
private fun GroundShadow(shape: Shape, lift: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outline = shape.createOutline(size, layoutDirection, this)
        val softness = size.minDimension * (0.06f + lift * 0.12f)
        val layer = Color.Black.copy(alpha = 0.09f * (1f - lift * 0.6f))
        for (step in 0..3) {
            val grow = softness * step / 3f
            scale(scaleX = (size.width + grow * 2) / size.width, scaleY = (size.height + grow * 2) / size.height) {
                drawOutline(outline, layer)
            }
        }
    }
}

/** A die in the player's chosen dice style - in its own colour, when it has one (see [TrayDiceStyles]). */
@Composable
private fun DieFace(die: Die, held: Boolean, diceStyles: TrayDiceStyles, modifier: Modifier) {
    val style = diceStyles.forDie(die)
    if (!die.isUnlucky) {
        style.Die(value = die.value, held = held, modifier = modifier)
        return
    }
    // A die locked by Unlucky Dice: the chains are laid over it, so they follow it wherever it lies.
    Box(modifier = modifier) {
        style.Die(value = die.value, held = held, modifier = Modifier.fillMaxSize())
        LockedChains(
            shape = style.shadowShape(die.value, LocalDieIndex.current, null),
            reach = style.lockedChainReach,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * The player's [plain] dice style, and that style [DiceStyle.recoloured] in each of [colours] - Luck
 * of the Irish's while [irish] - all built up front, so a die of any colour is drawn in the same
 * style object every time it lands that colour.
 */
private class TrayDiceStyles(val plain: DiceStyle, colours: List<DieColour>, irish: Boolean) {
    private val coloured: Map<DieColour, DiceStyle> = colours.associateWith { plain.recoloured(it.palette(irish)) }

    /** The style [die] is drawn in: recoloured in its colour when it has one. */
    fun forDie(die: Die): DiceStyle = die.colour?.let { coloured.getValue(it) } ?: plain
}
