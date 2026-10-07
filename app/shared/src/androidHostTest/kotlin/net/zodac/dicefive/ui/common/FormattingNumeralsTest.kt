package net.zodac.dicefive.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which numerals a number is written in follows the strings' language tag (`common_locale`), so a translation chooses them:
 * "ar-u-nu-arab" for Arabic-Indic digits, "fa-u-nu-arabext" for Persian, plain "en-GB" for 0-9. Plain JVM: the Android actual
 * uses java.text only.
 */
class FormattingNumeralsTest {

    @Test
    fun `a number is written in the numerals the language tag asks for`() {
        assertEquals("1234", formatInteger(1234, "en-GB"))
        assertEquals("١٢٣٤", formatInteger(1234, "ar-u-nu-arab"))
        assertEquals("۱۲۳۴", formatInteger(1234, "fa-u-nu-arabext"))
        assertEquals("१२३४", formatInteger(1234, "hi-u-nu-deva"))
    }

    @Test
    fun `numerals are never grouped - a score or a count is just its digits`() {
        assertEquals("1000000", formatInteger(1_000_000, "en-GB"))
        assertEquals("-5", formatInteger(-5, "en-GB"))
    }

    @Test
    fun `grouping follows the language and keeps its numerals`() {
        assertEquals("34,521", formatGrouped(34_521, "en-GB"))
        assertEquals("٣٤٬٥٢١", formatGrouped(34_521, "ar-u-nu-arab"))
    }
}
