package net.zodac.dicefive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import net.zodac.dicefive.ui.common.HoldScreenAwake
import net.zodac.dicefive.ui.common.LocalScreenIdle
import net.zodac.dicefive.ui.common.ScreenIdle
import net.zodac.dicefive.ui.common.wakesScreen

/** A touch wakes the screen without being taken from what's touched, and a composed [HoldScreenAwake] keeps animations running. */
@OptIn(ExperimentalTestApi::class)
class ScreenIdleUiTest : UiTest() {

    @Test
    fun aTouchWakesTheScreenAndStillReachesWhatsTouched() = uiTest {
        val screen = ScreenIdle()
        var wait = CompletableDeferred<Unit>()
        var clicks = 0
        setContent {
            LaunchedEffect(screen) { screen.runTimer { wait.await() } }
            Box(Modifier.fillMaxSize().wakesScreen(screen)) {
                Box(Modifier.size(100.dp).testTag(BUTTON_TAG).clickable { clicks++ })
            }
        }
        wait.complete(Unit)
        waitForIdle()
        assertTrue(screen.idle)

        wait = CompletableDeferred()
        onNodeWithTag(BUTTON_TAG).performTouchInput { click() }
        waitForIdle()
        assertFalse(screen.idle)
        assertEquals(1, clicks)
    }

    @Test
    fun anIdleScreenFreezesNothingWhileAHoldIsComposed() = uiTest {
        val screen = ScreenIdle()
        var held by mutableStateOf(true)
        val wait = CompletableDeferred<Unit>()
        setContent {
            CompositionLocalProvider(LocalScreenIdle provides screen) {
                LaunchedEffect(screen) { screen.runTimer { wait.await() } }
                if (held) HoldScreenAwake()
            }
        }
        wait.complete(Unit)
        waitForIdle()
        assertTrue(screen.idle)
        var ran = false
        screen.onInfiniteOperation { ran = true }
        assertTrue(ran, "Held, it runs at once")

        held = false
        waitForIdle()
        ran = false
        val frozen = withTimeoutOrNull(FROZEN_WAIT_MILLIS) { screen.onInfiniteOperation { ran = true } }
        assertEquals(null, frozen)
        assertFalse(ran, "Released, it waits")
    }

    private companion object {
        const val BUTTON_TAG = "button"
        const val FROZEN_WAIT_MILLIS = 50L
    }
}
