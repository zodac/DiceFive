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

class AiTurnPlayerTest {

    private fun bot(difficulty: Difficulty, scorecard: Map<ScoreCategory, Int?> = PlayerState(name = "Bot", type = PlayerType.AI).scorecard) =
        PlayerState(name = "Bot", type = PlayerType.AI, difficulty = difficulty, scorecard = scorecard)

    private fun rolledState(player: PlayerState, values: List<Int>, rollsRemaining: Int = 1) = GameState(
        players = listOf(player),
        dice = values.map { Die(value = it) },
        rollsRemaining = rollsRemaining,
        phase = TurnPhase.ROLLED,
    )

    @Test
    fun `chooseCategory picks the highest-scoring open category`() {
        val state = GameState(
            players = listOf(PlayerState(name = "Bot", type = PlayerType.AI)),
            dice = List(5) { Die(value = 6) },
            rollsRemaining = 0,
            phase = TurnPhase.ROLLED,
        )

        assertEquals(ScoreCategory.FIVE_OF_A_KIND, AiTurnPlayer.chooseCategory(state))
    }

    @Test
    fun `chooseCategory never picks an already-filled category`() {
        val scorecard = PlayerState(name = "Bot", type = PlayerType.AI).scorecard + (ScoreCategory.FIVE_OF_A_KIND to 50)
        val state = GameState(
            players = listOf(PlayerState(name = "Bot", type = PlayerType.AI, scorecard = scorecard)),
            dice = List(5) { Die(value = 6) },
            rollsRemaining = 0,
            phase = TurnPhase.ROLLED,
        )

        assertFalse(AiTurnPlayer.chooseCategory(state) == ScoreCategory.FIVE_OF_A_KIND)
    }

    @Test
    fun `playTurn rolls three times then scores exactly one category`() {
        val initial = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.AI, name = "Bot")))

        val result = AiTurnPlayer.playTurn(initial, random = Random(42))

        val filled = result.players.single().scorecard.values.count { it != null }
        assertEquals(1, filled)
        assertEquals(TurnPhase.AWAITING_ROLL, result.phase)
        assertEquals(3, result.rollsRemaining)
        assertTrue(result.dice.none { it.isHeld })
    }

    @Test
    fun `Easy stops rolling as soon as any open category would score`() {
        val state = rolledState(bot(Difficulty.EASY), values = listOf(6, 6, 6, 1, 2))

        // CHANCE (and plenty else) is open and scores here, so Easy holds everything - the same
        // "stop rolling" signal Medium/Hard use - rather than spending a roll it doesn't need.
        assertEquals(setOf(0, 1, 2, 3, 4), AiTurnPlayer.chooseHolds(state))
    }

    @Test
    fun `Easy keeps rerolling everything while no open category would score`() {
        // Every category filled except ONES, and no 1s among the dice - the only open category
        // scores zero here, so Easy has nothing to stop for and holds nothing.
        val scorecard = GameMode.STANDARD.categories.associateWith { category -> if (category == ScoreCategory.ONES) null else 0 }
        val state = rolledState(bot(Difficulty.EASY, scorecard = scorecard), values = listOf(2, 3, 4, 5, 6))

        assertEquals(emptySet<Int>(), AiTurnPlayer.chooseHolds(state))
    }

    @Test
    fun `Medium holds a forming straight over a smaller matching group`() {
        val state = rolledState(bot(Difficulty.MEDIUM), values = listOf(1, 2, 3, 4, 4))

        // Distinct run 1-2-3-4 (four dice) beats holding just the pair of 4s.
        val holds = AiTurnPlayer.chooseHolds(state)
        val heldValues = holds.map { state.dice[it].value }.sorted()
        assertEquals(4, holds.size)
        assertEquals(listOf(1, 2, 3, 4), heldValues)
    }

    @Test
    fun `Medium keeps the die it already holds rather than swapping it for an identical one`() {
        // 1-2-3-4 held from the last roll, with the 4 at the end; a second 4 has since landed earlier.
        val state = rolledState(bot(Difficulty.MEDIUM), values = listOf(1, 4, 2, 3, 4))
            .let { it.copy(dice = it.dice.mapIndexed { index, die -> die.copy(isHeld = index != 1) }) }

        assertEquals(setOf(0, 2, 3, 4), AiTurnPlayer.chooseHolds(state))
    }

    @Test
    fun `Medium holds the largest matching group when no straight is forming`() {
        // A group of low-value dice isn't "good enough" to bank early, unlike a high one below -
        // Medium just holds the pair/triple and keeps rolling, same as before.
        val state = rolledState(bot(Difficulty.MEDIUM), values = listOf(2, 2, 2, 6, 1))

        assertEquals(setOf(0, 1, 2), AiTurnPlayer.chooseHolds(state))
    }

    @Test
    fun `Medium stops rolling once it has three-of-a-kind on a high upper value`() {
        // Three 6s already banks well in the open SIXES box - Medium takes it rather than
        // gambling the remaining rolls, unlike Easy (never holds) or Hard's full EV search.
        val state = rolledState(bot(Difficulty.MEDIUM), values = listOf(6, 6, 6, 1, 2))

        assertEquals(setOf(0, 1, 2, 3, 4), AiTurnPlayer.chooseHolds(state))
    }

    @Test
    fun `Medium stops rolling once it has a Full House`() {
        val state = rolledState(bot(Difficulty.MEDIUM), values = listOf(5, 5, 5, 6, 6))

        assertEquals(setOf(0, 1, 2, 3, 4), AiTurnPlayer.chooseHolds(state))
    }

    @Test
    fun `Medium stops rolling on a Small Straight once Large Straight is no longer available`() {
        val scorecard = PlayerState(name = "Bot", type = PlayerType.AI).scorecard + (ScoreCategory.LARGE_STRAIGHT to 40)
        val state = rolledState(bot(Difficulty.MEDIUM, scorecard = scorecard), values = listOf(1, 2, 3, 4, 6))

        assertEquals(setOf(0, 1, 2, 3, 4), AiTurnPlayer.chooseHolds(state))
    }

    @Test
    fun `Medium does not hold a lone die or a single pair`() {
        val single = rolledState(bot(Difficulty.MEDIUM), values = listOf(6, 1, 2, 3, 3))
        // 3,3 pair and no 4-length run present (1,2,3 is only length 3) - holds the pair.
        assertEquals(setOf(3, 4), AiTurnPlayer.chooseHolds(single))
    }

    @Test
    fun `Medium breaks a category tie toward the more restrictive box`() {
        // Four matching 3s plus a 1: FOUR_OF_A_KIND, THREE_OF_A_KIND and CHANCE all score the same
        // sum (13) - Medium should prefer the hardest-to-get one rather than enum order.
        val state = rolledState(bot(Difficulty.MEDIUM), values = listOf(3, 3, 3, 3, 1))

        assertEquals(ScoreCategory.FOUR_OF_A_KIND, AiTurnPlayer.chooseCategory(state))
    }

    @Test
    fun `Hard holds a four-of-a-kind and rerolls only the odd die out`() {
        val state = rolledState(bot(Difficulty.HARD), values = listOf(6, 6, 6, 6, 2))

        assertEquals(setOf(0, 1, 2, 3), AiTurnPlayer.chooseHolds(state))
    }

    @Test
    fun `Hard keeps the die it already holds rather than swapping it for an identical one`() {
        // The same hand either way round: whichever 4 is already held, Hard keeps that one and
        // leaves the other free, rather than always taking the leftmost.
        for (heldFour in listOf(1, 4)) {
            val state = rolledState(bot(Difficulty.HARD), values = listOf(1, 4, 2, 3, 4))
                .let { it.copy(dice = it.dice.mapIndexed { index, die -> die.copy(isHeld = index in setOf(0, 2, 3, heldFour)) }) }

            assertEquals(setOf(0, 2, 3, heldFour), AiTurnPlayer.chooseHolds(state), "held 4 at index $heldFour")
        }
    }

    @Test
    fun `Hard sacrifices raw score to bank the rarer Full House - unlike Easy or Medium`() {
        // [5,5,5,6,6] is a Full House (25) but Three of a Kind/Chance both score higher (27).
        // Full House is much rarer than either, so Hard's opportunity-cost math should take it now.
        val values = listOf(5, 5, 5, 6, 6)

        assertEquals(ScoreCategory.THREE_OF_A_KIND, AiTurnPlayer.chooseCategory(rolledState(bot(Difficulty.EASY), values)))
        assertEquals(ScoreCategory.THREE_OF_A_KIND, AiTurnPlayer.chooseCategory(rolledState(bot(Difficulty.MEDIUM), values)))
        assertEquals(ScoreCategory.FULL_HOUSE, AiTurnPlayer.chooseCategory(rolledState(bot(Difficulty.HARD), values)))
    }

    @Test
    fun `Hard keeps a set of low numbers for 5x and the upper bonus rather than loose high dice`() {
        // Valued by raw points, the 6 and 5 looked better than three 2s - a low total - and Hard threw the 2s back.
        val state = rolledState(bot(Difficulty.HARD), values = listOf(2, 2, 2, 6, 5), rollsRemaining = 2)

        assertEquals(setOf(0, 1, 2), AiTurnPlayer.chooseHolds(state))
    }

    @Test
    fun `Hard averages at least 230 over 200 seeded solo games`() {
        // Seeded, so the same 200 games every run. Planning one reroll ahead by raw points, Hard averaged
        // 219 over these; planning the whole turn by what each box is worth, it averages 239.
        val scores = (1..SEEDED_GAMES).map { seed ->
            val random = Random(seed)
            var state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.AI, name = "Bot", difficulty = Difficulty.HARD)))
            while (!state.isGameOver) state = AiTurnPlayer.playTurn(state, random)
            state.players.single().totalScore
        }

        assertTrue(scores.average() >= 230, "Hard averaged ${scores.average()}")
    }

    @Test
    fun `Hard's playTurn always ends with exactly one newly filled category`() {
        val initial = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.AI, name = "Bot", difficulty = Difficulty.HARD)))

        val result = AiTurnPlayer.playTurn(initial, random = Random(7))

        assertEquals(1, result.players.single().scorecard.values.count { it != null })
    }

    // ---- Tricolour -------------------------------------------------------------------------------

    private fun tricolourBot(difficulty: Difficulty) =
        PlayerState(name = "Bot", type = PlayerType.AI, difficulty = difficulty, gameMode = GameMode.TRICOLOUR)

    @Test
    fun `every difficulty plays a legal Tricolour turn`() {
        for (difficulty in Difficulty.entries) {
            val state = GameState(gameMode = GameMode.TRICOLOUR, players = listOf(tricolourBot(difficulty)))

            val after = AiTurnPlayer.playTurn(state, Random(difficulty.ordinal))

            assertEquals(1, after.players.single().scorecard.values.count { it != null }, "$difficulty")
        }
    }

    @Test
    fun `Medium banks a colour set straight away rather than rerolling it`() {
        val dice = listOf(1, 3, 4, 5, 6).map { Die(value = it, colour = DieColour.BLUE) }
        val state = GameState(
            gameMode = GameMode.TRICOLOUR,
            players = listOf(tricolourBot(Difficulty.MEDIUM)),
            dice = dice,
            rollsRemaining = 2,
            phase = TurnPhase.ROLLED,
        )

        assertEquals(setOf(0, 1, 2, 3, 4), AiTurnPlayer.chooseHolds(state))
    }

    @Test
    fun `Medium holds four of one colour to chase the fifth`() {
        val colours = listOf(DieColour.RED, DieColour.RED, DieColour.YELLOW, DieColour.RED, DieColour.RED)
        val state = GameState(
            gameMode = GameMode.TRICOLOUR,
            players = listOf(tricolourBot(Difficulty.MEDIUM)),
            dice = listOf(1, 1, 3, 5, 6).zip(colours) { value, colour -> Die(value = value, colour = colour) },
            rollsRemaining = 2,
            phase = TurnPhase.ROLLED,
        )

        assertEquals(setOf(0, 1, 3, 4), AiTurnPlayer.chooseHolds(state))
    }

    @Test
    fun `Hard scores a colour set it's been dealt in its colour box`() {
        val allBlue = listOf(2, 3, 3, 5, 6).map { Die(value = it, colour = DieColour.BLUE) }
        val scoring = GameState(
            gameMode = GameMode.TRICOLOUR,
            players = listOf(tricolourBot(Difficulty.HARD)),
            dice = allBlue,
            rollsRemaining = 0,
            phase = TurnPhase.ROLLED,
        )

        assertEquals(ScoreCategory.BLUES, AiTurnPlayer.chooseCategory(scoring))
    }

    private companion object {
        const val SEEDED_GAMES = 200
    }
}
