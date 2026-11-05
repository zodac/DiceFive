package net.zodac.dicefive.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

/** The thumb's sums over a list's items: what each measured item measured, and the average for each of the rest. */
class ScrollbarItemSizeTest {

    private val measured = mapOf(1 to 10, 2 to 20, 5 to 50)

    @Test
    fun `measured items count at their own size and the rest at the average`() {
        // 0, 3, and 4 at the average of 4; 1, 2 as measured.
        assertEquals(42f, measuredOrAverageSize(measured, start = 0, end = 5, average = 4f))
    }

    @Test
    fun `an item measured outside the range is left out, and its end is exclusive`() {
        assertEquals(20f + 4f + 4f, measuredOrAverageSize(measured, start = 2, end = 5, average = 4f))
    }

    @Test
    fun `an empty range has no size`() {
        assertEquals(0f, measuredOrAverageSize(measured, start = 3, end = 3, average = 4f))
    }

    @Test
    fun `it comes to the same as adding up every item in the range`() {
        val walked = (0 until 8).sumOf { index -> (measured[index]?.toFloat() ?: 4f).toDouble() }.toFloat()
        assertEquals(walked, measuredOrAverageSize(measured, start = 0, end = 8, average = 4f))
    }

    @Test
    fun `no item count is too long for it, since it walks only the measured items`() {
        assertEquals(Int.MAX_VALUE.toFloat(), measuredOrAverageSize(emptyMap(), start = 0, end = Int.MAX_VALUE, average = 1f))
    }
}
