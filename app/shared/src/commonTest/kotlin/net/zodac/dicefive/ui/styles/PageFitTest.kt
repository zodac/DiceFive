package net.zodac.dicefive.ui.styles

import kotlin.test.Test
import androidx.compose.ui.unit.IntSize
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The scale the Styles page's cards are drawn at to fit its screen - see StylesWarmUp. */
class PageFitTest {

    @Test
    fun `a page that fits is left full size`() {
        assertEquals(1f, fittedPageScale(full = 900, smallest = 800, viewport = 900))
    }

    @Test
    fun `a page that would still scroll at the smallest scale is left full size`() {
        assertEquals(1f, fittedPageScale(full = 1000, smallest = 870, viewport = 850))
    }

    @Test
    fun `a page a little too tall shrinks only as far as it takes to fit`() {
        // 1000 at full size and 838 at 0.75: 648 of it scales and 352 doesn't - so 930 fits at about 0.89.
        val scale = fittedPageScale(full = 1000, smallest = 838, viewport = 930)
        assertTrue(scale in 0.88f..0.89f, "$scale")
        assertTrue(352 + 648 * scale <= 930)
    }

    @Test
    fun `a saved fit reads back as it was saved`() {
        val fit = PageFit(PageFitKey(IntSize(1080, 2400), density = 2.9375f, fontScale = 1.15f), scale = 0.757f)
        assertEquals(fit, PageFit.decode(fit.encode()))
    }

    @Test
    fun `nothing saved or something unreadable is no fit at all`() {
        assertNull(PageFit.decode(null))
        assertNull(PageFit.decode("not a fit"))
    }
}
