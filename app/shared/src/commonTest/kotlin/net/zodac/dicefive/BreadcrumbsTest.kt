package net.zodac.dicefive

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.app.Breadcrumbs

/** The breadcrumbs keep the last few things done, oldest first, and no more. */
class BreadcrumbsTest {

    @Test
    fun `only the last MAX are kept - oldest first`() {
        val actions = (1..Breadcrumbs.MAX + 5).map { "Action $it" }
        actions.forEach(Breadcrumbs::record)
        assertEquals(actions.takeLast(Breadcrumbs.MAX), Breadcrumbs.recent)
    }
}
