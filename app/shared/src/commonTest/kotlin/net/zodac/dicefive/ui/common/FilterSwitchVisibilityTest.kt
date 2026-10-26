package net.zodac.dicefive.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.UiTest

class FilterSwitchVisibilityTest : UiTest() {

    private var allUnlocked by mutableStateOf<Boolean?>(null)
    private var filterOn by mutableStateOf(false)
    private var state = FilterSwitchState(shown = true, enabled = true)

    private fun start(allUnlocked: Boolean?, filterOn: Boolean) {
        this.allUnlocked = allUnlocked
        this.filterOn = filterOn
        compose.setContent { state = rememberFilterSwitchState(this.allUnlocked, this.filterOn) }
        compose.waitForIdle()
    }

    private fun change(allUnlocked: Boolean) {
        this.allUnlocked = allUnlocked
        compose.waitForIdle()
    }

    @Test
    fun `everything already unlocked on opening leaves the switch off, and a saved filter filters nothing`() = uiTest {
        start(allUnlocked = true, filterOn = true)
        assertFalse(state.shown)
        assertFalse(state.applied(true))
    }

    @Test
    fun `with something locked the switch is shown and enabled, and applies the filter`() = uiTest {
        start(allUnlocked = false, filterOn = true)
        assertTrue(state.shown)
        assertTrue(state.enabled)
        assertTrue(state.applied(true))
    }

    @Test
    fun `the last unlock while open with the filter on keeps the switch, disabled`() = uiTest {
        start(allUnlocked = false, filterOn = true)
        change(allUnlocked = true)
        assertTrue(state.shown)
        assertFalse(state.enabled)
    }

    @Test
    fun `the last unlock while open with the filter off takes the switch away`() = uiTest {
        start(allUnlocked = false, filterOn = false)
        change(allUnlocked = true)
        assertFalse(state.shown)
    }

    @Test
    fun `not knowing yet shows an enabled switch, and loading to all unlocked hides it`() = uiTest {
        start(allUnlocked = null, filterOn = true)
        assertEquals(true to true, state.shown to state.enabled)
        change(allUnlocked = true)
        assertFalse(state.shown)
    }
}
