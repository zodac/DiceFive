package net.zodac.dicefive.ui.common

import androidx.compose.foundation.lazy.LazyListItemInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/** The list scrollbar measures from an item drawn where it lies in the list - never a sticky header pinned at the top. */
class ScrollbarTopItemTest {

    private class Item(override val index: Int, override val offset: Int, override val size: Int = 100) : LazyListItemInfo {
        override val key: Any = index
        override val contentType: Any? = null
    }

    @Test
    fun `a header pinned at the top is passed over for the row scrolled under it`() {
        // The header (index 0) pinned at 0, listed first; its rows scrolled up under it, the third partly.
        val visible = listOf(Item(0, offset = 0), Item(3, offset = -40), Item(4, offset = 60), Item(5, offset = 160))
        assertEquals(3, anchorItem(visible).index)
    }

    @Test
    fun `a pinned header pushed up by the next is passed over though it's the only item above the top`() {
        // Header 0 pinned and pushed up to -30; its rows have scrolled away; the next header (index 6) starts below it.
        val visible = listOf(Item(0, offset = -30), Item(6, offset = 26), Item(7, offset = 90))
        assertEquals(6, anchorItem(visible).index)
    }

    @Test
    fun `a header just pinned is passed over though it's next in line to the row under it`() {
        // Header 14 drawn pinned at 0 though it lies 54 above; the row after it is where it lies.
        val visible = listOf(Item(14, offset = 0, size = 60), Item(15, offset = 114), Item(16, offset = 222))
        assertEquals(15, anchorItem(visible).index)
    }

    @Test
    fun `a list of one visible item measures from it`() {
        assertEquals(7, anchorItem(listOf(Item(7, offset = -10))).index)
    }
}
