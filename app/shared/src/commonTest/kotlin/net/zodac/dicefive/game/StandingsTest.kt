package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory

class StandingsTest {

    private fun player(vararg scores: Pair<ScoreCategory, Int>) =
        PlayerState(name = "P", type = PlayerType.HUMAN).let { it.copy(scorecard = it.scorecard + scores) }

    /** A full Standard scorecard adding up to [total]: all in Chance, or one point of it in Ones - so one fewer zeroed box. */
    private fun finished(total: Int, zeroes: Boolean) = PlayerState(name = "P", type = PlayerType.HUMAN).let { base ->
        val card = GameMode.STANDARD.categories.associateWith<ScoreCategory, Int?> { 0 }.toMutableMap()
        card[ScoreCategory.CHANCE] = if (zeroes) total else total - 1
        if (!zeroes) card[ScoreCategory.ONES] = 1
        base.copy(scorecard = card)
    }

    @Test
    fun `a solo game has no places to show`() {
        assertNull(standings(listOf(player(ScoreCategory.CHANCE to 20))))
    }

    @Test
    fun `every total still level shows no places - as at the start`() {
        assertNull(standings(listOf(player(), player(), player())))
        assertNull(standings(listOf(player(ScoreCategory.CHANCE to 20), player(ScoreCategory.SIXES to 20))))
    }

    @Test
    fun `places go by total score and equal totals share their place`() {
        val players = listOf(
            player(ScoreCategory.CHANCE to 22),
            player(ScoreCategory.FULL_HOUSE to 25, ScoreCategory.ONES to 3),
            player(ScoreCategory.CHANCE to 22),
            player(ScoreCategory.ONES to 2),
        )

        assertEquals(
            listOf(Standing(2, tied = true), Standing(1, tied = false), Standing(2, tied = true), Standing(4, tied = false)),
            standings(players),
        )
    }

    @Test
    fun `a finished game's places follow the tie-break house rule as the results screen does`() {
        // Equal totals; the second scored a box the first zeroed - the house rule ranks more zeroes higher.
        val players = listOf(finished(30, zeroes = false), finished(30, zeroes = true))

        assertEquals(listOf(Standing(2, tied = false), Standing(1, tied = false)), standings(players))
    }

    @Test
    fun `places read as ordinals with an equals sign for a shared place and spoken without it`() {
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map { Standing(it, tied = false).label() },
        )
        assertEquals("=2nd", Standing(2, tied = true).label())
        assertEquals("tied 2nd place", Standing(2, tied = true).spoken())
        assertEquals("1st place", Standing(1, tied = false).spoken())
    }
}
