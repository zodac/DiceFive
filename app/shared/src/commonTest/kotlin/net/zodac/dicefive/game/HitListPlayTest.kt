package net.zodac.dicefive.game

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.UnluckyDice

/** How a CPU plays Hit List - see [HitListPlay]. */
class HitListPlayTest {

    private fun newGame(difficulty: Difficulty, seed: Int, unluckyDice: UnluckyDice? = null): GameState =
        GameEngine.newGame(
            listOf(PlayerConfig(slot = 1, type = PlayerType.AI, name = "Bot", difficulty = difficulty)),
            GameMode.HIT_LIST,
            unluckyDice = unluckyDice,
            random = Random(seed),
        )

    @Test
    fun `the odds of a whole turn's hit are what they are worked out to be`() {
        // Worked out separately, by the same rule: three rolls, holding every match.
        assertTrue(abs(HitListPlay.hitOdds(listOf(1, 2, 3, 4, 5), rolls = 3, extraDice = 0) - 0.1968) < 0.0005)
        assertTrue(abs(HitListPlay.hitOdds(listOf(1, 2, 3), rolls = 3, extraDice = 2) - 0.6673) < 0.0005)
        assertTrue(abs(HitListPlay.hitOdds(listOf(1, 1, 2, 2, 3), rolls = 3, extraDice = 0) - 0.1080) < 0.0005)
        assertEquals(1.0, HitListPlay.hitOdds(emptyList(), rolls = 0, extraDice = 0))
        assertEquals(0.0, HitListPlay.hitOdds(listOf(1, 2, 3), rolls = 3, extraDice = -1))
        assertTrue(abs(HitListPlay.exactOdds(places = 5, rolls = 3) - 0.0133) < 0.0005)
    }

    @Test
    fun `every difficulty plays whole legal games one box a turn with and without Unlucky Dice - Hard above Medium above Easy`() {
        for (unlucky in listOf(null, UnluckyDice(oddsPercent = 50, maxDice = 5))) for (difficulty in Difficulty.entries) {
            val random = Random(difficulty.ordinal + 3)
            var state = newGame(difficulty, seed = difficulty.ordinal, unluckyDice = unlucky)
            var turns = 0
            while (!state.isGameOver) {
                var turn = state
                while (turn.rollsRemaining > 0) {
                    turn = GameEngine.rollDice(turn, random)
                    if (turn.rollsRemaining == 0) break
                    val holds = AiTurnPlayer.chooseHolds(turn)
                    if (holds.size == turn.dice.size) break
                    assertTrue(holds.none { turn.dice[it].isUnlucky }, "$difficulty held a locked die")
                    turn = AiTurnPlayer.applyHolds(turn, holds)
                }
                state = GameEngine.commitScore(turn, AiTurnPlayer.chooseCategory(turn))
                turns++
            }
            assertEquals(13, turns, "$difficulty $unlucky")
            assertTrue(state.players.single().isScorecardComplete, "$difficulty $unlucky")
        }

        fun average(difficulty: Difficulty): Double = (0 until AVERAGED_GAMES).map { seed ->
            val random = Random(seed)
            var state = newGame(difficulty, seed = seed + 1000)
            while (!state.isGameOver) state = AiTurnPlayer.playTurn(state, random)
            state.players.single().totalScore
        }.average()
        val easy = average(Difficulty.EASY)
        val medium = average(Difficulty.MEDIUM)
        val hard = average(Difficulty.HARD)
        assertTrue(medium > easy, "Medium $medium, Easy $easy")
        assertTrue(hard > medium, "Hard $hard, Medium $medium")
    }

    @Test
    fun `Medium and Hard stop rolling on an exact hit - and take it in its own target`() {
        for (difficulty in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            val game = newGame(difficulty, seed = 5)
            for ((category, target) in game.hitList) {
                val dice = target.places.map { Die(value = it ?: 6) }
                // Exact on another target too is possible - then either is fine, as long as it's an exact hit's points.
                val state = game.copy(dice = dice, phase = TurnPhase.ROLLED, rollsRemaining = 0)
                val chosen = AiTurnPlayer.chooseCategory(state)
                assertTrue(chosen != ScoreCategory.ALIBI, "$difficulty put an exact hit in the Alibi")
                assertTrue(ScoreCalculator.scoreFor(state.players.single(), chosen, dice) >= target.exactPoints || chosen == category, "$difficulty $category")
            }

            val stopping = newGame(difficulty, seed = 6)
            val richest = stopping.hitList.values.maxBy { it.points }
            val rolled = stopping.copy(dice = richest.places.map { Die(value = it ?: 6) }, phase = TurnPhase.ROLLED, rollsRemaining = 2)
            assertEquals(rolled.dice.indices.toSet(), AiTurnPlayer.chooseHolds(rolled), "$difficulty")
        }
    }

    private companion object {
        const val AVERAGED_GAMES = 120
    }
}
