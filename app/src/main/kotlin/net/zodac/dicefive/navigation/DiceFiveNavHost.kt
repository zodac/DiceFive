package net.zodac.dicefive.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
import net.zodac.dicefive.ui.common.BrandBackdrop
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.menu.MenuScreen
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.settings.SettingsScreen
import net.zodac.dicefive.ui.settings.SettingsViewModel
import net.zodac.dicefive.ui.setup.GameSetupScreen
import net.zodac.dicefive.ui.statistics.StatisticsScreen
import net.zodac.dicefive.ui.statistics.StatisticsViewModel
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.styles.StylesViewModel

/**
 * Half of Navigation Compose's own default (a 700ms cross-fade), which is slow enough to feel like
 * the app is thinking between a menu tap and the screen arriving. Applied at the [NavHost] so every
 * destination moves at the same speed rather than each one setting its own.
 */
private const val SCREEN_TRANSITION_MILLIS = 350

@Composable
fun DiceFiveNavHost(navController: NavHostController = rememberNavController()) {
    val fadeIn = fadeIn(animationSpec = tween(SCREEN_TRANSITION_MILLIS))
    val fadeOut = fadeOut(animationSpec = tween(SCREEN_TRANSITION_MILLIS))

    NavHost(
        navController = navController,
        startDestination = Screen.MENU,
        enterTransition = { fadeIn },
        exitTransition = { fadeOut },
        popEnterTransition = { fadeIn },
        popExitTransition = { fadeOut },
    ) {
        composable(Screen.MENU) {
            val context = LocalContext.current
            val inProgressGameRepository = remember { InProgressGameRepository(context) }
            val hasInProgressGame by inProgressGameRepository.hasInProgressGame.collectAsState(initial = false)
            MenuScreen(
                hasInProgressGame = hasInProgressGame,
                onContinue = { navController.navigate(Screen.playSetup(resume = true)) },
                onNewGame = { navController.navigate(Screen.PLAY_GRAPH) },
                onScores = { navController.navigate(Screen.SCORES) },
                onStatistics = { navController.navigate(Screen.STATISTICS) },
                onAchievements = { navController.navigate(Screen.ACHIEVEMENTS) },
                onStyles = { navController.navigate(Screen.STYLES) },
                onSettings = { navController.navigate(Screen.SETTINGS) },
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
                        onBack = { navController.navigateUp() },
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
                            onBack = { navController.navigateUp() },
                        )
                        null -> BrandBackdrop {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
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
            ScoresScreen(
                viewModel = viewModel(factory = ScoresViewModel.factory(context)),
                onBack = { navController.navigateUp() },
            )
        }
        composable(Screen.STATISTICS) {
            val context = LocalContext.current
            StatisticsScreen(
                viewModel = viewModel(factory = StatisticsViewModel.factory(context)),
                onBack = { navController.navigateUp() },
            )
        }
        composable(Screen.ACHIEVEMENTS) {
            val context = LocalContext.current
            AchievementsScreen(
                viewModel = viewModel(factory = AchievementsViewModel.factory(context)),
                onBack = { navController.navigateUp() },
            )
        }
        composable(Screen.SETTINGS) {
            val context = LocalContext.current
            SettingsScreen(
                viewModel = viewModel(factory = SettingsViewModel.factory(context)),
                onBack = { navController.navigateUp() },
            )
        }
        composable(Screen.STYLES) {
            val context = LocalContext.current
            StylesScreen(
                viewModel = viewModel(factory = StylesViewModel.factory(context)),
                onBack = { navController.navigateUp() },
            )
        }
    }
}
