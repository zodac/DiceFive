package net.zodac.dicefive

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.device.AndroidPlatformServices
import net.zodac.dicefive.ui.DiceFiveApp

class MainActivity : ComponentActivity() {

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
        setContent {
            DiceFiveApp(container = container, platform = platform)
        }
    }
}
