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
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.TurnTimer

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * DataStore-backed settings: the display theme, the user's own name (slot 1, set from the
 * Settings screen), the last-used name/type/difficulty (User/CPU) for each player slot (1-4)
 * plus the last-used player count, so returning to setup pre-fills it, and whether leaving an
 * in-progress game needs a confirmation.
 */
class SettingsRepository(private val context: Context) {

    val theme: Flow<Theme> = context.settingsDataStore.data.map { prefs ->
        prefs[THEME_KEY]?.let { raw -> runCatching { Theme.valueOf(raw) }.getOrNull() } ?: Theme.SYSTEM
    }

    suspend fun setTheme(theme: Theme) {
        context.settingsDataStore.edit { it[THEME_KEY] = theme.name }
    }

    /** The primary user's own name, edited from the Settings screen - slot 1 is always them. */
    val userName: Flow<String?> = playerNameFor(1)

    suspend fun setUserName(name: String) = setPlayerName(1, name)

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

    val confirmBeforeLeavingGame: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[CONFIRM_BEFORE_LEAVING_GAME_KEY] ?: true }

    suspend fun setConfirmBeforeLeavingGame(confirm: Boolean) {
        context.settingsDataStore.edit { it[CONFIRM_BEFORE_LEAVING_GAME_KEY] = confirm }
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
        val THEME_KEY = stringPreferencesKey("theme")
        val CONFIRM_BEFORE_LEAVING_GAME_KEY = booleanPreferencesKey("confirm_before_leaving_game")
        val PLAYER_COUNT_KEY = intPreferencesKey("player_count")
        val TURN_TIMER_KEY = stringPreferencesKey("turn_timer")
        val DICE_STYLE_ID_KEY = stringPreferencesKey("dice_style_id")
        val DICE_CUP_STYLE_ID_KEY = stringPreferencesKey("dice_cup_style_id")
        val TABLE_BACKGROUND_ID_KEY = stringPreferencesKey("table_background_id")
        val DICE_MAT_ID_KEY = stringPreferencesKey("dice_mat_id")
        fun playerNameKey(slot: Int) = stringPreferencesKey("player_name_$slot")
        fun playerTypeKey(slot: Int) = stringPreferencesKey("player_type_$slot")
        fun playerDifficultyKey(slot: Int) = stringPreferencesKey("player_difficulty_$slot")
    }
}
