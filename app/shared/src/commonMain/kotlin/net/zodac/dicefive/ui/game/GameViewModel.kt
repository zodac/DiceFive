package net.zodac.dicefive.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.zodac.dicefive.app.AchievementUnlocker
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.orNullOnFailure
import net.zodac.dicefive.data.scores.PlayerResult
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.data.settings.savedStylesFlow
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.game.AchievementTracker
import net.zodac.dicefive.game.AiTurnPlayer
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.game.GameStartContext
import net.zodac.dicefive.game.HandCompletion
import net.zodac.dicefive.game.HoldSequence
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.game.StandardPerfectPlayTable
import net.zodac.dicefive.game.TutorialDice
import net.zodac.dicefive.game.TutorialScript
import net.zodac.dicefive.game.TutorialStep
import net.zodac.dicefive.game.isPlayerOneTurn
import net.zodac.dicefive.game.localHourOfDay
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.game.style.hasNonDefaultStyle
import net.zodac.dicefive.game.toTieBreakStats
import net.zodac.dicefive.game.wonBy
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.hasGrownSunflower
import net.zodac.dicefive.model.maxPlayerNameLength
import net.zodac.dicefive.model.resolvePlayerName
import net.zodac.dicefive.model.takeNameWidth
import net.zodac.dicefive.ui.style.ChickenDiceCupStyle
import net.zodac.dicefive.ui.style.EggDiceStyle
import net.zodac.dicefive.ui.style.FlowerpotDiceCupStyle
import net.zodac.dicefive.ui.style.GoldenEggDiceStyle
import net.zodac.dicefive.ui.style.PIP_FACES
import net.zodac.dicefive.ui.style.visualTheme

/**
 * Owns both the pre-game setup form ([setupForm]) and the live [GameState] once started,
 * scoped to the "play" nav graph so [net.zodac.dicefive.ui.setup.GameSetupScreen]
 * and [GameScreen] share one instance across navigation.
 *
 * [factory] supplies the real repositories; tests pass in-memory ones. [tutorialFactory] builds one for the guided game.
 */
class GameViewModel(
    private val scoreRepository: ScoreRepository,
    private val settingsRepository: SettingsRepository,
    private val inProgressGameRepository: InProgressGameRepository,
    private val achievementsRepository: AchievementStore,
    /** The dice. Injectable for the same reason [GameEngine.rollDice] takes one: so a test can deal a known hand. */
    private val random: Random = Random.Default,
    /** Where [AiTurnPlayer]'s (Hard, specifically) exhaustive-search hold/category choices run -
     * off [viewModelScope]'s own Main.immediate by default, since they're expensive enough to
     * visibly stall Compose's frame rendering otherwise. Injectable so a test using a virtual-time
     * [kotlinx.coroutines.test.TestDispatcher] for `Dispatchers.Main` can pass that same dispatcher
     * here too - Default's real thread pool isn't advanced by that test's `advanceUntilIdle()`. */
    private val aiDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Whether this is a debug build - superuser mode never activates without it. */
    private val isDebugBuild: Boolean = false,
    /** Standard's perfect-play table, for Hard CPUs in Standard (see [StandardPerfectPlayTable]) - without it, they estimate. */
    private val standardPerfectPlay: (suspend () -> StandardPerfectPlayTable?)? = null,
    /** The hour (0-23) on the phone's clock now. Injectable so a test can start a game at any time of day. */
    private val localHour: () -> Int = { localHourOfDay(nowEpochMillis()) },
    /**
     * Set for the guided game ([startTutorial]), played solo: every roll and move is the script's, and nothing is kept -
     * no saved game, scores, achievements or settings.
     */
    private val tutorial: TutorialScript? = null,
    /** Whether the purchase unlocks every style ([AppContainer.ownsAllStyles]). */
    private val ownsAllStyles: Flow<Boolean> = flowOf(false),
    /** Where its achievements are earned: the app's one ([AppContainer.achievementUnlocker]); a test's own otherwise. */
    private val achievementUnlocker: AchievementUnlocker = AchievementUnlocker(achievementsRepository, ownsAllStyles),
) : ViewModel() {

    /** The New Game form, filled from the settings as this is built. */
    val setupForm = GameSetupForm()

    private val _game = MutableStateFlow<GameState?>(null)
    val game: StateFlow<GameState?> = _game.asStateFlow()

    /**
     * The waits a CPU's turn keeps for the screen's animations - see [GamePacing]. Set by the screen; read by
     * the game loop on another thread, hence volatile.
     */
    @Volatile
    private var pacing = GamePacing()

    /** Replaces the CPU's waits with [pacing], as the screen's motion and sound settings change. */
    fun setPacing(pacing: GamePacing) {
        this.pacing = pacing
    }

    /** Whether the game is in front of the player - see [setForeground]. */
    private val foreground = MutableStateFlow(true)

    /**
     * Freezes the game's own clocks while the game isn't on screen (`GameScreen` is backgrounded, or
     * covered by another screen such as Achievements): the turn timer stops counting, and a CPU turn
     * stops where it is and carries on when the player is back - neither runs down or plays out
     * behind their back. Every wait in those two goes through [pausableDelay]; a wait already under
     * way when the game goes away starts again on return. Nothing else in the game runs on a timer.
     */
    fun setForeground(value: Boolean) {
        onScreen = value
        updateForeground()
    }

    /**
     * Holds the game's clocks while a dialog over it (the leave-game confirmation) is up, or the page cover
     * while a page opens, exactly as if the game were off screen - see [setForeground]. Released when the
     * dialog closes or the cover clears.
     */
    fun setHeld(value: Boolean) {
        held = value
        updateForeground()
    }

    private fun updateForeground() {
        val running = onScreen && !held
        foreground.value = running
        if (!running) saveTurnTimerProgress()
    }

    /** Saves the game with the turn timer's seconds left, so Continue resumes the countdown rather than
     * restarting it. Only when the game stops running: the live state never carries it, and saving it
     * every second would rewrite the game on every tick. */
    private fun saveTurnTimerProgress() {
        val state = _game.value ?: return
        val remaining = _turnSecondsRemaining.value ?: return
        if (state.isGameOver || tutorial != null) return
        inProgressGameRepository.save(savedCopy(state).copy(turnSecondsLeft = remaining))
    }

    /** Seconds [resumeGame] found left on the saved turn, for the next timer start to use. */
    private var resumedSecondsLeft: Int? = null

    private var onScreen = true
    private var held = false

    /** [delay] that doesn't run while [foreground] is false - see [setForeground]. */
    private suspend fun pausableDelay(millis: Long) {
        while (true) {
            foreground.first { it }
            if (withTimeoutOrNull(millis) { foreground.first { !it } } == null) return
        }
    }

    /** Whether a CPU's cup is shaking for its roll - see [maybeStartAiTurn]. */
    private val _aiRolling = MutableStateFlow(false)
    val aiRolling: StateFlow<Boolean> = _aiRolling.asStateFlow()

    /** Whether a human's cup is shaking for the roll they asked for - see [shakeAndRoll]. */
    private val _tapRolling = MutableStateFlow(false)
    val tapRolling: StateFlow<Boolean> = _tapRolling.asStateFlow()

    private var tapRollJob: Job? = null

    /** Seconds left on the current turn's timer - human or AI alike, so the badge's space in the
     * layout never shifts between turns - or null when [GameState.turnSeconds] is (no timer from
     * either the setup form or the mode) or the game is over - see [syncTurnTimer]. */
    private val _turnSecondsRemaining = MutableStateFlow<Int?>(null)
    val turnSecondsRemaining: StateFlow<Int?> = _turnSecondsRemaining.asStateFlow()

    /** The state to restore if [undo] is called - the pre-commit snapshot of the most recent
     * scoring action only. Rolling and holding/unholding dice are pure exploration/selection with
     * no result of their own to undo; only commitScore actually changes the scorecard. */
    private var undoSnapshot: GameState? = null
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    // The tutorial keeps nothing, so there's nothing to lose by leaving it.
    val confirmBeforeLeavingGame: StateFlow<Boolean> = if (tutorial != null) {
        MutableStateFlow(false)
    } else {
        settingsRepository.confirmBeforeLeavingGame.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    }

    /** Whether this is the guided game - see [tutorial]. */
    val isTutorial: Boolean
        get() = tutorial != null

    /** Every step of [tutorial]'s guided game - none outside one. */
    val tutorialSteps: List<TutorialStep>
        get() = tutorial?.steps.orEmpty()

    /** How far through [tutorial]'s steps the guided game is. */
    private var tutorialIndex = 0

    private val _tutorialStep = MutableStateFlow(tutorial?.steps?.firstOrNull())

    private val _tutorialTurn = MutableStateFlow(1)

    /** Which of [tutorial]'s turns [tutorialStep] is part of, from 1 - see [TutorialScript.turnOf]. */
    val tutorialTurn: StateFlow<Int> = _tutorialTurn.asStateFlow()

    /** How many turns [tutorial] has. */
    val tutorialTurns: Int
        get() = tutorial?.turns ?: 0

    /**
     * The guided game's step, for the coach card and the board - null outside a tutorial, and once its last step is done.
     * After a roll it's the step after once the dice have settled; what the player may do moves on as the roll lands.
     */
    val tutorialStep: StateFlow<TutorialStep?> = _tutorialStep.asStateFlow()

    private val currentTutorialStep: TutorialStep?
        get() = tutorial?.steps?.getOrNull(tutorialIndex)

    /** The guided game as it began, for [jumpToTutorialTurn] to replay from. */
    private var tutorialStart: GameState? = null

    /**
     * The coach card's back (-1) and forward (+1): to the start of the previous or next turn - or, going back from part
     * way through a turn, to the start of this one. The game is rebuilt as if every step before it had been played
     * ([TutorialScript.replay]); a roll still shaking or landing is dropped.
     */
    fun jumpToTutorialTurn(direction: Int) {
        val script = tutorial ?: return
        val start = tutorialStart ?: return
        val starts = script.turnStarts
        val current = starts.indexOfLast { it <= tutorialIndex }
        val target = when {
            direction < 0 && tutorialIndex > starts[current] -> starts[current]
            direction < 0 -> starts.getOrNull(current - 1) ?: return
            else -> starts.getOrNull(current + 1) ?: return
        }
        tapRollJob?.cancel()
        _tapRolling.value = false
        tutorialStepJob?.cancel()
        tutorialIndex = target
        showTutorialStep(target)
        setUndoSnapshot(null)
        applyGameState(script.replay(start, target), checkForAiTurn = false)
    }

    /** Whether [jumpToTutorialTurn] can go back: anywhere but the very start. */
    val canJumpBack: Boolean
        get() = tutorial != null && tutorialIndex > 0

    /** Whether [jumpToTutorialTurn] can go forward: before the last turn. */
    val canJumpForward: Boolean
        get() = tutorial?.let { script -> script.turnStarts.any { it > tutorialIndex } } == true

    /** Showing the next step once a roll's dice have settled - see [tutorialStep]. */
    private var tutorialStepJob: Job? = null

    /** On to the next step, shown after [shownAfterMillis]. */
    private fun advanceTutorial(shownAfterMillis: Long = 0L) {
        tutorialIndex++
        val index = tutorialIndex
        tutorialStepJob?.cancel()
        if (shownAfterMillis <= 0L) {
            showTutorialStep(index)
        } else {
            tutorialStepJob = viewModelScope.launch {
                pausableDelay(shownAfterMillis)
                showTutorialStep(index)
            }
        }
    }

    private fun showTutorialStep(index: Int) {
        val script = tutorial ?: return
        _tutorialStep.value = script.steps.getOrNull(index)
        _tutorialTurn.value = script.turnOf(index).coerceAtMost(script.turns)
    }

    /** The totals were opened: moves on from a step that asked for them. */
    fun tutorialTotalsOpened() {
        if (currentTutorialStep is TutorialStep.OpenTotals) advanceTutorial()
    }

    /** Starts [tutorial]'s guided game from its first step: player 1 alone, under their saved name ([defaultName] without one). */
    fun startTutorial(defaultName: String) {
        val script = tutorial ?: return
        viewModelScope.launch {
            val name = resolvePlayerName(settingsRepository.snapshot().playerNameFor(1).orEmpty(), defaultName).takeNameWidth(maxPlayerNameLength(1))
            tutorialIndex = 0
            showTutorialStep(0)
            val start = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = name)), script.gameMode)
            tutorialStart = start
            beginGame(start)
        }
    }

    /**
     * The table's art - each saved Styles pick resolved through its catalog's unlockedById, so an
     * id nothing recognises, or a style still locked, draws that category's default - and its
     * sound, vibration and roll settings. Null until they've all loaded, so the board is never
     * drawn in the defaults for a frame before switching to the player's own; this view model is
     * scoped to the whole "play" graph and loads them eagerly, so by the time the board shows
     * they're normally long since in.
     */
    val tableSettings: StateFlow<TableSettings?> = run {
        val visualTheme = savedStylesFlow(settingsRepository, achievementsRepository, ownsAllStyles).map { it.visualTheme }.distinctUntilChanged()
        combine(
            visualTheme,
            settingsRepository.soundEnabled,
            settingsRepository.vibrationEnabled,
        ) { theme, sound, vibration -> TableSettings(theme, sound, vibration) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    // A hidden cheat: hold, then unhold, all five dice in strict order (die 0's pair, then die
    // 1's, ... through die 4's) - on any turn, any player's. Once active for the rest of this
    // game, holding a finger on an already-held die cycles its face once a second - see
    // DiceTray's onCycleValue wiring and GameEngine.cycleDieValue.
    // holdSequence tracks progress toward unlocking it.
    private val _superuserModeActive = MutableStateFlow(false)
    val superuserModeActive: StateFlow<Boolean> = _superuserModeActive.asStateFlow()
    // A Channel, not a SharedFlow: a SharedFlow only buffers for collectors already subscribed at
    // emission time, so a message sent before the UI's LaunchedEffect attaches (a real possibility
    // right after process restart) would be silently dropped. A Channel queues it regardless of
    // when the collector shows up, and delivers each message exactly once - the right semantics
    // for a one-shot toast rather than state to replay.
    private val _toastMessages = Channel<String>(Channel.BUFFERED)
    val toastMessages: Flow<String> = _toastMessages.receiveAsFlow()
    private val holdSequence = HoldSequence()

    /** Player 1's achievement progress through the game that its final state can't show - see [AchievementTracker]. */
    private val tracker = AchievementTracker()


    /**
     * Whether this is the first 5x the player in [playerIndex]'s seat has had this game: true once per seat, then false.
     * Kept on the player ([PlayerState.fiveOfAKindFlashed]) and saved with the game, so a resume doesn't flash it again.
     * Stored without going through [applyGameState]: nothing about the turn changes, so the AI and timer have no reason to react.
     */
    fun claimFiveOfAKindFlash(playerIndex: Int): Boolean {
        val state = _game.value ?: return false
        val player = state.players.getOrNull(playerIndex) ?: return false
        if (player.fiveOfAKindFlashed) return false
        val updated = state.copy(players = state.players.toMutableList().also { it[playerIndex] = player.copy(fiveOfAKindFlashed = true) })
        _game.value = updated
        persistInProgressGame(updated)
        return true
    }

    init {
        viewModelScope.launch { setupForm.restore(settingsRepository.snapshot()) }
    }

    /**
     * Builds the initial [GameState] from the current setup form, generating AI names now. A Human seat
     * with no name typed gets [defaultName] for its slot ("Player 2", in the player's language - the
     * screen passes it; the English here is for tests, which have no screen).
     *
     * Returns whether the game started: not while two Human names clash ([GameSetupState.duplicateNameSlots]).
     */
    fun startGame(defaultName: (slot: Int) -> String = { "Player $it" }): Boolean { // i18n: not translated - for tests, the screen always passes the translated one
        // Read before anything below overwrites it: "One More Time" is about the game THIS call is
        // replacing, not the one it's about to create.
        val previousGame = _game.value

        val setupState = setupForm.setup.value
        if (setupState.duplicateNameSlots(defaultName).isNotEmpty()) return false
        val playerConfigs = setupForm.playerConfigs(defaultName)
        viewModelScope.launch { setupState.saveTo(settingsRepository) }
        // GameEngine.newGame leaves Extended Scores off in a mode that doesn't allow it, whatever the switch says - the
        // switch keeps the player's pick for the next mode.
        beginGame(
            GameEngine.newGame(playerConfigs, setupState.gameMode, setupState.turnTimer, setupState.rollModifiers, setupState.extendedScores, setupState.activeUnluckyDice),
            startedAtLocalHour = localHour(),
            previousGame = previousGame,
        )

        return true
    }

    /**
     * The game [startGame] would start from the form now, its players unnamed - for [GameWarmUp] to draw. Starts,
     * saves and unlocks nothing.
     */
    fun previewGame(): GameState {
        val setupState = setupForm.setup.value
        return GameEngine.newGame(
            setupForm.unnamedPlayerConfigs(),
            setupState.gameMode,
            setupState.turnTimer,
            setupState.rollModifiers,
            setupState.extendedScores,
            setupState.activeUnluckyDice,
        )
    }

    /** Loads a previously in-progress game, if any. Returns whether one was found and resumed. */
    suspend fun resumeGame(): Boolean {
        val loaded = inProgressGameRepository.load() ?: return false
        beginGame(
            loaded.copy(turnSecondsLeft = null, undoneCategory = null, undoSnapshot = null),
            resumedSecondsLeft = loaded.turnSecondsLeft,
            resumedUndo = loaded.undoneCategory,
            resumedUndoSnapshot = loaded.undoSnapshot,
        )
        unlockAchievements(setOf(Achievement.LETS_FINISH_THIS))
        return true
    }

    /**
     * Puts [state] on the table as a game starting or resuming, with no cheat active and nothing tracked towards an
     * achievement yet - [resumedSecondsLeft] is what a resumed turn's timer starts from, [resumedUndo] the box an undo
     * in it took back, [resumedUndoSnapshot] what its Undo goes back to (nothing to undo otherwise), and
     * [startedAtLocalHour] the hour on the phone's clock a new game started at (null for one resumed). A new game's
     * achievements are checked straight away: "Full Table" is settled the moment four seats are taken.
     */
    private fun beginGame(
        state: GameState,
        resumedSecondsLeft: Int? = null,
        resumedUndo: ScoreCategory? = null,
        resumedUndoSnapshot: GameState? = null,
        startedAtLocalHour: Int? = null,
        previousGame: GameState? = null,
    ) {
        // Before the state goes on the table, which saves it with whatever there is to undo.
        setUndoSnapshot(resumedUndoSnapshot)
        resetSuperuserMode()
        // The box an undo would take back is the one the last score went in.
        tracker.startGame(resumedUndo, resumedLastCommitted = resumedUndoSnapshot?.let { state.players.getOrNull(it.currentPlayerIndex)?.lastScoredCategory })
        this.resumedSecondsLeft = resumedSecondsLeft
        applyGameState(state)
        this.resumedSecondsLeft = null
        prepareHardCpus()
        checkInProgressAchievements()
        checkGameStartAchievements(startedAtLocalHour, previousGame)
    }

    /**
     * A human's cup tap or phone shake: the cup shakes for [GamePacing.cupShakeMillis] - the same wait a CPU's roll takes, and
     * paused the same way (see [setForeground]) - then [rollDice]. Ignored unless it's a human's turn with a roll left
     * and no shake already under way. The shake belongs to the turn it started in: one whose turn is scored first (the
     * turn timer, an undo) is dropped rather than rolling for the next player.
     */
    fun shakeAndRoll() {
        val state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN || state.rollsRemaining <= 0 || tapRollJob?.isActive == true) return
        if (tutorial != null && currentTutorialStep !is TutorialStep.Roll) return
        tapRollJob = viewModelScope.launch {
            _tapRolling.value = true
            try {
                pausableDelay(pacing.cupShakeMillis)
                rollDice()
            } finally {
                _tapRolling.value = false
            }
        }
    }

    // Not undoable: rolling has no scoring consequence of its own to undo - only a committed
    // score does (see commitScore below).
    fun rollDice() {
        val state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN) return
        // A roll can arrive after the turn it was tapped for has gone: the cup shakes before the
        // roll lands, and Undo in that window restores the previous turn - with no rolls left when
        // a turn has only one.
        if (state.rollsRemaining <= 0) return

        // Only player 1 - "You" - earns achievements; another human seat still plays normally
        // below, it just doesn't feed any of the tracking that leads to one.
        val isPlayerOneTurn = state.isPlayerOneTurn
        // Everything this roll earns is held back until the dice have landed - see heldBackRollUnlocks.
        if (isPlayerOneTurn) heldBackRollUnlocks = mutableSetOf()

        val rolled = performRoll(state)
        if (isPlayerOneTurn) {
            unlockAchievements(tracker.onRoll(state, rolled))
            announceRollAchievementsOnceLanded(
                // Greenfingers blooms on a roll, not a score: on the game's very last roll, before its last box is filled.
                bloomed = growsFlowerpot() && _game.value?.players?.firstOrNull()?.hasGrownSunflower == true,
            )
        }
    }

    /**
     * Unlocks from the roll being processed in [rollDice], held back instead of announced - null
     * outside it. The checks run the instant the roll is published, while the dice are still
     * tossing onto the mat, so a banner for "Roll a 5x on the first roll" would pop up before the
     * player has seen the 5x. The checks themselves (and all their per-turn tracking) still run
     * immediately, against the state as it was rolled; only the announcement waits.
     */
    private var heldBackRollUnlocks: MutableSet<Achievement>? = null

    /** Announces what [heldBackRollUnlocks] collected once the toss has finished (immediately with no toss). */
    private fun announceRollAchievementsOnceLanded(bloomed: Boolean) {
        val earned = heldBackRollUnlocks.orEmpty().toSet()
        heldBackRollUnlocks = null
        if (earned.isEmpty() && !bloomed) return
        viewModelScope.launch {
            pausableDelay(pacing.diceTossMillis)
            unlockAchievements(earned)
            if (bloomed) checkInProgressAchievements()
        }
    }

    // Not undoable, same reasoning as rollDice: holding/unholding just selects what a future roll
    // will touch, it doesn't itself score anything.
    fun toggleHold(dieIndex: Int) {
        // A tap that lands after its turn has moved on - the timer ran out, or a box was scored, in the same frame - holds
        // nothing: the dice can only be held once rolled.
        if (_game.value?.phase != TurnPhase.ROLLED) return
        // A die locked by Unlucky Dice can't be held, and a tap on it is no hold-or-release to track.
        if (_game.value?.dice?.getOrNull(dieIndex)?.isUnlucky == true) return
        if (tutorial != null) {
            holdForTutorial(dieIndex)
            return
        }
        trackSuperuserSequence(dieIndex)
        val state = _game.value
        val wasHeld = state?.dice?.getOrNull(dieIndex)?.isHeld == true
        // Only player 1 - "You" - earns achievements; another human seat can still toggle holds,
        // it just doesn't feed Achievement tracking.
        val isPlayerOneTurn = state?.isPlayerOneTurn == true

        onHumanAction(undoable = false) { GameEngine.toggleHold(it, dieIndex) }

        if (isPlayerOneTurn) _game.value?.let { unlockAchievements(tracker.onHold(dieIndex, wasHeld, it)) }
    }

    /** A hold in the tutorial: only one of the step's dice, never a release, and on once they're all held. */
    private fun holdForTutorial(dieIndex: Int) {
        val step = currentTutorialStep as? TutorialStep.Hold ?: return
        if (dieIndex !in step.dice || _game.value?.dice?.getOrNull(dieIndex)?.isHeld != false) return
        onHumanAction(undoable = false) { GameEngine.toggleHold(it, dieIndex) }
        if (step.dice.all { _game.value?.dice?.getOrNull(it)?.isHeld == true }) advanceTutorial()
    }

    /** One cycle step (see [GameEngine.cycleDieValue]), called once per second while a held die is
     * pressed in superuser mode. Superuser-mode-only; a no-op otherwise (also guards the die
     * actually being held and it being the human's turn, in case a stale call lands after either
     * stops being true - e.g. the turn ended mid-press). */
    fun cycleHeldDieValue(dieIndex: Int) {
        if (!isDebugBuild || !_superuserModeActive.value) return
        val state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN) return
        if (state.dice.getOrNull(dieIndex)?.isHeld != true) return
        applyGameState(GameEngine.cycleDieValue(state, dieIndex), checkForAiTurn = false)
    }

    // The only undoable action: committing a category is the only one of the three human actions
    // that actually records a score. Committing always ends the current player's turn (advanceTurn
    // in GameEngine), so this snapshot is still live when the NEXT player's turn opens: until that
    // player rolls (performRoll clears it), "Undo" reaches back into the previous player's
    // already-finished turn.
    fun commitScore(category: ScoreCategory) {
        val state = _game.value ?: return
        // A tap that lands after its turn has moved on - the timer ran out, or another box was scored, in the same frame -
        // scores nothing: not before a roll, and not in a box the dice can't go in.
        if (state.phase != TurnPhase.ROLLED) return
        val ready = HandCompletion.readyToScore(state, category) ?: return
        if (tutorial != null) {
            if ((currentTutorialStep as? TutorialStep.Score)?.category != category || state.currentPlayer?.type != PlayerType.HUMAN) return
            advanceTutorial()
        }
        if (ready === state) {
            finishCommit(category, undoTo = state)
            return
        }
        // A hand completed for the box (see HandCompletion.readyToScore) is shown as held, and scored in the same tap.
        if (state.currentPlayer?.type != PlayerType.HUMAN) return
        applyGameState(ready, checkForAiTurn = false)
        finishCommit(category, undoTo = state)
    }

    private fun finishCommit(category: ScoreCategory, undoTo: GameState) {
        // Only player 1 - "You" - earns achievements; another human seat can still commit a score
        // normally, it just doesn't feed any Achievement tracking. Read before the commit below,
        // which ends this player's turn and advances currentPlayerIndex to the next seat.
        val isPlayerOneTurn = _game.value?.isPlayerOneTurn == true

        if (isPlayerOneTurn) _game.value?.let { unlockAchievements(tracker.onCommit(it, category)) }

        // Undo returns to the turn as it was before any hand was completed for the player.
        val committing = _game.value ?: return
        if (committing.currentPlayer?.type != PlayerType.HUMAN) return
        // Noted before the commit is applied: a commit that ends the game clears the tracker's undo memory (see
        // AchievementTracker.onGameOver), and an undo into the game's last box still counts. Announced after, as before.
        val committedUnlocks = tracker.onCommitted(category, byPlayerOne = isPlayerOneTurn)
        setUndoSnapshot(undoTo)
        applyGameState(GameEngine.commitScore(committing, category))

        unlockAchievements(committedUnlocks)
        // Only player 1's own commits are checked: every mid-game achievement reads player 1's
        // scorecard, which nobody else's turn can change.
        if (isPlayerOneTurn) checkInProgressAchievements()
    }

    /** Reverts just the most recent human move, if there is one to undo. Cancels any pending AI turn it would have triggered. */
    fun undo() {
        val snapshot = undoSnapshot ?: return
        if (tutorial != null) {
            if (currentTutorialStep !is TutorialStep.Undo) return
            advanceTutorial()
        }
        tracker.onUndo()
        aiTurnJob?.cancel()
        aiTurnJob = null
        _aiRolling.value = false
        cancelTurnTimer()
        setUndoSnapshot(null)
        applyGameState(snapshot, checkForAiTurn = false)
    }

    /** A tap on the dice cup once a turn's last roll is already spent - it does nothing for the
     * game, but it's counted anyway for [Achievement.NO_MORE_ROLLS]. */
    fun tapCupWithNoRollsLeft() {
        unlockAchievements(tracker.onCupTapWithNoRollsLeft(_game.value ?: return))
    }

    /** A phone-shake roll actually happened - see [ShakeDetectorEffect]/[Achievement
     * .SHAKEN_NOT_TAPPED]. Only the achievement lives here: the roll itself is still driven
     * through the same [rollDice] call a cup tap uses, so this is purely a one-shot unlock,
     * same shape as [tapCupWithNoRollsLeft]. */
    fun onShakeRollDetected() {
        unlockAchievements(setOf(Achievement.SHAKEN_NOT_TAPPED))
    }

    /** The Top Hat cup's rabbit just peeked out - see [AchievementTracker.onRabbitSeen]. */
    fun onRabbitSeen() {
        unlockAchievements(tracker.onRabbitSeen(_game.value ?: return))
    }

    /**
     * Every roll in the game - a human's via [rollDice],
     * and an AI's via [maybeStartAiTurn] - so they all land the same way. Not undoable, like
     * rolling always has been: only a committed score is. Returns the rolled state for the AI loop,
     * which carries its own copy of it.
     */
    private fun performRoll(state: GameState, checkForAiTurn: Boolean = true): GameState {
        // The golden egg: a human's roll only, with the Egg dice and the Chicken cup both in use.
        val goldenOneIn = GameEngine.GOLDEN_EGG_ONE_IN.takeIf { state.currentPlayer?.type == PlayerType.HUMAN && showsGoldenEggs(state) && tutorial == null }
        val step = currentTutorialStep
        val scripted = (step as? TutorialStep.Roll)?.let { TutorialDice(it.dice, state.dice.map(Die::isHeld)) }
        val rolled = GameEngine.rollDice(state, scripted ?: random, goldenOneIn)
        if (step is TutorialStep.Roll) advanceTutorial(shownAfterMillis = pacing.diceTossMillis)
        setUndoSnapshot(null)
        applyGameState(rolled, checkForAiTurn)
        return rolled
    }

    /**
     * Whether the table is set up for [Achievement.EGGCELLENT_DISCOVERY]: the Egg dice in any colour but gold, and the
     * Chicken cup, picked. Earned or not, so the golden egg keeps turning up after it. Any mode or modifier, except one
     * whose dice have more faces than the Egg art (D20's), where the table draws a D20 instead.
     */
    private fun showsGoldenEggs(state: GameState): Boolean {
        val theme = tableSettings.value?.visualTheme ?: return false
        val dice = theme.diceStyle
        return dice is EggDiceStyle && dice.id != GoldenEggDiceStyle.id && theme.diceCupStyle is ChickenDiceCupStyle &&
            state.gameMode.dieValues.last <= PIP_FACES
    }

    /** Whether the cup picked is the Flowerpot that grows its plant, not one of its fixed-stage pots - [Achievement.GREENFINGERS]'s cup. */
    private fun growsFlowerpot(): Boolean = (tableSettings.value?.visualTheme?.diceCupStyle as? FlowerpotDiceCupStyle)?.growsPlant == true

    private fun onHumanAction(undoable: Boolean = true, transform: (GameState) -> GameState) {
        val state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN) return
        setUndoSnapshot(if (undoable) state else null)
        applyGameState(transform(state))
    }

    private fun setUndoSnapshot(snapshot: GameState?) {
        // In the tutorial, only a score its next step takes back with Undo.
        undoSnapshot = snapshot.takeIf { tutorial == null || currentTutorialStep is TutorialStep.Undo }
        _canUndo.value = undoSnapshot != null
    }

    /**
     * Reports every hold/release, before it's applied, to [holdSequence] - piggybacking on ordinary play rather than a
     * separate input mode, so the cheat stays hidden until it triggers. Tracked in every build, on any seat's turn: the
     * sequence earns [Achievement.TIME_WASTING] for player 1 ([AchievementTracker.onHoldSequenceDone]) whether or not it
     * goes on to do anything. Only the cheat itself - [_superuserModeActive] and the toast - stays behind [isDebugBuild],
     * the only gate it needs: [cycleHeldDieValue] already requires [_superuserModeActive], and every UI entry point
     * (DiceTray's onCycleValue wiring) is itself conditional on that same flag.
     */
    private fun trackSuperuserSequence(dieIndex: Int) {
        if (_superuserModeActive.value) return
        val state = _game.value ?: return
        val holding = state.dice.getOrNull(dieIndex)?.isHeld == false
        if (!holdSequence.onToggle(dieIndex, holding, state.dice.size)) return
        unlockAchievements(tracker.onHoldSequenceDone(state))
        if (isDebugBuild) {
            _superuserModeActive.value = true
            _toastMessages.trySend("Superuser mode activated!") // i18n: not translated - a debug-build developer toast
        }
    }

    private fun resetSuperuserMode() {
        _superuserModeActive.value = false
        holdSequence.reset()
    }

    private fun applyGameState(newState: GameState, checkForAiTurn: Boolean = true) {
        val wasGameOver = _game.value?.isGameOver ?: false
        // A shake still under way belongs to a turn that's now been scored (or undone): it's dropped - see shakeAndRoll.
        if (newState.scoresEntered != _game.value?.scoresEntered) tapRollJob?.cancel()
        _game.value = newState
        persistInProgressGame(newState)
        tracker.onState(newState)
        if (!wasGameOver && newState.isGameOver) {
            tracker.onGameOver()
            finishGame(newState)
        }
        syncTurnTimer(newState)
        if (checkForAiTurn) maybeStartAiTurn()
    }

    private var turnTimerJob: Job? = null

    /** The total number of turns scored across every player as of the turn [turnTimerJob] is
     * currently counting down for - a turn always ends by scoring exactly one slot, so this
     * strictly increases by one turn to turn and uniquely identifies "a new turn started" even in
     * a single-player game, where [GameState.currentPlayerIndex] alone would stay 0 forever. A
     * state update that doesn't actually change whose turn it is (e.g. a superuser die cycle,
     * rolling, holding) leaves it unchanged, so the countdown isn't restarted mid-turn. */
    private var turnTimerTurnSequence: Int? = null

    /**
     * Starts, restarts or cancels the per-turn countdown so it always matches [newState]: running
     * on any seat's turn - human or AI - only while [GameState.turnSeconds] allows one, and reset to
     * the full duration whenever the turn it's counting down for changes (a new turn starting, or
     * the previous turn reappearing after [undo]). An AI is expected to finish well within the
     * limit - [autoScoreOnTimeout] forfeits its turn the same as a human's if it doesn't.
     */
    private fun syncTurnTimer(newState: GameState) {
        val seconds = newState.turnSeconds
        if (newState.isGameOver || seconds == null) {
            cancelTurnTimer()
            return
        }
        val turnSequence = newState.players.sumOf { it.turnsTaken }
        if (turnTimerTurnSequence == turnSequence && turnTimerJob?.isActive == true) return
        turnTimerTurnSequence = turnSequence
        startTurnTimer(seconds, resumedSecondsLeft?.coerceIn(1, seconds) ?: seconds)
    }

    private fun startTurnTimer(totalSeconds: Int, startSeconds: Int = totalSeconds) {
        turnTimerJob?.cancel()
        _turnSecondsRemaining.value = startSeconds
        turnTimerJob = viewModelScope.launch {
            var remaining = startSeconds
            while (remaining > 0) {
                pausableDelay(1_000L)
                remaining--
                _turnSecondsRemaining.value = remaining
            }
            autoScoreOnTimeout()
        }
    }

    private fun cancelTurnTimer() {
        turnTimerJob?.cancel()
        turnTimerJob = null
        turnTimerTurnSequence = null
        _turnSecondsRemaining.value = null
    }

    /**
     * The turn timer running out ([GameEngine.timeOut]). Applies equally to an AI
     * seat that's taken too long to decide - Hard's exhaustive search is the only realistic way
     * this fires for one - cancelling its in-flight turn job first so it can't keep acting after
     * being timed out from under it.
     */
    private fun autoScoreOnTimeout() {
        val state = _game.value ?: return
        if (state.currentPlayer?.type == PlayerType.AI) {
            aiTurnJob?.cancel()
            aiTurnJob = null
            _aiRolling.value = false
        }
        // Only player 1 - "You" - earns achievements; another human seat can still be timed out,
        // it just doesn't feed Achievement tracking.
        val isPlayerOneTurn = state.isPlayerOneTurn

        val scored = GameEngine.timeOut(state) ?: return
        // Counted before the state goes out: a timeout on the game's last turn ends it, and the results' achievements read these.
        if (isPlayerOneTurn) tracker.onTimeout(scored)
        setUndoSnapshot(null)
        applyGameState(scored)
    }

    /**
     * The one place a finished game's side effects happen, in order: the leaderboard's previous
     * high score is read *first*, because "New Personal Best" compares against the board as it was
     * before this game's own rows were added to it.
     */
    private fun finishGame(state: GameState) {
        // The tutorial is played for practice: it records nothing, and doesn't count as a first game.
        if (tutorial != null) return
        // Written once: every game after the first finds the modes already unlocked.
        if (setupForm.unlockModes()) viewModelScope.launch { settingsRepository.setFirstGameCompleted() }
        viewModelScope.launch {
            // Guarded so a leaderboard problem costs only the leaderboard: before this, a throw
            // from either call took the achievement evaluation down with it, silently.
            // Both reads happen BEFORE the insert - "New Personal Best" compares against the board
            // as it was, and the score-collection bands need the before state to measure progress
            // against (the engine adds this game's own scores itself).
            // By name, and only player 1's: someone else's high score at the same table must not
            // count as beating YOUR best, now that achievements are player 1's alone.
            val playerOneName = state.players.firstOrNull()?.takeIf { it.type == PlayerType.HUMAN }?.name
            val previousBestScore = playerOneName?.let { name ->
                orNullOnFailure { scoreRepository.bestScoreForPlayer(name) }
            }
            val previousLeaderboard = orNullOnFailure { scoreRepository.leaderboardTotals() }
                ?: LeaderboardTotals()
            orNullOnFailure { persistHumanScores(state) }
            // Always records something (Games Played counts every game), so it also asks for the online sync that
            // posts this game's score - after the rows above are written.
            recordEndOfGameAchievements(state, previousBestScore, previousLeaderboard)
        }
    }

    /**
     * Everything decided the moment a game begins - checked from [startGame]/[resumeGame], not the
     * end: whatever's true here was already true before a single die was rolled, so there's no
     * reason to make the player finish a game to hear about it. Delegates the actual "what counts
     * as earned" decision to [AchievementEngine.evaluateAtGameStart], same as [checkInProgressAchievements]/
     * [recordEndOfGameAchievements] do for their own moments - this is only responsible for
     * gathering [GameStartContext]'s inputs (async, since the style ids live in
     * [SettingsRepository]'s DataStore, not [_game]) and persisting/announcing the result. A future
     * game-start achievement is a new field on [GameStartContext] and a line in that engine
     * function, not a new method here.
     */
    private fun checkGameStartAchievements(startedAtLocalHour: Int?, previousGame: GameState?) {
        if (tutorial != null) return
        val game = _game.value ?: return
        viewModelScope.launch {
            // A pick whose style is still locked can't count, since the default is drawn in its place.
            val playedNonDefaultStyle = savedStylesFlow(settingsRepository, achievementsRepository, ownsAllStyles).first().hasNonDefaultStyle
            val context = GameStartContext(
                playedNonDefaultStyle = playedNonDefaultStyle,
                game = game,
                previousGame = previousGame,
                startedAtLocalHour = startedAtLocalHour,
            )
            achievementUnlocker.apply { state -> AchievementEngine.evaluateAtGameStart(context, state, nowEpochMillis()) }
        }
    }

    /**
     * Checks what the game has earned **so far**, after every scored category - a maxed-out Sixes
     * box or a second 5x is worth saying so at the moment it happens, not on the results
     * screen twenty minutes later.
     *
     * Skipped once the game is over, where [finishGame] evaluates the same things (and more) -
     * running both would race to unlock the same achievement and pop it twice.
     */
    private fun checkInProgressAchievements() {
        val state = _game.value ?: return
        if (state.isGameOver || tutorial != null) return

        viewModelScope.launch {
            achievementUnlocker.apply { stored -> AchievementEngine.evaluateInProgress(state, stored, nowEpochMillis(), tracker.timedOutZeroes, growsFlowerpot()) }
        }
    }

    private suspend fun recordEndOfGameAchievements(
        state: GameState,
        previousBestScore: Int?,
        previousLeaderboard: LeaderboardTotals,
    ) {
        val context = tracker.endOfGameContext(previousBestScore, previousLeaderboard).copy(growingFlowerpotInPlay = growsFlowerpot())
        achievementUnlocker.apply { stored -> AchievementEngine.evaluate(state, context, stored, nowEpochMillis()) }
    }

    private fun unlockAchievements(achievements: Set<Achievement>) {
        // Nothing in the tutorial earns an achievement: the dice are the script's.
        if (achievements.isEmpty() || tutorial != null) return
        heldBackRollUnlocks?.let {
            it += achievements
            return
        }
        viewModelScope.launch { achievementUnlocker.unlock(achievements) }
    }

    // Suspending rather than launching its own coroutine: finishGame sequences this against the
    // leaderboard read that has to happen before it.
    private suspend fun persistHumanScores(state: GameState) {
        val results = state.players.withIndex().filter { (_, player) -> player.type == PlayerType.HUMAN }.map { (index, player) ->
            PlayerResult(
                playerName = player.name,
                // Feeds the leaderboard's own tie-break ordering (see TieBreak.kt).
                stats = player.toTieBreakStats(),
                // Null for a solo game: nobody to beat.
                won = state.wonBy(index),
                // Index 0 is always the primary player ("You") - see AchievementEngine's class doc.
                isPrimaryPlayer = index == 0,
            )
        }
        // Every human's row in one write.
        scoreRepository.recordGame(
            results,
            gameMode = state.gameMode,
            playedUnmodified = !state.hasModifiers,
        )
    }

    // Queued and conflated by the repository - see InProgressGameRepository.
    private fun persistInProgressGame(state: GameState) {
        // The tutorial is never saved: Continue stays on the player's own game.
        if (tutorial != null) return
        if (state.isGameOver) inProgressGameRepository.clear() else inProgressGameRepository.save(savedCopy(state))
    }

    /** [state] as it's saved: with the box an undo took back and what Undo goes back to, which the live game doesn't carry -
     * see [GameState.undoneCategory] and [GameState.undoSnapshot]. */
    private fun savedCopy(state: GameState): GameState = state.copy(undoneCategory = tracker.undoneCategory, undoSnapshot = undoSnapshot)

    private var aiTurnJob: Job? = null

    /**
     * Gets a Hard CPU's first decision ready while the humans before it play: its mode's hands scored
     * (most of a second for Tricolour's coloured dice - see [AiTurnPlayer.prepareHard]) and, in
     * Standard, the perfect-play table read. Off the main thread, like the decisions themselves.
     */
    private fun prepareHardCpus() {
        val state = _game.value ?: return
        if (state.players.none { it.type == PlayerType.AI && it.difficulty == Difficulty.HARD }) return
        viewModelScope.launch(aiDispatcher) {
            AiTurnPlayer.prepareHard(state.gameMode, state.extendedScores)
            if (state.gameMode.playsByPerfectPlayTable(state.extendedScores)) standardPerfectPlay?.invoke()
        }
    }

    /**
     * Flips each of [indices]' holds in turn, left to right, [AI_HOLD_STEP_MS] apart, publishing every
     * one - a CPU reaching for its dice one by one. Returns the state with all of them flipped.
     */
    private suspend fun toggleHoldsOneByOne(state: GameState, indices: List<Int>): GameState {
        var current = state
        for ((step, index) in indices.withIndex()) {
            if (step > 0) pausableDelay(AI_HOLD_STEP_MS)
            current = GameEngine.toggleHold(current, index)
            applyGameState(current, checkForAiTurn = false)
        }
        return current
    }

    /**
     * The CPU's hold changes: every release, then every new hold. One die at a time while the dice are animated
     * ([toggleHoldsOneByOne]), as a hand would; with them not animated ([GamePacing.diceAnimated] off) that stepping is just
     * motion, so every change lands at once, published together. Returns the state with all of them made.
     */
    private suspend fun changeHolds(state: GameState, toRelease: List<Int>, toHold: List<Int>): GameState {
        if (!pacing.diceAnimated) {
            val current = (toRelease + toHold).fold(state, GameEngine::toggleHold)
            if (current !== state) applyGameState(current, checkForAiTurn = false)
            return current
        }
        var current = toggleHoldsOneByOne(state, toRelease)
        if (toRelease.isNotEmpty() && toHold.isNotEmpty()) pausableDelay(AI_RELEASE_TO_HOLD_GAP_MS)
        current = toggleHoldsOneByOne(current, toHold)
        return current
    }

    /** The perfect-play table if [state]'s current player is a Hard CPU playing Standard - see [standardPerfectPlay]. */
    private suspend fun perfectPlayFor(state: GameState): StandardPerfectPlayTable? =
        if (AiTurnPlayer.usesPerfectPlayTable(state)) standardPerfectPlay?.invoke() else null

    /**
     * Starts a job that plays out every AI turn in a row from here (not just one) - e.g. with a
     * human followed by three AI, this single job carries players 2, 3 and 4 through their whole
     * turns before handing back to the human. Looping in place rather than recursively re-launching
     * itself matters: `aiTurnJob` isn't reassigned - and so isn't `isActive` - until this whole
     * coroutine returns, so a self re-launch from its own tail would see itself as still active and
     * bail out, silently dropping every AI turn after the first.
     */
    private fun maybeStartAiTurn() {
        val state = _game.value ?: return
        if (state.isGameOver) return
        if (state.currentPlayer?.type != PlayerType.AI) return
        if (aiTurnJob?.isActive == true) return

        aiTurnJob = viewModelScope.launch {
            var current = state
            while (!current.isGameOver && current.currentPlayer?.type == PlayerType.AI) {
                val perfectPlay = perfectPlayFor(current)
                while (current.rollsRemaining > 0) {
                    // The delay doubles as the cup's shake animation window - the same CUP_SHAKE_MILLIS
                    // a human's tap shakes for, whatever the difficulty, and the roll itself goes
                    // through the same performRoll. aiRolling is true for the shake's whole span
                    // and false the moment the roll (never the hold decision below - see that
                    // comment) is published.
                    _aiRolling.value = true
                    try {
                        pausableDelay(pacing.cupShakeMillis)
                        current = performRoll(current, checkForAiTurn = false)
                    } finally {
                        _aiRolling.value = false
                    }
                    // Nothing left to decide on the turn's last roll - there's no further reroll to
                    // hold dice FOR.
                    val rolled = current
                    val holdsChoice = if (rolled.rollsRemaining > 0) async(aiDispatcher) { AiTurnPlayer.chooseHolds(rolled, perfectPlay) } else null

                    // The roll is published but the dice are still tossing onto the mat: let them settle
                    // before holding, rerolling or scoring, as a human has to. The hold choice above is
                    // already being worked out meanwhile, so the toss covers up to its own length of thinking.
                    pausableDelay(pacing.diceTossMillis)
                    if (holdsChoice == null) break

                    // Deliberately outside the block above: choosing what to hold happens once
                    // the cup has already stopped shaking and this roll's dice are already on
                    // screen, never while the shake itself is still playing. Hard's hold choice
                    // is an exhaustive search over every 32 hold/reroll subsets, each averaged
                    // over every possible reroll outcome - expensive enough that computing it
                    // during the shake (as this used to) stretched the shake's own on-screen
                    // duration out past Easy/Medium's, whose choices are next to free. Now every
                    // difficulty's shake is the same fixed length, and "thinking" is just a
                    // static pause with nothing animating - however long it takes, only the pause
                    // before the dice's held state updates changes, not any animation. It runs during
                    // the toss, so the pause is only whatever it takes beyond the toss itself.
                    val holds = holdsChoice.await()
                    // Done rolling: stop before the holds are applied, as showing every die held just before scoring
                    // is noise. "x$rollsRemaining" on the cup still shows the rolls left - see AiTurnPlayer.stopsRolling.
                    if (AiTurnPlayer.stopsRolling(current, holds)) break

                    // A human's beat to take in the settled dice before reaching for them - with the
                    // choice worked out during the toss, the holds would otherwise land the instant the
                    // dice stop. Scoring gets the same beat from AI_STEP_DELAY_MS below.
                    pausableDelay(AI_REACTION_DELAY_MS)
                    // One die at a time, as a hand would: every release first, then every new hold, each
                    // published as it happens - all at once, a swap of held dice read as a jump cut.
                    val toRelease = current.dice.indices.filter { current.dice[it].isHeld && it !in holds }
                    val toHold = current.dice.indices.filter { !current.dice[it].isHeld && it in holds }
                    val releasedAny = toRelease.isNotEmpty()
                    setUndoSnapshot(null)
                    current = changeHolds(current, toRelease, toHold)

                    // A beat with the cup settled and the result visible before the next roll's
                    // shake starts - without it, back-to-back rolls (routine for Easy, which never
                    // holds anything and so never gets to skip a roll) read as one continuous blur
                    // rather than distinct rolls. Only between rolls: the very first roll and the
                    // score are already paced by the shake above and AI_STEP_DELAY_MS below. A die just
                    // let go of gets longer: it drops from its slot onto the mat, and the shake sweeps
                    // every loose die off the mat - so after only ROLL_GAP_MS it would be gone again
                    // almost as soon as it landed, reading as a die vanishing rather than being released.
                    // With the dice not animated, a released die is simply back on the mat: nothing to watch land.
                    pausableDelay(if (releasedAny && pacing.diceAnimated) RELEASE_GAP_MS else ROLL_GAP_MS)
                }
                // Where only held dice score, the CPU picks out its hand now it's done rolling - held as a
                // human would, so the hand it scores is the one in its slots.
                if (current.gameMode.scoresHeldDiceOnly) {
                    val toHold = current
                    val handChoice = async(aiDispatcher) { AiTurnPlayer.chooseHand(toHold, perfectPlay) }
                    pausableDelay(AI_REACTION_DELAY_MS)
                    val hand = handChoice.await()
                    setUndoSnapshot(null)
                    current = changeHolds(
                        current,
                        toRelease = current.dice.indices.filter { current.dice[it].isHeld && it !in hand },
                        toHold = current.dice.indices.filter { !current.dice[it].isHeld && it in hand },
                    )
                }
                // Also off the main thread: Hard's category choice compares against
                // HardCpu.CATEGORY_BASELINES, a `by lazy` average-over-every-outcome computed once per
                // process on whichever call touches it first - same cost/rationale as the hold
                // choice above. Worked out during the pause before scoring, not after it.
                val toScore = current
                val categoryChoice = async(aiDispatcher) { AiTurnPlayer.chooseCategory(toScore, perfectPlay) }
                pausableDelay(AI_STEP_DELAY_MS)
                val category = categoryChoice.await()
                current = GameEngine.commitScore(current, category)
                setUndoSnapshot(null)
                applyGameState(current, checkForAiTurn = false)
            }
        }
    }

    companion object {
        /** The AI's pause, dice settled, before it scores. Its rolls shake for CUP_SHAKE_MILLIS, same as a tap's. */
        private const val AI_STEP_DELAY_MS = 250L

        /** The AI's pause, dice settled, before it holds any of them for its next roll. */
        private const val AI_REACTION_DELAY_MS = 200L

        /** Between one die the AI holds (or releases) and the next, when it changes several. */
        private const val AI_HOLD_STEP_MS = 50L

        /** Between the last die the AI releases and the first it then holds in their place. */
        private const val AI_RELEASE_TO_HOLD_GAP_MS = 125L

        /** Pause between one roll settling and the next one's shake starting, within the same AI turn. */
        private const val ROLL_GAP_MS = 100L

        /** [ROLL_GAP_MS] when the AI has just let go of a die: long enough to see it land on the mat before the shake sweeps it up. */
        private const val RELEASE_GAP_MS = 500L

        /** Builds a [GameViewModel] backed by real Room/DataStore persistence. */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GameViewModel(
                    scoreRepository = container.scoreRepository,
                    settingsRepository = container.settingsRepository,
                    inProgressGameRepository = container.inProgressGameRepository,
                    achievementsRepository = container.achievementsRepository,
                    ownsAllStyles = container.ownsAllStyles,
                    achievementUnlocker = container.achievementUnlocker,
                    random = container.diceRandom,
                    isDebugBuild = container.buildInfo.isDebug,
                    standardPerfectPlay = container::standardPerfectPlayTable,
                )
            }
        }

        /** Builds a [GameViewModel] for [script]'s guided game: it reads the player's name and table, and writes nothing. */
        fun tutorialFactory(container: AppContainer, script: TutorialScript): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GameViewModel(
                    scoreRepository = container.scoreRepository,
                    settingsRepository = container.settingsRepository,
                    inProgressGameRepository = container.inProgressGameRepository,
                    achievementsRepository = container.achievementsRepository,
                    ownsAllStyles = container.ownsAllStyles,
                    achievementUnlocker = container.achievementUnlocker,
                    tutorial = script,
                )
            }
        }
    }
}
