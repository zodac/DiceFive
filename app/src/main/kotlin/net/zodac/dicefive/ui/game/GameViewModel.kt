package net.zodac.dicefive.ui.game

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
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
import net.zodac.dicefive.game.GameStartContext
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.game.DiceScoring
import net.zodac.dicefive.game.ScoreCalculator
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.TableBackgrounds

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
    val gameMode: GameMode = GameMode.default,
    val turnTimer: TurnTimer = TurnTimer.NONE,
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
    /** Where [AiTurnPlayer]'s (Hard, specifically) exhaustive-search hold/category choices run -
     * off [viewModelScope]'s own Main.immediate by default, since they're expensive enough to
     * visibly stall Compose's frame rendering otherwise. Injectable so a test using a virtual-time
     * [kotlinx.coroutines.test.TestDispatcher] for `Dispatchers.Main` can pass that same dispatcher
     * here too - Default's real thread pool isn't advanced by that test's `advanceUntilIdle()`. */
    private val aiDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _setup = MutableStateFlow(GameSetupState())
    val setup: StateFlow<GameSetupState> = _setup.asStateFlow()

    private val _game = MutableStateFlow<GameState?>(null)
    val game: StateFlow<GameState?> = _game.asStateFlow()

    /** Mirrors the human dice cup's tap-driven shake animation for AI turns: unlike [rollDice],
     * [maybeStartAiTurn] updates dice directly rather than through a UI click handler, so nothing
     * would otherwise flip the cup/tray into their "rolling" pose for an AI player's rolls. */
    private val _aiRolling = MutableStateFlow(false)
    val aiRolling: StateFlow<Boolean> = _aiRolling.asStateFlow()

    /** Seconds left on the current human turn's timer, or null when [GameState.turnTimer] is
     * [TurnTimer.NONE], it's an AI's turn, or the game is over - see [syncTurnTimer]. */
    private val _turnSecondsRemaining = MutableStateFlow<Int?>(null)
    val turnSecondsRemaining: StateFlow<Int?> = _turnSecondsRemaining.asStateFlow()

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

    // Per-game achievement tracking: things the final GameState can't tell us afterwards. All of
    // it is reset by startGame/resumeGame, and a resumed game starts its counters from zero - the
    // dice rolled before the app was closed are simply not counted, which costs a little progress
    // on a 10,000-dice total rather than justifying persisting a counter mid-game.
    // Only player 1's own turns feed any of this - see AchievementEngine's class doc - so every
    // one of these is only ever touched while state.currentPlayerIndex == 0.
    private var diceRolledByPlayerOne = 0
    private var trailedIntoFinalRound = false
    private var ledIntoFinalRound = false

    /** Whether player 1 has used a 2nd or 3rd roll on at least one of their own turns this game -
     * see [Achievement.IMPATIENT]/[Achievement.NATURALLY_GIFTED]. */
    private var playerOneTookExtraRoll = false

    /** Whether player 1's most recently completed turn scored a genuine 5x - see
     * [Achievement.TWICE_IN_A_LIFETIME]. */
    private var playerOnePreviousTurnWasFiveOfAKind = false

    private val achievementLock = Mutex()

    // Per-turn achievement tracking: how THIS turn's rolls and holds actually played out, which a
    // finished scorecard can't reconstruct afterwards. Reset at the start of every one of player
    // 1's own turns (its first roll - see resetPerTurnTracking, called from rollDice). Nobody
    // else's turns touch any of this: AI turns roll and commit straight through GameEngine,
    // bypassing every method below, and turns belonging to a human in another seat are skipped by
    // the same isPlayerOneTurn checks that guard rollDice/toggleHold/commitScore.
    private var previousRollFaces: List<Die>? = null
    private var heldChangedSinceLastRoll = false

    /** The die indices that made up a first-roll four of a kind this turn - null if roll 1 wasn't
     * one. See [checkPostRollAchievements]'s Almost Famous comment. */
    private var fourOfAKindIndicesFromFirstRoll: Set<Int>? = null

    /** Whether [fourOfAKindIndicesFromFirstRoll] has been exactly the held set - no more, no less -
     * going into every roll since (so the loose 5th die actually kept getting re-rolled). Starts
     * true the moment a first-roll four of a kind is seen, and can only ever be knocked false. */
    private var heldFourOfAKindThroughTurn = false
    private var fiveOfAKindSeenThisTurn = false
    private var facesAfterFirstRoll: List<Die>? = null
    private var loadedDiceHeldIndices: Set<Int>? = null
    private var loadedDiceMatchedSecondRoll = false
    private var heldIndicesBeforeSecondRoll: Set<Int>? = null

    /** Die indices held going into BOTH the 2nd and 3rd roll - i.e. held after the 1st roll and
     * still held after the 2nd. Computed in [rollDice] right before the 3rd roll, since that's the
     * only point both snapshots exist to compare - see [Achievement.TIME_TO_LET_IT_GO]'s check in
     * [checkPreCommitAchievements]. */
    private var heldThroughBothRerolls: Set<Int> = emptySet()
    private var everHeldAllFiveThisTurn = false
    private val holdUnholdCyclesByDieIndex = mutableMapOf<Int, Int>()
    private var hadScoringOptionAfterSecondRoll = false

    /** The value, and die indices, of an exact matching group (1-4 dice, all the same value,
     * nothing else held) currently or most recently held - see [Achievement.COMMITMENT_ISSUES]'s
     * doc comment on [checkPostHoldAchievements]. */
    private var pendingCommitmentGroupValue: Int? = null
    private var pendingCommitmentGroupIndices: Set<Int> = emptySet()

    /** Whether the currently-tracked group passed through a full 5-of-a-kind at some point since it
     * was last fresh - a real 5x is not indecision, so it disqualifies crediting the release even
     * once the held set has shrunk back down to looking like a plain group again. */
    private var commitmentGroupTainted = false

    /** The value of the exact matching group most recently held-then-fully-released this turn. */
    private var lastReleasedCommitmentGroupValue: Int? = null
    private var lastCommittedCategory: ScoreCategory? = null
    private var pendingUndoneCategory: ScoreCategory? = null
    private var outOfRollsCupTaps = 0

    init {
        settingsRepository?.let { repository ->
            viewModelScope.launch {
                for (slot in 1..GameSetupState.MAX_PLAYERS) {
                    val savedName = repository.playerNameFor(slot).first() ?: continue
                    // Via setPlayerName, so a name saved before the length cap existed is trimmed
                    // to it on the way back in rather than reappearing over-long.
                    setPlayerName(slot, savedName)
                }
                // Slot 1 is always Human, so its type and difficulty are never saved/restored.
                for (slot in 2..GameSetupState.MAX_PLAYERS) {
                    val savedType = repository.playerTypeFor(slot).first() ?: continue
                    updateSlot(slot) { it.copy(type = savedType) }
                }
                for (slot in 2..GameSetupState.MAX_PLAYERS) {
                    val savedDifficulty = repository.playerDifficultyFor(slot).first() ?: continue
                    updateSlot(slot) { it.copy(difficulty = savedDifficulty) }
                }
                repository.playerCount.first()?.let { savedCount -> setPlayerCount(savedCount) }
                setTurnTimer(repository.turnTimer.first())
                setGameMode(repository.gameMode.first())
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

    fun setGameMode(gameMode: GameMode) {
        _setup.update { it.copy(gameMode = gameMode) }
    }

    fun setTurnTimer(turnTimer: TurnTimer) {
        _setup.update { it.copy(turnTimer = turnTimer) }
    }

    /** Builds the initial [GameState] from the current setup form, generating AI names now. */
    fun startGame() {
        // Read before anything below overwrites it: "One More Time" is about the game THIS call is
        // replacing, not the one it's about to create.
        val previousGame = _game.value

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
        persistGameConfig(setupState.playerCount, activeSlots, setupState.turnTimer, setupState.gameMode)
        setUndoSnapshot(null)
        resetSuperuserMode()
        resetAchievementTracking()
        applyGameState(GameEngine.newGame(playerConfigs, setupState.gameMode, setupState.turnTimer))
        // "Full Table" is settled the moment four seats are taken - no need to make them play it out.
        checkInProgressAchievements()
        checkGameStartAchievements(
            customizedGameSettings = setupState.turnTimer != TurnTimer.NONE || setupState.gameMode != GameMode.default,
        )

        if (previousGame != null && previousGame.isGameOver && !humanWonGame(previousGame)) {
            unlockAchievements(setOf(Achievement.REPLAY_AFTER_LOSS))
        }
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
        checkGameStartAchievements()
        unlockAchievements(setOf(Achievement.CONTINUED_GAME))
        return true
    }

    // Not undoable: rolling has no scoring consequence of its own to undo - only a committed
    // score does (see commitScore below).
    fun rollDice() {
        val state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN) return

        // Only player 1 - "You" - earns achievements; another human seat still plays normally
        // below, it just doesn't feed any of the tracking that leads to one.
        val isPlayerOneTurn = state.currentPlayerIndex == 0

        if (isPlayerOneTurn) {
            if (state.dice.all { it.isHeld }) unlockAchievements(setOf(Achievement.POINTLESS_ROLL))
            if (state.rollsRemaining == state.fullRolls) resetPerTurnTracking()
            if (state.rollsRemaining < state.fullRolls) playerOneTookExtraRoll = true

            // Decisions, Decisions: "three times before rolling again" means all three hold/unhold
            // cycles on a die have to land in the same gap between rolls - progress made before
            // this roll doesn't carry over into the gap after it, on any die, same bug class as
            // Almost Famous requiring every roll be spent. resetPerTurnTracking above only clears
            // this at the START of a turn (the first roll), which left a 2nd/3rd roll mid-turn free
            // to bridge two otherwise-unrelated partial cycles into one.
            holdUnholdCyclesByDieIndex.clear()

            // Loaded Dice: the held set right before the 2nd roll, and whether it's still the held
            // set right before the 3rd - a proper, non-empty, non-full subset only, both times.
            if (state.rollsRemaining == state.rollsRemainingAfterFirst) {
                val held = state.dice.withIndex().filter { it.value.isHeld }.map { it.index }.toSet()
                heldIndicesBeforeSecondRoll = held
                loadedDiceHeldIndices = held.takeIf { it.isNotEmpty() && it.size < state.dice.size }
            } else if (state.rollsRemaining == state.rollsRemainingAfterSecond) {
                val heldNow = state.dice.withIndex().filter { it.value.isHeld }.map { it.index }.toSet()
                if (heldNow != heldIndicesBeforeSecondRoll) loadedDiceHeldIndices = null
                heldThroughBothRerolls = (heldIndicesBeforeSecondRoll ?: emptySet()).intersect(heldNow)
            }
        }

        val diceBeforeRoll = state.dice
        val rollsRemainingBeforeRoll = state.rollsRemaining
        // Counted before the roll, while it's still clear which dice are actually going to move.
        if (isPlayerOneTurn) diceRolledByPlayerOne += diceBeforeRoll.count { !it.isHeld }

        onHumanAction(undoable = false) { GameEngine.rollDice(it, random) }
        if (isPlayerOneTurn) {
            checkFirstRollAchievements()
            checkPostRollAchievements(rollsRemainingBeforeRoll, diceBeforeRoll)
        }
    }

    // Not undoable, same reasoning as rollDice: holding/unholding just selects what a future roll
    // will touch, it doesn't itself score anything.
    fun toggleHold(dieIndex: Int) {
        trackSuperuserSequence(dieIndex)
        val state = _game.value
        val wasHeld = state?.dice?.getOrNull(dieIndex)?.isHeld == true
        // Only player 1 - "You" - earns achievements; another human seat can still toggle holds,
        // it just doesn't feed Achievement tracking.
        val isPlayerOneTurn = state?.currentPlayer?.type == PlayerType.HUMAN && state.currentPlayerIndex == 0

        onHumanAction(undoable = false) { GameEngine.toggleHold(it, dieIndex) }

        if (isPlayerOneTurn) checkPostHoldAchievements(dieIndex, wasHeld)
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
        // Only player 1 - "You" - earns achievements; another human seat can still commit a score
        // normally, it just doesn't feed any Achievement tracking. Read before onHumanAction below,
        // which ends this player's turn and advances currentPlayerIndex to the next seat.
        val isPlayerOneTurn = _game.value?.currentPlayerIndex == 0

        if (isPlayerOneTurn) {
            checkWastedFiveOfAKind(category)
            checkPreCommitAchievements(category)
        }
        val categoryJustUndone = pendingUndoneCategory

        onHumanAction(undoable = true) { GameEngine.commitScore(it, category) }

        if (isPlayerOneTurn && categoryJustUndone != null && category != categoryJustUndone) {
            unlockAchievements(setOf(Achievement.UNDO_DIFFERENT_CATEGORY))
        }
        pendingUndoneCategory = null
        lastCommittedCategory = category
        // Only player 1's own commits are checked: every mid-game achievement reads player 1's
        // scorecard, which nobody else's turn can change.
        if (isPlayerOneTurn) checkInProgressAchievements()
    }

    /** Reverts just the most recent human move, if there is one to undo. Cancels any pending AI turn it would have triggered. */
    fun undo() {
        val snapshot = undoSnapshot ?: return
        pendingUndoneCategory = lastCommittedCategory
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
        val state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN) return
        // Only player 1 - "You" - earns achievements.
        if (state.currentPlayerIndex != 0) return
        if (state.rollsRemaining != 0) return
        outOfRollsCupTaps++
        if (outOfRollsCupTaps >= NO_MORE_ROLLS_TAP_TARGET) {
            unlockAchievements(setOf(Achievement.NO_MORE_ROLLS))
        }
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
     * Tracked in every build, debug or release, on any seat's turn - the cheat itself stays "any
     * player's", per its own doc comment above. [Achievement.TIME_WASTING] is the one part of this
     * gated to player 1: it rewards performing the sequence itself, whether or not it actually goes
     * on to do anything, but only when player 1 is the one doing it. Only the cheat's effect once
     * completed - flipping [_superuserModeActive] and the toast - stays behind [BuildConfig.DEBUG],
     * which is the only gate the cheat itself needs: [cycleHeldDieValue] already requires
     * [_superuserModeActive], and every UI entry point (DiceTray's onCycleValue wiring) is itself
     * conditional on that same flag.
     */
    private fun trackSuperuserSequence(dieIndex: Int) {
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
                resetSuperuserSequence()
                if (state.currentPlayerIndex == 0) unlockAchievements(setOf(Achievement.TIME_WASTING))
                if (BuildConfig.DEBUG) {
                    _superuserModeActive.value = true
                    _toastMessages.trySend("Superuser mode activated!")
                }
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
        syncTurnTimer(newState)
        if (checkForAiTurn) maybeStartAiTurn()
    }

    private var turnTimerJob: Job? = null

    /** The total number of categories scored across every player as of the turn [turnTimerJob] is
     * currently counting down for - a turn always ends by scoring exactly one category, so this
     * strictly increases by one turn to turn and uniquely identifies "a new turn started" even in
     * a single-player game, where [GameState.currentPlayerIndex] alone would stay 0 forever. A
     * state update that doesn't actually change whose turn it is (e.g. a superuser die cycle,
     * rolling, holding) leaves it unchanged, so the countdown isn't restarted mid-turn. */
    private var turnTimerTurnSequence: Int? = null

    /**
     * Starts, restarts or cancels the per-turn countdown so it always matches [newState]: running
     * only on a human seat's own turn, only while [GameState.turnTimer] allows one, and reset to
     * the full duration whenever the turn it's counting down for changes (a new turn starting, or
     * the previous turn reappearing after [undo]).
     */
    private fun syncTurnTimer(newState: GameState) {
        val seconds = newState.turnTimer.seconds
        if (newState.isGameOver || newState.currentPlayer?.type != PlayerType.HUMAN || seconds == null) {
            cancelTurnTimer()
            return
        }
        val turnSequence = newState.players.sumOf { player -> player.scorecard.values.count { it != null } }
        if (turnTimerTurnSequence == turnSequence && turnTimerJob?.isActive == true) return
        turnTimerTurnSequence = turnSequence
        startTurnTimer(seconds)
    }

    private fun startTurnTimer(totalSeconds: Int) {
        turnTimerJob?.cancel()
        _turnSecondsRemaining.value = totalSeconds
        turnTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1_000L)
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
     * The turn timer running out: forfeits the rest of this turn's rolls (rolling first, if the
     * player hadn't yet, since a category can't be committed before that) and commits into
     * whichever open category comes first, scoring zero if the current dice don't match it - a
     * forced miss rather than picking the player's best option for them.
     */
    private fun autoScoreOnTimeout() {
        var state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN) return
        // Only player 1 - "You" - earns achievements; another human seat can still be timed out,
        // it just doesn't feed Achievement tracking.
        val isPlayerOneTurn = state.currentPlayerIndex == 0

        if (state.phase != TurnPhase.ROLLED) {
            state = GameEngine.rollDice(state, random)
        }
        val player = state.currentPlayer ?: return
        val category = ScoreCalculator.availableCategories(player, state.dice).first()
        setUndoSnapshot(null)
        applyGameState(GameEngine.commitScore(state, category))
        if (isPlayerOneTurn) unlockAchievements(setOf(Achievement.OUT_OF_TIME))
    }

    /**
     * Notes whether player 1 went into their last turn of the game ahead of, or behind, everyone
     * else - the half of "Comeback Kid"/"Defeat From the Jaws of Victory" that the final scorecard
     * can no longer show once the game moves on. Only player 1 - "You" - earns achievements, so
     * another human seat reaching their own final turn is irrelevant here.
     */
    private fun trackFinalRoundPosition(state: GameState) {
        if (trailedIntoFinalRound && ledIntoFinalRound) return
        if (state.isGameOver || state.phase != TurnPhase.AWAITING_ROLL) return
        if (state.currentPlayerIndex != 0) return
        val player = state.currentPlayer ?: return
        if (player.type != PlayerType.HUMAN) return
        if (player.scorecard.values.count { it == null } != 1) return

        val bestOther = state.players
            .filterIndexed { index, _ -> index != state.currentPlayerIndex }
            .maxOfOrNull { it.totalScore } ?: return
        if (player.totalScore < bestOther) trailedIntoFinalRound = true
        if (player.totalScore > bestOther) ledIntoFinalRound = true
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
            // By name, and only player 1's: someone else's high score at the same table must not
            // count as beating YOUR best, now that achievements are player 1's alone.
            val playerOneName = state.players.firstOrNull()?.takeIf { it.type == PlayerType.HUMAN }?.name
            val previousBestScore = playerOneName?.let { name ->
                runCatching { scoreRepository?.bestScoreForPlayer(name) }.getOrNull()
            }
            val previousLeaderboard = runCatching { scoreRepository?.leaderboardTotals() }.getOrNull()
                ?: LeaderboardTotals()
            runCatching { persistHumanScores(state) }
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
     *
     * [customizedGameSettings] is passed in rather than read from [_setup] here: it's only true for
     * an actual [startGame] with a non-default setup, never for [resumeGame] resuming a previously
     * saved game, whose setup form may since have moved on to something else entirely.
     */
    private fun checkGameStartAchievements(customizedGameSettings: Boolean = false) {
        val repository = achievementsRepository ?: return
        val settings = settingsRepository ?: return
        viewModelScope.launch {
            // Never P1 - "You" is always index 0, so this only ever looks at the other seats.
            val game = _game.value
            val otherHumans = game?.players.orEmpty().drop(1).filter { it.type == PlayerType.HUMAN }
            val gameMode = game?.gameMode ?: GameMode.default
            // A mode that colours its own dice never shows the dice style, so picking one can't count.
            val playedNonDefaultDiceStyle = gameMode.usesPlayerDiceStyle &&
                isNonDefaultStyle(settings.diceStyleId, DiceStyles.default.id)
            val playedNonDefaultStyle = playedNonDefaultDiceStyle ||
                isNonDefaultStyle(settings.diceCupStyleId, DiceCupStyles.default.id) ||
                isNonDefaultStyle(settings.tableBackgroundId, TableBackgrounds.default.id) ||
                isNonDefaultStyle(settings.diceMatId, DiceMats.default.id)
            val context = GameStartContext(
                playedNonDefaultStyle = playedNonDefaultStyle,
                hasHumanPlayerNamedZodac = otherHumans.any { it.name == ZODAC_PLAYER_NAME },
                customizedGameSettings = customizedGameSettings,
                gameMode = gameMode,
            )
            withAchievementLock {
                val update = AchievementEngine.evaluateAtGameStart(context, repository.current(), System.currentTimeMillis())
                persistAndAnnounce(repository, update)
            }
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
            ledIntoFinalRound = ledIntoFinalRound,
            diceRolledByPlayerOne = diceRolledByPlayerOne,
            playerOneTookExtraRoll = playerOneTookExtraRoll,
        )
        withAchievementLock {
            val update = AchievementEngine.evaluate(state, context, repository.current(), System.currentTimeMillis())
            persistAndAnnounce(repository, update)
        }
    }

    /** Whether [styleIdFlow]'s current value is anything other than [defaultId] - false (not "yes,
     * non-default") when there's no repository to read at all, e.g. in a test with no Context. */
    private suspend fun isNonDefaultStyle(styleIdFlow: Flow<String>?, defaultId: String): Boolean =
        styleIdFlow?.first()?.let { it != defaultId } ?: false

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
     *
     * Each one also requires that category still be a genuine, legal option - not just that the
     * dice happen to match the pattern. A full house/large straight rolled after that box is
     * already filled (scored or zeroed) isn't a real choice, so it doesn't count; see
     * [fiveOfAKindScorable] for the 5x case specifically.
     */
    private fun checkFirstRollAchievements() {
        val state = _game.value ?: return
        if (state.rollsRemaining != state.rollsRemainingAfterFirst) return
        val player = state.currentPlayer ?: return

        val dice = state.dice
        val available = ScoreCalculator.availableCategories(player, dice)
        val earned = buildSet {
            if (ScoreCategory.FULL_HOUSE in available && DiceScoring.score(ScoreCategory.FULL_HOUSE, dice) > 0) {
                add(Achievement.FIRST_ROLL_FULL_HOUSE)
            }
            if (ScoreCategory.LARGE_STRAIGHT in available && DiceScoring.score(ScoreCategory.LARGE_STRAIGHT, dice) > 0) {
                add(Achievement.FIRST_ROLL_LARGE_STRAIGHT)
            }
            if (DiceScoring.isFiveOfAKind(dice) && fiveOfAKindScorable(player, dice, available)) {
                add(Achievement.FIRST_ROLL_5X)
            }
        }
        unlockAchievements(earned)
    }

    /**
     * Whether a 5x just rolled could actually be scored as one - either the FIVE_OF_A_KIND box is
     * still open (the genuine 50), or it's already showing a genuine 50 and this roll earns the
     * +100 joker bonus instead (see [ScoreCalculator.awardsFiveOfAKindBonus]). False once that box
     * has been zeroed - at that point neither path is available any more, so a 5x roll from here
     * on is never a real scoring option, just a shape the dice happened to land in.
     */
    private fun fiveOfAKindScorable(player: PlayerState, dice: List<Die>, available: List<ScoreCategory>): Boolean =
        ScoreCategory.FIVE_OF_A_KIND in available || ScoreCalculator.awardsFiveOfAKindBonus(player, dice)

    /**
     * Everything about a roll that can only be judged from the roll itself, not the finished
     * scorecard - checked once the roll has landed in [_game], using [rollsRemainingBeforeRoll]
     * (the value from just before it, so `== fullRolls` means "this was roll 1") and
     * [diceBeforeRoll] (so held-vs-unheld can be read as it was going INTO this roll).
     */
    private fun checkPostRollAchievements(rollsRemainingBeforeRoll: Int, diceBeforeRoll: List<Die>) {
        val state = _game.value ?: return
        val player = state.currentPlayer ?: return
        val dice = state.dice
        val values = dice.map { it.value }
        // Number and colour together: in a mode with coloured dice, "the same result" means both.
        val faces = dice.faces()

        // Déjà Vu: the same result as the immediately previous roll THIS turn, with nothing held -
        // not just unchanged since the last roll (that's Are These Loaded Dice?'s territory, the
        // partial-hold complement of this one), but actually zero dice held right now.
        if (previousRollFaces != null && !heldChangedSinceLastRoll && dice.none { it.isHeld } && faces == previousRollFaces) {
            unlockAchievements(setOf(Achievement.DEJA_VU))
        }
        previousRollFaces = faces
        heldChangedSinceLastRoll = false

        // I Can Count!: 1,2,3,4,5 in that order, first roll of the turn only.
        if (rollsRemainingBeforeRoll == state.fullRolls && values == COUNTING_DICE_VALUES) {
            unlockAchievements(setOf(Achievement.I_CAN_COUNT))
        }
        // Product Placement: the menu logo's own dice, in its own order - any roll of any turn.
        if (values == LOGO_DICE_VALUES) {
            unlockAchievements(setOf(Achievement.PRODUCT_PLACEMENT))
        }

        // Natural 5x: landed without holding anything for this roll, and it wasn't the first one
        // (rolling nothing-held on roll 1 is just how every turn starts) - and, same as First
        // Roll 5x, only if the 5x could actually be scored as one (see fiveOfAKindScorable).
        if (rollsRemainingBeforeRoll < state.fullRolls &&
            diceBeforeRoll.none { it.isHeld } &&
            DiceScoring.isFiveOfAKind(dice) &&
            fiveOfAKindScorable(player, dice, ScoreCalculator.availableCategories(player, dice))
        ) {
            unlockAchievements(setOf(Achievement.NATURAL_5X))
        }

        // Almost Famous: remember a first-roll four of a kind's own die indices, whether they're
        // still exactly what's held going into every roll since (nothing more, nothing less - so
        // the 5th die is the one actually getting re-rolled, not sitting held alongside them), and
        // whether a real 5x ever turns up on it anyway.
        if (rollsRemainingBeforeRoll == state.fullRolls) {
            val fourOfAKindValue = values.groupingBy { it }.eachCount().entries.firstOrNull { it.value == FOUR_OF_A_KIND_COUNT }?.key
            fourOfAKindIndicesFromFirstRoll = fourOfAKindValue?.let { value ->
                values.withIndex().filter { it.value == value }.map { it.index }.toSet()
            }
            heldFourOfAKindThroughTurn = fourOfAKindIndicesFromFirstRoll != null
            facesAfterFirstRoll = faces
        } else {
            fourOfAKindIndicesFromFirstRoll?.let { required ->
                val heldGoingIn = diceBeforeRoll.withIndex().filter { it.value.isHeld }.map { it.index }.toSet()
                if (heldGoingIn != required) heldFourOfAKindThroughTurn = false
            }
        }
        if (DiceScoring.isFiveOfAKind(dice)) fiveOfAKindSeenThisTurn = true

        // The Dice Hate Me: a real (non-zero) scoring option existed after roll 2, but none at all
        // after roll 3.
        if (rollsRemainingBeforeRoll == state.rollsRemainingAfterFirst) {
            hadScoringOptionAfterSecondRoll = hasScoringOption(player, dice)
        } else if (rollsRemainingBeforeRoll == state.rollsRemainingAfterSecond) {
            if (hadScoringOptionAfterSecondRoll && !hasScoringOption(player, dice)) {
                unlockAchievements(setOf(Achievement.DICE_HATE_ME))
            }
        }

        // Are These Loaded Dice?: the dice NOT held since roll 1 keep landing on exactly the same
        // values, roll after roll. heldIndicesBeforeSecondRoll/loadedDiceHeldIndices were captured
        // in rollDice, before this roll happened.
        val firstRollFaces = facesAfterFirstRoll
        val heldIndices = loadedDiceHeldIndices
        if (firstRollFaces != null && heldIndices != null && rollsRemainingBeforeRoll < state.fullRolls) {
            val unheldStillMatches = faces.indices.filter { it !in heldIndices }.all { faces[it] == firstRollFaces[it] }
            when {
                !unheldStillMatches -> loadedDiceHeldIndices = null
                rollsRemainingBeforeRoll == state.rollsRemainingAfterFirst -> loadedDiceMatchedSecondRoll = true
                rollsRemainingBeforeRoll == state.rollsRemainingAfterSecond && loadedDiceMatchedSecondRoll ->
                    unlockAchievements(setOf(Achievement.LOADED_DICE))
            }
        }
    }

    private fun hasScoringOption(player: PlayerState, dice: List<Die>): Boolean =
        ScoreCalculator.availableCategories(player, dice).any { ScoreCalculator.scoreFor(player, it, dice) > 0 }

    /**
     * Everything about holding/unholding that can only be judged as it happens, checked once
     * [_game] reflects the toggle - [wasHeld] is the die's state just before it, so `wasHeld &&
     * (now unheld)` identifies an unhold specifically, not a hold.
     *
     * Commitment Issues' state machine lives here: [pendingCommitmentGroupValue] is the value of an
     * EXACT matching group - 1 to 4 dice, all one value, nothing else held (5 is a 5x, not
     * indecision, so it's excluded) - currently or most recently held, tracked alongside the dice
     * that make it up ([pendingCommitmentGroupIndices]) so *releasing that same group one tap at a
     * time* - the only way the real UI can unhold more than one die, since each is its own tap
     * target - doesn't lose it the instant the first die of it lets go and the held set stops being
     * an exact group of its own. Only holding something that ISN'T a subset of the group being
     * released - an unrelated die, or a fresh group before this one finished clearing - breaks the
     * chain early. The moment the held set reaches empty having been released from a real group,
     * that group's value moves into [lastReleasedCommitmentGroupValue] as the baseline the next
     * group has to differ from. [checkPreCommitAchievements] is what actually awards it, once a
     * category is committed for the matching upper box.
     */
    private fun checkPostHoldAchievements(dieIndex: Int, wasHeld: Boolean) {
        val state = _game.value ?: return
        val dice = state.dice
        val heldIndices = dice.withIndex().filter { it.value.isHeld }.map { it.index }.toSet()
        val justUnheld = wasHeld

        // Breaks any pending Déjà Vu comparison for the next roll, whichever direction this toggle went.
        heldChangedSinceLastRoll = true

        if (justUnheld) {
            val cycles = (holdUnholdCyclesByDieIndex[dieIndex] ?: 0) + 1
            holdUnholdCyclesByDieIndex[dieIndex] = cycles
            if (cycles >= HOLD_UNHOLD_CYCLE_TARGET) {
                unlockAchievements(setOf(Achievement.DECISIONS_DECISIONS))
            }
        }

        // A Cunning Strategy: reached all-five-held at some point this turn, then all-none-held afterward.
        when {
            heldIndices.size == dice.size -> everHeldAllFiveThisTurn = true
            heldIndices.isEmpty() && everHeldAllFiveThisTurn -> unlockAchievements(setOf(Achievement.CUNNING_STRATEGY))
        }

        val heldValues = heldIndices.map { dice[it].value }
        val allOneValue = heldValues.isNotEmpty() && heldValues.toSet().size == 1
        when {
            heldIndices.isEmpty() -> {
                // Only credited if it never passed through a full 5-of-a-kind on the way here -
                // shrinking down from one taints the whole release, even once it looks like a plain
                // group again at 4, 3, 2, 1 held (commitmentGroupTainted carries over below).
                if (pendingCommitmentGroupValue != null && !commitmentGroupTainted) {
                    lastReleasedCommitmentGroupValue = pendingCommitmentGroupValue
                }
                pendingCommitmentGroupValue = null
                pendingCommitmentGroupIndices = emptySet()
                commitmentGroupTainted = false
            }
            allOneValue -> {
                // Building up, or shrinking back down, WITHIN the same tracked group keeps its taint;
                // anything else (nothing tracked yet, or this isn't a subset of what was) starts fresh.
                val continuingSameGroup = pendingCommitmentGroupIndices.isNotEmpty() && heldIndices.all { it in pendingCommitmentGroupIndices }
                if (!continuingSameGroup) {
                    pendingCommitmentGroupValue = heldValues.first()
                    commitmentGroupTainted = false
                }
                pendingCommitmentGroupIndices = heldIndices
                // A real 5x - not indecision - taints it: crediting whatever this shrinks back down
                // through afterwards would call rolling a genuine 5x "holding a group", which it isn't.
                if (heldIndices.size == dice.size) commitmentGroupTainted = true
            }
            else -> {
                pendingCommitmentGroupValue = null
                pendingCommitmentGroupIndices = emptySet()
                commitmentGroupTainted = false
            }
        }
    }

    /**
     * The dice a human is about to commit, checked BEFORE [GameEngine.commitScore] changes
     * anything: a genuine 5x roll (all five dice matching) that's about to be scored as a zero
     * anyway, whether that's a deliberate waste or the joker rule's forced-zero fallback once every
     * matching box is already full. [ScoreCalculator.scoreFor] is joker-aware, so it already
     * accounts for both.
     */
    private fun checkWastedFiveOfAKind(category: ScoreCategory) {
        val state = _game.value ?: return
        val player = state.currentPlayer ?: return
        if (player.type != PlayerType.HUMAN) return
        val dice = state.dice
        if (!DiceScoring.isFiveOfAKind(dice)) return
        if (ScoreCalculator.scoreFor(player, category, dice) != 0) return
        unlockAchievements(setOf(Achievement.WASTED_5X))
    }

    /** Everything else that has to be judged at the moment of commit, against the dice and
     * category about to be used - also called before [GameEngine.commitScore] changes anything. */
    private fun checkPreCommitAchievements(category: ScoreCategory) {
        val state = _game.value ?: return
        val player = state.currentPlayer ?: return
        if (player.type != PlayerType.HUMAN) return
        val dice = state.dice

        // Why Did You Do That?: the small straight scored while the large straight sat right
        // there, open and legal.
        if (category == ScoreCategory.SMALL_STRAIGHT &&
            ScoreCategory.LARGE_STRAIGHT in ScoreCalculator.availableCategories(player, dice) &&
            DiceScoring.score(ScoreCategory.LARGE_STRAIGHT, dice) > 0
        ) {
            unlockAchievements(setOf(Achievement.WHY_DID_YOU_DO_THAT))
        }

        // Almost Famous: this turn had a first-roll four of a kind, held exactly as-is (see
        // checkPostRollAchievements) through every roll since so the 5th die actually kept
        // getting re-rolled, and it never turned into a real 5x. Committing after only one or two
        // rolls isn't "almost" anything; rollsRemaining == 0 here means all three rolls this turn
        // were spent (see the fullRolls/rollsRemainingAfter* helpers). Whether the
        // 5x could even have been scored doesn't matter - only that it was rolled for and missed.
        if (fourOfAKindIndicesFromFirstRoll != null &&
            heldFourOfAKindThroughTurn &&
            !fiveOfAKindSeenThisTurn &&
            state.rollsRemaining == 0
        ) {
            unlockAchievements(setOf(Achievement.ALMOST_FAMOUS))
        }

        // Fuller House: the best full house there is - three 6s and two 5s specifically, which a
        // 5x-as-joker full house (all five dice the same value) can never produce.
        if (category == ScoreCategory.FULL_HOUSE && dice.map { it.value }.sorted() == FULLER_HOUSE_VALUES) {
            unlockAchievements(setOf(Achievement.FULLER_HOUSE))
        }

        // Commitment Issues: committing the upper box that matches the second, different exact group.
        val committingValue = pendingCommitmentGroupValue
        if (lastReleasedCommitmentGroupValue != null &&
            committingValue != null &&
            committingValue != lastReleasedCommitmentGroupValue &&
            category == PlayerState.UPPER_CATEGORIES[committingValue - 1]
        ) {
            unlockAchievements(setOf(Achievement.COMMITMENT_ISSUES))
        }

        // Time To Let It Go: a die held after both the 1st and 2nd rolls (heldThroughBothRerolls,
        // captured in rollDice) whose value the committed category never counted at all - whether
        // that die is still held right now or was let go beforehand doesn't matter, only whether it
        // fed the score. Only the upper section can be "not used" this way: every other category's
        // score is built from all five dice together (a sum, or a pattern needing all of them), so
        // there's no such thing as scoring one of those without a die still on the felt counting.
        if (category in PlayerState.UPPER_CATEGORIES) {
            val target = PlayerState.UPPER_CATEGORIES.indexOf(category) + 1
            if (heldThroughBothRerolls.any { dice[it].value != target }) {
                unlockAchievements(setOf(Achievement.TIME_TO_LET_IT_GO))
            }
        }

        // Twice in a Lifetime: this turn scores a 5x, and so did player 1's last one - matches the
        // SCORED_5X counter's own definition of "scored a 5x" (the box, or a bonus chip).
        val scoresFiveOfAKindNow = (category == ScoreCategory.FIVE_OF_A_KIND && DiceScoring.isFiveOfAKind(dice)) ||
            ScoreCalculator.awardsFiveOfAKindBonus(player, dice)
        if (playerOnePreviousTurnWasFiveOfAKind && scoresFiveOfAKindNow) {
            unlockAchievements(setOf(Achievement.TWICE_IN_A_LIFETIME))
        }
        playerOnePreviousTurnWasFiveOfAKind = scoresFiveOfAKindNow
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
        update.progressed.forEach { AchievementEvents.emit(AchievementEvent.Progressed(it.achievement, it.previous, it.current)) }
    }

    private fun resetAchievementTracking() {
        diceRolledByPlayerOne = 0
        trailedIntoFinalRound = false
        ledIntoFinalRound = false
        playerOneTookExtraRoll = false
        playerOnePreviousTurnWasFiveOfAKind = false
        outOfRollsCupTaps = 0
        resetPerTurnTracking()
    }

    private fun resetPerTurnTracking() {
        previousRollFaces = null
        heldChangedSinceLastRoll = false
        fourOfAKindIndicesFromFirstRoll = null
        heldFourOfAKindThroughTurn = false
        fiveOfAKindSeenThisTurn = false
        facesAfterFirstRoll = null
        loadedDiceHeldIndices = null
        loadedDiceMatchedSecondRoll = false
        heldIndicesBeforeSecondRoll = null
        heldThroughBothRerolls = emptySet()
        everHeldAllFiveThisTurn = false
        holdUnholdCyclesByDieIndex.clear()
        hadScoringOptionAfterSecondRoll = false
        pendingCommitmentGroupValue = null
        pendingCommitmentGroupIndices = emptySet()
        commitmentGroupTainted = false
        lastReleasedCommitmentGroupValue = null
        outOfRollsCupTaps = 0
    }

    /** Whether player 1 finished [state] with the top score - "One More Time" reads this off the
     * game [startGame] is about to replace, mirroring how [AchievementEngine] itself treats a tie
     * at the top as a win, and its player-1-only rule. */
    private fun humanWonGame(state: GameState): Boolean {
        val playerOne = state.players.firstOrNull()?.takeIf { it.type == PlayerType.HUMAN } ?: return false
        return playerOne.totalScore == state.topScore
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

    private fun persistGameConfig(playerCount: Int, slots: List<PlayerSetupSlot>, turnTimer: TurnTimer, gameMode: GameMode) {
        val repository = settingsRepository ?: return
        viewModelScope.launch {
            repository.setPlayerCount(playerCount)
            repository.setTurnTimer(turnTimer)
            repository.setGameMode(gameMode)
            for (slot in slots) {
                // Slot 1 is always Human, so its type and difficulty aren't worth persisting.
                if (slot.slot == 1) continue
                repository.setPlayerType(slot.slot, slot.type)
                if (slot.type == PlayerType.AI) repository.setPlayerDifficulty(slot.slot, slot.difficulty)
            }
        }
    }

    // Suspending rather than launching its own coroutine: finishGame sequences this against the
    // leaderboard read that has to happen before it.
    private suspend fun persistHumanScores(state: GameState) {
        val repository = scoreRepository ?: return
        // A solo game has nobody to beat, so it's recorded with no outcome at all - the same
        // "doesn't count toward win/loss" treatment AchievementEngine gives the WIN_STREAK counter.
        val multiplayer = state.players.size > 1
        val topScore = state.topScore
        state.players.forEachIndexed { index, player ->
            if (player.type == PlayerType.HUMAN) {
                val won = if (multiplayer) player.totalScore == topScore else null
                // Index 0 is always the primary player ("You") - see AchievementEngine's class doc.
                repository.recordScore(
                    player.name,
                    player.totalScore,
                    won = won,
                    isPrimaryPlayer = index == 0,
                    fiveOfAKindCount = player.fiveOfAKindCount,
                )
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
                while (current.rollsRemaining > 0) {
                    // The delay doubles as the cup's shake animation window, same as the human tap
                    // handler in GameScreen - always exactly AI_STEP_DELAY_MS, whatever the
                    // difficulty, since it's true for its whole span and then false the moment the
                    // roll itself (never the hold decision below - see that comment) is published.
                    _aiRolling.value = true
                    try {
                        delay(AI_STEP_DELAY_MS)
                        current = GameEngine.rollDice(current, random)
                        setUndoSnapshot(null)
                        applyGameState(current, checkForAiTurn = false)
                    } finally {
                        _aiRolling.value = false
                    }

                    // Nothing left to decide on the turn's last roll - there's no further reroll to
                    // hold dice FOR.
                    if (current.rollsRemaining == 0) break

                    // Deliberately outside the block above: choosing what to hold happens once
                    // the cup has already stopped shaking and this roll's dice are already on
                    // screen, never while the shake itself is still playing. Hard's hold choice
                    // is an exhaustive search over every 32 hold/reroll subsets, each averaged
                    // over every possible reroll outcome - expensive enough that computing it
                    // during the shake (as this used to) stretched the shake's own on-screen
                    // duration out past Easy/Medium's, whose choices are next to free. Now every
                    // difficulty's shake is the same fixed length, and "thinking" is just a
                    // static pause with nothing animating - however long it takes, only the pause
                    // before the dice's held state updates changes, not any animation.
                    val holds = withContext(aiDispatcher) { AiTurnPlayer.chooseHolds(current) }
                    current = AiTurnPlayer.applyHolds(current, holds)
                    setUndoSnapshot(null)
                    applyGameState(current, checkForAiTurn = false)

                    // Every die is being kept, so a further roll would only ever reroll nothing
                    // (GameEngine.rollDice skips held dice) - stop here, same as a human choosing to
                    // score early with rolls still legally available. `rollsRemaining` is left
                    // exactly as the rules say: "x$rollsRemaining" on the cup means legal rolls
                    // still available, not how many the player intends to use, so this must never
                    // force it down to fake an early stop - it was doing exactly that before, and a
                    // roll that still had legal rerolls left was showing as none remaining.
                    if (holds.size == current.dice.size) break

                    // A beat with the cup settled and the result visible before the next roll's
                    // shake starts - without it, back-to-back rolls (routine for Easy, which never
                    // holds anything and so never gets to skip a roll) read as one continuous blur
                    // rather than distinct rolls. Only between rolls: the delay before the very
                    // first roll and before scoring are already paced by AI_STEP_DELAY_MS above/below.
                    delay(ROLL_GAP_MS)
                }
                delay(AI_STEP_DELAY_MS)
                // Also off the main thread: Hard's category choice compares against
                // CATEGORY_BASELINE, a `by lazy` average-over-every-outcome computed once per
                // process on whichever call touches it first - same cost/rationale as the hold
                // choice above.
                val category = withContext(aiDispatcher) { AiTurnPlayer.chooseCategory(current) }
                current = GameEngine.commitScore(current, category)
                setUndoSnapshot(null)
                applyGameState(current, checkForAiTurn = false)
            }
        }
    }

    companion object {
        private const val AI_STEP_DELAY_MS = 600L

        /** Pause between one roll settling and the next one's shake starting, within the same AI turn. */
        private const val ROLL_GAP_MS = 250L

        /** What `rollsRemaining` reads before any roll has happened this turn - the mode's full allowance. */
        private val GameState.fullRolls: Int
            get() = gameMode.rollsPerTurn

        /** What `rollsRemaining` reads once the first of a turn's rolls has been used. */
        private val GameState.rollsRemainingAfterFirst: Int
            get() = gameMode.rollsPerTurn - 1

        /** What `rollsRemaining` reads once the second of a turn's rolls has been used. */
        private val GameState.rollsRemainingAfterSecond: Int
            get() = gameMode.rollsPerTurn - 2

        /** Each die's face - number and colour - without whether it's held, for comparing one roll to another. */
        private fun List<Die>.faces(): List<Die> = map { Die(value = it.value, colour = it.colour) }

        private const val FOUR_OF_A_KIND_COUNT = 4
        private const val HOLD_UNHOLD_CYCLE_TARGET = 3
        private const val NO_MORE_ROLLS_TAP_TARGET = 3

        /** The exact roll "I Can Count!" is named for. */
        private val COUNTING_DICE_VALUES = listOf(1, 2, 3, 4, 5)

        /** [net.zodac.dicefive.ui.common.AppLogo]'s own dice, in its own order - "Product Placement". */
        private val LOGO_DICE_VALUES = listOf(2, 4, 5, 3, 6)

        /** Three 6s and two 5s, sorted - the exact roll "Fuller House" is named for. */
        private val FULLER_HOUSE_VALUES = listOf(5, 5, 6, 6, 6)

        /** Case-sensitive - "Big Fan"'s exact match, not just a case-insensitive namesake. */
        private const val ZODAC_PLAYER_NAME = "zodac"

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
