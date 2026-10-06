package net.zodac.dicefive.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
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

    fun playerNameFor(slot: Int): Flow<String?> =
        dataStore.data.map { prefs -> prefs[playerNameKey(slot)] }

    suspend fun setPlayerName(slot: Int, name: String) {
        dataStore.edit { it[playerNameKey(slot)] = name }
    }

    fun playerTypeFor(slot: Int): Flow<PlayerType?> = dataStore.data.map { prefs ->
        prefs[playerTypeKey(slot)]?.let { raw -> runCatching { PlayerType.valueOf(raw) }.getOrNull() }
    }

    suspend fun setPlayerType(slot: Int, type: PlayerType) {
        dataStore.edit { it[playerTypeKey(slot)] = type.name }
    }

    fun playerDifficultyFor(slot: Int): Flow<Difficulty?> = dataStore.data.map { prefs ->
        prefs[playerDifficultyKey(slot)]?.let { raw -> runCatching { Difficulty.valueOf(raw) }.getOrNull() }
    }

    suspend fun setPlayerDifficulty(slot: Int, difficulty: Difficulty) {
        dataStore.edit { it[playerDifficultyKey(slot)] = difficulty.name }
    }

    fun playerColourFor(slot: Int): Flow<PlayerColour?> = dataStore.data.map { prefs ->
        prefs[playerColourKey(slot)]?.let { raw -> runCatching { PlayerColour.valueOf(raw) }.getOrNull() }
    }

    suspend fun setPlayerColour(slot: Int, colour: PlayerColour) {
        dataStore.edit { it[playerColourKey(slot)] = colour.name }
    }

    val playerCount: Flow<Int?> = dataStore.data.map { prefs -> prefs[PLAYER_COUNT_KEY] }

    suspend fun setPlayerCount(count: Int) {
        dataStore.edit { it[PLAYER_COUNT_KEY] = count }
    }

    val turnTimer: Flow<TurnTimer> = dataStore.data.map { prefs ->
        prefs[TURN_TIMER_KEY]?.let { raw -> runCatching { TurnTimer.valueOf(raw) }.getOrNull() } ?: TurnTimer.NONE
    }

    suspend fun setTurnTimer(turnTimer: TurnTimer) {
        dataStore.edit { it[TURN_TIMER_KEY] = turnTimer.name }
    }

    /**
     * The turn timer length last chosen, kept while the timer is switched off ([turnTimer] is then
     * [TurnTimer.NONE]) so switching it back on restores it. Falls back to the timer's own stored
     * value from before this was kept separately, then to 60 seconds.
     */
    val turnTimerLength: Flow<TurnTimer> = dataStore.data.map { prefs ->
        val stored = prefs[TURN_TIMER_LENGTH_KEY] ?: prefs[TURN_TIMER_KEY]
        stored?.let { raw -> runCatching { TurnTimer.valueOf(raw) }.getOrNull() }?.takeIf { it != TurnTimer.NONE } ?: TurnTimer.SECONDS_60
    }

    suspend fun setTurnTimerLength(length: TurnTimer) {
        dataStore.edit { it[TURN_TIMER_LENGTH_KEY] = length.name }
    }

    /**
     * The roll modifiers the setup form last started a game with. The Number of Rolls length is read
     * separately ([rollsPerTurnLength]) because it's kept while that modifier is off.
     */
    val rollModifiers: Flow<RollModifiers> = dataStore.data.map { prefs ->
        RollModifiers(
            rollsPerTurn = prefs[ROLLS_PER_TURN_KEY]?.takeIf { it in RollModifiers.MIN_ROLLS..RollModifiers.MAX_ROLLS },
            storedRolls = prefs[STORED_ROLLS_KEY] ?: false,
            storedRollsMax = prefs[STORED_ROLLS_MAX_KEY]?.takeIf { it >= 0 },
        )
    }

    /** The Number of Rolls value last chosen, kept while the modifier is off so switching it back on restores it. */
    val rollsPerTurnLength: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[ROLLS_PER_TURN_LENGTH_KEY] ?: prefs[ROLLS_PER_TURN_KEY])
            ?.takeIf { it in RollModifiers.MIN_ROLLS..RollModifiers.MAX_ROLLS } ?: RollModifiers.DEFAULT_ROLLS
    }

    suspend fun setRollModifiers(modifiers: RollModifiers, rollsPerTurnLength: Int) {
        dataStore.edit { prefs ->
            modifiers.rollsPerTurn?.let { prefs[ROLLS_PER_TURN_KEY] = it } ?: prefs.remove(ROLLS_PER_TURN_KEY)
            prefs[ROLLS_PER_TURN_LENGTH_KEY] = rollsPerTurnLength
            prefs[STORED_ROLLS_KEY] = modifiers.storedRolls
            modifiers.storedRollsMax?.let { prefs[STORED_ROLLS_MAX_KEY] = it } ?: prefs.remove(STORED_ROLLS_MAX_KEY)
        }
    }

    /** Whether the Extended Scores modifier was on the last time the setup form started a game. */
    val extendedScores: Flow<Boolean> = dataStore.data.map { prefs -> prefs[EXTENDED_SCORES_KEY] ?: false }

    suspend fun setExtendedScores(enabled: Boolean) {
        dataStore.edit { it[EXTENDED_SCORES_KEY] = enabled }
    }

    /** Whether the Unlucky Dice modifier was on the last time the setup form started a game. */
    val unluckyDiceEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[UNLUCKY_DICE_KEY] ?: false }

    /**
     * The Unlucky Dice odds and cap the setup form last had, kept while the modifier is off so switching it back
     * on restores them. A stored value outside the allowed range - or one that isn't a step - falls back to the default.
     */
    val unluckyDice: Flow<UnluckyDice> = dataStore.data.map { prefs ->
        UnluckyDice(
            oddsPercent = prefs[UNLUCKY_ODDS_KEY]
                ?.takeIf { it in UnluckyDice.MIN_ODDS_PERCENT..UnluckyDice.MAX_ODDS_PERCENT && it % UnluckyDice.ODDS_STEP_PERCENT == 0 }
                ?: UnluckyDice.DEFAULT_ODDS_PERCENT,
            maxDice = prefs[UNLUCKY_MAX_DICE_KEY]?.takeIf { it in UnluckyDice.MIN_MAX_DICE..UnluckyDice.MAX_MAX_DICE }
                ?: UnluckyDice.DEFAULT_MAX_DICE,
        )
    }

    suspend fun setUnluckyDice(enabled: Boolean, settings: UnluckyDice) {
        dataStore.edit { prefs ->
            prefs[UNLUCKY_DICE_KEY] = enabled
            prefs[UNLUCKY_ODDS_KEY] = settings.oddsPercent
            prefs[UNLUCKY_MAX_DICE_KEY] = settings.maxDice
        }
    }

    /** The mode the setup form last started a game in - read back by id, falling back to the default
     * for one nothing recognises (see [GameMode.id]). */
    val gameMode: Flow<GameMode> = dataStore.data.map { prefs ->
        prefs[GAME_MODE_KEY]?.let { GameMode.fromId(it) } ?: GameMode.default
    }

    suspend fun setGameMode(gameMode: GameMode) {
        dataStore.edit { it[GAME_MODE_KEY] = gameMode.id }
    }

    val confirmBeforeLeavingGame: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[CONFIRM_BEFORE_LEAVING_GAME_KEY] ?: true }

    suspend fun setConfirmBeforeLeavingGame(confirm: Boolean) {
        dataStore.edit { it[CONFIRM_BEFORE_LEAVING_GAME_KEY] = confirm }
    }

    val soundEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SOUND_ENABLED_KEY] ?: true }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[SOUND_ENABLED_KEY] = enabled }
    }

    val vibrationEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[VIBRATION_ENABLED_KEY] ?: true }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[VIBRATION_ENABLED_KEY] = enabled }
    }

    /**
     * The player's own "Remove animations": the app behaves as it does when the system asks for reduced
     * motion (see `LocalReduceMotion`), whatever the system says, and what still moves is capped at 30fps.
     * Replaced the narrower "Simple dice roll" switch, whose saved value isn't carried over.
     */
    val removeAnimations: Flow<Boolean> = dataStore.data.map { prefs -> prefs[REMOVE_ANIMATIONS_KEY] ?: false }

    suspend fun setRemoveAnimations(enabled: Boolean) {
        dataStore.edit { it[REMOVE_ANIMATIONS_KEY] = enabled }
    }

    // Style ids, not the ui.game.style types themselves - this is the data layer, and resolving an
    // id to a concrete DiceStyle/DiceCupStyle/TableBackground is the Styles screen's job (via its
    // catalog). The literal defaults below must match ui.game.style's DiceStyles/DiceCupStyles/
    // TableBackgrounds.default ids.
    val diceStyleId: Flow<String> = dataStore.data.map { prefs -> prefs[DICE_STYLE_ID_KEY] ?: "ivory" }

    suspend fun setDiceStyleId(id: String) {
        dataStore.edit { it[DICE_STYLE_ID_KEY] = id }
    }

    // Dropped cups, read back as today's default: "leather" was the default before it went, and
    // "casino_burgundy" made way for the Gold Classic cup. Anyone who picked one explicitly still has
    // it saved, and it's drawn as the default - so it must not count as a non-default pick either,
    // or it would unlock the cup style achievement for a cup they aren't playing with.
    val diceCupStyleId: Flow<String> = dataStore.data.map { prefs ->
        prefs[DICE_CUP_STYLE_ID_KEY]?.takeUnless { it in DROPPED_DICE_CUP_STYLE_IDS } ?: "casino_gold"
    }

    suspend fun setDiceCupStyleId(id: String) {
        dataStore.edit { it[DICE_CUP_STYLE_ID_KEY] = id }
    }

    val tableBackgroundId: Flow<String> = dataStore.data.map { prefs -> prefs[TABLE_BACKGROUND_ID_KEY] ?: "midnight_felt" }

    suspend fun setTableBackgroundId(id: String) {
        dataStore.edit { it[TABLE_BACKGROUND_ID_KEY] = id }
    }

    val diceMatId: Flow<String> = dataStore.data.map { prefs -> prefs[DICE_MAT_ID_KEY] ?: "tray_blue" }

    suspend fun setDiceMatId(id: String) {
        dataStore.edit { it[DICE_MAT_ID_KEY] = id }
    }

    // The frame round the player whose turn it is - "classic" is ScoreFrames.default's id.
    val scoreFrameId: Flow<String> = dataStore.data.map { prefs -> prefs[SCORE_FRAME_ID_KEY] ?: "classic" }

    suspend fun setScoreFrameId(id: String) {
        dataStore.edit { it[SCORE_FRAME_ID_KEY] = id }
    }

    // The Styles page's fitted size for the screen it was measured on, as the Styles screen writes it -
    // opaque here. Null until it's first measured.
    val stylesPageFit: Flow<String?> = dataStore.data.map { prefs -> prefs[STYLES_PAGE_FIT_KEY] }

    suspend fun setStylesPageFit(fit: String) {
        dataStore.edit { it[STYLES_PAGE_FIT_KEY] = fit }
    }

    private companion object {
        val CONFIRM_BEFORE_LEAVING_GAME_KEY = booleanPreferencesKey("confirm_before_leaving_game")
        val SOUND_ENABLED_KEY = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED_KEY = booleanPreferencesKey("vibration_enabled")
        val REMOVE_ANIMATIONS_KEY = booleanPreferencesKey("remove_animations")
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
        val DROPPED_DICE_CUP_STYLE_IDS = setOf("leather", "casino_burgundy")
        fun playerNameKey(slot: Int) = stringPreferencesKey("player_name_$slot")
        fun playerTypeKey(slot: Int) = stringPreferencesKey("player_type_$slot")
        fun playerColourKey(slot: Int) = stringPreferencesKey("player_colour_$slot")
        fun playerDifficultyKey(slot: Int) = stringPreferencesKey("player_difficulty_$slot")
    }
}
