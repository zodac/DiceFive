package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.data.settings.ScreenOrientation

/**
 * Pins every [StoredId]: these strings are in players' settings and saved games, so each one is permanent once
 * released. A constant can be renamed or moved; its id can't. A new constant adds its id to the list here.
 */
class StoredIdTest {

    private fun <E> ids(entries: List<E>): List<String> where E : Enum<E>, E : StoredId = entries.map { it.id }

    @Test
    fun `every stored id is the one released - in declaration order`() {
        assertEquals(listOf("HUMAN", "AI"), ids(PlayerType.entries))
        assertEquals(listOf("EASY", "MEDIUM", "HARD"), ids(Difficulty.entries))
        assertEquals(listOf("CYAN", "GREEN", "PURPLE", "AMBER", "PINK", "RED", "BLUE", "LIME"), ids(PlayerColour.entries))
        assertEquals(listOf("RED", "YELLOW", "BLUE"), ids(DieColour.entries))
        assertEquals(listOf("AWAITING_ROLL", "ROLLED"), ids(TurnPhase.entries))
        assertEquals(listOf("HIGH", "MEDIUM", "LOW", "OFF"), ids(AnimationLevel.entries))
        assertEquals(listOf("DEVICE", "PORTRAIT", "LANDSCAPE"), ids(ScreenOrientation.entries))
        assertEquals(listOf("GAMES_PLAYED", "GAMES_WON", "WIN_STREAK", "SCORED_5X", "DICE_ROLLED", "FIVE_X_NUMBERS"), ids(AchievementCounter.entries))
        assertEquals(
            listOf(
                "ONES", "TWOS", "THREES", "FOURS", "FIVES", "SIXES",
                "THREE_OF_A_KIND", "FOUR_OF_A_KIND", "FULL_HOUSE", "SMALL_STRAIGHT", "LARGE_STRAIGHT", "FIVE_OF_A_KIND", "CHANCE",
                "REDS", "YELLOWS", "BLUES", "COLOURED_HOUSE",
                "TWO_PAIR", "EVENS", "ODDS",
                "TARGET_1", "TARGET_2", "TARGET_3", "TARGET_4", "TARGET_5", "TARGET_6",
                "TARGET_7", "TARGET_8", "TARGET_9", "TARGET_10", "TARGET_11", "TARGET_12",
                "ALIBI",
                "D20_1", "D20_2", "D20_3", "D20_4", "D20_5", "D20_6", "D20_7", "D20_8", "D20_9", "D20_10",
                "D20_11", "D20_12", "D20_13", "D20_14", "D20_15", "D20_16", "D20_17", "D20_18", "D20_19", "D20_20",
                "D20_WILD_LOW", "D20_WILD_HIGH",
            ),
            ids(ScoreCategory.entries),
        )
    }

    @Test
    fun `an id reads back as its value - and one nothing recognises as nothing`() {
        assertEquals(PlayerColour.AMBER, storedIdOf<PlayerColour>("AMBER"))
        assertEquals(ScoreCategory.D20_WILD_HIGH, storedIdOf<ScoreCategory>("D20_WILD_HIGH"))
        assertNull(storedIdOf<Difficulty>("IMPOSSIBLE"))
        assertNull(storedIdOf<Difficulty>("hard"))
    }
}
