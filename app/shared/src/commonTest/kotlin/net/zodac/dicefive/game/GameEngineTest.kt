package net.zodac.dicefive.game

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
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.oneScoreEach

class GameEngineTest {

    private val onePlayer = listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"))
    private val twoPlayers = listOf(
        PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"),
        PlayerConfig(slot = 2, type = PlayerType.AI, name = "Bot"),
    )

    @Test
    fun `newGame rejects an empty player list`() {
        assertFailsWith<IllegalArgumentException> { GameEngine.newGame(emptyList()) }
    }

    @Test
    fun `newGame defaults to no turn timer but carries a chosen one`() {
        assertEquals(TurnTimer.NONE, GameEngine.newGame(onePlayer).turnTimer)
        assertEquals(TurnTimer.SECONDS_30, GameEngine.newGame(onePlayer, turnTimer = TurnTimer.SECONDS_30).turnTimer)
    }

    @Test
    fun `rollDice decrements rolls remaining and marks the turn rolled`() {
        val state = GameEngine.newGame(onePlayer)

        val rolled = GameEngine.rollDice(state)

        assertEquals(2, rolled.rollsRemaining)
        assertEquals(TurnPhase.ROLLED, rolled.phase)
    }

    @Test
    fun `rollDice fails once no rolls remain`() {
        var state = GameEngine.newGame(onePlayer)
        repeat(3) { state = GameEngine.rollDice(state) }

        assertFailsWith<IllegalStateException> { GameEngine.rollDice(state) }
    }

    @Test
    fun `held dice keep their value across a re-roll`() {
        var state = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        state = GameEngine.toggleHold(state, dieIndex = 0)
        val heldValue = state.dice[0].value

        state = GameEngine.rollDice(state)

        assertEquals(heldValue, state.dice[0].value)
        assertTrue(state.dice[0].isHeld)
    }

    @Test
    fun `toggleHold before any roll fails`() {
        val state = GameEngine.newGame(onePlayer)

        assertFailsWith<IllegalStateException> { GameEngine.toggleHold(state, dieIndex = 0) }
    }

    @Test
    fun `toggleHold still works after the final roll`() {
        // Holding has no effect on a roll that won't happen after the last one, but there's no
        // reason to actually forbid it - see the comment on GameEngine.toggleHold.
        var state = GameEngine.newGame(onePlayer)
        repeat(3) { state = GameEngine.rollDice(state) }

        val result = GameEngine.toggleHold(state, dieIndex = 0)

        assertTrue(result.dice[0].isHeld)
    }

    @Test
    fun `commitScore before rolling fails`() {
        val state = GameEngine.newGame(onePlayer)

        assertFailsWith<IllegalStateException> { GameEngine.commitScore(state, ScoreCategory.CHANCE) }
    }

    @Test
    fun `commitScore rejects an already-filled category`() {
        var state = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        state = GameEngine.commitScore(state, ScoreCategory.CHANCE)
        // Second player's turn now (there is only one, so it's back to the same player) with a fresh scorecard slot.
        state = GameEngine.rollDice(state)

        assertFailsWith<IllegalStateException> { GameEngine.commitScore(state, ScoreCategory.CHANCE) }
    }

    @Test
    fun `commitScore advances to the next player and resets the turn`() {
        var state = GameEngine.rollDice(GameEngine.newGame(twoPlayers))

        state = GameEngine.commitScore(state, ScoreCategory.CHANCE)

        assertEquals(1, state.currentPlayerIndex)
        assertEquals(3, state.rollsRemaining)
        assertEquals(TurnPhase.AWAITING_ROLL, state.phase)
        assertTrue(state.dice.none { it.isHeld })
    }

    @Test
    fun `commitScore records the dice it was scored with as the player's lastRoll`() {
        var state = GameEngine.rollDice(GameEngine.newGame(twoPlayers))
        state = GameEngine.toggleHold(state, dieIndex = 0)
        val diceUsedToScore = state.dice

        state = GameEngine.commitScore(state, ScoreCategory.CHANCE)

        assertEquals(diceUsedToScore, state.players[0].lastRoll)
        // The next player's own dice are unaffected - and, having not yet finished a turn, still null.
        assertEquals(null, state.players[1].lastRoll)
    }

    @Test
    fun `commitScore records the box it scored in as the player's lastScoredCategory`() {
        var state = GameEngine.commitScore(GameEngine.rollDice(GameEngine.newGame(twoPlayers)), ScoreCategory.CHANCE)
        assertEquals(ScoreCategory.CHANCE, state.players[0].lastScoredCategory)
        assertEquals(null, state.players[1].lastScoredCategory)

        // Each turn replaces the last.
        state = GameEngine.commitScore(GameEngine.rollDice(state), ScoreCategory.CHANCE)
        state = GameEngine.commitScore(GameEngine.rollDice(state), ScoreCategory.FOUR_OF_A_KIND)
        assertEquals(ScoreCategory.FOUR_OF_A_KIND, state.players[0].lastScoredCategory)
    }

    @Test
    fun `rollDice counts each roll towards the rolling player only - across turns`() {
        var state = GameEngine.rollDice(GameEngine.newGame(twoPlayers))
        state = GameEngine.rollDice(state)
        state = GameEngine.commitScore(state, ScoreCategory.CHANCE)
        state = GameEngine.rollDice(state)

        assertEquals(listOf(2, 1), state.players.map { it.rollCount })

        state = GameEngine.commitScore(state, ScoreCategory.CHANCE)
        state = GameEngine.rollDice(state)

        assertEquals(listOf(3, 1), state.players.map { it.rollCount })
    }

    @Test
    fun `cycleDieValue advances only the target die to the next face`() {
        var state = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        state = GameEngine.toggleHold(state, dieIndex = 0)
        state = state.copy(dice = state.dice.mapIndexed { i, die -> if (i == 0) die.copy(value = 3) else die })
        val otherDiceBefore = state.dice.drop(1)
        val rollsBefore = state.rollsRemaining
        val phaseBefore = state.phase

        val result = GameEngine.cycleDieValue(state, dieIndex = 0)

        assertEquals(4, result.dice[0].value)
        assertTrue(result.dice[0].isHeld)
        assertEquals(otherDiceBefore, result.dice.drop(1))
        assertEquals(rollsBefore, result.rollsRemaining)
        assertEquals(phaseBefore, result.phase)
    }

    @Test
    fun `cycleDieValue wraps 6 back to 1`() {
        var state = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        state = state.copy(dice = state.dice.mapIndexed { i, die -> if (i == 0) die.copy(value = 6) else die })

        val result = GameEngine.cycleDieValue(state, dieIndex = 0)

        assertEquals(1, result.dice[0].value)
    }

    @Test
    fun `cycleDieValue ignores the normal rolls-remaining rule`() {
        var state = GameEngine.newGame(onePlayer)
        repeat(3) { state = GameEngine.rollDice(state) }
        val before = state.dice[0].value

        val result = GameEngine.cycleDieValue(state, dieIndex = 0)

        assertEquals(if (before >= 6) 1 else before + 1, result.dice[0].value)
    }

    @Test
    fun `game ends once every player's scorecard is full`() {
        val almostFullScorecard: Map<ScoreCategory, Int?> = GameMode.STANDARD.categories
            .associateWith { category -> if (category == ScoreCategory.CHANCE) null else 0 }

        val state = GameState(
            players = listOf(
                PlayerState(name = "Player 1", type = PlayerType.HUMAN, scorecard = oneScoreEach(almostFullScorecard)),
            ),
            dice = List(5) { Die(value = 4) },
            rollsRemaining = 1,
            phase = TurnPhase.ROLLED,
        )

        val result = GameEngine.commitScore(state, ScoreCategory.CHANCE)

        assertTrue(result.isGameOver)
    }

    // ---- Game modes ------------------------------------------------------------------------------

    @Test
    fun `newGame carries the chosen mode to the game and every player's scorecard`() {
        val state = GameEngine.newGame(twoPlayers, GameMode.TRICOLOUR)

        assertEquals(GameMode.TRICOLOUR, state.gameMode)
        assertTrue(state.players.all { it.gameMode == GameMode.TRICOLOUR })
        assertTrue(state.players.all { it.scorecard.keys.toList() == GameMode.TRICOLOUR.categories })
        assertEquals(GameMode.TRICOLOUR.diceCount, state.dice.size)
        assertEquals(GameMode.TRICOLOUR.rollsPerTurn, state.rollsRemaining)
    }

    @Test
    fun `Tricolour rolls give every rolled die a colour - and Standard rolls never do`() {
        val random = kotlin.random.Random(42)
        repeat(20) {
            val tricolour = GameEngine.rollDice(GameEngine.newGame(onePlayer, GameMode.TRICOLOUR), random)
            val standard = GameEngine.rollDice(GameEngine.newGame(onePlayer, GameMode.STANDARD), random)

            assertTrue(tricolour.dice.all { it.colour in GameMode.TRICOLOUR.dieColours && it.value in 1..6 })
            assertTrue(standard.dice.all { it.colour == null })
        }
    }

    @Test
    fun `a held die keeps its colour through a reroll`() {
        val state = GameEngine.newGame(onePlayer, GameMode.TRICOLOUR).copy(
            dice = List(5) { Die(value = 2, colour = DieColour.BLUE, isHeld = it == 0) },
            phase = TurnPhase.ROLLED,
        )

        val rolled = GameEngine.rollDice(state, kotlin.random.Random(7))

        assertEquals(Die(value = 2, colour = DieColour.BLUE, isHeld = true), rolled.dice[0])
    }

    @Test
    fun `superuser cycling in Tricolour runs 1 to 6 within a colour - then on to the next colour`() {
        var state = GameEngine.newGame(onePlayer, GameMode.TRICOLOUR).copy(
            dice = List(5) { Die(value = 1, colour = DieColour.RED, isHeld = true) },
            phase = TurnPhase.ROLLED,
        )
        val seen = mutableListOf(state.dice[0].value to state.dice[0].colour)
        repeat(18) {
            state = GameEngine.cycleDieValue(state, dieIndex = 0)
            seen += state.dice[0].value to state.dice[0].colour
        }

        val expected = listOf(DieColour.RED, DieColour.YELLOW, DieColour.BLUE)
            .flatMap { colour -> (1..6).map { it to colour } } + (1 to DieColour.RED)
        assertEquals<List<Pair<Int, DieColour?>>>(expected, seen)
        assertTrue(state.dice.drop(1).all { it == Die(value = 1, colour = DieColour.RED, isHeld = true) })
    }

    @Test
    fun `a Tricolour game ends once all 17 of its boxes are filled`() {
        val mode = GameMode.TRICOLOUR
        val almostFull = mode.categories.associateWith { category -> if (category == ScoreCategory.COLOURED_HOUSE) null else 0 }
        val state = GameEngine.newGame(onePlayer, mode).let {
            it.copy(
                players = listOf(it.players.single().copy(scorecard = oneScoreEach(almostFull))),
                dice = List(5) { index -> Die(value = index + 1, colour = DieColour.RED) },
                phase = TurnPhase.ROLLED,
            )
        }

        assertTrue(GameEngine.commitScore(state, ScoreCategory.COLOURED_HOUSE).isGameOver)
    }

    // ---- Stud: seven dice, five hold slots, only the held dice score ----------------------------

    private fun rolledStud(vararg values: Int): GameState =
        GameEngine.newGame(onePlayer, GameMode.STUD).copy(dice = values.map { Die(value = it) }, phase = TurnPhase.ROLLED, rollsRemaining = 2)

    @Test
    fun `a Stud die held goes to the slot nearest its column and stays there until let go`() {
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
    }

    @Test
    fun `a Stud die held where its slot is taken goes to the nearest free one - and the held dice never move`() {
        var state = rolledStud(1, 2, 3, 4, 5, 6, 6)
        for (index in listOf(3, 2, 1)) state = GameEngine.toggleHold(state, index)
        assertEquals(listOf(null, 0, 1, 2, null, null, null), state.dice.map { it.heldSlot })

        // Die 1's slot is taken, so it goes in the leftmost free one.
        state = GameEngine.toggleHold(state, 0)
        assertEquals(listOf(3, 0, 1, 2, null, null, null), state.dice.map { it.heldSlot })
        state = GameEngine.toggleHold(state, 6)
        assertEquals(listOf(3, 0, 1, 2, null, null, 4), state.dice.map { it.heldSlot })
    }

    @Test
    fun `between two free slots equally near a Stud die takes the one that keeps column order`() {
        var state = rolledStud(1, 2, 3, 4, 5, 6, 6)
        state = GameEngine.toggleHold(state, 1)
        state = GameEngine.toggleHold(state, 2)
        state = GameEngine.toggleHold(state, 1)
        assertEquals(listOf(null, null, 2, null, null, null, null), state.dice.map { it.heldSlot })

        // Die 4 sits under the middle slot, which die 3 has; slots 2 and 4 are as near, but only slot 4 is right of die 3.
        state = GameEngine.toggleHold(state, 3)
        assertEquals(3, state.dice[3].heldSlot)
    }

    @Test
    fun `a Stud die can't be held once all five slots are full`() {
        var state = rolledStud(1, 2, 3, 4, 5, 6, 6)
        for (index in 0 until 5) state = GameEngine.toggleHold(state, index)
        assertEquals(false, GameEngine.canHold(state))

        assertEquals(state, GameEngine.toggleHold(state, 5))
        // Letting one go frees its slot for another - the dice already held stay where they are.
        state = GameEngine.toggleHold(GameEngine.toggleHold(state, 2), 5)
        assertEquals(listOf(0, 1, null, 3, 4, 2, null), state.dice.map { it.heldSlot })
    }

    @Test
    fun `Stud scores only the held dice - and only once all five slots are full`() {
        var state = rolledStud(6, 1, 6, 6, 1, 6, 6)
        for (index in listOf(0, 2, 3, 5)) state = GameEngine.toggleHold(state, index)
        assertEquals(4, state.scoringDice.size)
        assertFailsWith<IllegalStateException> { GameEngine.commitScore(state, ScoreCategory.SIXES) }

        state = GameEngine.toggleHold(state, 1)
        val scored = GameEngine.commitScore(state, ScoreCategory.SIXES).players.single()
        // Four 6s and the held 1 - the last 6 on the mat doesn't count.
        assertEquals(24, scored.scoresIn(ScoreCategory.SIXES).singleOrNull())
        assertEquals(7, scored.lastRoll?.size)
    }

    @Test
    fun `a Stud hand is in slot order whichever dice the slots hold`() {
        var state = rolledStud(1, 2, 3, 4, 5, 6, 6)
        for (index in listOf(3, 2, 1, 0)) state = GameEngine.toggleHold(state, index)
        assertEquals(listOf(2, 3, 4, 1), state.scoringDice.map { it.value })
    }

    @Test
    fun `fillHand holds the leftmost unheld dice until the Stud hand is full - and leaves other modes alone`() {
        var state = rolledStud(1, 2, 3, 4, 5, 6, 6)
        state = GameEngine.toggleHold(state, 5)
        state = GameEngine.fillHand(state)
        assertTrue(state.hasFullHand)
        assertEquals(listOf(true, true, true, true, false, true, false), state.dice.map { it.isHeld })

        val standard = GameEngine.rollDice(GameEngine.newGame(onePlayer))
        assertEquals(standard, GameEngine.fillHand(standard))
    }

    @Test
    fun `a Stud turn rolls seven dice and the next turn starts with seven unheld`() {
        var state = GameEngine.rollDice(GameEngine.newGame(onePlayer, GameMode.STUD))
        assertEquals(7, state.dice.size)
        state = GameEngine.commitScore(GameEngine.fillHand(state), ScoreCategory.CHANCE)
        assertEquals(7, state.dice.size)
        assertTrue(state.dice.none { it.isHeld || it.heldSlot != null })
    }

    @Test
    fun `in Third Wind scoring a box fills its next slot until all three are used`() {
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
    }

    @Test
    fun `a Third Wind game ends after 39 turns - every box scored three times`() {
        var state = GameEngine.newGame(onePlayer, GameMode.THIRD_WIND)
        var turns = 0
        while (!state.isGameOver) {
            val player = state.players.single()
            val open = GameMode.THIRD_WIND.categories.first { player.isOpen(it) }
            state = GameEngine.commitScore(state.copy(dice = List(5) { Die(value = 1) }, phase = TurnPhase.ROLLED), open)
            turns++
        }
        assertEquals(39, turns)
        assertTrue(state.players.single().scorecard.values.all { it.size == 3 })
    }
}
