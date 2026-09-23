package net.zodac.dicefive.ui.game

import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.scores.PlayerScoreSummary
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** In-memory [AchievementStore], so the whole game -> unlock path runs with no Context. */
private class FakeAchievementStore : AchievementStore {

    private val _state = MutableStateFlow(AchievementsState())
    override val state: Flow<AchievementsState> = _state

    override suspend fun current(): AchievementsState = _state.value

    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) {
        _state.value = AchievementsState(
            unlockedAt = _state.value.unlockedAt + unlockedAt.filterKeys { it !in _state.value.unlockedAt },
            counters = _state.value.counters + counters,
        )
    }

    override suspend fun resetAll() {
        _state.value = AchievementsState()
    }

    val unlocked: Set<Achievement> get() = _state.value.unlockedAt.keys
}

/** Dice that always land on [value], so a test can deal itself a known hand. */
private class LoadedDice(private val value: Int) : Random() {
    override fun nextBits(bitCount: Int): Int = 0
    override fun nextInt(from: Int, until: Int): Int = value
}

/** Dice that land on [values] in order, cycling - five dice rolled once give exactly that hand. */
private class CountingDice(values: IntRange) : Random() {
    private val sequence = values.toList()
    private var index = 0

    override fun nextBits(bitCount: Int): Int = 0
    override fun nextInt(from: Int, until: Int): Int = sequence[index++ % sequence.size]
}

/** Enough of [ScoreDao] for the leaderboard read/write that `finishGame` sequences around. */
private class FakeScoreDao : ScoreDao {
    private val entries = mutableListOf<ScoreEntry>()

    override suspend fun insert(entry: ScoreEntry) {
        entries += entry
    }

    override suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry> =
        entries.sortedByDescending { it.score }.drop(offset).take(limit)

    override suspend fun count(): Int = entries.size

    override suspend fun bestScore(): Int? = entries.maxOfOrNull { it.score }

    override suspend fun distinctScores(): List<Int> = entries.map { it.score }.distinct()

    override suspend fun totalPoints(): Int? = entries.map { it.score }.sum().takeIf { entries.isNotEmpty() }

    override suspend fun playerSummaries(): List<PlayerScoreSummary> = emptyList()

    override suspend fun outcomesForPlayer(playerName: String): List<Boolean?> = emptyList()

    override suspend fun clearAll() {
        entries.clear()
    }

    /** For tests that need to inspect what was actually recorded, not just Score repository totals. */
    fun recorded(): List<ScoreEntry> = entries.toList()
}

/**
 * Covers the wiring between a finished game and a stored unlock, which `AchievementEngineTest`
 * (pure, hand-built `GameState`s) deliberately doesn't reach: the game-over transition in
 * `applyGameState`, the ordering inside `finishGame`, and the per-game counters the view model
 * collects while play happens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameAchievementsWiringTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Plays every turn of every player to the end: roll once, then take the first open category. */
    private fun GameViewModel.playToCompletion() {
        repeat(ScoreCategory.entries.size * (game.value?.players?.size ?: 1)) {
            val state = game.value ?: return
            if (state.isGameOver) return
            rollDice()
            val player = state.currentPlayer ?: return
            val open = ScoreCategory.entries.first { player.scorecard[it] == null }
            commitScore(open)
        }
    }

    /**
     * With a real score repository in play, `finishGame` actually suspends on the leaderboard read
     * before it ever reaches the achievement evaluation - which the no-repository tests above skip
     * entirely, since `scoreRepository?.bestScore()` on a null repository never suspends at all.
     */
    @Test
    fun `achievements are still recorded when the leaderboard read suspends first`() = runTest {
        val store = FakeAchievementStore()
        val dao = FakeScoreDao()
        val viewModel = GameViewModel(scoreRepository = ScoreRepository(dao), achievementsRepository = store)
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.playToCompletion()
        advanceUntilIdle()

        assertEquals("the human's score should be on the leaderboard", 1, dao.count())
        assertTrue("FIRST_GAME should unlock, got ${store.unlocked}", Achievement.FIRST_GAME in store.unlocked)
    }

    @Test
    fun `a maxed box pops mid-game, long before the results screen`() = runTest {
        val store = FakeAchievementStore()
        // Every die comes up 6, so the first roll is five 6s: 30 in Sixes, the maximum.
        val viewModel = GameViewModel(achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.SIXES)
        advanceUntilIdle()

        assertTrue("game should still be running", viewModel.game.value?.isGameOver == false)
        assertTrue("SIXES_30 should pop mid-game, got ${store.unlocked}", Achievement.SIXES_30 in store.unlocked)
    }

    @Test
    fun `a feat rolled straight out of the cup pops on the roll itself`() = runTest {
        val store = FakeAchievementStore()
        // 1-2-3-4-5 on the first throw: a large straight, out of the cup.
        val viewModel = GameViewModel(achievementsRepository = store, random = CountingDice(1..5))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        advanceUntilIdle()

        assertTrue(
            "FIRST_ROLL_LARGE_STRAIGHT should pop on the roll, got ${store.unlocked}",
            Achievement.FIRST_ROLL_LARGE_STRAIGHT in store.unlocked,
        )
        assertFalse(Achievement.FIRST_ROLL_5X in store.unlocked)
        assertFalse(Achievement.FIRST_ROLL_FULL_HOUSE in store.unlocked)
    }

    @Test
    fun `superuser mode no longer disqualifies a game - the cheat has to stay debuggable`() = runTest {
        // The cheat is compiled out of release builds, so there is nothing to assert there.
        assumeTrue(BuildConfig.DEBUG)

        val store = FakeAchievementStore()
        val viewModel = GameViewModel(achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        // The real unlock sequence: hold then unhold every die, in order.
        repeat(5) { die ->
            viewModel.toggleHold(die)
            viewModel.toggleHold(die)
        }
        assertTrue("superuser mode should be active", viewModel.superuserModeActive.value)

        viewModel.commitScore(ScoreCategory.SIXES)
        advanceUntilIdle()

        assertTrue("SIXES_30 should still unlock, got ${store.unlocked}", Achievement.SIXES_30 in store.unlocked)
    }

    @Test
    fun `finishing a two-human game records achievements`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(achievementsRepository = store)
        viewModel.setPlayerCount(2)
        viewModel.startGame()

        viewModel.playToCompletion()
        advanceUntilIdle()

        assertTrue("game should be over", viewModel.game.value?.isGameOver == true)
        assertTrue("FIRST_GAME should unlock, got ${store.unlocked}", Achievement.FIRST_GAME in store.unlocked)
        assertTrue("FIRST_WIN should unlock, got ${store.unlocked}", Achievement.FIRST_WIN in store.unlocked)
        assertEquals(1, store.state.first().counter(AchievementCounter.GAMES_PLAYED))
    }

    @Test
    fun `dice rolled by the human are counted towards the running total`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(achievementsRepository = store)
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.playToCompletion()
        advanceUntilIdle()

        // One roll of all five dice per turn, one turn per category.
        assertEquals(
            ScoreCategory.entries.size * 5,
            store.state.first().counter(AchievementCounter.DICE_ROLLED),
        )
    }

    @Test
    fun `a game finished by an AI still records the human's achievements`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(achievementsRepository = store)
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.startGame()

        // The AI plays itself via a coroutine, so its turns interleave with advanceUntilIdle.
        repeat(ScoreCategory.entries.size * 2) {
            advanceUntilIdle()
            val state = viewModel.game.value ?: return@repeat
            if (state.isGameOver) return@repeat
            if (state.currentPlayer?.type != PlayerType.HUMAN) return@repeat
            viewModel.rollDice()
            val player = state.currentPlayer ?: return@repeat
            viewModel.commitScore(ScoreCategory.entries.first { player.scorecard[it] == null })
        }
        advanceUntilIdle()

        assertTrue("game should be over", viewModel.game.value?.isGameOver == true)
        assertTrue("FIRST_GAME should unlock, got ${store.unlocked}", Achievement.FIRST_GAME in store.unlocked)
    }

    @Test
    fun `a solo game records no win outcome - there's nobody to beat`() = runTest {
        val dao = FakeScoreDao()
        val viewModel = GameViewModel(scoreRepository = ScoreRepository(dao))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.playToCompletion()
        advanceUntilIdle()

        assertEquals(1, dao.recorded().size)
        assertEquals(null, dao.recorded().single().won)
    }

    @Test
    fun `a two-human game records each player's win outcome against the top score`() = runTest {
        val dao = FakeScoreDao()
        val viewModel = GameViewModel(scoreRepository = ScoreRepository(dao))
        viewModel.setPlayerCount(2)
        viewModel.startGame()

        viewModel.playToCompletion()
        advanceUntilIdle()

        val finalState = viewModel.game.value!!
        val topScore = finalState.topScore
        val expected = finalState.players.associate { it.name to (it.totalScore == topScore) }
        val actual = dao.recorded().associate { it.playerName to it.won }

        assertEquals(expected, actual)
    }
}
