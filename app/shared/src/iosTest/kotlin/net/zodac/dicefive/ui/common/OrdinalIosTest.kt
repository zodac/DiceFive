package net.zodac.dicefive.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * iOS's [formatOrdinal] says what the hand-written English suffixes it replaced did, and follows the
 * language it's given rather than the device's. Android's is checked through the player tabs in
 * BoardSemanticsTest (:app:android), where ICU is real; neither can live in commonTest, whose JVM host
 * run has no platform formatter. Runs on a Mac only - see .claude/IOS_SUPPORT.md.
 */
class OrdinalIosTest {

    @Test
    fun englishOrdinalsMatchTheOldHandWrittenSuffixes() {
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map { formatOrdinal(it, "en-GB") },
        )
    }

    @Test
    fun theLanguageIsTheOneAskedFor() {
        assertEquals("1er", formatOrdinal(1, "fr"))
    }
}
