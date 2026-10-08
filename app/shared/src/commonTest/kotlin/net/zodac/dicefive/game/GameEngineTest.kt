package net.zodac.dicefive.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.oneScoreEach

class GameEngineTest {

    private val onePlayer = listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"))
    private val twoPlayers = listOf(
        PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"),
        PlayerConfig(slot = 2, type = PlayerType.AI, name = "Bot"),
    )

    private fun scoreNow(state: GameState, category: ScoreCategory = ScoreCategory.CHANCE) =
        GameEngine.commitScore(state, category)

    @Test
    fun `newGame - its players - mode and timer and every modifier taking it off the Leaderboard`() {
        assertFailsWith<IllegalArgumentException> { GameEngine.newGame(emptyList()) }

        // The chosen mode goes to the game and every player's scorecard.
        val tricolour = GameEngine.newGame(twoPlayers, GameMode.TRICOLOUR)
        assertEquals(GameMode.TRICOLOUR, tricolour.gameMode)
        assertTrue(tricolour.players.all { it.gameMode == GameMode.TRICOLOUR })
        assertTrue(tricolour.players.all { it.scorecard.keys.toList() == GameMode.TRICOLOUR.categories })
        assertEquals(GameMode.TRICOLOUR.diceCount, tricolour.dice.size)
        assertEquals(GameMode.TRICOLOUR.rollsPerTurn, tricolour.rollsRemaining)

        // No turn timer by default, but a chosen one is carried.
        assertEquals(TurnTimer.NONE, GameEngine.newGame(onePlayer).turnTimer)
        assertEquals(TurnTimer.SECONDS_30, GameEngine.newGame(onePlayer, turnTimer = TurnTimer.SECONDS_30).turnTimer)

        assertTrue(GameEngine.newGame(onePlayer).countsOnLeaderboard)
        assertTrue(GameEngine.newGame(onePlayer, rollModifiers = RollModifiers()).countsOnLeaderboard)
        assertFalse(GameEngine.newGame(onePlayer, turnTimer = TurnTimer.SECONDS_30).countsOnLeaderboard)
        assertFalse(GameEngine.newGame(onePlayer, rollModifiers = RollModifiers(rollsPerTurn = 3)).countsOnLeaderboard)
        assertFalse(GameEngine.newGame(onePlayer, rollModifiers = RollModifiers(storedRolls = true)).countsOnLeaderboard)
        assertFalse(GameEngine.newGame(onePlayer, extendedScores = true).countsOnLeaderboard)
        assertFalse(GameEngine.newGame(onePlayer, unluckyDice = UnluckyDice()).countsOnLeaderboard)
        assertTrue(GameEngine.newGame(onePlayer).unluckyDice == null)

        // A roll modifier is set on every player.
        assertTrue(GameEngine.newGame(twoPlayers, rollModifiers = RollModifiers(storedRolls = true)).players.all { it.rollsModified })
        assertTrue(GameEngine.newGame(twoPlayers).players.none { it.rollsModified })
    }

    @Test
    fun `a modifier's settings must be in range - Number of Rolls 1 to 9 - Unlucky Dice odds and cap`() {
        assertFailsWith<IllegalArgumentException> { RollModifiers(rollsPerTurn = 0) }
        assertFailsWith<IllegalArgumentException> { RollModifiers(rollsPerTurn = 10) }
        assertFailsWith<IllegalArgumentException> { RollModifiers(storedRollsMax = -1) }
        assertFailsWith<IllegalArgumentException> { UnluckyDice(oddsPercent = 0) }
        assertFailsWith<IllegalArgumentException> { UnluckyDice(oddsPercent = 60) }
        assertFailsWith<IllegalArgumentException> { UnluckyDice(maxDice = 0) }
        assertFailsWith<IllegalArgumentException> { UnluckyDice(maxDice = 6) }
    }

    @Test
    fun `rolling and holding - rolls run out - a held die keeps its value and no hold before the first roll`() {
        val rolled = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        assertEquals(2, rolled.rollsRemaining)
        assertEquals(TurnPhase.ROLLED, rolled.phase)

        var spent = GameEngine.newGame(onePlayer)
        repeat(3) { spent = GameEngine.rollDice(spent) }
        assertFailsWith<IllegalStateException> { GameEngine.rollDice(spent) }
        // Holding has no effect on a roll that won't happen after the last one, but there's no reason to actually forbid
        // it - see the comment on GameEngine.toggleHold.
        assertTrue(GameEngine.toggleHold(spent, dieIndex = 0).dice[0].isHeld)

        assertFailsWith<IllegalStateException> { GameEngine.toggleHold(GameEngine.newGame(onePlayer), dieIndex = 0) }

        val held = GameEngine.toggleHold(rolled, dieIndex = 0)
        val rerolled = GameEngine.rollDice(held)
        assertEquals(held.dice[0].value, rerolled.dice[0].value)
        assertTrue(rerolled.dice[0].isHeld)
    }

    @Test
    fun `committing a score - after a roll - in an open box - records the hand - moves the turn on and ends the game when full`() {
        assertFailsWith<IllegalStateException> { GameEngine.commitScore(GameEngine.newGame(onePlayer), ScoreCategory.CHANCE) }
        // There is only one player, so it's back to the same one - with Chance already filled.
        val chanceFilled = GameEngine.rollDice(GameEngine.commitScore(GameEngine.rollDice(GameEngine.newGame(onePlayer)), ScoreCategory.CHANCE))
        assertFailsWith<IllegalStateException> { GameEngine.commitScore(chanceFilled, ScoreCategory.CHANCE) }

        // It advances to the next player and resets the turn, recording the dice and box player 1 scored with - the next
        // player's own, not having finished a turn, still null.
        val held = GameEngine.toggleHold(GameEngine.rollDice(GameEngine.newGame(twoPlayers)), dieIndex = 0)
        var state = GameEngine.commitScore(held, ScoreCategory.CHANCE)
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(3, state.rollsRemaining)
        assertEquals(TurnPhase.AWAITING_ROLL, state.phase)
        assertTrue(state.dice.none { it.isHeld })
        assertEquals(held.dice, state.players[0].lastRoll)
        assertEquals(null, state.players[1].lastRoll)
        assertEquals(ScoreCategory.CHANCE, state.players[0].lastScoredCategory)
        assertEquals(null, state.players[1].lastScoredCategory)
        // Each turn replaces the last.
        state = GameEngine.commitScore(GameEngine.rollDice(state), ScoreCategory.CHANCE)
        state = GameEngine.commitScore(GameEngine.rollDice(state), ScoreCategory.FOUR_OF_A_KIND)
        assertEquals(ScoreCategory.FOUR_OF_A_KIND, state.players[0].lastScoredCategory)

        // Each roll counts towards the rolling player only - across turns.
        var counted = GameEngine.rollDice(GameEngine.rollDice(GameEngine.newGame(twoPlayers)))
        counted = GameEngine.rollDice(GameEngine.commitScore(counted, ScoreCategory.CHANCE))
        assertEquals(listOf(2, 1), counted.players.map { it.rollCount })
        counted = GameEngine.rollDice(GameEngine.commitScore(counted, ScoreCategory.CHANCE))
        assertEquals(listOf(3, 1), counted.players.map { it.rollCount })

        // The game ends once every player's scorecard is full.
        val almostFull = GameState(
            players = listOf(
                PlayerState(
                    name = "Player 1",
                    type = PlayerType.HUMAN,
                    scorecard = oneScoreEach(GameMode.STANDARD.categories.associateWith { if (it == ScoreCategory.CHANCE) null else 0 }),
                ),
            ),
            dice = List(5) { Die(value = 4) },
            rollsRemaining = 1,
            phase = TurnPhase.ROLLED,
        )
        assertTrue(GameEngine.commitScore(almostFull, ScoreCategory.CHANCE).isGameOver)
    }

    @Test
    fun `superuser cycling moves one die to its next face - 6 back to 1 - in Tricolour through each colour - whatever rolls are left`() {
        val rolled = GameEngine.toggleHold(GameEngine.rollDice(GameEngine.newGame(onePlayer)), dieIndex = 0)
        val three = rolled.copy(dice = rolled.dice.mapIndexed { i, die -> if (i == 0) die.copy(value = 3) else die })
        val cycled = GameEngine.cycleDieValue(three, dieIndex = 0)
        assertEquals(4, cycled.dice[0].value)
        assertTrue(cycled.dice[0].isHeld)
        assertEquals(three.dice.drop(1), cycled.dice.drop(1))
        assertEquals(three.rollsRemaining, cycled.rollsRemaining)
        assertEquals(three.phase, cycled.phase)

        val six = rolled.copy(dice = rolled.dice.mapIndexed { i, die -> if (i == 0) die.copy(value = 6) else die })
        assertEquals(1, GameEngine.cycleDieValue(six, dieIndex = 0).dice[0].value)

        // It ignores the normal rolls-remaining rule.
        var spent = GameEngine.newGame(onePlayer)
        repeat(3) { spent = GameEngine.rollDice(spent) }
        val before = spent.dice[0].value
        assertEquals(if (before >= 6) 1 else before + 1, GameEngine.cycleDieValue(spent, dieIndex = 0).dice[0].value)

        // Tricolour runs 1 to 6 within a colour - then on to the next colour.
        var state = GameEngine.newGame(onePlayer, GameMode.TRICOLOUR).copy(
            dice = List(5) { Die(value = 1, colour = DieColour.RED, isHeld = true) },
            phase = TurnPhase.ROLLED,
        )
        val seen = mutableListOf(state.dice[0].value to state.dice[0].colour)
        repeat(18) {
            state = GameEngine.cycleDieValue(state, dieIndex = 0)
            seen += state.dice[0].value to state.dice[0].colour
        }
        val expected = listOf(DieColour.RED, DieColour.YELLOW, DieColour.BLUE).flatMap { colour -> (1..6).map { it to colour } } + (1 to DieColour.RED)
        assertEquals<List<Pair<Int, DieColour?>>>(expected, seen)
        assertTrue(state.dice.drop(1).all { it == Die(value = 1, colour = DieColour.RED, isHeld = true) })
    }

    @Test
    fun `Tricolour - every rolled die has a colour a hold keeps and the game ends once its 17 boxes are filled`() {
        val random = Random(42)
        repeat(20) {
            val tricolour = GameEngine.rollDice(GameEngine.newGame(onePlayer, GameMode.TRICOLOUR), random)
            val standard = GameEngine.rollDice(GameEngine.newGame(onePlayer, GameMode.STANDARD), random)
            assertTrue(tricolour.dice.all { it.colour in GameMode.TRICOLOUR.dieColours && it.value in 1..6 })
            assertTrue(standard.dice.all { it.colour == null })
        }

        val held = GameEngine.newGame(onePlayer, GameMode.TRICOLOUR).copy(
            dice = List(5) { Die(value = 2, colour = DieColour.BLUE, isHeld = it == 0) },
            phase = TurnPhase.ROLLED,
        )
        assertEquals(Die(value = 2, colour = DieColour.BLUE, isHeld = true), GameEngine.rollDice(held, Random(7)).dice[0])

        val mode = GameMode.TRICOLOUR
        val almostFull = mode.categories.associateWith { category -> if (category == ScoreCategory.COLOURED_HOUSE) null else 0 }
        val lastTurn = GameEngine.newGame(onePlayer, mode).let {
            it.copy(
                players = listOf(it.players.single().copy(scorecard = oneScoreEach(almostFull))),
                dice = List(5) { index -> Die(value = index + 1, colour = DieColour.RED) },
                phase = TurnPhase.ROLLED,
            )
        }
        assertTrue(GameEngine.commitScore(lastTurn, ScoreCategory.COLOURED_HOUSE).isGameOver)
    }

    // ---- Stud: seven dice, five hold slots, only the held dice score ----------------------------

    private fun rolledStud(vararg values: Int): GameState =
        GameEngine.newGame(onePlayer, GameMode.STUD).copy(dice = values.map { Die(value = it) }, phase = TurnPhase.ROLLED, rollsRemaining = 2)

    @Test
    fun `a Stud die held goes to the free slot nearest its column - keeping column order and stays there - at most five`() {
        var state = rolledStud(1, 2, 3, 4, 5, 6, 6)
        state = GameEngine.toggleHold(state, 4)
        state = GameEngine.toggleHold(state, 1)
        state = GameEngine.toggleHold(state, 6)
        assertEquals(listOf(null, 1, null, null, 3, null, 4), state.dice.map { it.heldSlot })
        state = GameEngine.toggleHold(state, 4)
        assertEquals(null, state.dice[4].heldSlot)
        assertEquals(false, state.dice[4].isHeld)
        // The others stay where they are.
        state = GameEngine.toggleHold(state, 0)
        assertEquals(listOf(0, 1, null, null, null, null, 4), state.dice.map { it.heldSlot })

        // Where its slot is taken, a die goes to the nearest free one - and the held dice never move.
        state = rolledStud(1, 2, 3, 4, 5, 6, 6)
        for (index in listOf(3, 2, 1)) state = GameEngine.toggleHold(state, index)
        assertEquals(listOf(null, 0, 1, 2, null, null, null), state.dice.map { it.heldSlot })
        // Die 1's slot is taken, so it goes in the leftmost free one.
        state = GameEngine.toggleHold(state, 0)
        assertEquals(listOf(3, 0, 1, 2, null, null, null), state.dice.map { it.heldSlot })
        state = GameEngine.toggleHold(state, 6)
        assertEquals(listOf(3, 0, 1, 2, null, null, 4), state.dice.map { it.heldSlot })

        // Between two free slots equally near: die 4 sits under the middle slot, which die 3 has; slots 2 and 4 are as
        // near, but only slot 4 is right of die 3.
        state = rolledStud(1, 2, 3, 4, 5, 6, 6)
        state = GameEngine.toggleHold(GameEngine.toggleHold(GameEngine.toggleHold(state, 1), 2), 1)
        assertEquals(listOf(null, null, 2, null, null, null, null), state.dice.map { it.heldSlot })
        state = GameEngine.toggleHold(state, 3)
        assertEquals(3, state.dice[3].heldSlot)

        // No die can be held once all five slots are full; letting one go frees its slot for another.
        state = rolledStud(1, 2, 3, 4, 5, 6, 6)
        for (index in 0 until 5) state = GameEngine.toggleHold(state, index)
        assertEquals(false, GameEngine.canHold(state))
        assertEquals(state, GameEngine.toggleHold(state, 5))
        state = GameEngine.toggleHold(GameEngine.toggleHold(state, 2), 5)
        assertEquals(listOf(0, 1, null, 3, 4, 2, null), state.dice.map { it.heldSlot })
    }

    @Test
    fun `Stud scores only the held dice in slot order once the hand is full - fillHand completes it - over seven dice a turn`() {
        var state = rolledStud(6, 1, 6, 6, 1, 6, 6)
        for (index in listOf(0, 2, 3, 5)) state = GameEngine.toggleHold(state, index)
        assertEquals(4, state.scoringDice.size)
        assertFailsWith<IllegalStateException> { GameEngine.commitScore(state, ScoreCategory.SIXES) }
        state = GameEngine.toggleHold(state, 1)
        val scored = GameEngine.commitScore(state, ScoreCategory.SIXES).players.single()
        // Four 6s and the held 1 - the last 6 on the mat doesn't count.
        assertEquals(24, scored.scoresIn(ScoreCategory.SIXES).singleOrNull())
        assertEquals(7, scored.lastRoll?.size)

        // The hand is in slot order whichever dice the slots hold.
        var slotted = rolledStud(1, 2, 3, 4, 5, 6, 6)
        for (index in listOf(3, 2, 1, 0)) slotted = GameEngine.toggleHold(slotted, index)
        assertEquals(listOf(2, 3, 4, 1), slotted.scoringDice.map { it.value })

        // fillHand holds the leftmost unheld dice until the hand is full - and leaves other modes alone.
        val filled = GameEngine.fillHand(GameEngine.toggleHold(rolledStud(1, 2, 3, 4, 5, 6, 6), 5))
        assertTrue(filled.hasFullHand)
        assertEquals(listOf(true, true, true, true, false, true, false), filled.dice.map { it.isHeld })
        val standard = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        assertEquals(standard, GameEngine.fillHand(standard))

        // A turn rolls seven dice and the next starts with seven unheld.
        var turn = GameEngine.rollDice(GameEngine.newGame(onePlayer, GameMode.STUD))
        assertEquals(7, turn.dice.size)
        turn = GameEngine.commitScore(GameEngine.fillHand(turn), ScoreCategory.CHANCE)
        assertEquals(7, turn.dice.size)
        assertTrue(turn.dice.none { it.isHeld || it.heldSlot != null })
    }

    @Test
    fun `in Third Wind scoring a box fills its next slot until all three are used - and the game ends after 39 turns`() {
        var state = GameEngine.newGame(onePlayer, GameMode.THIRD_WIND)
        for (value in 1..3) {
            state = GameEngine.commitScore(state.copy(dice = List(5) { Die(value = value) }, phase = TurnPhase.ROLLED), ScoreCategory.CHANCE)
        }
        val player = state.players.single()
        assertEquals(listOf(5, 10, 15), player.scoresIn(ScoreCategory.CHANCE))
        assertEquals(30, player.totalScore)
        assertEquals(36, player.turnsLeft)
        assertFalse(state.isGameOver)
        // A fourth time is one too many.
        assertFailsWith<IllegalStateException> {
            GameEngine.commitScore(state.copy(dice = List(5) { Die(value = 6) }, phase = TurnPhase.ROLLED), ScoreCategory.CHANCE)
        }

        var whole = GameEngine.newGame(onePlayer, GameMode.THIRD_WIND)
        var turns = 0
        while (!whole.isGameOver) {
            val open = GameMode.THIRD_WIND.categories.first { whole.players.single().isOpen(it) }
            whole = GameEngine.commitScore(whole.copy(dice = List(5) { Die(value = 1) }, phase = TurnPhase.ROLLED), open)
            turns++
        }
        assertEquals(39, turns)
        assertTrue(whole.players.single().scorecard.values.all { it.size == 3 })
    }

    // ---- Roll modifiers ---------------------------------------------------------------------------

    @Test
    fun `Number of Rolls replaces the mode's rolls every turn - that many and no more`() {
        var state = GameEngine.newGame(twoPlayers, rollModifiers = RollModifiers(rollsPerTurn = 7))
        assertEquals(7, state.rollsRemaining)
        assertEquals(7, state.turnRolls)
        state = scoreNow(GameEngine.rollDice(state))
        assertEquals(7, state.rollsRemaining)
        assertEquals(7, state.turnRolls)

        var nine = GameEngine.newGame(onePlayer, rollModifiers = RollModifiers(rollsPerTurn = 9))
        repeat(9) { nine = GameEngine.rollDice(nine) }
        assertEquals(0, nine.rollsRemaining)
        assertFailsWith<IllegalStateException> { GameEngine.rollDice(nine) }
    }

    @Test
    fun `Stored Rolls carries a player's unused rolls into their own next turn - up to its cap - on top of Number of Rolls`() {
        // Without it, unused rolls are lost.
        val plain = scoreNow(GameEngine.rollDice(GameEngine.newGame(onePlayer)))
        assertEquals(3, plain.rollsRemaining)
        assertEquals(0, plain.players.single().storedRolls)

        var state = scoreNow(GameEngine.rollDice(GameEngine.newGame(onePlayer, rollModifiers = RollModifiers(storedRolls = true)))) // 1 of 3 used, 2 kept
        assertEquals(2, state.players.single().storedRolls)
        assertEquals(5, state.rollsRemaining)
        assertEquals(5, state.turnRolls)
        state = scoreNow(GameEngine.rollDice(state), ScoreCategory.ONES) // 1 of 5 used, 4 kept
        assertEquals(4, state.players.single().storedRolls)
        assertEquals(7, state.rollsRemaining)

        // It waits for each player's own turn: player 2 has nothing stored, so gets the plain 3.
        var two = scoreNow(GameEngine.rollDice(GameEngine.newGame(twoPlayers, rollModifiers = RollModifiers(storedRolls = true)))) // player 1 keeps 2
        assertEquals(1, two.currentPlayerIndex)
        assertEquals(3, two.rollsRemaining)
        two = scoreNow(GameEngine.rollDice(GameEngine.rollDice(GameEngine.rollDice(two)))) // player 2 uses all 3
        assertEquals(0, two.players[1].storedRolls)
        // Back to player 1, who gets their 2 on top of 3.
        assertEquals(0, two.currentPlayerIndex)
        assertEquals(5, two.rollsRemaining)

        // A cap limits what is kept and the rest are lost; a cap of zero keeps nothing.
        val capped = scoreNow(GameEngine.rollDice(GameEngine.newGame(onePlayer, rollModifiers = RollModifiers(storedRolls = true, storedRollsMax = 1))))
        assertEquals(1, capped.players.single().storedRolls)
        assertEquals(4, capped.rollsRemaining)
        assertEquals(3, scoreNow(GameEngine.rollDice(GameEngine.newGame(onePlayer, rollModifiers = RollModifiers(storedRolls = true, storedRollsMax = 0)))).rollsRemaining)

        // It adds to a changed Number of Rolls: 1 of 2 used, 1 kept.
        val added = scoreNow(GameEngine.rollDice(GameEngine.newGame(onePlayer, rollModifiers = RollModifiers(rollsPerTurn = 2, storedRolls = true))))
        assertEquals(3, added.rollsRemaining)
        assertEquals(3, added.turnRolls)

        // Stored rolls can run to several digits: nothing rolled for 11 turns, 9 kept each time on top of 9.
        var hoarded = GameEngine.newGame(onePlayer, rollModifiers = RollModifiers(rollsPerTurn = 9, storedRolls = true))
        repeat(11) { hoarded = scoreNow(hoarded.copy(phase = TurnPhase.ROLLED), ScoreCategory.entries.first { hoarded.players.single().isOpen(it) }) }
        assertEquals(9 + 9 * 11, hoarded.rollsRemaining)
    }

    @Test
    fun `Extended Scores puts its boxes on every card - scores a roll in them and ends the game only once all 16 are scored`() {
        val game = GameEngine.newGame(twoPlayers, extendedScores = true)
        assertTrue(game.extendedScores)
        assertEquals(GameMode.STANDARD.categories + ScoreCategory.EXTENDED, game.categories)
        assertTrue(game.players.all { it.extendedScores && it.categories == game.categories })

        val twoPair = GameEngine.commitScore(
            GameEngine.newGame(onePlayer, extendedScores = true).copy(phase = TurnPhase.ROLLED, dice = listOf(6, 6, 5, 5, 2).map { Die(value = it) }),
            ScoreCategory.TWO_PAIR,
        )
        assertEquals(listOf(22), twoPair.players.single().scoresIn(ScoreCategory.TWO_PAIR))
        assertEquals(22, twoPair.players.single().totalScore)

        var state = GameEngine.newGame(onePlayer, extendedScores = true)
        repeat(15) {
            state = scoreNow(state.copy(phase = TurnPhase.ROLLED), state.categories.first { state.players.single().isOpen(it) })
            assertFalse(state.isGameOver)
        }
        state = scoreNow(state.copy(phase = TurnPhase.ROLLED), state.categories.first { state.players.single().isOpen(it) })
        assertTrue(state.isGameOver)
        assertTrue(state.players.single().isScorecardComplete)
    }

    // ---- Unlucky Dice -----------------------------------------------------------------------------

    @Test
    fun `golden dice roll at the odds given - a held one keeps its gold - and without odds nothing is drawn`() {
        // Without odds the dice come out exactly as they always have, none golden.
        val plain = GameEngine.rollDice(GameEngine.newGame(onePlayer), Random(7))
        assertTrue(plain.dice.none { it.isGolden })
        assertEquals(Random(7).let { random -> List(5) { random.nextInt(1, 7) } }, plain.dice.map { it.value })

        val random = Random(3)
        var golden = 0
        repeat(4_000) { golden += GameEngine.rollDice(GameEngine.newGame(onePlayer), random, goldenOneIn = 100).dice.count { it.isGolden } }
        assertEquals(0.01, golden / (4_000 * 5.0), 0.004)

        // A held die isn't rolled again, so it stays as it was; a rerolled one is drawn afresh.
        val start = GameEngine.newGame(onePlayer).let { it.copy(dice = it.dice.mapIndexed { i, die -> if (i == 0) die.copy(isHeld = true, isGolden = true) else die.copy(isGolden = true) }) }
        val rerolled = GameEngine.rollDice(start, Random(1))
        assertTrue(rerolled.dice[0].isGolden)
        assertTrue(rerolled.dice.drop(1).none { it.isGolden })
    }

    private fun unluckyGame(odds: Int = 50, maxDice: Int = 5, mode: GameMode = GameMode.STANDARD) =
        GameEngine.newGame(onePlayer, mode, unluckyDice = UnluckyDice(odds, maxDice))

    @Test
    fun `Unlucky Dice locks unheld dice at its odds and never past its cap - and without it - rolls are as they always were`() {
        repeat(200) { seed -> assertTrue(GameEngine.rollDice(GameEngine.newGame(onePlayer), Random(seed)).dice.none { it.isUnlucky }) }
        // A game without it rolls exactly the dice it always has.
        val plain = GameEngine.rollDice(GameEngine.newGame(onePlayer), Random(7)).dice.map { it.value }
        assertEquals(Random(7).let { random -> List(5) { random.nextInt(1, 7) } }, plain)

        for (cap in 1..5) repeat(300) { seed ->
            assertTrue(GameEngine.rollDice(unluckyGame(odds = 50, maxDice = cap), Random(seed)).dice.count { it.isUnlucky } <= cap, "cap $cap seed $seed")
        }

        fun lockedShare(odds: Int): Double {
            val random = Random(odds)
            var locked = 0
            repeat(2_000) { locked += GameEngine.rollDice(unluckyGame(odds), random).dice.count { it.isUnlucky } }
            return locked / (2_000 * 5.0)
        }
        assertEquals(0.1, lockedShare(10), 0.03)
        assertEquals(0.3, lockedShare(30), 0.03)
        assertEquals(0.5, lockedShare(50), 0.03)

        // At 50% over five dice a cap of one is hit often, and the die picked varies roll to roll.
        val picked = (0 until 300).mapNotNull { seed ->
            GameEngine.rollDice(unluckyGame(odds = 50, maxDice = 1), Random(seed)).dice.indexOfFirst { it.isUnlucky }.takeIf { it >= 0 }
        }.toSet()
        assertEquals(setOf(0, 1, 2, 3, 4), picked)

        // A held die is never locked.
        repeat(300) { seed ->
            val held = unluckyGame().copy(phase = TurnPhase.ROLLED, rollsRemaining = 2, dice = List(5) { Die(value = 3, isHeld = it < 2) })
            val rolled = GameEngine.rollDice(held, Random(seed))
            assertTrue(rolled.dice.take(2).none { it.isUnlucky }, "seed $seed")
            assertTrue(rolled.dice.take(2).all { it.isHeld && it.value == 3 })
        }

        // A locked die is rolled again and starts clear: odds of 10% leave most rolls clear, so it can't stay locked by itself.
        val locked = unluckyGame(odds = 10, maxDice = 1).copy(phase = TurnPhase.ROLLED, rollsRemaining = 2, dice = List(5) { Die(value = 2, isUnlucky = it == 0) })
        assertTrue((0 until 50).count { seed -> !GameEngine.rollDice(locked, Random(seed)).dice[0].isUnlucky } > 25)
    }

    @Test
    fun `a locked die can't be held and isn't in the hand scored - so the hand shrinks and five matching with one locked are no 5x`() {
        val one = unluckyGame().copy(phase = TurnPhase.ROLLED, dice = List(5) { Die(value = it + 1, isUnlucky = it == 2) })
        assertFalse(GameEngine.canHold(one, 2))
        assertTrue(GameEngine.canHold(one, 1))
        assertEquals(one, GameEngine.toggleHold(one, 2))
        assertTrue(GameEngine.toggleHold(one, 1).dice[1].isHeld)

        val sixes = unluckyGame().copy(phase = TurnPhase.ROLLED, dice = List(5) { Die(value = 6, isUnlucky = it == 4) })
        assertEquals(4, sixes.scoringDice.size)
        assertEquals(4, sixes.handSize)
        assertTrue(sixes.hasFullHand)
        assertEquals(listOf(24), GameEngine.commitScore(sixes, ScoreCategory.SIXES).players.single().scoresIn(ScoreCategory.SIXES))

        val fours = unluckyGame().copy(phase = TurnPhase.ROLLED, dice = List(5) { Die(value = 4, isUnlucky = it == 0) })
        assertEquals(0, ScoreCalculator.scoreFor(fours.players.single(), ScoreCategory.FIVE_OF_A_KIND, fours.scoringDice))
        assertEquals(0, ScoreCalculator.scoreFor(fours.players.single(), ScoreCategory.LARGE_STRAIGHT, fours.scoringDice))

        // Every die locked leaves a hand of nothing that scores zero.
        val all = unluckyGame().copy(phase = TurnPhase.ROLLED, dice = List(5) { Die(value = 6, isUnlucky = true) })
        assertTrue(all.scoringDice.isEmpty())
        assertTrue(all.hasFullHand)
        assertEquals(listOf(0), GameEngine.commitScore(all, ScoreCategory.CHANCE).players.single().scoresIn(ScoreCategory.CHANCE))

        // With seven dice and five slots, locked dice shrink the hand to the dice left.
        val stud = GameEngine.newGame(onePlayer, GameMode.STUD, unluckyDice = UnluckyDice())
            .copy(phase = TurnPhase.ROLLED, dice = List(7) { Die(value = it % 6 + 1, isUnlucky = it >= 4) })
        assertEquals(4, stud.handSize)
        val filled = GameEngine.fillHand(stud)
        assertEquals(listOf(0, 1, 2, 3), filled.dice.withIndex().filter { it.value.isHeld }.map { it.index })
        assertTrue(filled.hasFullHand)
        assertEquals(4, filled.scoringDice.size)
    }
}
