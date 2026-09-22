package net.zodac.dicefive.ui.game

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.zodac.dicefive.BuildConfig
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.AppDatabase
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.AiNameGenerator
import net.zodac.dicefive.game.AiTurnPlayer
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.GameType
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory

/**
 * A configured-but-not-yet-started player slot on the setup screen. Slot 1
 * is always [PlayerType.HUMAN] (1P mode forces the user to be human).
 */
data class PlayerSetupSlot(
    val slot: Int,
    val type: PlayerType = PlayerType.HUMAN,
    val name: String = "Player $slot",
    val difficulty: Difficulty = Difficulty.MEDIUM,
)

data class GameSetupState(
    val playerCount: Int = 2,
    val playerSlots: List<PlayerSetupSlot> = (1..MAX_PLAYERS).map { PlayerSetupSlot(slot = it) },
    val gameType: GameType = GameType.CLASSIC,
) {
    companion object {
        const val MIN_PLAYERS = 1
        const val MAX_PLAYERS = 4
    }
}

/**
 * Owns both the pre-game setup form and the live [GameState] once started,
 * scoped to the "play" nav graph so [net.zodac.dicefive.ui.setup.GameSetupScreen]
 * and [GameScreen] share one instance across navigation.
 *
 * [scoreRepository], [settingsRepository] and [inProgressGameRepository] are
 * optional so this class stays constructible (and testable) on a plain JVM
 * with no Android `Context` - [factory] supplies the real ones. When
 * absent, remembered names, score history, and game resumption are
 * silently skipped.
 */
class GameViewModel(
    private val scoreRepository: ScoreRepository? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val inProgressGameRepository: InProgressGameRepository? = null,
) : ViewModel() {

    private val _setup = MutableStateFlow(GameSetupState())
    val setup: StateFlow<GameSetupState> = _setup.asStateFlow()

    private val _game = MutableStateFlow<GameState?>(null)
    val game: StateFlow<GameState?> = _game.asStateFlow()

    /** The state to restore if [undo] is called - the pre-commit snapshot of the most recent
     * scoring action only. Rolling and holding/unholding dice are pure exploration/selection with
     * no result of their own to undo; only commitScore actually changes the scorecard. */
    private var undoSnapshot: GameState? = null
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    val confirmBeforeLeavingGame: StateFlow<Boolean> = (settingsRepository?.confirmBeforeLeavingGame ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // A hidden cheat: hold, then unhold, all five dice in strict order (die 0's pair, then die
    // 1's, ... through die 4's) - on any turn, any player's. Once active for the rest of this
    // game, holding a finger on an already-held die cycles its face once a second - see
    // DiceTray's onCycleValue wiring and GameEngine.cycleDieValue.
    // superuserSequenceDieIndex/AwaitingUnhold track progress toward unlocking it; neither is
    // exposed, since nothing outside trackSuperuserSequence needs them.
    private val _superuserModeActive = MutableStateFlow(false)
    val superuserModeActive: StateFlow<Boolean> = _superuserModeActive.asStateFlow()
    // A Channel, not a SharedFlow: a SharedFlow only buffers for collectors already subscribed at
    // emission time, so a message sent before the UI's LaunchedEffect attaches (a real possibility
    // right after process restart) would be silently dropped. A Channel queues it regardless of
    // when the collector shows up, and delivers each message exactly once - the right semantics
    // for a one-shot toast rather than state to replay.
    private val _toastMessages = Channel<String>(Channel.BUFFERED)
    val toastMessages: Flow<String> = _toastMessages.receiveAsFlow()
    private var superuserSequenceDieIndex = 0
    private var superuserSequenceAwaitingUnhold = false

    init {
        settingsRepository?.let { repository ->
            viewModelScope.launch {
                for (slot in 1..GameSetupState.MAX_PLAYERS) {
                    val savedName = repository.playerNameFor(slot).first() ?: continue
                    updateSlot(slot) { it.copy(name = savedName) }
                }
                // Slot 1 is always Human, so its type is never saved/restored.
                for (slot in 2..GameSetupState.MAX_PLAYERS) {
                    val savedType = repository.playerTypeFor(slot).first() ?: continue
                    updateSlot(slot) { it.copy(type = savedType) }
                }
                repository.playerCount.first()?.let { savedCount -> setPlayerCount(savedCount) }
            }
        }
    }

    fun setPlayerCount(count: Int) {
        _setup.update { it.copy(playerCount = count.coerceIn(GameSetupState.MIN_PLAYERS, GameSetupState.MAX_PLAYERS)) }
    }

    fun setPlayerType(slot: Int, type: PlayerType) {
        require(slot != 1) { "Player 1 is always Human" }
        updateSlot(slot) { it.copy(type = type) }
    }

    fun setPlayerName(slot: Int, name: String) {
        updateSlot(slot) { it.copy(name = name) }
    }

    fun setPlayerDifficulty(slot: Int, difficulty: Difficulty) {
        updateSlot(slot) { it.copy(difficulty = difficulty) }
    }

    fun setGameType(gameType: GameType) {
        _setup.update { it.copy(gameType = gameType) }
    }

    /** Builds the initial [GameState] from the current setup form, generating AI names now. */
    fun startGame() {
        val setupState = _setup.value
        val activeSlots = setupState.playerSlots.take(setupState.playerCount)
        val aiNames = AiNameGenerator.generateNames(activeSlots.count { it.type == PlayerType.AI }).iterator()
        val playerConfigs = activeSlots.map { slot ->
            val name = when (slot.type) {
                PlayerType.HUMAN -> slot.name.ifBlank { "Player ${slot.slot}" }
                PlayerType.AI -> aiNames.next()
            }
            PlayerConfig(slot = slot.slot, type = slot.type, name = name, difficulty = slot.difficulty)
        }
        persistHumanNames(activeSlots)
        persistGameConfig(setupState.playerCount, activeSlots)
        setUndoSnapshot(null)
        resetSuperuserMode()
        applyGameState(GameEngine.newGame(playerConfigs, setupState.gameType))
    }

    /** Loads a previously in-progress game, if any. Returns whether one was found and resumed. */
    suspend fun resumeGame(): Boolean {
        val repository = inProgressGameRepository ?: return false
        val loaded = repository.load() ?: return false
        setUndoSnapshot(null)
        resetSuperuserMode()
        applyGameState(loaded)
        return true
    }

    // Not undoable: rolling has no scoring consequence of its own to undo - only a committed
    // score does (see commitScore below).
    fun rollDice() = onHumanAction(undoable = false) { GameEngine.rollDice(it) }

    // Not undoable, same reasoning as rollDice: holding/unholding just selects what a future roll
    // will touch, it doesn't itself score anything.
    fun toggleHold(dieIndex: Int) {
        trackSuperuserSequence(dieIndex)
        onHumanAction(undoable = false) { GameEngine.toggleHold(it, dieIndex) }
    }

    /** One cycle step (see [GameEngine.cycleDieValue]), called once per second while a held die is
     * pressed in superuser mode. Superuser-mode-only; a no-op otherwise (also guards the die
     * actually being held and it being the human's turn, in case a stale call lands after either
     * stops being true - e.g. the turn ended mid-press). */
    fun cycleHeldDieValue(dieIndex: Int) {
        if (!BuildConfig.DEBUG || !_superuserModeActive.value) return
        val state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN) return
        if (state.dice.getOrNull(dieIndex)?.isHeld != true) return
        applyGameState(GameEngine.cycleDieValue(state, dieIndex), checkForAiTurn = false)
    }

    // The only undoable action: committing a category is the only one of the three human actions
    // that actually records a score. Note the trade-off versus the old "not undoable" rule this
    // replaces: committing a score always ends the current player's turn (advanceTurn in
    // GameEngine), so this snapshot is still live when the NEXT player's turn opens - if nobody
    // has rolled or held anything yet (both now non-undoable and don't touch this snapshot),
    // "Undo" at that point reaches back into the previous player's already-finished turn. Revisit
    // this if that turns out to matter more in practice than being able to undo a bad category
    // pick.
    fun commitScore(category: ScoreCategory) = onHumanAction(undoable = true) { GameEngine.commitScore(it, category) }

    /** Reverts just the most recent human move, if there is one to undo. Cancels any pending AI turn it would have triggered. */
    fun undo() {
        val snapshot = undoSnapshot ?: return
        aiTurnJob?.cancel()
        aiTurnJob = null
        setUndoSnapshot(null)
        applyGameState(snapshot, checkForAiTurn = false)
    }

    private fun onHumanAction(undoable: Boolean = true, transform: (GameState) -> GameState) {
        val state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN) return
        setUndoSnapshot(if (undoable) state else null)
        applyGameState(transform(state))
    }

    private fun setUndoSnapshot(snapshot: GameState?) {
        undoSnapshot = snapshot
        _canUndo.value = snapshot != null
    }

    /**
     * Advances (or resets) progress toward the superuser-mode unlock sequence, called on every
     * hold/unhold BEFORE it's applied - piggybacking on ordinary play rather than a separate
     * input mode, so the cheat stays hidden until it triggers.
     *
     * Gated on [BuildConfig.DEBUG] - a release build's copy of this class has that constant
     * inlined to `false`, so this returns immediately and [_superuserModeActive] can never
     * become true no matter what a player does. That's the ONLY gate the whole feature needs:
     * [cycleHeldDieValue] already requires [_superuserModeActive], and every UI entry point
     * (DiceTray's onCycleValue wiring) is itself conditional on that same flag.
     */
    private fun trackSuperuserSequence(dieIndex: Int) {
        if (!BuildConfig.DEBUG) return
        if (_superuserModeActive.value) return
        val state = _game.value ?: return
        val willBeHeld = state.dice.getOrNull(dieIndex)?.isHeld == false
        val isExpectedStep = dieIndex == superuserSequenceDieIndex &&
            willBeHeld == !superuserSequenceAwaitingUnhold

        if (!isExpectedStep) {
            resetSuperuserSequence()
            // A toggle that happens to BE a valid opening move (holding die 0) restarts the
            // sequence from there, rather than also requiring an unrelated action first.
            if (dieIndex == 0 && willBeHeld) {
                superuserSequenceAwaitingUnhold = true
            }
            return
        }

        if (superuserSequenceAwaitingUnhold) {
            // This die's hold-then-unhold pair just completed.
            superuserSequenceDieIndex++
            superuserSequenceAwaitingUnhold = false
            if (superuserSequenceDieIndex == state.dice.size) {
                _superuserModeActive.value = true
                resetSuperuserSequence()
                _toastMessages.trySend("Superuser mode activated!")
            }
        } else {
            superuserSequenceAwaitingUnhold = true
        }
    }

    private fun resetSuperuserSequence() {
        superuserSequenceDieIndex = 0
        superuserSequenceAwaitingUnhold = false
    }

    private fun resetSuperuserMode() {
        _superuserModeActive.value = false
        resetSuperuserSequence()
    }

    private fun updateSlot(slot: Int, transform: (PlayerSetupSlot) -> PlayerSetupSlot) {
        _setup.update { state ->
            state.copy(playerSlots = state.playerSlots.map { if (it.slot == slot) transform(it) else it })
        }
    }

    private fun applyGameState(newState: GameState, checkForAiTurn: Boolean = true) {
        val wasGameOver = _game.value?.isGameOver ?: false
        _game.value = newState
        persistInProgressGame(newState)
        if (!wasGameOver && newState.isGameOver) {
            persistHumanScores(newState)
        }
        if (checkForAiTurn) maybeStartAiTurn()
    }

    private fun persistHumanNames(slots: List<PlayerSetupSlot>) {
        val repository = settingsRepository ?: return
        viewModelScope.launch {
            for (slot in slots) {
                if (slot.type == PlayerType.HUMAN) {
                    repository.setPlayerName(slot.slot, slot.name.ifBlank { "Player ${slot.slot}" })
                }
            }
        }
    }

    private fun persistGameConfig(playerCount: Int, slots: List<PlayerSetupSlot>) {
        val repository = settingsRepository ?: return
        viewModelScope.launch {
            repository.setPlayerCount(playerCount)
            for (slot in slots) {
                // Slot 1 is always Human, so its type isn't worth persisting.
                if (slot.slot != 1) repository.setPlayerType(slot.slot, slot.type)
            }
        }
    }

    private fun persistHumanScores(state: GameState) {
        val repository = scoreRepository ?: return
        viewModelScope.launch {
            for (player in state.players) {
                if (player.type == PlayerType.HUMAN) {
                    repository.recordScore(player.name, player.totalScore)
                }
            }
        }
    }

    private fun persistInProgressGame(state: GameState) {
        val repository = inProgressGameRepository ?: return
        viewModelScope.launch {
            if (state.isGameOver) repository.clear() else repository.save(state)
        }
    }

    private var aiTurnJob: Job? = null

    private fun maybeStartAiTurn() {
        val state = _game.value ?: return
        if (state.isGameOver) return
        val player = state.currentPlayer ?: return
        if (player.type != PlayerType.AI) return
        if (aiTurnJob?.isActive == true) return

        aiTurnJob = viewModelScope.launch {
            var current = state
            while (current.rollsRemaining > 0) {
                delay(AI_STEP_DELAY_MS)
                current = GameEngine.rollDice(current)
                setUndoSnapshot(null)
                applyGameState(current, checkForAiTurn = false)
            }
            delay(AI_STEP_DELAY_MS)
            current = GameEngine.commitScore(current, AiTurnPlayer.chooseCategory(current))
            setUndoSnapshot(null)
            applyGameState(current, checkForAiTurn = false)
            maybeStartAiTurn()
        }
    }

    companion object {
        private const val AI_STEP_DELAY_MS = 600L

        /** Builds a [GameViewModel] backed by real Room/DataStore persistence. */
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                GameViewModel(
                    scoreRepository = ScoreRepository(AppDatabase.getInstance(appContext).scoreDao()),
                    settingsRepository = SettingsRepository(appContext),
                    inProgressGameRepository = InProgressGameRepository(appContext),
                )
            }
        }
    }
}
