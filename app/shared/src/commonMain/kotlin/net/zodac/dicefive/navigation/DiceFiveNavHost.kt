package net.zodac.dicefive.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
import net.zodac.dicefive.ui.common.BrandBackdrop
import net.zodac.dicefive.ui.common.LocalDriftState
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.menu.MenuScreen
import net.zodac.dicefive.ui.menu.MenuViewModel
import net.zodac.dicefive.ui.rules.RulesScreen
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.settings.SettingsScreen
import net.zodac.dicefive.ui.settings.SettingsViewModel
import net.zodac.dicefive.ui.setup.GameSetupScreen
import net.zodac.dicefive.ui.statistics.StatisticsScreen
import net.zodac.dicefive.ui.statistics.StatisticsViewModel
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.styles.StylesWarmUp
import net.zodac.dicefive.ui.styles.StylesViewModel

/**
 * Half of Navigation Compose's own default (a 700ms cross-fade), which is slow enough to feel like
 * the app is thinking between a menu tap and the screen arriving. Applied at the [NavHost] so every
 * destination moves at the same speed rather than each one setting its own.
 */
private const val SCREEN_TRANSITION_MILLIS = 350

/** How long continuing a game can take to load before it's worth showing a spinner for. */
private const val RESUME_SPINNER_DELAY_MILLIS = 500L

@Composable
fun DiceFiveNavHost(navController: NavHostController = rememberNavController()) {
    val container = LocalAppContainer.current
    // Under reduced motion a page just replaces the last one, with no cross-fade.
    val reduceMotion = LocalReduceMotion.current
    val fadeIn = if (reduceMotion) EnterTransition.None else fadeIn(animationSpec = tween(SCREEN_TRANSITION_MILLIS))
    val fadeOut = if (reduceMotion) ExitTransition.None else fadeOut(animationSpec = tween(SCREEN_TRANSITION_MILLIS))

    NavHost(
        navController = navController,
        startDestination = Screen.MENU,
        enterTransition = { fadeIn },
        exitTransition = { fadeOut },
        popEnterTransition = { fadeIn },
        popExitTransition = { fadeOut },
    ) {
        composable(Screen.MENU) {
            val hasInProgressGame by container.inProgressGameRepository.hasInProgressGame.collectAsStateWithLifecycle(initialValue = null)
            val menuViewModel = viewModel<MenuViewModel>(factory = MenuViewModel.factory(container))
            val logoStyles by menuViewModel.logoStyles.collectAsStateWithLifecycle()
            val savedStyles by container.savedStyles.collectAsStateWithLifecycle()
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Under the menu, which is opaque: runs the Styles page's drawing code once while the
                // menu idles, so opening Styles isn't the first time it runs - see StylesWarmUp.
                StylesWarmUp(picks = savedStyles, width = maxWidth)
                MenuScreen(
                    hasInProgressGame = hasInProgressGame,
                    onContinue = { navController.navigate(Screen.playSetup(resume = true)) },
                    onNewGame = { navController.navigate(Screen.PLAY_GRAPH) },
                    onAchievements = { navController.navigate(Screen.ACHIEVEMENTS) },
                    onScores = { navController.navigate(Screen.SCORES) },
                    onStatistics = { navController.navigate(Screen.STATISTICS) },
                    onStyles = { navController.navigate(Screen.STYLES) },
                    onRules = { navController.navigate(Screen.RULES) },
                    onSettings = { navController.navigate(Screen.SETTINGS) },
                    logoStyles = logoStyles,
                    onDiceTap = menuViewModel::onDiceTapped,
                )
            }
        }

        navigation(startDestination = Screen.PLAY_SETUP_ROUTE, route = Screen.PLAY_GRAPH) {
            composable(
                route = Screen.PLAY_SETUP_ROUTE,
                arguments = listOf(navArgument("resume") { type = NavType.BoolType; defaultValue = false }),
            ) { backStackEntry ->
                val playGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.PLAY_GRAPH) }
                val viewModel = viewModel<GameViewModel>(playGraphEntry, factory = GameViewModel.factory(container))
                val resume = backStackEntry.arguments?.read { getBooleanOrNull("resume") } ?: false

                if (!resume) {
                    GameSetupScreen(
                        viewModel = viewModel,
                        onStartGame = { navController.navigate(Screen.PLAY_GAME) },
                        onBack = { navController.navigateUp() },
                    )
                } else {
                    var resumed by remember { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(Unit) { resumed = viewModel.resumeGame() }

                    // While the game loads, and on the way to it, the game's own backdrop - the page it's about to be
                    // drawn on - so with no page transitions to cover the hand-off (reduced motion), nothing else
                    // shows in between. The spinner only once the load has been slow enough to need one.
                    when (resumed) {
                        true -> {
                            BrandBackdrop(showDice = false) {}
                            LaunchedEffect(Unit) { navController.navigate(Screen.PLAY_GAME) }
                        }
                        // Nothing was actually found to resume (e.g. the save was cleared elsewhere) - fall back to setup.
                        false -> GameSetupScreen(
                            viewModel = viewModel,
                            onStartGame = { navController.navigate(Screen.PLAY_GAME) },
                            onBack = { navController.navigateUp() },
                        )
                        null -> BrandBackdrop(showDice = false) {
                            var slow by remember { mutableStateOf(false) }
                            val lifecycle = LocalLifecycleOwner.current.lifecycle
                            LaunchedEffect(Unit) {
                                lifecycle.delayWhileResumed(RESUME_SPINNER_DELAY_MILLIS)
                                slow = true
                            }
                            if (slow) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
            composable(Screen.PLAY_GAME) { backStackEntry ->
                val playGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.PLAY_GRAPH) }
                val gameViewModel = viewModel<GameViewModel>(playGraphEntry, factory = GameViewModel.factory(container))
                // The menu's dice start over after a game that was left unfinished, not while the setup screen is
                // still fading out. A finished game's results page drifts them like any other, so the menu carries on.
                val driftState = LocalDriftState.current
                val gameOver by rememberUpdatedState(gameViewModel.game.collectAsStateWithLifecycle().value?.isGameOver == true)
                DisposableEffect(driftState) { onDispose { if (!gameOver) driftState?.reset() } }
                GameScreen(
                    viewModel = gameViewModel,
                    onBackToMenu = { navController.popBackStack(Screen.MENU, inclusive = false) },
                )
            }
        }

        composable(Screen.SCORES) {
            ScoresScreen(
                viewModel = viewModel(factory = ScoresViewModel.factory(container)),
                onBack = { navController.navigateUp() },
            )
        }
        composable(Screen.STATISTICS) {
            StatisticsScreen(
                viewModel = viewModel(factory = StatisticsViewModel.factory(container)),
                onBack = { navController.navigateUp() },
            )
        }
        composable(Screen.ACHIEVEMENTS) {
            AchievementsScreen(
                viewModel = viewModel(factory = AchievementsViewModel.factory(container)),
                onBack = { navController.navigateUp() },
            )
        }
        composable(Screen.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel(factory = SettingsViewModel.factory(container)),
                onBack = { navController.navigateUp() },
            )
        }
        composable(Screen.STYLES) {
            StylesScreen(
                viewModel = viewModel(factory = StylesViewModel.factory(container)),
                onBack = { navController.navigateUp() },
            )
        }
        composable(Screen.RULES) {
            RulesScreen(onBack = { navController.navigateUp() })
        }
    }
}
