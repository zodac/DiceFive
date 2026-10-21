package net.zodac.dicefive.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.ui.game.GameSetupState

/** The settings' side of the cloud save: the New Game setup and Styles picks, each stamped when it changes. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryCloudTest {

    private var clock = 100L
    private val repository = SettingsRepository(InMemoryPreferences(), now = { clock })

    @Test
    fun `settings are stamped when they change - the Animations level - Orientation - page fit and list filters aren't in the save`() = runTest {
        repository.setDiceStyleId("ocean")
        repository.setPlayerName(slot = 2, name = "Bob")
        clock = 200L
        // Writing the same value again isn't a change.
        repository.setDiceStyleId("ocean")
        repository.setGameMode(GameMode.QUICKFIRE)
        repository.setSoundEnabled(false)
        repository.setAnimationLevel(AnimationLevel.OFF)
        repository.setScreenOrientation(ScreenOrientation.LANDSCAPE)
        repository.setConfirmBeforeLeavingGame(false)
        repository.setStylesPageFit("fit")
        repository.setLockedOnlyAchievements(true)
        repository.setUnlockedOnlyStyles(true)
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
    fun `the Statistics sort travels in the save - so a restored or imported one keeps its order`() = runTest {
        repository.setStatisticsSort("highest_score:desc")
        val save = repository.cloudSettings()
        assertEquals(Stamped<SettingValue?>(SettingValue.Text("highest_score:desc"), 100L), save["statistics_sort"])

        val other = SettingsRepository(InMemoryPreferences(), now = { clock })
        assertEquals(null, other.statisticsSort.first())
        other.mergeCloud(save)
        assertEquals("highest_score:desc", other.statisticsSort.first())
    }

    @Test
    fun `a first finished game travels in the save - so a reinstall signed in to the same account stays unlocked`() = runTest {
        repository.setFirstGameCompleted()
        val save = repository.cloudSettings()
        assertEquals(Stamped<SettingValue?>(SettingValue.Flag(true), 100L), save["first_game_completed"])

        val reinstalled = SettingsRepository(InMemoryPreferences(), now = { clock })
        assertEquals(false, reinstalled.firstGameCompleted.first())
        reinstalled.mergeCloud(save)
        assertEquals(true, reinstalled.firstGameCompleted.first())
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
    fun `wiping forgets every synced setting - and keeps the Animations level - Orientation and page fit`() = runTest {
        repository.setDiceStyleId("ocean")
        repository.setSoundEnabled(false)
        repository.setAnimationLevel(AnimationLevel.OFF)
        repository.setScreenOrientation(ScreenOrientation.PORTRAIT)
        repository.setStylesPageFit("fit")
        repository.setLockedOnlyAchievements(true)
        repository.setUnlockedOnlyStyles(true)
        repository.wipeSynced()
        assertEquals(emptyMap(), repository.cloudSettings())
        assertEquals(DefaultStyleIds.DICE, repository.diceStyleId.first())
        assertEquals(true, repository.soundEnabled.first())
        assertEquals("fit", repository.stylesPageFit.first())
        assertEquals(AnimationLevel.OFF, repository.animationLevel.first())
        assertEquals(ScreenOrientation.PORTRAIT, repository.screenOrientation.first())
        assertEquals(true, repository.lockedOnlyAchievements.first())
        assertEquals(true, repository.unlockedOnlyStyles.first())
    }

    @Test
    fun `the Locked only and Unlocked only switches start off - are kept - and are set apart from each other`() = runTest {
        assertEquals(false, repository.lockedOnlyAchievements.first())
        assertEquals(false, repository.unlockedOnlyStyles.first())
        repository.setLockedOnlyAchievements(true)
        assertEquals(true, repository.lockedOnlyAchievements.first())
        assertEquals(false, repository.unlockedOnlyStyles.first())
        repository.setUnlockedOnlyStyles(true)
        repository.setLockedOnlyAchievements(false)
        assertEquals(false, repository.lockedOnlyAchievements.first())
        assertEquals(true, repository.unlockedOnlyStyles.first())
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
    fun `a change to a synced setting is announced - the Animations level's - Orientation's - page fit's and list filters' aren't`() = runTest {
        var changes = 0
        backgroundScope.launch { repository.syncedChanges.collect { changes++ } }
        runCurrent()
        repository.setStylesPageFit("fit")
        repository.setAnimationLevel(AnimationLevel.OFF)
        repository.setScreenOrientation(ScreenOrientation.LANDSCAPE)
        repository.setLockedOnlyAchievements(true)
        repository.setUnlockedOnlyStyles(true)
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
    fun `an unstamped pick travels to a device that never set one - and still loses to a later change`() = runTest {
        // A pick another device's Reset achievements unstamped: in the save, stamped 0.
        repository.mergeCloud(mapOf("dice_style_id" to Stamped(SettingValue.Text("ocean"), 0L)))
        assertEquals("ocean", repository.diceStyleId.first())
        // Picked again here since: this change wins.
        repository.setDiceStyleId("gold")
        repository.mergeCloud(mapOf("dice_style_id" to Stamped(SettingValue.Text("ocean"), 0L)))
        assertEquals("gold", repository.diceStyleId.first())
    }
}
