package net.zodac.dicefive.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory

/** [HandScoring] mirrors [ScoreCalculator] - which boxes a hand may go in, what it scores there and any 5x chip - in every mode. */
class HandScoringTest {

    @Test
    fun `HandScoring agrees with ScoreCalculator on random scorecards and hands in every mode`() {
        val random = Random(1)
        for (mode in GameMode.entries) {
            val faces = mode.dieValues.flatMap { value ->
                if (mode.dieColours.isEmpty()) listOf(Die(value = value)) else mode.dieColours.map { Die(value = value, colour = it) }
            }
            val scoring = HandScoring(mode, DiceSpace(faces, mode.diceCount))
            val fiveOfAKind = mode.categories.indexOf(ScoreCategory.FIVE_OF_A_KIND)
            repeat(CASES_PER_MODE) {
                val filled = random.nextInt(0, (1 shl mode.categories.size) - 1)
                // Joker cases are rare by chance alone, so half the hands are made five of a kind.
                val hand = if (random.nextBoolean()) {
                    scoring.space.handOf(List(mode.diceCount) { faces[random.nextInt(faces.size)] })
                } else {
                    val value = mode.dieValues.random(random)
                    scoring.space.handOf(List(mode.diceCount) { faces.filter { it.value == value }.random(random) })
                }
                val base = PlayerState(name = "Bot", type = PlayerType.AI, gameMode = mode)
                val scorecard = base.scorecard.toMutableMap()
                for ((index, category) in mode.categories.withIndex()) {
                    if ((filled shr index) and 1 == 1) {
                        scorecard[category] = if (index == fiveOfAKind && random.nextBoolean()) requireNotNull(category.fixedScore) else 0
                    }
                }
                val player = base.copy(scorecard = scorecard)
                val dice = scoring.space.diceOf(hand)

                val expected = ScoreCalculator.availableCategories(player, dice)
                    .associateWith { ScoreCalculator.scoreFor(player, it, dice) to ScoreCalculator.fiveOfAKindBonusFor(player, dice) }
                val actual = HashMap<ScoreCategory, Pair<Int, Int>>()
                scoring.forEachLegal(hand, scoring.filledMask(player), scoring.fiveOfAKindScored(player)) { category, score, chip ->
                    actual[scoring.categories[category]] = score to chip
                }
                assertEquals(expected, actual, "$mode: ${dice.map { it.value to it.colour }} on $scorecard")
            }
        }
    }

    private companion object {
        const val CASES_PER_MODE = 3000
    }
}
