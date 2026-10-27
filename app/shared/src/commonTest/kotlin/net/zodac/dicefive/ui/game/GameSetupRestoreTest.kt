package net.zodac.dicefive.ui.game

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.model.maxPlayerNameLength

/** [InMemoryPreferences], counting the reads of it that finish: a `first()`, not a flow held open. */
private class CountingReadsStore(private val store: InMemoryPreferences = InMemoryPreferences()) : DataStore<Preferences> by store {
    var finishedReads = 0
    override val data: Flow<Preferences> = store.data.onCompletion { finishedReads++ }
}

/** [InMemoryPreferences], counting its writes. */
private class CountingWritesStore(private val store: InMemoryPreferences = InMemoryPreferences()) : DataStore<Preferences> by store {
    var writes = 0
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = store.updateData(transform).also { writes++ }
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
        val viewModel = gameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `saved setup is published in one update and only then flagged restored`() = runTest(testDispatcher) {
        val repository = SettingsRepository(InMemoryPreferences())
        repository.setFirstGameCompleted()
        repository.setPlayerCount(3)
        repository.setPlayerName(1, "Alexandra the Great")
        repository.setPlayerType(2, PlayerType.AI)
        repository.setPlayerDifficulty(2, Difficulty.HARD)
        repository.setTurnTimer(TurnTimer.SECONDS_60)
        repository.setGameMode(GameMode.TRICOLOUR)

        val viewModel = gameViewModel(settingsRepository = repository, aiDispatcher = testDispatcher)
        assertFalse(viewModel.setupForm.setupRestored.value)

        // Every state the form could be drawn with once it's shown - there must be exactly one.
        val shownStates = mutableListOf<GameSetupState>()
        backgroundScope.launch(testDispatcher) {
            viewModel.setupForm.setupRestored.collect { restored -> if (restored) shownStates += viewModel.setupForm.setup.value }
        }
        advanceUntilIdle()
        // advanceUntilIdle stops once only background work is left - the collector above included.
        runCurrent()

        assertTrue(viewModel.setupForm.setupRestored.value)
        val restored = shownStates.single()
        assertEquals(GameMode.TRICOLOUR, restored.gameMode)
        assertEquals(TurnTimer.SECONDS_60, restored.turnTimer)
        assertEquals(3, restored.playerCount)
        assertEquals("Alexandra the Great".take(maxPlayerNameLength(3)), restored.playerSlots[0].name)
        assertEquals(PlayerType.AI, restored.playerSlots[1].type)
        assertEquals(Difficulty.HARD, restored.playerSlots[1].difficulty)
        assertEquals(restored, viewModel.setupForm.setup.value)
    }

    @Test
    fun `the saved setup is restored from one read of the settings`() = runTest(testDispatcher) {
        val store = CountingReadsStore()
        val repository = SettingsRepository(store)
        repository.setFirstGameCompleted()
        repository.setPlayerCount(4)
        repository.setGameMode(GameMode.TRICOLOUR)
        store.finishedReads = 0

        val viewModel = restored(repository)

        assertEquals(1, store.finishedReads)
        assertEquals(4, viewModel.setupForm.setup.value.playerCount)
        assertEquals(GameMode.TRICOLOUR, viewModel.setupForm.setup.value.gameMode)
    }

    @Test
    fun `starting a game saves its whole form in one write - and the next form has all of it`() = runTest(testDispatcher) {
        val store = CountingWritesStore()
        val repository = SettingsRepository(store)
        repository.setFirstGameCompleted()
        val first = restored(repository)
        first.setupForm.setPlayerCount(4)
        first.setupForm.setPlayerName(1, "Ann")
        first.setupForm.setPlayerName(3, "Cal")
        first.setupForm.setPlayerType(2, PlayerType.AI)
        first.setupForm.setPlayerDifficulty(2, Difficulty.HARD)
        first.setupForm.setPlayerType(4, PlayerType.AI)
        first.setupForm.setPlayerDifficulty(4, Difficulty.EASY)
        first.setupForm.setPlayerColour(1, PlayerColour.PINK)
        first.setupForm.setGameMode(GameMode.TRICOLOUR)
        first.setupForm.setTurnTimer(TurnTimer.SECONDS_30)
        first.setupForm.setRollsPerTurn(4)
        first.setupForm.setUnluckyDiceEnabled(true)
        store.writes = 0

        first.startGame()
        advanceUntilIdle()

        assertEquals(1, store.writes)
        val second = restored(repository).setupForm.setup.value
        assertEquals(4, second.playerCount)
        assertEquals(listOf("Ann", "", "Cal", ""), second.playerSlots.map { it.name })
        assertEquals(listOf(PlayerType.HUMAN, PlayerType.AI, PlayerType.HUMAN, PlayerType.AI), second.playerSlots.map { it.type })
        assertEquals(Difficulty.HARD, second.playerSlots[1].difficulty)
        assertEquals(Difficulty.EASY, second.playerSlots[3].difficulty)
        assertEquals(PlayerColour.PINK, second.playerSlots[0].colour)
        assertEquals(GameMode.TRICOLOUR, second.gameMode)
        assertEquals(TurnTimer.SECONDS_30, second.turnTimer)
        assertEquals(4, second.rollModifiers.rollsPerTurn)
        assertTrue(second.unluckyDiceEnabled)
    }

    @Test
    fun `each seat has its own colour - a taken one swaps - and they're remembered and carried into the game unless they clash`() = runTest(testDispatcher) {
        val defaults = listOf(PlayerColour.CYAN, PlayerColour.GREEN, PlayerColour.PURPLE, PlayerColour.AMBER)
        val viewModel = gameViewModel(aiDispatcher = testDispatcher)
        assertEquals(defaults, viewModel.setupForm.setup.value.playerSlots.map { it.colour })

        // Picking a colour another player has swaps the two; a free one just replaces.
        viewModel.setupForm.setPlayerColour(1, PlayerColour.GREEN)
        assertEquals(listOf(PlayerColour.GREEN, PlayerColour.CYAN, PlayerColour.PURPLE, PlayerColour.AMBER), viewModel.setupForm.setup.value.playerSlots.map { it.colour })
        viewModel.setupForm.setPlayerColour(3, PlayerColour.PINK)
        val colours = viewModel.setupForm.setup.value.playerSlots.map { it.colour }
        assertEquals(PlayerColour.PINK, colours[2])
        assertEquals(4, colours.toSet().size)

        val repository = SettingsRepository(InMemoryPreferences())
        val first = restored(repository)
        first.setupForm.setPlayerColour(2, PlayerColour.LIME)
        first.startGame()
        advanceUntilIdle()
        assertEquals(PlayerColour.LIME, checkNotNull(first.game.value).players[1].colour)
        val second = restored(repository)
        assertEquals(PlayerColour.LIME, second.setupForm.setup.value.playerSlots[1].colour)
        assertEquals(4, second.setupForm.setup.value.playerSlots.map { it.colour }.toSet().size)

        // Saved colours that clash are dropped for the defaults.
        val clashing = SettingsRepository(InMemoryPreferences())
        for (slot in 1..4) clashing.setPlayerColour(slot, PlayerColour.RED)
        assertEquals(defaults, restored(clashing).setupForm.setup.value.playerSlots.map { it.colour })
    }

    @Test
    fun `every modifier is remembered between games - its value kept while it's off`() = runTest(testDispatcher) {
        val repository = SettingsRepository(InMemoryPreferences())
        repository.setFirstGameCompleted()
        val first = restored(repository)
        assertFalse(first.setupForm.setup.value.extendedScores)
        assertFalse(first.setupForm.setup.value.unluckyDiceEnabled)
        assertEquals(UnluckyDice(10, 1), first.setupForm.setup.value.unluckyDice)
        first.setupForm.setTurnTimer(TurnTimer.SECONDS_120)
        first.setupForm.setTurnTimer(TurnTimer.NONE)
        assertEquals(TurnTimer.SECONDS_120, first.setupForm.setup.value.turnTimerLength)
        first.setupForm.setRollsPerTurn(7)
        first.setupForm.setRollsPerTurn(null)
        first.setupForm.setStoredRolls(true)
        first.setupForm.setStoredRollsMax(12)
        first.setupForm.setExtendedScores(true)
        first.setupForm.setUnluckyDiceEnabled(true)
        first.setupForm.setUnluckyOdds(40)
        first.setupForm.setUnluckyMaxDice(3)
        first.startGame()
        advanceUntilIdle()

        val second = restored(repository).setupForm.setup.value
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
        val rolls = gameViewModel(aiDispatcher = testDispatcher)
        rolls.setupForm.setRollsPerTurn(5)
        rolls.setupForm.setStoredRolls(true)
        rolls.startGame()
        val game = checkNotNull(rolls.game.value)
        assertEquals(RollModifiers(rollsPerTurn = 5, storedRolls = true), game.rollModifiers)
        assertEquals(5, game.rollsRemaining)

        for (mode in GameMode.entries) {
            val viewModel = gameViewModel(aiDispatcher = testDispatcher)
            viewModel.setupForm.setGameMode(mode)
            viewModel.setupForm.setExtendedScores(true)
            viewModel.setupForm.setUnluckyDiceEnabled(true)
            viewModel.setupForm.setUnluckyOdds(20)
            viewModel.setupForm.setUnluckyMaxDice(2)
            viewModel.startGame()
            val started = checkNotNull(viewModel.game.value)
            assertEquals(UnluckyDice(20, 2), started.unluckyDice, mode.id)
            assertEquals(mode.allowsExtendedScores, started.extendedScores, mode.id)
            assertEquals(if (mode.allowsExtendedScores) mode.categories + ScoreCategory.EXTENDED else mode.categories, started.players.first().categories, mode.id)
        }

        // Hit List starts without Extended Scores - and keeps the switch on for the next mode.
        val hitList = gameViewModel(aiDispatcher = testDispatcher)
        hitList.setupForm.setExtendedScores(true)
        hitList.setupForm.setGameMode(GameMode.HIT_LIST)
        hitList.startGame()
        assertFalse(checkNotNull(hitList.game.value).extendedScores)
        assertTrue(hitList.setupForm.setup.value.extendedScores)
        hitList.setupForm.setGameMode(GameMode.STANDARD)
        hitList.startGame()
        assertTrue(checkNotNull(hitList.game.value).extendedScores)

        // Unlucky Dice's settings are kept while it is switched off, and a game starts without it.
        val unlucky = gameViewModel(aiDispatcher = testDispatcher)
        unlucky.setupForm.setUnluckyOdds(50)
        unlucky.setupForm.setUnluckyDiceEnabled(true)
        unlucky.setupForm.setUnluckyDiceEnabled(false)
        unlucky.startGame()
        assertEquals(null, checkNotNull(unlucky.game.value).unluckyDice)
        assertEquals(50, unlucky.setupForm.setup.value.unluckyDice.oddsPercent)
    }

    @Test
    fun `a name left blank is its seat's default in the player's language - and is saved blank`() = runTest(testDispatcher) {
        val viewModel = gameViewModel(aiDispatcher = testDispatcher)
        viewModel.setupForm.setPlayerCount(3)
        viewModel.setupForm.setPlayerName(2, "Wolfgang")
        assertEquals(listOf("", "Wolfgang", ""), viewModel.setupForm.setup.value.playerSlots.take(3).map { it.name })
        viewModel.startGame { slot -> "Joueur $slot" }
        assertEquals(listOf("Joueur 1", "Wolfgang"), checkNotNull(viewModel.game.value).players.take(2).map { it.name })

        val repository = SettingsRepository(InMemoryPreferences())
        repository.setPlayerName(1, "")
        repository.setPlayerName(2, "Wolfgang")
        repository.setPlayerName(3, "Player 4")
        val first = restored(repository)
        assertEquals(listOf("", "Wolfgang", "Player 4"), first.setupForm.setup.value.playerSlots.take(3).map { it.name })
        first.setupForm.setPlayerCount(2)
        first.startGame()
        advanceUntilIdle()
        assertEquals("", repository.playerNameFor(1).first())
        assertEquals("Wolfgang", repository.playerNameFor(2).first())
    }
}
