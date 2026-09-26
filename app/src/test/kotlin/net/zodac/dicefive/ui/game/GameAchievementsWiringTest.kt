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
import net.zodac.dicefive.model.GameMode
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

    override suspend fun forceLock(achievement: Achievement) {
        _state.value = _state.value.copy(unlockedAt = _state.value.unlockedAt - achievement)
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

/** Dice that land on an exact, explicit [values] script in order, cycling once exhausted - for
 * tests that need to control several different rolls (not just one repeated hand) precisely,
 * e.g. a roll with holds where only some dice are re-rolled. A `values` sized to a whole multiple
 * of 5 with nothing held never actually needs to cycle; it's there so a short script can't run out. */
private class ScriptedDice(private val values: List<Int>) : Random() {
    private var index = 0

    override fun nextBits(bitCount: Int): Int = 0
    override fun nextInt(from: Int, until: Int): Int = values[index++ % values.size]
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

    override suspend fun bestScoreForPlayer(playerName: String): Int? =
        entries.filter { it.playerName == playerName }.maxOfOrNull { it.score }

    override suspend fun distinctScores(): List<Int> = entries.map { it.score }.distinct()

    override suspend fun primaryPlayerTotalPoints(): Int? =
        entries.filter { it.isPrimaryPlayer }.takeIf { it.isNotEmpty() }?.sumOf { it.score }

    override suspend fun playerSummaries(): List<PlayerScoreSummary> = emptyList()

    override suspend fun outcomesForPlayer(playerName: String): List<Boolean?> = emptyList()

    override suspend fun dismissPlayer(playerName: String) = Unit

    override suspend fun clearDismissal(playerName: String) = Unit

    override suspend fun clearAllScores() {
        entries.clear()
    }

    override suspend fun clearAllDismissals() = Unit

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
        repeat(GameMode.STANDARD.categories.size * (game.value?.players?.size ?: 1)) {
            val state = game.value ?: return
            if (state.isGameOver) return
            rollDice()
            val player = state.currentPlayer ?: return
            val open = GameMode.STANDARD.categories.first { player.scorecard[it] == null }
            commitScore(open)
        }
    }

    /**
     * With a real score repository in play, `finishGame` actually suspends on the leaderboard read
     * before it ever reaches the achievement evaluation - which the no-repository tests above skip
     * entirely, since `scoreRepository?.bestScoreForPlayer(name)` on a null repository never
     * suspends at all.
     */
    @Test
    fun `achievements are still recorded when the leaderboard read suspends first`() = runTest {
        val store = FakeAchievementStore()
        val dao = FakeScoreDao()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, scoreRepository = ScoreRepository(dao), achievementsRepository = store)
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.playToCompletion()
        advanceUntilIdle()

        assertEquals("the human's score should be on the leaderboard", 1, dao.count())
        assertTrue("SOLO_GAME should unlock, got ${store.unlocked}", Achievement.SOLO_GAME in store.unlocked)
    }

    @Test
    fun `a maxed box pops mid-game, long before the results screen`() = runTest {
        val store = FakeAchievementStore()
        // Every die comes up 6, so the first roll is five 6s: 30 in Sixes, the maximum.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
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
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = CountingDice(1..5))
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
    fun `rolling a full house on the first roll does not unlock House Call once Full House is already zeroed`() = runTest {
        val store = FakeAchievementStore()
        // Turn 1: a pair with no triple - no full house at all - zeroes Full House. Turn 2: three
        // 6s and two 5s, a genuine full house, but the box is no longer a real option.
        val viewModel = GameViewModel(
            aiDispatcher = testDispatcher,
            achievementsRepository = store,
            random = ScriptedDice(listOf(1, 1, 2, 3, 4, 6, 6, 6, 5, 5)),
        )
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.FULL_HOUSE)

        viewModel.rollDice()
        advanceUntilIdle()

        assertFalse(
            "FIRST_ROLL_FULL_HOUSE should not pop once Full House is zeroed, got ${store.unlocked}",
            Achievement.FIRST_ROLL_FULL_HOUSE in store.unlocked,
        )
    }

    @Test
    fun `rolling a large straight on the first roll does not unlock Straight Away once Large Straight is already zeroed`() = runTest {
        val store = FakeAchievementStore()
        // Turn 1: five 6s - no straight at all - zeroes Large Straight. Turn 2: 1-2-3-4-5, a
        // genuine large straight, but the box is no longer a real option.
        val viewModel = GameViewModel(
            aiDispatcher = testDispatcher,
            achievementsRepository = store,
            random = ScriptedDice(listOf(6, 6, 6, 6, 6, 1, 2, 3, 4, 5)),
        )
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.LARGE_STRAIGHT)

        viewModel.rollDice()
        advanceUntilIdle()

        assertFalse(
            "FIRST_ROLL_LARGE_STRAIGHT should not pop once Large Straight is zeroed, got ${store.unlocked}",
            Achievement.FIRST_ROLL_LARGE_STRAIGHT in store.unlocked,
        )
    }

    @Test
    fun `scoring three 6s and two 5s in Full House unlocks Fuller House`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(listOf(6, 6, 6, 5, 5)))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.FULL_HOUSE)
        advanceUntilIdle()

        assertTrue("FULLER_HOUSE should pop, got ${store.unlocked}", Achievement.FULLER_HOUSE in store.unlocked)
    }

    @Test
    fun `Fuller House does not care what order the dice landed in`() = runTest {
        val store = FakeAchievementStore()
        // Same three 6s and two 5s as above, interleaved rather than grouped.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(listOf(6, 5, 6, 5, 6)))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.FULL_HOUSE)
        advanceUntilIdle()

        assertTrue("FULLER_HOUSE should pop regardless of dice order, got ${store.unlocked}", Achievement.FULLER_HOUSE in store.unlocked)
    }

    @Test
    fun `a full house of other values does not unlock Fuller House`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(listOf(3, 3, 3, 2, 2)))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.FULL_HOUSE)
        advanceUntilIdle()

        assertFalse(Achievement.FULLER_HOUSE in store.unlocked)
    }

    @Test
    fun `a 5x used as a joker to fill Full House does not unlock Fuller House`() = runTest {
        val store = FakeAchievementStore()
        // Turn 1: an all-1s roll closes Sixes at 0, so the joker rule can't force it later. Turns
        // 2 and 3: an all-6s roll banks the 5x box, then the same-shaped roll fills Full House via
        // the joker rule instead - not a genuine three-and-two split.
        val script = List(5) { 1 } + List(5) { 6 } + List(5) { 6 }
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(script))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.SIXES)
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.FIVE_OF_A_KIND)
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.FULL_HOUSE)
        advanceUntilIdle()

        assertFalse(
            "a joker-rule full house should not unlock Fuller House, got ${store.unlocked}",
            Achievement.FULLER_HOUSE in store.unlocked,
        )
    }

    @Test
    fun `rolling a 5x but scoring it as a zero elsewhere unlocks Wasted Fortune`() = runTest {
        val store = FakeAchievementStore()
        // Every die comes up 6: a genuine 5x, but committed to Ones - no die shows a 1, so it scores 0.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.ONES)
        advanceUntilIdle()

        assertTrue("WASTED_5X should pop on the commit itself, got ${store.unlocked}", Achievement.WASTED_5X in store.unlocked)
    }

    @Test
    fun `scoring the 5x box itself with a real 5x does not count as wasting it`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.FIVE_OF_A_KIND)
        advanceUntilIdle()

        assertFalse("scoring 50 in the 5x box is not a waste, got ${store.unlocked}", Achievement.WASTED_5X in store.unlocked)
    }

    @Test
    fun `wasting a 5x on player 2's turn does not unlock Wasted Fortune - only player 1 earns achievements`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(2)
        viewModel.startGame()

        // Player 1's turn: commit the 5x itself, so nothing of theirs is wasted.
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.FIVE_OF_A_KIND)
        // Player 2's turn: a genuine 5x, deliberately wasted on Ones - would unlock WASTED_5X if it
        // were player 1's own turn.
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.ONES)
        advanceUntilIdle()

        assertFalse(
            "wasting a 5x on another human's turn must not unlock an achievement, got ${store.unlocked}",
            Achievement.WASTED_5X in store.unlocked,
        )
    }

    @Test
    fun `superuser mode no longer disqualifies a game - the cheat has to stay debuggable`() = runTest {
        // The cheat is compiled out of release builds, so there is nothing to assert there.
        assumeTrue(BuildConfig.DEBUG)

        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
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
        // Every die comes up 6, so both players fill identical scorecards in the same order - a
        // tie, which counts as a win for player 1 (the only player whose achievements this test can
        // rely on - see AchievementEngine's player-1-only rule) - keeping this test deterministic
        // rather than depending on which of the two humans happens to roll better.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(2)
        viewModel.startGame()

        viewModel.playToCompletion()
        advanceUntilIdle()

        assertTrue("game should be over", viewModel.game.value?.isGameOver == true)
        assertTrue("FIRST_WIN should unlock, got ${store.unlocked}", Achievement.FIRST_WIN in store.unlocked)
        assertEquals(1, store.state.first().counter(AchievementCounter.GAMES_PLAYED))
    }

    @Test
    fun `dice rolled by the human are counted towards the running total`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store)
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.playToCompletion()
        advanceUntilIdle()

        // One roll of all five dice per turn, one turn per category.
        assertEquals(
            GameMode.STANDARD.categories.size * 5,
            store.state.first().counter(AchievementCounter.DICE_ROLLED),
        )
    }

    @Test
    fun `a second human seat's dice rolls don't count towards Well Rolled - only player 1's do`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store)
        // Both default to HUMAN - a local pass-and-play game, not player 1 vs. the CPU.
        viewModel.setPlayerCount(2)
        viewModel.startGame()

        viewModel.playToCompletion()
        advanceUntilIdle()

        // If player 2's rolls counted too, this would be double - one roll of five dice per turn,
        // one turn per category, for player 1 alone.
        assertEquals(
            GameMode.STANDARD.categories.size * 5,
            store.state.first().counter(AchievementCounter.DICE_ROLLED),
        )
    }

    @Test
    fun `a game finished by an AI still records the human's achievements`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store)
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.startGame()

        // The AI plays itself via a coroutine, so its turns interleave with advanceUntilIdle.
        repeat(GameMode.STANDARD.categories.size * 2) {
            advanceUntilIdle()
            val state = viewModel.game.value ?: return@repeat
            if (state.isGameOver) return@repeat
            if (state.currentPlayer?.type != PlayerType.HUMAN) return@repeat
            viewModel.rollDice()
            val player = state.currentPlayer ?: return@repeat
            viewModel.commitScore(GameMode.STANDARD.categories.first { player.scorecard[it] == null })
        }
        advanceUntilIdle()

        assertTrue("game should be over", viewModel.game.value?.isGameOver == true)
        assertEquals(1, store.state.first().counter(AchievementCounter.GAMES_PLAYED))
    }

    @Test
    fun `a solo game records no win outcome - there's nobody to beat`() = runTest {
        val dao = FakeScoreDao()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, scoreRepository = ScoreRepository(dao))
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
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, scoreRepository = ScoreRepository(dao))
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

    @Test
    fun `each recorded score carries how many 5x that player scored`() = runTest {
        val dao = FakeScoreDao()
        // Every roll is five 6s, so the game scores its 5x box and then bonus chips.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, scoreRepository = ScoreRepository(dao), random = LoadedDice(6))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.playToCompletion()
        advanceUntilIdle()

        val player = viewModel.game.value!!.players.single()
        assertTrue("a game of nothing but 6s should score at least one 5x", player.fiveOfAKindCount > 0)
        assertEquals(player.fiveOfAKindCount, dao.recorded().single().fiveOfAKindCount)
    }

    @Test
    fun `only player 1's recorded score is flagged as the primary player`() = runTest {
        val dao = FakeScoreDao()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, scoreRepository = ScoreRepository(dao))
        viewModel.setPlayerCount(2)
        viewModel.startGame()

        viewModel.playToCompletion()
        advanceUntilIdle()

        val finalState = viewModel.game.value!!
        val playerOneName = finalState.players[0].name
        val flaggedRows = dao.recorded().associate { it.playerName to it.isPrimaryPlayer }

        assertTrue("player 1's own row should be flagged", flaggedRows.getValue(playerOneName))
        assertEquals("no other row should be flagged as the primary player", 1, flaggedRows.values.count { it })
    }

    // ---- The interaction-driven batch: rolls, holds and commits watched as they happen ---------

    @Test
    fun `performing the hidden hold sequence unlocks Time Wasting regardless`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        repeat(5) { die ->
            viewModel.toggleHold(die)
            viewModel.toggleHold(die)
        }
        advanceUntilIdle()

        assertTrue("TIME_WASTING should pop, got ${store.unlocked}", Achievement.TIME_WASTING in store.unlocked)
    }

    @Test
    fun `the hidden hold sequence on player 2's turn still activates the cheat but not Time Wasting`() = runTest {
        // The cheat itself stays "any player's turn" - only the achievement is player 1's alone.
        assumeTrue(BuildConfig.DEBUG)

        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(2)
        viewModel.startGame()

        // Player 1's turn: pass without touching the sequence.
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.ONES)
        // Player 2's turn: perform the real unlock sequence - hold then unhold every die, in order.
        viewModel.rollDice()
        repeat(5) { die ->
            viewModel.toggleHold(die)
            viewModel.toggleHold(die)
        }
        advanceUntilIdle()

        assertTrue("the cheat itself still works on any seat's turn", viewModel.superuserModeActive.value)
        assertFalse("TIME_WASTING is player 1's alone, got ${store.unlocked}", Achievement.TIME_WASTING in store.unlocked)
    }

    @Test
    fun `rolling the exact same result twice in a row with no holds in between unlocks Deja Vu`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(listOf(3, 1, 4, 1, 5)))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.rollDice()
        advanceUntilIdle()

        assertTrue("DEJA_VU should pop, got ${store.unlocked}", Achievement.DEJA_VU in store.unlocked)
    }

    @Test
    fun `holding a die between two identical rolls breaks Deja Vu`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(listOf(3, 1, 4, 1, 5)))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.toggleHold(0)
        viewModel.toggleHold(0)
        viewModel.rollDice()
        advanceUntilIdle()

        assertFalse("a hold in between should disqualify it, got ${store.unlocked}", Achievement.DEJA_VU in store.unlocked)
    }

    @Test
    fun `a die held (but unchanged) across two identical unheld results does not unlock Deja Vu`() = runTest {
        val store = FakeAchievementStore()
        // Roll 1: [1,2,2,2,3]; hold die 0 (value 1) and leave it held. Roll 2 lands [1,4,4,4,4].
        // Roll 3 re-rolls the same four unheld dice back to [4,4,4,4] - the FULL five-dice result
        // matches roll 2's exactly, and nothing was toggled in between, but die 0 is still held -
        // this is Are These Loaded Dice?'s territory (a partial hold), not Déjà Vu's (nothing held).
        val dice = ScriptedDice(listOf(1, 2, 2, 2, 3, 4, 4, 4, 4, 4, 4, 4, 4))
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = dice)
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.toggleHold(0)
        viewModel.rollDice()
        viewModel.rollDice()
        advanceUntilIdle()

        assertFalse(
            "a die still held right now must disqualify Deja Vu, even if the hold itself is unchanged, got ${store.unlocked}",
            Achievement.DEJA_VU in store.unlocked,
        )
    }

    @Test
    fun `unheld dice landing on the same values through both re-rolls unlocks Are These Loaded Dice`() = runTest {
        val store = FakeAchievementStore()
        // Roll 1: [1,2,2,2,3]; hold the three 2s; rolls 2 and 3 land the same 1 and 3 in the gaps.
        val dice = ScriptedDice(listOf(1, 2, 2, 2, 3, 1, 3, 1, 3))
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = dice)
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.toggleHold(1)
        viewModel.toggleHold(2)
        viewModel.toggleHold(3)
        viewModel.rollDice()
        viewModel.rollDice()
        advanceUntilIdle()

        assertTrue("LOADED_DICE should pop, got ${store.unlocked}", Achievement.LOADED_DICE in store.unlocked)
    }

    @Test
    fun `scoring a 5x on two turns in a row unlocks Twice in a Lifetime`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(2))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.FIVE_OF_A_KIND)
        viewModel.rollDice()
        // Forced by the joker rule (Twos is the matching upper box) - still a genuine bonus 5x.
        viewModel.commitScore(ScoreCategory.TWOS)
        advanceUntilIdle()

        assertTrue("TWICE_IN_A_LIFETIME should pop, got ${store.unlocked}", Achievement.TWICE_IN_A_LIFETIME in store.unlocked)
    }

    @Test
    fun `rolling a 5x on the 2nd roll without holding anything unlocks Natural 5x`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.rollDice()
        advanceUntilIdle()

        assertTrue("NATURAL_5X should pop, got ${store.unlocked}", Achievement.NATURAL_5X in store.unlocked)
    }

    @Test
    fun `rolling a 5x does not unlock First Roll 5x or Natural 5x once Five Of A Kind is already zeroed`() = runTest {
        val store = FakeAchievementStore()
        // Turn 1: no 5x at all - zeroes Five Of A Kind. Turn 2: five 6s on both rolls - a genuine
        // 5x on the first roll and (nothing held) the second, but the box is no longer a real
        // option, and it hasn't shown a genuine 50 for the joker bonus to kick in either.
        val viewModel = GameViewModel(
            aiDispatcher = testDispatcher,
            achievementsRepository = store,
            random = ScriptedDice(listOf(1, 2, 3, 4, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6)),
        )
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.FIVE_OF_A_KIND)

        viewModel.rollDice()
        viewModel.rollDice()
        advanceUntilIdle()

        assertFalse(
            "FIRST_ROLL_5X should not pop once Five Of A Kind is zeroed, got ${store.unlocked}",
            Achievement.FIRST_ROLL_5X in store.unlocked,
        )
        assertFalse(
            "NATURAL_5X should not pop once Five Of A Kind is zeroed, got ${store.unlocked}",
            Achievement.NATURAL_5X in store.unlocked,
        )
    }

    @Test
    fun `rolling the menu logo's exact dice unlocks Product Placement`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(listOf(2, 4, 5, 3, 6)))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        advanceUntilIdle()

        assertTrue("PRODUCT_PLACEMENT should pop, got ${store.unlocked}", Achievement.PRODUCT_PLACEMENT in store.unlocked)
    }

    @Test
    fun `rolling 1,2,3,4,5 on the first roll unlocks I Can Count`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = CountingDice(1..5))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        advanceUntilIdle()

        assertTrue("I_CAN_COUNT should pop, got ${store.unlocked}", Achievement.I_CAN_COUNT in store.unlocked)
    }

    @Test
    fun `switching from one held matching pair to a different one and scoring it unlocks Commitment Issues`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(listOf(6, 6, 1, 3, 3)))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice() // [6,6,1,3,3]
        viewModel.toggleHold(0)
        viewModel.toggleHold(1) // holding exactly the two 6s
        viewModel.toggleHold(0)
        viewModel.toggleHold(1) // released
        viewModel.toggleHold(3)
        viewModel.toggleHold(4) // holding exactly the two 3s
        viewModel.commitScore(ScoreCategory.THREES)
        advanceUntilIdle()

        assertTrue("COMMITMENT_ISSUES should pop, got ${store.unlocked}", Achievement.COMMITMENT_ISSUES in store.unlocked)
    }

    @Test
    fun `a single held die switching to a different single die still unlocks Commitment Issues`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(listOf(6, 1, 2, 3, 4)))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice() // [6,1,2,3,4]
        viewModel.toggleHold(0) // holding exactly the single 6
        viewModel.toggleHold(0) // released
        viewModel.toggleHold(1) // holding exactly the single 1
        viewModel.commitScore(ScoreCategory.ONES)
        advanceUntilIdle()

        assertTrue("a single die counts as a group too, got ${store.unlocked}", Achievement.COMMITMENT_ISSUES in store.unlocked)
    }

    @Test
    fun `a four of a kind switching to a different value still unlocks Commitment Issues`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(listOf(6, 6, 6, 6, 3)))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice() // [6,6,6,6,3]
        repeat(4) { viewModel.toggleHold(it) } // holding exactly the four 6s
        repeat(4) { viewModel.toggleHold(it) } // released
        viewModel.toggleHold(4) // holding exactly the single 3
        viewModel.commitScore(ScoreCategory.THREES)
        advanceUntilIdle()

        assertTrue("a four of a kind counts as a group too, got ${store.unlocked}", Achievement.COMMITMENT_ISSUES in store.unlocked)
    }

    @Test
    fun `holding all five of the same value is a 5x, not a Commitment Issues group`() = runTest {
        val store = FakeAchievementStore()
        // Roll 1: five 6s. Once released (however far it shrinks back down first), that's a 5x, not
        // indecision - it must not become the baseline the later group of 3s is compared against.
        val script = listOf(6, 6, 6, 6, 6) + listOf(3, 3, 3, 3, 3)
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(script))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice() // [6,6,6,6,6]
        repeat(5) { viewModel.toggleHold(it) } // all five held - a genuine 5x
        repeat(5) { viewModel.toggleHold(it) } // released again
        viewModel.rollDice() // the (now unheld) dice reroll to [3,3,3,3,3]
        repeat(4) { viewModel.toggleHold(it) } // holding exactly four of the five 3s
        viewModel.commitScore(ScoreCategory.THREES)
        advanceUntilIdle()

        assertFalse(
            "the only thing ever released was a 5x, so there's no baseline to differ from",
            Achievement.COMMITMENT_ISSUES in store.unlocked,
        )
    }

    @Test
    fun `holding and unholding the same die three times unlocks Decisions Decisions`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(4))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        repeat(3) {
            viewModel.toggleHold(0)
            viewModel.toggleHold(0)
        }
        advanceUntilIdle()

        assertTrue("DECISIONS_DECISIONS should pop, got ${store.unlocked}", Achievement.DECISIONS_DECISIONS in store.unlocked)
    }

    @Test
    fun `holding and unholding split across a roll does not unlock Decisions Decisions`() = runTest {
        val store = FakeAchievementStore()
        // Two cycles, a roll, then one more cycle - three total, but "before rolling again" means
        // rolling in between should wipe the first two rather than letting them carry over.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(4))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        repeat(2) {
            viewModel.toggleHold(0)
            viewModel.toggleHold(0)
        }
        viewModel.rollDice()
        viewModel.toggleHold(0)
        viewModel.toggleHold(0)
        advanceUntilIdle()

        assertFalse(
            "DECISIONS_DECISIONS should not pop when cycles are split across a roll, got ${store.unlocked}",
            Achievement.DECISIONS_DECISIONS in store.unlocked,
        )
    }

    @Test
    fun `holding and unholding split across turns does not unlock Decisions Decisions`() = runTest {
        val store = FakeAchievementStore()
        // Two cycles in turn 1, committed without a third; turn 2 (same solo player) does one more
        // - a turn boundary has to wipe partial progress the same as a mid-turn roll does, not let
        // it "travel" into the next turn just because it's the same player again.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(4))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        repeat(2) {
            viewModel.toggleHold(0)
            viewModel.toggleHold(0)
        }
        viewModel.commitScore(ScoreCategory.FOURS)

        viewModel.rollDice()
        viewModel.toggleHold(0)
        viewModel.toggleHold(0)
        advanceUntilIdle()

        assertFalse(
            "DECISIONS_DECISIONS should not pop when cycles are split across turns, got ${store.unlocked}",
            Achievement.DECISIONS_DECISIONS in store.unlocked,
        )
    }

    @Test
    fun `holding a die through both re-rolls, then scoring a category it doesn't count towards, unlocks Time to Let It Go`() = runTest {
        val store = FakeAchievementStore()
        // Every die (held or not) always shows 6 - die 0 is held from right after roll 1, through
        // both re-rolls, then Ones is scored: die 0's face is never counted towards it at all.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.toggleHold(0)
        viewModel.rollDice()
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.ONES)
        advanceUntilIdle()

        assertTrue("TIME_TO_LET_IT_GO should pop, got ${store.unlocked}", Achievement.TIME_TO_LET_IT_GO in store.unlocked)
    }

    @Test
    fun `unholding the die before committing still unlocks Time to Let It Go - only whether it was used matters`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.toggleHold(0)
        viewModel.rollDice()
        viewModel.rollDice()
        // Let it go before committing - its held/unheld state right now must not matter.
        viewModel.toggleHold(0)
        viewModel.commitScore(ScoreCategory.ONES)
        advanceUntilIdle()

        assertTrue("TIME_TO_LET_IT_GO should pop regardless of the die's final held state", Achievement.TIME_TO_LET_IT_GO in store.unlocked)
    }

    @Test
    fun `scoring the upper category that matches the held die does not unlock Time to Let It Go`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.toggleHold(0)
        viewModel.rollDice()
        viewModel.rollDice()
        // Sixes counts the held die's own face - it WAS used, so this must not pop.
        viewModel.commitScore(ScoreCategory.SIXES)
        advanceUntilIdle()

        assertFalse(
            "scoring the category the held die counts towards must not unlock TIME_TO_LET_IT_GO",
            Achievement.TIME_TO_LET_IT_GO in store.unlocked,
        )
    }

    @Test
    fun `holding a die through only one re-roll does not unlock Time to Let It Go`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(6))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.toggleHold(0)
        viewModel.rollDice()
        // Released before the 3rd roll - only held through one re-roll, not both.
        viewModel.toggleHold(0)
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.ONES)
        advanceUntilIdle()

        assertFalse(
            "a die held through only one re-roll must not unlock TIME_TO_LET_IT_GO",
            Achievement.TIME_TO_LET_IT_GO in store.unlocked,
        )
    }

    @Test
    fun `holding all five dice then rolling anyway unlocks What Was the Point of That`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(4))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        repeat(5) { viewModel.toggleHold(it) }
        viewModel.rollDice()
        advanceUntilIdle()

        assertTrue("POINTLESS_ROLL should pop, got ${store.unlocked}", Achievement.POINTLESS_ROLL in store.unlocked)
    }

    @Test
    fun `holding all five dice then releasing every one unlocks A Cunning Strategy`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(4))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        repeat(5) { viewModel.toggleHold(it) }
        repeat(5) { viewModel.toggleHold(it) }
        advanceUntilIdle()

        assertTrue("CUNNING_STRATEGY should pop, got ${store.unlocked}", Achievement.CUNNING_STRATEGY in store.unlocked)
    }

    @Test
    fun `tapping the cup three times with no rolls left unlocks No More Rolls`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(3))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.rollDice()
        viewModel.rollDice()
        viewModel.tapCupWithNoRollsLeft()
        viewModel.tapCupWithNoRollsLeft()
        viewModel.tapCupWithNoRollsLeft()
        advanceUntilIdle()

        assertTrue("NO_MORE_ROLLS should pop, got ${store.unlocked}", Achievement.NO_MORE_ROLLS in store.unlocked)
    }

    @Test
    fun `holding a first-roll four of a kind through every roll, with no 5x, unlocks Almost Famous`() = runTest {
        val store = FakeAchievementStore()
        // Roll 1: four 6s (indices 0-3) and a lone 1 (index 4). The four 6s are held from here on,
        // so only the loose die is re-rolled for rolls 2 and 3 - landing on 2, then 3, never a
        // matching 6.
        val viewModel = GameViewModel(
            aiDispatcher = testDispatcher,
            achievementsRepository = store,
            random = ScriptedDice(listOf(6, 6, 6, 6, 1, 2, 3)),
        )
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.toggleHold(0)
        viewModel.toggleHold(1)
        viewModel.toggleHold(2)
        viewModel.toggleHold(3)
        viewModel.rollDice()
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.SIXES)
        advanceUntilIdle()

        assertTrue("ALMOST_FAMOUS should pop, got ${store.unlocked}", Achievement.ALMOST_FAMOUS in store.unlocked)
    }

    @Test
    fun `breaking the held four of a kind before the last roll does not unlock Almost Famous`() = runTest {
        val store = FakeAchievementStore()
        // Same first roll as above, but the four 6s are only held for roll 2, not roll 3 - all
        // five dice are back in play for the last roll, so the loose die was never actually the
        // only thing still being chased.
        val viewModel = GameViewModel(
            aiDispatcher = testDispatcher,
            achievementsRepository = store,
            random = ScriptedDice(listOf(6, 6, 6, 6, 1, 2, 1, 2, 3, 4)),
        )
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.toggleHold(0)
        viewModel.toggleHold(1)
        viewModel.toggleHold(2)
        viewModel.toggleHold(3)
        viewModel.rollDice()
        // Unhold everything before the last roll - the four 6s are no longer held going into it.
        viewModel.toggleHold(0)
        viewModel.toggleHold(1)
        viewModel.toggleHold(2)
        viewModel.toggleHold(3)
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        advanceUntilIdle()

        assertFalse(
            "ALMOST_FAMOUS should not pop once the held four of a kind was broken up, got ${store.unlocked}",
            Achievement.ALMOST_FAMOUS in store.unlocked,
        )
    }

    @Test
    fun `committing after only one roll does not unlock Almost Famous`() = runTest {
        val store = FakeAchievementStore()
        // Four of a kind on the first roll, same as above, but committed straight away - the
        // player never got a real chance (2nd/3rd roll) to try turning it into a 5x, so this
        // isn't "almost" anything.
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(listOf(6, 6, 6, 6, 1)))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.SIXES)
        advanceUntilIdle()

        assertFalse("ALMOST_FAMOUS should not pop after only one roll, got ${store.unlocked}", Achievement.ALMOST_FAMOUS in store.unlocked)
    }

    @Test
    fun `a four of a kind flag from an abandoned turn does not leak into the next turn's Almost Famous`() = runTest {
        val store = FakeAchievementStore()
        // Turn 1: four 6s on the first roll, committed immediately - not "almost" anything (see
        // the test above), but the flag that sets must not survive into turn 2. Turn 2 (same solo
        // player): no four of a kind at all, but all three rolls get used and committed - if the
        // flag leaked, this would wrongly unlock too.
        val viewModel = GameViewModel(
            aiDispatcher = testDispatcher,
            achievementsRepository = store,
            random = ScriptedDice(listOf(6, 6, 6, 6, 1) + listOf(1, 2, 3, 4, 6) + listOf(1, 2, 3, 4, 6) + listOf(1, 2, 3, 4, 6)),
        )
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.SIXES)

        viewModel.rollDice()
        viewModel.rollDice()
        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        advanceUntilIdle()

        assertFalse(
            "ALMOST_FAMOUS should not leak a stale four-of-a-kind flag into the next turn, got ${store.unlocked}",
            Achievement.ALMOST_FAMOUS in store.unlocked,
        )
    }

    @Test
    fun `scoring the small straight while the large straight was also available unlocks Why Did You Do That`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = CountingDice(1..5))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.SMALL_STRAIGHT)
        advanceUntilIdle()

        assertTrue("WHY_DID_YOU_DO_THAT should pop, got ${store.unlocked}", Achievement.WHY_DID_YOU_DO_THAT in store.unlocked)
    }

    @Test
    fun `undoing a score and choosing a different category unlocks I Didn't Mean That`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(3))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.THREES)
        viewModel.undo()
        viewModel.commitScore(ScoreCategory.CHANCE)
        advanceUntilIdle()

        assertTrue("UNDO_DIFFERENT_CATEGORY should pop, got ${store.unlocked}", Achievement.UNDO_DIFFERENT_CATEGORY in store.unlocked)
    }

    @Test
    fun `undoing a score and recommitting the same category does not unlock I Didn't Mean That`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = LoadedDice(3))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.THREES)
        viewModel.undo()
        viewModel.commitScore(ScoreCategory.THREES)
        advanceUntilIdle()

        assertFalse(Achievement.UNDO_DIFFERENT_CATEGORY in store.unlocked)
    }

    @Test
    fun `a real scoring option after roll 2 that's gone after roll 3 unlocks The Dice Hate Me`() = runTest {
        val store = FakeAchievementStore()
        // 12 filler turns (every category but Ones) rolling all-2s, then a final turn on Ones:
        // roll 2 shows a 1 (a real option), roll 3 doesn't (no option left at all).
        val script = List(65) { 2 } + listOf(1, 2, 2, 2, 2) + List(5) { 2 }
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store, random = ScriptedDice(script))
        viewModel.setPlayerCount(1)
        viewModel.startGame()

        for (category in GameMode.STANDARD.categories.filter { it != ScoreCategory.ONES }) {
            viewModel.rollDice()
            viewModel.commitScore(category)
        }
        viewModel.rollDice()
        viewModel.rollDice()
        viewModel.rollDice()
        advanceUntilIdle()

        assertTrue("DICE_HATE_ME should pop, got ${store.unlocked}", Achievement.DICE_HATE_ME in store.unlocked)
    }

    @Test
    fun `starting a new game right after finishing one that wasn't lost does not unlock One More Time`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store)
        viewModel.setPlayerCount(1)
        viewModel.startGame()
        viewModel.playToCompletion()
        advanceUntilIdle()

        // A solo game is never "lost" - nobody to lose to - so replaying it must not count.
        viewModel.startGame()
        advanceUntilIdle()

        assertFalse(Achievement.REPLAY_AFTER_LOSS in store.unlocked)
    }

    /**
     * The real "picked a non-default style" -> unlock path needs [net.zodac.dicefive.data.settings.SettingsRepository],
     * which - unlike [AchievementStore]/[ScoreDao] - is a concrete DataStore-backed class with no
     * fake to substitute here (see `.claude/DESIGN.md`'s note on the same gap for CONTINUED_GAME).
     * What IS covered on a plain JVM: starting (or resuming) a game with no settings repository at
     * all doesn't unlock a style achievement out of nowhere, and doesn't crash - `checkGameStartAchievements`
     * returning early on a null repository is the only thing standing in for a real style pick here.
     * `AchievementEngineTest` covers the actual "non-default -> unlocked" decision, which now lives
     * in the pure `AchievementEngine.evaluateAtGameStart` rather than here.
     */
    @Test
    fun `starting a game with no settings repository does not unlock a style achievement`() = runTest {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, achievementsRepository = store)
        viewModel.setPlayerCount(1)

        viewModel.startGame()
        advanceUntilIdle()

        assertFalse(Achievement.FRESH_COAT_OF_PAINT in store.unlocked)
    }
}
