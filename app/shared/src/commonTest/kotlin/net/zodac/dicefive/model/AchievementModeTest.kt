package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The achievements that belong to one game mode - see [Achievement.mode]. */
class AchievementModeTest {

    @Test
    fun `every mode beyond the official rules has exactly one win of its own - and the official rules none`() {
        for (mode in GameMode.entries) {
            val wins = Achievement.entries.filter { it.isModeWin && it.mode == mode }
            assertEquals(if (mode.isOfficialRules) 0 else 1, wins.size, "$mode: $wins")
        }
    }

    @Test
    fun `a mode's win names its mode`() {
        assertTrue(Achievement.entries.filter { it.isModeWin }.all { it.mode != null })
    }

    @Test
    fun `only the official rules are the official rules`() {
        assertEquals(listOf(GameMode.STANDARD), GameMode.entries.filter { it.isOfficialRules })
    }

    @Test
    fun `an achievement of one mode is earnable only in it - one of none in any`() {
        assertTrue(Achievement.HOLD_EM.isEarnableIn(GameMode.STUD))
        assertFalse(Achievement.HOLD_EM.isEarnableIn(GameMode.STANDARD))
        assertTrue(GameMode.entries.all { Achievement.FIRST_VICTORY.isEarnableIn(it) })
        assertEquals(setOf(Achievement.FIRST_VICTORY), setOf(Achievement.HOLD_EM, Achievement.FIRST_VICTORY).earnableIn(GameMode.TRICOLOUR))
    }

    @Test
    fun `an achievement's limited modes are its mode and the others it can also be earned in - in mode order - and none for any mode`() {
        assertEquals(listOf(GameMode.STUD), Achievement.HOLD_EM.limitedModes)
        assertEquals(listOf(GameMode.STANDARD, GameMode.TRICOLOUR), Achievement.SOLID_ROUND.limitedModes)
        assertEquals(emptyList(), Achievement.FIRST_VICTORY.limitedModes)
    }

    @Test
    fun `a deliberately limited achievement lists the modes left - neither the ones it's kept out of nor the impossible ones`() {
        // Encore is guarded in Third Wind and Stud; D20, Hit List and Quickfire have no 5x box.
        assertEquals(listOf(GameMode.STANDARD, GameMode.TRICOLOUR), Achievement.ENCORE.limitedModes)
        assertFalse(Achievement.ENCORE.isEarnableIn(GameMode.THIRD_WIND))
        assertFalse(Achievement.ENCORE.isEarnableIn(GameMode.QUICKFIRE))
        assertEquals(setOf(Achievement.FIRST_VICTORY), setOf(Achievement.ENCORE, Achievement.FIRST_VICTORY).earnableIn(GameMode.QUICKFIRE))
        // Cold Dice is kept out of Hit List and Quickfire, whose totals are low by design.
        assertEquals(GameMode.entries - GameMode.HIT_LIST - GameMode.QUICKFIRE, Achievement.COLD_DICE.limitedModes)
    }

    @Test
    fun `an achievement only some modes' rules rule out is not limited - though those modes still can't earn it`() {
        // Scratched needs a 5x box: D20, Hit List and Quickfire have none, and nothing else keeps it out.
        assertEquals(emptyList(), Achievement.SCRATCHED.limitedModes)
        assertFalse(Achievement.SCRATCHED.isEarnableIn(GameMode.QUICKFIRE))
        // A mode's own boxes, which no other mode has.
        assertEquals(emptyList(), Achievement.TRICOLOUR_ME_IMPRESSED.limitedModes)
        assertEquals(listOf(GameMode.TRICOLOUR), GameMode.entries.filter { Achievement.TRICOLOUR_ME_IMPRESSED.isEarnableIn(it) })
    }

    @Test
    fun `every achievement some mode can earn - and a mode is kept out or impossible, never both`() {
        assertTrue(Achievement.entries.all { a -> GameMode.entries.any { a.isEarnableIn(it) } })
        assertTrue(Achievement.entries.none { it.mode != null && (it.notEarnableIn + it.impossibleIn).isNotEmpty() }, "mode with notEarnableIn or impossibleIn")
        assertTrue(Achievement.entries.none { (it.notEarnableIn intersect it.impossibleIn).isNotEmpty() })
    }
}
