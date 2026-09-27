package net.zodac.dicefive.ui.game

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.isLuckOfTheIrish
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.GameVisualTheme
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.game.style.LocalIrishTricolour
import net.zodac.dicefive.ui.game.style.TableBackgrounds
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** How long the cup shakes before the roll result is revealed - purely a presentation delay. */
private const val CUP_SHAKE_MILLIS = 420L

/** Below this many seconds left, the badge flashes between red and its normal muted color instead
 * of sitting static. */
private const val TURN_TIMER_FLASH_SECONDS = 5

/** One full red-to-muted-to-red cycle of the flash, in milliseconds. */
private const val TURN_TIMER_FLASH_PERIOD_MILLIS = 300

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
    onBackToMenu: () -> Unit = {},
) {
    val state by viewModel.game.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val confirmBeforeLeaving by viewModel.confirmBeforeLeavingGame.collectAsState()
    val superuserModeActive by viewModel.superuserModeActive.collectAsState()
    val aiRolling by viewModel.aiRolling.collectAsState()
    val turnSecondsRemaining by viewModel.turnSecondsRemaining.collectAsState()
    val currentState = state ?: return
    var showLeaveConfirmation by remember { mutableStateOf(false) }
    // Whether Game Over's "Review Scorecards" button has been tapped - reset the moment the game
    // stops being over (Play Again starts a fresh one), so a stale review doesn't reappear the
    // next time this game finishes.
    var reviewingScorecards by remember { mutableStateOf(false) }
    LaunchedEffect(currentState.isGameOver) {
        if (!currentState.isGameOver) reviewingScorecards = false
    }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Redirect system back to Menu (default nav behavior would land on the setup form instead).
    // While reviewing scorecards, back returns to the results instead - ScorecardReviewScreen
    // installs its own BackHandler for that, which composes later and so wins over this one.
    BackHandler {
        if (!currentState.isGameOver && confirmBeforeLeaving) {
            showLeaveConfirmation = true
        } else {
            onBackToMenu()
        }
    }

    if (showLeaveConfirmation) {
        DiceFiveDialog(
            icon = Icons.AutoMirrored.Filled.Logout,
            title = "Leave game?",
            message = "Your progress is saved - you can continue this game later from Play.",
            confirmLabel = "Leave",
            onConfirm = { showLeaveConfirmation = false; onBackToMenu() },
            dismissLabel = "Cancel",
            onDismiss = { showLeaveConfirmation = false },
            onDismissRequest = { showLeaveConfirmation = false },
        )
    }

    // A single injection point for the pluggable dice/cup/background art, built from whatever the
    // Styles screen last persisted (each id resolved through its own catalog's byId, which falls
    // back to that category's default for an id nothing recognizes).
    val settingsRepository = remember { SettingsRepository(context) }
    val diceStyleId by settingsRepository.diceStyleId.collectAsState(initial = DiceStyles.default.id)
    val diceCupStyleId by settingsRepository.diceCupStyleId.collectAsState(initial = DiceCupStyles.default.id)
    val tableBackgroundId by settingsRepository.tableBackgroundId.collectAsState(initial = TableBackgrounds.default.id)
    val diceMatId by settingsRepository.diceMatId.collectAsState(initial = DiceMats.default.id)
    val soundEnabled by settingsRepository.soundEnabled.collectAsState(initial = true)
    val vibrationEnabled by settingsRepository.vibrationEnabled.collectAsState(initial = true)
    val visualTheme = remember(diceStyleId, diceCupStyleId, tableBackgroundId, diceMatId) {
        GameVisualTheme(
            diceStyle = DiceStyles.byId(diceStyleId),
            diceCupStyle = DiceCupStyles.byId(diceCupStyleId),
            background = TableBackgrounds.byId(tableBackgroundId),
            mat = DiceMats.byId(diceMatId),
        )
    }
    CompositionLocalProvider(LocalGameVisualTheme provides visualTheme, LocalIrishTricolour provides currentState.isLuckOfTheIrish) {
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

        Column(
            modifier = modifier
                .fillMaxSize()
                // Without this, the player header row rendered flush against the very top of the
                // screen and sat under the status bar's clock/icons on some devices - a fixed
                // padding amount can't account for how tall that area actually is per device, so
                // ask the system for its real inset instead.
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InProgressGame(
                state = currentState,
                canUndo = canUndo,
                superuserModeActive = superuserModeActive,
                aiRolling = aiRolling,
                turnSecondsRemaining = turnSecondsRemaining,
                onUndo = viewModel::undo,
                onRoll = viewModel::rollDice,
                onToggleHold = viewModel::toggleHold,
                onCycleValue = viewModel::cycleHeldDieValue,
                onScoreCategory = viewModel::commitScore,
                onTapCupWithNoRollsLeft = viewModel::tapCupWithNoRollsLeft,
                onShakeRollDetected = viewModel::onShakeRollDetected,
                soundEnabled = soundEnabled,
                vibrationEnabled = vibrationEnabled,
            )
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
    onUndo: () -> Unit,
    onRoll: () -> Unit,
    onToggleHold: (Int) -> Unit,
    onCycleValue: (Int) -> Unit,
    onScoreCategory: (ScoreCategory) -> Unit,
    onTapCupWithNoRollsLeft: () -> Unit,
    onShakeRollDetected: () -> Unit,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
) {
    val currentPlayer = state.currentPlayer
    val isHumanTurn = currentPlayer?.type == PlayerType.HUMAN
    val canRoll = isHumanTurn && state.rollsRemaining > 0
    // No rollsRemaining condition here (see GameEngine.toggleHold): holding still has no effect on
    // a roll that won't happen after the last one, but disabling the dice entirely once it hits 0
    // looked like the tray had broken, and it blocked superuser cycling right when it's most
    // likely to be used - right after seeing the final roll.
    val canHold = isHumanTurn && state.phase == TurnPhase.ROLLED

    var isTapRolling by remember { mutableStateOf(false) }
    // The cup/tray don't care whether the shake was kicked off by a human tap or the ViewModel's
    // own AI-turn loop (GameViewModel.aiRolling) - either way it's the same "rolling" pose.
    val isRolling = isTapRolling || aiRolling
    // Scattered dice (and their scramble animation) should appear the instant the cup is tapped,
    // not only once the real roll has resolved a few hundred ms later.
    val showDice = state.phase == TurnPhase.ROLLED || isRolling

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
                delay(CUP_SHAKE_MILLIS)
                onRoll()
                isTapRolling = false
            }
        }
        if (!canRoll && isHumanTurn && state.rollsRemaining == 0 && !isRolling) {
            onTapCupWithNoRollsLeft()
        }
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
    rememberShakeDetector(onShake = onShakeDetected)

    PlayerHeaderBar(
        players = state.players,
        currentPlayerIndex = state.currentPlayerIndex,
        viewedPlayerIndex = viewedPlayerIndex,
        enabled = isHumanTurn,
        onPlayerTap = onPlayerTap,
    )

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
private fun TurnTimerBadge(secondsRemaining: Int, modifier: Modifier = Modifier) {
    val flashing = secondsRemaining in 1..TURN_TIMER_FLASH_SECONDS
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val infiniteTransition = rememberInfiniteTransition(label = "turnTimerFlash")
    val flashColor by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.error,
        targetValue = mutedColor,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = TURN_TIMER_FLASH_PERIOD_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "turnTimerFlashColor",
    )
    val color = if (flashing) flashColor else mutedColor
    Text(
        text = "Time left: ${secondsRemaining}s",
        modifier = modifier,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        color = color,
        style = MaterialTheme.typography.labelMedium,
    )
}

// The lint check exists because a real screen must scope its view model to the host, not build one
// per composition - but a @Preview has no host to scope to, and GameViewModel is deliberately
// constructible with no Context for exactly this (and for unit tests). Preview-only.
@Suppress("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
private fun GameScreenPreview() {
    val viewModel = GameViewModel().apply { startGame() }
    DiceFiveTheme {
        GameScreen(viewModel = viewModel)
    }
}
