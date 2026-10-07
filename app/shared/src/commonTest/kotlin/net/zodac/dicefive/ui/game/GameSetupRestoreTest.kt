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

    @Test
    fun `saved setup is published in one update and only then flagged restored`() = runTest(testDispatcher) {
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
    fun `each seat starts in its own colour`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        assertEquals(
            listOf(PlayerColour.CYAN, PlayerColour.GREEN, PlayerColour.PURPLE, PlayerColour.AMBER),
            viewModel.setup.value.playerSlots.map { it.colour },
        )
    }

    @Test
    fun `picking a colour another player has swaps the two and a free one just replaces`() {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)

        viewModel.setPlayerColour(1, PlayerColour.GREEN)
        assertEquals(
            listOf(PlayerColour.GREEN, PlayerColour.CYAN, PlayerColour.PURPLE, PlayerColour.AMBER),
            viewModel.setup.value.playerSlots.map { it.colour },
        )

        viewModel.setPlayerColour(3, PlayerColour.PINK)
        val colours = viewModel.setup.value.playerSlots.map { it.colour }
        assertEquals(PlayerColour.PINK, colours[2])
        assertEquals(4, colours.toSet().size)
    }

    @Test
    fun `colours are remembered and carried into the game and its players`() = runTest(testDispatcher) {
        val repository = SettingsRepository(FakePreferencesStore())
        val first = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        first.setPlayerColour(2, PlayerColour.LIME)
        first.startGame()
        advanceUntilIdle()
        assertEquals(PlayerColour.LIME, checkNotNull(first.game.value).players[1].colour)

        val second = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        assertEquals(PlayerColour.LIME, second.setup.value.playerSlots[1].colour)
        assertEquals(4, second.setup.value.playerSlots.map { it.colour }.toSet().size)
    }

    @Test
    fun `saved colours that clash are dropped for the defaults`() = runTest(testDispatcher) {
        val repository = SettingsRepository(FakePreferencesStore())
        for (slot in 1..4) repository.setPlayerColour(slot, PlayerColour.RED)
        val viewModel = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        assertEquals(
            listOf(PlayerColour.CYAN, PlayerColour.GREEN, PlayerColour.PURPLE, PlayerColour.AMBER),
            viewModel.setup.value.playerSlots.map { it.colour },
        )
    }

    @Test
    fun `with no saved settings to restore the form is shown straight away`() {
        assertTrue(GameViewModel(aiDispatcher = testDispatcher).setupRestored.value)
    }

    @Test
    fun `turn timer length is remembered while the timer is off`() = runTest(testDispatcher) {
        val repository = SettingsRepository(FakePreferencesStore())
        val first = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        first.setTurnTimer(TurnTimer.SECONDS_120)
        first.setTurnTimer(TurnTimer.NONE)
        assertEquals(TurnTimer.SECONDS_120, first.setup.value.turnTimerLength)
        first.startGame()
        advanceUntilIdle()

        val second = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        assertEquals(TurnTimer.NONE, second.setup.value.turnTimer)
        assertEquals(TurnTimer.SECONDS_120, second.setup.value.turnTimerLength)
    }

    @Test
    fun `roll modifiers are remembered and the rolls value is kept while off`() = runTest(testDispatcher) {
        val repository = SettingsRepository(FakePreferencesStore())
        val first = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        first.setRollsPerTurn(7)
        first.setRollsPerTurn(null)
        first.setStoredRolls(true)
        first.setStoredRollsMax(12)
        first.startGame()
        advanceUntilIdle()

        val second = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        assertEquals(RollModifiers(rollsPerTurn = null, storedRolls = true, storedRollsMax = 12), second.setup.value.rollModifiers)
        assertEquals(7, second.setup.value.rollsPerTurnLength)
    }

    @Test
    fun `a game starts with the chosen roll modifiers`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setRollsPerTurn(5)
        viewModel.setStoredRolls(true)
        viewModel.startGame()

        val game = checkNotNull(viewModel.game.value)
        assertEquals(RollModifiers(rollsPerTurn = 5, storedRolls = true), game.rollModifiers)
        assertEquals(5, game.rollsRemaining)
    }

    @Test
    fun `Extended Scores is remembered between games`() = runTest(testDispatcher) {
        val repository = SettingsRepository(FakePreferencesStore())
        val first = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        assertFalse(first.setup.value.extendedScores)
        first.setExtendedScores(true)
        first.startGame()
        advanceUntilIdle()

        val second = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        assertTrue(second.setup.value.extendedScores)
    }

    @Test
    fun `a game starts with Extended Scores in any mode that allows it`() = runTest(testDispatcher) {
        for (mode in GameMode.entries.filter { it.allowsExtendedScores }) {
            val viewModel = GameViewModel(aiDispatcher = testDispatcher)
            viewModel.setGameMode(mode)
            viewModel.setExtendedScores(true)
            viewModel.startGame()

            val game = checkNotNull(viewModel.game.value)
            assertTrue(game.extendedScores, mode.id)
            assertEquals(mode.categories + ScoreCategory.EXTENDED, game.players.first().categories, mode.id)
        }
    }

    @Test
    fun `Hit List starts without Extended Scores - and keeps the switch on for the next mode`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setExtendedScores(true)
        viewModel.setGameMode(GameMode.HIT_LIST)
        viewModel.startGame()

        val game = checkNotNull(viewModel.game.value)
        assertFalse(game.extendedScores)
        assertEquals(GameMode.HIT_LIST.categories, game.players.first().categories)
        assertTrue(viewModel.setup.value.extendedScores)

        viewModel.setGameMode(GameMode.STANDARD)
        viewModel.startGame()
        assertTrue(checkNotNull(viewModel.game.value).extendedScores)
    }

    @Test
    fun `Unlucky Dice and its settings are remembered between games`() = runTest(testDispatcher) {
        val repository = SettingsRepository(FakePreferencesStore())
        val first = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        assertFalse(first.setup.value.unluckyDiceEnabled)
        assertEquals(UnluckyDice(10, 1), first.setup.value.unluckyDice)
        first.setUnluckyDiceEnabled(true)
        first.setUnluckyOdds(40)
        first.setUnluckyMaxDice(3)
        first.startGame()
        advanceUntilIdle()

        val second = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        assertTrue(second.setup.value.unluckyDiceEnabled)
        assertEquals(UnluckyDice(40, 3), second.setup.value.unluckyDice)
    }

    @Test
    fun `the Unlucky Dice settings are kept while it is switched off and a game starts without it`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setUnluckyOdds(50)
        viewModel.setUnluckyDiceEnabled(true)
        viewModel.setUnluckyDiceEnabled(false)
        viewModel.startGame()

        assertEquals(null, checkNotNull(viewModel.game.value).unluckyDice)
        assertEquals(50, viewModel.setup.value.unluckyDice.oddsPercent)
    }

    @Test
    fun `a game starts with Unlucky Dice in any mode`() = runTest(testDispatcher) {
        for (mode in GameMode.entries) {
            val viewModel = GameViewModel(aiDispatcher = testDispatcher)
            viewModel.setGameMode(mode)
            viewModel.setUnluckyDiceEnabled(true)
            viewModel.setUnluckyOdds(20)
            viewModel.setUnluckyMaxDice(2)
            viewModel.startGame()

            assertEquals(UnluckyDice(20, 2), checkNotNull(viewModel.game.value).unluckyDice, mode.id)
        }
    }

    @Test
    fun `a name left blank is the default for its seat in the player's language - a typed one is kept`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher)
        viewModel.setPlayerCount(3)
        viewModel.setPlayerName(2, "Wolfgang")

        assertEquals(listOf("", "Wolfgang", ""), viewModel.setup.value.playerSlots.take(3).map { it.name })

        viewModel.startGame { slot -> "Joueur $slot" }

        assertEquals(listOf("Joueur 1", "Wolfgang"), checkNotNull(viewModel.game.value).players.take(2).map { it.name })
    }

    @Test
    fun `names are saved as typed - blank stays blank - and the English default older versions saved reads as blank`() = runTest(testDispatcher) {
        val repository = SettingsRepository(FakePreferencesStore())
        repository.setPlayerName(1, "Player 1")
        repository.setPlayerName(2, "Wolfgang")
        repository.setPlayerName(3, "Player 4")
        val first = GameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()

        // Slot 1's old default is not something the player typed; slot 3's "Player 4" isn't its own default, so it stays.
        assertEquals(listOf("", "Wolfgang", "Player 4"), first.setup.value.playerSlots.take(3).map { it.name })

        first.setPlayerCount(2)
        first.startGame()
        advanceUntilIdle()

        assertEquals("", repository.playerNameFor(1).first())
        assertEquals("Wolfgang", repository.playerNameFor(2).first())
    }
}
