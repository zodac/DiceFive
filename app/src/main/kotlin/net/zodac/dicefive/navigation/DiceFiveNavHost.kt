package net.zodac.dicefive.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.ui.about.AboutScreen
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.menu.MenuScreen
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.settings.SettingsScreen
import net.zodac.dicefive.ui.settings.SettingsViewModel
import net.zodac.dicefive.ui.setup.GameSetupScreen

@Composable
fun DiceFiveNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.MENU) {
        composable(Screen.MENU) {
            val context = LocalContext.current
            val inProgressGameRepository = remember { InProgressGameRepository(context) }
            val hasInProgressGame by inProgressGameRepository.hasInProgressGame.collectAsState(initial = false)
            MenuScreen(
                hasInProgressGame = hasInProgressGame,
                onContinue = { navController.navigate(Screen.playSetup(resume = true)) },
                onNewGame = { navController.navigate(Screen.PLAY_GRAPH) },
                onScores = { navController.navigate(Screen.SCORES) },
                onAchievements = { navController.navigate(Screen.ACHIEVEMENTS) },
                onSettings = { navController.navigate(Screen.SETTINGS) },
                onAbout = { navController.navigate(Screen.ABOUT) },
            )
        }

        navigation(startDestination = Screen.PLAY_SETUP_ROUTE, route = Screen.PLAY_GRAPH) {
            composable(
                route = Screen.PLAY_SETUP_ROUTE,
                arguments = listOf(navArgument("resume") { type = NavType.BoolType; defaultValue = false }),
            ) { backStackEntry ->
                val context = LocalContext.current
                val playGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.PLAY_GRAPH) }
                val viewModel = viewModel<GameViewModel>(playGraphEntry, factory = GameViewModel.factory(context))
                val resume = backStackEntry.arguments?.getBoolean("resume") ?: false

                if (!resume) {
                    GameSetupScreen(
                        viewModel = viewModel,
                        onStartGame = { navController.navigate(Screen.PLAY_GAME) },
                    )
                } else {
                    var resumed by remember { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(Unit) { resumed = viewModel.resumeGame() }

                    when (resumed) {
                        true -> LaunchedEffect(Unit) { navController.navigate(Screen.PLAY_GAME) }
                        // Nothing was actually found to resume (e.g. the save was cleared elsewhere) - fall back to setup.
                        false -> GameSetupScreen(
                            viewModel = viewModel,
                            onStartGame = { navController.navigate(Screen.PLAY_GAME) },
                        )
                        null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
            composable(Screen.PLAY_GAME) { backStackEntry ->
                val context = LocalContext.current
                val playGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.PLAY_GRAPH) }
                GameScreen(
                    viewModel = viewModel<GameViewModel>(playGraphEntry, factory = GameViewModel.factory(context)),
                    onBackToMenu = { navController.popBackStack(Screen.MENU, inclusive = false) },
                )
            }
        }

        composable(Screen.SCORES) {
            val context = LocalContext.current
            ScoresScreen(viewModel = viewModel(factory = ScoresViewModel.factory(context)))
        }
        composable(Screen.ACHIEVEMENTS) { AchievementsScreen() }
        composable(Screen.SETTINGS) {
            val context = LocalContext.current
            SettingsScreen(viewModel = viewModel(factory = SettingsViewModel.factory(context)))
        }
        composable(Screen.ABOUT) { AboutScreen() }
    }
}
