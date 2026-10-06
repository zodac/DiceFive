package net.zodac.dicefive.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.game.ScoreCalculator
import net.zodac.dicefive.oneScoreEach

class GameModeTest {

    /**
     * Plays [mode]'s "perfect game" through the real engine: every turn is a 5x of sixes (or of the
     * matching number, for an upper box), the 5x box goes first so every later turn earns the bonus
     * chip, and each colour box's 5x is also all that box's colour. Nothing is hand-totalled, so a
     * change to the joker rule, a bonus or a category's score that moved the ceiling shows up here.
     */
    private fun perfectGame(mode: GameMode): PlayerState {
        var state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Perfect")), mode)
        // Every slot of the 5x box first, so every later 5x is a joker; then each box's slots in turn.
        // Boxes the game switched off aren't played.
        val player = state.players.single()
        val turnOrder = (listOf(ScoreCategory.FIVE_OF_A_KIND) + mode.categories.filter { it != ScoreCategory.FIVE_OF_A_KIND })
            .filter { player.isOpen(it) }
            .flatMap { category -> List(mode.scoresPerCategory) { category } }
        for (category in turnOrder) {
            val upperValue = PlayerState.UPPER_CATEGORIES.indexOf(category) + 1
            val value = if (upperValue > 0) upperValue else mode.dieValues.last
            val colour = category.matchingColour ?: mode.dieColours.firstOrNull()
            val dice = List(mode.diceCount) { Die(value = value, colour = colour) }
            // Where only held dice score, the hand is held first - five of the matching dice.
            state = GameEngine.commitScore(GameEngine.fillHand(state.copy(dice = dice, phase = TurnPhase.ROLLED)), category)
        }
        assertTrue(state.isGameOver)
        return state.players.single()
    }

    @Test
    fun `every mode's max possible score is exactly what a perfect game through the engine totals`() {
        // Quickfire's card changes every game and has no 5x to play, and Hit List has no 5x at all, so each has its own test below.
        for (mode in GameMode.entries - GameMode.QUICKFIRE - GameMode.HIT_LIST) {
            assertEquals(mode.maxPossibleScore, perfectGame(mode).totalScore, "$mode")
        }
    }

    /** The dice that score [category] the most it can without a 5x: five of its number, a straight, a full house of 6s and 5s. */
    private fun bestHand(category: ScoreCategory): List<Die> {
        val values = when (category) {
            ScoreCategory.FULL_HOUSE -> listOf(6, 6, 6, 5, 5)
            ScoreCategory.SMALL_STRAIGHT, ScoreCategory.LARGE_STRAIGHT -> listOf(1, 2, 3, 4, 5)
            ScoreCategory.THREE_OF_A_KIND, ScoreCategory.FOUR_OF_A_KIND, ScoreCategory.CHANCE -> listOf(6, 6, 6, 6, 6)
            else -> List(5) { PlayerState.UPPER_CATEGORIES.indexOf(category) + 1 }
        }
        return values.map { Die(value = it) }
    }

    @Test
    fun `Quickfire's max possible score is the best of every set of boxes it can switch off - played through the engine`() {
        val others = GameMode.QUICKFIRE.categories - GameMode.QUICKFIRE.disabledCategories
        var best = 0
        // Every way to leave six of the twelve boxes on.
        for (mask in 0 until (1 shl others.size)) {
            if (mask.countOneBits() != GameMode.QUICKFIRE.turnsPerGame) continue
            val enabled = others.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }
            var state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Perfect")), GameMode.QUICKFIRE)
            val switchedOff = GameMode.QUICKFIRE.categories.toSet() - enabled.toSet()
            state = state.copy(
                disabledCategories = switchedOff,
                players = state.players.map { it.copy(disabledCategories = switchedOff) },
            )
            for (category in enabled) {
                state = GameEngine.commitScore(state.copy(dice = bestHand(category), phase = TurnPhase.ROLLED), category)
            }
            assertTrue(state.isGameOver)
            best = maxOf(best, state.players.single().totalScore)
        }
        assertEquals(GameMode.QUICKFIRE.maxPossibleScore, best)
    }

    @Test
    fun `every mode's max rolls is exactly what a game using every roll through the engine counts`() {
        for (mode in GameMode.entries) {
            var state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Roller")), mode)
            while (!state.isGameOver) {
                while (state.rollsRemaining > 0) state = GameEngine.rollDice(state, Random(1))
                val player = state.players.single()
                state = GameEngine.commitScore(GameEngine.fillHand(state), player.categories.first { player.isOpen(it) })
            }
            assertEquals(mode.maxRollsPerGame, state.players.single().rollCount, "$mode")
        }
        assertEquals(39, GameMode.STANDARD.maxRollsPerGame)
        assertEquals(51, GameMode.TRICOLOUR.maxRollsPerGame)
        assertEquals(18, GameMode.QUICKFIRE.maxRollsPerGame)
        assertEquals(39, GameMode.STUD.maxRollsPerGame)
        assertEquals(117, GameMode.THIRD_WIND.maxRollsPerGame)
    }

    @Test
    fun `Standard's ceiling is 1575 - Quickfire's 225 - Tricolour's 2120 and Third Wind's 4725`() {
        assertEquals(1575, GameMode.STANDARD.maxPossibleScore)
        assertEquals(225, GameMode.QUICKFIRE.maxPossibleScore)
        assertEquals(1575, GameMode.STUD.maxPossibleScore)
        assertEquals(2120, GameMode.TRICOLOUR.maxPossibleScore)
        // Exactly three of Standard's perfect game.
        assertEquals(3 * GameMode.STANDARD.maxPossibleScore, GameMode.THIRD_WIND.maxPossibleScore)
        assertEquals(4725, GameMode.HIGHEST_POSSIBLE_SCORE)
    }

    @Test
    fun `Third Wind is Standard with every box scored three times - a tripled bonus - and off the Leaderboard`() {
        val thirdWind = GameMode.THIRD_WIND
        val standard = GameMode.STANDARD
        assertEquals(3, thirdWind.scoresPerCategory)
        assertEquals(39, thirdWind.turnsPerGame)
        assertEquals(189, thirdWind.upperBonusThreshold)
        assertEquals(105, thirdWind.upperBonusAmount)
        assertEquals(standard.fiveOfAKindBonusAmount, thirdWind.fiveOfAKindBonusAmount)
        assertEquals(standard.categories, thirdWind.categories)
        assertEquals(standard.diceCount, thirdWind.diceCount)
        assertEquals(standard.rollsPerTurn, thirdWind.rollsPerTurn)
        assertFalse(thirdWind.countsOnLeaderboard)
        // Every other mode scores each box once and goes on the Leaderboard.
        for (mode in GameMode.entries - thirdWind) {
            assertEquals(1, mode.scoresPerCategory, "$mode")
            assertEquals(mode.categories.size - mode.disabledCategoryCount, mode.turnsPerGame, "$mode")
        }
        // Only these keep their scores off the Leaderboard.
        assertEquals(listOf(GameMode.QUICKFIRE, GameMode.THIRD_WIND, GameMode.HIT_LIST), GameMode.entries.filter { !it.countsOnLeaderboard })
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
        assertTrue(ScoreCategory.COLOURED_HOUSE.jokerFreeFill)
    }

    @Test
    fun `Quickfire is Standard with 5x and six random boxes switched off`() {
        val quickfire = GameMode.QUICKFIRE
        val standard = GameMode.STANDARD
        assertEquals(setOf(ScoreCategory.FIVE_OF_A_KIND), quickfire.disabledCategories)
        assertEquals(6, quickfire.randomDisabledCategories)
        assertEquals(7, quickfire.disabledCategoryCount)
        assertEquals(6, quickfire.turnsPerGame)
        assertEquals(standard.rollsPerTurn, quickfire.rollsPerTurn)
        assertEquals(standard.categories, quickfire.categories)
        assertEquals(standard.diceCount, quickfire.diceCount)
        assertEquals(standard.dieValues, quickfire.dieValues)
        assertEquals(standard.upperBonusThreshold, quickfire.upperBonusThreshold)
        assertEquals(standard.upperBonusAmount, quickfire.upperBonusAmount)
        assertEquals(standard.fiveOfAKindBonusAmount, quickfire.fiveOfAKindBonusAmount)
        assertFalse(quickfire.countsOnLeaderboard)
    }

    @Test
    fun `every game of Quickfire switches off 5x and six others - the same on every card - and a seed repeats it`() {
        val players = listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "A"), PlayerConfig(slot = 2, type = PlayerType.AI, name = "B"))
        val seen = mutableSetOf<Set<ScoreCategory>>()
        for (seed in 1..40) {
            val game = GameEngine.newGame(players, GameMode.QUICKFIRE, random = Random(seed))
            assertEquals(7, game.disabledCategories.size)
            assertTrue(ScoreCategory.FIVE_OF_A_KIND in game.disabledCategories)
            assertTrue(game.players.all { it.disabledCategories == game.disabledCategories })
            assertTrue(game.players.all { it.turnsPerGame == 6 && it.turnsLeft == 6 })
            seen += game.disabledCategories
            assertEquals(game.disabledCategories, GameEngine.newGame(players, GameMode.QUICKFIRE, random = Random(seed)).disabledCategories)
        }
        assertTrue(seen.size > 10, "the six should vary game to game, saw ${seen.size} sets")
    }

    @Test
    fun `no other mode switches anything off - or draws from the random to say so`() {
        for (mode in GameMode.entries - GameMode.QUICKFIRE) {
            assertEquals(emptySet(), mode.disabledCategories, "$mode")
            assertEquals(0, mode.randomDisabledCategories, "$mode")
        }
        val random = Random(5)
        GameMode.STANDARD.drawDisabledCategories(random)
        assertEquals(Random(5).nextInt(), random.nextInt())
    }

    @Test
    fun `a disabled box is never open - never offered for a roll - and never a turn`() {
        val player = PlayerState(
            name = "P",
            type = PlayerType.HUMAN,
            gameMode = GameMode.QUICKFIRE,
            disabledCategories = setOf(ScoreCategory.FIVE_OF_A_KIND, ScoreCategory.THREES),
        )
        assertFalse(player.isOpen(ScoreCategory.THREES))
        assertTrue(player.isDisabled(ScoreCategory.THREES))
        assertTrue(player.isOpen(ScoreCategory.FOURS))
        assertFalse(ScoreCategory.THREES in ScoreCalculator.availableCategories(player, List(5) { Die(value = 3) }))
        // Five 3s are a 5x with the box off, so they go where they can - there's no joker either.
        assertFalse(player.fiveOfAKindJokerActive)
        assertEquals(11, player.turnsPerGame)
        assertEquals(11, player.turnsLeft)
    }

    @Test
    fun `the upper bonus is scaled to the upper boxes still on - three of each number`() {
        fun player(vararg off: ScoreCategory, upper: Map<ScoreCategory, Int>) = PlayerState(
            name = "P",
            type = PlayerType.HUMAN,
            gameMode = GameMode.QUICKFIRE,
            disabledCategories = off.toSet(),
            scorecard = oneScoreEach(GameMode.QUICKFIRE.categories.associateWith { upper[it] }),
        )
        // 3s off: 63 - 9.
        val noThrees = player(ScoreCategory.THREES, upper = mapOf(ScoreCategory.ONES to 3, ScoreCategory.TWOS to 6, ScoreCategory.FOURS to 12, ScoreCategory.FIVES to 15, ScoreCategory.SIXES to 18))
        assertEquals(54, noThrees.upperBonusThreshold)
        assertEquals(54, noThrees.upperSectionTotal)
        assertEquals(35, noThrees.upperSectionBonus)
        assertEquals(0, noThrees.copy(scorecard = noThrees.scorecard + (ScoreCategory.SIXES to listOf(17))).upperSectionBonus)
        // Only the 6s left: 18.
        val onlySixes = player(
            ScoreCategory.ONES, ScoreCategory.TWOS, ScoreCategory.THREES, ScoreCategory.FOURS, ScoreCategory.FIVES,
            upper = mapOf(ScoreCategory.SIXES to 18),
        )
        assertEquals(18, onlySixes.upperBonusThreshold)
        assertEquals(35, onlySixes.upperSectionBonus)
        // Nothing left in the section: no bonus to earn, even though 0 reaches a threshold of 0.
        val noUpper = player(*PlayerState.UPPER_CATEGORIES.toTypedArray(), upper = emptyMap())
        assertEquals(0, noUpper.upperBonusThreshold)
        assertFalse(noUpper.hasUpperBonus)
        assertEquals(0, noUpper.upperSectionBonus)
        // Nothing off: Standard's 63, and Third Wind's 189.
        assertEquals(63, PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = GameMode.QUICKFIRE).upperBonusThreshold)
        assertEquals(189, PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = GameMode.THIRD_WIND).upperBonusThreshold)
    }

    @Test
    fun `a Quickfire game is over once its six boxes are scored`() {
        var state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "P")), GameMode.QUICKFIRE, random = Random(3))
        repeat(6) {
            assertFalse(state.isGameOver)
            state = GameEngine.rollDice(state, Random(it))
            val player = state.players.single()
            state = GameEngine.commitScore(state, ScoreCalculator.availableCategories(player, state.dice).first())
        }
        assertTrue(state.isGameOver)
        assertEquals(6, state.players.single().turnsTaken)
        assertTrue(state.players.single().disabledCategories.all { state.players.single().scoresIn(it).isEmpty() })
    }

    @Test
    fun `Stud is Standard with seven dice rolled - and five of them held to score`() {
        val stud = GameMode.STUD
        val standard = GameMode.STANDARD
        assertEquals(7, stud.diceCount)
        assertEquals(5, stud.scoringDiceCount)
        assertTrue(stud.scoresHeldDiceOnly)
        assertEquals(standard.rollsPerTurn, stud.rollsPerTurn)
        assertEquals(standard.categories, stud.categories)
        assertEquals(standard.dieValues, stud.dieValues)
        assertEquals(standard.upperBonusThreshold, stud.upperBonusThreshold)
        assertEquals(standard.upperBonusAmount, stud.upperBonusAmount)
        assertEquals(standard.fiveOfAKindBonusAmount, stud.fiveOfAKindBonusAmount)
    }

    @Test
    fun `every other mode scores every die it rolls`() {
        for (mode in GameMode.entries - GameMode.STUD) {
            assertEquals(mode.diceCount, mode.scoringDiceCount, "$mode")
            assertFalse(mode.scoresHeldDiceOnly, "$mode")
        }
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
            assertTrue(player.turnsTaken == 0)
            assertEquals(mode.categories, ScoreCalculator.availableCategories(player, List(mode.diceCount) { Die() }))
            // A new game's card is the same, less whatever the mode switched off.
            val game = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "P")), mode, random = Random(1))
            val dealt = game.players.single()
            assertEquals(mode.categories, dealt.scorecard.keys.toList())
            assertEquals(mode.categories - game.disabledCategories, ScoreCalculator.availableCategories(dealt, List(mode.diceCount) { Die() }))
        }
    }

    @Test
    fun `colour boxes add to the total but not to the lower section`() {
        val mode = GameMode.TRICOLOUR
        val player = PlayerState(
            name = "P",
            type = PlayerType.HUMAN,
            gameMode = mode,
            scorecard = oneScoreEach(
                mode.categories.associateWith { null } + mapOf(
                    ScoreCategory.CHANCE to 20,
                    ScoreCategory.REDS to 40,
                    ScoreCategory.COLOURED_HOUSE to 25,
                ),
            ),
        )

        assertEquals(20, player.lowerSectionTotal)
        assertEquals(65, player.colourSectionTotal)
        assertEquals(85, player.totalScore)
    }
}
