package net.zodac.dicefive

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.device.AndroidPlatformServices
import net.zodac.dicefive.device.CappedFrameClock
import net.zodac.dicefive.device.preferRefreshRateFor
import net.zodac.dicefive.ui.DiceFiveApp
import net.zodac.dicefive.ui.common.stringsLanguageConfiguration

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // On a phone whose language the app has no translation for, the whole Activity runs in the language the strings
        // fell back to (English): Android's own text and the layout direction too, not just the app's words.
        stringsLanguageConfiguration()?.let(::applyOverrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draws behind the system bars, which is the platform default from Android 15 (targetSdk
        // 35) onwards rather than an opt-in: screens handle the insets themselves, via Scaffold or
        // windowInsetsPadding, so the backdrop runs edge to edge under a transparent status bar.
        // The bars are told the app is dark: left to follow the system, a phone in light mode would
        // draw dark status bar icons on the app's dark page.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = AndroidAppContainer.get(this)
        val platform = AndroidPlatformServices(this)
        // The window's recomposer, built as Compose would build it itself, but on a frame clock that can be capped
        // for the player's "Animations" level - see CappedFrameClock. setContent's ComposeView finds it on the
        // decor view, its parent, and composes with it instead of making its own.
        if (useCappedFrameClock) {
            val ui = AndroidUiDispatcher.CurrentThread
            val frameClock = CappedFrameClock(checkNotNull(ui[MonotonicFrameClock]) { "The UI dispatcher has no frame clock" })
            window.decorView.compositionContext = window.decorView.createLifecycleAwareWindowRecomposer(ui + frameClock, lifecycle)
        }
        // And a capped level asks the screen for 60Hz where it can drop to it, rather than only skipping its refreshes.
        lifecycleScope.launch {
            CappedFrameClock.maxFramesPerSecondChanges.collect { window.preferRefreshRateFor(it) }
        }
        setContent {
            DiceFiveApp(container = container, platform = platform)
        }
    }

    companion object {
        /**
         * Off only in a test driving this Activity through a Compose test rule: the rule installs its own window
         * recomposer, on its own test clock (through `WindowRecomposerPolicy`), which one set here would bypass -
         * the test's clock would then never move the app.
         */
        @VisibleForTesting
        var useCappedFrameClock = true
    }
}
