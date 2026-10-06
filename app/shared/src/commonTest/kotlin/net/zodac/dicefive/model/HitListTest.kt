package net.zodac.dicefive.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.game.DiceScoring
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.game.ScoreCalculator

/** Hit List's targets: what hits one, what's exact, the Alibi, and how a game deals them. */
class HitListTest {

    private fun dice(vararg values: Int) = values.map { Die(value = it) }

    /** 4 1 3 2 and an any place, worth 20. */
    private val target = HitTarget(listOf(4, 1, 3, 2, null), points = 20)

    @Test
    fun `every named number among the dice in any order is a hit - each die counting once`() {
        assertTrue(target.isHit(dice(2, 4, 1, 3, 5)))
        assertFalse(target.isHit(dice(4, 1, 3, 5, 5)))
        val pair = HitTarget(listOf(4, null, 4, null, 1), points = 15)
        assertTrue(pair.isHit(dice(4, 6, 1, 4, 2)))
        // One 4 can't be both of the target's.
        assertFalse(pair.isHit(dice(4, 6, 1, 3, 2)))
    }

    @Test
    fun `every named number in its own place is an exact hit - double points - whatever the any places show`() {
        assertEquals(40, target.score(dice(4, 1, 3, 2, 6)))
        assertEquals(40, target.score(dice(4, 1, 3, 2, 2)))
        assertEquals(20, target.score(dice(4, 2, 3, 1, 6)))
        assertEquals(0, target.score(dice(6, 6, 6, 5, 5)))
    }

    @Test
    fun `short of a hit - half the points times the share rolled - rounded to the nearest five`() {
        // 3 of 4 on a 20: 7.5, a half, so up to 10.
        assertEquals(10, target.score(dice(4, 1, 3, 5, 6)))
        assertEquals(3, target.rolledCount(dice(4, 1, 3, 5, 6)))
        // 4 of 5 on a 75: 30.
        val hardest = HitTarget(listOf(4, 6, 2, 6, 4), points = 75)
        assertEquals(30, hardest.score(dice(4, 6, 2, 6, 1)))
        // 2 of 3 on a 15: 5. 1 of 3 on a 10: 1.67, so nothing - and 2 of 3 on a 10: 3.33, so 5.
        assertEquals(5, HitTarget(listOf(4, null, 4, null, 1), points = 15).score(dice(4, 1, 6, 6, 6)))
        assertEquals(0, HitTarget(listOf(2, null, 5, 3, null), points = 10).score(dice(2, 1, 6, 6, 6)))
        assertEquals(5, HitTarget(listOf(2, null, 5, 3, null), points = 10).score(dice(2, 5, 6, 6, 6)))
        // 4 of 5 on a 40: 16, so 15. 3 of 4 on a 30: 11.25, so 10.
        assertEquals(15, HitTarget(listOf(3, 6, 1, 5, 2), points = 40).score(dice(3, 6, 1, 5, 4)))
        assertEquals(10, HitTarget(listOf(1, 5, null, 5, 1), points = 30).score(dice(1, 5, 1, 6, 6)))
        // One number rolled is never enough, whatever the target's worth: 1 of 4 on a 20 and 1 of 5 on a 75 would be 5 and 10.
        assertEquals(0, target.score(dice(4, 6, 6, 6, 6)))
        assertEquals(0, HitTarget(listOf(4, 6, 2, 6, 4), points = 75).score(dice(2, 1, 1, 1, 1)))
        // Two is: 2 of 5 on a 75.
        assertEquals(15, HitTarget(listOf(4, 6, 2, 6, 4), points = 75).score(dice(2, 4, 1, 1, 1)))
        // A partial hit is always less than the target's points, so it's never mistaken for a hit.
        for (shape in GameMode.HIT_LIST.hitListShapes) {
            val drawn = HitTarget.draw(shape, Random(1))
            val oneShort = drawn.called.drop(1).map { Die(value = it) } + List(drawn.places.size - drawn.called.size + 1) { Die(value = 0) }
            assertTrue(drawn.score(oneShort) < drawn.points, "$shape")
            assertEquals(0, drawn.score(oneShort) % HitTarget.PARTIAL_STEP, "$shape")
        }
    }

    @Test
    fun `a hand missing a die can still hit - but never exactly`() {
        // As Unlucky Dice leaves it: the locked die is gone, and with it every place.
        val fourDice = dice(4, 1, 3, 2)
        assertTrue(target.isHit(fourDice))
        assertFalse(target.isExactHit(fourDice))
        assertEquals(20, target.score(fourDice))
    }

    @Test
    fun `each place shows whether its number is rolled and whether it's in place - a die counted once`() {
        assertEquals(
            listOf(PlaceMatch.IN_PLACE, PlaceMatch.ROLLED, PlaceMatch.MISSING, PlaceMatch.ROLLED, PlaceMatch.ANY),
            target.matches(dice(4, 2, 6, 1, 5)),
        )
        // The 4 in place is the target's first; the target's second 4 needs a die of its own.
        val pair = HitTarget(listOf(4, null, 4, null, 1), points = 15)
        assertEquals(
            listOf(PlaceMatch.IN_PLACE, PlaceMatch.ANY, PlaceMatch.MISSING, PlaceMatch.ANY, PlaceMatch.MISSING),
            pair.matches(dice(4, 6, 2, 3, 5)),
        )
    }

    @Test
    fun `a shape has at most two any places - and one with any places is worth less than every one with none`() {
        assertFailsWith<IllegalArgumentException> { TargetShape("ab···", points = 5) }
        val shapes = GameMode.HIT_LIST.hitListShapes
        assertTrue(shapes.all { shape -> shape.pattern.count { it == TargetShape.ANY } <= TargetShape.MAX_ANY_PLACES })
        val withAny = shapes.filter { TargetShape.ANY in it.pattern }
        val allNamed = shapes.filter { TargetShape.ANY !in it.pattern }
        assertTrue(withAny.maxOf { it.points } < allNamed.minOf { it.points })
    }

    @Test
    fun `a drawn target names a different number for each letter of its shape - its places shuffled`() {
        val random = Random(3)
        repeat(50) {
            for (shape in GameMode.HIT_LIST.hitListShapes) {
                val drawn = HitTarget.draw(shape, random)
                assertEquals(shape.points, drawn.points)
                assertEquals(shape.pattern.count { it == TargetShape.ANY }, drawn.places.count { it == null })
                // As many different numbers as letters, each as often as its letter.
                val letterCounts = shape.pattern.filter { it != TargetShape.ANY }.groupingBy { it }.eachCount().values.sorted()
                assertEquals(letterCounts, drawn.called.groupingBy { it }.eachCount().values.sorted())
                assertTrue(drawn.called.all { it in 1..6 })
            }
        }
    }

    @Test
    fun `every game deals twelve different targets - the same on every card - and a seed repeats them`() {
        val players = listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "A"), PlayerConfig(slot = 2, type = PlayerType.AI, name = "B"))
        val seen = mutableSetOf<Map<ScoreCategory, HitTarget>>()
        for (seed in 1..20) {
            val game = GameEngine.newGame(players, GameMode.HIT_LIST, random = Random(seed))
            assertEquals(ScoreCategory.TARGETS, game.hitList.keys.toList())
            assertEquals(12, game.hitList.values.map { it.places }.toSet().size)
            assertTrue(game.players.all { it.hitList == game.hitList })
            assertEquals(game.hitList, GameEngine.newGame(players, GameMode.HIT_LIST, random = Random(seed)).hitList)
            seen += game.hitList
        }
        assertEquals(20, seen.size)
    }

    @Test
    fun `no other mode deals targets - or draws from the random to say so`() {
        for (mode in GameMode.entries - GameMode.HIT_LIST) {
            assertFalse(mode.hasHitList, "$mode")
            assertEquals(emptyMap(), mode.drawHitList(Random(1)), "$mode")
        }
        val random = Random(5)
        GameMode.STANDARD.drawHitList(random)
        assertEquals(Random(5).nextInt(), random.nextInt())
    }

    /** A Hit List card with three known targets - 4 1 3 2 · for 20, 5 · 5 · 1 for 15 and 1 2 3 4 5 for 40 - and [scores] in it. */
    private fun hitListPlayer(scores: Map<ScoreCategory, List<Int>> = emptyMap()): PlayerState {
        val hitList = mapOf(
            ScoreCategory.TARGET_1 to target,
            ScoreCategory.TARGET_2 to HitTarget(listOf(5, null, 5, null, 1), points = 15),
            ScoreCategory.TARGET_3 to HitTarget(listOf(1, 2, 3, 4, 5), points = 40),
        )
        val player = PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = GameMode.HIT_LIST, hitList = hitList)
        return player.copy(scorecard = player.scorecard + scores)
    }

    @Test
    fun `a target scores its own points - double when exact - through the calculator`() {
        val player = hitListPlayer()
        assertEquals(40, ScoreCalculator.scoreFor(player, ScoreCategory.TARGET_1, dice(4, 1, 3, 2, 6)))
        assertEquals(20, ScoreCalculator.scoreFor(player, ScoreCategory.TARGET_1, dice(1, 4, 3, 2, 6)))
        // 1 of 3 on a 15 is 2.5, which would round up - but one number alone scores nothing.
        assertEquals(0, ScoreCalculator.scoreFor(player, ScoreCategory.TARGET_2, dice(1, 4, 3, 2, 6)))
    }

    @Test
    fun `the Alibi scores the best open target the dice hit - never doubled - and nothing for no hit`() {
        // In place for 1 2 3 4 5, and a hit for 4 1 3 2 too: the Alibi takes the 40, not 80.
        val straight = dice(1, 2, 3, 4, 5)
        assertEquals(40, ScoreCalculator.scoreFor(hitListPlayer(), ScoreCategory.ALIBI, straight))
        // With that target already scored, it's the best of the rest the dice still hit.
        assertEquals(20, ScoreCalculator.scoreFor(hitListPlayer(mapOf(ScoreCategory.TARGET_3 to listOf(0))), ScoreCategory.ALIBI, straight))
        // Exact for 4 1 3 2: still only its points.
        assertEquals(20, ScoreCalculator.scoreFor(hitListPlayer(), ScoreCategory.ALIBI, dice(4, 1, 3, 2, 6)))
        assertEquals(0, ScoreCalculator.scoreFor(hitListPlayer(), ScoreCategory.ALIBI, dice(6, 6, 6, 6, 6)))
        // A partial hit is a target's alone: 3 of 4 scores 10 there, and nothing in the Alibi.
        assertEquals(10, ScoreCalculator.scoreFor(hitListPlayer(), ScoreCategory.TARGET_1, dice(4, 1, 3, 6, 6)))
        assertEquals(0, ScoreCalculator.scoreFor(hitListPlayer(), ScoreCategory.ALIBI, dice(4, 1, 3, 6, 6)))
    }

    @Test
    fun `a hit in the Alibi leaves its target open for another try`() {
        var state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "P")), GameMode.HIT_LIST, random = Random(4))
        val (category, drawn) = state.hitList.entries.first().toPair()
        val hit = drawn.called.map { Die(value = it) } + List(drawn.places.size - drawn.called.size) { Die(value = 6) }
        state = GameEngine.commitScore(state.copy(dice = hit, phase = TurnPhase.ROLLED), ScoreCategory.ALIBI)
        val player = state.players.single()

        assertTrue(player.scoresIn(ScoreCategory.ALIBI).single() >= drawn.points)
        assertTrue(player.isOpen(category))
        assertFalse(player.isOpen(ScoreCategory.ALIBI))
        assertEquals(12, player.turnsLeft)
    }

    @Test
    fun `targets and the Alibi have no fixed rule to score by`() {
        for (category in ScoreCategory.TARGETS + ScoreCategory.ALIBI) {
            assertFailsWith<IllegalArgumentException> { DiceScoring.score(category, dice(1, 2, 3, 4, 5)) }
        }
    }

    @Test
    fun `Hit List is Standard's dice and rolls with twelve targets and the Alibi - no Extended Scores - off the Leaderboard`() {
        val mode = GameMode.HIT_LIST
        assertEquals(ScoreCategory.TARGETS + ScoreCategory.ALIBI, mode.categories)
        assertEquals(13, mode.turnsPerGame)
        assertEquals(GameMode.STANDARD.rollsPerTurn, mode.rollsPerTurn)
        assertEquals(GameMode.STANDARD.diceCount, mode.diceCount)
        assertFalse(mode.allowsExtendedScores)
        assertEquals(mode.categories, mode.categoriesWith(extendedScores = true))
        assertFalse(mode.countsOnLeaderboard)
        assertEquals(785, mode.maxPossibleScore)
        assertTrue(GameMode.entries.filter { it != mode }.all { it.allowsExtendedScores })
    }

    @Test
    fun `Hit List's max possible score is every target exact plus the Alibi's best - played through the engine`() {
        var state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Perfect")), GameMode.HIT_LIST, random = Random(2))
        val hitList = state.hitList
        fun exactly(target: HitTarget) = target.places.map { Die(value = it ?: 1) }
        // The Alibi first, on the richest target - its exact hit still to come.
        val richest = hitList.values.maxBy { it.points }
        state = GameEngine.commitScore(state.copy(dice = exactly(richest), phase = TurnPhase.ROLLED), ScoreCategory.ALIBI)
        for ((category, target) in hitList) {
            state = GameEngine.commitScore(state.copy(dice = exactly(target), phase = TurnPhase.ROLLED), category)
        }
        assertTrue(state.isGameOver)
        assertEquals(GameMode.HIT_LIST.maxPossibleScore, state.players.single().totalScore)
    }
}
