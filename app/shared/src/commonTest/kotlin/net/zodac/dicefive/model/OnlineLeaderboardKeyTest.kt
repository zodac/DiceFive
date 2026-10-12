package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnlineLeaderboardKeyTest {

    @Test
    fun everyModeHasItsOwnStableSnakeCaseKey() {
        val keys = GameMode.entries.map { it.onlineLeaderboardKey }
        assertEquals(keys.size, keys.toSet().size, "two modes share an online leaderboard key: $keys")
        keys.forEach { assertTrue(Regex("[a-z][a-z0-9_]*").matches(it), "'$it' is not a snake_case key") }
    }

    /** Pinned: a published key is permanent on the service's side too (see PLAY_GAMES.md, "Risks and gotchas"). */
    @Test
    fun publishedKeysDoNotChange() {
        assertEquals(
            mapOf(
                GameMode.STANDARD to "standard",
                GameMode.TRICOLOUR to "tricolour",
                GameMode.QUICKFIRE to "quickfire",
                GameMode.STUD to "stud",
                GameMode.THIRD_WIND to "third_wind",
                GameMode.D20 to "d20",
                GameMode.HIT_LIST to "hit_list",
            ),
            GameMode.entries.associateWith { it.onlineLeaderboardKey },
        )
    }
}
