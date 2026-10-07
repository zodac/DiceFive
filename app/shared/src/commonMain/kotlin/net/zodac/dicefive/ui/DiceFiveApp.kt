package net.zodac.dicefive.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.achievements.AchievementScrollRequests
import net.zodac.dicefive.data.achievements.StyleScrollRequests
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.navigation.DiceFiveNavHost
import net.zodac.dicefive.navigation.Screen
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.common.DriftState
import net.zodac.dicefive.ui.common.LocalAnimationLevel
import net.zodac.dicefive.ui.common.LocalDriftState
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.game.LeaveGameConfirmation
import net.zodac.dicefive.ui.game.LeaveGameConfirmationDialog
import net.zodac.dicefive.ui.game.LocalLeaveGameConfirmation
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * The whole app, on whatever platform hosts it: each platform's entry point builds its
 * [AppContainer] and [PlatformServices] and hands them in here, and everything below is shared.
 */
@Composable
fun DiceFiveApp(container: AppContainer, platform: PlatformServices) {
    // Held here, above the NavHost, so the menu's drifting dice carry on unbroken across every screen off it.
    val driftState = remember { DriftState() }
    // Lifecycle-aware, so the system-settings observer behind it is only registered while the app is in front.
    val systemReduceMotion by remember(platform) { platform.reduceMotion() }.collectAsStateWithLifecycle(initialValue = false)
    // The player's own "Animations" level: Off asks for the same as the system's reduced motion, and every level
    // below High caps the frame rate of whatever moves - see PlatformServices.capFrameRate. Only the player's
    // choice caps it: the system's reduced motion stops the motion, not the frames.
    val savedLevel by container.settingsRepository.animationLevel.collectAsStateWithLifecycle(initialValue = AnimationLevel.default)
    LaunchedEffect(platform, savedLevel) { platform.capFrameRate(savedLevel.maxFramesPerSecond) }
    val animationLevel = if (systemReduceMotion) AnimationLevel.OFF else savedLevel
    val reduceMotion = !animationLevel.gameplayMotion
    val leaveConfirmation = remember { LeaveGameConfirmation() }
    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalAnimationLevel provides animationLevel,
        LocalAppContainer provides container,
        LocalPlatformServices provides platform,
        LocalDriftState provides driftState,
        LocalLeaveGameConfirmation provides leaveConfirmation,
    ) {
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
                    isOnGameScreen = { navController.currentDestination?.route == Screen.PLAY_GAME },
                    onAchievementSelected = { achievement ->
                        val alreadyOnAchievements = navController.currentDestination?.route == Screen.ACHIEVEMENTS
                        // The request has to be made before navigating, not after: if the
                        // Achievements screen isn't open yet, its own collector only starts once
                        // it composes, and the request's replay is what lets it still catch this.
                        AchievementScrollRequests.request(achievement.id, animate = alreadyOnAchievements)
                        if (!alreadyOnAchievements) {
                            navController.navigate(Screen.ACHIEVEMENTS)
                        }
                    },
                    onStylesSelected = { styles ->
                        val alreadyOnStyles = navController.currentDestination?.route == Screen.STYLES
                        // Before navigating, for the same reason as the achievement request above.
                        StyleScrollRequests.request(styles, animate = alreadyOnStyles)
                        if (!alreadyOnStyles) {
                            navController.navigate(Screen.STYLES)
                        }
                    },
                ) {
                    DiceFiveNavHost(navController = navController)
                }
                LeaveGameConfirmationDialog(leaveConfirmation)
            }
        }
    }
}
