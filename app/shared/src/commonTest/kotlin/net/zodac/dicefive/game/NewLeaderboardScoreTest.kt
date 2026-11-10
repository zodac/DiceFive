package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnTimer
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

    private fun scored(mode: GameMode, total: Int) =
        PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = mode).let { it.copy(scorecard = it.scorecard + oneScoreEach(mapOf(mode.categories.first() to total))) }

    private fun marked(player: PlayerState) =
        newLeaderboardScoreSeats(GameState(players = listOf(player), gameMode = player.gameMode), emptySet()) == setOf(0)

    @Test
    fun `a total under the mode's lowest final score is not marked - one at it is`() {
        for (mode in GameMode.entries.filter { it.minPossibleScore > 0 }) {
            assertEquals(false, marked(scored(mode, mode.minPossibleScore - 1)), "$mode: ${mode.minPossibleScore - 1} ends no game")
            assertEquals(true, marked(scored(mode, mode.minPossibleScore)), "$mode")
        }
        // Standard's 1 to 4 and Third Wind's 1 to 14 are never marked; a mode that can end on 0 marks any total above it.
        assertEquals(false, marked(scored(GameMode.STANDARD, 4)))
        assertEquals(false, marked(scored(GameMode.THIRD_WIND, 14)))
        for (mode in listOf(GameMode.D20, GameMode.HIT_LIST, GameMode.QUICKFIRE)) assertEquals(true, marked(scored(mode, 1)), "$mode")
    }

    @Test
    fun `nothing is marked before the board is read - once the game is over - or with a modifier on - and any mode is marked`() {
        val state = game(player(PlayerType.HUMAN, 30))
        assertEquals(emptySet(), newLeaderboardScoreSeats(state, null))
        assertEquals(emptySet(), newLeaderboardScoreSeats(state.copy(isGameOver = true), emptySet()))
        assertEquals(emptySet(), newLeaderboardScoreSeats(state.copy(turnTimer = TurnTimer.SECONDS_30), emptySet()))
        assertEquals(setOf(0), newLeaderboardScoreSeats(state, emptySet()))
        // Every mode has its own board: a Third Wind game is marked against Third Wind's scores.
        assertEquals(setOf(0), newLeaderboardScoreSeats(state.copy(gameMode = GameMode.THIRD_WIND), emptySet()))
        assertEquals(emptySet(), newLeaderboardScoreSeats(state.copy(gameMode = GameMode.THIRD_WIND), setOf(30)))
    }
}
