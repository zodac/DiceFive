package net.zodac.dicefive.ui.game

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.random.Random
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zodac.dicefive.BuildConfig
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.AppDatabase
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.game.AchievementUpdate
import net.zodac.dicefive.game.AiNameGenerator
import net.zodac.dicefive.game.AiTurnPlayer
import net.zodac.dicefive.game.GameAchievementContext
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.game.DiceScoring
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.GameType
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase

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

        /**
         * Sized by where names are tightest: the in-game header, where [MAX_PLAYERS] tabs share one
         * row. At four players a tab is about a quarter of the screen - roughly 72dp of text on a
         * 360dp-wide phone - and a 10-character name fits that at the header's own type size
         * without being ellipsised. It is not a database or gameplay limit; it is a layout one.
         */
        const val MAX_PLAYER_NAME_LENGTH = 10
    }
}

/**
 * Owns both the pre-game setup form and the live [GameState] once started,
 * scoped to the "play" nav graph so [net.zodac.dicefive.ui.setup.GameSetupScreen]
 * and [GameScreen] share one instance across navigation.
 *
 * Every repository is optional so this class stays constructible (and
 * testable) on a plain JVM with no Android `Context` - [factory] supplies
 * the real ones. When absent, remembered names, score history, game
 * resumption and achievements are silently skipped.
 */
class GameViewModel(
    private val scoreRepository: ScoreRepository? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val inProgressGameRepository: InProgressGameRepository? = null,
    private val achievementsRepository: AchievementStore? = null,
    /** The dice. Injectable for the same reason [GameEngine.rollDice] takes one: so a test can deal a known hand. */
    private val random: Random = Random.Default,
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

    // Per-game achievement tracking: the two things the final GameState can't tell us afterwards.
    // Both are reset by startGame/resumeGame, and a resumed game starts its dice count from zero -
    // the dice rolled before the app was closed are simply not counted, which costs a little
    // progress on a 10,000-dice total rather than justifying persisting a counter mid-game.
    private var diceRolledByHumans = 0
    private var trailedIntoFinalRound = false
    private val achievementLock = Mutex()

    init {
        settingsRepository?.let { repository ->
            viewModelScope.launch {
                for (slot in 1..GameSetupState.MAX_PLAYERS) {
                    val savedName = repository.playerNameFor(slot).first() ?: continue
                    // Via setPlayerName, so a name saved before the length cap existed is trimmed
                    // to it on the way back in rather than reappearing over-long.
                    setPlayerName(slot, savedName)
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

    /**
     * Enforced here rather than only in the text field, so the cap holds for every path into a
     * name - including a longer one restored from a previous version's saved preferences.
     */
    fun setPlayerName(slot: Int, name: String) {
        updateSlot(slot) { it.copy(name = name.take(GameSetupState.MAX_PLAYER_NAME_LENGTH)) }
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
        resetAchievementTracking()
        applyGameState(GameEngine.newGame(playerConfigs, setupState.gameType))
        // "Full Table" is settled the moment four seats are taken - no need to make them play it out.
        checkInProgressAchievements()
    }

    /** Loads a previously in-progress game, if any. Returns whether one was found and resumed. */
    suspend fun resumeGame(): Boolean {
        val repository = inProgressGameRepository ?: return false
        val loaded = repository.load() ?: return false
        setUndoSnapshot(null)
        resetSuperuserMode()
        resetAchievementTracking()
        applyGameState(loaded)
        checkInProgressAchievements()
        return true
    }

    // Not undoable: rolling has no scoring consequence of its own to undo - only a committed
    // score does (see commitScore below).
    fun rollDice() {
        val state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN) return
        // Counted before the roll, while it's still clear which dice are actually going to move.
        diceRolledByHumans += state.dice.count { !it.isHeld }

        onHumanAction(undoable = false) { GameEngine.rollDice(it, random) }
        checkFirstRollAchievements()
    }

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
    fun commitScore(category: ScoreCategory) {
        onHumanAction(undoable = true) { GameEngine.commitScore(it, category) }
        // Only human commits are checked: every mid-game achievement reads human scorecards, which
        // an AI's turn can't change.
        checkInProgressAchievements()
    }

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
        trackFinalRoundPosition(newState)
        if (!wasGameOver && newState.isGameOver) {
            finishGame(newState)
        }
        if (checkForAiTurn) maybeStartAiTurn()
    }

    /**
     * Notes whether a human went into their last turn of the game behind everyone else, which is
     * the half of "Comeback Kid" that the final scorecard can no longer show.
     */
    private fun trackFinalRoundPosition(state: GameState) {
        if (trailedIntoFinalRound || state.isGameOver || state.phase != TurnPhase.AWAITING_ROLL) return
        val player = state.currentPlayer ?: return
        if (player.type != PlayerType.HUMAN) return
        if (player.scorecard.values.count { it == null } != 1) return

        val bestOther = state.players
            .filterIndexed { index, _ -> index != state.currentPlayerIndex }
            .maxOfOrNull { it.totalScore } ?: return
        if (player.totalScore < bestOther) trailedIntoFinalRound = true
    }

    /**
     * The one place a finished game's side effects happen, in order: the leaderboard's previous
     * high score is read *first*, because "New Personal Best" compares against the board as it was
     * before this game's own rows were added to it.
     */
    private fun finishGame(state: GameState) {
        viewModelScope.launch {
            // Guarded so a leaderboard problem costs only the leaderboard: before this, a throw
            // from either call took the achievement evaluation down with it, silently.
            // Both reads happen BEFORE the insert - "New Personal Best" compares against the board
            // as it was, and the score-collection bands need the before state to measure progress
            // against (the engine adds this game's own scores itself).
            val previousBestScore = runCatching { scoreRepository?.bestScore() }.getOrNull()
            val previousLeaderboard = runCatching { scoreRepository?.leaderboardTotals() }.getOrNull()
                ?: LeaderboardTotals()
            runCatching { persistHumanScores(state) }
            recordEndOfGameAchievements(state, previousBestScore, previousLeaderboard)
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
        val repository = achievementsRepository ?: return
        val state = _game.value ?: return
        if (state.isGameOver) return

        viewModelScope.launch {
            withAchievementLock {
                val update = AchievementEngine.evaluateInProgress(state, repository.current(), System.currentTimeMillis())
                persistAndAnnounce(repository, update)
            }
        }
    }

    private suspend fun recordEndOfGameAchievements(
        state: GameState,
        previousBestScore: Int?,
        previousLeaderboard: LeaderboardTotals,
    ) {
        val repository = achievementsRepository ?: return
        val context = GameAchievementContext(
            previousBestScore = previousBestScore,
            previousLeaderboard = previousLeaderboard,
            trailedIntoFinalRound = trailedIntoFinalRound,
            diceRolledByHumans = diceRolledByHumans,
        )
        withAchievementLock {
            val update = AchievementEngine.evaluate(state, context, repository.current(), System.currentTimeMillis())
            persistAndAnnounce(repository, update)
        }
    }

    /**
     * Serialises every read-decide-write cycle. Three separate triggers can run one - a scored
     * category, a feat off the first roll, and the end of the game - and two overlapping would
     * each read "not unlocked yet" and both announce the same achievement.
     */
    private suspend fun <T> withAchievementLock(block: suspend () -> T): T = achievementLock.withLock { block() }

    /**
     * The feats rolled straight out of the cup, earned mid-turn rather than off a scorecard: a
     * full house, a large straight or a 5x on the first of a turn's three rolls. They're mutually
     * exclusive on any given throw, but all three are checked rather than assuming that.
     */
    private fun checkFirstRollAchievements() {
        val state = _game.value ?: return
        if (state.rollsRemaining != ROLLS_REMAINING_AFTER_FIRST) return

        val dice = state.dice
        val earned = buildSet {
            if (DiceScoring.score(ScoreCategory.FULL_HOUSE, dice) > 0) add(Achievement.FIRST_ROLL_FULL_HOUSE)
            if (DiceScoring.score(ScoreCategory.LARGE_STRAIGHT, dice) > 0) add(Achievement.FIRST_ROLL_LARGE_STRAIGHT)
            if (DiceScoring.isFiveOfAKind(dice)) add(Achievement.FIRST_ROLL_5X)
        }
        unlockAchievements(earned)
    }

    private fun unlockAchievements(achievements: Set<Achievement>) {
        val repository = achievementsRepository ?: return
        if (achievements.isEmpty()) return
        viewModelScope.launch {
            withAchievementLock {
                val before = repository.current()
                val update = AchievementEngine.unlockNow(achievements, before, System.currentTimeMillis())
                persistAndAnnounce(repository, update)
            }
        }
    }

    private suspend fun persistAndAnnounce(repository: AchievementStore, update: AchievementUpdate) {
        if (update.isEmpty) return
        // Stored before anything is announced, so a banner can never outlive its unlock.
        repository.record(update.unlockedAt(), update.counters)
        update.newlyUnlocked.forEach { AchievementEvents.emit(AchievementEvent.Unlocked(it)) }
        update.progressed.forEach { AchievementEvents.emit(AchievementEvent.Progressed(it.achievement, it.current)) }
    }

    private fun resetAchievementTracking() {
        diceRolledByHumans = 0
        trailedIntoFinalRound = false
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

    // Suspending rather than launching its own coroutine: finishGame sequences this against the
    // leaderboard read that has to happen before it.
    private suspend fun persistHumanScores(state: GameState) {
        val repository = scoreRepository ?: return
        for (player in state.players) {
            if (player.type == PlayerType.HUMAN) {
                repository.recordScore(player.name, player.totalScore)
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
                current = GameEngine.rollDice(current, random)
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

        /** What `rollsRemaining` reads once the first of a turn's three rolls has been used. */
        private const val ROLLS_REMAINING_AFTER_FIRST = 2

        /** Builds a [GameViewModel] backed by real Room/DataStore persistence. */
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                GameViewModel(
                    scoreRepository = ScoreRepository(AppDatabase.getInstance(appContext).scoreDao()),
                    settingsRepository = SettingsRepository(appContext),
                    inProgressGameRepository = InProgressGameRepository(appContext),
                    achievementsRepository = AchievementsRepository(appContext),
                )
            }
        }
    }
}
