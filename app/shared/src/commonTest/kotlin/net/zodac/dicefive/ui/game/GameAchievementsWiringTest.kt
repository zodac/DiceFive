package net.zodac.dicefive.ui.game

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.scores.PlayerGame
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection
import net.zodac.dicefive.model.TurnTimer

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

    // As the real queries do, only Leaderboard rows (ScoreEntry.onLeaderboard) are paged, counted,
    // best or collected; career points count every row.
    private val leaderboard get() = entries.filter { it.onLeaderboard }

    override suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry> =
        leaderboard.sortedByDescending { it.score }.drop(offset).take(limit)

    private fun forMode(gameModeId: String, includeOffBoard: Boolean) =
        entries.filter { (it.onLeaderboard || includeOffBoard) && it.gameModeId == gameModeId }

    override suspend fun pagedScoresForMode(gameModeId: String, includeOffBoard: Boolean, limit: Int, offset: Int): List<ScoreEntry> =
        forMode(gameModeId, includeOffBoard).sortedByDescending { it.score }.drop(offset).take(limit)

    override suspend fun count(): Int = leaderboard.size

    override suspend fun countForMode(gameModeId: String, includeOffBoard: Boolean): Int = forMode(gameModeId, includeOffBoard).size

    override suspend fun bestScoreForPlayer(playerName: String): Int? =
        leaderboard.filter { it.playerName == playerName }.maxOfOrNull { it.score }

    override suspend fun distinctScores(): List<Int> = leaderboard.map { it.score }.distinct()

    override suspend fun primaryPlayerTotalPoints(): Int? =
        entries.filter { it.isPrimaryPlayer }.takeIf { it.isNotEmpty() }?.sumOf { it.score }

    override suspend fun playerGames(): List<PlayerGame> = emptyList()

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

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A started game and the store its achievements are recorded in. */
    private class Table(val viewModel: GameViewModel, val store: FakeAchievementStore) {
        val unlocked: Set<Achievement> get() = store.unlocked
    }

    /** A game of [players] dealt [dice], set up by [setUp] and started, recording into a fresh store. */
    private fun started(
        dice: Random = Random.Default,
        players: Int = 1,
        isDebugBuild: Boolean = false,
        scoreRepository: ScoreRepository? = null,
        setUp: GameViewModel.() -> Unit = {},
    ): Table {
        val store = FakeAchievementStore()
        val viewModel = GameViewModel(
            aiDispatcher = testDispatcher,
            scoreRepository = scoreRepository,
            achievementsRepository = store,
            random = dice,
            isDebugBuild = isDebugBuild,
        )
        viewModel.setPlayerCount(players)
        viewModel.setUp()
        viewModel.startGame()
        return Table(viewModel, store)
    }

    /** What a game [started] with [dice] has unlocked once [play] is played and everything has settled. */
    private fun TestScope.unlockedAfter(
        dice: Random,
        players: Int = 1,
        setUp: GameViewModel.() -> Unit = {},
        play: GameViewModel.() -> Unit,
    ): Set<Achievement> {
        val table = started(dice, players, setUp = setUp)
        table.viewModel.play()
        advanceUntilIdle()
        return table.unlocked
    }

    /** Holds then releases every die, in order - the hidden sequence that turns superuser mode on. */
    private fun GameViewModel.holdSequence() = repeat(5) { die ->
        toggleHold(die)
        toggleHold(die)
    }

    /** Plays every turn of every player to the end: roll once, then take the first open category. */
    private fun GameViewModel.playToCompletion() {
        val mode = game.value?.gameMode ?: GameMode.STANDARD
        repeat(mode.turnsPerGame * (game.value?.players?.size ?: 1)) {
            val state = game.value ?: return
            if (state.isGameOver) return
            rollDice()
            val player = state.currentPlayer ?: return
            val open = mode.categories.first { player.isOpen(it) }
            commitScore(open)
        }
    }

    // ---- A finished game -------------------------------------------------------------------------

    @Test
    fun `finishing a game records its achievements and counters - solo - between two humans or against an AI`() = runTest {
        // With a real score repository in play, finishGame actually suspends on the leaderboard read before it ever
        // reaches the achievement evaluation - which a null repository never does.
        val dao = FakeScoreDao()
        val solo = started(scoreRepository = ScoreRepository(dao))
        solo.viewModel.playToCompletion()
        advanceUntilIdle()
        assertEquals(1, dao.count(), "the human's score should be on the leaderboard")
        assertTrue(Achievement.SOLO_GAME in solo.unlocked, "SOLO_GAME should unlock, got ${solo.unlocked}")
        // One roll of all five dice per turn, one turn per category.
        assertEquals(GameMode.STANDARD.categories.size * 5, solo.store.state.first().counter(AchievementCounter.DICE_ROLLED))
        // A solo game is never "lost" - nobody to lose to - so replaying it must not count as One More Time.
        solo.viewModel.startGame()
        advanceUntilIdle()
        assertFalse(Achievement.REPLAY_AFTER_LOSS in solo.unlocked)

        // Every die comes up 6, so both humans fill identical scorecards in the same order - a tie, which counts as a
        // win for player 1 (the only player whose achievements count - see AchievementEngine's player-1-only rule).
        val twoHumans = started(LoadedDice(6), players = 2)
        twoHumans.viewModel.playToCompletion()
        advanceUntilIdle()
        assertTrue(twoHumans.viewModel.game.value?.isGameOver == true, "game should be over")
        assertTrue(Achievement.FIRST_WIN in twoHumans.unlocked, "FIRST_WIN should unlock, got ${twoHumans.unlocked}")
        assertEquals(1, twoHumans.store.state.first().counter(AchievementCounter.GAMES_PLAYED))

        // Both seats HUMAN - a local pass-and-play game. If player 2's rolls counted too, Well Rolled would be double.
        val passAndPlay = started(players = 2)
        passAndPlay.viewModel.playToCompletion()
        advanceUntilIdle()
        assertEquals(GameMode.STANDARD.categories.size * 5, passAndPlay.store.state.first().counter(AchievementCounter.DICE_ROLLED))

        // A game finished by an AI still records the human's achievements. The AI plays itself via a coroutine, so its
        // turns interleave with advanceUntilIdle.
        val againstBot = started(players = 2) { setPlayerType(2, PlayerType.AI) }
        repeat(GameMode.STANDARD.categories.size * 2) {
            advanceUntilIdle()
            val state = againstBot.viewModel.game.value ?: return@repeat
            if (state.isGameOver || state.currentPlayer?.type != PlayerType.HUMAN) return@repeat
            againstBot.viewModel.rollDice()
            val player = state.currentPlayer ?: return@repeat
            againstBot.viewModel.commitScore(GameMode.STANDARD.categories.first { player.isOpen(it) })
        }
        advanceUntilIdle()
        assertTrue(againstBot.viewModel.game.value?.isGameOver == true, "game should be over")
        assertEquals(1, againstBot.store.state.first().counter(AchievementCounter.GAMES_PLAYED))
    }

    /**
     * The real "picked a non-default style" -> unlock path needs [net.zodac.dicefive.data.settings.SettingsRepository],
     * which - unlike [AchievementStore]/[ScoreDao] - is a concrete DataStore-backed class with no fake to substitute
     * here (see `.claude/DESIGN.md`'s note on the same gap for CONTINUED_GAME). What IS covered on a plain JVM: starting
     * a game with no settings repository at all doesn't unlock a style achievement out of nowhere, and doesn't crash.
     * `AchievementEngineTest` covers the actual "non-default -> unlocked" decision, in `AchievementEngine.evaluateAtGameStart`.
     */
    @Test
    fun `starting a game with no settings repository does not unlock a style achievement`() = runTest {
        val table = started()
        advanceUntilIdle()

        assertFalse(Achievement.FRESH_COAT_OF_PAINT in table.unlocked)
    }

    @Test
    fun `each recorded score carries its outcome - 5x count - primary-player flag and whether it's on the Leaderboard`() = runTest {
        fun finished(players: Int = 1, dice: Random = Random.Default, mode: GameMode = GameMode.STANDARD): Pair<GameViewModel, FakeScoreDao> {
            val dao = FakeScoreDao()
            val table = started(dice, players, scoreRepository = ScoreRepository(dao)) { setGameMode(mode) }
            table.viewModel.playToCompletion()
            advanceUntilIdle()
            return table.viewModel to dao
        }

        // A solo game records no win outcome - there's nobody to beat - and goes on the Leaderboard.
        val (_, solo) = finished()
        assertEquals(null, solo.recorded().single().won)
        assertTrue(solo.recorded().single().onLeaderboard)

        // A two-human game records each player's win outcome against the top score - and only player 1's row is
        // flagged as the primary player.
        val (twoHumans, twoHumansDao) = finished(players = 2)
        val finalState = twoHumans.game.value!!
        assertEquals(finalState.players.associate { it.name to (it.totalScore == finalState.topScore) }, twoHumansDao.recorded().associate { it.playerName to it.won })
        val flagged = twoHumansDao.recorded().associate { it.playerName to it.isPrimaryPlayer }
        assertTrue(flagged.getValue(finalState.players[0].name), "player 1's own row should be flagged")
        assertEquals(1, flagged.values.count { it }, "no other row should be flagged as the primary player")

        // Every roll is five 6s, so the game scores its 5x box and then bonus chips.
        val (sixes, sixesDao) = finished(dice = LoadedDice(6))
        val player = sixes.game.value!!.players.single()
        assertTrue(player.fiveOfAKindCount > 0, "a game of nothing but 6s should score at least one 5x")
        assertEquals(player.fiveOfAKindCount, sixesDao.recorded().single().fiveOfAKindCount)

        // A Third Wind game is recorded for Statistics but kept off the Leaderboard.
        val (thirdWind, thirdWindDao) = finished(mode = GameMode.THIRD_WIND)
        assertTrue(thirdWind.game.value?.isGameOver == true, "all 39 turns should finish the game")
        val row = thirdWindDao.recorded().single()
        val repository = ScoreRepository(thirdWindDao)
        assertFalse(row.onLeaderboard)
        assertEquals(thirdWind.game.value!!.players.single().totalScore, row.score)
        assertEquals(0, repository.totalCount(), "the Leaderboard shows no row for it")
        assertEquals(null, repository.bestScoreForPlayer(row.playerName))
        assertEquals(emptySet(), repository.distinctScores())
    }

    // ---- What a roll or a score earns, as it happens ----------------------------------------------

    @Test
    fun `a maxed box pops mid-game - long before the results screen - superuser mode or not`() = runTest {
        // Every die comes up 6, so the first roll is five 6s: 30 in Sixes, the maximum.
        val table = started(LoadedDice(6))
        table.viewModel.rollDice()
        table.viewModel.commitScore(ScoreCategory.SIXES)
        advanceUntilIdle()
        assertTrue(table.viewModel.game.value?.isGameOver == false, "game should still be running")
        assertTrue(Achievement.SIXES_30 in table.unlocked, "SIXES_30 should pop mid-game, got ${table.unlocked}")

        // Superuser mode no longer disqualifies a game - the cheat has to stay debuggable. It only exists in debug builds.
        val cheat = started(LoadedDice(6), isDebugBuild = true)
        cheat.viewModel.rollDice()
        cheat.viewModel.holdSequence()
        assertTrue(cheat.viewModel.superuserModeActive.value, "superuser mode should be active")
        cheat.viewModel.commitScore(ScoreCategory.SIXES)
        advanceUntilIdle()
        assertTrue(Achievement.SIXES_30 in cheat.unlocked, "SIXES_30 should still unlock, got ${cheat.unlocked}")
    }

    @Test
    fun `a feat rolled straight out of the cup pops once the dice land - and only while its box is still open`() = runTest {
        // 1-2-3-4-5 on the first throw: a large straight out of the cup - and I Can Count.
        val straight = unlockedAfter(CountingDice(1..5)) { rollDice() }
        assertTrue(Achievement.FIRST_ROLL_LARGE_STRAIGHT in straight, "FIRST_ROLL_LARGE_STRAIGHT should pop on the roll, got $straight")
        assertTrue(Achievement.I_CAN_COUNT in straight, "I_CAN_COUNT should pop, got $straight")
        assertFalse(Achievement.FIRST_ROLL_5X in straight)
        assertFalse(Achievement.FIRST_ROLL_FULL_HOUSE in straight)

        // It waits for the dice to land before it pops.
        val tossing = started(CountingDice(1..5))
        tossing.viewModel.diceTossMillis = 1_000L
        tossing.viewModel.rollDice()
        advanceTimeBy(999L)
        runCurrent()
        assertFalse(Achievement.FIRST_ROLL_LARGE_STRAIGHT in tossing.unlocked, "popped while the dice were still tossing: ${tossing.unlocked}")
        advanceTimeBy(2L)
        runCurrent()
        assertTrue(Achievement.FIRST_ROLL_LARGE_STRAIGHT in tossing.unlocked, "should pop once the dice have landed: ${tossing.unlocked}")

        // The menu logo's exact dice are Product Placement.
        assertTrue(Achievement.PRODUCT_PLACEMENT in unlockedAfter(ScriptedDice(listOf(2, 4, 5, 3, 6))) { rollDice() })

        // Turn 1 zeroes the box with no such hand at all; turn 2 rolls a genuine one, but the box is no longer a real option.
        // A pair with no triple, then three 6s and two 5s: no House Call.
        val houseCall = unlockedAfter(ScriptedDice(listOf(1, 1, 2, 3, 4, 6, 6, 6, 5, 5))) {
            rollDice()
            commitScore(ScoreCategory.FULL_HOUSE)
            rollDice()
        }
        assertFalse(Achievement.FIRST_ROLL_FULL_HOUSE in houseCall, "FIRST_ROLL_FULL_HOUSE should not pop once Full House is zeroed, got $houseCall")
        // Five 6s, then 1-2-3-4-5: no Straight Away.
        val straightAway = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 6, 1, 2, 3, 4, 5))) {
            rollDice()
            commitScore(ScoreCategory.LARGE_STRAIGHT)
            rollDice()
        }
        assertFalse(Achievement.FIRST_ROLL_LARGE_STRAIGHT in straightAway, "FIRST_ROLL_LARGE_STRAIGHT should not pop once Large Straight is zeroed, got $straightAway")
        // No 5x, then five 6s on both rolls - a 5x on the first roll and (nothing held) the second - and the box hasn't
        // shown a genuine 50 for the joker bonus to kick in either.
        val fiveX = unlockedAfter(ScriptedDice(listOf(1, 2, 3, 4, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6))) {
            rollDice()
            commitScore(ScoreCategory.FIVE_OF_A_KIND)
            rollDice()
            rollDice()
        }
        assertFalse(Achievement.FIRST_ROLL_5X in fiveX, "FIRST_ROLL_5X should not pop once Five Of A Kind is zeroed, got $fiveX")
        assertFalse(Achievement.NATURAL_5X in fiveX, "NATURAL_5X should not pop once Five Of A Kind is zeroed, got $fiveX")
    }

    @Test
    fun `Empty House and Fuller House need a genuine three-and-two of their values - in any order - never a joker`() = runTest {
        fun fullHouse(vararg dice: Int) = unlockedAfter(ScriptedDice(dice.toList())) {
            rollDice()
            commitScore(ScoreCategory.FULL_HOUSE)
        }
        // A 5x used as a joker to fill Full House: turn 1 closes the matching upper box at 0 so the joker rule can't force
        // it later, turn 2 banks the 5x box, and turn 3's same-shaped roll fills Full House - not a genuine three-and-two split.
        fun joker(value: Int, closed: ScoreCategory, opening: Int) = unlockedAfter(ScriptedDice(List(5) { opening } + List(10) { value })) {
            rollDice()
            commitScore(closed)
            rollDice()
            commitScore(ScoreCategory.FIVE_OF_A_KIND)
            rollDice()
            commitScore(ScoreCategory.FULL_HOUSE)
        }

        assertTrue(Achievement.EMPTY_HOUSE in fullHouse(1, 1, 1, 2, 2), "EMPTY_HOUSE should pop")
        assertTrue(Achievement.EMPTY_HOUSE in fullHouse(1, 2, 1, 2, 1), "EMPTY_HOUSE should pop regardless of dice order")
        assertTrue(Achievement.FULLER_HOUSE in fullHouse(6, 6, 6, 5, 5), "FULLER_HOUSE should pop")
        assertTrue(Achievement.FULLER_HOUSE in fullHouse(6, 5, 6, 5, 6), "FULLER_HOUSE should pop regardless of dice order")
        val otherValues = fullHouse(3, 3, 3, 2, 2)
        assertFalse(Achievement.EMPTY_HOUSE in otherValues)
        assertFalse(Achievement.FULLER_HOUSE in otherValues)
        assertFalse(Achievement.EMPTY_HOUSE in joker(1, closed = ScoreCategory.ONES, opening = 6), "a joker-rule full house should not unlock Empty House")
        assertFalse(Achievement.FULLER_HOUSE in joker(6, closed = ScoreCategory.SIXES, opening = 1), "a joker-rule full house should not unlock Fuller House")
    }

    @Test
    fun `a 5x wasted as a zero is Wasted Fortune for player 1 - and two in a row or one rerolled into is its own feat`() = runTest {
        // Every die comes up 6: a genuine 5x, but committed to Ones - no die shows a 1, so it scores 0.
        val wasted = unlockedAfter(LoadedDice(6)) {
            rollDice()
            commitScore(ScoreCategory.ONES)
        }
        assertTrue(Achievement.WASTED_5X in wasted, "WASTED_5X should pop on the commit itself, got $wasted")
        val kept = unlockedAfter(LoadedDice(6)) {
            rollDice()
            commitScore(ScoreCategory.FIVE_OF_A_KIND)
        }
        assertFalse(Achievement.WASTED_5X in kept, "scoring 50 in the 5x box is not a waste, got $kept")
        // Player 1 commits the 5x itself; player 2 wastes theirs on Ones - which would count were it player 1's turn.
        val playerTwoWasted = unlockedAfter(LoadedDice(6), players = 2) {
            rollDice()
            commitScore(ScoreCategory.FIVE_OF_A_KIND)
            rollDice()
            commitScore(ScoreCategory.ONES)
        }
        assertFalse(Achievement.WASTED_5X in playerTwoWasted, "wasting a 5x on another human's turn must not unlock an achievement, got $playerTwoWasted")

        // A 5x on two turns in a row is Twice in a Lifetime - the second forced by the joker rule (Twos is the matching
        // upper box), still a genuine bonus 5x.
        val twice = unlockedAfter(LoadedDice(2)) {
            rollDice()
            commitScore(ScoreCategory.FIVE_OF_A_KIND)
            rollDice()
            commitScore(ScoreCategory.TWOS)
        }
        assertTrue(Achievement.TWICE_IN_A_LIFETIME in twice, "TWICE_IN_A_LIFETIME should pop, got $twice")

        // A 5x on the 2nd roll without holding anything is Natural 5x.
        val natural = unlockedAfter(LoadedDice(6)) {
            rollDice()
            rollDice()
        }
        assertTrue(Achievement.NATURAL_5X in natural, "NATURAL_5X should pop, got $natural")
    }

    // ---- Holds, rolls and commits watched as they happen ----------------------------------------

    @Test
    fun `the hidden hold sequence is Time Wasting for player 1 alone - and the Top Hat rabbit is The Magicians Secret`() = runTest {
        val timeWasting = unlockedAfter(LoadedDice(6)) {
            rollDice()
            holdSequence()
        }
        assertTrue(Achievement.TIME_WASTING in timeWasting, "TIME_WASTING should pop, got $timeWasting")

        // The cheat itself stays "any player's turn" - only the achievement is player 1's alone. Player 1 passes without
        // touching the sequence; player 2 performs it.
        val playerTwo = started(LoadedDice(6), players = 2, isDebugBuild = true)
        playerTwo.viewModel.rollDice()
        playerTwo.viewModel.commitScore(ScoreCategory.ONES)
        playerTwo.viewModel.rollDice()
        playerTwo.viewModel.holdSequence()
        advanceUntilIdle()
        assertTrue(playerTwo.viewModel.superuserModeActive.value, "the cheat itself still works on any seat's turn")
        assertFalse(Achievement.TIME_WASTING in playerTwo.unlocked, "TIME_WASTING is player 1's alone, got ${playerTwo.unlocked}")

        val rabbit = unlockedAfter(LoadedDice(3)) {
            rollDice()
            onRabbitSeen()
        }
        assertTrue(Achievement.MAGICIANS_SECRET in rabbit, "MAGICIANS_SECRET should pop, got $rabbit")
    }

    @Test
    fun `the same result again is Deja Vu with nothing held - and Are These Loaded Dice with some held`() = runTest {
        val dejaVu = unlockedAfter(ScriptedDice(listOf(3, 1, 4, 1, 5))) {
            rollDice()
            rollDice()
        }
        assertTrue(Achievement.DEJA_VU in dejaVu, "DEJA_VU should pop, got $dejaVu")

        val heldBetween = unlockedAfter(ScriptedDice(listOf(3, 1, 4, 1, 5))) {
            rollDice()
            toggleHold(0)
            toggleHold(0)
            rollDice()
        }
        assertFalse(Achievement.DEJA_VU in heldBetween, "a hold in between should disqualify it, got $heldBetween")

        // Roll 1: [1,2,2,2,3]; hold die 0 (value 1) and leave it held. Roll 2 lands [1,4,4,4,4]. Roll 3 re-rolls the same
        // four unheld dice back to [4,4,4,4] - the FULL five-dice result matches roll 2's exactly, and nothing was toggled
        // in between, but die 0 is still held - Are These Loaded Dice?'s territory (a partial hold), not Deja Vu's.
        val stillHeld = unlockedAfter(ScriptedDice(listOf(1, 2, 2, 2, 3, 4, 4, 4, 4, 4, 4, 4, 4))) {
            rollDice()
            toggleHold(0)
            rollDice()
            rollDice()
        }
        assertFalse(Achievement.DEJA_VU in stillHeld, "a die still held right now must disqualify Deja Vu, even if the hold itself is unchanged, got $stillHeld")

        // Roll 1: [1,2,2,2,3]; hold the three 2s; rolls 2 and 3 land the same 1 and 3 in the gaps.
        val loaded = unlockedAfter(ScriptedDice(listOf(1, 2, 2, 2, 3, 1, 3, 1, 3))) {
            rollDice()
            toggleHold(1)
            toggleHold(2)
            toggleHold(3)
            rollDice()
            rollDice()
        }
        assertTrue(Achievement.LOADED_DICE in loaded, "LOADED_DICE should pop, got $loaded")
    }

    @Test
    fun `switching one held group of matching dice for another and scoring it is Commitment Issues - a 5x is no group`() = runTest {
        // Holding exactly the two 6s, releasing them, then holding exactly the two 3s.
        val pairs = unlockedAfter(ScriptedDice(listOf(6, 6, 1, 3, 3))) {
            rollDice()
            toggleHold(0)
            toggleHold(1)
            toggleHold(0)
            toggleHold(1)
            toggleHold(3)
            toggleHold(4)
            commitScore(ScoreCategory.THREES)
        }
        assertTrue(Achievement.COMMITMENT_ISSUES in pairs, "COMMITMENT_ISSUES should pop, got $pairs")

        // A single die counts as a group too.
        val single = unlockedAfter(ScriptedDice(listOf(6, 1, 2, 3, 4))) {
            rollDice()
            toggleHold(0)
            toggleHold(0)
            toggleHold(1)
            commitScore(ScoreCategory.ONES)
        }
        assertTrue(Achievement.COMMITMENT_ISSUES in single, "a single die counts as a group too, got $single")

        // So does a four of a kind.
        val four = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 3))) {
            rollDice()
            repeat(4) { toggleHold(it) }
            repeat(4) { toggleHold(it) }
            toggleHold(4)
            commitScore(ScoreCategory.THREES)
        }
        assertTrue(Achievement.COMMITMENT_ISSUES in four, "a four of a kind counts as a group too, got $four")

        // Five 6s held and released (however far it shrinks back down first) is a 5x, not indecision - it must not become
        // the baseline the later group of 3s is compared against.
        val fiveX = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 6) + listOf(3, 3, 3, 3, 3))) {
            rollDice()
            repeat(5) { toggleHold(it) }
            repeat(5) { toggleHold(it) }
            rollDice()
            repeat(4) { toggleHold(it) }
            commitScore(ScoreCategory.THREES)
        }
        assertFalse(Achievement.COMMITMENT_ISSUES in fiveX, "the only thing ever released was a 5x, so there's no baseline to differ from")
    }

    @Test
    fun `holding and releasing the same die three times between two rolls is Decisions Decisions - a roll or a turn resets it`() = runTest {
        val three = unlockedAfter(LoadedDice(4)) {
            rollDice()
            repeat(3) {
                toggleHold(0)
                toggleHold(0)
            }
        }
        assertTrue(Achievement.DECISIONS_DECISIONS in three, "DECISIONS_DECISIONS should pop, got $three")

        // Two cycles, then a roll (or, for the same solo player, a whole new turn), then one more: three in all, but
        // "before rolling again" means what came before is wiped rather than carried over.
        val aRoll: GameViewModel.() -> Unit = { rollDice() }
        val aTurn: GameViewModel.() -> Unit = {
            commitScore(ScoreCategory.FOURS)
            rollDice()
        }
        for ((between, name) in listOf(aRoll to "a roll", aTurn to "turns")) {
            val split = unlockedAfter(LoadedDice(4)) {
                rollDice()
                repeat(2) {
                    toggleHold(0)
                    toggleHold(0)
                }
                between()
                toggleHold(0)
                toggleHold(0)
            }
            assertFalse(Achievement.DECISIONS_DECISIONS in split, "DECISIONS_DECISIONS should not pop when cycles are split across $name, got $split")
        }
    }

    @Test
    fun `a die held through both re-rolls then left out of the box scored is Time to Let It Go`() = runTest {
        // Die 0 rolls a 6 and is held from right after roll 1, through both re-rolls. The other four dice roll 6 initially
        // too, then settle on 1s by the final roll, so Ones scores a real 4 - die 0's 6 is never counted towards it, but
        // the category didn't just fail.
        fun heldThrough(dice: Random, released: Boolean = false, releasedBeforeLastRoll: Boolean = false, box: ScoreCategory = ScoreCategory.ONES) =
            Achievement.TIME_TO_LET_IT_GO in unlockedAfter(dice) {
                rollDice()
                toggleHold(0)
                rollDice()
                if (releasedBeforeLastRoll) toggleHold(0)
                rollDice()
                if (released) toggleHold(0)
                commitScore(box)
            }
        fun sixesThenOnes() = ScriptedDice(listOf(6, 6, 6, 6, 6, 1, 1, 1, 1, 1, 1, 1, 1))

        assertTrue(heldThrough(sixesThenOnes()), "TIME_TO_LET_IT_GO should pop")
        // Let go before committing - only whether it was used matters, not its held state right now.
        assertTrue(heldThrough(sixesThenOnes(), released = true), "TIME_TO_LET_IT_GO should pop regardless of the die's final held state")
        // Every die always a 6: Ones scores 0 outright. That's the category failing, not the player choosing to let the
        // held die go.
        assertFalse(heldThrough(LoadedDice(6)), "a zero score must not unlock TIME_TO_LET_IT_GO")
        // Sixes counts the held die's own face - it WAS used.
        assertFalse(heldThrough(LoadedDice(6), box = ScoreCategory.SIXES), "scoring the category the held die counts towards must not unlock TIME_TO_LET_IT_GO")
        // Released before the 3rd roll - held through only one re-roll, not both.
        assertFalse(heldThrough(LoadedDice(6), releasedBeforeLastRoll = true), "a die held through only one re-roll must not unlock TIME_TO_LET_IT_GO")
    }

    @Test
    fun `all five held then rolled is What Was the Point - all five released is A Cunning Strategy - and three taps of a spent cup No More Rolls`() = runTest {
        val pointless = unlockedAfter(LoadedDice(4)) {
            rollDice()
            repeat(5) { toggleHold(it) }
            rollDice()
        }
        assertTrue(Achievement.POINTLESS_ROLL in pointless, "POINTLESS_ROLL should pop, got $pointless")

        val cunning = unlockedAfter(LoadedDice(4)) {
            rollDice()
            repeat(5) { toggleHold(it) }
            repeat(5) { toggleHold(it) }
        }
        assertTrue(Achievement.CUNNING_STRATEGY in cunning, "CUNNING_STRATEGY should pop, got $cunning")

        val noMoreRolls = unlockedAfter(LoadedDice(3)) {
            repeat(3) { rollDice() }
            repeat(3) { tapCupWithNoRollsLeft() }
        }
        assertTrue(Achievement.NO_MORE_ROLLS in noMoreRolls, "NO_MORE_ROLLS should pop, got $noMoreRolls")
    }

    /** Plays a solo game to its last turn, rolling [rollsOnTurn] times on each (by index) then taking the first open box. */
    private fun GameViewModel.playEveryTurnButTheLast(rollsOnTurn: (Int) -> Int) {
        repeat(GameMode.STANDARD.categories.size - 1) { turn ->
            repeat(rollsOnTurn(turn)) { rollDice() }
            val player = game.value?.currentPlayer ?: return
            commitScore(GameMode.STANDARD.categories.first { player.isOpen(it) })
        }
    }

    @Test
    fun `using every roll of every turn unlocks Greenfingers on the game's very last roll - one turn scored early keeps it locked`() = runTest {
        val table = started(LoadedDice(3))
        table.viewModel.playEveryTurnButTheLast { 3 }
        repeat(2) { table.viewModel.rollDice() }
        advanceUntilIdle()
        assertFalse(Achievement.GREENFINGERS in table.unlocked, "GREENFINGERS should wait for the last roll, got ${table.unlocked}")
        table.viewModel.rollDice()
        advanceUntilIdle()
        assertEquals(false, table.viewModel.game.value?.isGameOver, "the last box is still to fill")
        assertTrue(Achievement.GREENFINGERS in table.unlocked, "GREENFINGERS should pop on the 39th roll, got ${table.unlocked}")

        val early = started(LoadedDice(3))
        early.viewModel.playEveryTurnButTheLast { turn -> if (turn == 5) 2 else 3 }
        repeat(3) { early.viewModel.rollDice() }
        early.viewModel.commitScore(GameMode.STANDARD.categories.first { early.viewModel.game.value?.currentPlayer?.isOpen(it) != false })
        advanceUntilIdle()
        assertEquals(true, early.viewModel.game.value?.isGameOver)
        assertFalse(Achievement.GREENFINGERS in early.unlocked, "GREENFINGERS needs every roll, got ${early.unlocked}")
    }

    @Test
    fun `a first-roll four of a kind held through every roll with no 5x is Almost Famous - only that and only that turn`() = runTest {
        // Roll 1: four 6s (indices 0-3) and a lone 1 (index 4). The four 6s are held from here on, so only the loose die
        // is re-rolled for rolls 2 and 3 - landing on 2, then 3, never a matching 6.
        val famous = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 1, 2, 3))) {
            rollDice()
            repeat(4) { toggleHold(it) }
            rollDice()
            rollDice()
            commitScore(ScoreCategory.SIXES)
        }
        assertTrue(Achievement.ALMOST_FAMOUS in famous, "ALMOST_FAMOUS should pop, got $famous")

        // The four 6s held for roll 2 only: all five dice are back in play for the last roll, so the loose die was never
        // actually the only thing still being chased.
        val broken = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 1, 2, 1, 2, 3, 4))) {
            rollDice()
            repeat(4) { toggleHold(it) }
            rollDice()
            repeat(4) { toggleHold(it) }
            rollDice()
            commitScore(ScoreCategory.CHANCE)
        }
        assertFalse(Achievement.ALMOST_FAMOUS in broken, "ALMOST_FAMOUS should not pop once the held four of a kind was broken up, got $broken")

        // Committed straight away: the player never got a real chance (2nd/3rd roll) to try turning it into a 5x.
        val oneRoll = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 1))) {
            rollDice()
            commitScore(ScoreCategory.SIXES)
        }
        assertFalse(Achievement.ALMOST_FAMOUS in oneRoll, "ALMOST_FAMOUS should not pop after only one roll, got $oneRoll")

        // With one roll a turn, that roll is also the last, so "every roll spent" is true straight away and nothing was ever
        // rerolled to break the hold - the case the one-roll guard is for.
        val oneRollATurn = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 1)), setUp = { setRollsPerTurn(1) }) {
            rollDice()
            commitScore(ScoreCategory.SIXES)
        }
        assertFalse(Achievement.ALMOST_FAMOUS in oneRollATurn, "ALMOST_FAMOUS should not pop with one roll a turn, got $oneRollATurn")

        // Turn 1's four 6s, committed at once, must not leave a flag that survives into turn 2 - no four of a kind at all,
        // but all three rolls used and committed.
        val leaked = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 1) + List(3) { listOf(1, 2, 3, 4, 6) }.flatten())) {
            rollDice()
            commitScore(ScoreCategory.SIXES)
            repeat(3) { rollDice() }
            commitScore(ScoreCategory.CHANCE)
        }
        assertFalse(Achievement.ALMOST_FAMOUS in leaked, "ALMOST_FAMOUS should not leak a stale four-of-a-kind flag into the next turn, got $leaked")

        // Four 6s and three others in Stud: the 6s held, the other three rerolled twice and never a 6.
        val stud = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 1, 2, 3, 2, 3, 4, 3, 4, 5)), setUp = { setGameMode(GameMode.STUD) }) {
            rollDice()
            for (index in 0 until 4) toggleHold(index)
            rollDice()
            rollDice()
            toggleHold(6)
            commitScore(ScoreCategory.SIXES)
        }
        assertFalse(Achievement.ALMOST_FAMOUS in stud, "ALMOST_FAMOUS should not pop in Stud, got $stud")
    }

    @Test
    fun `Stud - seven matching dice are Lucky Seven but no 5x feat and a timed-out turn completes and scores only the held hand`() = runTest {
        val stud: GameViewModel.() -> Unit = { setGameMode(GameMode.STUD) }
        val firstRoll = unlockedAfter(LoadedDice(6), setUp = stud) { rollDice() }
        assertTrue(Achievement.STUD_LUCKY_SEVEN in firstRoll, "STUD_LUCKY_SEVEN should pop, got $firstRoll")
        assertFalse(Achievement.FIRST_ROLL_5X in firstRoll, "a Stud roll isn't a hand: $firstRoll")
        val rerolled = unlockedAfter(LoadedDice(6), setUp = stud) {
            rollDice()
            rollDice()
        }
        assertFalse(Achievement.NATURAL_5X in rerolled, "NATURAL_5X should not pop in Stud, got $rerolled")

        // 6, 1, 6, 2, 6, 3, 1: the player holds the first 1 (die 2), then lets the timer run out. The turn goes in Ones, the
        // hand completed with the last 1 and then the leftmost dice (the first, third and fourth) - so the third 6 and the
        // 3 are left on the mat.
        val timedOut = started(ScriptedDice(listOf(6, 1, 6, 2, 6, 3, 1))) {
            setGameMode(GameMode.STUD)
            setTurnTimer(TurnTimer.SECONDS_30)
        }
        timedOut.viewModel.rollDice()
        timedOut.viewModel.toggleHold(1)
        testDispatcher.scheduler.advanceTimeBy(30_000)
        testDispatcher.scheduler.runCurrent()
        val player = timedOut.viewModel.game.value!!.players.single()
        assertEquals(2, player.scoresIn(ScoreCategory.ONES).singleOrNull())
        assertEquals(listOf(true, true, true, true, false, false, true), player.lastRoll!!.map { it.isHeld })
    }

    @Test
    fun `letting every turn time out and still winning unlocks Luck Of The Draw`() = runTest {
        // Every die a 6. Player 1 never touches their turn: the timer scores each one in the first open box, and their last
        // is a repeat 5x - Chance plus the 100 bonus. Player 2 scores every turn by hand, the 5x box last so they never earn
        // a bonus - so player 1 wins by 100 or more.
        val table = started(LoadedDice(6), players = 2) { setTurnTimer(TurnTimer.SECONDS_30) }
        val playerTwoOrder = listOf(
            ScoreCategory.ONES, ScoreCategory.TWOS, ScoreCategory.THREES, ScoreCategory.FOURS,
            ScoreCategory.FIVES, ScoreCategory.FULL_HOUSE, ScoreCategory.SMALL_STRAIGHT,
            ScoreCategory.LARGE_STRAIGHT, ScoreCategory.SIXES, ScoreCategory.THREE_OF_A_KIND,
            ScoreCategory.FOUR_OF_A_KIND, ScoreCategory.CHANCE, ScoreCategory.FIVE_OF_A_KIND,
        )
        for (category in playerTwoOrder) {
            testDispatcher.scheduler.advanceTimeBy(30_000)
            testDispatcher.scheduler.runCurrent()
            table.viewModel.rollDice()
            table.viewModel.commitScore(category)
        }
        advanceUntilIdle()

        val state = table.viewModel.game.value!!
        assertTrue(state.isGameOver)
        assertTrue(state.players[0].totalScore > state.players[1].totalScore, "player 1 should win: ${state.players.map { it.totalScore }}")
        assertTrue(Achievement.LUCK_OF_THE_DRAW in table.unlocked, "LUCK_OF_THE_DRAW should pop, got ${table.unlocked}")
    }

    @Test
    fun `odd choices - the small straight over the large is Why Did You Do That - an undo into another box I Didn't Mean That`() = runTest {
        val small = unlockedAfter(CountingDice(1..5)) {
            rollDice()
            commitScore(ScoreCategory.SMALL_STRAIGHT)
        }
        assertTrue(Achievement.WHY_DID_YOU_DO_THAT in small, "WHY_DID_YOU_DO_THAT should pop, got $small")

        fun undoneInto(box: ScoreCategory) = unlockedAfter(LoadedDice(3)) {
            rollDice()
            commitScore(ScoreCategory.THREES)
            undo()
            commitScore(box)
        }
        assertTrue(Achievement.UNDO_DIFFERENT_CATEGORY in undoneInto(ScoreCategory.CHANCE), "UNDO_DIFFERENT_CATEGORY should pop")
        assertFalse(Achievement.UNDO_DIFFERENT_CATEGORY in undoneInto(ScoreCategory.THREES))
    }

    /**
     * A solo game taken to its last turn with Sixes the only box open and the upper section on 60 - three short of the bonus,
     * so a single 6 would earn it. The last turn then rolls [finalRolls] (five dice a roll) and scores Sixes with them.
     */
    private fun TestScope.playToSixesNeedingOneDie(finalRolls: List<List<Int>>, rollsToTake: Int = finalRolls.size): Set<Achievement> {
        val upperTurns = listOf(
            ScoreCategory.ONES to listOf(1, 1, 1, 1, 2),
            ScoreCategory.TWOS to listOf(2, 2, 2, 2, 3),
            ScoreCategory.THREES to listOf(3, 3, 3, 3, 2),
            ScoreCategory.FOURS to listOf(4, 4, 4, 4, 2),
            ScoreCategory.FIVES to listOf(5, 5, 5, 5, 2),
        )
        val lowerBoxes = GameMode.STANDARD.categories.filter { it.section != ScoreSection.UPPER }
        val script = upperTurns.flatMap { it.second } + List(5 * lowerBoxes.size) { 2 } + finalRolls.flatten()
        return unlockedAfter(ScriptedDice(script)) {
            for (category in upperTurns.map { it.first } + lowerBoxes) {
                rollDice()
                commitScore(category)
            }
            repeat(rollsToTake) { rollDice() }
            commitScore(ScoreCategory.SIXES)
        }
    }

    @Test
    fun `bad luck - The Dice Hate Me and Probability Never Heard of Her`() = runTest {
        // 12 filler turns (every category but Ones) rolling all-2s, then a final turn on Ones: roll 2 shows a 1 (a real
        // option), roll 3 doesn't (no option left at all).
        val hate = unlockedAfter(ScriptedDice(List(65) { 2 } + listOf(1, 2, 2, 2, 2) + List(5) { 2 })) {
            for (category in GameMode.STANDARD.categories.filter { it != ScoreCategory.ONES }) {
                rollDice()
                commitScore(category)
            }
            repeat(3) { rollDice() }
        }
        assertTrue(Achievement.DICE_HATE_ME in hate, "DICE_HATE_ME should pop, got $hate")

        // A zero in the last box with one die short of the upper bonus after every roll - which needs every roll spent and
        // the last box scoring nothing.
        val unlucky = playToSixesNeedingOneDie(List(3) { List(5) { 2 } })
        assertTrue(Achievement.PROBABILITY_NEVER_HEARD_OF_HER in unlucky, "should pop, got $unlucky")
        val spare = playToSixesNeedingOneDie(List(3) { List(5) { 2 } }, rollsToTake = 2)
        assertFalse(Achievement.PROBABILITY_NEVER_HEARD_OF_HER in spare, "scored with a roll to spare, got $spare")
        val earned = playToSixesNeedingOneDie(listOf(List(5) { 2 }, List(5) { 2 }, listOf(6, 2, 2, 2, 2)))
        assertFalse(Achievement.PROBABILITY_NEVER_HEARD_OF_HER in earned, "the bonus was earned, got $earned")
    }
}
