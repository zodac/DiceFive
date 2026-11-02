package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.oneScoreEach

class NewLeaderboardScoreTest {

    private fun player(type: PlayerType, total: Int) =
        PlayerState(name = "P", type = type).let { it.copy(scorecard = it.scorecard + oneScoreEach(mapOf(ScoreCategory.CHANCE to total))) }

    private fun game(vararg players: PlayerState, over: Boolean = false) = GameState(players = players.toList(), isGameOver = over)

    @Test
    fun `a human's total the Leaderboard lacks is marked - one it holds, a CPU's, or a total of 0 is not`() {
        val state = game(player(PlayerType.HUMAN, 30), player(PlayerType.HUMAN, 31), player(PlayerType.AI, 32), player(PlayerType.HUMAN, 0), player(PlayerType.HUMAN, 450))
        assertEquals(setOf(0, 1, 4), newLeaderboardScoreSeats(state, emptySet()), "a 450 is marked too: no achievement band limits it")
        assertEquals(setOf(1), newLeaderboardScoreSeats(state, setOf(30, 32, 0, 450)))
    }

    @Test
    fun `nothing is marked before the Leaderboard is read - once the game is over - or for a game that stays off it`() {
        val state = game(player(PlayerType.HUMAN, 30))
        assertEquals(emptySet(), newLeaderboardScoreSeats(state, null))
        assertEquals(emptySet(), newLeaderboardScoreSeats(state.copy(isGameOver = true), emptySet()))
        assertEquals(emptySet(), newLeaderboardScoreSeats(state.copy(gameMode = GameMode.THIRD_WIND), emptySet()))
        assertEquals(setOf(0), newLeaderboardScoreSeats(state, emptySet()))
    }
}
