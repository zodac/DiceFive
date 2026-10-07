package net.zodac.dicefive.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

/** [isolatedIfOpposite]: text placed into a sentence is set apart only when it reads the other way from the sentence. */
class BidiIsolationTest {

    @Test
    fun `a right-to-left name in a left-to-right sentence is isolated`() {
        assertEquals("⁨علي⁩", "علي".isolatedIfOpposite(rightToLeft = false))
        assertEquals("⁨שרה 2⁩", "שרה 2".isolatedIfOpposite(rightToLeft = false))
    }

    @Test
    fun `the game's marks in a right-to-left sentence are isolated`() {
        assertEquals("⁨5x⁩", "5x".isolatedIfOpposite(rightToLeft = true))
        assertEquals("⁨Player 1⁩", "Player 1".isolatedIfOpposite(rightToLeft = true))
    }

    @Test
    fun `text in the sentence's own direction is unchanged`() {
        assertEquals("Ann", "Ann".isolatedIfOpposite(rightToLeft = false))
        assertEquals("علي", "علي".isolatedIfOpposite(rightToLeft = true))
    }

    @Test
    fun `text with no letters is unchanged`() {
        assertEquals("1,001", "1,001".isolatedIfOpposite(rightToLeft = false))
        assertEquals("١٢", "١٢".isolatedIfOpposite(rightToLeft = true))
    }
}
