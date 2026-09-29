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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.game.style.BEVELED_DIE_CORNER_PERCENT
import net.zodac.dicefive.ui.game.style.ColouredDie
import net.zodac.dicefive.ui.game.style.DiceMat
import net.zodac.dicefive.ui.game.style.DiceStyle
import net.zodac.dicefive.ui.game.style.DieMotion
import net.zodac.dicefive.ui.game.style.LocalDieCastsShadow
import net.zodac.dicefive.ui.game.style.LocalDieIndex
import net.zodac.dicefive.ui.game.style.LocalDieMotion
import net.zodac.dicefive.ui.game.style.LocalDieTumbleMillis
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.game.style.LocalIrishTricolour
import net.zodac.dicefive.ui.game.style.LocalSimpleDiceRoll
import net.zodac.dicefive.ui.game.style.PickUpPath
import net.zodac.dicefive.ui.game.style.TossPath
import net.zodac.dicefive.ui.game.style.TossPose
import net.zodac.dicefive.ui.game.style.TossedCube
import net.zodac.dicefive.ui.game.style.palette

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
// Round the tray's contents, inside its rounded edge - which clips anything past it.
private val TRAY_PADDING = 16.dp
private val DICE_COLUMN_GAP = 14.dp

// How much wider than it is square a die tipping over can look, nearest edge looming (TossedCube).
private const val TIPPED_FOOTPRINT = 1.06f

// The one light the dice on the mat cast their shadows from: above and to the left of the tray's
// top-left corner, in dp from that corner of the dice row. A shadow falls away from it by this much
// of the die's distance from it - so dice further from the light cast longer shadows.
private val LIGHT_X = (-48).dp
private val LIGHT_Y = (-140).dp
private const val SHADOW_LENGTH = 0.022f
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
                .padding(TRAY_PADDING)
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
            horizontalArrangement = Arrangement.spacedBy(DICE_COLUMN_GAP),
        ) {
            val irish = LocalIrishTricolour.current
            dice.forEachIndexed { index, die ->
                // The tray's own touch handling is one hand-rolled gesture over the whole row, which a
                // screen reader can't see into - so each die is its own node, named by its face (and
                // colour), saying whether it's held, and offering hold/release as its click action.
                val dieSemantics = if (showDice) {
                    Modifier.semantics {
                        val colour = die.colour?.let { "${it.spokenName(irish)} " }.orEmpty()
                        contentDescription = "Die ${index + 1}, $colour${die.value}"
                        stateDescription = if (die.isHeld) "Held" else "Not held"
                        if (enabled) {
                            onClick(label = if (die.isHeld) "Release" else "Hold") {
                                currentOnToggleHold(index)
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
                    scrambleTick = scrambleTick,
                    scatter = SCATTER_OFFSETS[index % SCATTER_OFFSETS.size],
                    seed = index,
                    gameMode = gameMode,
                    diceStyle = visualTheme.diceStyle,
                    mat = visualTheme.mat,
                    modifier = Modifier.weight(1f).then(dieSemantics),
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
    // For a style whose faces the die's movement throws about (googly eyes): the die's movement,
    // kept for the whole column, so a die keeps its looks as it's held and released. Moved on every
    // frame only while the die or its pupils are moving.
    val motion = diceStyle.pupilTravel?.let { travel -> remember(travel) { DieMotion(seed, travel) } }
    if (motion != null) {
        LaunchedEffect(motion, motion.awake) {
            if (motion.awake) motion.follow()
        }
    }
    // Which physical die this column is, so a natural-looking style can give each its own pattern.
    CompositionLocalProvider(LocalDieIndex provides seed, LocalDieMotion provides motion) {
        DiceColumnContent(die, show, rolling, scrambleTick, scatter, seed, gameMode, diceStyle, mat, modifier)
    }
}

@Composable
private fun DiceColumnContent(
    die: Die,
    show: Boolean,
    rolling: Boolean,
    scrambleTick: Int,
    scatter: ScatterOffset,
    seed: Int,
    gameMode: GameMode,
    diceStyle: DiceStyle,
    mat: DiceMat,
    modifier: Modifier,
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

        ScatterArea(die, show, rolling, scrambleTick, scatter, seed, gameMode, diceStyle)
    }
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
 * keep it in its own column. With the player's "Simple dice roll" on, it just flicks
 * through faces in place while rolling instead, as dice always used to.
 */
@Composable
private fun ScatterArea(
    die: Die,
    show: Boolean,
    rolling: Boolean,
    scrambleTick: Int,
    scatter: ScatterOffset,
    seed: Int,
    gameMode: GameMode,
    diceStyle: DiceStyle,
) {
    val simple = LocalSimpleDiceRoll.current
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

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(SCATTER_AREA_HEIGHT)) {
        // Past the tray's near edge, the whole die clipped off by it: off the mat entirely - a third
        // of a die further than its own height, since a die lying at an angle pokes its corners up
        // past its square outline.
        val startY = (SCATTER_AREA_HEIGHT + TRAY_PADDING + SCATTERED_DIE_SIZE / 3).value
        val restY = scatter.yOffset.value
        val size = SCATTERED_DIE_SIZE.value
        if (rolling && !(show && !die.isHeld)) cupFace[0] = 0
        if (!show || die.isHeld) return@BoxWithConstraints
        val rest = Modifier.align(Alignment.TopCenter).size(SCATTERED_DIE_SIZE)
        val selfTumbling = die.colour == null && diceStyle.tumblesItself

        // A die's rotated footprint stays inside its own column plus half the gap to the next, so
        // neighbours can never overlap, however they're turned - pushed back in from the edge as if
        // off a wall. A little extra for a cube tipping over, whose near edge looms a touch wider.
        val halfSlot = (maxWidth + DICE_COLUMN_GAP) / 2
        fun keptIn(x: Dp, yawDegrees: Float): Dp {
            val radians = yawDegrees * PI.toFloat() / 180f
            val half = SCATTERED_DIE_SIZE / 2 * (abs(cos(radians)) + abs(sin(radians))) * TIPPED_FOOTPRINT
            val room = (halfSlot - half).coerceAtLeast(0.dp)
            return x.coerceIn(-room, room)
        }

        // Where the die sits in the whole dice row, for its shadow: this column's place in the row
        // plus where the die is within it.
        val columnLeft = (maxWidth + DICE_COLUMN_GAP) * seed

        // Where the die is, for a style whose faces its movement throws about (DieMotion) - not a die
        // in its own colour, which isn't drawn in the style at all.
        val motion = LocalDieMotion.current?.takeIf { die.colour == null }

        /**
         * Tells [motion] the die is at [x]/[y] in its column (measured in die sizes), turned
         * [yawDegrees], and [roll] quarter-turns into a tumble.
         */
        @Composable
        fun Track(x: Dp, y: Dp, yawDegrees: Float, roll: Float = 0f) {
            if (motion == null) return
            val centre = Offset((columnLeft + maxWidth / 2 + x) / SCATTERED_DIE_SIZE, (y + SCATTERED_DIE_SIZE / 2) / SCATTERED_DIE_SIZE)
            // The face mostly in view: tipping away over its top edge for the first half of each
            // quarter-turn, then the next one tipping up to take its place.
            val tipped = roll - floor(roll)
            val tip = if (tipped < 0.5f) tipped * 90f else (tipped - 1f) * 90f
            SideEffect { motion.moveTo(centre, yawDegrees, tip) }
        }

        /**
         * The die's ground shadow, cast from the one light, [lift] (0..1) off the mat, in the outline
         * of the die itself - its style's, or a bevelled square for a die in a colour of its own.
         * [tumbleMillis] is how long a die that turns itself has been tumbling mid-toss, for a shadow
         * that turns with it.
         */
        @Composable
        fun Shadow(x: Dp, y: Dp, yawDegrees: Float, lift: Float, tumbleMillis: Float? = null) {
            val shape = if (die.colour != null) {
                RoundedCornerShape(BEVELED_DIE_CORNER_PERCENT)
            } else {
                diceStyle.shadowShape(die.value, seed, tumbleMillis)
            }
            val centreX = columnLeft + maxWidth / 2 + x
            val centreY = y + SCATTERED_DIE_SIZE / 2
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
            val yaw = scatter.rotationDegrees + pose.yawDegrees
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
                    DieFace(die = die, held = false, diceStyle = diceStyle, modifier = placed)
                }
            } else if (die.colour == null) {
                diceStyle.TossedDie(roll = pose.roll, finalTurns = finalTurns, ring = ring, modifier = placed)
            } else {
                val palette = die.colour.palette(LocalIrishTricolour.current)
                TossedCube(
                    roll = pose.roll,
                    finalTurns = finalTurns,
                    ring = ring,
                    body = lerp(palette.diceTop, palette.diceBottom, 0.5f),
                    modifier = placed,
                ) { value, faceModifier ->
                    DieFace(die = die.copy(value = value), held = false, diceStyle = diceStyle, modifier = faceModifier)
                }
            }
        }

        // Every die on the mat takes its shadow from the one light (Shadow, above), not its own.
        CompositionLocalProvider(LocalDieCastsShadow provides false) {
            when {
                // Swept off the mat while the cup shakes, still showing the last roll.
                rolling && !simple -> {
                    val path = remember(tracker.starts, die.value) { PickUpPath(die.value, startY, restY) }
                    cupFace[0] = path.value
                    Moving(path.pose(pickUp.value), listOf(die.value), finalTurns = 0, tossMillis = null)
                }

                toss.value < 1f && !simple -> {
                    val sideRoom = ((maxWidth - SCATTERED_DIE_SIZE) / 2 - abs(scatter.xOffset.value).dp).value.coerceAtLeast(0f)
                    val path = remember(tracker.landings) {
                        TossPath(
                            seed = tracker.landings * 7 + seed,
                            result = die.value,
                            startFace = cupFace[0].takeIf { it != 0 },
                            restTop = if (die.colour == null) diceStyle.topFace(die.value) else null,
                            startY = startY,
                            restY = restY,
                            dieSize = size,
                            sideRoom = sideRoom,
                        )
                    }
                    Moving(path.pose(toss.value), path.ring, path.finalTurns, tossMillis = toss.value * DICE_TOSS_MILLIS)
                }

                else -> {
                    // At rest - or, with the simple roll, flicking through faces in place while rolling.
                    // Reads scrambleTick so each tick's recomposition seeds a fresh face - deliberately
                    // not remember()'d, since a cached value wouldn't flicker. A coloured die tumbles
                    // through colours as well as numbers.
                    val displayDie = if (rolling) scrambledFace(Random(scrambleTick * 31 + seed), gameMode) else die
                    val restX = keptIn(scatter.xOffset, scatter.rotationDegrees)
                    Shadow(restX, scatter.yOffset, scatter.rotationDegrees, lift = 0f)
                    Track(restX, scatter.yOffset, scatter.rotationDegrees)
                    // Landed: the pupils stop with the dice, rather than sloshing on once scoring is open.
                    if (motion != null && !rolling) SideEffect { motion.settle() }
                    DieFace(
                        die = displayDie,
                        held = false,
                        diceStyle = diceStyle,
                        modifier = rest
                            .offset(x = restX, y = scatter.yOffset)
                            .graphicsLayer { rotationZ = scatter.rotationDegrees },
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
