package net.zodac.dicefive.ui.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.game.TutorialMessage
import net.zodac.dicefive.game.TutorialScript
import net.zodac.dicefive.game.TutorialStep
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory

/** [InMemoryPreferences], counting its writes. */
private class CountingWrites(private val store: InMemoryPreferences = InMemoryPreferences()) : DataStore<Preferences> by store {
    var writes = 0
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = store.updateData(transform).also { writes++ }
}

/** The guided game: only the script's move can be made, and nothing is kept. */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorialGameTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Table(
        val viewModel: GameViewModel,
        val settings: CountingWrites,
        val saves: CountingWrites,
        val scores: InMemoryScoreDao,
        val achievements: InMemoryAchievementStore,
    )

    private fun TestScope.tutorial(settings: SettingsRepository? = null, settingsStore: CountingWrites = CountingWrites()): Table {
        val saves = CountingWrites()
        val scores = InMemoryScoreDao()
        val achievements = InMemoryAchievementStore()
        val viewModel = gameViewModel(
            scoreRepository = ScoreRepository(scores),
            settingsRepository = settings ?: SettingsRepository(settingsStore),
            inProgressGameRepository = InProgressGameRepository(saves, backgroundScope),
            achievementsRepository = achievements,
            aiDispatcher = testDispatcher,
            tutorial = TutorialScript.STANDARD,
        )
        advanceUntilIdle()
        viewModel.startTutorial(defaultName = "Player 1")
        advanceUntilIdle()
        return Table(viewModel, settingsStore, saves, scores, achievements)
    }

    /** Every step to the end: what each asks, played out. */
    private fun TestScope.playThrough(viewModel: GameViewModel) {
        while (true) doStep(viewModel, viewModel.tutorialStep.value ?: return)
    }

    /** Does what [step] asks, and lets it play out. */
    private fun TestScope.doStep(viewModel: GameViewModel, step: TutorialStep) {
        when (step) {
            is TutorialStep.OpenTotals -> viewModel.tutorialTotalsOpened()
            is TutorialStep.Roll -> viewModel.shakeAndRoll()
            is TutorialStep.Hold -> step.dice.forEach(viewModel::toggleHold)
            is TutorialStep.Score -> viewModel.commitScore(step.category)
            is TutorialStep.Undo -> viewModel.undo()
        }
        advanceUntilIdle()
    }

    @Test
    fun `played through solo - the game ends on 382 - and the steps run out with it`() = runTest(testDispatcher) {
        val viewModel = tutorial().viewModel
        assertEquals(listOf("Player 1"), viewModel.game.value?.players?.map { it.name })
        assertEquals(listOf(PlayerType.HUMAN), viewModel.game.value?.players?.map { it.type })

        playThrough(viewModel)

        val game = checkNotNull(viewModel.game.value)
        assertTrue(game.isGameOver)
        assertEquals(listOf(382), game.players.map { it.totalScore })
        assertNull(viewModel.tutorialStep.value)
    }

    @Test
    fun `only the step's move is taken - another roll - hold - box - or the totals do nothing`() = runTest(testDispatcher) {
        val viewModel = tutorial().viewModel
        // The welcome is the first roll's card: the totals don't skip it, and the cup rolls straight away.
        viewModel.tutorialTotalsOpened()
        advanceUntilIdle()
        assertEquals(TutorialMessage.WELCOME, viewModel.tutorialStep.value?.message)
        assertTrue(viewModel.tutorialStep.value is TutorialStep.Roll)
        viewModel.shakeAndRoll()
        advanceUntilIdle()
        assertEquals(listOf(6, 2, 6, 5, 6), viewModel.game.value?.dice?.map { it.value })

        // Hold the three 6s: the 2 can't be held, and nothing can be scored yet.
        viewModel.toggleHold(1)
        viewModel.commitScore(ScoreCategory.SIXES)
        viewModel.shakeAndRoll()
        advanceUntilIdle()
        assertEquals(listOf(false, false, false, false, false), viewModel.game.value?.dice?.map { it.isHeld })
        assertEquals(2, viewModel.game.value?.rollsRemaining)

        viewModel.toggleHold(0)
        // Not let go again: a second tap on a held die does nothing.
        viewModel.toggleHold(0)
        viewModel.toggleHold(2)
        assertEquals(listOf(true, false, true, false, false), viewModel.game.value?.dice?.map { it.isHeld })
        assertTrue(viewModel.tutorialStep.value is TutorialStep.Hold, "one die still to hold")
        viewModel.toggleHold(4)
        assertTrue(viewModel.tutorialStep.value is TutorialStep.Roll)
    }

    @Test
    fun `after a roll the next step shows once the dice have settled - but its move is taken from the moment they land`() = runTest(testDispatcher) {
        val viewModel = tutorial().viewModel
        viewModel.setPacing(GamePacing(cupShakeMillis = 400, diceAnimated = true))
        viewModel.shakeAndRoll()
        advanceTimeBy(401)
        runCurrent()
        // Landed, still tossing: the card still says to roll, but a hold is already the move.
        assertEquals(listOf(6, 2, 6, 5, 6), viewModel.game.value?.dice?.map { it.value })
        assertTrue(viewModel.tutorialStep.value is TutorialStep.Roll)
        advanceUntilIdle()
        assertEquals(TutorialMessage.HOLD_SIXES, viewModel.tutorialStep.value?.message)
    }

    @Test
    fun `undo works only at its step - taking back the score in Fives for the 4x`() = runTest(testDispatcher) {
        val viewModel = tutorial().viewModel
        while (viewModel.tutorialStep.value?.message != TutorialMessage.SCORE_FIVES_MISTAKE) doStep(viewModel, checkNotNull(viewModel.tutorialStep.value))
        // Nothing to undo yet, and nothing taken back if it's asked.
        assertFalse(viewModel.canUndo.value)
        viewModel.commitScore(ScoreCategory.FIVES)
        advanceUntilIdle()
        assertEquals(listOf(20), viewModel.game.value?.players?.single()?.scoresIn(ScoreCategory.FIVES))
        assertTrue(viewModel.canUndo.value)

        viewModel.undo()
        advanceUntilIdle()
        val game = checkNotNull(viewModel.game.value)
        assertTrue(game.players.single().isOpen(ScoreCategory.FIVES))
        assertEquals(listOf(5, 5, 6, 5, 5), game.dice.map { it.value })
        assertFalse(viewModel.canUndo.value)
        assertEquals(TutorialStep.Score(TutorialMessage.SCORE_FOUR_X, ScoreCategory.FOUR_OF_A_KIND), viewModel.tutorialStep.value)
    }

    @Test
    fun `back and forward jump between turns as if each were played - and the rest plays on from there`() = runTest(testDispatcher) {
        val viewModel = tutorial().viewModel
        assertFalse(viewModel.canJumpBack)
        assertTrue(viewModel.canJumpForward)

        // Forward from the first roll: turn 2, with turn 1's 5x scored.
        viewModel.jumpToTutorialTurn(1)
        advanceUntilIdle()
        assertEquals(2, viewModel.tutorialTurn.value)
        assertEquals(listOf(50), viewModel.game.value?.players?.single()?.scoresIn(ScoreCategory.FIVE_OF_A_KIND))
        assertEquals(TutorialMessage.ROLL_NEW_TURN, viewModel.tutorialStep.value?.message)

        // Part way through turn 2, back goes to its start - the roll and holds undone - then back again to turn 1.
        viewModel.shakeAndRoll()
        advanceUntilIdle()
        viewModel.jumpToTutorialTurn(-1)
        advanceUntilIdle()
        assertEquals(2, viewModel.tutorialTurn.value)
        assertEquals(3, viewModel.game.value?.rollsRemaining)
        viewModel.jumpToTutorialTurn(-1)
        advanceUntilIdle()
        assertEquals(1, viewModel.tutorialTurn.value)
        assertTrue(viewModel.game.value?.players?.single()?.isOpen(ScoreCategory.FIVE_OF_A_KIND) == true)

        // To the last turn: no further forward. Played from there, the game ends on 382.
        repeat(12) { viewModel.jumpToTutorialTurn(1) }
        advanceUntilIdle()
        assertEquals(13, viewModel.tutorialTurn.value)
        assertFalse(viewModel.canJumpForward)
        viewModel.jumpToTutorialTurn(1)
        assertEquals(13, viewModel.tutorialTurn.value)
        playThrough(viewModel)
        assertEquals(listOf(382), viewModel.game.value?.players?.map { it.totalScore })
    }

    @Test
    fun `nothing is kept - no saved game is touched - no scores - achievements - counters or settings`() = runTest(testDispatcher) {
        // A game the player left to continue later, which the tutorial mustn't replace.
        val saveStore = InMemoryPreferences()
        val saved = InProgressGameRepository(saveStore, backgroundScope)
        val ownGame = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Ann")), GameMode.TRICOLOUR)
        saved.save(ownGame)
        advanceUntilIdle()

        val table = tutorial()
        playThrough(table.viewModel)
        assertTrue(table.viewModel.game.value?.isGameOver == true)

        assertEquals(0, table.saves.writes, "the tutorial's game is never saved")
        assertEquals(ownGame, saved.load())
        assertEquals(0, table.settings.writes, "settings, the first-game unlock included")
        assertTrue(table.scores.allScores().isEmpty(), "no scores")
        assertEquals(AchievementsState(), table.achievements.mutableState.value, "no achievements or counters")
        assertFalse(table.viewModel.canUndo.value)
        assertFalse(table.viewModel.confirmBeforeLeavingGame.value)
    }

    @Test
    fun `the player plays under their saved name`() = runTest(testDispatcher) {
        val settings = SettingsRepository(InMemoryPreferences())
        settings.setPlayerName(1, "Wolfgang")
        assertEquals("Wolfgang", tutorial(settings = settings).viewModel.game.value?.players?.first()?.name)
    }
}
