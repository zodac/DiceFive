package net.zodac.dicefive.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.game.TutorialScript
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_default_player_name
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
import net.zodac.dicefive.ui.common.LocalDriftState
import net.zodac.dicefive.ui.common.LocalPageCover
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.PageCover
import net.zodac.dicefive.ui.common.PageCoverOverlay
import net.zodac.dicefive.ui.common.rememberPageCover
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.game.GameWarmUp
import net.zodac.dicefive.ui.game.SavedGameWarmUp
import net.zodac.dicefive.ui.menu.MenuScreen
import net.zodac.dicefive.ui.menu.MenuViewModel
import net.zodac.dicefive.ui.rules.RulesScreen
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresWarmUp
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
 * The app's pages. Opening one is done under the [cover] (see [PageCover]), never with the NavHost's own transitions: one
 * page goes, then the next is built out of sight and comes. Going back - the system's back, a page's back arrow, leaving
 * a game - shows the page it returns to at once.
 */
@Composable
fun DiceFiveNavHost(navController: NavHostController = rememberNavController(), cover: PageCover = rememberPageCover()) {
    val container = LocalAppContainer.current
    val reduceMotion = LocalReduceMotion.current
    // Made once the page in front has faded out, unless a back press in the meantime has already left it.
    val change = { navigation: () -> Unit ->
        val from = navController.currentBackStackEntry
        cover.change(reduceMotion) { if (navController.currentBackStackEntry == from) navigation() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPageCover provides cover) {
            NavHost(
                navController = navController,
                startDestination = Screen.MENU,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                pages(navController, container, change)
            }
        }
        PageCoverOverlay(cover)
    }
}

/** Each page, opened by a [change] of page under the cover. */
private fun NavGraphBuilder.pages(navController: NavHostController, container: AppContainer, change: (() -> Unit) -> Unit) {
    val navigate = { route: String -> change { navController.navigate(route) } }
    val navigateUp: () -> Unit = { navController.navigateUp() }
    val backToMenu: () -> Unit = { navController.popBackStack(Screen.MENU, inclusive = false) }

    composable(Screen.MENU) {
        val hasInProgressGame by container.inProgressGameRepository.hasInProgressGame.collectAsStateWithLifecycle(initialValue = null)
        val menuViewModel = viewModel<MenuViewModel>(factory = MenuViewModel.factory(container))
        val logoStyles by menuViewModel.logoStyles.collectAsStateWithLifecycle()
        val savedStyles by container.savedStyles.collectAsStateWithLifecycle()
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Under the menu, which is opaque: runs the Styles page's drawing code once while the
            // menu idles, so opening Styles isn't the first time it runs - see StylesWarmUp.
            val warmUpScope = rememberCoroutineScope()
            // Opens the scores database now, while the menu idles, so the Leaderboard's first read isn't
            // also the database's first open.
            LaunchedEffect(Unit) { container.scoreRepository.totalCount() }
            // The warm-ups' copies of a page are never what the cover waits on.
            CompositionLocalProvider(LocalPageCover provides null) {
                StylesWarmUp(
                    picks = savedStyles,
                    width = maxWidth,
                    onPageFit = { fit -> warmUpScope.launch { container.settingsRepository.setStylesPageFit(fit) } },
                )
                // Likewise for the Leaderboard,
                ScoresWarmUp(width = maxWidth)
                // and for the game Continue resumes, which has no setup form to warm it up under.
                SavedGameWarmUp(savedStyles, hasInProgressGame == true, container.inProgressGameRepository::load)
            }
            MenuScreen(
                hasInProgressGame = hasInProgressGame,
                // Straight to the board, which loads the saved game itself (see below).
                onContinue = { navigate(Screen.PLAY_GAME) },
                onNewGame = { navigate(Screen.PLAY_GRAPH) },
                onAchievements = { navigate(Screen.ACHIEVEMENTS) },
                onScores = { navigate(Screen.SCORES) },
                onStatistics = { navigate(Screen.STATISTICS) },
                onStyles = { navigate(Screen.STYLES) },
                onRules = { navigate(Screen.RULES) },
                onSettings = { navigate(Screen.SETTINGS) },
                logoStyles = logoStyles,
                onDiceTap = menuViewModel::onDiceTapped,
            )
        }
    }

    navigation(startDestination = Screen.PLAY_SETUP, route = Screen.PLAY_GRAPH) {
        composable(Screen.PLAY_SETUP) { backStackEntry ->
            val playGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.PLAY_GRAPH) }
            val viewModel = viewModel<GameViewModel>(playGraphEntry, factory = GameViewModel.factory(container))
            Box {
                // Under the form, which is opaque: draws the game's art once while the form is filled in, so the
                // game's first frame isn't also its art's first paint - see GameWarmUp.
                GameWarmUp(viewModel)
                GameSetupScreen(
                    viewModel = viewModel,
                    onStartGame = { navigate(Screen.PLAY_GAME) },
                    onTutorial = { navigate(Screen.TUTORIAL) },
                    onBack = navigateUp,
                )
            }
        }
        composable(Screen.PLAY_GAME) { backStackEntry ->
            val playGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.PLAY_GRAPH) }
            val gameViewModel = viewModel<GameViewModel>(playGraphEntry, factory = GameViewModel.factory(container))
            // The menu's dice start over after a game that was left unfinished, as it's left. A finished game's results
            // page drifts them like any other, so the menu carries on.
            val driftState = LocalDriftState.current
            val gameOver by rememberUpdatedState(gameViewModel.game.collectAsStateWithLifecycle().value?.isGameOver == true)
            DisposableEffect(driftState) { onDispose { if (!gameOver) driftState?.reset() } }
            // Continue comes straight here, to a fresh view model holding no game, and so does Android rebuilding
            // this back stack after ending the app's process in the background. Every move is saved, so the game is
            // loaded back, under the page cover; with nothing saved (cleared elsewhere), it's back to the menu.
            LaunchedEffect(gameViewModel) {
                if (gameViewModel.game.value == null && !gameViewModel.resumeGame()) {
                    navController.popBackStack(Screen.MENU, inclusive = false)
                }
            }
            GameScreen(viewModel = gameViewModel, onBackToMenu = backToMenu)
        }
    }

    composable(Screen.SCORES) {
        ScoresScreen(
            viewModel = viewModel(factory = ScoresViewModel.factory(container)),
            onBack = navigateUp,
        )
    }
    composable(Screen.STATISTICS) {
        StatisticsScreen(
            viewModel = viewModel(factory = StatisticsViewModel.factory(container)),
            onBack = navigateUp,
        )
    }
    composable(Screen.ACHIEVEMENTS) {
        AchievementsScreen(
            viewModel = viewModel(factory = AchievementsViewModel.factory(container)),
            onBack = navigateUp,
        )
    }
    composable(Screen.SETTINGS) {
        SettingsScreen(
            viewModel = viewModel(factory = SettingsViewModel.factory(container)),
            onBack = navigateUp,
        )
    }
    composable(Screen.STYLES) {
        StylesScreen(
            viewModel = viewModel(factory = StylesViewModel.factory(container)),
            onBack = navigateUp,
        )
    }
    composable(Screen.RULES) {
        RulesScreen(onBack = navigateUp, onStartTutorial = { navigate(Screen.TUTORIAL) })
    }
    // The guided game, from How to Play or the New Game form, on a view model of its own: the play graph's game - the one
    // Continue resumes - is never touched. Done goes back to wherever it was started from.
    composable(Screen.TUTORIAL) {
        val tutorialViewModel = viewModel<GameViewModel>(factory = GameViewModel.tutorialFactory(container, TutorialScript.STANDARD))
        val defaultName = stringResource(Res.string.common_default_player_name, 1)
        // From its first step each time it's opened - and again if Android ended the app while it was in the background.
        LaunchedEffect(tutorialViewModel) {
            if (tutorialViewModel.game.value == null) tutorialViewModel.startTutorial(defaultName)
        }
        GameScreen(
            viewModel = tutorialViewModel,
            onBackToMenu = navigateUp,
            onNewGame = { change { navController.navigate(Screen.PLAY_GRAPH) { popUpTo(Screen.MENU) } } },
        )
    }
}
