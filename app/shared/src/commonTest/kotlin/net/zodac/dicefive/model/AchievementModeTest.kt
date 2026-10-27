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
}
