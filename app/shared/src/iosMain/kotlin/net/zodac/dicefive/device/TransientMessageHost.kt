package net.zodac.dicefive.device

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/** About as long as an Android `Toast.LENGTH_SHORT`. */
private const val MESSAGE_MILLIS = 2_000L

/**
 * iOS has no toast, so [IosPlatformServices.showTransientMessage]'s messages are drawn here instead:
 * a snackbar over the bottom of [content] for a couple of seconds, needing no response.
 */
@Composable
internal fun TransientMessageHost(platform: IosPlatformServices, content: @Composable BoxScope.() -> Unit) {
    val message by platform.transientMessage.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        message?.let { text ->
            LaunchedEffect(text) {
                delay(MESSAGE_MILLIS)
                platform.transientMessageShown()
            }
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            ) {
                Text(text = text)
            }
        }
    }
}
