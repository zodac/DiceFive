package net.zodac.dicefive.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.achievements.AchievementScrollRequests
import net.zodac.dicefive.navigation.DiceFiveNavHost
import net.zodac.dicefive.navigation.Screen
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
                // Hoisted out of DiceFiveNavHost (which otherwise creates its own) so a banner
                // long press, handled above the NavHost, can navigate the same controller the
                // NavHost itself renders from.
                val navController = rememberNavController()
                // Above the NavHost, not inside it: the burst of banners at the end of a game
                // has to survive the move from the board to the results screen and on to the
                // menu, which a per-destination overlay wouldn't.
                AchievementBannerHost(
                    modifier = Modifier.fillMaxSize(),
                    onAchievementSelected = { achievement ->
                        // The request has to be made before navigating, not after: if the
                        // Achievements screen isn't open yet, its own collector only starts once
                        // it composes, and the request's replay is what lets it still catch this.
                        AchievementScrollRequests.request(achievement.id)
                        if (navController.currentDestination?.route != Screen.ACHIEVEMENTS) {
                            navController.navigate(Screen.ACHIEVEMENTS)
                        }
                    },
                ) {
                    DiceFiveNavHost(navController = navController)
                }
            }
        }
    }
}
