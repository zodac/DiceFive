package net.zodac.dicefive.ui.game

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.game.style.GameVisualTheme
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** How long the cup shakes before the roll result is revealed - purely a presentation delay. */
private const val CUP_SHAKE_MILLIS = 420L

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
    val currentState = state ?: return
    var showLeaveConfirmation by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Redirect system back to Menu (default nav behavior would land on the setup form instead).
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

    // A single injection point for the pluggable dice/cup/background art - swap this value for a
    // user-selected GameVisualTheme once that setting exists.
    CompositionLocalProvider(LocalGameVisualTheme provides GameVisualTheme()) {
        // Once the game is over the board isn't what anyone is looking at, so the results get the
        // whole screen as their own themed page rather than being appended under the felt.
        if (currentState.isGameOver) {
            GameOverScreen(state = currentState, onBackToMenu = onBackToMenu, modifier = modifier)
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
                onUndo = viewModel::undo,
                onRoll = viewModel::rollDice,
                onToggleHold = viewModel::toggleHold,
                onCycleValue = viewModel::cycleHeldDieValue,
                onScoreCategory = viewModel::commitScore,
            )
        }
    }
}

@Composable
private fun InProgressGame(
    state: GameState,
    canUndo: Boolean,
    superuserModeActive: Boolean,
    onUndo: () -> Unit,
    onRoll: () -> Unit,
    onToggleHold: (Int) -> Unit,
    onCycleValue: (Int) -> Unit,
    onScoreCategory: (ScoreCategory) -> Unit,
) {
    val currentPlayer = state.currentPlayer
    val isHumanTurn = currentPlayer?.type == PlayerType.HUMAN
    val canRoll = isHumanTurn && state.rollsRemaining > 0
    // No rollsRemaining condition here (see GameEngine.toggleHold): holding still has no effect on
    // a roll that won't happen after the last one, but disabling the dice entirely once it hits 0
    // looked like the tray had broken, and it blocked superuser cycling right when it's most
    // likely to be used - right after seeing the final roll.
    val canHold = isHumanTurn && state.phase == TurnPhase.ROLLED

    var isRolling by remember { mutableStateOf(false) }
    // Scattered dice (and their scramble animation) should appear the instant the cup is tapped,
    // not only once the real roll has resolved a few hundred ms later.
    val showDice = state.phase == TurnPhase.ROLLED || isRolling

    val coroutineScope = rememberCoroutineScope()
    val onCupTap = {
        if (canRoll && !isRolling) {
            coroutineScope.launch {
                isRolling = true
                delay(CUP_SHAKE_MILLIS)
                onRoll()
                isRolling = false
            }
        }
    }

    PlayerHeaderBar(players = state.players, currentPlayerIndex = state.currentPlayerIndex)

    GameBoard(
        state = state,
        rolling = isRolling,
        canRoll = canRoll && !isRolling,
        canUndo = canUndo,
        onScoreCategory = onScoreCategory,
        onCupTap = onCupTap,
        onUndo = onUndo,
    )

    DiceTray(
        dice = state.dice,
        enabled = canHold,
        showDice = showDice,
        rolling = isRolling,
        onToggleHold = onToggleHold,
        superuserModeActive = superuserModeActive,
        onCycleValue = onCycleValue,
        modifier = Modifier.fillMaxWidth(),
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
