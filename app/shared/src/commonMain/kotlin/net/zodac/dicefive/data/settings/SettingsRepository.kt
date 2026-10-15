package net.zodac.dicefive.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.data.newerOf
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.storedIdOf
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.model.TurnTimer

/**
 * DataStore-backed settings: the last-used name/type/difficulty (User/CPU) for each player slot
 * (1-4, slot 1 always Human) plus the last-used player count, so returning to setup pre-fills it,
 * whether leaving an in-progress game needs a confirmation, and whether sound effects/vibration
 * are enabled.
 *
 * Every setting but the Animations level and the Styles page fit goes in the cloud save ([SYNCED_KEYS]): those two are
 * about this device - what it can draw, and its screen. Each
 * synced key is stamped with when its value last changed ([stampKey]), and the cloud save keeps whichever device set
 * it last. A Styles pick Reset achievements unstamps ([unstampStylePicks]) is stamped 0: it loses to any later change,
 * but beats a device that never set one.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val now: () -> Long = ::nowEpochMillis,
) {

    /** Every write: [block], then a stamp on each synced key whose value it changed. */
    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { prefs ->
            val before = SYNCED_KEYS.map { prefs.asMap()[it] }
            block(prefs)
            val at = now()
            SYNCED_KEYS.forEachIndexed { index, key -> if (prefs.asMap()[key] != before[index]) prefs[stampKey(key)] = at }
        }
    }

    /** Emits each time a synced setting changes here, or arrives from the cloud save - not for the first value. */
    val syncedChanges: Flow<Unit> =
        dataStore.data.map { prefs -> SYNCED_KEYS.map { prefs[stampKey(it)] } }.distinctUntilChanged().drop(1).map { }

    /** The synced settings, by key name, each with its stamp - every one that is set, or was set and since removed. */
    suspend fun cloudSettings(): Map<String, Stamped<SettingValue?>> {
        val prefs = dataStore.data.first()
        return SYNCED_KEYS.mapNotNull { key ->
            val value = settingValue(prefs.asMap()[key])
            val at = prefs[stampKey(key)]
            if (value == null && at == null) null else key.name to Stamped(value, at ?: 0)
        }.toMap()
    }

    /**
     * Takes each of the cloud save's [remote] settings that was set later than this device's, keeping its stamp. A key
     * this build doesn't sync, or a value of the wrong type, is skipped.
     */
    suspend fun mergeCloud(remote: Map<String, Stamped<SettingValue?>>) {
        dataStore.edit { prefs ->
            for ((name, theirs) in remote) {
                val key = SYNCED_KEYS_BY_NAME[name] ?: continue
                val ours = Stamped(settingValue(prefs.asMap()[key]), prefs[stampKey(key)] ?: 0)
                if (ours == theirs || newerOf(ours, theirs) { it.toString() } !== theirs) continue
                if (write(prefs, key, theirs.value)) prefs[stampKey(key)] = theirs.atEpochMillis
            }
        }
    }

    /** Forgets every synced setting and its stamp, as on a device that never set one - for another account signing in. */
    suspend fun wipeSynced() {
        dataStore.edit { prefs ->
            for (key in SYNCED_KEYS) {
                prefs.remove(key)
                prefs.remove(stampKey(key))
            }
        }
    }

    /**
     * Unstamps the Styles picks, so the cloud save's own copy wins over them at the next sync: Reset achievements puts
     * locked picks back to the defaults on this device only, as the reset itself is.
     */
    suspend fun unstampStylePicks() {
        dataStore.edit { prefs -> STYLE_KEYS.forEach { prefs.remove(stampKey(it)) } }
    }

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
            prefs[playerTypeKey(slot)]?.let { storedIdOf<PlayerType>(it) }

        fun playerDifficultyFor(slot: Int): Difficulty? =
            prefs[playerDifficultyKey(slot)]?.let { storedIdOf<Difficulty>(it) }

        fun playerColourFor(slot: Int): PlayerColour? =
            prefs[playerColourKey(slot)]?.let { storedIdOf<PlayerColour>(it) }

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
        edit { it[playerNameKey(slot)] = name }
    }

    fun playerTypeFor(slot: Int): Flow<PlayerType?> = readSnapshot { it.playerTypeFor(slot) }

    suspend fun setPlayerType(slot: Int, type: PlayerType) {
        edit { it[playerTypeKey(slot)] = type.id }
    }

    fun playerDifficultyFor(slot: Int): Flow<Difficulty?> = readSnapshot { it.playerDifficultyFor(slot) }

    suspend fun setPlayerDifficulty(slot: Int, difficulty: Difficulty) {
        edit { it[playerDifficultyKey(slot)] = difficulty.id }
    }

    fun playerColourFor(slot: Int): Flow<PlayerColour?> = readSnapshot { it.playerColourFor(slot) }

    suspend fun setPlayerColour(slot: Int, colour: PlayerColour) {
        edit { it[playerColourKey(slot)] = colour.id }
    }

    val playerCount: Flow<Int?> = readSnapshot { it.playerCount }

    suspend fun setPlayerCount(count: Int) {
        edit { it[PLAYER_COUNT_KEY] = count }
    }

    val turnTimer: Flow<TurnTimer> = readSnapshot { it.turnTimer }

    suspend fun setTurnTimer(turnTimer: TurnTimer) {
        edit { it[TURN_TIMER_KEY] = turnTimer.name }
    }

    /**
     * The turn timer length last chosen, kept while the timer is switched off ([turnTimer] is then
     * [TurnTimer.NONE]) so switching it back on restores it. 60 seconds until one is chosen.
     */
    val turnTimerLength: Flow<TurnTimer> = readSnapshot { it.turnTimerLength }

    suspend fun setTurnTimerLength(length: TurnTimer) {
        edit { it[TURN_TIMER_LENGTH_KEY] = length.name }
    }

    /**
     * The roll modifiers the setup form last started a game with. The Number of Rolls length is read
     * separately ([rollsPerTurnLength]) because it's kept while that modifier is off.
     */
    val rollModifiers: Flow<RollModifiers> = readSnapshot { it.rollModifiers }

    /** The Number of Rolls value last chosen, kept while the modifier is off so switching it back on restores it. */
    val rollsPerTurnLength: Flow<Int> = readSnapshot { it.rollsPerTurnLength }

    suspend fun setRollModifiers(modifiers: RollModifiers, rollsPerTurnLength: Int) {
        edit { prefs ->
            modifiers.rollsPerTurn?.let { prefs[ROLLS_PER_TURN_KEY] = it } ?: prefs.remove(ROLLS_PER_TURN_KEY)
            prefs[ROLLS_PER_TURN_LENGTH_KEY] = rollsPerTurnLength
            prefs[STORED_ROLLS_KEY] = modifiers.storedRolls
            modifiers.storedRollsMax?.let { prefs[STORED_ROLLS_MAX_KEY] = it } ?: prefs.remove(STORED_ROLLS_MAX_KEY)
        }
    }

    /** Whether the Extended Scores modifier was on the last time the setup form started a game. */
    val extendedScores: Flow<Boolean> = readSnapshot { it.extendedScores }

    suspend fun setExtendedScores(enabled: Boolean) {
        edit { it[EXTENDED_SCORES_KEY] = enabled }
    }

    /** Whether the Unlucky Dice modifier was on the last time the setup form started a game. */
    val unluckyDiceEnabled: Flow<Boolean> = readSnapshot { it.unluckyDiceEnabled }

    /**
     * The Unlucky Dice odds and cap the setup form last had, kept while the modifier is off so switching it back
     * on restores them. A stored value outside the allowed range - or one that isn't a step - falls back to the default.
     */
    val unluckyDice: Flow<UnluckyDice> = readSnapshot { it.unluckyDice }

    suspend fun setUnluckyDice(enabled: Boolean, settings: UnluckyDice) {
        edit { prefs ->
            prefs[UNLUCKY_DICE_KEY] = enabled
            prefs[UNLUCKY_ODDS_KEY] = settings.oddsPercent
            prefs[UNLUCKY_MAX_DICE_KEY] = settings.maxDice
        }
    }

    /** The mode the setup form last started a game in - read back by id, falling back to the default
     * for one nothing recognises (see [GameMode.id]). */
    val gameMode: Flow<GameMode> = readSnapshot { it.gameMode }

    suspend fun setGameMode(gameMode: GameMode) {
        edit { it[GAME_MODE_KEY] = gameMode.id }
    }

    val confirmBeforeLeavingGame: Flow<Boolean> =
        read { prefs -> prefs[CONFIRM_BEFORE_LEAVING_GAME_KEY] ?: true }

    suspend fun setConfirmBeforeLeavingGame(confirm: Boolean) {
        edit { it[CONFIRM_BEFORE_LEAVING_GAME_KEY] = confirm }
    }

    val soundEnabled: Flow<Boolean> = read { prefs -> prefs[SOUND_ENABLED_KEY] ?: true }

    suspend fun setSoundEnabled(enabled: Boolean) {
        edit { it[SOUND_ENABLED_KEY] = enabled }
    }

    val vibrationEnabled: Flow<Boolean> = read { prefs -> prefs[VIBRATION_ENABLED_KEY] ?: true }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        edit { it[VIBRATION_ENABLED_KEY] = enabled }
    }

    /** The player's "Animations" level - see [AnimationLevel]. */
    val animationLevel: Flow<AnimationLevel> = read { prefs ->
        prefs[ANIMATION_LEVEL_KEY]?.let { storedIdOf<AnimationLevel>(it) } ?: AnimationLevel.default
    }

    suspend fun setAnimationLevel(level: AnimationLevel) {
        edit { it[ANIMATION_LEVEL_KEY] = level.id }
    }

    // Style ids, not the ui.game.style types themselves - this is the data layer, and resolving an
    // id to a concrete DiceStyle/DiceCupStyle/TableBackground is the Styles screen's job (via its
    // catalog). The defaults are DefaultStyleIds, which the default styles themselves are given.
    val diceStyleId: Flow<String> = read { prefs -> prefs[DICE_STYLE_ID_KEY] ?: DefaultStyleIds.DICE }

    suspend fun setDiceStyleId(id: String) {
        edit { it[DICE_STYLE_ID_KEY] = id }
    }

    val diceCupStyleId: Flow<String> = read { prefs -> prefs[DICE_CUP_STYLE_ID_KEY] ?: DefaultStyleIds.DICE_CUP }

    suspend fun setDiceCupStyleId(id: String) {
        edit { it[DICE_CUP_STYLE_ID_KEY] = id }
    }

    val tableBackgroundId: Flow<String> = read { prefs -> prefs[TABLE_BACKGROUND_ID_KEY] ?: DefaultStyleIds.TABLE_BACKGROUND }

    suspend fun setTableBackgroundId(id: String) {
        edit { it[TABLE_BACKGROUND_ID_KEY] = id }
    }

    val diceMatId: Flow<String> = read { prefs -> prefs[DICE_MAT_ID_KEY] ?: DefaultStyleIds.DICE_MAT }

    suspend fun setDiceMatId(id: String) {
        edit { it[DICE_MAT_ID_KEY] = id }
    }

    // The frame round the player whose turn it is.
    val scoreFrameId: Flow<String> = read { prefs -> prefs[SCORE_FRAME_ID_KEY] ?: DefaultStyleIds.SCORE_FRAME }

    suspend fun setScoreFrameId(id: String) {
        edit { it[SCORE_FRAME_ID_KEY] = id }
    }

    // The Styles page's fitted size for the screen it was measured on, as the Styles screen writes it -
    // opaque here. Null until it's first measured.
    val stylesPageFit: Flow<String?> = read { prefs -> prefs[STYLES_PAGE_FIT_KEY] }

    suspend fun setStylesPageFit(fit: String) {
        edit { it[STYLES_PAGE_FIT_KEY] = fit }
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

        /** How many player slots the setup form remembers - `GameSetupState.MAX_PLAYERS`. */
        private const val PLAYER_SLOTS = 4

        val STYLE_KEYS = listOf(DICE_STYLE_ID_KEY, DICE_CUP_STYLE_ID_KEY, TABLE_BACKGROUND_ID_KEY, DICE_MAT_ID_KEY, SCORE_FRAME_ID_KEY)

        private val SYNCED_STRING_KEYS: List<Preferences.Key<String>> = STYLE_KEYS +
            listOf(TURN_TIMER_KEY, TURN_TIMER_LENGTH_KEY, GAME_MODE_KEY) +
            (1..PLAYER_SLOTS).flatMap { listOf(playerNameKey(it), playerTypeKey(it), playerColourKey(it), playerDifficultyKey(it)) }
        private val SYNCED_INT_KEYS: List<Preferences.Key<Int>> = listOf(
            PLAYER_COUNT_KEY, ROLLS_PER_TURN_KEY, ROLLS_PER_TURN_LENGTH_KEY, STORED_ROLLS_MAX_KEY, UNLUCKY_ODDS_KEY, UNLUCKY_MAX_DICE_KEY,
        )
        private val SYNCED_BOOLEAN_KEYS: List<Preferences.Key<Boolean>> = listOf(
            STORED_ROLLS_KEY, EXTENDED_SCORES_KEY, UNLUCKY_DICE_KEY, SOUND_ENABLED_KEY, VIBRATION_ENABLED_KEY, CONFIRM_BEFORE_LEAVING_GAME_KEY,
        )

        /**
         * What the cloud save carries: everything but the Animations level ([ANIMATION_LEVEL_KEY]), chosen for what this
         * device can draw, and the Styles page fit ([STYLES_PAGE_FIT_KEY]), a measurement of its screen.
         */
        val SYNCED_KEYS: List<Preferences.Key<*>> = SYNCED_STRING_KEYS + SYNCED_INT_KEYS + SYNCED_BOOLEAN_KEYS
        private val SYNCED_KEYS_BY_NAME = SYNCED_KEYS.associateBy { it.name }

        /** When [key] last changed: `synced_at:<key>`. */
        fun stampKey(key: Preferences.Key<*>) = longPreferencesKey("synced_at:${key.name}")

        fun settingValue(stored: Any?): SettingValue? = when (stored) {
            is String -> SettingValue.Text(stored)
            is Int -> SettingValue.Number(stored)
            is Boolean -> SettingValue.Flag(stored)
            else -> null
        }

        /** Sets [key] to [value] (null: removes it). False if [value] isn't [key]'s type, and nothing is written. */
        fun write(prefs: MutablePreferences, key: Preferences.Key<*>, value: SettingValue?): Boolean {
            if (value == null) {
                prefs.remove(key)
                return true
            }
            when {
                value is SettingValue.Text && key in SYNCED_STRING_KEYS -> prefs[SYNCED_STRING_KEYS.first { it == key }] = value.value
                value is SettingValue.Number && key in SYNCED_INT_KEYS -> prefs[SYNCED_INT_KEYS.first { it == key }] = value.value
                value is SettingValue.Flag && key in SYNCED_BOOLEAN_KEYS -> prefs[SYNCED_BOOLEAN_KEYS.first { it == key }] = value.value
                else -> return false
            }
            return true
        }
    }
}
