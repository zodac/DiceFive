package net.zodac.dicefive

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import net.zodac.dicefive.navigation.DiceFiveNavHost
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.theme.DiceFiveTheme

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
        setContent {
            DiceFiveTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Above the NavHost, not inside it: the burst of banners at the end of a game
                    // has to survive the move from the board to the results screen and on to the
                    // menu, which a per-destination overlay wouldn't.
                    AchievementBannerHost(modifier = Modifier.fillMaxSize()) {
                        DiceFiveNavHost()
                    }
                }
            }
        }
    }
}
