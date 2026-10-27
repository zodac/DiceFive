package net.zodac.dicefive.ui.game

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.game.ProjectedHand
import net.zodac.dicefive.game.TutorialStep
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.isLuckOfTheIrish
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.game_timer_left
import net.zodac.dicefive.resources.game_timer_left_cd
import net.zodac.dicefive.resources.game_timer_running_out_cd
import net.zodac.dicefive.resources.game_tutorial_coach_name
import net.zodac.dicefive.resources.game_tutorial_turn
import net.zodac.dicefive.ui.common.BACK_ARROW_OUTSET
import net.zodac.dicefive.ui.common.BackArrow
import net.zodac.dicefive.ui.common.BackHandler
import net.zodac.dicefive.ui.common.BrandBackdrop
import net.zodac.dicefive.ui.common.HoldPageCover
import net.zodac.dicefive.ui.common.LocalPageCover
import net.zodac.dicefive.ui.common.VerticalScrollbar
import net.zodac.dicefive.ui.common.LocalAnimationLevel
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.OffscreenWarmUp
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.style.ChickenDiceCupStyle
import net.zodac.dicefive.ui.style.GameVisualTheme
import net.zodac.dicefive.ui.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.style.LocalIrishTricolour
import net.zodac.dicefive.ui.style.LocalOnRabbitSeen
import net.zodac.dicefive.ui.style.visualTheme

/** How long the cup shakes before the roll result is revealed - purely a presentation delay. Shared
 * by every roll: a tap here, and an AI's in [GameViewModel]. */
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

/** Where the back arrow's 48dp target starts below the status bar - the same as ScreenScaffold's app bar. */
private val BACK_ARROW_TOP = 8.dp

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
    onBackToMenu: () -> Unit = {},
    // The tutorial's Game Over offers a game of the player's own in place of Play Again.
    onNewGame: () -> Unit = {},
) {
    val state by viewModel.game.collectAsStateWithLifecycle()
    val tutorialStep by viewModel.tutorialStep.collectAsStateWithLifecycle()
    val tutorialTurn by viewModel.tutorialTurn.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val confirmBeforeLeaving by viewModel.confirmBeforeLeavingGame.collectAsStateWithLifecycle()
    val superuserModeActive by viewModel.superuserModeActive.collectAsStateWithLifecycle()
    val aiRolling by viewModel.aiRolling.collectAsStateWithLifecycle()
    val tapRolling by viewModel.tapRolling.collectAsStateWithLifecycle()
    // Kept as a State and read only by the timer itself, so its tick each second recomposes the timer, not the board.
    val turnSecondsRemaining = viewModel.turnSecondsRemaining.collectAsStateWithLifecycle()
    val table by viewModel.tableSettings.collectAsStateWithLifecycle()
    // Until the game (and below, the table's settings) load, just the page the board is drawn on, rather than nothing,
    // which showed whatever was behind for a frame - and the page cover kept up over it meanwhile.
    val currentState = state ?: return BrandBackdrop(modifier = modifier, showDice = false) { HoldPageCover() }
    val leaveConfirmation = LocalLeaveGameConfirmation.current
    // Whether Game Over's "Review Scorecards" button has been tapped - reset the moment the game
    // stops being over (Play Again starts a fresh one), so a stale review doesn't reappear the
    // next time this game finishes.
    var reviewingScorecards by remember { mutableStateOf(false) }
    // Whether this game's celebration (fanfare and fireworks) has already played, so coming back
    // from the scorecard review doesn't replay it. Reset along with the review.
    var celebrated by remember { mutableStateOf(false) }
    LaunchedEffect(currentState.isGameOver) {
        if (!currentState.isGameOver) {
            reviewingScorecards = false
            celebrated = false
        }
    }

    // The game's clocks (turn timer, CPU turns) run only while this screen is in front and resumed. Driven from
    // lifecycle callbacks, not composition: a backgrounded app draws no frames, so nothing would recompose
    // to say it had gone. Leaving composition (another screen opening over the game) pauses it too.
    LifecycleResumeEffect(viewModel) {
        viewModel.setForeground(true)
        onPauseOrDispose { viewModel.setForeground(false) }
    }

    // The leave-game confirmation freezes the turn timer and CPU turns while it is up, however it was asked for, and so
    // does a change of page: the board is in full view before a CPU moves or the timer counts.
    val held = leaveConfirmation.isShowing || LocalPageCover.current?.changing == true
    DisposableEffect(viewModel, held) {
        viewModel.setHeld(held)
        onDispose { viewModel.setHeld(false) }
    }

    val platform = LocalPlatformServices.current
    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message -> platform.showTransientMessage(message) }
    }

    // Redirect system back to Menu (default nav behavior would land on the setup form instead).
    // While reviewing scorecards, back returns to the results instead - ScorecardReviewScreen
    // installs its own BackHandler for that, which composes later and so wins over this one.
    // The tutorial always asks while it's running, whatever the setting: it isn't saved, so leaving loses the place in it.
    val leaveGame = {
        if (!currentState.isGameOver && (confirmBeforeLeaving || viewModel.isTutorial)) {
            leaveConfirmation.request(tutorial = viewModel.isTutorial, onLeave = onBackToMenu)
        } else {
            onBackToMenu()
        }
    }
    BackHandler(onBack = leaveGame)

    // A single injection point for the pluggable dice/cup/background art and the table's settings -
    // see GameViewModel.tableSettings. Nothing's drawn until they've loaded, rather than the defaults.
    val tableSettings = table ?: return BrandBackdrop(modifier = modifier, showDice = false) { HoldPageCover() }
    val soundEnabled = tableSettings.soundEnabled
    val vibrationEnabled = tableSettings.vibrationEnabled
    val reduceMotion = LocalReduceMotion.current
    // One length for a human's tap and a CPU's roll, kept in step with the shake sound and buzz - see cupShakeMillis.
    val cupShakeMillis = cupShakeMillis(reduceMotion, soundEnabled, vibrationEnabled)
    // Under reduced motion, or the Low level's simple roll, the dice snap to their result, so a CPU has no toss to wait for.
    val simpleRoll = LocalAnimationLevel.current.simpleDiceRoll
    SideEffect { viewModel.setPacing(GamePacing(cupShakeMillis = cupShakeMillis, diceAnimated = !reduceMotion && !simpleRoll)) }
    // In the tutorial, what its step points the board's pieces to - and nothing at all in a real game.
    val tutorialPulse = if (viewModel.isTutorial) rememberTutorialPulse() else null
    val tutorialRolling by viewModel.tapRolling.collectAsStateWithLifecycle()
    val tutorialGuide = tutorialPulse?.let { pulse ->
        remember(tutorialStep, tutorialRolling) { TutorialGuide(tutorialStep, pulse, viewModel::tutorialTotalsOpened, tutorialRolling) }
    }
    val coachName = stringResource(Res.string.game_tutorial_coach_name)
    CompositionLocalProvider(
        LocalGameVisualTheme provides tableSettings.visualTheme,
        LocalTutorialGuide provides tutorialGuide,
        LocalIrishTricolour provides currentState.isLuckOfTheIrish,
        LocalOnRabbitSeen provides viewModel::onRabbitSeen,
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
                    onPlayAgain = { if (viewModel.isTutorial) onNewGame() else viewModel.startGame() },
                    tutorialFinished = viewModel.isTutorial,
                    onReviewScorecards = { reviewingScorecards = true },
                    modifier = modifier,
                    celebrate = !celebrated,
                    onCelebrated = { celebrated = true },
                )
            }
            return@CompositionLocalProvider
        }

        // The same colours as the menu and every page off it, but with no dice: the table's own art sits on top.
        BrandBackdrop(modifier = modifier, showDice = false) {
            // The tutorial's coach card stands in for the player tabs, and is taller: the game is sized for the header it has.
            val coachHeight: @Composable (Dp, Boolean) -> Dp? = { width, landscape -> tutorialStep?.let { coachHeaderHeight(viewModel.tutorialSteps, width, landscape) } }
            GamePage(categories = currentState.categories, headerHeight = coachHeight) { layout ->
                InProgressGame(
                    state = currentState,
                    landscape = layout.landscape,
                    cupScale = layout.cupScale,
                    canUndo = canUndo,
                    superuserModeActive = superuserModeActive,
                    aiRolling = aiRolling,
                    tapRolling = tapRolling,
                    turnSecondsRemaining = { turnSecondsRemaining.value },
                    onBack = leaveGame,
                    onUndo = viewModel::undo,
                    onRoll = viewModel::shakeAndRoll,
                    onToggleHold = viewModel::toggleHold,
                    onCycleValue = viewModel::cycleHeldDieValue,
                    onScoreCategory = viewModel::commitScore,
                    claimFiveOfAKindFlash = viewModel::claimFiveOfAKindFlash,
                    onTapCupWithNoRollsLeft = viewModel::tapCupWithNoRollsLeft,
                    onShakeRollDetected = viewModel::onShakeRollDetected,
                    cupShakeMillis = cupShakeMillis,
                    coach = tutorialStep?.let { step ->
                        { cardModifier ->
                            BoxWithConstraints(modifier = cardModifier) {
                                // As tall as its longest step's words, or as the room above the game allows (its words then
                                // scroll inside it), but never so short it shows too little to read.
                                val height = minOf(coachCardHeight(viewModel.tutorialSteps, maxWidth), maxOf(layout.headerRoom, MIN_COACH_HEIGHT))
                                val turn = stringResource(Res.string.game_tutorial_turn, tutorialTurn, viewModel.tutorialTurns)
                                CoachCard(
                                    step = step,
                                    coachName = coachName,
                                    turn = turn,
                                    onBack = { viewModel.jumpToTutorialTurn(-1) }.takeIf { viewModel.canJumpBack },
                                    onForward = { viewModel.jumpToTutorialTurn(1) }.takeIf { viewModel.canJumpForward },
                                    modifier = Modifier.height(height),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * The game's page, as [GameScreen] lays it out on this screen: the board and tray scaled up from [REFERENCE_WIDTH]
 * ([gameLayout]), centred, scrolling if too tall. [content] is the game, given the layout; [headerHeight] is the
 * header row's height on a screen that wide, laid out for landscape or not, where it isn't the player tabs' (null).
 */
@Composable
private fun GamePage(
    categories: List<ScoreCategory>,
    headerHeight: @Composable (width: Dp, landscape: Boolean) -> Dp?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(GameLayout) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // Without this, the player header row rendered flush against the very top of the
            // screen and sat under the status bar's clock/icons on some devices - a fixed
            // padding amount can't account for how tall that area actually is per device, so
            // ask the system for its real inset instead. The sides too: in landscape a camera
            // cutout or the navigation buttons sit there.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.TopCenter,
    ) {
        val layout = gameLayout(maxWidth, maxHeight, categories, headerHeight = headerHeight(maxWidth, maxWidth > maxHeight))
        val density = LocalDensity.current
        // Scrolled from the whole width, the empty sides too, not just the game centred in it.
        val scroll = rememberScrollState()
        // The board is a drawn object, so on a bigger screen all of it is drawn bigger - art, text and
        // touch targets together - rather than its pieces spreading apart. The player's font scale is kept.
        CompositionLocalProvider(LocalDensity provides Density(density.density * layout.scale, density.fontScale)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The tray's mat as deep as gameLayout leaves room for.
                CompositionLocalProvider(LocalScatterHeight provides layout.scatterHeight) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = layout.maxWidth)
                            .fillMaxWidth()
                            .padding(GAME_PADDING),
                        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
                    ) {
                        content(layout)
                    }
                }
            }
        }
        // In the right-hand margin, beside the game rather than over it, and only while it's too tall to fit.
        VerticalScrollbar(scroll, width = SCROLLBAR_WIDTH, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/**
 * Draws the game the setup form would start - its board and cup, its tray's mat and every face of its dice - once, out of
 * sight, in the player's table art and laid out as the game will be on this screen, so the game's first frame isn't also
 * the first paint of its art. A background, mat or die face is painted once at its size and then stamped
 * ([net.zodac.dicefive.ui.style.drawCachedSurface]), and the heaviest (Leather, Stone) took several frames to paint, all
 * on the frame the game opened. Placed on the setup screen, under its opaque backdrop, in a 1dp clipped box: it waits for
 * the screen to settle, then draws one piece a pass and drops it again. Done again when the art, the mode or the screen
 * changes, and not otherwise: the art is still painted from last time.
 */
@Composable
fun GameWarmUp(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val table by viewModel.tableSettings.collectAsStateWithLifecycle()
    val setup by viewModel.setupForm.setup.collectAsStateWithLifecycle()
    val setupRestored by viewModel.setupForm.setupRestored.collectAsStateWithLifecycle()
    val theme = table?.visualTheme
    if (theme == null || !setupRestored) return
    val game = remember(setup) { viewModel.previewGame() }
    GameArtWarmUp(theme, game, GAME_WARM_UP_DELAY_MILLIS, modifier)
}

/**
 * [GameWarmUp] for the game Continue resumes, which opens with no setup form to draw it under: placed on the menu, under
 * its opaque page, it draws the saved game ([loadGame], read once there's [hasInProgressGame]) in the player's [picks].
 * Nothing without a saved game.
 */
@Composable
fun SavedGameWarmUp(picks: SavedStyles?, hasInProgressGame: Boolean, loadGame: suspend () -> GameState?, modifier: Modifier = Modifier) {
    val game by produceState<GameState?>(null, hasInProgressGame) { value = if (hasInProgressGame) loadGame() else null }
    val theme = remember(picks) { picks?.visualTheme }
    GameArtWarmUp(theme ?: return, game ?: return, SAVED_GAME_WARM_UP_DELAY_MILLIS, modifier)
}

/** Draws [game]'s art in [theme] for [GameWarmUp], [delayMillis] after the screen it's under settles. */
@Composable
private fun GameArtWarmUp(theme: GameVisualTheme, game: GameState, delayMillis: Long, modifier: Modifier) {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val key = GameWarmUpKey(theme, game.gameMode, game.categories, window, density.density, density.fontScale)
    if (key == gameWarmedUp) return
    // As big as the game's page, so it lays out as it would there.
    val page = with(density) { DpSize(window.width.toDp(), window.height.toDp()) }
    OffscreenWarmUp<GameWarmUpPass>(
        // Restarted by each change to the form, so a few quick taps through the modes draw only the last.
        key = key,
        delayMillis = delayMillis,
        pageSize = page,
        modifier = modifier,
        run = {
            // Each die has a face of its own for each value (naturalPatternSeed): turned round a value a pass, every die shows every one.
            val values = game.gameMode.dieValues.toList()
            val faces = values.indices.map { turn -> GameWarmUpPass.Dice(game.dice.indices.map { values[(it + turn) % values.size] }) }
            for (pass in listOf(GameWarmUpPass.Board, GameWarmUpPass.Mat) + faces) draw(pass)
            gameWarmedUp = key
        },
    ) { pass ->
        CompositionLocalProvider(LocalGameVisualTheme provides theme) {
            GamePage(categories = game.categories, headerHeight = { _, _ -> null }, modifier = Modifier.fillMaxSize()) { layout ->
                val board = @Composable {
                    if (pass == GameWarmUpPass.Board) {
                        GameBoard(state = game, rolling = false, canUndo = false, onScoreCategory = {}, onCupTap = {}, onUndo = {}, landscape = layout.landscape)
                    }
                }
                val tray = @Composable {
                    if (pass != GameWarmUpPass.Board) {
                        val values = (pass as? GameWarmUpPass.Dice)?.values
                        DiceTray(
                            dice = game.dice.mapIndexed { index, die -> die.copy(value = values?.get(index) ?: die.value) },
                            gameMode = game.gameMode,
                            enabled = false,
                            showDice = pass is GameWarmUpPass.Dice,
                            rolling = false,
                            onToggleHold = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                val sideCup = @Composable {
                    if (pass == GameWarmUpPass.Board) {
                        SideCup(layout.cupScale) { sized -> GameCup(state = game, rolling = false, canUndo = false, onCupTap = {}, onUndo = {}, modifier = sized) }
                    }
                }
                GameTable(layout.landscape, board, tray, sideCup)
            }
        }
    }
}

/** One step of [GameWarmUp]: the board and its cup, the tray's empty mat, or its dice showing these [Dice.values], one a die. */
private sealed interface GameWarmUpPass {
    data object Board : GameWarmUpPass
    data object Mat : GameWarmUpPass
    data class Dice(val values: List<Int>) : GameWarmUpPass
}

/** What [GameWarmUp] drew for: the art, the game's mode and scorecard, and the screen. */
private data class GameWarmUpKey(
    val theme: GameVisualTheme,
    val mode: GameMode,
    val categories: List<ScoreCategory>,
    val window: IntSize,
    val density: Float,
    val fontScale: Float,
)

// What GameWarmUp last drew for, for the life of the process. Main thread only.
private var gameWarmedUp: GameWarmUpKey? = null

// Long enough for the setup screen's own entrance (the page cover's fade) to be over.
private const val GAME_WARM_UP_DELAY_MILLIS = 600L

// On the menu, later than the Styles page's warm-up starts (600ms), so the two don't begin on the same frame.
private const val SAVED_GAME_WARM_UP_DELAY_MILLIS = 1200L

@Composable
private fun InProgressGame(
    state: GameState,
    landscape: Boolean,
    cupScale: Float,
    canUndo: Boolean,
    superuserModeActive: Boolean,
    aiRolling: Boolean,
    tapRolling: Boolean,
    turnSecondsRemaining: () -> Int?,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRoll: () -> Unit,
    onToggleHold: (Int) -> Unit,
    onCycleValue: (Int) -> Unit,
    onScoreCategory: (ScoreCategory) -> Unit,
    claimFiveOfAKindFlash: (Int) -> Boolean,
    onTapCupWithNoRollsLeft: () -> Unit,
    onShakeRollDetected: () -> Unit,
    cupShakeMillis: Long,
    // The tutorial's coach card, in place of the player tabs: null in a real game.
    coach: (@Composable (Modifier) -> Unit)? = null,
) {
    val currentPlayer = state.currentPlayer
    val isHumanTurn = currentPlayer?.type == PlayerType.HUMAN
    // In the tutorial, the dice roll and hold only at the steps that ask for it.
    val tutorial = LocalTutorialGuide.current
    val canRoll = isHumanTurn && state.rollsRemaining > 0 && (tutorial == null || tutorial.rolls)
    // No rollsRemaining condition here (see GameEngine.toggleHold): holding still has no effect on
    // a roll that won't happen after the last one, but disabling the dice entirely once it hits 0
    // looked like the tray had broken, and it blocked superuser cycling right when it's most
    // likely to be used - right after seeing the final roll.
    val canHold = isHumanTurn && state.phase == TurnPhase.ROLLED && (tutorial == null || tutorial.step is TutorialStep.Hold)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The cup/tray don't care whether the shake was kicked off by a human tap or a CPU's turn - both are timed by the
    // view model (GameViewModel.shakeAndRoll and its CPU loop) and either way it's the same "rolling" pose.
    val isRolling = tapRolling || aiRolling
    // A turn's first dice are still in the cup until they're thrown - there are no dice on the mat to
    // gather up yet - so they appear as the roll lands. Under reduced motion there's no toss: the dice
    // simply appear on their result then, and a later roll's dice stay put until they snap to theirs.
    val reduceMotion = LocalReduceMotion.current
    val showDice = state.phase == TurnPhase.ROLLED

    // The dice are still tumbling to a stop for a moment after the roll lands (see DiceTray), and
    // scoring waits for them: nothing lights up or takes a tap until they've settled. Tracked from
    // the very composition the roll lands in - not a frame later - so the highlights can't flash on
    // before being taken away again.
    val rollTracker = remember { RollTracker(isRolling) }
    rollTracker.update(isRolling)
    val settled = remember(rollTracker.landings) { mutableStateOf(rollTracker.landings == 0 || reduceMotion) }
    LaunchedEffect(rollTracker.landings) {
        if (!settled.value) {
            lifecycle.delayWhileResumed(DICE_TOSS_MILLIS.toLong())
            settled.value = true
        }
    }
    val diceSettling = !settled.value
    // A roll is in hand from the cup's first shake until its dice have come to rest - the same window
    // GameBoard holds scoring for. The cup takes no tap in it either (a finger or a phone shake):
    // the next roll waits until this one's dice have settled.
    val rollInHand = isRolling || diceSettling

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
    // sound with no shake before it. Which clips play is shakeSound's and landingSounds' choice,
    // from the dice as the shake started (shakenDice) and as they landed.
    val soundEffects = rememberSoundEffects()
    val haptics = rememberDiceHaptics()
    val chickenCup = LocalGameVisualTheme.current.diceCupStyle is ChickenDiceCupStyle
    var previousRolling by remember { mutableStateOf(false) }
    var shakenDice by remember { mutableStateOf(state.dice) }
    LaunchedEffect(isRolling) {
        if (isRolling && !previousRolling) {
            shakenDice = state.dice
            soundEffects.playRollSound(shakeSound(state.dice, chickenCup))
            // A CPU's roll is heard but not felt: the phone only buzzes for the player's own roll.
            if (isHumanTurn) haptics.playShakeBuzz()
        } else if (!isRolling && previousRolling) {
            landingSounds(shakenDice, state.dice).forEach(soundEffects::playRollSound)
        }
        previousRolling = isRolling
    }

    // Fired from the die's own held state *before* the toggle is applied, not the toggle's
    // result, since onToggleHold only forwards the tapped index - it doesn't report which way
    // the hold flipped.
    val onToggleHoldWithSound = { dieIndex: Int ->
        // A die locked by Unlucky Dice can't be held: nothing to hear or feel, and nothing to do.
        if (state.dice.getOrNull(dieIndex)?.isUnlucky != true) {
            if (state.dice.getOrNull(dieIndex)?.isHeld == true) {
                soundEffects.playUnhold()
            } else {
                soundEffects.playHold()
            }
            haptics.playHoldTick()
            onToggleHold(dieIndex)
        }
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

    // Two independent ifs, not if/else - "No More Rolls" is the mutually-exclusive fallback case
    // (rollsRemaining hit 0, the tap does nothing for the game itself). The shake and the roll after it are the view
    // model's (GameViewModel.shakeAndRoll), timed and paused as a CPU's are.
    val onCupTap = {
        if (canRoll && !rollInHand) onRoll()
        if (!canRoll && isHumanTurn && state.rollsRemaining == 0 && !rollInHand) {
            onTapCupWithNoRollsLeft()
        }
    }

    // Shaking the phone is just another way to "tap" the cup - same gating, same animation/sound,
    // same no-rolls-left fallback - it only additionally reports the achievement, and only for a
    // shake that actually triggers a roll, not one that lands on the no-op fallback. Not offered at
    // all while viewing another player's scorecard, matching the cup itself being untappable then.
    val onShakeDetected = {
        if (viewedPlayer == null) {
            if (canRoll && !rollInHand) onShakeRollDetected()
            onCupTap()
        }
    }
    // Only listening while a shake could do anything: this player's own turn, on their own scorecard.
    ShakeDetectorEffect(enabled = isHumanTurn && viewedPlayer == null, onShake = onShakeDetected)

    // The back arrow shares the tabs' row, so the row is no taller and nothing below it moves. It is pulled
    // out into the screen's padding so the arrow lines up with the board's edge, as every page's BackArrow does.
    Row(
        modifier = Modifier.layout { measurable, constraints ->
            val extra = BACK_ARROW_OUTSET.roundToPx()
            val placeable = measurable.measure(constraints.offset(horizontal = extra))
            layout(placeable.width - extra, placeable.height) { placeable.place(-extra, 0) }
        },
        verticalAlignment = Alignment.Top,
    ) {
        // Lifted so its centre is ScreenScaffold's 32dp below the status bar: the screen's top padding
        // puts this row at 16dp, and a bar's 48dp button sits 8dp down in it. Drawn only - the row's height is unchanged.
        BackArrow(onBack = onBack, modifier = Modifier.offset(y = BACK_ARROW_TOP - GAME_PADDING))
        if (coach != null) {
            coach(Modifier.weight(1f))
        } else {
            PlayerHeaderBar(
                players = state.players,
                currentPlayerIndex = state.currentPlayerIndex,
                viewedPlayerIndex = viewedPlayerIndex,
                enabled = isHumanTurn,
                onPlayerTap = onPlayerTap,
                modifier = Modifier.weight(1f),
                singleLine = landscape,
            )
        }
    }

    RunningTurnTimer(turnSecondsRemaining)

    val board = @Composable {
        val viewedIndex = viewedPlayerIndex
        if (viewedPlayer != null && viewedIndex != null) {
            ReadOnlyScoreboard(player = viewedPlayer, landscape = landscape)
        } else {
            GameBoard(
                state = state,
                rolling = isRolling,
                pouring = pouring,
                diceSettling = diceSettling,
                canUndo = canUndo,
                onScoreCategory = onScoreCategory,
                claimFiveOfAKindFlash = claimFiveOfAKindFlash,
                onCupTap = onCupTap,
                onUndo = onUndo,
                landscape = landscape,
            )
        }
    }

    // The one DiceTray call for both branches above - a live turn's own dice, or a viewed player's
    // last roll - so the gap beside it is identical either way, not a second hand-picked layout that
    // only one branch remembers to apply. Skipped entirely for a viewed player who hasn't finished a
    // turn yet, or whose last turn timed out unrolled - there's no roll of theirs to show, not even an empty mat.
    val viewedLastRoll = viewedPlayer?.lastRoll
    val tray = @Composable {
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

    // Which dice the box under a finger would take, from the board to the tray (see LocalHandPreview).
    val handPreview = remember { mutableStateOf<ProjectedHand?>(null) }
    // The cup beside the landscape tray. Its space is kept while another player's scorecard is up (no cup then), so the
    // tray stays put.
    val sideCup = @Composable {
        SideCup(cupScale) { sized ->
            if (viewedPlayer == null) {
                GameCup(
                    state = state,
                    rolling = isRolling,
                    pouring = pouring,
                    diceSettling = diceSettling,
                    canUndo = canUndo,
                    onCupTap = onCupTap,
                    onUndo = onUndo,
                    modifier = sized,
                )
            } else {
                Spacer(modifier = sized)
            }
        }
    }

    CompositionLocalProvider(LocalHandPreview provides handPreview) { GameTable(landscape, board, tray, sideCup) }
}

/**
 * The board over the tray - or, in [landscape], the board across the top and under it the tray in two thirds of the
 * width with [sideCup] in the last third, at the right-hand side as it is in the board on a phone.
 */
@Composable
private fun GameTable(landscape: Boolean, board: @Composable () -> Unit, tray: @Composable () -> Unit, sideCup: @Composable () -> Unit) {
    if (landscape) {
        // The board drawn larger than the tray: its three rows are otherwise small beside the dice.
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density * LANDSCAPE_BOARD_SCALE, density.fontScale)) { board() }
        Row(horizontalArrangement = Arrangement.spacedBy(SECTION_GAP), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(LANDSCAPE_TRAY_WEIGHT)) { tray() }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { sideCup() }
        }
    } else {
        board()
        tray()
    }
}

/** The cup beside the landscape tray: drawn [cupScale] times larger than in the board, as it would otherwise look small beside the dice. */
@Composable
private fun SideCup(cupScale: Float, cup: @Composable (Modifier) -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density * cupScale, density.fontScale)) {
        cup(Modifier.size(width = SIDE_CUP_WIDTH, height = SIDE_CUP_HEIGHT))
    }
}

/** The game's scrollbar, in the screen's right-hand margin. */
private val SCROLLBAR_WIDTH = 12.dp

/** The gap between the header, the board and the tray. */
private val SECTION_GAP = 14.dp

/** The phone width the board was laid out on: the game is drawn at its own size here, and scaled up from it. */
private val REFERENCE_WIDTH = 411.dp

/** Roughly how tall the player tabs' row is - near enough to choose a layout by; the page scrolls if it's off. */
private val HEADER_HEIGHT = 82.dp

/** The same in landscape, its tabs one line each: the back arrow's 48dp row. */
private val LANDSCAPE_HEADER_HEIGHT = 48.dp

/** The cup and its roll count beside the landscape tray: the cup's 104dp square and room for "x2" beside it, drawn as large
 * as its third of the row allows (see gameLayout). */
private val SIDE_CUP_WIDTH = 150.dp
private val SIDE_CUP_HEIGHT = 120.dp

/** Widest the game is laid out at (before scaling) in landscape: the board's six columns across, the tray and cup under it. */
private val LANDSCAPE_WIDTH = 720.dp

/** How much larger than the tray the landscape board is drawn. */
private const val LANDSCAPE_BOARD_SCALE = 1.35f

/** The smallest a box or die is drawn in landscape: a full touch target. */
private val MIN_TOUCH_TARGET = 48.dp

/** The tray's share of the landscape row under the board, against the cup's 1. */
private const val LANDSCAPE_TRAY_WEIGHT = 2f

/** How far the game is ever scaled up - past this, a larger screen just has more space around the board. */
private const val MAX_GAME_SCALE = 2f

/** Widest the game is laid out at (before scaling), one above the other: a squarish screen centres it rather
 * than stretching the board's columns apart. */
private val STACKED_MAX_WIDTH = 480.dp

/** Whether the game is laid out for landscape, how much it's scaled up, and how wide it's laid out. */
internal data class GameLayout(
    val landscape: Boolean,
    val scale: Float,
    val maxWidth: Dp,
    val cupScale: Float = 1f,
    val headerRoom: Dp = Dp.Infinity,
    val scatterHeight: Dp = SCATTER_AREA_HEIGHT,
)

/**
 * How the game fits a [width] x [height] screen: on a landscape one, the board over the tray and cup ([GameLayout.landscape]);
 * otherwise (a phone, a tablet held upright) the board over the tray. And how much larger than on [REFERENCE_WIDTH]'s phone it's
 * drawn - never so small that the tiles and dice fall under their minimum touch size, so a screen too short scrolls instead.
 * [headerHeight] is the header row's where it isn't the player tabs' (the tutorial's coach card). [GameLayout.headerRoom] is
 * how tall the header can be at the scale chosen and the game still fit, in the dp it's laid out in - where it's drawn at the
 * smallest it may be, for the coach card to shrink to rather than the game scrolling. On a portrait screen too short for its
 * header at 1x (the coach card on a small phone), the tray's mat is first made shallower ([GameLayout.scatterHeight]), as far
 * as [MIN_SCATTER_HEIGHT], to make the room.
 */
/**
 * How wide a Standard game's content (inside its padding) is drawn on a landscape [width] x [screenHeight] screen, the
 * room [GamePage] has: a wide page's content is as wide where that's wider (see `wideColumnMaxWidth`).
 */
fun landscapeGameContentWidth(width: Dp, screenHeight: Dp): Dp =
    (LANDSCAPE_WIDTH - GAME_PADDING * 2) * gameLayout(width, screenHeight, GameMode.STANDARD.categories).scale

internal fun gameLayout(width: Dp, screenHeight: Dp, categories: List<ScoreCategory>, headerHeight: Dp? = null): GameLayout {
    // A little short of the screen: drawn scaled, the pieces' sizes round to whole pixels, and a game that only just fits
    // otherwise came out a pixel or two too tall, and scrolled.
    val height = screenHeight - ROUNDING_SLACK
    val paneWidth = REFERENCE_WIDTH - GAME_PADDING * 2
    if (width > height) {
        val landscapeChrome = GAME_PADDING * 2 + (headerHeight ?: LANDSCAPE_HEADER_HEIGHT) + SECTION_GAP
        // Laid out at LANDSCAPE_WIDTH, the cup as large as its third of the width and the tray's height allow. Its tiles and
        // dice are larger there than a phone's, so on a short screen (a phone on its side) it may be drawn smaller than 1x,
        // as far as keeps both a full touch target.
        val content = LANDSCAPE_WIDTH - GAME_PADDING * 2 - SECTION_GAP
        val trayWidth = content * LANDSCAPE_TRAY_WEIGHT / (LANDSCAPE_TRAY_WEIGHT + 1f)
        val tray = diceTrayHeight(trayWidth)
        val belowHeader = landscapeBoardHeight(categories) * LANDSCAPE_BOARD_SCALE + SECTION_GAP + tray
        val scale = minOf(width / LANDSCAPE_WIDTH, height / (landscapeChrome + belowHeader))
        val cupScale = minOf((content - trayWidth) / SIDE_CUP_WIDTH, tray / SIDE_CUP_HEIGHT)
        val minScale = maxOf(MIN_TOUCH_TARGET / (REGULAR_TILE_SIZE * LANDSCAPE_BOARD_SCALE), MIN_TOUCH_TARGET / diceTraySlot(trayWidth))
        val shown = scale.coerceIn(minScale, MAX_GAME_SCALE)
        val headerRoom = height / shown - (GAME_PADDING * 2 + SECTION_GAP + belowHeader)
        return GameLayout(landscape = true, scale = shown, maxWidth = LANDSCAPE_WIDTH, cupScale = cupScale, headerRoom = headerRoom)
    }
    val chrome = GAME_PADDING * 2 + (headerHeight ?: HEADER_HEIGHT) + SECTION_GAP
    val belowHeader = gameBoardHeight(categories) + SECTION_GAP + diceTrayHeight(paneWidth)
    val stacked = minOf(width / REFERENCE_WIDTH, height / (chrome + belowHeader))
    val shown = stacked.coerceIn(1f, MAX_GAME_SCALE)
    // Measured at the width it's actually laid out at - narrower than REFERENCE_WIDTH on a small phone, where the tray is shorter.
    val laidOutPane = minOf(width / shown, STACKED_MAX_WIDTH) - GAME_PADDING * 2
    val room = height / shown - (GAME_PADDING * 2 + SECTION_GAP + gameBoardHeight(categories) + SECTION_GAP + diceTrayHeight(laidOutPane))
    val shortfall = ((headerHeight ?: HEADER_HEIGHT) - room).coerceIn(0.dp, SCATTER_AREA_HEIGHT - MIN_SCATTER_HEIGHT)
    return GameLayout(
        landscape = false,
        scale = shown,
        maxWidth = STACKED_MAX_WIDTH,
        headerRoom = room + shortfall,
        scatterHeight = SCATTER_AREA_HEIGHT - shortfall,
    )
}

/**
 * The header row's height in the tutorial, its coach card's, on a [screenWidth] screen laid out for [landscape] or not: the
 * row is the game's width, pulled out into the padding for the back arrow (see InProgressGame), and the card has it less the
 * arrow. Measured at the width the game is laid out at before it's scaled - a phone narrower than REFERENCE_WIDTH's is laid
 * out at its own, where the words take more lines - so it's in the same dp as the rest of [gameLayout].
 */
@Composable
private fun coachHeaderHeight(steps: List<TutorialStep>, screenWidth: Dp, landscape: Boolean): Dp {
    val layoutWidth = if (landscape) LANDSCAPE_WIDTH else minOf(screenWidth, REFERENCE_WIDTH)
    val cardWidth = layoutWidth - GAME_PADDING * 2 + BACK_ARROW_OUTSET - BACK_ARROW_SIZE
    return maxOf(coachCardHeight(steps, cardWidth), BACK_ARROW_SIZE)
}

/** The shortest the tutorial's coach card is made to fit a short screen: its label and three lines of words. */
private val MIN_COACH_HEIGHT = 96.dp

/** What [gameLayout] leaves spare of the screen's height, for its pieces' sizes rounding to whole pixels. */
private val ROUNDING_SLACK = 4.dp

/** The back arrow's button: M3's 48dp icon button. */
private val BACK_ARROW_SIZE = 48.dp

private val NO_OP_TOGGLE_HOLD: (Int) -> Unit = {}

/** The current player's remaining turn time - counts down to zero, at which point the turn is
 * forfeited and auto-scored (see [GameViewModel.autoScoreOnTimeout]). Sits at its normal muted
 * color until the last [TURN_TIMER_FLASH_SECONDS] seconds, when it flashes red against that same
 * muted color instead of sitting static, so the final countdown is hard to miss even out of the
 * corner of an eye. */
@Composable
fun TurnTimerBadge(secondsRemaining: Int, modifier: Modifier = Modifier) {
    val flashing = secondsRemaining in 1..TURN_TIMER_FLASH_SECONDS
    val runningOutDescription = pluralStringResource(Res.plurals.game_timer_running_out_cd, TURN_TIMER_FLASH_SECONDS, TURN_TIMER_FLASH_SECONDS)
    val timeLeftDescription = pluralStringResource(Res.plurals.game_timer_left_cd, secondsRemaining, secondsRemaining)
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
        text = stringResource(Res.string.game_timer_left, secondsRemaining),
        // The flash is colour alone, so its spoken twin: once the flash starts, the badge is a polite live
        // region whose text is the same for every second of it - TalkBack announces the warning once,
        // not a count every second. Before that the seconds change silently, and read in words ("12s"
        // would be spoken as letters).
        modifier = modifier.clearAndSetSemantics {
            if (flashing) {
                contentDescription = runningOutDescription
                liveRegion = LiveRegionMode.Polite
            } else {
                contentDescription = timeLeftDescription
            }
        },
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        color = color,
        style = MaterialTheme.typography.labelMedium,
    )
}

/**
 * The [TurnTimerBadge] for [secondsRemaining], only while a timer is actually running for this turn - see
 * GameViewModel.syncTurnTimer. The seconds are read here, so a tick recomposes this alone.
 */
@Composable
private fun RunningTurnTimer(secondsRemaining: () -> Int?) {
    val seconds = secondsRemaining() ?: return
    TurnTimerBadge(secondsRemaining = seconds, modifier = Modifier.fillMaxWidth())
}

/** [TurnTimerBadge]'s flash: the error red and back to [mutedColor], round and round. */
@Composable
private fun rememberFlashColor(mutedColor: Color): Color {
    val flashColor by rememberInfiniteTransition(label = "turnTimerFlash").animateColor( // i18n: not translated - an animation label, not shown
        initialValue = MaterialTheme.colorScheme.error,
        targetValue = mutedColor,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = TURN_TIMER_FLASH_PERIOD_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "turnTimerFlashColor", // i18n: not translated - an animation label, not shown
    )
    return flashColor
}
