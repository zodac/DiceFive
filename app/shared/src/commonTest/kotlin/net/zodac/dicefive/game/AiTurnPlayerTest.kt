package net.zodac.dicefive.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.oneScoreEach

class AiTurnPlayerTest {

    private fun bot(difficulty: Difficulty, scorecard: Map<ScoreCategory, List<Int>> = PlayerState(name = "Bot", type = PlayerType.AI).scorecard) =
        PlayerState(name = "Bot", type = PlayerType.AI, difficulty = difficulty, scorecard = scorecard)

    private fun rolledState(player: PlayerState, values: List<Int>, rollsRemaining: Int = 1) = GameState(
        players = listOf(player),
        dice = values.map { Die(value = it) },
        rollsRemaining = rollsRemaining,
        phase = TurnPhase.ROLLED,
    )

    /** A new solo game of [mode] for a CPU at [difficulty]. */
    private fun soloGame(difficulty: Difficulty, mode: GameMode = GameMode.STANDARD, random: Random = Random.Default, extendedScores: Boolean = false) =
        GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.AI, name = "Bot", difficulty = difficulty)), mode, random = random, extendedScores = extendedScores)

    /** A whole solo game of [mode] played by a CPU at [difficulty] off [random]: the finished game and how many turns it took. */
    private fun playedOut(difficulty: Difficulty, mode: GameMode = GameMode.STANDARD, random: Random, extendedScores: Boolean = false): Pair<GameState, Int> {
        var state = soloGame(difficulty, mode, random, extendedScores)
        var turns = 0
        while (!state.isGameOver) {
            state = AiTurnPlayer.playTurn(state, random)
            turns++
        }
        return state to turns
    }

    /** The average score a CPU at [difficulty] makes over [games] seeded solo games of [mode] - the same games every run. */
    private fun seededAverage(difficulty: Difficulty, mode: GameMode, games: Int) =
        (1..games).map { seed -> playedOut(difficulty, mode, Random(seed)).first.players.single().totalScore }.average()

    @Test
    fun `the category chosen - the best open one - ties to the more restrictive box and for Hard the rarer box over raw points`() {
        val sixes = GameState(players = listOf(PlayerState(name = "Bot", type = PlayerType.AI)), dice = List(5) { Die(value = 6) }, rollsRemaining = 0, phase = TurnPhase.ROLLED)
        assertEquals(ScoreCategory.FIVE_OF_A_KIND, AiTurnPlayer.chooseCategory(sixes))
        // Never an already-filled one.
        val filled = PlayerState(name = "Bot", type = PlayerType.AI).let { it.copy(scorecard = it.scorecard + (ScoreCategory.FIVE_OF_A_KIND to listOf(50))) }
        assertFalse(AiTurnPlayer.chooseCategory(sixes.copy(players = listOf(filled))) == ScoreCategory.FIVE_OF_A_KIND)

        // Four matching 3s plus a 1: 4x, 3x and Chance all score the same sum (13) - Medium prefers the hardest-to-get one
        // rather than enum order.
        assertEquals(ScoreCategory.FOUR_OF_A_KIND, AiTurnPlayer.chooseCategory(rolledState(bot(Difficulty.MEDIUM), values = listOf(3, 3, 3, 3, 1))))

        // [5,5,5,6,6] is a Full House (25) but 3x and Chance both score higher (27). Full House is much rarer than either,
        // so Hard's opportunity-cost math takes it now - unlike Easy or Medium.
        val values = listOf(5, 5, 5, 6, 6)
        assertEquals(ScoreCategory.THREE_OF_A_KIND, AiTurnPlayer.chooseCategory(rolledState(bot(Difficulty.EASY), values)))
        assertEquals(ScoreCategory.THREE_OF_A_KIND, AiTurnPlayer.chooseCategory(rolledState(bot(Difficulty.MEDIUM), values)))
        assertEquals(ScoreCategory.FULL_HOUSE, AiTurnPlayer.chooseCategory(rolledState(bot(Difficulty.HARD), values)))
    }

    @Test
    fun `playTurn rolls then scores exactly one category - leaving the next turn fresh - at Medium and Hard`() {
        val result = AiTurnPlayer.playTurn(GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.AI, name = "Bot"))), random = Random(42))
        assertEquals(1, result.players.single().turnsTaken)
        assertEquals(TurnPhase.AWAITING_ROLL, result.phase)
        assertEquals(3, result.rollsRemaining)
        assertTrue(result.dice.none { it.isHeld })

        assertEquals(1, AiTurnPlayer.playTurn(soloGame(Difficulty.HARD), random = Random(7)).players.single().turnsTaken)
    }

    @Test
    fun `Easy stops rolling as soon as any open category would score - and rerolls everything while none would`() {
        // Chance (and plenty else) is open and scores here, so Easy holds everything - the same "stop rolling" signal
        // Medium/Hard use - rather than spending a roll it doesn't need.
        assertEquals(setOf(0, 1, 2, 3, 4), AiTurnPlayer.chooseHolds(rolledState(bot(Difficulty.EASY), values = listOf(6, 6, 6, 1, 2))))

        // Every category filled except Ones, and no 1s among the dice: the only open category scores zero here, so Easy has
        // nothing to stop for and holds nothing.
        val scorecard = GameMode.STANDARD.categories.associateWith { category -> if (category == ScoreCategory.ONES) null else 0 }
        assertEquals(emptySet<Int>(), AiTurnPlayer.chooseHolds(rolledState(bot(Difficulty.EASY, scorecard = oneScoreEach(scorecard)), values = listOf(2, 3, 4, 5, 6))))
    }

    @Test
    fun `Medium holds a forming straight or its biggest group - and stops once it has a hand worth banking`() {
        fun holds(values: List<Int>, scorecard: Map<ScoreCategory, List<Int>> = PlayerState(name = "Bot", type = PlayerType.AI).scorecard) =
            AiTurnPlayer.chooseHolds(rolledState(bot(Difficulty.MEDIUM, scorecard), values))

        // A distinct run 1-2-3-4 (four dice) beats holding just the pair of 4s.
        val straight = listOf(1, 2, 3, 4, 4)
        assertEquals(listOf(1, 2, 3, 4), holds(straight).map { straight[it] }.sorted())
        // It keeps the die it already holds rather than swapping it for an identical one: 1-2-3-4 held from the last roll,
        // with the 4 at the end, and a second 4 has since landed earlier.
        val alreadyHeld = rolledState(bot(Difficulty.MEDIUM), values = listOf(1, 4, 2, 3, 4)).let { it.copy(dice = it.dice.mapIndexed { index, die -> die.copy(isHeld = index != 1) }) }
        assertEquals(setOf(0, 2, 3, 4), AiTurnPlayer.chooseHolds(alreadyHeld))
        // With no straight forming, a group of low-value dice isn't "good enough" to bank early - it holds the triple and
        // keeps rolling; and a lone die isn't worth holding over a pair (1,2,3 is only a run of three).
        assertEquals(setOf(0, 1, 2), holds(listOf(2, 2, 2, 6, 1)))
        assertEquals(setOf(3, 4), holds(listOf(6, 1, 2, 3, 3)))

        // It stops rolling once it has three of a kind on a high upper value (three 6s already bank well in the open Sixes
        // box), a Full House, or a Small Straight once Large Straight is no longer available.
        val everything = setOf(0, 1, 2, 3, 4)
        assertEquals(everything, holds(listOf(6, 6, 6, 1, 2)))
        assertEquals(everything, holds(listOf(5, 5, 5, 6, 6)))
        assertEquals(everything, holds(listOf(1, 2, 3, 4, 6), PlayerState(name = "Bot", type = PlayerType.AI).scorecard + (ScoreCategory.LARGE_STRAIGHT to listOf(40))))
    }

    @Test
    fun `Hard holds a four of a kind and a set of low numbers - keeping the very dice it already holds`() {
        assertEquals(setOf(0, 1, 2, 3), AiTurnPlayer.chooseHolds(rolledState(bot(Difficulty.HARD), values = listOf(6, 6, 6, 6, 2))))

        // The same hand either way round: whichever 4 is already held, Hard keeps that one and leaves the other free,
        // rather than always taking the leftmost.
        for (heldFour in listOf(1, 4)) {
            val state = rolledState(bot(Difficulty.HARD), values = listOf(1, 4, 2, 3, 4))
                .let { it.copy(dice = it.dice.mapIndexed { index, die -> die.copy(isHeld = index in setOf(0, 2, 3, heldFour)) }) }
            assertEquals(setOf(0, 2, 3, heldFour), AiTurnPlayer.chooseHolds(state), "held 4 at index $heldFour")
        }

        // Valued by raw points, the 6 and 5 looked better than three 2s - a low total - and Hard threw the 2s back. It keeps
        // the set for 5x and the upper bonus.
        assertEquals(setOf(0, 1, 2), AiTurnPlayer.chooseHolds(rolledState(bot(Difficulty.HARD), values = listOf(2, 2, 2, 6, 5), rollsRemaining = 2)))
    }

    @Test
    fun `Hard's strength over seeded games - at least 230 in Standard - more in Stud and ahead of Medium in Third Wind`() {
        // Seeded, so the same games every run. Planning one reroll ahead by raw points, Hard averaged 219 over these 200;
        // planning the whole turn by what each box is worth, it averages 239.
        val standard = seededAverage(Difficulty.HARD, GameMode.STANDARD, SEEDED_GAMES)
        assertTrue(standard >= 230, "Hard averaged $standard")

        val stud = seededAverage(Difficulty.HARD, GameMode.STUD, STUD_SEEDED_GAMES)
        assertTrue(stud > seededAverage(Difficulty.HARD, GameMode.STANDARD, STUD_SEEDED_GAMES), "Hard averaged $stud in Stud")

        val hard = seededAverage(Difficulty.HARD, GameMode.THIRD_WIND, THIRD_WIND_SEEDED_GAMES)
        val medium = seededAverage(Difficulty.MEDIUM, GameMode.THIRD_WIND, THIRD_WIND_SEEDED_GAMES)
        assertTrue(hard > medium, "Hard averaged $hard to Medium's $medium in Third Wind")
    }

    @Test
    fun `every difficulty plays whole legal games of every mode and modifier`() {
        for (difficulty in Difficulty.entries) {
            // Tricolour: a legal turn.
            val tricolour = AiTurnPlayer.playTurn(GameState(gameMode = GameMode.TRICOLOUR, players = listOf(tricolourBot(difficulty))), Random(difficulty.ordinal))
            assertEquals(1, tricolour.players.single().turnsTaken, "$difficulty")

            // Stud: a full held hand scored every turn.
            val random = Random(difficulty.ordinal)
            var stud = GameState(gameMode = GameMode.STUD, players = listOf(studBot(difficulty)))
            while (!stud.isGameOver) {
                stud = AiTurnPlayer.playTurn(stud, random)
                val last = requireNotNull(stud.players.single().lastRoll)
                assertEquals(7, last.size, "$difficulty")
                assertEquals(5, last.count { it.isHeld }, "$difficulty")
            }
            assertTrue(stud.players.single().isScorecardComplete, "$difficulty")

            // Third Wind: every box three times.
            val (thirdWind, thirdWindTurns) = playedOut(difficulty, GameMode.THIRD_WIND, Random(difficulty.ordinal))
            assertEquals(39, thirdWindTurns, "$difficulty")
            assertTrue(thirdWind.players.single().scorecard.values.all { it.size == 3 }, "$difficulty")

            // Quickfire: six turns, never scoring a switched off box.
            for (seed in 1..QUICKFIRE_SEEDED_GAMES) {
                val (quickfire, turns) = playedOut(difficulty, GameMode.QUICKFIRE, Random(seed))
                val player = quickfire.players.single()
                assertEquals(6, turns, "$difficulty seed $seed")
                assertTrue(quickfire.disabledCategories.all { player.scoresIn(it).isEmpty() }, "$difficulty seed $seed")
                assertEquals(6, player.allScores.size, "$difficulty seed $seed")
            }

            // Extended Scores, in Standard and Tricolour.
            for (mode in listOf(GameMode.STANDARD, GameMode.TRICOLOUR)) {
                val (extended, turns) = playedOut(difficulty, mode, Random(difficulty.ordinal), extendedScores = true)
                assertEquals(mode.categories.size + 3, turns, "$mode $difficulty")
                assertTrue(extended.players.single().scorecard.values.all { it.size == 1 }, "$mode $difficulty")
            }

            // Unlucky Dice: never holding a locked die.
            for (mode in listOf(GameMode.STANDARD, GameMode.TRICOLOUR, GameMode.STUD)) {
                val unluckyRandom = Random(difficulty.ordinal + 11)
                var state = GameEngine.newGame(
                    listOf(PlayerConfig(slot = 1, type = PlayerType.AI, name = "Bot", difficulty = difficulty)),
                    mode,
                    unluckyDice = UnluckyDice(oddsPercent = 50, maxDice = 5),
                )
                var turns = 0
                while (!state.isGameOver) {
                    var turn = state
                    while (turn.rollsRemaining > 0) {
                        turn = GameEngine.rollDice(turn, unluckyRandom)
                        if (turn.rollsRemaining == 0) break
                        val holds = AiTurnPlayer.chooseHolds(turn)
                        assertTrue(holds.none { turn.dice[it].isUnlucky && holds.size < turn.dice.size }, "$mode $difficulty held a locked die")
                        if (holds.size == turn.dice.size) break
                        turn = AiTurnPlayer.applyHolds(turn, holds)
                    }
                    if (turn.gameMode.scoresHeldDiceOnly) turn = AiTurnPlayer.applyHolds(turn, AiTurnPlayer.chooseHand(turn))
                    assertTrue(turn.hasFullHand, "$mode $difficulty")
                    state = GameEngine.commitScore(turn, AiTurnPlayer.chooseCategory(turn))
                    turns++
                }
                assertEquals(mode.categories.size * mode.scoresPerCategory, turns, "$mode $difficulty")
            }
        }
    }

    // ---- Tricolour -------------------------------------------------------------------------------

    private fun tricolourBot(difficulty: Difficulty) =
        PlayerState(name = "Bot", type = PlayerType.AI, difficulty = difficulty, gameMode = GameMode.TRICOLOUR)

    @Test
    fun `in Tricolour Medium banks a colour set or holds four of a colour for the fifth - and Hard scores a set in its colour box`() {
        fun rolled(difficulty: Difficulty, dice: List<Die>, rollsRemaining: Int) =
            GameState(gameMode = GameMode.TRICOLOUR, players = listOf(tricolourBot(difficulty)), dice = dice, rollsRemaining = rollsRemaining, phase = TurnPhase.ROLLED)

        val blueSet = listOf(1, 3, 4, 5, 6).map { Die(value = it, colour = DieColour.BLUE) }
        assertEquals(setOf(0, 1, 2, 3, 4), AiTurnPlayer.chooseHolds(rolled(Difficulty.MEDIUM, blueSet, rollsRemaining = 2)))

        val colours = listOf(DieColour.RED, DieColour.RED, DieColour.YELLOW, DieColour.RED, DieColour.RED)
        val fourRed = listOf(1, 1, 3, 5, 6).zip(colours) { value, colour -> Die(value = value, colour = colour) }
        assertEquals(setOf(0, 1, 3, 4), AiTurnPlayer.chooseHolds(rolled(Difficulty.MEDIUM, fourRed, rollsRemaining = 2)))

        val allBlue = listOf(2, 3, 3, 5, 6).map { Die(value = it, colour = DieColour.BLUE) }
        assertEquals(ScoreCategory.BLUES, AiTurnPlayer.chooseCategory(rolled(Difficulty.HARD, allBlue, rollsRemaining = 0)))
    }

    // ---- Stud ------------------------------------------------------------------------------------

    private fun studBot(difficulty: Difficulty) =
        PlayerState(name = "Bot", type = PlayerType.AI, difficulty = difficulty, gameMode = GameMode.STUD)

    private fun rolledStud(difficulty: Difficulty, values: List<Int>, rollsRemaining: Int) = GameState(
        gameMode = GameMode.STUD,
        players = listOf(studBot(difficulty)),
        dice = values.map { Die(value = it) },
        rollsRemaining = rollsRemaining,
        phase = TurnPhase.ROLLED,
    )

    @Test
    fun `in Stud holds fit the slots - the 5x is picked out as the hand and the category comes from the held hand alone`() {
        // A hold never asks for more dice than there are slots - except every die, to stop.
        val random = Random(3)
        for (difficulty in Difficulty.entries) {
            repeat(STUD_HOLD_CASES) {
                val state = rolledStud(difficulty, List(7) { random.nextInt(1, 7) }, rollsRemaining = 2)
                val holds = AiTurnPlayer.chooseHolds(state)
                assertTrue(holds.size <= 5 || holds.size == 7, "$difficulty held $holds of ${state.dice.map { it.value }}")
            }
            assertEquals(setOf(0, 1, 3, 4, 6), AiTurnPlayer.chooseHand(rolledStud(difficulty, listOf(4, 4, 1, 4, 4, 6, 4), rollsRemaining = 0)), "$difficulty")
        }

        // Hard holds the four 6s and rerolls the rest.
        assertTrue(AiTurnPlayer.chooseHolds(rolledStud(Difficulty.HARD, listOf(6, 2, 6, 1, 6, 3, 6), rollsRemaining = 2)).containsAll(setOf(0, 2, 4, 6)))

        // Five 2s held; the two 6s left on the mat would make Sixes or Chance worth more, but don't score.
        val dice = listOf(2, 2, 2, 2, 2).mapIndexed { slot, value -> Die(value = value, isHeld = true, heldSlot = slot) } + List(2) { Die(value = 6) }
        val held = GameState(gameMode = GameMode.STUD, players = listOf(studBot(Difficulty.EASY)), dice = dice, rollsRemaining = 0, phase = TurnPhase.ROLLED)
        assertEquals(ScoreCategory.FIVE_OF_A_KIND, AiTurnPlayer.chooseCategory(held))
    }

    private companion object {
        const val THIRD_WIND_SEEDED_GAMES = 30
        const val QUICKFIRE_SEEDED_GAMES = 25
        const val SEEDED_GAMES = 200
        const val STUD_HOLD_CASES = 200
        const val STUD_SEEDED_GAMES = 40
    }
}
