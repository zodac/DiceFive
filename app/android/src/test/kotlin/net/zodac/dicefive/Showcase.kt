package net.zodac.dicefive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/**
 * One composition for a test that looks at several things in turn - a rule takes its content only once a test, and a
 * test of its own for each thing costs a whole activity and composition apiece. [show] swaps what is drawn and waits
 * for it; each new thing is composed afresh, so nothing remembered by the last one carries over.
 */
internal class Showcase(private val compose: ComposeContentTestRule) {
    private var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var shown by mutableIntStateOf(0)

    fun show(next: @Composable () -> Unit) {
        val first = content == null
        content = next
        shown++
        if (first) compose.setContent { key(shown) { content?.invoke() } }
        compose.waitForIdle()
    }
}
