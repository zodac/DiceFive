package net.zodac.dicefive.ui.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.AiNameGenerator
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.model.duplicateHumanNameSlots
import net.zodac.dicefive.model.maxPlayerNameLength
import net.zodac.dicefive.model.resolvePlayerName
import net.zodac.dicefive.model.sanitizePlayerName
import net.zodac.dicefive.model.takeNameWidth

/**
 * A configured-but-not-yet-started player slot on the setup screen. Slot 1
 * is always [PlayerType.HUMAN] (1P mode forces the user to be human).
 */
data class PlayerSetupSlot(
    val slot: Int,
    val type: PlayerType = PlayerType.HUMAN,
    /** What the player typed. Blank means the default name ("Player 2" and its translations): the field shows it as a hint, and [GameViewModel.startGame] fills it in. */
    val name: String = "",
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /** Distinct per seat by default; [GameSetupForm.setPlayerColour] keeps all four slots distinct. */
    val colour: PlayerColour = PlayerColour.defaultFor(slot - 1),
)

data class GameSetupState(
    val playerCount: Int = 2,
    val playerSlots: List<PlayerSetupSlot> = (1..GameState.MAX_PLAYERS).map { PlayerSetupSlot(slot = it) },
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
    /**
     * Whether the game mode and modifiers can be changed: only once a game has been finished
     * ([SettingsRepository.firstGameCompleted]). Until then the form plays Standard with no modifiers, and the saved
     * picks are neither restored nor overwritten.
     */
    val modesUnlocked: Boolean = false,
) {
    /** What a game started now plays with: the Unlucky Dice settings, or null with the modifier off. */
    val activeUnluckyDice: UnluckyDice?
        get() = unluckyDice.takeIf { unluckyDiceEnabled }

    /** The seats a game started now has: the first [playerCount] of [playerSlots]. */
    val activeSlots: List<PlayerSetupSlot>
        get() = playerSlots.take(playerCount)

    /**
     * The shown seats whose Human names clash ([duplicateHumanNameSlots]), each blank one counted as its [defaultName] - what
     * it becomes at [GameViewModel.startGame]. While there are any, the game can't start.
     */
    fun duplicateNameSlots(defaultName: (slot: Int) -> String): Set<Int> = duplicateHumanNameSlots(
        activeSlots.map { PlayerConfig(slot = it.slot, type = it.type, name = resolvePlayerName(it.name, defaultName(it.slot))) },
    )

    /** This form, saved for the next game in a single write. */
    suspend fun saveTo(settingsRepository: SettingsRepository) {
        val setup = this
        settingsRepository.batch {
            setPlayerCount(setup.playerCount)
            // Locked, the form shows the defaults rather than the saved picks, which are kept for when it unlocks.
            if (setup.modesUnlocked) {
                setTurnTimer(setup.turnTimer)
                setTurnTimerLength(setup.turnTimerLength)
                setRollModifiers(setup.rollModifiers, setup.rollsPerTurnLength)
                setExtendedScores(setup.extendedScores)
                setUnluckyDice(setup.unluckyDiceEnabled, setup.unluckyDice)
                setGameMode(setup.gameMode)
            }
            // All four, not just the shown ones: they're kept distinct together (see setPlayerColour).
            for (slot in setup.playerSlots) setPlayerColour(slot.slot, slot.colour)
            for (slot in setup.activeSlots) {
                if (slot.type == PlayerType.HUMAN) setPlayerName(slot.slot, slot.name.trim())
                // Slot 1 is always Human, so its type and difficulty aren't worth persisting.
                if (slot.slot == 1) continue
                setPlayerType(slot.slot, slot.type)
                if (slot.type == PlayerType.AI) setPlayerDifficulty(slot.slot, slot.difficulty)
            }
        }
    }
}

/**
 * The New Game form: what it shows ([setup]), every change the screen makes to it, and how it's read back from the
 * settings ([restore]). Its rules - a name's cap for the player count, four distinct colours,
 * a modifier's value kept while it's off - live here; starting a game from it is [GameViewModel]'s, which saves it
 * ([GameSetupState.saveTo]) for the next.
 */
class GameSetupForm {

    private val _setup = MutableStateFlow(GameSetupState())
    val setup: StateFlow<GameSetupState> = _setup.asStateFlow()

    /** Whether [setup] holds the player's saved choices yet. Until then the setup screen shows no form, rather
     * than the defaults it would otherwise draw and then switch away from. */
    private val _setupRestored = MutableStateFlow(false)
    val setupRestored: StateFlow<Boolean> = _setupRestored.asStateFlow()

    /**
     * Fills the form from [saved] and flags it restored. Built up off to the side and published in one update: the
     * setup screen holds its form back until [setupRestored], so it never draws the defaults (Standard mode, 2 players)
     * for the frames the read takes and then visibly switches to the saved choices.
     */
    fun restore(saved: SettingsRepository.Snapshot) {
        var restored = _setup.value
        // Restored first: every restored name is capped to whatever length fits *this*
        // player count's tabs, so that cap has to be known before any name is restored,
        // not applied against the default count of 2.
        saved.playerCount?.let { savedCount ->
            restored = restored.copy(
                playerCount = savedCount.coerceIn(GameState.MIN_PLAYERS, GameState.MAX_PLAYERS),
            )
        }
        val cap = maxPlayerNameLength(restored.playerCount)
        val slots = restored.playerSlots.map { slot ->
            // Capped on the way in, so a name saved at a player count with wider tabs is trimmed
            // rather than reappearing over-long.
            val savedName = saved.playerNameFor(slot.slot)
            var updated = slot.copy(name = (savedName ?: slot.name).takeNameWidth(cap))
            // Slot 1 is always Human, so its type and difficulty are never saved/restored.
            if (slot.slot >= 2) {
                saved.playerTypeFor(slot.slot)?.let { updated = updated.copy(type = it) }
                saved.playerDifficultyFor(slot.slot)?.let { updated = updated.copy(difficulty = it) }
            }
            updated
        }
        // A saved set that isn't four different colours (never written by this app, but a hand-edited
        // or partly written store could) is dropped for the defaults, so no two players ever match.
        val colours = slots.map { slot -> saved.playerColourFor(slot.slot) }
        val restoredColours = colours.takeIf { savedColours -> savedColours.none { it == null } && savedColours.toSet().size == savedColours.size }
        restored = restored.copy(
            playerSlots = if (restoredColours == null) slots else slots.mapIndexed { i, slot -> slot.copy(colour = restoredColours[i]!!) },
            modesUnlocked = saved.firstGameCompleted,
        )
        if (restored.modesUnlocked) restored = restored.copy(
            turnTimer = saved.turnTimer,
            turnTimerLength = saved.turnTimerLength,
            rollModifiers = saved.rollModifiers,
            rollsPerTurnLength = saved.rollsPerTurnLength,
            extendedScores = saved.extendedScores,
            unluckyDiceEnabled = saved.unluckyDiceEnabled,
            unluckyDice = saved.unluckyDice,
            gameMode = saved.gameMode,
        )
        _setup.value = restored
        _setupRestored.value = true
    }

    /** Unlocks the game modes and modifiers, once a game has been finished. Returns whether they were locked until now. */
    fun unlockModes(): Boolean {
        if (_setup.value.modesUnlocked) return false
        _setup.update { it.copy(modesUnlocked = true) }
        return true
    }

    fun setPlayerCount(count: Int) {
        val newCount = count.coerceIn(GameState.MIN_PLAYERS, GameState.MAX_PLAYERS)
        _setup.update { it.copy(playerCount = newCount) }
        // A count change can only ever tighten or loosen every seat's cap at once, never just one
        // of them - so every name (not just the seat(s) just added/removed) is reclamped here,
        // including ones for currently-inactive seats beyond the new count, which keeps them
        // already-valid if the count is raised back before the game starts.
        val cap = maxPlayerNameLength(newCount)
        _setup.update { state -> state.copy(playerSlots = state.playerSlots.map { it.copy(name = it.name.takeNameWidth(cap)) }) }
    }

    fun setPlayerType(slot: Int, type: PlayerType) {
        require(slot != 1) { "Player 1 is always Human" }
        updateSlot(slot) { it.copy(type = type) }
    }

    /**
     * Enforced here rather than only in the text field, so the cap holds for every path into a
     * name - including a longer one restored from a previous version's saved preferences - and at
     * whatever length fits the player count in the form right now (see
     * [maxPlayerNameLength]).
     */
    fun setPlayerName(slot: Int, name: String) {
        val cap = maxPlayerNameLength(_setup.value.playerCount)
        updateSlot(slot) { it.copy(name = sanitizePlayerName(name).takeNameWidth(cap)) }
    }

    /**
     * Gives [slot] the colour, and its old one to whichever slot held it - every slot, shown or not, so
     * the four are always distinct and raising the player count can never produce two of a colour.
     */
    fun setPlayerColour(slot: Int, colour: PlayerColour) {
        _setup.update { state ->
            val previous = state.playerSlots.firstOrNull { it.slot == slot }?.colour ?: return@update state
            state.copy(
                playerSlots = state.playerSlots.map {
                    when {
                        it.slot == slot -> it.copy(colour = colour)
                        it.colour == colour -> it.copy(colour = previous)
                        else -> it
                    }
                },
            )
        }
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
        _setup.update { it.copy(unluckyDice = it.unluckyDice.copy(oddsPercent = oddsPercent.coerceIn(UnluckyDice.ODDS_PERCENT))) }
    }

    /** Sets the most dice Unlucky Dice can lock on one roll. */
    fun setUnluckyMaxDice(maxDice: Int) {
        _setup.update { it.copy(unluckyDice = it.unluckyDice.copy(maxDice = maxDice.coerceIn(UnluckyDice.MAX_DICE))) }
    }

    /**
     * The players a game started now has: each Human seat with no name typed under [defaultName] for its slot, and
     * each CPU under a name generated now.
     */
    fun playerConfigs(defaultName: (slot: Int) -> String): List<PlayerConfig> {
        val setup = _setup.value
        val aiNames = AiNameGenerator.generateNames(
            count = setup.activeSlots.count { it.type == PlayerType.AI },
            playerCount = setup.playerCount,
        ).iterator()
        return setup.activeSlots.map { slot ->
            val name = when (slot.type) {
                PlayerType.HUMAN -> resolvePlayerName(slot.name, defaultName(slot.slot))
                PlayerType.AI -> aiNames.next()
            }
            PlayerConfig(slot = slot.slot, type = slot.type, name = name, difficulty = slot.difficulty, colour = slot.colour)
        }
    }

    /** [playerConfigs], every player unnamed - for drawing the game the form would start, not starting it. */
    fun unnamedPlayerConfigs(): List<PlayerConfig> = _setup.value.activeSlots.map { slot ->
        PlayerConfig(slot = slot.slot, type = slot.type, name = "", difficulty = slot.difficulty, colour = slot.colour)
    }

    private fun updateSlot(slot: Int, transform: (PlayerSetupSlot) -> PlayerSetupSlot) {
        _setup.update { state ->
            state.copy(playerSlots = state.playerSlots.map { if (it.slot == slot) transform(it) else it })
        }
    }
}
