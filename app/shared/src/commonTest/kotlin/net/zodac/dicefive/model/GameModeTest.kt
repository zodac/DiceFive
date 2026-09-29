package net.zodac.dicefive.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.game.ScoreCalculator

class GameModeTest {

    /**
     * Plays [mode]'s "perfect game" through the real engine: every turn is a 5x of sixes (or of the
     * matching number, for an upper box), the 5x box goes first so every later turn earns the bonus
     * chip, and each colour box's 5x is also all that box's colour. Nothing is hand-totalled, so a
     * change to the joker rule, a bonus or a category's score that moved the ceiling shows up here.
     */
    private fun perfectGame(mode: GameMode): PlayerState {
        var state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Perfect")), mode)
        val turnOrder = listOf(ScoreCategory.FIVE_OF_A_KIND) + mode.categories.filter { it != ScoreCategory.FIVE_OF_A_KIND }
        for (category in turnOrder) {
            val upperValue = PlayerState.UPPER_CATEGORIES.indexOf(category) + 1
            val value = if (upperValue > 0) upperValue else mode.dieValues.last
            val colour = category.matchingColour ?: mode.dieColours.firstOrNull()
            val dice = List(mode.diceCount) { Die(value = value, colour = colour) }
            state = GameEngine.commitScore(state.copy(dice = dice, phase = TurnPhase.ROLLED), category)
        }
        assertTrue(state.isGameOver)
        return state.players.single()
    }

    @Test
    fun `every mode's max possible score is exactly what a perfect game through the engine totals`() {
        for (mode in GameMode.entries) {
            assertEquals(mode.maxPossibleScore, perfectGame(mode).totalScore, "$mode")
        }
    }

    @Test
    fun `Standard's ceiling is 1575 - as is Quickfire's - and Tricolour's is 2120`() {
        assertEquals(1575, GameMode.STANDARD.maxPossibleScore)
        assertEquals(1575, GameMode.QUICKFIRE.maxPossibleScore)
        assertEquals(2120, GameMode.TRICOLOUR.maxPossibleScore)
        assertEquals(2120, GameMode.HIGHEST_POSSIBLE_SCORE)
    }

    @Test
    fun `Tricolour is Standard plus the four colour boxes - played with coloured dice`() {
        assertEquals(
            GameMode.STANDARD.categories + listOf(
                ScoreCategory.REDS,
                ScoreCategory.YELLOWS,
                ScoreCategory.BLUES,
                ScoreCategory.COLOURED_HOUSE,
            ),
            GameMode.TRICOLOUR.categories,
        )
        assertEquals(listOf(DieColour.RED, DieColour.YELLOW, DieColour.BLUE), GameMode.TRICOLOUR.dieColours)
        assertFalse(GameMode.TRICOLOUR.usesPlayerDiceStyle)
        assertTrue(GameMode.STANDARD.usesPlayerDiceStyle)
        assertTrue(ScoreCategory.COLOURED_HOUSE.jokerFreeFill)
    }

    @Test
    fun `Quickfire is Standard with one roll per turn and its own 10 second timer`() {
        val quickfire = GameMode.QUICKFIRE
        val standard = GameMode.STANDARD
        assertEquals(1, quickfire.rollsPerTurn)
        assertEquals(10, quickfire.turnTimerSeconds)
        assertEquals(standard.categories, quickfire.categories)
        assertEquals(standard.diceCount, quickfire.diceCount)
        assertEquals(standard.dieValues, quickfire.dieValues)
        assertEquals(standard.upperBonusThreshold, quickfire.upperBonusThreshold)
        assertEquals(standard.upperBonusAmount, quickfire.upperBonusAmount)
        assertEquals(standard.fiveOfAKindBonusAmount, quickfire.fiveOfAKindBonusAmount)
        assertTrue(quickfire.usesPlayerDiceStyle)
    }

    @Test
    fun `a mode's own timer overrides the setup pick - otherwise the pick stands`() {
        assertEquals(10, GameState(gameMode = GameMode.QUICKFIRE, turnTimer = TurnTimer.SECONDS_60).turnSeconds)
        assertEquals(60, GameState(gameMode = GameMode.STANDARD, turnTimer = TurnTimer.SECONDS_60).turnSeconds)
        assertNull(GameState(gameMode = GameMode.TRICOLOUR).turnSeconds)
    }

    @Test
    fun `a Quickfire turn is one roll - then the cup is empty`() {
        var state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "P")), GameMode.QUICKFIRE)
        assertEquals(1, state.rollsRemaining)
        state = GameEngine.rollDice(state, Random(1))
        assertEquals(0, state.rollsRemaining)
        state = GameEngine.commitScore(state, ScoreCategory.CHANCE)
        assertEquals(1, state.rollsRemaining)
    }

    @Test
    fun `no mode's scorecard lists a category twice`() {
        for (mode in GameMode.entries) {
            assertEquals(mode.categories.distinct(), mode.categories, "$mode")
        }
    }

    @Test
    fun `ids are unique and round trip - and Standard is the default`() {
        assertEquals(GameMode.entries.size, GameMode.entries.map { it.id }.toSet().size)
        for (mode in GameMode.entries) {
            assertEquals(mode, GameMode.fromId(mode.id))
        }
        assertEquals(GameMode.STANDARD, GameMode.default)
    }

    @Test
    fun `a new player's scorecard holds exactly their mode's categories - all open`() {
        for (mode in GameMode.entries) {
            val player = PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = mode)
            assertEquals(mode.categories, player.scorecard.keys.toList())
            assertTrue(player.scorecard.values.all { it == null })
            assertEquals(mode.categories, ScoreCalculator.availableCategories(player, List(mode.diceCount) { Die() }))
        }
    }

    @Test
    fun `colour boxes add to the total but not to the lower section`() {
        val mode = GameMode.TRICOLOUR
        val player = PlayerState(
            name = "P",
            type = PlayerType.HUMAN,
            gameMode = mode,
            scorecard = mode.categories.associateWith { null } + mapOf(
                ScoreCategory.CHANCE to 20,
                ScoreCategory.REDS to 40,
                ScoreCategory.COLOURED_HOUSE to 25,
            ),
        )

        assertEquals(20, player.lowerSectionTotal)
        assertEquals(65, player.colourSectionTotal)
        assertEquals(85, player.totalScore)
    }
}
