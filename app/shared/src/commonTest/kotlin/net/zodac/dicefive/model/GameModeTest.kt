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
        val turnOrder = (listOf(ScoreCategory.FIVE_OF_A_KIND).filter { it in mode.categories } + mode.categories.filter { it != ScoreCategory.FIVE_OF_A_KIND })
            .filter { player.isOpen(it) }
            .flatMap { category -> List(mode.scoresPerCategory) { category } }
        for (category in turnOrder) {
            val value = category.number ?: category.wildcardNumbers?.last ?: mode.dieValues.last
            val colour = category.matchingColour ?: mode.dieColours.firstOrNull()
            val dice = List(mode.diceCount) { Die(value = value, colour = colour) }
            // Where only held dice score, the hand is held first - five of the matching dice.
            state = GameEngine.commitScore(GameEngine.fillHand(state.copy(dice = dice, phase = TurnPhase.ROLLED)), category)
        }
        assertTrue(state.isGameOver)
        return state.players.single()
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
    fun `every mode's ceiling is exactly what a perfect game through the engine totals`() {
        // Quickfire's card changes every game and has no 5x to play, and Hit List and D20 have no 5x at all.
        for (mode in GameMode.entries - GameMode.QUICKFIRE - GameMode.HIT_LIST) {
            assertEquals(mode.maxPossibleScore, perfectGame(mode).totalScore, "$mode")
        }

        // Quickfire's is the best of every set of boxes it can switch off: 3 upper enabled out of 6, and 3 lower enabled out of 6 (plus 5x disabled).
        val upperCategories = PlayerState.UPPER_CATEGORIES
        val lowerCategories = ScoreCategory.entries.filter { it.section == ScoreSection.LOWER && it != ScoreCategory.FIVE_OF_A_KIND }
        var best = 0
        for (upperMask in 0 until (1 shl upperCategories.size)) {
            if (upperMask.countOneBits() != 3) continue
            val enabledUpper = upperCategories.filterIndexed { index, _ -> (upperMask shr index) and 1 == 1 }
            for (lowerMask in 0 until (1 shl lowerCategories.size)) {
                if (lowerMask.countOneBits() != 3) continue
                val enabledLower = lowerCategories.filterIndexed { index, _ -> (lowerMask shr index) and 1 == 1 }
                val enabled = enabledUpper + enabledLower
                var state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Perfect")), GameMode.QUICKFIRE)
                val switchedOff = GameMode.QUICKFIRE.categories.toSet() - enabled.toSet()
                state = state.copy(disabledCategories = switchedOff, players = state.players.map { it.copy(disabledCategories = switchedOff) })
                for (category in enabled) state = GameEngine.commitScore(state.copy(dice = bestHand(category), phase = TurnPhase.ROLLED), category)
                assertTrue(state.isGameOver)
                best = maxOf(best, state.players.single().totalScore)
            }
        }
        assertEquals(GameMode.QUICKFIRE.maxPossibleScore, best)

        // Standard's is 1575, Quickfire's 210, Tricolour's 2120 and Third Wind's 4725 - exactly three of Standard's.
        assertEquals(1575, GameMode.STANDARD.maxPossibleScore)
        assertEquals(210, GameMode.QUICKFIRE.maxPossibleScore)
        assertEquals(1575, GameMode.STUD.maxPossibleScore)
        assertEquals(2120, GameMode.TRICOLOUR.maxPossibleScore)
        assertEquals(3 * GameMode.STANDARD.maxPossibleScore, GameMode.THIRD_WIND.maxPossibleScore)
        assertEquals(4725, GameMode.HIGHEST_POSSIBLE_SCORE)
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
        assertEquals(110, GameMode.D20.maxRollsPerGame)
    }

    @Test
    fun `D20 is twenty-sided dice and the numbers 1 to 20 in two halves - each with a bonus - five dice and five rolls`() {
        val d20 = GameMode.D20
        assertEquals(1..20, d20.dieValues)
        assertEquals(5, d20.diceCount)
        assertEquals(5, d20.rollsPerTurn)
        assertEquals(ScoreCategory.D20_NUMBERS + listOf(ScoreCategory.D20_WILD_LOW, ScoreCategory.D20_WILD_HIGH), d20.categories)
        assertEquals((1..20).toList(), d20.categories.mapNotNull { it.number })
        assertEquals(22, d20.turnsPerGame)
        assertEquals(1350, d20.maxPossibleScore)
        assertFalse(d20.allowsExtendedScores)
        assertFalse(d20.hasStandardCard)
        assertEquals(listOf(ScoreSection.FIRST_HALF to 138, ScoreSection.SECOND_HALF to 388), d20.sectionBonuses.map { it.section to it.threshold })
        assertEquals(listOf(75, 75), d20.sectionBonuses.map { it.amount })
        // Each half is ten numbers and a wildcard, and every other card is Standard's or has none of these.
        assertEquals(11, d20.categories.count { it.section == ScoreSection.FIRST_HALF })
        assertEquals(11, d20.categories.count { it.section == ScoreSection.SECOND_HALF })
        assertEquals(1..10, ScoreCategory.D20_WILD_LOW.wildcardNumbers)
        assertEquals(11..20, ScoreCategory.D20_WILD_HIGH.wildcardNumbers)
        for (mode in GameMode.entries - d20) assertTrue(mode.sectionBonuses.isEmpty(), "$mode")
        // It is listed before Hit List, which is the order the setup screen shows the modes in.
        assertTrue(GameMode.entries.indexOf(d20) < GameMode.entries.indexOf(GameMode.HIT_LIST))
    }

    @Test
    fun `each D20 half earns its bonus at its threshold and not before - and both can be earned in one game`() {
        fun player(firstHalf: Int, secondHalf: Int): PlayerState {
            val first = ScoreCategory.D20_NUMBERS.filter { it.section == ScoreSection.FIRST_HALF }
            val second = ScoreCategory.D20_NUMBERS.filter { it.section == ScoreSection.SECOND_HALF }
            // Everything in the half's first box but the rest zero: the total is what matters.
            val card = first.associateWith { listOf(if (it == first.first()) firstHalf else 0) } +
                second.associateWith { listOf(if (it == second.first()) secondHalf else 0) }
            return PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = GameMode.D20, scorecard = card)
        }
        assertEquals(0, player(137, 387).sectionBonusTotal)
        assertEquals(75, player(138, 387).sectionBonusTotal)
        assertEquals(75, player(137, 388).sectionBonusTotal)
        assertEquals(150, player(138, 388).sectionBonusTotal)
        assertEquals(137 + 387, player(137, 387).totalScore)
        assertEquals(138 + 388 + 150, player(138, 388).totalScore)
        assertEquals(138, player(138, 388).firstHalfTotal)
        assertEquals(388, player(138, 388).secondHalfTotal)
    }

    @Test
    fun `a D20 wildcard counts towards its half and so towards that half's bonus`() {
        val card = ScoreCategory.D20_CARD.associateWith { emptyList<Int>() } +
            mapOf(ScoreCategory.D20_1 to listOf(100), ScoreCategory.D20_WILD_LOW to listOf(38), ScoreCategory.D20_11 to listOf(350), ScoreCategory.D20_WILD_HIGH to listOf(37))
        val player = PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = GameMode.D20, scorecard = card)
        // 100 + 38 = 138 and 350 + 37 = 387: the wildcard takes the first half over its threshold, the second a point short.
        assertEquals(138, player.firstHalfTotal)
        assertEquals(387, player.secondHalfTotal)
        assertEquals(75, player.sectionBonusTotal)
        assertEquals(138 + 387 + 75, player.totalScore)
        assertEquals(150, player.copy(scorecard = card + (ScoreCategory.D20_WILD_HIGH to listOf(38))).sectionBonusTotal)
    }

    @Test
    fun `each mode is Standard with its own changes - unique ids - no box listed twice - Standard the default`() {
        val standard = GameMode.STANDARD
        assertEquals(GameMode.entries.size, GameMode.entries.map { it.id }.toSet().size)
        for (mode in GameMode.entries) {
            assertEquals(mode, GameMode.fromId(mode.id))
            assertEquals(mode.categories.distinct(), mode.categories, "$mode")
        }
        assertEquals(GameMode.STANDARD, GameMode.default)

        // Third Wind: every box scored three times, a tripled bonus, and off the Leaderboard.
        val thirdWind = GameMode.THIRD_WIND
        assertEquals(3, thirdWind.scoresPerCategory)
        assertEquals(39, thirdWind.turnsPerGame)
        assertEquals(189, thirdWind.upperBonusThreshold)
        assertEquals(105, thirdWind.upperBonusAmount)
        assertEquals(standard.fiveOfAKindBonusAmount, thirdWind.fiveOfAKindBonusAmount)
        assertEquals(standard.categories, thirdWind.categories)
        assertEquals(standard.diceCount, thirdWind.diceCount)
        assertEquals(standard.rollsPerTurn, thirdWind.rollsPerTurn)
        // Every other mode scores each box once.
        for (mode in GameMode.entries - thirdWind) {
            assertEquals(1, mode.scoresPerCategory, "$mode")
            assertEquals(mode.categories.size - mode.disabledCategoryCount, mode.turnsPerGame, "$mode")
        }
        // Only these keep their scores off the Leaderboard.
        assertEquals(listOf(GameMode.THIRD_WIND, GameMode.STUD, GameMode.D20, GameMode.HIT_LIST, GameMode.QUICKFIRE), GameMode.entries.filter { !it.countsOnLeaderboard })

        // Tricolour: the four colour boxes, played with coloured dice.
        assertEquals(standard.categories + listOf(ScoreCategory.REDS, ScoreCategory.YELLOWS, ScoreCategory.BLUES, ScoreCategory.COLOURED_HOUSE), GameMode.TRICOLOUR.categories)
        assertEquals(listOf(DieColour.RED, DieColour.YELLOW, DieColour.BLUE), GameMode.TRICOLOUR.dieColours)
        assertTrue(ScoreCategory.COLOURED_HOUSE.jokerFreeFill)

        // Quickfire: 5x and six random boxes switched off.
        val quickfire = GameMode.QUICKFIRE
        assertEquals(setOf(ScoreCategory.FIVE_OF_A_KIND), quickfire.disabledCategories)
        assertEquals(6, quickfire.randomDisabledCategories)
        assertEquals(7, quickfire.disabledCategoryCount)
        assertEquals(6, quickfire.turnsPerGame)

        // Stud: seven dice rolled, and five of them held to score - every other mode scores every die it rolls.
        val stud = GameMode.STUD
        assertEquals(7, stud.diceCount)
        assertEquals(5, stud.scoringDiceCount)
        assertTrue(stud.scoresHeldDiceOnly)
        for (mode in GameMode.entries - stud) {
            assertEquals(mode.diceCount, mode.scoringDiceCount, "$mode")
            assertFalse(mode.scoresHeldDiceOnly, "$mode")
        }

        // Quickfire and Stud keep everything else of Standard's.
        for (mode in listOf(quickfire, stud)) {
            assertEquals(standard.rollsPerTurn, mode.rollsPerTurn, "$mode")
            assertEquals(standard.categories, mode.categories, "$mode")
            assertEquals(standard.dieValues, mode.dieValues, "$mode")
            assertEquals(standard.upperBonusThreshold, mode.upperBonusThreshold, "$mode")
            assertEquals(standard.upperBonusAmount, mode.upperBonusAmount, "$mode")
            assertEquals(standard.fiveOfAKindBonusAmount, mode.fiveOfAKindBonusAmount, "$mode")
        }
        assertEquals(standard.diceCount, quickfire.diceCount)
    }

    @Test
    fun `Quickfire switches off 5x and six others each game - a disabled box is never open - and the upper bonus scales to the rest`() {
        // Every game switches off 5x and six others, the same on every card, and a seed repeats it.
        val players = listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "A"), PlayerConfig(slot = 2, type = PlayerType.AI, name = "B"))
        val seen = mutableSetOf<Set<ScoreCategory>>()
        for (seed in 1..40) {
            val game = GameEngine.newGame(players, GameMode.QUICKFIRE, random = Random(seed))
            assertEquals(7, game.disabledCategories.size)
            assertEquals(3, game.disabledCategories.count { it.section == ScoreSection.UPPER })
            assertEquals(4, game.disabledCategories.count { it.section == ScoreSection.LOWER })
            assertTrue(ScoreCategory.FIVE_OF_A_KIND in game.disabledCategories)
            assertTrue(game.players.all { it.disabledCategories == game.disabledCategories })
            assertTrue(game.players.all { it.turnsPerGame == 6 && it.turnsLeft == 6 })
            seen += game.disabledCategories
            assertEquals(game.disabledCategories, GameEngine.newGame(players, GameMode.QUICKFIRE, random = Random(seed)).disabledCategories)
        }
        assertTrue(seen.size > 10, "the six should vary game to game, saw ${seen.size} sets")

        // No other mode switches anything off - or draws from the random to say so.
        for (mode in GameMode.entries - GameMode.QUICKFIRE) {
            assertEquals(emptySet(), mode.disabledCategories, "$mode")
            assertEquals(0, mode.randomDisabledCategories, "$mode")
        }
        val random = Random(5)
        GameMode.STANDARD.drawDisabledCategories(random)
        assertEquals(Random(5).nextInt(), random.nextInt())

        // A disabled box is never open, never offered for a roll, and never a turn.
        val player = PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = GameMode.QUICKFIRE, disabledCategories = setOf(ScoreCategory.FIVE_OF_A_KIND, ScoreCategory.THREES))
        assertFalse(player.isOpen(ScoreCategory.THREES))
        assertTrue(player.isDisabled(ScoreCategory.THREES))
        assertTrue(player.isOpen(ScoreCategory.FOURS))
        assertFalse(ScoreCategory.THREES in ScoreCalculator.availableCategories(player, List(5) { Die(value = 3) }))
        // Five 3s are a 5x with the box off, so they go where they can - there's no joker either.
        assertFalse(player.fiveOfAKindJokerActive)
        assertEquals(11, player.turnsPerGame)
        assertEquals(11, player.turnsLeft)

        // The game is over once its six boxes are scored.
        var state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "P")), GameMode.QUICKFIRE, random = Random(3))
        repeat(6) {
            assertFalse(state.isGameOver)
            state = GameEngine.rollDice(state, Random(it))
            state = GameEngine.commitScore(state, ScoreCalculator.availableCategories(state.players.single(), state.dice).first())
        }
        assertTrue(state.isGameOver)
        assertEquals(6, state.players.single().turnsTaken)
        assertTrue(state.players.single().disabledCategories.all { state.players.single().scoresIn(it).isEmpty() })

        // The upper bonus is scaled to the upper boxes still on - three of each number.
        fun upperPlayer(vararg off: ScoreCategory, upper: Map<ScoreCategory, Int>) = PlayerState(
            name = "P",
            type = PlayerType.HUMAN,
            gameMode = GameMode.QUICKFIRE,
            disabledCategories = off.toSet(),
            scorecard = oneScoreEach(GameMode.QUICKFIRE.categories.associateWith { upper[it] }),
        )
        // 3s off: 63 - 9.
        val noThrees = upperPlayer(ScoreCategory.THREES, upper = mapOf(ScoreCategory.ONES to 3, ScoreCategory.TWOS to 6, ScoreCategory.FOURS to 12, ScoreCategory.FIVES to 15, ScoreCategory.SIXES to 18))
        assertEquals(54, noThrees.upperBonusThreshold)
        assertEquals(54, noThrees.upperSectionTotal)
        assertEquals(35, noThrees.upperSectionBonus)
        assertEquals(0, noThrees.copy(scorecard = noThrees.scorecard + (ScoreCategory.SIXES to listOf(17))).upperSectionBonus)
        // Only the 6s left: 18.
        val onlySixes = upperPlayer(ScoreCategory.ONES, ScoreCategory.TWOS, ScoreCategory.THREES, ScoreCategory.FOURS, ScoreCategory.FIVES, upper = mapOf(ScoreCategory.SIXES to 18))
        assertEquals(18, onlySixes.upperBonusThreshold)
        assertEquals(35, onlySixes.upperSectionBonus)
        // Nothing left in the section: no bonus to earn, even though 0 reaches a threshold of 0.
        val noUpper = upperPlayer(*PlayerState.UPPER_CATEGORIES.toTypedArray(), upper = emptyMap())
        assertEquals(0, noUpper.upperBonusThreshold)
        assertFalse(noUpper.hasUpperBonus)
        assertEquals(0, noUpper.upperSectionBonus)
        // Nothing off: Standard's 63, and Third Wind's 189.
        assertEquals(63, PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = GameMode.QUICKFIRE).upperBonusThreshold)
        assertEquals(189, PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = GameMode.THIRD_WIND).upperBonusThreshold)
    }

    @Test
    fun `a new player's scorecard holds exactly their mode's boxes all open - and colour boxes add to the total but not the lower section`() {
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

        val mode = GameMode.TRICOLOUR
        val player = PlayerState(
            name = "P",
            type = PlayerType.HUMAN,
            gameMode = mode,
            scorecard = oneScoreEach(mode.categories.associateWith { null } + mapOf(ScoreCategory.CHANCE to 20, ScoreCategory.REDS to 40, ScoreCategory.COLOURED_HOUSE to 25)),
        )
        assertEquals(20, player.lowerSectionTotal)
        assertEquals(65, player.colourSectionTotal)
        assertEquals(85, player.totalScore)
    }

    @Test
    fun `only Standard's own card is played by the perfect-play table - Extended Scores' boxes put a game off it`() {
        assertEquals(listOf(GameMode.STANDARD), GameMode.entries.filter { it.hasPerfectPlayTable })
        assertTrue(GameMode.STANDARD.playsByPerfectPlayTable(extendedScores = false))
        assertFalse(GameMode.STANDARD.playsByPerfectPlayTable(extendedScores = true))
        assertFalse(GameMode.TRICOLOUR.playsByPerfectPlayTable(extendedScores = false))
    }
}
