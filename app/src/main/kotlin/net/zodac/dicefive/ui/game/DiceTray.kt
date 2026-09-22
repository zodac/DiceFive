package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.ui.game.style.DiceStyle
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.theme.SlotSocketBorder
import net.zodac.dicefive.ui.theme.SlotSocketBottom
import net.zodac.dicefive.ui.theme.SlotSocketTop

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
 * already-held die's column cycles its face. Uses [LocalGameVisualTheme] for both the die art and
 * the background.
 */
@Composable
fun DiceTray(
    dice: List<Die>,
    enabled: Boolean,
    showDice: Boolean,
    rolling: Boolean,
    onToggleHold: (Int) -> Unit,
    // Superuser mode (a hidden cheat - see GameViewModel.trackSuperuserSequence): once unlocked,
    // holding a finger anywhere in an already-held die's column cycles its face once a second,
    // until released. onCycleValue is always wired; it's a no-op in the ViewModel until
    // superuserModeActive is true, same as it being false (or the die not being held) here just
    // means DiceColumn never attaches the press-and-hold handler in the first place.
    superuserModeActive: Boolean = false,
    onCycleValue: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val visualTheme = LocalGameVisualTheme.current

    var scrambleTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(rolling) {
        while (rolling) {
            delay(SCRAMBLE_INTERVAL_MILLIS)
            scrambleTick++
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(visualTheme.background.diceTrayBrush)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        dice.forEachIndexed { index, die ->
            DiceColumn(
                die = die,
                show = showDice,
                enabled = enabled,
                rolling = rolling,
                scrambleTick = scrambleTick,
                scatter = SCATTER_OFFSETS[index % SCATTER_OFFSETS.size],
                seed = index,
                diceStyle = visualTheme.diceStyle,
                onClick = { onToggleHold(index) },
                onCycleValue = if (superuserModeActive && die.isHeld) { { onCycleValue(index) } } else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One die's full column: the slot (top) and its scattered position (below), both driven by the
 * same [die] and both inside one clickable/press-trackable region spanning the whole column - see
 * [DiceTray]'s doc comment for why.
 */
@Composable
private fun DiceColumn(
    die: Die,
    show: Boolean,
    enabled: Boolean,
    rolling: Boolean,
    scrambleTick: Int,
    scatter: ScatterOffset,
    seed: Int,
    diceStyle: DiceStyle,
    onClick: () -> Unit,
    // Null (not just a no-op lambda) so a plain tap-to-toggle is the only thing wired up when
    // superuser mode isn't active (or this die isn't held), rather than every column silently
    // paying for the press-tracking below whether or not it can ever do anything.
    onCycleValue: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // rememberUpdatedState, not the raw parameters: dice (including this one's own value, while
    // cycling) recomposes DiceTray every tick, which hands DiceColumn a BRAND NEW onClick/
    // onCycleValue lambda instance each time. The gesture handler below is keyed only on
    // `enabled` (see the pointerInput call) so a value-only recomposition never restarts a
    // press already in progress - reading the callbacks through this keeps them current anyway.
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnCycleValue by rememberUpdatedState(onCycleValue)

    Column(
        modifier = modifier
            .then(
                if (enabled) {
                    // No indication/ripple here on purpose: at the size of a whole column it
                    // painted as an obvious translucent rectangle over the entire clickable area,
                    // not a per-die press effect.
                    //
                    // Hand-rolled instead of Modifier.clickable: a plain clickable's gesture
                    // recognizer treats enough drag as a cancel, which handed off to this
                    // screen's enclosing verticalScroll on the slightest finger movement - even
                    // movement that stayed well inside this same column - cancelling the press
                    // (and the superuser cycling with it). Consuming every pointer change for as
                    // long as any pointer here stays down denies the scroll container that drag
                    // delta, so it never has grounds to steal the gesture.
                    Modifier.pointerInput(enabled) {
                        // coroutineScope for a real CoroutineScope to launch the concurrent
                        // cycle-ticking coroutine on (PointerInputScope itself isn't one). Two
                        // separate awaitPointerEventScope calls within it, not one: that scope is
                        // `@RestrictsSuspension` and can't itself launch/cancel a coroutine, so the
                        // down is detected in one restricted block, the launch/cancel bookkeeping
                        // happens back in the plain coroutineScope in between, then a second
                        // restricted block tracks movement/up.
                        coroutineScope {
                            while (true) {
                                awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false).consume() }

                                var cycled = false
                                val cycleJob = currentOnCycleValue?.let { cycle ->
                                    launch {
                                        while (isActive) {
                                            delay(CYCLE_INTERVAL_MILLIS)
                                            cycled = true
                                            cycle()
                                        }
                                    }
                                }

                                awaitPointerEventScope {
                                    do {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consume() }
                                    } while (event.changes.any { it.pressed })
                                }
                                cycleJob?.cancel()

                                // Only a press that lasted long enough to actually change the
                                // die's face suppresses the tap - a quick tap (released before the
                                // first 1s cycle tick) still toggles hold as normal, and releasing
                                // right after cycling doesn't ALSO immediately toggle the value
                                // just picked.
                                if (!cycled) currentOnClick()
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .sizeIn(maxWidth = MAX_SLOT_DIE_SIZE, maxHeight = MAX_SLOT_DIE_SIZE)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(SlotSocketTop, SlotSocketBottom)))
                .border(1.dp, SlotSocketBorder, shape),
        ) {
            if (show && die.isHeld) {
                diceStyle.Die(value = die.value, held = true, modifier = Modifier.fillMaxSize().padding(3.dp))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(modifier = Modifier.fillMaxWidth().height(SCATTER_AREA_HEIGHT)) {
            if (show && !die.isHeld) {
                // Reads scrambleTick so each tick's recomposition seeds a fresh face -
                // deliberately not remember()'d, since a cached value wouldn't flicker.
                val displayValue = if (rolling) Random(scrambleTick * 31 + seed).nextInt(1, 7) else die.value
                diceStyle.Die(
                    value = displayValue,
                    held = false,
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
