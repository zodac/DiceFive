package net.zodac.dicefive.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.zodac.dicefive.model.PlayerType

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * DataStore-backed settings: the display theme, the last-used name and
 * player type (Human/AI) for each player slot (1-4) plus the last-used
 * player count, so returning to setup pre-fills it, and whether leaving an
 * in-progress game needs a confirmation.
 */
class SettingsRepository(private val context: Context) {

    val theme: Flow<Theme> = context.settingsDataStore.data.map { prefs ->
        prefs[THEME_KEY]?.let { raw -> runCatching { Theme.valueOf(raw) }.getOrNull() } ?: Theme.SYSTEM
    }

    suspend fun setTheme(theme: Theme) {
        context.settingsDataStore.edit { it[THEME_KEY] = theme.name }
    }

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

    val playerCount: Flow<Int?> = context.settingsDataStore.data.map { prefs -> prefs[PLAYER_COUNT_KEY] }

    suspend fun setPlayerCount(count: Int) {
        context.settingsDataStore.edit { it[PLAYER_COUNT_KEY] = count }
    }

    val confirmBeforeLeavingGame: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[CONFIRM_BEFORE_LEAVING_GAME_KEY] ?: true }

    suspend fun setConfirmBeforeLeavingGame(confirm: Boolean) {
        context.settingsDataStore.edit { it[CONFIRM_BEFORE_LEAVING_GAME_KEY] = confirm }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme")
        val CONFIRM_BEFORE_LEAVING_GAME_KEY = booleanPreferencesKey("confirm_before_leaving_game")
        val PLAYER_COUNT_KEY = intPreferencesKey("player_count")
        fun playerNameKey(slot: Int) = stringPreferencesKey("player_name_$slot")
        fun playerTypeKey(slot: Int) = stringPreferencesKey("player_type_$slot")
    }
}
