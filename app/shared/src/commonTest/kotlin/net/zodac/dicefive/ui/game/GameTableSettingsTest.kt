package net.zodac.dicefive.ui.game

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.random.Random
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.ChickenDiceCupStyle

/** In-memory preferences, so a real [SettingsRepository] can back the table's settings. */
private class TablePreferencesStore : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(_data.value).also { _data.value = it }
}

/** Just enough of an [AchievementStore] to set which achievements are unlocked. */
private class TableAchievementStore : AchievementStore {

    private val _state = MutableStateFlow(AchievementsState())
    override val state: Flow<AchievementsState> = _state

    override suspend fun current(): AchievementsState = _state.value

    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) {
        _state.value = _state.value.copy(unlockedAt = _state.value.unlockedAt + unlockedAt)
    }

    override suspend fun resetAll() {
        _state.value = AchievementsState()
    }

    override suspend fun forceLock(achievement: Achievement) {
        _state.value = _state.value.copy(unlockedAt = _state.value.unlockedAt - achievement)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameTableSettingsTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the table has no settings until they load - never a flash of the defaults - and draws a locked pick as the default until it unlocks`() = runTest {
        // With no repository the table is the defaults straight away.
        assertEquals(TableSettings(), GameViewModel().tableSettings.value)

        val settings = SettingsRepository(TablePreferencesStore())
        settings.setDiceStyleId("barrel")
        settings.setSoundEnabled(false)
        val viewModel = GameViewModel(settingsRepository = settings, achievementsRepository = TableAchievementStore())
        assertNull(viewModel.tableSettings.value)
        advanceUntilIdle()
        assertEquals("barrel", viewModel.tableSettings.value?.visualTheme?.diceStyle?.id)
        assertEquals(false, viewModel.tableSettings.value?.soundEnabled)

        val lockedSettings = SettingsRepository(TablePreferencesStore())
        val achievements = TableAchievementStore()
        lockedSettings.setDiceStyleId("googly_ivory")
        val locked = GameViewModel(settingsRepository = lockedSettings, achievementsRepository = achievements)
        advanceUntilIdle()
        assertEquals(DiceStyles.default, locked.tableSettings.value?.visualTheme?.diceStyle)
        achievements.record(Achievement.entries.associateWith { 0L }, emptyMap())
        advanceUntilIdle()
        assertEquals("googly_ivory", locked.tableSettings.value?.visualTheme?.diceStyle?.id)
    }

    /** Every one-in-a-thousand draw comes up, and every other draw is the lowest it can be. */
    private object GoldenEveryTime : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextInt(until: Int): Int = if (until == 1000) 0 else 1
    }

    private suspend fun kotlinx.coroutines.test.TestScope.firstRoll(diceId: String, cupId: String): Pair<GameViewModel, TableAchievementStore> {
        val settings = SettingsRepository(TablePreferencesStore())
        settings.setDiceStyleId(diceId)
        settings.setDiceCupStyleId(cupId)
        val achievements = TableAchievementStore()
        achievements.record(Achievement.entries.filter { it != Achievement.EGGCELLENT_DISCOVERY }.associateWith { 0L }, emptyMap())
        val viewModel = GameViewModel(settingsRepository = settings, achievementsRepository = achievements, random = GoldenEveryTime)
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        advanceUntilIdle()
        viewModel.rollDice()
        advanceUntilIdle()
        return viewModel to achievements
    }

    @Test
    fun `a golden egg rolls only with the Egg dice and the Chicken cup both picked - and earns Eggcellent Discovery`() = runTest {
        val (both, achievements) = firstRoll("egg_white", "chicken_brown")
        assertTrue(both.tableSettings.value?.visualTheme?.diceCupStyle is ChickenDiceCupStyle)
        assertTrue(both.game.value!!.dice.all { it.isGolden })
        assertTrue(achievements.current().isUnlocked(Achievement.EGGCELLENT_DISCOVERY))

        for ((dice, cup) in listOf("egg_white" to DiceCupStyles.default.id, DiceStyles.default.id to "chicken_white")) {
            val (viewModel, store) = firstRoll(dice, cup)
            assertTrue(viewModel.game.value!!.dice.none { it.isGolden }, "$dice with $cup rolled a golden die")
            assertFalse(store.current().isUnlocked(Achievement.EGGCELLENT_DISCOVERY))
        }
    }
}
