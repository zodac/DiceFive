package net.zodac.dicefive.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
 */
class GameViewModel : ViewModel() {

    private val _setup = MutableStateFlow(GameSetupState())
    val setup: StateFlow<GameSetupState> = _setup.asStateFlow()

    private val _game = MutableStateFlow<GameState?>(null)
    val game: StateFlow<GameState?> = _game.asStateFlow()

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
        setGameState(GameEngine.newGame(playerConfigs, setupState.gameType))
    }

    fun rollDice() = onHumanAction { GameEngine.rollDice(it) }

    fun toggleHold(dieIndex: Int) = onHumanAction { GameEngine.toggleHold(it, dieIndex) }

    fun commitScore(category: ScoreCategory) = onHumanAction { GameEngine.commitScore(it, category) }

    private fun onHumanAction(transform: (GameState) -> GameState) {
        val state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN) return
        setGameState(transform(state))
    }

    private fun updateSlot(slot: Int, transform: (PlayerSetupSlot) -> PlayerSetupSlot) {
        _setup.update { state ->
            state.copy(playerSlots = state.playerSlots.map { if (it.slot == slot) transform(it) else it })
        }
    }

    private fun setGameState(newState: GameState) {
        _game.value = newState
        maybeStartAiTurn()
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
                _game.value = current
            }
            delay(AI_STEP_DELAY_MS)
            current = GameEngine.commitScore(current, AiTurnPlayer.chooseCategory(current))
            _game.value = current
            maybeStartAiTurn()
        }
    }

    private companion object {
        const val AI_STEP_DELAY_MS = 600L
    }
}
