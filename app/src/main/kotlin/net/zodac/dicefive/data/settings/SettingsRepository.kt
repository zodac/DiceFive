package net.zodac.dicefive.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.TurnTimer

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * DataStore-backed settings: the last-used name/type/difficulty (User/CPU) for each player slot
 * (1-4, slot 1 always Human) plus the last-used player count, so returning to setup pre-fills it,
 * whether leaving an in-progress game needs a confirmation, and whether sound effects/vibration
 * are enabled.
 */
class SettingsRepository(private val context: Context) {

    fun playerNameFor(slot: Int): Flow<String?> =
        context.settingsDataStore.data.map { prefs -> prefs[playerNameKey(slot)] }

    suspend fun setPlayerName(slot: Int, name: String) {
        context.settingsDataStore.edit { it[playerNameKey(slot)] = name }
    }

    fun playerTypeFor(slot: Int): Flow<PlayerType?> = context.settingsDataStore.data.map { prefs ->
        prefs[playerTypeKey(slot)]?.let { raw -> runCatching { PlayerType.valueOf(raw) }.getOrNull() }
    }

    suspend fun setPlayerType(slot: Int, type: PlayerType) {
        context.settingsDataStore.edit { it[playerTypeKey(slot)] = type.name }
    }

    fun playerDifficultyFor(slot: Int): Flow<Difficulty?> = context.settingsDataStore.data.map { prefs ->
        prefs[playerDifficultyKey(slot)]?.let { raw -> runCatching { Difficulty.valueOf(raw) }.getOrNull() }
    }

    suspend fun setPlayerDifficulty(slot: Int, difficulty: Difficulty) {
        context.settingsDataStore.edit { it[playerDifficultyKey(slot)] = difficulty.name }
    }

    val playerCount: Flow<Int?> = context.settingsDataStore.data.map { prefs -> prefs[PLAYER_COUNT_KEY] }

    suspend fun setPlayerCount(count: Int) {
        context.settingsDataStore.edit { it[PLAYER_COUNT_KEY] = count }
    }

    val turnTimer: Flow<TurnTimer> = context.settingsDataStore.data.map { prefs ->
        prefs[TURN_TIMER_KEY]?.let { raw -> runCatching { TurnTimer.valueOf(raw) }.getOrNull() } ?: TurnTimer.NONE
    }

    suspend fun setTurnTimer(turnTimer: TurnTimer) {
        context.settingsDataStore.edit { it[TURN_TIMER_KEY] = turnTimer.name }
    }

    /** The mode the setup form last started a game in - read back by id, falling back to the default
     * for one nothing recognises (see [GameMode.id]). */
    val gameMode: Flow<GameMode> = context.settingsDataStore.data.map { prefs ->
        prefs[GAME_MODE_KEY]?.let { GameMode.fromId(it) } ?: GameMode.default
    }

    suspend fun setGameMode(gameMode: GameMode) {
        context.settingsDataStore.edit { it[GAME_MODE_KEY] = gameMode.id }
    }

    val confirmBeforeLeavingGame: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[CONFIRM_BEFORE_LEAVING_GAME_KEY] ?: true }

    suspend fun setConfirmBeforeLeavingGame(confirm: Boolean) {
        context.settingsDataStore.edit { it[CONFIRM_BEFORE_LEAVING_GAME_KEY] = confirm }
    }

    val soundEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs -> prefs[SOUND_ENABLED_KEY] ?: true }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[SOUND_ENABLED_KEY] = enabled }
    }

    val vibrationEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs -> prefs[VIBRATION_ENABLED_KEY] ?: true }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[VIBRATION_ENABLED_KEY] = enabled }
    }

    // Style ids, not the ui.game.style types themselves - this is the data layer, and resolving an
    // id to a concrete DiceStyle/DiceCupStyle/TableBackground is the Styles screen's job (via its
    // catalog). The literal defaults below must match ui.game.style's DiceStyles/DiceCupStyles/
    // TableBackgrounds.default ids.
    val diceStyleId: Flow<String> = context.settingsDataStore.data.map { prefs -> prefs[DICE_STYLE_ID_KEY] ?: "ivory" }

    suspend fun setDiceStyleId(id: String) {
        context.settingsDataStore.edit { it[DICE_STYLE_ID_KEY] = id }
    }

    val diceCupStyleId: Flow<String> = context.settingsDataStore.data.map { prefs -> prefs[DICE_CUP_STYLE_ID_KEY] ?: "leather" }

    suspend fun setDiceCupStyleId(id: String) {
        context.settingsDataStore.edit { it[DICE_CUP_STYLE_ID_KEY] = id }
    }

    val tableBackgroundId: Flow<String> = context.settingsDataStore.data.map { prefs -> prefs[TABLE_BACKGROUND_ID_KEY] ?: "midnight_felt" }

    suspend fun setTableBackgroundId(id: String) {
        context.settingsDataStore.edit { it[TABLE_BACKGROUND_ID_KEY] = id }
    }

    val diceMatId: Flow<String> = context.settingsDataStore.data.map { prefs -> prefs[DICE_MAT_ID_KEY] ?: "tray_blue" }

    suspend fun setDiceMatId(id: String) {
        context.settingsDataStore.edit { it[DICE_MAT_ID_KEY] = id }
    }

    private companion object {
        val CONFIRM_BEFORE_LEAVING_GAME_KEY = booleanPreferencesKey("confirm_before_leaving_game")
        val SOUND_ENABLED_KEY = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED_KEY = booleanPreferencesKey("vibration_enabled")
        val PLAYER_COUNT_KEY = intPreferencesKey("player_count")
        val TURN_TIMER_KEY = stringPreferencesKey("turn_timer")
        val GAME_MODE_KEY = stringPreferencesKey("game_mode")
        val DICE_STYLE_ID_KEY = stringPreferencesKey("dice_style_id")
        val DICE_CUP_STYLE_ID_KEY = stringPreferencesKey("dice_cup_style_id")
        val TABLE_BACKGROUND_ID_KEY = stringPreferencesKey("table_background_id")
        val DICE_MAT_ID_KEY = stringPreferencesKey("dice_mat_id")
        fun playerNameKey(slot: Int) = stringPreferencesKey("player_name_$slot")
        fun playerTypeKey(slot: Int) = stringPreferencesKey("player_type_$slot")
        fun playerDifficultyKey(slot: Int) = stringPreferencesKey("player_difficulty_$slot")
    }
}
