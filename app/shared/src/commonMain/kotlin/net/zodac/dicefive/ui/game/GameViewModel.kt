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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.AchievementEngine
import net.zodac.dicefive.game.AchievementUpdate
import net.zodac.dicefive.game.AiNameGenerator
import net.zodac.dicefive.game.AiTurnPlayer
import net.zodac.dicefive.game.StandardPerfectPlayTable
import net.zodac.dicefive.game.DiceScoring
import net.zodac.dicefive.game.GameAchievementContext
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.game.GameStartContext
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.game.ScoreCalculator
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.game.toTieBreakStats
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
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.hasGrownSunflower
import net.zodac.dicefive.model.isLuckOfTheIrish
import net.zodac.dicefive.ui.achievements.announce
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.GameVisualTheme
import net.zodac.dicefive.ui.game.style.StyleCatalog
import net.zodac.dicefive.ui.game.style.TableArt
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
    /** The length the turn timer modifier returns to when switched back on: the last one chosen, kept while [turnTimer] is [TurnTimer.NONE]. Never NONE. */
    val turnTimerLength: TurnTimer = TurnTimer.SECONDS_60,
    val rollModifiers: RollModifiers = RollModifiers(),
    /** The Number of Rolls value the modifier returns to when switched back on: the last one chosen, kept while [RollModifiers.rollsPerTurn] is null. */
    val rollsPerTurnLength: Int = RollModifiers.DEFAULT_ROLLS,
    /** The Extended Scores modifier: Two Pair, Evens and Odds join the scorecard. */
    val extendedScores: Boolean = false,
    /** Whether the Unlucky Dice modifier is on. Its [unluckyDice] settings are kept while it's off. */
    val unluckyDiceEnabled: Boolean = false,
    /** The Unlucky Dice odds and cap the modifier returns to when switched back on. */
    val unluckyDice: UnluckyDice = UnluckyDice(),
) {
    /** What a game started now plays with: the Unlucky Dice settings, or null with the modifier off. */
    val activeUnluckyDice: UnluckyDice?
        get() = unluckyDice.takeIf { unluckyDiceEnabled }

    companion object {
        const val MIN_PLAYERS = 1
        const val MAX_PLAYERS = 4

        /**
         * The longest name that fits its own tab in the in-game header without being ellipsised -
         * not a database or gameplay limit, a layout one. Every seat shares that one row, so the
         * more of them there are the less width (and, for a CPU seat, the less width left over
         * once its chip icon takes its own share) each tab - and so each name - gets.
         */
        fun maxPlayerNameLength(playerCount: Int): Int = when (playerCount) {
            1 -> 14
            2 -> 12
            3 -> 10
            else -> 8
        }

        /** How many characters' worth of width a CPU tab's chip icon and the gap after it cost -
         * a rough conversion, not a pixel measurement, but enough to pick a shorter [AiNameGenerator]
         * pool for a tighter player count rather than an AI name arriving pre-ellipsised where a
         * Human one at the same length wouldn't have been. */
        private const val CPU_ICON_ALLOWANCE = 2

        /** [maxPlayerNameLength], minus room for the CPU chip icon every AI's own tab also carries -
         * see [CPU_ICON_ALLOWANCE]. */
        fun maxAiNameLength(playerCount: Int): Int = maxPlayerNameLength(playerCount) - CPU_ICON_ALLOWANCE
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
    /** Whether this is a debug build - superuser mode never activates without it. */
    private val isDebugBuild: Boolean = false,
    /** Standard's perfect-play table, for Hard CPUs in Standard (see [StandardPerfectPlayTable]) - without it, they estimate. */
    private val standardPerfectPlay: (suspend () -> StandardPerfectPlayTable?)? = null,
) : ViewModel() {

    private val _setup = MutableStateFlow(GameSetupState())
    val setup: StateFlow<GameSetupState> = _setup.asStateFlow()

    /** Whether [setup] holds the player's saved choices yet - immediately true with no
     * [settingsRepository] to restore from. Until then the setup screen shows no form, rather
     * than the defaults it would otherwise draw and then switch away from. */
    private val _setupRestored = MutableStateFlow(settingsRepository == null)
    val setupRestored: StateFlow<Boolean> = _setupRestored.asStateFlow()

    private val _game = MutableStateFlow<GameState?>(null)
    val game: StateFlow<GameState?> = _game.asStateFlow()

    /** Mirrors the human dice cup's tap-driven shake animation for AI turns: unlike [rollDice],
     * [maybeStartAiTurn] updates dice directly rather than through a UI click handler, so nothing
     * would otherwise flip the cup/tray into their "rolling" pose for an AI player's rolls. */
    /**
     * How long a CPU's roll waits before landing - a human's tap waits the same, in `GameScreen`. Set by
     * the screen from `cupShakeMillis`, which shortens it only when reduced motion is on and there is no
     * sound or vibration to keep in step with; the full [CUP_SHAKE_MILLIS] otherwise.
     */
    @Volatile
    var cupShakeMillis: Long = CUP_SHAKE_MILLIS

    /**
     * Whether the screen animates the dice - tossing them onto the mat, dropping a released one back onto it.
     * False under reduced motion (the player's "Remove animations" or the system's), where they just snap to
     * where they end up, so a CPU has nothing to wait for. Set by the screen.
     */
    @Volatile
    var diceAnimated: Boolean = true

    /**
     * How long a CPU waits after a roll lands for the dice to finish tossing onto the mat before it holds,
     * rerolls or scores - `DICE_TOSS_MILLIS`, or 0 when the dice aren't animated ([diceAnimated]).
     * Settable so a test can pin it.
     */
    @Volatile
    var diceTossMillis: Long = DICE_TOSS_MILLIS.toLong()
        get() = if (diceAnimated) field else 0L

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
     * Holds the game's clocks while a dialog over it (the leave-game confirmation) is up, exactly as
     * if the game were off screen - see [setForeground]. Released when the dialog closes.
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
        if (state.isGameOver) return
        inProgressGameRepository?.save(state.copy(turnSecondsLeft = remaining))
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

    private val _aiRolling = MutableStateFlow(false)
    val aiRolling: StateFlow<Boolean> = _aiRolling.asStateFlow()

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

    val confirmBeforeLeavingGame: StateFlow<Boolean> = (settingsRepository?.confirmBeforeLeavingGame ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * The table's art - each saved Styles pick resolved through its catalog's unlockedById, so an
     * id nothing recognises, or a style still locked, draws that category's default - and its
     * sound, vibration and roll settings. Null until they've all loaded, so the board is never
     * drawn in the defaults for a frame before switching to the player's own; this view model is
     * scoped to the whole "play" graph and loads them eagerly, so by the time the board shows
     * they're normally long since in. With no repository, straight to the defaults.
     */
    val tableSettings: StateFlow<TableSettings?> = if (settingsRepository == null) {
        MutableStateFlow(TableSettings())
    } else {
        val visualTheme = combine(
            settingsRepository.diceStyleId,
            settingsRepository.diceCupStyleId,
            settingsRepository.tableBackgroundId,
            settingsRepository.diceMatId,
            achievementsRepository?.state ?: flowOf(AchievementsState()),
        ) { diceId, cupId, backgroundId, matId, achievements ->
            GameVisualTheme(
                diceStyle = DiceStyles.unlockedById(diceId, achievements),
                diceCupStyle = DiceCupStyles.unlockedById(cupId, achievements),
                background = TableBackgrounds.unlockedById(backgroundId, achievements),
                mat = DiceMats.unlockedById(matId, achievements),
            )
        }
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

    /** How many of player 1's own turns the turn timer has run out on this game - see
     * [Achievement.LUCK_OF_THE_DRAW]. A timed-out score
     * can't be undone, so this never needs counting back down. */
    private var playerOneTimeouts = 0

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
                // Built up off to the side and published in one update, then flagged restored: the
                // setup screen holds its form back until [setupRestored], so it never draws the
                // defaults (Standard mode, 2 players) for the frames these reads take and then
                // visibly switches to the saved choices.
                var restored = _setup.value
                // Restored first: every restored name is capped to whatever length fits *this*
                // player count's tabs, so that cap has to be known before any name is restored,
                // not applied against the default count of 2.
                repository.playerCount.first()?.let { savedCount ->
                    restored = restored.copy(
                        playerCount = savedCount.coerceIn(GameSetupState.MIN_PLAYERS, GameSetupState.MAX_PLAYERS),
                    )
                }
                val cap = GameSetupState.maxPlayerNameLength(restored.playerCount)
                val slots = restored.playerSlots.map { slot ->
                    // Capped on the way in, so a name saved before the length cap existed - or saved
                    // at a longer-lived player count - is trimmed rather than reappearing over-long.
                    var updated = slot.copy(name = (repository.playerNameFor(slot.slot).first() ?: slot.name).take(cap))
                    // Slot 1 is always Human, so its type and difficulty are never saved/restored.
                    if (slot.slot >= 2) {
                        repository.playerTypeFor(slot.slot).first()?.let { updated = updated.copy(type = it) }
                        repository.playerDifficultyFor(slot.slot).first()?.let { updated = updated.copy(difficulty = it) }
                    }
                    updated
                }
                restored = restored.copy(
                    playerSlots = slots,
                    turnTimer = repository.turnTimer.first(),
                    turnTimerLength = repository.turnTimerLength.first(),
                    rollModifiers = repository.rollModifiers.first(),
                    rollsPerTurnLength = repository.rollsPerTurnLength.first(),
                    extendedScores = repository.extendedScores.first(),
                    unluckyDiceEnabled = repository.unluckyDiceEnabled.first(),
                    unluckyDice = repository.unluckyDice.first(),
                    gameMode = repository.gameMode.first(),
                )
                _setup.value = restored
                _setupRestored.value = true
            }
        }
    }

    fun setPlayerCount(count: Int) {
        val newCount = count.coerceIn(GameSetupState.MIN_PLAYERS, GameSetupState.MAX_PLAYERS)
        _setup.update { it.copy(playerCount = newCount) }
        // A count change can only ever tighten or loosen every seat's cap at once, never just one
        // of them - so every name (not just the seat(s) just added/removed) is reclamped here,
        // including ones for currently-inactive seats beyond the new count, which keeps them
        // already-valid if the count is raised back before the game starts.
        val cap = GameSetupState.maxPlayerNameLength(newCount)
        _setup.update { state -> state.copy(playerSlots = state.playerSlots.map { it.copy(name = it.name.take(cap)) }) }
    }

    fun setPlayerType(slot: Int, type: PlayerType) {
        require(slot != 1) { "Player 1 is always Human" }
        updateSlot(slot) { it.copy(type = type) }
    }

    /**
     * Enforced here rather than only in the text field, so the cap holds for every path into a
     * name - including a longer one restored from a previous version's saved preferences - and at
     * whatever length fits the player count in the form right now (see
     * [GameSetupState.maxPlayerNameLength]).
     */
    fun setPlayerName(slot: Int, name: String) {
        val cap = GameSetupState.maxPlayerNameLength(_setup.value.playerCount)
        updateSlot(slot) { it.copy(name = name.take(cap)) }
    }

    fun setPlayerDifficulty(slot: Int, difficulty: Difficulty) {
        updateSlot(slot) { it.copy(difficulty = difficulty) }
    }

    fun setGameMode(gameMode: GameMode) {
        _setup.update { it.copy(gameMode = gameMode) }
    }

    fun setTurnTimer(turnTimer: TurnTimer) {
        _setup.update { it.copy(turnTimer = turnTimer, turnTimerLength = if (turnTimer == TurnTimer.NONE) it.turnTimerLength else turnTimer) }
    }

    /** Switches the Number of Rolls modifier on at [rolls], or off with null - the value is remembered either way. */
    fun setRollsPerTurn(rolls: Int?) {
        _setup.update {
            it.copy(
                rollModifiers = it.rollModifiers.copy(rollsPerTurn = rolls),
                rollsPerTurnLength = rolls ?: it.rollsPerTurnLength,
            )
        }
    }

    /** Switches the Stored Rolls modifier on or off. Its cap is kept while it's off. */
    fun setStoredRolls(enabled: Boolean) {
        _setup.update { it.copy(rollModifiers = it.rollModifiers.copy(storedRolls = enabled)) }
    }

    /** Sets the most rolls Stored Rolls may keep, or null for no cap. */
    fun setStoredRollsMax(max: Int?) {
        _setup.update { it.copy(rollModifiers = it.rollModifiers.copy(storedRollsMax = max)) }
    }

    /** Switches the Extended Scores modifier on or off. */
    fun setExtendedScores(enabled: Boolean) {
        _setup.update { it.copy(extendedScores = enabled) }
    }

    /** Switches the Unlucky Dice modifier on or off. Its odds and cap are kept while it's off. */
    fun setUnluckyDiceEnabled(enabled: Boolean) {
        _setup.update { it.copy(unluckyDiceEnabled = enabled) }
    }

    /** Sets the Unlucky Dice odds of a rolled die being locked, in percent. */
    fun setUnluckyOdds(oddsPercent: Int) {
        _setup.update { it.copy(unluckyDice = it.unluckyDice.copy(oddsPercent = oddsPercent.coerceIn(UnluckyDice.MIN_ODDS_PERCENT, UnluckyDice.MAX_ODDS_PERCENT))) }
    }

    /** Sets the most dice Unlucky Dice can lock on one roll. */
    fun setUnluckyMaxDice(maxDice: Int) {
        _setup.update { it.copy(unluckyDice = it.unluckyDice.copy(maxDice = maxDice.coerceIn(UnluckyDice.MIN_MAX_DICE, UnluckyDice.MAX_MAX_DICE))) }
    }

    /** Builds the initial [GameState] from the current setup form, generating AI names now. */
    fun startGame() {
        // Read before anything below overwrites it: "One More Time" is about the game THIS call is
        // replacing, not the one it's about to create.
        val previousGame = _game.value

        val setupState = _setup.value
        val activeSlots = setupState.playerSlots.take(setupState.playerCount)
        val aiNames = AiNameGenerator.generateNames(
            count = activeSlots.count { it.type == PlayerType.AI },
            playerCount = setupState.playerCount,
        ).iterator()
        val playerConfigs = activeSlots.map { slot ->
            val name = when (slot.type) {
                PlayerType.HUMAN -> slot.name.trim().ifBlank { "Player ${slot.slot}" }
                PlayerType.AI -> aiNames.next()
            }
            PlayerConfig(slot = slot.slot, type = slot.type, name = name, difficulty = slot.difficulty)
        }
        persistHumanNames(activeSlots)
        persistGameConfig(setupState)
        val turnTimer = setupState.turnTimer
        val rollModifiers = setupState.rollModifiers
        setUndoSnapshot(null)
        resetSuperuserMode()
        resetAchievementTracking()
        applyGameState(GameEngine.newGame(playerConfigs, setupState.gameMode, turnTimer, rollModifiers, setupState.extendedScores, setupState.activeUnluckyDice))
        prepareHardCpus()
        // "Full Table" is settled the moment four seats are taken - no need to make them play it out.
        checkInProgressAchievements()
        checkGameStartAchievements(
            customizedGameSettings = turnTimer != TurnTimer.NONE || rollModifiers.isActive || setupState.extendedScores ||
                setupState.unluckyDiceEnabled || setupState.gameMode != GameMode.default,
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
        resumedSecondsLeft = loaded.turnSecondsLeft
        applyGameState(loaded.copy(turnSecondsLeft = null))
        resumedSecondsLeft = null
        prepareHardCpus()
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
        // A roll can arrive after the turn it was tapped for has gone: the cup shakes before the
        // roll lands, and Undo in that window restores the previous turn - with no rolls left when
        // a turn has only one.
        if (state.rollsRemaining <= 0) return

        // Only player 1 - "You" - earns achievements; another human seat still plays normally
        // below, it just doesn't feed any of the tracking that leads to one.
        val isPlayerOneTurn = state.currentPlayerIndex == 0

        // Everything this roll earns is held back until the dice have landed - see heldBackRollUnlocks.
        if (isPlayerOneTurn) heldBackRollUnlocks = mutableSetOf()

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

        performRoll(state)
        if (isPlayerOneTurn) {
            checkFirstRollAchievements()
            checkPostRollAchievements(rollsRemainingBeforeRoll, diceBeforeRoll)
            announceRollAchievementsOnceLanded(
                // Greenfingers blooms on a roll, not a score: on the game's very last roll, before its last box is filled.
                bloomed = _game.value?.players?.firstOrNull()?.hasGrownSunflower == true,
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
            pausableDelay(diceTossMillis)
            unlockAchievements(earned)
            if (bloomed) checkInProgressAchievements()
        }
    }

    // Not undoable, same reasoning as rollDice: holding/unholding just selects what a future roll
    // will touch, it doesn't itself score anything.
    fun toggleHold(dieIndex: Int) {
        // A die locked by Unlucky Dice can't be held, and a tap on it is no hold-or-release to track.
        if (_game.value?.dice?.getOrNull(dieIndex)?.isUnlucky == true) return
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
        if (!isDebugBuild || !_superuserModeActive.value) return
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
        // The board only offers a box once the hand is whole (every hold slot filled, where only held
        // dice score) - a tap that gets here otherwise has nothing to score.
        if (_game.value?.hasFullHand == false) return
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

    /** A phone-shake roll actually happened - see [ShakeDetectorEffect]/[Achievement
     * .SHAKEN_NOT_TAPPED]. Only the achievement lives here: the roll itself is still driven
     * through the same [rollDice] call a cup tap uses, so this is purely a one-shot unlock,
     * same shape as [tapCupWithNoRollsLeft]. */
    fun onShakeRollDetected() {
        unlockAchievements(setOf(Achievement.SHAKEN_NOT_TAPPED))
    }

    /** The Top Hat cup's rabbit just peeked out - see [Achievement.MAGICIANS_SECRET]. Only on a human's
     * turn: the hat has to have been left alone by someone at the table, not tipped by an AI. */
    fun onRabbitSeen() {
        val state = _game.value ?: return
        if (state.currentPlayer?.type != PlayerType.HUMAN) return
        unlockAchievements(setOf(Achievement.MAGICIANS_SECRET))
    }

    /**
     * Every roll in the game - a human's via [rollDice],
     * and an AI's via [maybeStartAiTurn] - so they all land the same way. Not undoable, like
     * rolling always has been: only a committed score is. Returns the rolled state for the AI loop,
     * which carries its own copy of it.
     */
    private fun performRoll(state: GameState, checkForAiTurn: Boolean = true): GameState {
        val rolled = GameEngine.rollDice(state, random)
        setUndoSnapshot(null)
        applyGameState(rolled, checkForAiTurn)
        return rolled
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
                if (isDebugBuild) {
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
     * The turn timer running out: forfeits the rest of this turn's rolls (rolling first, if the
     * player hadn't yet, since a category can't be committed before that) and commits into the
     * the first open category (scoring zero if the dice don't match it).
     * A forced miss rather than picking the player's best option for them. Applies equally to an AI
     * seat that's taken too long to decide - Hard's exhaustive search is the only realistic way
     * this fires for one - cancelling its in-flight turn job first so it can't keep acting after
     * being timed out from under it.
     */
    private fun autoScoreOnTimeout() {
        var state = _game.value ?: return
        if (state.currentPlayer?.type == PlayerType.AI) {
            aiTurnJob?.cancel()
            aiTurnJob = null
            _aiRolling.value = false
        }
        // Only player 1 - "You" - earns achievements; another human seat can still be timed out,
        // it just doesn't feed Achievement tracking.
        val isPlayerOneTurn = state.currentPlayerIndex == 0

        if (state.phase != TurnPhase.ROLLED) {
            state = GameEngine.rollDice(state, random)
        }
        // Where only held dice score, the empty hold slots are filled for the player first.
        state = GameEngine.fillHand(state)
        val player = state.currentPlayer ?: return
        val category = ScoreCalculator.timeoutCategory(player, state.scoringDice)
        setUndoSnapshot(null)
        applyGameState(GameEngine.commitScore(state, category))
        if (isPlayerOneTurn) {
            playerOneTimeouts++
            unlockAchievements(setOf(Achievement.OUT_OF_TIME))
        }
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
        if (player.turnsLeft != 1) return

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
            val game = _game.value
            val players = game?.players.orEmpty()
            val gameMode = game?.gameMode ?: GameMode.default
            // Specifically a two-player game's P2 - "You" (P1, index 0) never counts, and neither
            // does P2 in a 3P/4P game, since Big Fan is about who's sitting across from you, not
            // just who's at the table.
            val playerTwo = players.getOrNull(1)
            val hasZodacAsPlayerTwo = players.size == 2 && playerTwo?.type == PlayerType.HUMAN && playerTwo.name == ZODAC_PLAYER_NAME
            val isLuckOfTheIrish = game?.isLuckOfTheIrish == true
            // A pick whose style is still locked can't count, since the default is drawn in its place.
            val achievements = repository.current()
            val playedNonDefaultStyle = isNonDefaultStyle(settings.diceStyleId, DiceStyles, achievements) ||
                isNonDefaultStyle(settings.diceCupStyleId, DiceCupStyles, achievements) ||
                isNonDefaultStyle(settings.tableBackgroundId, TableBackgrounds, achievements) ||
                isNonDefaultStyle(settings.diceMatId, DiceMats, achievements)
            val context = GameStartContext(
                playedNonDefaultStyle = playedNonDefaultStyle,
                hasHumanPlayerNamedZodac = hasZodacAsPlayerTwo,
                hasIrishPlayerOneInTricolour = isLuckOfTheIrish,
                customizedGameSettings = customizedGameSettings,
                gameMode = gameMode,
            )
            withAchievementLock {
                val update = AchievementEngine.evaluateAtGameStart(context, repository.current(), nowEpochMillis())
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
                val update = AchievementEngine.evaluateInProgress(state, repository.current(), nowEpochMillis())
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
            playerOneTimeouts = playerOneTimeouts,
        )
        withAchievementLock {
            val update = AchievementEngine.evaluate(state, context, repository.current(), nowEpochMillis())
            persistAndAnnounce(repository, update)
        }
    }

    /** Whether [styleIdFlow]'s current value draws anything other than [catalog]'s default, given
     * which styles [achievements] has unlocked - false (not "yes, non-default") when there's no
     * repository to read at all, e.g. in a test with no Context. */
    private suspend fun <T : TableArt> isNonDefaultStyle(
        styleIdFlow: Flow<String>?,
        catalog: StyleCatalog<T>,
        achievements: AchievementsState,
    ): Boolean = styleIdFlow?.first()?.let { catalog.unlockedById(it, achievements).id != catalog.default.id } ?: false

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
        // Where only held dice score, a roll isn't a hand - nothing is held straight out of the cup.
        if (state.gameMode.scoresHeldDiceOnly) return
        val player = state.currentPlayer ?: return

        // Not a die locked by Unlucky Dice: a hand with one missing is none of these.
        val dice = state.scoringDice
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
        // Where only held dice score, a roll isn't a hand: the feats judged on one as it lands (Natural
        // 5x, The Dice Hate Me, Almost Famous) aren't earned there. Seven dice would hand them out.
        // A roll with a die locked by Unlucky Dice isn't a whole hand either.
        val rollIsHand = !state.gameMode.scoresHeldDiceOnly && dice.none { it.isUnlucky }

        // Lucky Seven: every one of 'Stud' mode's seven dice showing the same number.
        if (state.gameMode == GameMode.STUD && values.toSet().size == 1) {
            unlockAchievements(setOf(Achievement.STUD_LUCKY_SEVEN))
        }

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
        if (rollIsHand &&
            rollsRemainingBeforeRoll < state.fullRolls &&
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
            fourOfAKindIndicesFromFirstRoll = fourOfAKindValue?.takeIf { rollIsHand }?.let { value ->
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
        if (!rollIsHand) {
            hadScoringOptionAfterSecondRoll = false
        } else if (rollsRemainingBeforeRoll == state.rollsRemainingAfterFirst) {
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
        // "All five" is every hold slot - every die, unless the mode rolls more dice than it scores.
        val fullHand = state.gameMode.scoringDiceCount
        when {
            heldIndices.size == fullHand -> everHeldAllFiveThisTurn = true
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
                if (heldIndices.size == fullHand) commitmentGroupTainted = true
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
        val dice = state.scoringDice
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
        // The hand being scored - every die, or where only held dice score, the held ones.
        val dice = state.scoringDice

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
        // were spent (see the fullRolls/rollsRemainingAfter* helpers) - and there has to have
        // been a reroll to spend at all, or a one-roll turn would hand it to any
        // first-roll 4x. Whether the 5x could even have been scored doesn't matter - only that it
        // was rolled for and missed.
        if (state.fullRolls > 1 &&
            fourOfAKindIndicesFromFirstRoll != null &&
            heldFourOfAKindThroughTurn &&
            !fiveOfAKindSeenThisTurn &&
            state.rollsRemaining == 0
        ) {
            unlockAchievements(setOf(Achievement.ALMOST_FAMOUS))
        }

        // Empty House: the worst full house there is - three 1s and two 2s specifically, which a
        // 5x-as-joker full house (all five dice the same value) can never produce.
        if (category == ScoreCategory.FULL_HOUSE && dice.map { it.value }.sorted() == EMPTY_HOUSE_VALUES) {
            unlockAchievements(setOf(Achievement.EMPTY_HOUSE))
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
        // there's no such thing as scoring one of those without a die still on the felt counting. A
        // zero score means the category failed outright rather than the player choosing to let the
        // held die go, so it doesn't count.
        if (category in PlayerState.UPPER_CATEGORIES && DiceScoring.score(category, dice) > 0) {
            val target = PlayerState.UPPER_CATEGORIES.indexOf(category) + 1
            if (heldThroughBothRerolls.any { state.dice[it].value != target }) {
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
        heldBackRollUnlocks?.let {
            it += achievements
            return
        }
        viewModelScope.launch {
            withAchievementLock {
                val before = repository.current()
                val update = AchievementEngine.unlockNow(achievements, before, nowEpochMillis())
                persistAndAnnounce(repository, update)
            }
        }
    }

    private suspend fun persistAndAnnounce(repository: AchievementStore, update: AchievementUpdate) {
        if (update.isEmpty) return
        // Stored before anything is announced, so a banner can never outlive its unlock.
        repository.record(update.unlockedAt(), update.counters)
        announce(update)
    }

    private fun resetAchievementTracking() {
        diceRolledByPlayerOne = 0
        trailedIntoFinalRound = false
        ledIntoFinalRound = false
        playerOneTookExtraRoll = false
        playerOneTimeouts = 0
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
                    repository.setPlayerName(slot.slot, slot.name.trim().ifBlank { "Player ${slot.slot}" })
                }
            }
        }
    }

    private fun persistGameConfig(setup: GameSetupState) {
        val repository = settingsRepository ?: return
        val slots = setup.playerSlots.take(setup.playerCount)
        viewModelScope.launch {
            repository.setPlayerCount(setup.playerCount)
            repository.setTurnTimer(setup.turnTimer)
            repository.setTurnTimerLength(setup.turnTimerLength)
            repository.setRollModifiers(setup.rollModifiers, setup.rollsPerTurnLength)
            repository.setExtendedScores(setup.extendedScores)
            repository.setUnluckyDice(setup.unluckyDiceEnabled, setup.unluckyDice)
            repository.setGameMode(setup.gameMode)
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
                // Feeds the leaderboard's own tie-break ordering (see TieBreak.kt) - unrelated to
                // [won] above, which keeps its existing "a tie at the top counts as a win" rule for
                // achievements/statistics untouched.
                val tieBreakStats = player.toTieBreakStats()
                // Index 0 is always the primary player ("You") - see AchievementEngine's class doc.
                repository.recordScore(
                    player.name,
                    tieBreakStats,
                    won = won,
                    isPrimaryPlayer = index == 0,
                    onLeaderboard = state.countsOnLeaderboard,
                )
            }
        }
    }

    // Queued and conflated by the repository - see InProgressGameRepository.
    private fun persistInProgressGame(state: GameState) {
        val repository = inProgressGameRepository ?: return
        if (state.isGameOver) repository.clear() else repository.save(state)
    }

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
            if (state.gameMode == GameMode.STANDARD && !state.extendedScores) standardPerfectPlay?.invoke()
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
     * ([toggleHoldsOneByOne]), as a hand would; with them not animated ([diceAnimated] off) that stepping is just
     * motion, so every change lands at once, published together. Returns the state with all of them made.
     */
    private suspend fun changeHolds(state: GameState, toRelease: List<Int>, toHold: List<Int>): GameState {
        if (!diceAnimated) {
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
    private suspend fun perfectPlayFor(state: GameState): StandardPerfectPlayTable? {
        val player = state.currentPlayer ?: return null
        if (state.gameMode != GameMode.STANDARD || state.extendedScores || player.type != PlayerType.AI || player.difficulty != Difficulty.HARD) return null
        return standardPerfectPlay?.invoke()
    }

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
                        pausableDelay(cupShakeMillis)
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
                    pausableDelay(diceTossMillis)
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
                    // Every die is being kept, so the turn is over: stop before the holds are applied, as
                    // showing every die held just before scoring is noise. A further roll would only ever
                    // reroll nothing (GameEngine.rollDice skips held dice) - stop here, same as a human choosing to
                    // score early with rolls still legally available. `rollsRemaining` is left
                    // exactly as the rules say: "x$rollsRemaining" on the cup means legal rolls
                    // still available, not how many the player intends to use, so this must never
                    // force it down to fake an early stop - it was doing exactly that before, and a
                    // roll that still had legal rerolls left was showing as none remaining.
                    if (holds.size == current.dice.size) break

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
                    pausableDelay(if (releasedAny && diceAnimated) RELEASE_GAP_MS else ROLL_GAP_MS)
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
                // CATEGORY_BASELINES, a `by lazy` average-over-every-outcome computed once per
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

        /** What `rollsRemaining` reads before any roll has happened this turn - the turn's full allowance, stored rolls included. */
        private val GameState.fullRolls: Int
            get() = turnRolls

        /** What `rollsRemaining` reads once the first of a turn's rolls has been used. */
        private val GameState.rollsRemainingAfterFirst: Int
            get() = turnRolls - 1

        /** What `rollsRemaining` reads once the second of a turn's rolls has been used. */
        private val GameState.rollsRemainingAfterSecond: Int
            get() = turnRolls - 2

        /** Each die's face - number and colour - without whether it's held, for comparing one roll to another. */
        private fun List<Die>.faces(): List<Die> = map { Die(value = it.value, colour = it.colour) }

        private const val FOUR_OF_A_KIND_COUNT = 4
        private const val HOLD_UNHOLD_CYCLE_TARGET = 3
        private const val NO_MORE_ROLLS_TAP_TARGET = 3

        /** The exact roll "I Can Count!" is named for. */
        private val COUNTING_DICE_VALUES = listOf(1, 2, 3, 4, 5)

        /** [net.zodac.dicefive.ui.common.AppLogo]'s own dice, in its own order - "Product Placement". */
        private val LOGO_DICE_VALUES = listOf(2, 4, 5, 3, 6)

        /** Three 1s and two 2s, sorted - the exact roll "Empty House" is named for. */
        private val EMPTY_HOUSE_VALUES = listOf(1, 1, 1, 2, 2)

        /** Three 6s and two 5s, sorted - the exact roll "Fuller House" is named for. */
        private val FULLER_HOUSE_VALUES = listOf(5, 5, 6, 6, 6)

        /** Case-sensitive - "Big Fan"'s exact match, not just a case-insensitive namesake. */
        private const val ZODAC_PLAYER_NAME = "zodac"

        /** Builds a [GameViewModel] backed by real Room/DataStore persistence. */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GameViewModel(
                    scoreRepository = container.scoreRepository,
                    settingsRepository = container.settingsRepository,
                    inProgressGameRepository = container.inProgressGameRepository,
                    achievementsRepository = container.achievementsRepository,
                    isDebugBuild = container.buildInfo.isDebug,
                    standardPerfectPlay = container::standardPerfectPlayTable,
                )
            }
        }
    }
}
