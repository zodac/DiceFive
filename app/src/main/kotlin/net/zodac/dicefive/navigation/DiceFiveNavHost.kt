package net.zodac.dicefive.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import net.zodac.dicefive.ui.about.AboutScreen
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.menu.MenuScreen
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.settings.SettingsScreen
import net.zodac.dicefive.ui.setup.GameSetupScreen

@Composable
fun DiceFiveNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.MENU) {
        composable(Screen.MENU) {
            MenuScreen(
                onPlay = { navController.navigate(Screen.PLAY_GRAPH) },
                onScores = { navController.navigate(Screen.SCORES) },
                onAchievements = { navController.navigate(Screen.ACHIEVEMENTS) },
                onSettings = { navController.navigate(Screen.SETTINGS) },
                onAbout = { navController.navigate(Screen.ABOUT) },
            )
        }

        navigation(startDestination = Screen.PLAY_SETUP, route = Screen.PLAY_GRAPH) {
            composable(Screen.PLAY_SETUP) { backStackEntry ->
                val playGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.PLAY_GRAPH) }
                GameSetupScreen(
                    viewModel = viewModel(playGraphEntry),
                    onStartGame = { navController.navigate(Screen.PLAY_GAME) },
                )
            }
            composable(Screen.PLAY_GAME) { backStackEntry ->
                val playGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.PLAY_GRAPH) }
                GameScreen(viewModel = viewModel<GameViewModel>(playGraphEntry))
            }
        }

        composable(Screen.SCORES) { ScoresScreen() }
        composable(Screen.ACHIEVEMENTS) { AchievementsScreen() }
        composable(Screen.SETTINGS) { SettingsScreen() }
        composable(Screen.ABOUT) { AboutScreen() }
    }
}
