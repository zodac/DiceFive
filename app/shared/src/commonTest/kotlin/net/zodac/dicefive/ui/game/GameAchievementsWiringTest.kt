package net.zodac.dicefive.ui.game

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.LoadedDice
import net.zodac.dicefive.ScriptedDice
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection
import net.zodac.dicefive.model.TurnTimer

/** More turns than any game here lasts, so a helper playing one out can't loop for ever. */
private const val MAX_TURNS_PLAYED = 200

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
    private class Table(val viewModel: GameViewModel, val store: InMemoryAchievementStore) {
        val unlocked: Set<Achievement> get() = store.unlocked
    }

    /** A game of [players] dealt [dice], set up by [setUp] and started, recording into a fresh store. */
    private fun started(
        dice: Random = Random(1),
        players: Int = 1,
        isDebugBuild: Boolean = false,
        scoreRepository: ScoreRepository = ScoreRepository(InMemoryScoreDao()),
        settingsRepository: SettingsRepository = SettingsRepository(InMemoryPreferences()),
        ownsAllStyles: Boolean = false,
        setUp: GameViewModel.() -> Unit = {},
    ): Table {
        val store = InMemoryAchievementStore()
        val viewModel = gameViewModel(
            settingsRepository = settingsRepository,
            ownsAllStyles = ownsAllStyles,
            aiDispatcher = testDispatcher,
            scoreRepository = scoreRepository,
            achievementsRepository = store,
            random = dice,
            isDebugBuild = isDebugBuild,
        )
        viewModel.setupForm.setPlayerCount(players)
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

    /** Plays every box of the game's own card (Extended Scores' too) to the end: roll once, then take the first open one. */
    private fun GameViewModel.playEveryBox() {
        repeat(MAX_TURNS_PLAYED) {
            val state = game.value ?: return
            if (state.isGameOver) return
            rollDice()
            val player = state.currentPlayer ?: return
            commitScore(state.gameMode.categoriesWith(state.extendedScores).first { player.isOpen(it) })
        }
    }

    @Test
    fun `a finished game with any modifier on is recorded as modified - off the board and its mode's card - and one with none as not`() = runTest {
        val modifiers: List<Pair<String, GameViewModel.() -> Unit>> = listOf(
            "none" to {},
            "turn timer" to { setupForm.setTurnTimer(TurnTimer.SECONDS_120) },
            "rolls per turn" to { setupForm.setRollsPerTurn(2) },
            "stored rolls" to { setupForm.setStoredRolls(true) },
            "extended scores" to { setupForm.setExtendedScores(true) },
            "unlucky dice" to { setupForm.setUnluckyDiceEnabled(true) },
        )
        for ((name, modifier) in modifiers) {
            val dao = InMemoryScoreDao()
            val table = started(scoreRepository = ScoreRepository(dao), setUp = modifier)
            table.viewModel.playEveryBox()
            advanceUntilIdle()
            val row = dao.rows().single()
            val unmodified = name == "none"
            assertEquals(unmodified, row.playedUnmodified, name)
            assertEquals(unmodified, row.onLeaderboard, name)
            assertEquals(if (unmodified) 1 else 0, ScoreRepository(dao).totalCountForMode(GameMode.STANDARD), name)
        }
    }

    @Test
    fun `a game with any modifier on earns no achievement - not even The Journey Begins - moves no counter and adds nothing to Statistics`() = runTest {
        val modifiers: List<Pair<String, GameViewModel.() -> Unit>> = listOf(
            "turn timer" to { setupForm.setTurnTimer(TurnTimer.SECONDS_120) },
            "rolls per turn" to { setupForm.setRollsPerTurn(2) },
            "stored rolls" to { setupForm.setStoredRolls(true) },
            "extended scores" to { setupForm.setExtendedScores(true) },
            "unlucky dice" to { setupForm.setUnluckyDiceEnabled(true) },
        )
        for ((name, modifier) in modifiers) {
            val dao = InMemoryScoreDao()
            // All 6s: a Natural 5x on the first roll, then the 5x joker into every other box. Unlucky Dice draws its odds
            // from the dice too, which loaded dice can't answer, so it plays seeded ones.
            val dice = if (name == "unlucky dice") Random(1) else LoadedDice(6)
            val table = started(dice = dice, scoreRepository = ScoreRepository(dao), setUp = modifier)
            table.viewModel.playEveryBox()
            advanceUntilIdle()
            assertEquals(emptySet(), table.unlocked, name)
            assertEquals(emptyMap(), table.store.state.first().counters.filterValues { it != 0 }, name)
            assertEquals(emptyList(), ScoreRepository(dao).playerStatistics(), name)
        }
        // The same game with none earns them.
        val unmodified = started(dice = LoadedDice(6))
        unmodified.viewModel.playEveryBox()
        advanceUntilIdle()
        for (achievement in listOf(Achievement.THE_JOURNEY_BEGINS, Achievement.PRACTICE_MAKES_PERFECT, Achievement.NATURAL_5X, Achievement.FIVE_X)) {
            assertTrue(achievement in unmodified.unlocked, "$achievement without a modifier, got ${unmodified.unlocked}")
        }
    }

    @Test
    fun `finishing a game records its achievements and counters - solo - between two humans or against an AI`() = runTest {
        // With a real score repository in play, finishGame actually suspends on the leaderboard read before it ever
        // reaches the achievement evaluation - which a null repository never does.
        val dao = InMemoryScoreDao()
        val solo = started(scoreRepository = ScoreRepository(dao))
        solo.viewModel.playToCompletion()
        advanceUntilIdle()
        assertEquals(1, dao.count(), "the human's score should be on the leaderboard")
        assertTrue(Achievement.PRACTICE_MAKES_PERFECT in solo.unlocked, "PRACTICE_MAKES_PERFECT should unlock, got ${solo.unlocked}")
        // One roll of all five dice per turn, one turn per category.
        assertEquals(GameMode.STANDARD.categories.size * 5, solo.store.state.first().counter(AchievementCounter.DICE_ROLLED))
        // A solo game is never "lost" - nobody to lose to - so replaying it must not count as One More Time.
        solo.viewModel.startGame()
        advanceUntilIdle()
        assertFalse(Achievement.ONE_MORE_TIME in solo.unlocked)

        // Every die comes up 6, so both humans fill identical scorecards in the same order - a tie, which counts as a
        // win for player 1 (the only player whose achievements count - see AchievementEngine's player-1-only rule).
        val twoHumans = started(LoadedDice(6), players = 2)
        twoHumans.viewModel.playToCompletion()
        advanceUntilIdle()
        assertTrue(twoHumans.viewModel.game.value?.isGameOver == true, "game should be over")
        assertTrue(Achievement.FIRST_VICTORY in twoHumans.unlocked, "FIRST_VICTORY should unlock, got ${twoHumans.unlocked}")
        assertEquals(1, twoHumans.store.state.first().counter(AchievementCounter.GAMES_PLAYED))

        // Both seats HUMAN - a local pass-and-play game. If player 2's rolls counted too, Well Rolled would be double.
        val passAndPlay = started(players = 2)
        passAndPlay.viewModel.playToCompletion()
        advanceUntilIdle()
        assertEquals(GameMode.STANDARD.categories.size * 5, passAndPlay.store.state.first().counter(AchievementCounter.DICE_ROLLED))

        // A game finished by an AI still records the human's achievements. The AI plays itself via a coroutine, so its
        // turns interleave with advanceUntilIdle.
        val againstBot = started(players = 2) { setupForm.setPlayerType(2, PlayerType.AI) }
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
     * here (see `.claude/DESIGN.md`'s note on the same gap for LETS_FINISH_THIS). What IS covered on a plain JVM: starting
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
        fun finished(players: Int = 1, dice: Random = Random(1), mode: GameMode = GameMode.STANDARD): Pair<GameViewModel, InMemoryScoreDao> {
            val dao = InMemoryScoreDao()
            val table = started(dice, players, scoreRepository = ScoreRepository(dao)) { setupForm.setGameMode(mode) }
            table.viewModel.playToCompletion()
            advanceUntilIdle()
            return table.viewModel to dao
        }

        // A solo game records no win outcome - there's nobody to beat - and goes on the Leaderboard.
        val (_, solo) = finished()
        assertEquals(null, solo.rows().single().won)
        assertTrue(solo.rows().single().onLeaderboard)

        // A two-human game records each player's win outcome against the top score - and only player 1's row is
        // flagged as the primary player.
        val (twoHumans, twoHumansDao) = finished(players = 2)
        val finalState = twoHumans.game.value!!
        assertEquals(finalState.players.associate { it.name to (it.totalScore == finalState.topScore) }, twoHumansDao.rows().associate { it.playerName to it.won })
        val flagged = twoHumansDao.rows().associate { it.playerName to it.isPrimaryPlayer }
        assertTrue(flagged.getValue(finalState.players[0].name), "player 1's own row should be flagged")
        assertEquals(1, flagged.values.count { it }, "no other row should be flagged as the primary player")

        // Every roll is five 6s, so the game scores its 5x box and then bonus chips.
        val (sixes, sixesDao) = finished(dice = LoadedDice(6))
        val player = sixes.game.value!!.players.single()
        assertTrue(player.fiveOfAKindCount > 0, "a game of nothing but 6s should score at least one 5x")
        assertEquals(player.fiveOfAKindCount, sixesDao.rows().single().fiveOfAKindCount)

        // A Third Wind game is recorded for Statistics but kept off the Leaderboard.
        val (thirdWind, thirdWindDao) = finished(mode = GameMode.THIRD_WIND)
        assertTrue(thirdWind.game.value?.isGameOver == true, "all 39 turns should finish the game")
        val row = thirdWindDao.rows().single()
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
        assertTrue(Achievement.SIX_APPEAL in table.unlocked, "SIX_APPEAL should pop mid-game, got ${table.unlocked}")

        // Superuser mode no longer disqualifies a game - the cheat has to stay debuggable. It only exists in debug builds.
        val cheat = started(LoadedDice(6), isDebugBuild = true)
        cheat.viewModel.rollDice()
        cheat.viewModel.holdSequence()
        assertTrue(cheat.viewModel.superuserModeActive.value, "superuser mode should be active")
        cheat.viewModel.commitScore(ScoreCategory.SIXES)
        advanceUntilIdle()
        assertTrue(Achievement.SIX_APPEAL in cheat.unlocked, "SIX_APPEAL should still unlock, got ${cheat.unlocked}")
    }

    @Test
    fun `a feat rolled straight out of the cup pops once the dice land - and only while its box is still open`() = runTest {
        // 1-2-3-4-5 on the first throw: a large straight out of the cup. Rolling it alone is no I Can Count.
        val straight = unlockedAfter(ScriptedDice((1..5).toList())) { rollDice() }
        assertTrue(Achievement.STRAIGHT_AWAY in straight, "STRAIGHT_AWAY should pop on the roll, got $straight")
        assertFalse(Achievement.I_CAN_COUNT in straight, "rolling 1-2-3-4-5 is not scoring it, got $straight")
        assertFalse(Achievement.HOUSE_CALL in straight)

        // It waits for the dice to land before it pops.
        val tossing = started(ScriptedDice((1..5).toList()))
        tossing.viewModel.setPacing(GamePacing(diceTossMillis = 1_000L))
        tossing.viewModel.rollDice()
        advanceTimeBy(999L)
        runCurrent()
        assertFalse(Achievement.STRAIGHT_AWAY in tossing.unlocked, "popped while the dice were still tossing: ${tossing.unlocked}")
        advanceTimeBy(2L)
        runCurrent()
        assertTrue(Achievement.STRAIGHT_AWAY in tossing.unlocked, "should pop once the dice have landed: ${tossing.unlocked}")

        // The menu logo's exact dice are Product Placement.
        assertTrue(Achievement.PRODUCT_PLACEMENT in unlockedAfter(ScriptedDice(listOf(2, 4, 5, 3, 6))) { rollDice() })

        // Turn 1 zeroes the box with no such hand at all; turn 2 rolls a genuine one, but the box is no longer a real option.
        // A pair with no triple, then three 6s and two 5s: no House Call.
        val houseCall = unlockedAfter(ScriptedDice(listOf(1, 1, 2, 3, 4, 6, 6, 6, 5, 5))) {
            rollDice()
            commitScore(ScoreCategory.FULL_HOUSE)
            rollDice()
        }
        assertFalse(Achievement.HOUSE_CALL in houseCall, "HOUSE_CALL should not pop once Full House is zeroed, got $houseCall")
        // Five 6s, then 1-2-3-4-5: no Straight Away.
        val straightAway = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 6, 1, 2, 3, 4, 5))) {
            rollDice()
            commitScore(ScoreCategory.LARGE_STRAIGHT)
            rollDice()
        }
        assertFalse(Achievement.STRAIGHT_AWAY in straightAway, "STRAIGHT_AWAY should not pop once Large Straight is zeroed, got $straightAway")
        // No 5x, then five 6s on both rolls - a 5x on the first roll and (nothing held) the second - and the box hasn't
        // shown a genuine 50 for the joker bonus to kick in either.
        val fiveX = unlockedAfter(ScriptedDice(listOf(1, 2, 3, 4, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6))) {
            rollDice()
            commitScore(ScoreCategory.FIVE_OF_A_KIND)
            rollDice()
            rollDice()
        }
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
    fun `scoring Chance with 1-2-3-4-5 in order is I Can Count - on any roll of the turn - and no other box or order is`() = runTest {
        fun counted(box: ScoreCategory, dice: List<Int> = (1..5).toList(), rolls: Int = 1) = unlockedAfter(ScriptedDice(dice)) {
            repeat(rolls) { rollDice() }
            commitScore(box)
        }
        assertTrue(Achievement.I_CAN_COUNT in counted(ScoreCategory.CHANCE), "I_CAN_COUNT should pop on the commit")
        assertTrue(Achievement.I_CAN_COUNT in counted(ScoreCategory.CHANCE, (1..5).toList() + (1..5).toList(), rolls = 2), "a later roll counts")
        assertFalse(Achievement.I_CAN_COUNT in counted(ScoreCategory.LARGE_STRAIGHT), "the wrong box")
        assertFalse(Achievement.I_CAN_COUNT in counted(ScoreCategory.CHANCE, listOf(1, 2, 3, 5, 4)), "the wrong order")
    }

    @Test
    fun `a 5x wasted as a zero is Wasted Fortune for player 1 - and two in a row or one rerolled into is its own feat`() = runTest {
        // Every die comes up 6: a genuine 5x, but committed to Ones - no die shows a 1, so it scores 0.
        val wasted = unlockedAfter(LoadedDice(6)) {
            rollDice()
            commitScore(ScoreCategory.ONES)
        }
        assertTrue(Achievement.WASTED_FORTUNE in wasted, "WASTED_FORTUNE should pop on the commit itself, got $wasted")
        val kept = unlockedAfter(LoadedDice(6)) {
            rollDice()
            commitScore(ScoreCategory.FIVE_OF_A_KIND)
        }
        assertFalse(Achievement.WASTED_FORTUNE in kept, "scoring 50 in the 5x box is not a waste, got $kept")
        // Player 1 commits the 5x itself; player 2 wastes theirs on Ones - which would count were it player 1's turn.
        val playerTwoWasted = unlockedAfter(LoadedDice(6), players = 2) {
            rollDice()
            commitScore(ScoreCategory.FIVE_OF_A_KIND)
            rollDice()
            commitScore(ScoreCategory.ONES)
        }
        assertFalse(Achievement.WASTED_FORTUNE in playerTwoWasted, "wasting a 5x on another human's turn must not unlock an achievement, got $playerTwoWasted")

        // A 5x on two turns in a row is Twice in a Lifetime - the second forced by the joker rule (Twos is the matching
        // upper box), still a genuine bonus 5x.
        val twice = unlockedAfter(LoadedDice(2)) {
            rollDice()
            commitScore(ScoreCategory.FIVE_OF_A_KIND)
            rollDice()
            commitScore(ScoreCategory.TWOS)
        }
        assertTrue(Achievement.TWICE_IN_A_LIFETIME in twice, "TWICE_IN_A_LIFETIME should pop, got $twice")

        // A 5x on the 2nd roll without holding anything is Natural 5x - and so is one straight out of the cup.
        val natural = unlockedAfter(LoadedDice(6)) {
            rollDice()
            rollDice()
        }
        assertTrue(Achievement.NATURAL_5X in natural, "NATURAL_5X should pop, got $natural")
        val firstRoll = unlockedAfter(LoadedDice(6)) { rollDice() }
        assertTrue(Achievement.NATURAL_5X in firstRoll, "NATURAL_5X should pop on a first roll, got $firstRoll")
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
        assertTrue(Achievement.THE_MAGICIANS_SECRET in rabbit, "THE_MAGICIANS_SECRET should pop, got $rabbit")
    }

    @Test
    fun `the unheld dice landing the same on both re-rolls is Deja Vu`() = runTest {
        // Roll 1: [1,2,2,2,3]; hold the three 2s; rolls 2 and 3 land the same 1 and 3 in the gaps.
        val loaded = unlockedAfter(ScriptedDice(listOf(1, 2, 2, 2, 3, 1, 3, 1, 3))) {
            rollDice()
            toggleHold(1)
            toggleHold(2)
            toggleHold(3)
            rollDice()
            rollDice()
        }
        assertTrue(Achievement.DEJA_VU in loaded, "DEJA_VU should pop, got $loaded")
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
        assertTrue(Achievement.WHAT_WAS_THE_POINT_OF_THAT in pointless, "WHAT_WAS_THE_POINT_OF_THAT should pop, got $pointless")

        val cunning = unlockedAfter(LoadedDice(4)) {
            rollDice()
            repeat(5) { toggleHold(it) }
            repeat(5) { toggleHold(it) }
        }
        assertTrue(Achievement.A_CUNNING_STRATEGY in cunning, "A_CUNNING_STRATEGY should pop, got $cunning")

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

    /** Settings with the Flowerpot that grows its plant picked as the cup (a style that needs [started]'s `ownsAllStyles`). */
    private suspend fun flowerpotSettings() = SettingsRepository(InMemoryPreferences()).apply { setDiceCupStyleId("flowerpot_terracotta") }

    @Test
    fun `a full bloom on any cup but the growing Flowerpot never unlocks Green Fingers`() = runTest {
        val table = started(LoadedDice(3))
        table.viewModel.playEveryTurnButTheLast { 3 }
        repeat(3) { table.viewModel.rollDice() }
        advanceUntilIdle()
        assertFalse(Achievement.GREENFINGERS in table.unlocked, "GREENFINGERS needs the Flowerpot, got ${table.unlocked}")
    }

    @Test
    fun `using every roll of every turn unlocks Green Fingers on the game's very last roll - one turn scored early keeps it locked`() = runTest {
        val table = started(LoadedDice(3), settingsRepository = flowerpotSettings(), ownsAllStyles = true)
        advanceUntilIdle()
        table.viewModel.playEveryTurnButTheLast { 3 }
        repeat(2) { table.viewModel.rollDice() }
        advanceUntilIdle()
        assertFalse(Achievement.GREENFINGERS in table.unlocked, "GREENFINGERS should wait for the last roll, got ${table.unlocked}")
        table.viewModel.rollDice()
        advanceUntilIdle()
        assertEquals(false, table.viewModel.game.value?.isGameOver, "the last box is still to fill")
        assertTrue(Achievement.GREENFINGERS in table.unlocked, "GREENFINGERS should pop on the 39th roll, got ${table.unlocked}")

        val early = started(LoadedDice(3), settingsRepository = flowerpotSettings(), ownsAllStyles = true)
        advanceUntilIdle()
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
        val oneRollATurn = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 1)), setUp = { setupForm.setRollsPerTurn(1) }) {
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

        // Four 6s and three others in Stud: the 6s held, the other three rerolled twice and never a 6 - Almost Famous too.
        val stud = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 1, 2, 3, 2, 3, 4, 3, 4, 5)), setUp = { setupForm.setGameMode(GameMode.STUD) }) {
            rollDice()
            for (index in 0 until 4) toggleHold(index)
            rollDice()
            rollDice()
            toggleHold(6)
            commitScore(ScoreCategory.SIXES)
        }
        assertTrue(Achievement.ALMOST_FAMOUS in stud, "ALMOST_FAMOUS should pop in Stud, got $stud")

        // The same, but a fifth 6 among the seven on the second roll: five of the seven matching is a 5x in Stud.
        val studFifth = unlockedAfter(ScriptedDice(listOf(6, 6, 6, 6, 1, 2, 3, 6, 3, 4, 3, 4, 5)), setUp = { setupForm.setGameMode(GameMode.STUD) }) {
            rollDice()
            for (index in 0 until 4) toggleHold(index)
            rollDice()
            rollDice()
            toggleHold(6)
            commitScore(ScoreCategory.SIXES)
        }
        assertFalse(Achievement.ALMOST_FAMOUS in studFifth, "ALMOST_FAMOUS should not pop once a fifth 6 lands in Stud, got $studFifth")
    }

    @Test
    fun `Stud - seven matching dice are Lucky Seven - five of them with none held a Natural 5x - and a timed-out turn scores only the held hand`() = runTest {
        val stud: GameViewModel.() -> Unit = { setupForm.setGameMode(GameMode.STUD) }
        val firstRoll = unlockedAfter(LoadedDice(6), setUp = stud) { rollDice() }
        assertTrue(Achievement.LUCKY_SEVEN in firstRoll, "LUCKY_SEVEN should pop, got $firstRoll")
        assertTrue(Achievement.NATURAL_5X in firstRoll, "NATURAL_5X should pop on a first roll too, got $firstRoll")
        // Any five of the seven matching, rolled with none held.
        val rerolled = unlockedAfter(ScriptedDice(listOf(1, 2, 3, 4, 5, 6, 1) + listOf(4, 4, 2, 4, 4, 6, 4)), setUp = stud) {
            rollDice()
            rollDice()
        }
        assertTrue(Achievement.NATURAL_5X in rerolled, "NATURAL_5X should pop in Stud with five of seven matching, got $rerolled")
        // Not with a die held for the roll.
        val held = unlockedAfter(ScriptedDice(listOf(4, 2, 3, 1, 5, 6, 1) + listOf(4, 2, 4, 4, 6, 4)), setUp = stud) {
            rollDice()
            toggleHold(0)
            rollDice()
        }
        assertFalse(Achievement.NATURAL_5X in held, "NATURAL_5X should not pop with a die held, got $held")

        // 6, 1, 6, 2, 6, 3, 1: the player holds the first 1 (die 2), then lets the timer run out. The turn goes in Ones, the
        // hand completed with the last 1 and then the leftmost dice (the first, third and fourth) - so the third 6 and the
        // 3 are left on the mat.
        val timedOut = started(ScriptedDice(listOf(6, 1, 6, 2, 6, 3, 1))) {
            setupForm.setGameMode(GameMode.STUD)
            setupForm.setTurnTimer(TurnTimer.SECONDS_30)
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
    fun `letting every turn time out scores a zero in each box with no roll - and earns none of the low or zeroed cards`() = runTest {
        val table = started(LoadedDice(1)) { setupForm.setTurnTimer(TurnTimer.SECONDS_30) }
        repeat(GameMode.STANDARD.categories.size) {
            testDispatcher.scheduler.advanceTimeBy(30_000)
            testDispatcher.scheduler.runCurrent()
        }
        advanceUntilIdle()

        val state = table.viewModel.game.value!!
        assertTrue(state.isGameOver)
        val player = state.players.single()
        assertEquals(0, player.totalScore)
        assertTrue(player.allScores.all { it == 0 }, "every box a zero: ${player.scorecard}")
        assertEquals(0, player.rollCount, "nothing was rolled")
        assertNull(player.lastRoll)
        for (achievement in listOf(
            Achievement.HOW_DO_YOU_PLAY_THIS_GAME, Achievement.LOW_ROLLS, Achievement.COLD_DICE, Achievement.SCRATCHED, Achievement.IMPATIENT,
        )) {
            assertFalse(achievement in table.unlocked, "$achievement shouldn't pop for a game the timer played, got ${table.unlocked}")
        }
    }

    @Test
    fun `odd choices - the small straight over the large is Why Did You Do That - an undo into another box I Didn't Mean That`() = runTest {
        val small = unlockedAfter(ScriptedDice((1..5).toList())) {
            rollDice()
            commitScore(ScoreCategory.SMALL_STRAIGHT)
        }
        assertTrue(Achievement.SIZE_ISNT_EVERYTHING in small, "SIZE_ISNT_EVERYTHING should pop, got $small")

        fun undoneInto(box: ScoreCategory) = unlockedAfter(LoadedDice(3)) {
            rollDice()
            commitScore(ScoreCategory.THREES)
            undo()
            commitScore(box)
        }
        assertTrue(Achievement.I_DIDNT_MEAN_THAT in undoneInto(ScoreCategory.CHANCE), "I_DIDNT_MEAN_THAT should pop")
        assertFalse(Achievement.I_DIDNT_MEAN_THAT in undoneInto(ScoreCategory.THREES))

        // An undo is forgotten with its game: left for a new one, a different first box earns nothing.
        val newGame = unlockedAfter(LoadedDice(3)) {
            rollDice()
            commitScore(ScoreCategory.THREES)
            undo()
            startGame()
            rollDice()
            commitScore(ScoreCategory.CHANCE)
        }
        assertFalse(Achievement.I_DIDNT_MEAN_THAT in newGame, "carried over into a new game, got $newGame")
    }

    @Test
    fun `an undo is kept when the game is left and resumed - Continue is the same game`() = runTest(testDispatcher) {
        val saves = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val store = InMemoryAchievementStore()
        fun table() = gameViewModel(
            aiDispatcher = testDispatcher,
            inProgressGameRepository = saves,
            achievementsRepository = store,
            random = LoadedDice(3),
        )

        val left = table()
        left.startGame()
        left.rollDice()
        left.commitScore(ScoreCategory.THREES)
        left.undo()
        runCurrent()

        // Leaving for the menu drops the view model; Continue builds a new one from the save.
        val resumed = table()
        assertTrue(resumed.resumeGame())
        resumed.commitScore(ScoreCategory.CHANCE)
        runCurrent()
        assertTrue(Achievement.I_DIDNT_MEAN_THAT in store.unlocked, "the undo was lost on resuming, got ${store.unlocked}")
    }

    @Test
    fun `a score left undoable is still undoable on resuming - undone into another box it's I Didn't Mean That`() = runTest(testDispatcher) {
        val saves = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val store = InMemoryAchievementStore()
        fun table() = gameViewModel(
            aiDispatcher = testDispatcher,
            inProgressGameRepository = saves,
            achievementsRepository = store,
            random = LoadedDice(3),
        )

        val left = table()
        left.startGame()
        left.rollDice()
        val beforeScore = left.game.value
        left.commitScore(ScoreCategory.THREES)
        runCurrent()

        val resumed = table()
        assertTrue(resumed.resumeGame())
        assertTrue(resumed.canUndo.value, "the score left undoable lost its Undo on resuming")
        resumed.undo()
        assertEquals(beforeScore, resumed.game.value, "Undo goes back to the turn as it was before the score")
        assertFalse(resumed.canUndo.value)
        resumed.commitScore(ScoreCategory.CHANCE)
        runCurrent()
        assertTrue(Achievement.I_DIDNT_MEAN_THAT in store.unlocked, "the undone box was lost on resuming, got ${store.unlocked}")
    }

    @Test
    fun `a roll after the score clears its Undo - and a resumed game has none either`() = runTest(testDispatcher) {
        val saves = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        fun table() = gameViewModel(aiDispatcher = testDispatcher, inProgressGameRepository = saves, random = LoadedDice(3))

        val left = table()
        left.startGame()
        left.rollDice()
        left.commitScore(ScoreCategory.THREES)
        left.rollDice()
        assertFalse(left.canUndo.value)
        runCurrent()

        val resumed = table()
        assertTrue(resumed.resumeGame())
        assertFalse(resumed.canUndo.value, "an Undo the roll had cleared came back on resuming")
    }

    @Test
    fun `a new game started in the small hours is Rolling The Midnight Dice - one resumed then is not`() = runTest(testDispatcher) {
        val saves = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val store = InMemoryAchievementStore()
        fun table(hour: Int) = gameViewModel(
            aiDispatcher = testDispatcher,
            inProgressGameRepository = saves,
            settingsRepository = SettingsRepository(InMemoryPreferences()),
            achievementsRepository = store,
            localHour = { hour },
        )

        // Started in the afternoon, then left.
        table(hour = 14).startGame()
        advanceUntilIdle()
        assertFalse(Achievement.ROLLING_THE_MIDNIGHT_DICE in store.unlocked, "started at 2pm")

        // Resumed at 2am: the game began in the afternoon.
        assertTrue(table(hour = 2).resumeGame())
        advanceUntilIdle()
        assertFalse(Achievement.ROLLING_THE_MIDNIGHT_DICE in store.unlocked, "resumed at 2am")

        // A new one at 2am.
        table(hour = 2).startGame()
        advanceUntilIdle()
        assertTrue(Achievement.ROLLING_THE_MIDNIGHT_DICE in store.unlocked, "started at 2am, got ${store.unlocked}")
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
    fun `bad luck - The Dice Hate Me and Probability Never Heard Of Her`() = runTest {
        // 12 filler turns (every category but Ones) rolling all-2s, then a final turn on Ones: roll 2 shows a 1 (a real
        // option), roll 3 doesn't (no option left at all).
        val hate = unlockedAfter(ScriptedDice(List(65) { 2 } + listOf(1, 2, 2, 2, 2) + List(5) { 2 })) {
            for (category in GameMode.STANDARD.categories.filter { it != ScoreCategory.ONES }) {
                rollDice()
                commitScore(category)
            }
            repeat(3) { rollDice() }
        }
        assertTrue(Achievement.THE_DICE_HATE_ME in hate, "THE_DICE_HATE_ME should pop, got $hate")

        // The same in Stud, judged on any five of the seven: roll 2's 1 is an option for Ones, roll 3 has none.
        val studHate = unlockedAfter(ScriptedDice(List(84) { 2 } + List(7) { 2 } + listOf(1) + List(6) { 2 } + List(7) { 2 }), setUp = { setupForm.setGameMode(GameMode.STUD) }) {
            for (category in GameMode.STUD.categories.filter { it != ScoreCategory.ONES }) {
                rollDice()
                commitScore(category)
            }
            repeat(3) { rollDice() }
        }
        assertTrue(Achievement.THE_DICE_HATE_ME in studHate, "THE_DICE_HATE_ME should pop in Stud, got $studHate")

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
