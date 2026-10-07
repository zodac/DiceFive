package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase

class HandCompletionTest {

    private val onePlayer = listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"))

    private fun rolledStud(vararg values: Int): GameState =
        GameEngine.newGame(onePlayer, GameMode.STUD).copy(dice = values.map { Die(value = it) }, phase = TurnPhase.ROLLED, rollsRemaining = 0)

    private fun held(state: GameState, vararg indices: Int): GameState = indices.fold(state) { current, index -> GameEngine.toggleHold(current, index) }

    private fun GameState.heldValues(): List<Int> = scoringDice.map { it.value }.sorted()

    @Test
    fun `with nothing held the best five of the seven dice are taken for the tapped box`() {
        val state = rolledStud(1, 2, 6, 6, 3, 6, 5)

        assertEquals(listOf(3, 5, 6, 6, 6), HandCompletion.bestCompletion(state, ScoreCategory.CHANCE)!!.heldValues())
    }

    @Test
    fun `the held dice are always kept in the hand`() {
        val state = held(rolledStud(1, 2, 6, 6, 3, 6, 5), 0)

        val hand = HandCompletion.bestCompletion(state, ScoreCategory.CHANCE)!!
        assertTrue(hand.dice[0].isHeld)
        assertEquals(listOf(1, 5, 6, 6, 6), hand.heldValues())
    }

    @Test
    fun `the upper boxes take every matching die and the best of the rest`() {
        val hand = HandCompletion.bestCompletion(rolledStud(4, 4, 4, 1, 2, 3, 6), ScoreCategory.FOURS)!!

        assertEquals(12, ScoreCalculator.scoreFor(hand.currentPlayer!!, ScoreCategory.FOURS, hand.scoringDice))
    }

    @Test
    fun `a straight is found among seven dice`() {
        val state = rolledStud(1, 2, 3, 4, 5, 5, 6)

        val hand = HandCompletion.bestCompletion(state, ScoreCategory.LARGE_STRAIGHT)!!
        assertEquals(40, ScoreCalculator.scoreFor(hand.currentPlayer!!, ScoreCategory.LARGE_STRAIGHT, hand.scoringDice))
    }

    @Test
    fun `a full house is found among seven dice`() {
        val state = rolledStud(2, 2, 2, 5, 5, 6, 6)

        val hand = HandCompletion.bestCompletion(state, ScoreCategory.FULL_HOUSE)!!
        assertEquals(25, ScoreCalculator.scoreFor(hand.currentPlayer!!, ScoreCategory.FULL_HOUSE, hand.scoringDice))
    }

    @Test
    fun `the preview of every box is its best completion`() {
        val state = rolledStud(6, 6, 6, 6, 1, 2, 3)

        val hands = HandCompletion.projectedHands(state)
        val player = state.currentPlayer!!
        assertEquals(ScoreCategory.entries.filter { it in player.categories }.toSet(), hands.keys)
        assertEquals(24, ScoreCalculator.scoreFor(player, ScoreCategory.SIXES, hands.getValue(ScoreCategory.SIXES).dice))
        assertTrue(hands.values.all { it.dice.size == 5 })
    }

    @Test
    fun `a box already scored is not offered`() {
        val state = rolledStud(6, 6, 6, 6, 1, 2, 3)
        val scored = GameEngine.commitScore(GameEngine.fillHand(state), ScoreCategory.CHANCE)
            .copy(dice = state.dice, phase = TurnPhase.ROLLED)

        assertFalse(ScoreCategory.CHANCE in HandCompletion.projectedHands(scored))
        assertNull(HandCompletion.bestCompletion(scored, ScoreCategory.CHANCE))
    }

    @Test
    fun `a whole hand is its own only completion - and so is any mode where every die scores`() {
        val whole = held(rolledStud(1, 2, 3, 4, 5, 6, 6), 0, 1, 2, 3, 4)
        assertEquals(listOf(whole), HandCompletion.completions(whole))

        val standard = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        assertEquals(listOf(standard), HandCompletion.completions(standard))
        assertEquals(ScoreCategory.ONES, HandCompletion.timeoutCategory(standard))
    }

    @Test
    fun `nothing is completed before the first roll`() {
        val fresh = GameEngine.newGame(onePlayer, GameMode.STUD)

        assertEquals(listOf(fresh), HandCompletion.completions(fresh))
    }

    @Test
    fun `a turn that ran out of time goes in the first box`() {
        assertEquals(ScoreCategory.ONES, HandCompletion.timeoutCategory(rolledStud(1, 2, 3, 4, 5, 6, 6)))
    }

    @Test
    fun `equal completions take the leftmost dice`() {
        // Nothing is a pair: every five dice score the same zero in the 5x box.
        val hand = HandCompletion.bestCompletion(rolledStud(1, 2, 3, 4, 5, 6, 1), ScoreCategory.FIVE_OF_A_KIND)!!

        assertEquals(listOf(true, true, true, true, true, false, false), hand.dice.map { it.isHeld })
    }

    @Test
    fun `the completed dice take hold slots`() {
        val hand = HandCompletion.bestCompletion(rolledStud(1, 2, 3, 4, 5, 6, 6), ScoreCategory.CHANCE)!!

        assertEquals(setOf(0, 1, 2, 3, 4), hand.dice.mapNotNull { it.heldSlot }.toSet())
    }

    @Test
    fun `dice that would score the same in other faces are the alternatives`() {
        val fours = HandCompletion.projectedHands(rolledStud(4, 4, 4, 1, 2, 3, 6)).getValue(ScoreCategory.FOURS)

        // The three 4s and the leftmost two of the rest; the 3 and the 6 would have done as well.
        assertEquals(setOf(0, 1, 2, 3, 4), fours.used)
        assertEquals(setOf(5, 6), fours.alternatives)
    }

    @Test
    fun `a best hand nothing else matches has no alternatives`() {
        val chance = HandCompletion.projectedHands(rolledStud(1, 2, 6, 6, 3, 6, 5)).getValue(ScoreCategory.CHANCE)

        assertEquals(setOf(2, 3, 4, 5, 6), chance.used)
        assertEquals(emptySet(), chance.alternatives)
    }

    @Test
    fun `the same faces on other dice are not an alternative`() {
        val ones = HandCompletion.projectedHands(rolledStud(1, 1, 2, 2, 2, 2, 2)).getValue(ScoreCategory.ONES)

        // Every five dice with both 1s are 1, 1, 2, 2, 2 - there is only one hand to see.
        assertEquals(emptySet(), ones.alternatives)
    }
}
