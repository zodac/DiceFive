package net.zodac.dicefive.ui.game

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.UnluckyDice

/** In-memory preferences, so a real [SettingsRepository] can hold the saved setup. */
private class FakePreferencesStore : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(_data.value).also { _data.value = it }
}

/** The setup form comes back with the last game's choices, all at once - never the defaults first. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameSetupRestoreTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A form over [repository] that has finished restoring what it saved. */
    private fun TestScope.restored(repository: SettingsRepository): GameViewModel {
        val viewModel = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `saved setup is published in one update and only then flagged restored - with nothing saved the form is shown at once`() = runTest(testDispatcher) {
        assertTrue(GameViewModel(aiDispatcher = testDispatcher).setupRestored.value)

        val repository = SettingsRepository(FakePreferencesStore())
        repository.setPlayerCount(3)
        repository.setPlayerName(1, "Alexandra the Great")
        repository.setPlayerType(2, PlayerType.AI)
        repository.setPlayerDifficulty(2, Difficulty.HARD)
        repository.setTurnTimer(TurnTimer.SECONDS_60)
        repository.setGameMode(GameMode.TRICOLOUR)

        val viewModel = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        assertFalse(viewModel.setupRestored.value)

        // Every state the form could be drawn with once it's shown - there must be exactly one.
        val shownStates = mutableListOf<GameSetupState>()
        backgroundScope.launch(testDispatcher) {
            viewModel.setupRestored.collect { restored -> if (restored) shownStates += viewModel.setup.value }
        }
        advanceUntilIdle()
        // advanceUntilIdle stops once only background work is left - the collector above included.
        runCurrent()

        assertTrue(viewModel.setupRestored.value)
        val restored = shownStates.single()
        assertEquals(GameMode.TRICOLOUR, restored.gameMode)
        assertEquals(TurnTimer.SECONDS_60, restored.turnTimer)
        assertEquals(3, restored.playerCount)
        assertEquals("Alexandra the Great".take(GameSetupState.maxPlayerNameLength(3)), restored.playerSlots[0].name)
        assertEquals(PlayerType.AI, restored.playerSlots[1].type)
        assertEquals(Difficulty.HARD, restored.playerSlots[1].difficulty)
        assertEquals(restored, viewModel.setup.value)
    }

    @Test
    fun `each seat has its own colour - a taken one swaps - and they're remembered and carried into the game unless they clash`() = runTest(testDispatcher) {
        val defaults = listOf(PlayerColour.CYAN, PlayerColour.GREEN, PlayerColour.PURPLE, PlayerColour.AMBER)
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        assertEquals(defaults, viewModel.setup.value.playerSlots.map { it.colour })

        // Picking a colour another player has swaps the two; a free one just replaces.
        viewModel.setPlayerColour(1, PlayerColour.GREEN)
        assertEquals(listOf(PlayerColour.GREEN, PlayerColour.CYAN, PlayerColour.PURPLE, PlayerColour.AMBER), viewModel.setup.value.playerSlots.map { it.colour })
        viewModel.setPlayerColour(3, PlayerColour.PINK)
        val colours = viewModel.setup.value.playerSlots.map { it.colour }
        assertEquals(PlayerColour.PINK, colours[2])
        assertEquals(4, colours.toSet().size)

        val repository = SettingsRepository(FakePreferencesStore())
        val first = restored(repository)
        first.setPlayerColour(2, PlayerColour.LIME)
        first.startGame()
        advanceUntilIdle()
        assertEquals(PlayerColour.LIME, checkNotNull(first.game.value).players[1].colour)
        val second = restored(repository)
        assertEquals(PlayerColour.LIME, second.setup.value.playerSlots[1].colour)
        assertEquals(4, second.setup.value.playerSlots.map { it.colour }.toSet().size)

        // Saved colours that clash are dropped for the defaults.
        val clashing = SettingsRepository(FakePreferencesStore())
        for (slot in 1..4) clashing.setPlayerColour(slot, PlayerColour.RED)
        assertEquals(defaults, restored(clashing).setup.value.playerSlots.map { it.colour })
    }

    @Test
    fun `every modifier is remembered between games - its value kept while it's off`() = runTest(testDispatcher) {
        val repository = SettingsRepository(FakePreferencesStore())
        val first = restored(repository)
        assertFalse(first.setup.value.extendedScores)
        assertFalse(first.setup.value.unluckyDiceEnabled)
        assertEquals(UnluckyDice(10, 1), first.setup.value.unluckyDice)
        first.setTurnTimer(TurnTimer.SECONDS_120)
        first.setTurnTimer(TurnTimer.NONE)
        assertEquals(TurnTimer.SECONDS_120, first.setup.value.turnTimerLength)
        first.setRollsPerTurn(7)
        first.setRollsPerTurn(null)
        first.setStoredRolls(true)
        first.setStoredRollsMax(12)
        first.setExtendedScores(true)
        first.setUnluckyDiceEnabled(true)
        first.setUnluckyOdds(40)
        first.setUnluckyMaxDice(3)
        first.startGame()
        advanceUntilIdle()

        val second = restored(repository).setup.value
        assertEquals(TurnTimer.NONE, second.turnTimer)
        assertEquals(TurnTimer.SECONDS_120, second.turnTimerLength)
        assertEquals(RollModifiers(rollsPerTurn = null, storedRolls = true, storedRollsMax = 12), second.rollModifiers)
        assertEquals(7, second.rollsPerTurnLength)
        assertTrue(second.extendedScores)
        assertTrue(second.unluckyDiceEnabled)
        assertEquals(UnluckyDice(40, 3), second.unluckyDice)
    }

    @Test
    fun `a game starts with the modifiers chosen - in every mode that allows them`() = runTest(testDispatcher) {
        val rolls = GameViewModel(aiDispatcher = testDispatcher)
        rolls.setRollsPerTurn(5)
        rolls.setStoredRolls(true)
        rolls.startGame()
        val game = checkNotNull(rolls.game.value)
        assertEquals(RollModifiers(rollsPerTurn = 5, storedRolls = true), game.rollModifiers)
        assertEquals(5, game.rollsRemaining)

        for (mode in GameMode.entries) {
            val viewModel = GameViewModel(aiDispatcher = testDispatcher)
            viewModel.setGameMode(mode)
            viewModel.setExtendedScores(true)
            viewModel.setUnluckyDiceEnabled(true)
            viewModel.setUnluckyOdds(20)
            viewModel.setUnluckyMaxDice(2)
            viewModel.startGame()
            val started = checkNotNull(viewModel.game.value)
            assertEquals(UnluckyDice(20, 2), started.unluckyDice, mode.id)
            assertEquals(mode.allowsExtendedScores, started.extendedScores, mode.id)
            assertEquals(if (mode.allowsExtendedScores) mode.categories + ScoreCategory.EXTENDED else mode.categories, started.players.first().categories, mode.id)
        }

        // Hit List starts without Extended Scores - and keeps the switch on for the next mode.
        val hitList = GameViewModel(aiDispatcher = testDispatcher)
        hitList.setExtendedScores(true)
        hitList.setGameMode(GameMode.HIT_LIST)
        hitList.startGame()
        assertFalse(checkNotNull(hitList.game.value).extendedScores)
        assertTrue(hitList.setup.value.extendedScores)
        hitList.setGameMode(GameMode.STANDARD)
        hitList.startGame()
        assertTrue(checkNotNull(hitList.game.value).extendedScores)

        // Unlucky Dice's settings are kept while it is switched off, and a game starts without it.
        val unlucky = GameViewModel(aiDispatcher = testDispatcher)
        unlucky.setUnluckyOdds(50)
        unlucky.setUnluckyDiceEnabled(true)
        unlucky.setUnluckyDiceEnabled(false)
        unlucky.startGame()
        assertEquals(null, checkNotNull(unlucky.game.value).unluckyDice)
        assertEquals(50, unlucky.setup.value.unluckyDice.oddsPercent)
    }

    @Test
    fun `a name left blank is its seat's default in the player's language - saved blank - and an old English default reads as blank`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(3)
        viewModel.setPlayerName(2, "Wolfgang")
        assertEquals(listOf("", "Wolfgang", ""), viewModel.setup.value.playerSlots.take(3).map { it.name })
        viewModel.startGame { slot -> "Joueur $slot" }
        assertEquals(listOf("Joueur 1", "Wolfgang"), checkNotNull(viewModel.game.value).players.take(2).map { it.name })

        val repository = SettingsRepository(FakePreferencesStore())
        repository.setPlayerName(1, "Player 1")
        repository.setPlayerName(2, "Wolfgang")
        repository.setPlayerName(3, "Player 4")
        val first = restored(repository)
        // Slot 1's old default is not something the player typed; slot 3's "Player 4" isn't its own default, so it stays.
        assertEquals(listOf("", "Wolfgang", "Player 4"), first.setup.value.playerSlots.take(3).map { it.name })
        first.setPlayerCount(2)
        first.startGame()
        advanceUntilIdle()
        assertEquals("", repository.playerNameFor(1).first())
        assertEquals("Wolfgang", repository.playerNameFor(2).first())
    }
}
