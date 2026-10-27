package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.oneScoreEach

class StandingsTest {

    private fun player(vararg scores: Pair<ScoreCategory, Int>) =
        PlayerState(name = "P", type = PlayerType.HUMAN).let { it.copy(scorecard = it.scorecard + oneScoreEach(scores.toMap())) }

    /** A full Standard scorecard adding up to [total]: all in Chance, or one point of it in Ones - so one fewer zeroed box. */
    private fun finished(total: Int, zeroes: Boolean) = PlayerState(name = "P", type = PlayerType.HUMAN).let { base ->
        val card = GameMode.STANDARD.categories.associateWith<ScoreCategory, Int?> { 0 }.toMutableMap()
        card[ScoreCategory.CHANCE] = if (zeroes) total else total - 1
        if (!zeroes) card[ScoreCategory.ONES] = 1
        base.copy(scorecard = oneScoreEach(card))
    }

    @Test
    fun `places go by total with equal totals sharing a place - none solo or before a turn is scored - and a finished game's by the tie-break`() {
        assertNull(standings(listOf(player(ScoreCategory.CHANCE to 20))), "a solo game has no places to show")
        assertNull(standings(listOf(player(), player(), player())), "no places before anyone has scored a turn")

        // Level totals once turns are scored share first place - and a zero is a scored turn too.
        val tied = Standing(1, tied = true)
        assertEquals(listOf(tied, tied), standings(listOf(player(ScoreCategory.CHANCE to 20), player(ScoreCategory.SIXES to 20))))
        assertEquals(listOf(tied, tied), standings(listOf(player(ScoreCategory.ONES to 0), player())))

        val players = listOf(
            player(ScoreCategory.CHANCE to 22),
            player(ScoreCategory.FULL_HOUSE to 25, ScoreCategory.ONES to 3),
            player(ScoreCategory.CHANCE to 22),
            player(ScoreCategory.ONES to 2),
        )
        assertEquals(listOf(Standing(2, tied = true), Standing(1, tied = false), Standing(2, tied = true), Standing(4, tied = false)), standings(players))

        // A finished game's places follow the tie-break house rule as the results screen does: equal totals, the second
        // scored a box the first zeroed - and the house rule ranks more zeroes higher.
        assertEquals(listOf(Standing(2, tied = false), Standing(1, tied = false)), standings(listOf(finished(30, zeroes = false), finished(30, zeroes = true))))
    }

    @Test
    fun `a finished game is won by whoever the tie-break ranks first - shared only on a true tie - and won by nobody solo`() {
        fun game(vararg players: PlayerState) = GameState(gameMode = GameMode.STANDARD, players = players.toList(), isGameOver = true)

        assertNull(game(finished(30, zeroes = true)).wonBy(0), "a solo game is neither won nor lost")
        val outright = game(finished(31, zeroes = true), finished(30, zeroes = true))
        assertEquals(listOf(true, false), outright.players.indices.map { outright.wonBy(it) })
        // Level on 30: the house rule ranks the one with more zeroed boxes first, so the other lost.
        val tieBroken = game(finished(30, zeroes = false), finished(30, zeroes = true))
        assertEquals(listOf(false, true), tieBroken.players.indices.map { tieBroken.wonBy(it) })
        val trueTie = game(finished(30, zeroes = true), finished(30, zeroes = true), finished(20, zeroes = true))
        assertEquals(listOf(true, true, false), trueTie.players.indices.map { trueTie.wonBy(it) })
    }
}
