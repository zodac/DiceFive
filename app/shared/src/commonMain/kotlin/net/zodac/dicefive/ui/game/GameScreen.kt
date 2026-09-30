package net.zodac.dicefive.ui.game

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.isLuckOfTheIrish
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.common.BackHandler
import net.zodac.dicefive.ui.common.BrandBackdrop
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.game.style.LocalOnRabbitSeen
import net.zodac.dicefive.ui.game.style.LocalSimpleDiceRoll
import net.zodac.dicefive.ui.game.style.LocalIrishTricolour
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** How long the cup shakes before the roll result is revealed - purely a presentation delay. Shared
 * by every roll: a tap (or Quickfire's automatic one) here, and an AI's in [GameViewModel]. */
internal const val CUP_SHAKE_MILLIS = 420L

/** How long before the roll lands the cup stops shaking and starts to tip the dice out - a few frames,
 * so the tip is already under way before the heavy frame the dice land on, rather than starting on it. */
internal const val CUP_POUR_LEAD_MILLIS = 64L

/** Below this many seconds left, the badge flashes between red and its normal muted color instead
 * of sitting static. */
private const val TURN_TIMER_FLASH_SECONDS = 5

/** One full red-to-muted-to-red cycle of the flash, in milliseconds. */
private const val TURN_TIMER_FLASH_PERIOD_MILLIS = 300

private val GAME_PADDING = 16.dp

/** Where the back arrow's 48dp target starts from the screen edge - the same as ScreenScaffold's app bar. */
private val BACK_ARROW_INSET = 4.dp

/** Where the back arrow's 48dp target starts below the status bar - the same as ScreenScaffold's app bar. */
private val BACK_ARROW_TOP = 8.dp

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
    onBackToMenu: () -> Unit = {},
) {
    val state by viewModel.game.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val confirmBeforeLeaving by viewModel.confirmBeforeLeavingGame.collectAsStateWithLifecycle()
    val superuserModeActive by viewModel.superuserModeActive.collectAsStateWithLifecycle()
    val aiRolling by viewModel.aiRolling.collectAsStateWithLifecycle()
    val turnSecondsRemaining by viewModel.turnSecondsRemaining.collectAsStateWithLifecycle()
    val table by viewModel.tableSettings.collectAsStateWithLifecycle()
    val currentState = state ?: return
    val leaveConfirmation = LocalLeaveGameConfirmation.current
    // Whether Game Over's "Review Scorecards" button has been tapped - reset the moment the game
    // stops being over (Play Again starts a fresh one), so a stale review doesn't reappear the
    // next time this game finishes.
    var reviewingScorecards by remember { mutableStateOf(false) }
    LaunchedEffect(currentState.isGameOver) {
        if (!currentState.isGameOver) reviewingScorecards = false
    }

    // The game's clocks (turn timer, CPU turns) run only while this screen is in front and resumed. Driven from
    // lifecycle callbacks, not composition: a backgrounded app draws no frames, so nothing would recompose
    // to say it had gone. Leaving composition (another screen opening over the game) pauses it too.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(viewModel, lifecycleOwner) {
        viewModel.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setForeground(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setForeground(false)
        }
    }

    // The leave-game confirmation freezes the turn timer and CPU turns while it is up, however it was asked for.
    val leaveConfirmationShowing = leaveConfirmation.isShowing
    DisposableEffect(viewModel, leaveConfirmationShowing) {
        viewModel.setHeld(leaveConfirmationShowing)
        onDispose { viewModel.setHeld(false) }
    }

    val platform = LocalPlatformServices.current
    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message -> platform.showTransientMessage(message) }
    }

    // Redirect system back to Menu (default nav behavior would land on the setup form instead).
    // While reviewing scorecards, back returns to the results instead - ScorecardReviewScreen
    // installs its own BackHandler for that, which composes later and so wins over this one.
    val leaveGame = {
        if (!currentState.isGameOver && confirmBeforeLeaving) {
            leaveConfirmation.request(onBackToMenu)
        } else {
            onBackToMenu()
        }
    }
    BackHandler(onBack = leaveGame)

    // A single injection point for the pluggable dice/cup/background art and the table's settings -
    // see GameViewModel.tableSettings. Nothing's drawn until they've loaded, rather than the defaults.
    val tableSettings = table ?: return
    val soundEnabled = tableSettings.soundEnabled
    val vibrationEnabled = tableSettings.vibrationEnabled
    val reduceMotion = LocalReduceMotion.current
    // One length for a human's tap and a CPU's roll, kept in step with the shake sound and buzz - see cupShakeMillis.
    val cupShakeMillis = cupShakeMillis(reduceMotion, soundEnabled, vibrationEnabled)
    SideEffect { viewModel.cupShakeMillis = cupShakeMillis }
    val simpleDiceRoll = tableSettings.simpleDiceRoll || reduceMotion
    SideEffect { viewModel.diceTossMillis = if (simpleDiceRoll) 0L else DICE_TOSS_MILLIS.toLong() }
    CompositionLocalProvider(
        LocalGameVisualTheme provides tableSettings.visualTheme,
        LocalIrishTricolour provides currentState.isLuckOfTheIrish,
        LocalOnRabbitSeen provides viewModel::onRabbitSeen,
        // Reduced motion means the simple roll: the dice appear at once and scoring doesn't wait for a toss.
        LocalSimpleDiceRoll provides (tableSettings.simpleDiceRoll || reduceMotion),
    ) {
        // Once the game is over the board isn't what anyone is looking at, so the results get the
        // whole screen as their own themed page rather than being appended under the felt.
        if (currentState.isGameOver) {
            if (reviewingScorecards) {
                ScorecardReviewScreen(
                    state = currentState,
                    onBack = { reviewingScorecards = false },
                    modifier = modifier,
                )
            } else {
                GameOverScreen(
                    state = currentState,
                    onBackToMenu = onBackToMenu,
                    onPlayAgain = viewModel::startGame,
                    onReviewScorecards = { reviewingScorecards = true },
                    modifier = modifier,
                    soundEnabled = soundEnabled,
                )
            }
            return@CompositionLocalProvider
        }

        // The same colours as the menu and every page off it, but with no dice: the table's own art sits on top.
        BrandBackdrop(modifier = modifier, showDice = false) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Without this, the player header row rendered flush against the very top of the
                    // screen and sat under the status bar's clock/icons on some devices - a fixed
                    // padding amount can't account for how tall that area actually is per device, so
                    // ask the system for its real inset instead.
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .verticalScroll(rememberScrollState())
                    .padding(GAME_PADDING),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                InProgressGame(
                    state = currentState,
                    canUndo = canUndo,
                    superuserModeActive = superuserModeActive,
                    aiRolling = aiRolling,
                    turnSecondsRemaining = turnSecondsRemaining,
                    onBack = leaveGame,
                    onUndo = viewModel::undo,
                    onRoll = viewModel::rollDice,
                    onToggleHold = viewModel::toggleHold,
                    onCycleValue = viewModel::cycleHeldDieValue,
                    onScoreCategory = viewModel::commitScore,
                    onTapCupWithNoRollsLeft = viewModel::tapCupWithNoRollsLeft,
                    onShakeRollDetected = viewModel::onShakeRollDetected,
                    soundEnabled = soundEnabled,
                    vibrationEnabled = vibrationEnabled,
                    cupShakeMillis = cupShakeMillis,
                )
            }
        }
    }
        }
@Composable
private fun InProgressGame(
    state: GameState,
    canUndo: Boolean,
    superuserModeActive: Boolean,
    aiRolling: Boolean,
    turnSecondsRemaining: Int?,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRoll: () -> Unit,
    onToggleHold: (Int) -> Unit,
    onCycleValue: (Int) -> Unit,
    onScoreCategory: (ScoreCategory) -> Unit,
    onTapCupWithNoRollsLeft: () -> Unit,
    onShakeRollDetected: () -> Unit,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    cupShakeMillis: Long,
) {
    val currentPlayer = state.currentPlayer
    val isHumanTurn = currentPlayer?.type == PlayerType.HUMAN
    val canRoll = isHumanTurn && state.rollsRemaining > 0
    // No rollsRemaining condition here (see GameEngine.toggleHold): holding still has no effect on
    // a roll that won't happen after the last one, but disabling the dice entirely once it hits 0
    // looked like the tray had broken, and it blocked superuser cycling right when it's most
    // likely to be used - right after seeing the final roll.
    val canHold = isHumanTurn && state.phase == TurnPhase.ROLLED

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var isTapRolling by remember { mutableStateOf(false) }
    // The cup/tray don't care whether the shake was kicked off by a human tap or the ViewModel's
    // own AI-turn loop (GameViewModel.aiRolling) - either way it's the same "rolling" pose.
    val isRolling = isTapRolling || aiRolling
    // With the simple roll, scattered dice (and their scramble) appear the instant the cup is
    // tapped. With the full roll, a turn's first dice are still in the cup until they're thrown -
    // there are no dice on the mat to gather up yet - so they appear as the roll lands.
    val simpleDiceRoll = LocalSimpleDiceRoll.current
    val showDice = state.phase == TurnPhase.ROLLED || (isRolling && simpleDiceRoll)

    // The dice are still tumbling to a stop for a moment after the roll lands (see DiceTray), and
    // scoring waits for them: nothing lights up or takes a tap until they've settled. Tracked from
    // the very composition the roll lands in - not a frame later - so the highlights can't flash on
    // before being taken away again.
    val rollTracker = remember { RollTracker(isRolling) }
    rollTracker.update(isRolling)
    val settled = remember(rollTracker.landings) { mutableStateOf(rollTracker.landings == 0 || simpleDiceRoll) }
    LaunchedEffect(rollTracker.landings) {
        if (!settled.value) {
            lifecycle.delayWhileResumed(DICE_TOSS_MILLIS.toLong())
            settled.value = true
        }
    }
    val diceSettling = !settled.value

    // The last CUP_POUR_LEAD_MILLIS of a shake, a human's or a CPU's: the cup starts pouring then, a
    // few frames ahead of the dice landing (see CupPanelState.pouring). Only while still rolling, so
    // it can never outlast the shake it belongs to.
    var pourStarted by remember { mutableStateOf(false) }
    LaunchedEffect(isRolling) {
        pourStarted = false
        if (isRolling) {
            lifecycle.delayWhileResumed((cupShakeMillis - CUP_POUR_LEAD_MILLIS).coerceAtLeast(0L))
            pourStarted = true
        }
    }
    val pouring = isRolling && pourStarted

    // The shake sound starts the instant isRolling goes true (human tap or an AI turn kicking
    // off), and the landing sound plays the instant it goes false again, whichever side started
    // it - mirroring the cup/tray's own rolling pose above. previousRolling starts false in step
    // with isRolling, so mounting this screen mid-turn (already settled) doesn't fire a landing
    // sound with no shake before it.
    val soundEffects = rememberSoundEffects()
    soundEffects.enabled = soundEnabled
    val haptics = rememberDiceHaptics()
    haptics.enabled = vibrationEnabled
    var previousRolling by remember { mutableStateOf(false) }
    LaunchedEffect(isRolling) {
        if (isRolling && !previousRolling) {
            soundEffects.playShake()
            haptics.playShakeBuzz()
        } else if (!isRolling && previousRolling) {
            soundEffects.playRoll()
        }
        previousRolling = isRolling
    }

    // Fired from the die's own held state *before* the toggle is applied, not the toggle's
    // result, since onToggleHold only forwards the tapped index - it doesn't report which way
    // the hold flipped.
    val onToggleHoldWithSound = { dieIndex: Int ->
        if (state.dice.getOrNull(dieIndex)?.isHeld == true) {
            soundEffects.playUnhold()
        } else {
            soundEffects.playHold()
        }
        haptics.playHoldTick()
        onToggleHold(dieIndex)
    }

    // Which other player's scorecard the active player has tapped into viewing, if any - keyed on
    // currentPlayerIndex so it's forgotten automatically the moment the turn moves on, rather than
    // leaving a stale view pinned once it's someone else's turn to look at. Only offered on a human
    // seat's own turn - an AI's turn plays out fully automatically, so switching away from it would
    // just hide it running rather than let anyone actually look at another scorecard mid-decision.
    var viewedPlayerIndex by remember(state.currentPlayerIndex) { mutableStateOf<Int?>(null) }
    val viewedPlayer = viewedPlayerIndex?.let { state.players.getOrNull(it) }
    val onPlayerTap = { index: Int ->
        if (isHumanTurn) {
            viewedPlayerIndex = if (index == state.currentPlayerIndex || index == viewedPlayerIndex) null else index
        }
    }

    val coroutineScope = rememberCoroutineScope()
    // Two independent ifs, not if/else - "No More Rolls" is the mutually-exclusive fallback case
    // (rollsRemaining hit 0, the tap does nothing for the game itself), and keeping them separate
    // avoids Kotlin inferring this lambda's type from the join of a Job (the launch) and Unit.
    val onCupTap = {
        if (canRoll && !isRolling) {
            coroutineScope.launch {
                isTapRolling = true
                lifecycle.delayWhileResumed(cupShakeMillis)
                onRoll()
                isTapRolling = false
            }
        }
        if (!canRoll && isHumanTurn && state.rollsRemaining == 0 && !isRolling) {
            onTapCupWithNoRollsLeft()
        }
    }

    // Quickfire taps the cup for the player as their turn starts - the very same tap, so the shake,
    // sound, haptics and roll are exactly what a real one gives. Keyed on awaitsAutoRoll, which
    // goes false once the roll lands and true again on the next human turn.
    LaunchedEffect(state.awaitsAutoRoll) {
        if (state.awaitsAutoRoll) onCupTap()
    }

    // Shaking the phone is just another way to "tap" the cup - same gating, same animation/sound,
    // same no-rolls-left fallback - it only additionally reports the achievement, and only for a
    // shake that actually triggers a roll, not one that lands on the no-op fallback. Not offered at
    // all while viewing another player's scorecard, matching the cup itself being untappable then.
    val onShakeDetected = {
        if (viewedPlayer == null) {
            if (canRoll && !isRolling) onShakeRollDetected()
            onCupTap()
        }
    }
    // Only listening while a shake could do anything: this player's own turn, on their own scorecard.
    ShakeDetectorEffect(enabled = isHumanTurn && viewedPlayer == null, onShake = onShakeDetected)

    // The back arrow shares the tabs' row, so the row is no taller and nothing below it moves. It is pulled
    // out over the screen's 16dp padding so the arrow sits where ScreenScaffold's does (4dp from the edge).
    Row(
        modifier = Modifier.layout { measurable, constraints ->
            val extra = (GAME_PADDING - BACK_ARROW_INSET).roundToPx()
            val placeable = measurable.measure(constraints.offset(horizontal = extra))
            layout(placeable.width - extra, placeable.height) { placeable.place(-extra, 0) }
        },
        verticalAlignment = Alignment.Top,
    ) {
        // Lifted so its centre is ScreenScaffold's 32dp below the status bar: the screen's top padding
        // puts this row at 16dp, and a bar's 48dp button sits 8dp down in it. Drawn only - the row's height is unchanged.
        IconButton(onClick = onBack, modifier = Modifier.offset(y = BACK_ARROW_TOP - GAME_PADDING)) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        PlayerHeaderBar(
            players = state.players,
            currentPlayerIndex = state.currentPlayerIndex,
            viewedPlayerIndex = viewedPlayerIndex,
            enabled = isHumanTurn,
            onPlayerTap = onPlayerTap,
            modifier = Modifier.weight(1f),
        )
    }

    // Only shown while a timer is actually running for this turn - see GameViewModel.syncTurnTimer.
    if (turnSecondsRemaining != null) {
        TurnTimerBadge(secondsRemaining = turnSecondsRemaining, modifier = Modifier.fillMaxWidth())
    }

    if (viewedPlayer != null) {
        ReadOnlyScoreboard(player = viewedPlayer)
    } else {
        GameBoard(
            state = state,
            rolling = isRolling,
            pouring = pouring,
            diceSettling = diceSettling,
            canUndo = canUndo,
            onScoreCategory = onScoreCategory,
            onCupTap = onCupTap,
            onUndo = onUndo,
        )
    }

    // The one DiceTray call for both branches above - a live turn's own dice, or a viewed player's
    // last roll - so the gap above it (this Column's own Arrangement.spacedBy, in GameScreen) is
    // identical either way, not a second hand-picked layout that only one branch remembers to
    // apply. Skipped entirely for a viewed player who hasn't finished a turn yet - there's no roll
    // of theirs to show, not even an empty mat.
    val viewedLastRoll = viewedPlayer?.lastRoll
    if (viewedPlayer == null || viewedLastRoll != null) {
        DiceTray(
            dice = viewedLastRoll ?: state.dice,
            gameMode = viewedPlayer?.gameMode ?: state.gameMode,
            // No mat interactivity for a viewed player - it's not their turn playing out, just their
            // last one on display.
            enabled = viewedPlayer == null && canHold,
            showDice = viewedPlayer != null || showDice,
            rolling = viewedPlayer == null && isRolling,
            onToggleHold = if (viewedPlayer == null) onToggleHoldWithSound else NO_OP_TOGGLE_HOLD,
            superuserModeActive = viewedPlayer == null && superuserModeActive,
            onCycleValue = if (viewedPlayer == null) onCycleValue else NO_OP_TOGGLE_HOLD,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val NO_OP_TOGGLE_HOLD: (Int) -> Unit = {}

/** The current player's remaining turn time - counts down to zero, at which point the turn is
 * forfeited and auto-scored (see [GameViewModel.autoScoreOnTimeout]). Sits at its normal muted
 * color until the last [TURN_TIMER_FLASH_SECONDS] seconds, when it flashes red against that same
 * muted color instead of sitting static, so the final countdown is hard to miss even out of the
 * corner of an eye. */
@Composable
fun TurnTimerBadge(secondsRemaining: Int, modifier: Modifier = Modifier) {
    val flashing = secondsRemaining in 1..TURN_TIMER_FLASH_SECONDS
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    // The flash's clock only runs in those last seconds - left running all turn, it recomposed the
    // badge every frame for the whole of every timed game.
    // Steady red under reduced motion: still says "running out" (and the live region says it in words).
    val color = when {
        !flashing -> mutedColor
        LocalReduceMotion.current -> MaterialTheme.colorScheme.error
        else -> rememberFlashColor(mutedColor)
    }
    Text(
        text = "Time left: ${secondsRemaining}s",
        // The flash is colour alone, so its spoken twin: once the flash starts, the badge is a polite live
        // region whose text is the same for every second of it - TalkBack announces the warning once,
        // not a count every second. Before that the seconds change silently, and read in words ("12s"
        // would be spoken as letters).
        modifier = modifier.clearAndSetSemantics {
            if (flashing) {
                contentDescription = "Time running out, $TURN_TIMER_FLASH_SECONDS seconds or less left"
                liveRegion = LiveRegionMode.Polite
            } else {
                contentDescription = "Time left: $secondsRemaining ${if (secondsRemaining == 1) "second" else "seconds"}"
            }
        },
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        color = color,
        style = MaterialTheme.typography.labelMedium,
    )
}

/** [TurnTimerBadge]'s flash: the error red and back to [mutedColor], round and round. */
@Composable
private fun rememberFlashColor(mutedColor: Color): Color {
    val flashColor by rememberInfiniteTransition(label = "turnTimerFlash").animateColor(
        initialValue = MaterialTheme.colorScheme.error,
        targetValue = mutedColor,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = TURN_TIMER_FLASH_PERIOD_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "turnTimerFlashColor",
    )
    return flashColor
}

// The lint check exists because a real screen must scope its view model to the host, not build one
// per composition - but a @Preview has no host to scope to, and GameViewModel is deliberately
// constructible with no repositories for exactly this (and for unit tests). Preview-only.
@Suppress("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
private fun GameScreenPreview() {
    val viewModel = GameViewModel().apply { startGame() }
    CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
        DiceFiveTheme {
            GameScreen(viewModel = viewModel)
        }
    }
}
