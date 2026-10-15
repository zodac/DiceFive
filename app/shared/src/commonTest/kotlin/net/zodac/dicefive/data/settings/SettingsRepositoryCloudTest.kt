package net.zodac.dicefive.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.ui.game.GameSetupState

private class CloudSettingsPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

/** The settings' side of the cloud save: the New Game setup and Styles picks, each stamped when it changes. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryCloudTest {

    private var clock = 100L
    private val repository = SettingsRepository(CloudSettingsPreferences(), now = { clock })

    @Test
    fun `settings are stamped when they change - the Animations level and page fit aren't in the save`() = runTest {
        repository.setDiceStyleId("ocean")
        repository.setPlayerName(slot = 2, name = "Bob")
        clock = 200L
        // Writing the same value again isn't a change.
        repository.setDiceStyleId("ocean")
        repository.setGameMode(GameMode.QUICKFIRE)
        repository.setSoundEnabled(false)
        repository.setAnimationLevel(AnimationLevel.OFF)
        repository.setConfirmBeforeLeavingGame(false)
        repository.setStylesPageFit("fit")
        assertEquals(
            mapOf(
                "dice_style_id" to Stamped(SettingValue.Text("ocean"), 100L),
                "player_name_2" to Stamped(SettingValue.Text("Bob"), 100L),
                "game_mode" to Stamped(SettingValue.Text(GameMode.QUICKFIRE.id), 200L),
                "sound_enabled" to Stamped(SettingValue.Flag(false), 200L),
                "confirm_before_leaving_game" to Stamped(SettingValue.Flag(false), 200L),
            ),
            repository.cloudSettings(),
        )
    }

    @Test
    fun `a removed setting stays in the save as unset - so the removal travels`() = runTest {
        repository.setRollModifiers(RollModifiers(rollsPerTurn = 4), rollsPerTurnLength = 4)
        clock = 200L
        repository.setRollModifiers(RollModifiers(), rollsPerTurnLength = 4)
        assertEquals(Stamped<SettingValue?>(null, 200L), repository.cloudSettings()["rolls_per_turn_modifier"])
    }

    @Test
    fun `the cloud's setting is taken only if it was set later - with its own stamp - and a wrong type or unknown key is skipped`() = runTest {
        repository.setDiceStyleId("ocean")
        repository.setPlayerCount(3)
        repository.mergeCloud(
            mapOf(
                "dice_style_id" to Stamped(SettingValue.Text("gold"), 150L),
                "player_count" to Stamped(SettingValue.Number(2), 50L),
                "extended_scores_modifier" to Stamped(SettingValue.Number(1), 150L),
                "vibration_enabled" to Stamped(SettingValue.Flag(false), 150L),
                "styles_page_fit" to Stamped(SettingValue.Text("another screen's"), 150L),
                "animation_level" to Stamped(SettingValue.Text(AnimationLevel.OFF.id), 150L),
                "future_setting" to Stamped(SettingValue.Flag(true), 150L),
            ),
        )
        assertEquals("gold", repository.diceStyleId.first())
        assertEquals(3, repository.playerCount.first())
        assertEquals(false, repository.extendedScores.first())
        assertEquals(false, repository.vibrationEnabled.first())
        assertEquals(null, repository.stylesPageFit.first())
        assertEquals(AnimationLevel.default, repository.animationLevel.first())
        assertEquals(
            mapOf(
                "dice_style_id" to Stamped(SettingValue.Text("gold"), 150L),
                "player_count" to Stamped(SettingValue.Number(3), 100L),
                "vibration_enabled" to Stamped(SettingValue.Flag(false), 150L),
            ),
            repository.cloudSettings(),
        )
    }

    @Test
    fun `wiping forgets every synced setting - and keeps the Animations level and page fit`() = runTest {
        repository.setDiceStyleId("ocean")
        repository.setSoundEnabled(false)
        repository.setAnimationLevel(AnimationLevel.OFF)
        repository.setStylesPageFit("fit")
        repository.wipeSynced()
        assertEquals(emptyMap(), repository.cloudSettings())
        assertEquals(DefaultStyleIds.DICE, repository.diceStyleId.first())
        assertEquals(true, repository.soundEnabled.first())
        assertEquals("fit", repository.stylesPageFit.first())
        assertEquals(AnimationLevel.OFF, repository.animationLevel.first())
    }

    @Test
    fun `unstamped style picks lose to the cloud's copy`() = runTest {
        repository.setDiceStyleId("ocean")
        repository.setDiceStyleId(DefaultStyleIds.DICE)
        repository.unstampStylePicks()
        repository.mergeCloud(mapOf("dice_style_id" to Stamped(SettingValue.Text("ocean"), 1L)))
        assertEquals("ocean", repository.diceStyleId.first())
    }

    @Test
    fun `a change to a synced setting is announced - the Animations level's and page fit's aren't`() = runTest {
        var changes = 0
        backgroundScope.launch { repository.syncedChanges.collect { changes++ } }
        runCurrent()
        repository.setStylesPageFit("fit")
        repository.setAnimationLevel(AnimationLevel.OFF)
        runCurrent()
        assertEquals(0, changes)
        repository.setDiceMatId("felt")
        runCurrent()
        assertEquals(1, changes)
    }

    @Test
    fun `every player slot the setup form has is synced`() = runTest {
        repository.setPlayerName(GameSetupState.MAX_PLAYERS, "Dee")
        assertEquals(Stamped(SettingValue.Text("Dee"), 100L), repository.cloudSettings()["player_name_${GameSetupState.MAX_PLAYERS}"])
    }

    @Test
    fun `a setting chosen before stamps existed travels at the first sync - and still loses to a later change`() = runTest {
        // Sound turned off on another device before this update: in the save, unstamped.
        repository.mergeCloud(mapOf("sound_enabled" to Stamped(SettingValue.Flag(false), 0L)))
        assertEquals(false, repository.soundEnabled.first())
        // Turned back on here since: this change wins.
        repository.setSoundEnabled(true)
        repository.mergeCloud(mapOf("sound_enabled" to Stamped(SettingValue.Flag(false), 0L)))
        assertEquals(true, repository.soundEnabled.first())
    }
}
