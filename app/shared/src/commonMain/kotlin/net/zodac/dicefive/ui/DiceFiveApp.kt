package net.zodac.dicefive.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.navigation.DiceFiveNavHost
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * The whole app, on whatever platform hosts it: each platform's entry point builds its
 * [AppContainer] and [PlatformServices] and hands them in here, and everything below is shared.
 */
@Composable
fun DiceFiveApp(container: AppContainer, platform: PlatformServices) {
    CompositionLocalProvider(LocalAppContainer provides container, LocalPlatformServices provides platform) {
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
