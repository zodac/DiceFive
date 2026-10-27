package net.zodac.dicefive.ui.game

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.random.Random
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.style.ChickenDiceCupStyle
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.PIP_FACES

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
        val settings = SettingsRepository(InMemoryPreferences())
        settings.setDiceStyleId("classic_oak")
        settings.setSoundEnabled(false)
        val viewModel = gameViewModel(settingsRepository = settings, achievementsRepository = InMemoryAchievementStore(), aiDispatcher = testDispatcher)
        assertNull(viewModel.tableSettings.value)
        advanceUntilIdle()
        assertEquals("classic_oak", viewModel.tableSettings.value?.visualTheme?.diceStyle?.id)
        assertEquals(false, viewModel.tableSettings.value?.soundEnabled)

        val lockedSettings = SettingsRepository(InMemoryPreferences())
        val achievements = InMemoryAchievementStore()
        lockedSettings.setDiceStyleId("googly_ivory")
        val locked = gameViewModel(settingsRepository = lockedSettings, achievementsRepository = achievements, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        assertEquals(DiceStyles.default, locked.tableSettings.value?.visualTheme?.diceStyle)
        achievements.record(Achievement.entries.associateWith { 0L }, emptyMap())
        advanceUntilIdle()
        assertEquals("googly_ivory", locked.tableSettings.value?.visualTheme?.diceStyle?.id)
    }

    @Test
    fun `an owner of every style has a locked pick drawn - and earns Fresh Coat Of Paint with it`() = runTest {
        val settings = SettingsRepository(InMemoryPreferences())
        settings.setDiceStyleId("googly_ivory")
        val achievements = InMemoryAchievementStore()
        val viewModel = gameViewModel(settingsRepository = settings, achievementsRepository = achievements, aiDispatcher = testDispatcher, ownsAllStyles = true)
        advanceUntilIdle()
        assertEquals("googly_ivory", viewModel.tableSettings.value?.visualTheme?.diceStyle?.id)
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        advanceUntilIdle()
        assertTrue(achievements.current().isUnlocked(Achievement.FRESH_COAT_OF_PAINT))
    }

    /** Every golden egg draw comes up, and every other draw is the lowest it can be. */
    private object GoldenEveryTime : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextInt(until: Int): Int = if (until == GameEngine.GOLDEN_EGG_ONE_IN) 0 else 1
    }

    /** The first roll of a solo game with [diceId] and [cupId] picked; every other achievement is earned, and Eggcellent
     * Discovery too when [eggcellentEarned] (at time 0, so a fresh unlock would show as a later time). */
    private suspend fun kotlinx.coroutines.test.TestScope.firstRoll(
        diceId: String,
        cupId: String,
        eggcellentEarned: Boolean = false,
        gameMode: GameMode = GameMode.STANDARD,
    ): Pair<GameViewModel, InMemoryAchievementStore> {
        val settings = SettingsRepository(InMemoryPreferences())
        settings.setDiceStyleId(diceId)
        settings.setDiceCupStyleId(cupId)
        val achievements = InMemoryAchievementStore()
        achievements.record(Achievement.entries.filter { eggcellentEarned || it != Achievement.EGGCELLENT_DISCOVERY }.associateWith { 0L }, emptyMap())
        val viewModel = gameViewModel(settingsRepository = settings, achievementsRepository = achievements, random = GoldenEveryTime, aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(1)
        viewModel.setGameMode(gameMode)
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

    @Test
    fun `a golden egg keeps rolling once Eggcellent Discovery is earned - and never earns it again`() = runTest {
        for (dice in listOf("egg_white", "egg_brown")) {
            val (viewModel, achievements) = firstRoll(dice, "chicken_brown", eggcellentEarned = true)
            assertTrue(viewModel.game.value!!.dice.all { it.isGolden }, "$dice rolled no golden egg")
            assertEquals(0L, achievements.current().unlockedAt[Achievement.EGGCELLENT_DISCOVERY], "$dice unlocked it again")
        }
    }

    @Test
    fun `the Golden Egg dice it unlocks never roll a golden egg`() = runTest {
        val (viewModel, _) = firstRoll("egg_gold", "chicken_brown", eggcellentEarned = true)
        assertEquals("egg_gold", viewModel.tableSettings.value?.visualTheme?.diceStyle?.id)
        assertTrue(viewModel.game.value!!.dice.none { it.isGolden })
    }

    @Test
    fun `a golden egg rolls in every mode whose dice the Egg art can draw - never in one drawn as D20s`() = runTest {
        for (mode in GameMode.entries) {
            val (viewModel, _) = firstRoll("egg_white", "chicken_brown", gameMode = mode)
            assertEquals(mode, viewModel.game.value!!.gameMode)
            assertEquals(mode.dieValues.last <= PIP_FACES, viewModel.game.value!!.dice.all { it.isGolden }, "${mode.id} golden eggs")
        }
        assertTrue(GameMode.entries.any { it.dieValues.last > PIP_FACES })
    }

    @Test
    fun `a golden egg is one in 500 per die`() {
        assertEquals(500, GameEngine.GOLDEN_EGG_ONE_IN)
    }
}
