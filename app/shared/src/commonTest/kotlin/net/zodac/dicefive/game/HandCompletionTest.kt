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

    private fun score(hand: GameState, category: ScoreCategory) = ScoreCalculator.scoreFor(hand.currentPlayer!!, category, hand.scoringDice)

    @Test
    fun `a box tapped takes the best five dice for it - keeping those held - leftmost on a tie - into hold slots`() {
        assertEquals(listOf(3, 5, 6, 6, 6), HandCompletion.bestCompletion(rolledStud(1, 2, 6, 6, 3, 6, 5), ScoreCategory.CHANCE)!!.heldValues())

        // The held dice are always kept in the hand.
        val keptHeld = HandCompletion.bestCompletion(held(rolledStud(1, 2, 6, 6, 3, 6, 5), 0), ScoreCategory.CHANCE)!!
        assertTrue(keptHeld.dice[0].isHeld)
        assertEquals(listOf(1, 5, 6, 6, 6), keptHeld.heldValues())

        // The upper boxes take every matching die and the best of the rest; a straight and a full house are found among seven.
        assertEquals(12, score(HandCompletion.bestCompletion(rolledStud(4, 4, 4, 1, 2, 3, 6), ScoreCategory.FOURS)!!, ScoreCategory.FOURS))
        assertEquals(40, score(HandCompletion.bestCompletion(rolledStud(1, 2, 3, 4, 5, 5, 6), ScoreCategory.LARGE_STRAIGHT)!!, ScoreCategory.LARGE_STRAIGHT))
        assertEquals(25, score(HandCompletion.bestCompletion(rolledStud(2, 2, 2, 5, 5, 6, 6), ScoreCategory.FULL_HOUSE)!!, ScoreCategory.FULL_HOUSE))

        // Equal completions take the leftmost dice: nothing is a pair, so every five dice score the same zero in the 5x box.
        val tie = HandCompletion.bestCompletion(rolledStud(1, 2, 3, 4, 5, 6, 1), ScoreCategory.FIVE_OF_A_KIND)!!
        assertEquals(listOf(true, true, true, true, true, false, false), tie.dice.map { it.isHeld })

        // The completed dice take hold slots.
        assertEquals(setOf(0, 1, 2, 3, 4), HandCompletion.bestCompletion(rolledStud(1, 2, 3, 4, 5, 6, 6), ScoreCategory.CHANCE)!!.dice.mapNotNull { it.heldSlot }.toSet())
    }

    @Test
    fun `every open box previews its best completion - with the dice that would do as well as alternatives`() {
        val state = rolledStud(6, 6, 6, 6, 1, 2, 3)
        val hands = HandCompletion.projectedHands(state)
        val player = state.currentPlayer!!
        assertEquals(ScoreCategory.entries.filter { it in player.categories }.toSet(), hands.keys)
        assertEquals(24, ScoreCalculator.scoreFor(player, ScoreCategory.SIXES, hands.getValue(ScoreCategory.SIXES).dice))
        assertTrue(hands.values.all { it.dice.size == 5 })

        // A box already scored is not offered.
        val scored = GameEngine.commitScore(GameEngine.fillHand(state), ScoreCategory.CHANCE).copy(dice = state.dice, phase = TurnPhase.ROLLED)
        assertFalse(ScoreCategory.CHANCE in HandCompletion.projectedHands(scored))
        assertNull(HandCompletion.bestCompletion(scored, ScoreCategory.CHANCE))

        // The three 4s and the leftmost two of the rest; the 3 and the 6 would have done as well.
        val fours = HandCompletion.projectedHands(rolledStud(4, 4, 4, 1, 2, 3, 6)).getValue(ScoreCategory.FOURS)
        assertEquals(setOf(0, 1, 2, 3, 4), fours.used)
        assertEquals(setOf(5, 6), fours.alternatives)
        // A best hand nothing else matches has none.
        val chance = HandCompletion.projectedHands(rolledStud(1, 2, 6, 6, 3, 6, 5)).getValue(ScoreCategory.CHANCE)
        assertEquals(setOf(2, 3, 4, 5, 6), chance.used)
        assertEquals(emptySet(), chance.alternatives)
        // The same faces on other dice are not an alternative: every five dice with both 1s are 1, 1, 2, 2, 2 - one hand to see.
        assertEquals(emptySet(), HandCompletion.projectedHands(rolledStud(1, 1, 2, 2, 2, 2, 2)).getValue(ScoreCategory.ONES).alternatives)
    }

    @Test
    fun `a whole hand - a mode where every die scores - or no roll yet is its own only completion - and a timeout goes in the first box`() {
        val whole = held(rolledStud(1, 2, 3, 4, 5, 6, 6), 0, 1, 2, 3, 4)
        assertEquals(listOf(whole), HandCompletion.completions(whole))

        val standard = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        assertEquals(listOf(standard), HandCompletion.completions(standard))
        assertEquals(ScoreCategory.ONES, HandCompletion.timeoutCategory(standard))

        val fresh = GameEngine.newGame(onePlayer, GameMode.STUD)
        assertEquals(listOf(fresh), HandCompletion.completions(fresh))

        assertEquals(ScoreCategory.ONES, HandCompletion.timeoutCategory(rolledStud(1, 2, 3, 4, 5, 6, 6)))
    }
}
