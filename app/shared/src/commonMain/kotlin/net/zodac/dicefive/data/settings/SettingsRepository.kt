package net.zodac.dicefive.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.model.TurnTimer

/**
 * DataStore-backed settings: the last-used name/type/difficulty (User/CPU) for each player slot
 * (1-4, slot 1 always Human) plus the last-used player count, so returning to setup pre-fills it,
 * whether leaving an in-progress game needs a confirmation, and whether sound effects/vibration
 * are enabled.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    /** [transform] of the settings as they change - only when its own value does, not on a write to another setting. */
    private fun <T> read(transform: (Preferences) -> T): Flow<T> = dataStore.data.map(transform).distinctUntilChanged()

    /** [read], of a [Snapshot]. */
    private fun <T> readSnapshot(transform: (Snapshot) -> T): Flow<T> = read { transform(Snapshot(it)) }

    /** Every setting as it is now, in one read - for reading many together, as the setup form does. */
    suspend fun snapshot(): Snapshot = Snapshot(dataStore.data.first())

    /**
     * The settings at one moment, each read as its own flow reads it: the player slots and the setup form's last
     * choices. [SettingsRepository.snapshot] reads one; the flows of the same names are each of these as it changes.
     */
    class Snapshot internal constructor(private val prefs: Preferences) {

        fun playerNameFor(slot: Int): String? = prefs[playerNameKey(slot)]

        fun playerTypeFor(slot: Int): PlayerType? =
            prefs[playerTypeKey(slot)]?.let { raw -> runCatching { PlayerType.valueOf(raw) }.getOrNull() }

        fun playerDifficultyFor(slot: Int): Difficulty? =
            prefs[playerDifficultyKey(slot)]?.let { raw -> runCatching { Difficulty.valueOf(raw) }.getOrNull() }

        fun playerColourFor(slot: Int): PlayerColour? =
            prefs[playerColourKey(slot)]?.let { raw -> runCatching { PlayerColour.valueOf(raw) }.getOrNull() }

        val playerCount: Int? get() = prefs[PLAYER_COUNT_KEY]

        val turnTimer: TurnTimer get() = prefs[TURN_TIMER_KEY]?.let { raw -> TurnTimer.parse(raw) } ?: TurnTimer.NONE

        val turnTimerLength: TurnTimer
            get() = prefs[TURN_TIMER_LENGTH_KEY]?.let { raw -> TurnTimer.parse(raw) }?.takeIf { it != TurnTimer.NONE } ?: TurnTimer.SECONDS_60

        val rollModifiers: RollModifiers
            get() = RollModifiers(
                rollsPerTurn = prefs[ROLLS_PER_TURN_KEY]?.takeIf { it in RollModifiers.MIN_ROLLS..RollModifiers.MAX_ROLLS },
                storedRolls = prefs[STORED_ROLLS_KEY] ?: false,
                storedRollsMax = prefs[STORED_ROLLS_MAX_KEY]?.takeIf { it >= 0 },
            )

        val rollsPerTurnLength: Int
            get() = prefs[ROLLS_PER_TURN_LENGTH_KEY]
                ?.takeIf { it in RollModifiers.MIN_ROLLS..RollModifiers.MAX_ROLLS } ?: RollModifiers.DEFAULT_ROLLS

        val extendedScores: Boolean get() = prefs[EXTENDED_SCORES_KEY] ?: false

        val unluckyDiceEnabled: Boolean get() = prefs[UNLUCKY_DICE_KEY] ?: false

        val unluckyDice: UnluckyDice
            get() = UnluckyDice(
                oddsPercent = prefs[UNLUCKY_ODDS_KEY]
                    ?.takeIf { it in UnluckyDice.MIN_ODDS_PERCENT..UnluckyDice.MAX_ODDS_PERCENT && it % UnluckyDice.ODDS_STEP_PERCENT == 0 }
                    ?: UnluckyDice.DEFAULT_ODDS_PERCENT,
                maxDice = prefs[UNLUCKY_MAX_DICE_KEY]?.takeIf { it in UnluckyDice.MIN_MAX_DICE..UnluckyDice.MAX_MAX_DICE }
                    ?: UnluckyDice.DEFAULT_MAX_DICE,
            )

        val gameMode: GameMode get() = prefs[GAME_MODE_KEY]?.let { GameMode.fromId(it) } ?: GameMode.default
    }

    fun playerNameFor(slot: Int): Flow<String?> = readSnapshot { it.playerNameFor(slot) }

    suspend fun setPlayerName(slot: Int, name: String) {
        dataStore.edit { it[playerNameKey(slot)] = name }
    }

    fun playerTypeFor(slot: Int): Flow<PlayerType?> = readSnapshot { it.playerTypeFor(slot) }

    suspend fun setPlayerType(slot: Int, type: PlayerType) {
        dataStore.edit { it[playerTypeKey(slot)] = type.name }
    }

    fun playerDifficultyFor(slot: Int): Flow<Difficulty?> = readSnapshot { it.playerDifficultyFor(slot) }

    suspend fun setPlayerDifficulty(slot: Int, difficulty: Difficulty) {
        dataStore.edit { it[playerDifficultyKey(slot)] = difficulty.name }
    }

    fun playerColourFor(slot: Int): Flow<PlayerColour?> = readSnapshot { it.playerColourFor(slot) }

    suspend fun setPlayerColour(slot: Int, colour: PlayerColour) {
        dataStore.edit { it[playerColourKey(slot)] = colour.name }
    }

    val playerCount: Flow<Int?> = readSnapshot { it.playerCount }

    suspend fun setPlayerCount(count: Int) {
        dataStore.edit { it[PLAYER_COUNT_KEY] = count }
    }

    val turnTimer: Flow<TurnTimer> = readSnapshot { it.turnTimer }

    suspend fun setTurnTimer(turnTimer: TurnTimer) {
        dataStore.edit { it[TURN_TIMER_KEY] = turnTimer.name }
    }

    /**
     * The turn timer length last chosen, kept while the timer is switched off ([turnTimer] is then
     * [TurnTimer.NONE]) so switching it back on restores it. 60 seconds until one is chosen.
     */
    val turnTimerLength: Flow<TurnTimer> = readSnapshot { it.turnTimerLength }

    suspend fun setTurnTimerLength(length: TurnTimer) {
        dataStore.edit { it[TURN_TIMER_LENGTH_KEY] = length.name }
    }

    /**
     * The roll modifiers the setup form last started a game with. The Number of Rolls length is read
     * separately ([rollsPerTurnLength]) because it's kept while that modifier is off.
     */
    val rollModifiers: Flow<RollModifiers> = readSnapshot { it.rollModifiers }

    /** The Number of Rolls value last chosen, kept while the modifier is off so switching it back on restores it. */
    val rollsPerTurnLength: Flow<Int> = readSnapshot { it.rollsPerTurnLength }

    suspend fun setRollModifiers(modifiers: RollModifiers, rollsPerTurnLength: Int) {
        dataStore.edit { prefs ->
            modifiers.rollsPerTurn?.let { prefs[ROLLS_PER_TURN_KEY] = it } ?: prefs.remove(ROLLS_PER_TURN_KEY)
            prefs[ROLLS_PER_TURN_LENGTH_KEY] = rollsPerTurnLength
            prefs[STORED_ROLLS_KEY] = modifiers.storedRolls
            modifiers.storedRollsMax?.let { prefs[STORED_ROLLS_MAX_KEY] = it } ?: prefs.remove(STORED_ROLLS_MAX_KEY)
        }
    }

    /** Whether the Extended Scores modifier was on the last time the setup form started a game. */
    val extendedScores: Flow<Boolean> = readSnapshot { it.extendedScores }

    suspend fun setExtendedScores(enabled: Boolean) {
        dataStore.edit { it[EXTENDED_SCORES_KEY] = enabled }
    }

    /** Whether the Unlucky Dice modifier was on the last time the setup form started a game. */
    val unluckyDiceEnabled: Flow<Boolean> = readSnapshot { it.unluckyDiceEnabled }

    /**
     * The Unlucky Dice odds and cap the setup form last had, kept while the modifier is off so switching it back
     * on restores them. A stored value outside the allowed range - or one that isn't a step - falls back to the default.
     */
    val unluckyDice: Flow<UnluckyDice> = readSnapshot { it.unluckyDice }

    suspend fun setUnluckyDice(enabled: Boolean, settings: UnluckyDice) {
        dataStore.edit { prefs ->
            prefs[UNLUCKY_DICE_KEY] = enabled
            prefs[UNLUCKY_ODDS_KEY] = settings.oddsPercent
            prefs[UNLUCKY_MAX_DICE_KEY] = settings.maxDice
        }
    }

    /** The mode the setup form last started a game in - read back by id, falling back to the default
     * for one nothing recognises (see [GameMode.id]). */
    val gameMode: Flow<GameMode> = readSnapshot { it.gameMode }

    suspend fun setGameMode(gameMode: GameMode) {
        dataStore.edit { it[GAME_MODE_KEY] = gameMode.id }
    }

    val confirmBeforeLeavingGame: Flow<Boolean> =
        read { prefs -> prefs[CONFIRM_BEFORE_LEAVING_GAME_KEY] ?: true }

    suspend fun setConfirmBeforeLeavingGame(confirm: Boolean) {
        dataStore.edit { it[CONFIRM_BEFORE_LEAVING_GAME_KEY] = confirm }
    }

    val soundEnabled: Flow<Boolean> = read { prefs -> prefs[SOUND_ENABLED_KEY] ?: true }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[SOUND_ENABLED_KEY] = enabled }
    }

    val vibrationEnabled: Flow<Boolean> = read { prefs -> prefs[VIBRATION_ENABLED_KEY] ?: true }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[VIBRATION_ENABLED_KEY] = enabled }
    }

    /** The player's "Animations" level - see [AnimationLevel]. */
    val animationLevel: Flow<AnimationLevel> = read { prefs ->
        prefs[ANIMATION_LEVEL_KEY]?.let { raw -> runCatching { AnimationLevel.valueOf(raw) }.getOrNull() } ?: AnimationLevel.default
    }

    suspend fun setAnimationLevel(level: AnimationLevel) {
        dataStore.edit { it[ANIMATION_LEVEL_KEY] = level.name }
    }

    // Style ids, not the ui.game.style types themselves - this is the data layer, and resolving an
    // id to a concrete DiceStyle/DiceCupStyle/TableBackground is the Styles screen's job (via its
    // catalog). The literal defaults below must match ui.game.style's DiceStyles/DiceCupStyles/
    // TableBackgrounds/DiceMats/ScoreFrames.default ids.
    val diceStyleId: Flow<String> = read { prefs -> prefs[DICE_STYLE_ID_KEY] ?: "classic_ivory" }

    suspend fun setDiceStyleId(id: String) {
        dataStore.edit { it[DICE_STYLE_ID_KEY] = id }
    }

    val diceCupStyleId: Flow<String> = read { prefs -> prefs[DICE_CUP_STYLE_ID_KEY] ?: "classic_gold" }

    suspend fun setDiceCupStyleId(id: String) {
        dataStore.edit { it[DICE_CUP_STYLE_ID_KEY] = id }
    }

    val tableBackgroundId: Flow<String> = read { prefs -> prefs[TABLE_BACKGROUND_ID_KEY] ?: "classic_navy" }

    suspend fun setTableBackgroundId(id: String) {
        dataStore.edit { it[TABLE_BACKGROUND_ID_KEY] = id }
    }

    val diceMatId: Flow<String> = read { prefs -> prefs[DICE_MAT_ID_KEY] ?: "classic_blue" }

    suspend fun setDiceMatId(id: String) {
        dataStore.edit { it[DICE_MAT_ID_KEY] = id }
    }

    // The frame round the player whose turn it is - "classic_ring" is ScoreFrames.default's id.
    val scoreFrameId: Flow<String> = read { prefs -> prefs[SCORE_FRAME_ID_KEY] ?: "classic_ring" }

    suspend fun setScoreFrameId(id: String) {
        dataStore.edit { it[SCORE_FRAME_ID_KEY] = id }
    }

    // The Styles page's fitted size for the screen it was measured on, as the Styles screen writes it -
    // opaque here. Null until it's first measured.
    val stylesPageFit: Flow<String?> = read { prefs -> prefs[STYLES_PAGE_FIT_KEY] }

    suspend fun setStylesPageFit(fit: String) {
        dataStore.edit { it[STYLES_PAGE_FIT_KEY] = fit }
    }

    private companion object {
        val CONFIRM_BEFORE_LEAVING_GAME_KEY = booleanPreferencesKey("confirm_before_leaving_game")
        val SOUND_ENABLED_KEY = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED_KEY = booleanPreferencesKey("vibration_enabled")
        val ANIMATION_LEVEL_KEY = stringPreferencesKey("animation_level")
        val PLAYER_COUNT_KEY = intPreferencesKey("player_count")
        val TURN_TIMER_KEY = stringPreferencesKey("turn_timer")
        val TURN_TIMER_LENGTH_KEY = stringPreferencesKey("turn_timer_length")
        val ROLLS_PER_TURN_KEY = intPreferencesKey("rolls_per_turn_modifier")
        val ROLLS_PER_TURN_LENGTH_KEY = intPreferencesKey("rolls_per_turn_modifier_length")
        val STORED_ROLLS_KEY = booleanPreferencesKey("stored_rolls_modifier")
        val STORED_ROLLS_MAX_KEY = intPreferencesKey("stored_rolls_modifier_max")
        val EXTENDED_SCORES_KEY = booleanPreferencesKey("extended_scores_modifier")
        val UNLUCKY_DICE_KEY = booleanPreferencesKey("unlucky_dice_modifier")
        val UNLUCKY_ODDS_KEY = intPreferencesKey("unlucky_dice_odds")
        val UNLUCKY_MAX_DICE_KEY = intPreferencesKey("unlucky_dice_max")
        val GAME_MODE_KEY = stringPreferencesKey("game_mode")
        val DICE_STYLE_ID_KEY = stringPreferencesKey("dice_style_id")
        val DICE_CUP_STYLE_ID_KEY = stringPreferencesKey("dice_cup_style_id")
        val TABLE_BACKGROUND_ID_KEY = stringPreferencesKey("table_background_id")
        val DICE_MAT_ID_KEY = stringPreferencesKey("dice_mat_id")
        val SCORE_FRAME_ID_KEY = stringPreferencesKey("score_frame_id")
        val STYLES_PAGE_FIT_KEY = stringPreferencesKey("styles_page_fit")
        fun playerNameKey(slot: Int) = stringPreferencesKey("player_name_$slot")
        fun playerTypeKey(slot: Int) = stringPreferencesKey("player_type_$slot")
        fun playerColourKey(slot: Int) = stringPreferencesKey("player_colour_$slot")
        fun playerDifficultyKey(slot: Int) = stringPreferencesKey("player_difficulty_$slot")
    }
}
