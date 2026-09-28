package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.game.style.ColouredDie
import net.zodac.dicefive.ui.game.style.DiceMat
import net.zodac.dicefive.ui.game.style.DiceStyle
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme

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
)

private val SCATTERED_DIE_SIZE = 44.dp
private val MAX_SLOT_DIE_SIZE = 52.dp
private val SCATTER_AREA_HEIGHT = 96.dp
private const val SCRAMBLE_INTERVAL_MILLIS = 90L
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
 * already-held die's column cycles its face. The touch tracking is owned by this whole row, not
 * each column individually: Compose locks a pointer's move/up events to whichever node first
 * hit-tested its down event, so a per-column handler could never see a finger that started on a
 * sibling column and slid over - dragging across the mat has to be handled at the one shared level
 * that's under the finger the whole time. Sliding into another die's column cancels whatever the
 * previous column was doing (a pending click, or superuser cycling) and starts fresh on the new
 * one; only the column the finger is actually released over can register a click or leave cycling
 * in effect. Uses [LocalGameVisualTheme] for both the die art and the mat - except in a [gameMode]
 * whose dice carry their own colour, which are drawn in that colour instead of the dice style (see
 * [ColouredDie]).
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

    var scrambleTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(rolling) {
        while (rolling) {
            delay(SCRAMBLE_INTERVAL_MILLIS)
            scrambleTick++
        }
    }

    // rememberUpdatedState, not the raw parameters: `dice` (including whichever die is currently
    // cycling) recomposes this composable every tick, which would otherwise hand the gesture
    // handler a stale closure over `dice`/`onToggleHold`/`onCycleValue` from whenever it started.
    // The handler itself is keyed only on `enabled` (see the pointerInput call below) so a
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .then(
                    if (enabled) {
                        // No indication/ripple here on purpose: at the size of a whole column it
                        // painted as an obvious translucent rectangle over the entire clickable
                        // area, not a per-die press effect.
                        //
                        // Hand-rolled instead of Modifier.clickable: a plain clickable's gesture
                        // recognizer treats enough drag as a cancel, which handed off to this
                        // screen's enclosing verticalScroll on the slightest finger movement -
                        // even movement that stayed well inside one column - cancelling the press
                        // (and the superuser cycling with it). Consuming every pointer change for
                        // as long as any pointer here stays down denies the scroll container that
                        // drag delta, so it never has grounds to steal the gesture.
                        Modifier.pointerInput(enabled) {
                            // coroutineScope for a real CoroutineScope to launch the concurrent
                            // cycle-ticking coroutine on (PointerInputScope itself isn't one). The
                            // whole press is one awaitEachGesture, so no pointer event can slip
                            // through between reading the down and tracking what follows; launch and
                            // cancel aren't suspending calls, so the restricted gesture scope can
                            // still make them against the outer coroutineScope.
                            coroutineScope {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false).also { it.consume() }
                                    val columnCount = currentDice.size
                                    var activeIndex = columnIndexForX(down.position.x, size.width, columnCount)
                                    var cycled = false

                                    fun cycleEligible(index: Int) =
                                        currentSuperuserModeActive && currentDice.getOrNull(index)?.isHeld == true

                                    fun startCycling() =
                                        if (cycleEligible(activeIndex)) {
                                            val index = activeIndex
                                            launch {
                                                while (isActive) {
                                                    delay(CYCLE_INTERVAL_MILLIS)
                                                    cycled = true
                                                    currentOnCycleValue(index)
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
                                            // Crossed into a different die's column: whatever
                                            // the previous one was doing (a pending click, or
                                            // cycling) is abandoned, not completed - only the
                                            // column the finger actually settles on and
                                            // releases over acts.
                                            cycleJob?.cancel()
                                            activeIndex = newIndex
                                            cycled = false
                                            cycleJob = startCycling()
                                        }
                                    } while (event.changes.any { it.pressed })
                                    cycleJob?.cancel()

                                    // Only a press that lasted long enough to actually change the
                                    // die's face suppresses the tap - a quick tap (released before
                                    // the first 1s cycle tick) still toggles hold as normal, and
                                    // releasing right after cycling doesn't ALSO immediately
                                    // toggle the value just picked.
                                    if (!cycled) currentOnToggleHold(activeIndex)
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            dice.forEachIndexed { index, die ->
                DiceColumn(
                    die = die,
                    show = showDice,
                    rolling = rolling,
                    scrambleTick = scrambleTick,
                    scatter = SCATTER_OFFSETS[index % SCATTER_OFFSETS.size],
                    seed = index,
                    gameMode = gameMode,
                    diceStyle = visualTheme.diceStyle,
                    mat = visualTheme.mat,
                    modifier = Modifier.weight(1f),
                )
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
    scrambleTick: Int,
    scatter: ScatterOffset,
    seed: Int,
    gameMode: GameMode,
    diceStyle: DiceStyle,
    mat: DiceMat,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .sizeIn(maxWidth = MAX_SLOT_DIE_SIZE, maxHeight = MAX_SLOT_DIE_SIZE)
                .clip(shape)
                .background(mat.slotSocketBrush)
                .border(1.5.dp, mat.slotSocketBorder, shape),
        ) {
            if (show && die.isHeld) {
                DieFace(die = die, held = true, diceStyle = diceStyle, modifier = Modifier.fillMaxSize().padding(3.dp))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(modifier = Modifier.fillMaxWidth().height(SCATTER_AREA_HEIGHT)) {
            if (show && !die.isHeld) {
                // Reads scrambleTick so each tick's recomposition seeds a fresh face -
                // deliberately not remember()'d, since a cached value wouldn't flicker. A coloured
                // die tumbles through colours as well as numbers.
                val displayDie = if (rolling) scrambledFace(Random(scrambleTick * 31 + seed), gameMode) else die
                DieFace(
                    die = displayDie,
                    held = false,
                    diceStyle = diceStyle,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(SCATTERED_DIE_SIZE)
                        .offset(x = scatter.xOffset, y = scatter.yOffset)
                        .graphicsLayer { rotationZ = scatter.rotationDegrees },
                )
            }
        }
    }
}

/** A die in its own colour when it has one, otherwise in the player's chosen [diceStyle]. */
@Composable
private fun DieFace(die: Die, held: Boolean, diceStyle: DiceStyle, modifier: Modifier) {
    val colour = die.colour
    if (colour != null) {
        ColouredDie(value = die.value, colour = colour, held = held, modifier = modifier)
    } else {
        diceStyle.Die(value = die.value, held = held, modifier = modifier)
    }
}

/** A random face [gameMode]'s dice could land on - its number, and its colour if it has them. */
private fun scrambledFace(random: Random, gameMode: GameMode): Die {
    val values = gameMode.dieValues
    val colours = gameMode.dieColours
    return Die(
        value = random.nextInt(values.first, values.last + 1),
        colour = if (colours.isEmpty()) null else colours[random.nextInt(colours.size)],
    )
}
