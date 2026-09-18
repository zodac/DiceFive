package net.zodac.dicefive.ui.game

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    /** The state to restore if [undo] is called - the pre-action snapshot of the most recent HUMAN move only. */
    private var undoSnapshot: GameState? = null
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    val confirmBeforeLeavingGame: StateFlow<Boolean> = (settingsRepository?.confirmBeforeLeavingGame ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        settingsRepository?.let { repository ->
            viewModelScope.launch {
                for (slot in 1..GameSetupState.MAX_PLAYERS) {
                    val savedName = repository.playerNameFor(slot).first() ?: continue
                    updateSlot(slot) { it.copy(name = savedName) }
                }
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
        setUndoSnapshot(null)
        applyGameState(GameEngine.newGame(playerConfigs, setupState.gameType))
    }

    /** Loads a previously in-progress game, if any. Returns whether one was found and resumed. */
    suspend fun resumeGame(): Boolean {
        val repository = inProgressGameRepository ?: return false
        val loaded = repository.load() ?: return false
        setUndoSnapshot(null)
        applyGameState(loaded)
        return true
    }

    fun rollDice() = onHumanAction { GameEngine.rollDice(it) }

    fun toggleHold(dieIndex: Int) = onHumanAction { GameEngine.toggleHold(it, dieIndex) }

    fun commitScore(category: ScoreCategory) = onHumanAction { GameEngine.commitScore(it, category) }

    /** Reverts just the most recent human move, if there is one to undo. Cancels any pending AI turn it would have triggered. */
    fun undo() {
        val snapshot = undoSnapshot ?: return
        aiTurnJob?.cancel()
        aiTurnJob = null
        setUndoSnapshot(null)
        applyGameState(snapshot, checkForAiTurn = false)
    }

    private fun onHumanAction(transform: (GameState) -> GameState) {
        val state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN) return
        setUndoSnapshot(state)
        applyGameState(transform(state))
    }

    private fun setUndoSnapshot(snapshot: GameState?) {
        undoSnapshot = snapshot
        _canUndo.value = snapshot != null
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
