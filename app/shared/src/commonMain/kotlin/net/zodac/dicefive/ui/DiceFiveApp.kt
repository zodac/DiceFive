package net.zodac.dicefive.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.Breadcrumbs
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.orNullOnFailure
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.navigation.AchievementScrollRequest
import net.zodac.dicefive.navigation.AchievementScrollRequests
import net.zodac.dicefive.navigation.DiceFiveNavHost
import net.zodac.dicefive.navigation.Screen
import net.zodac.dicefive.navigation.StyleScrollRequest
import net.zodac.dicefive.navigation.StyleScrollRequests
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.online.LocalCloudSave
import net.zodac.dicefive.online.LocalGameServices
import net.zodac.dicefive.online.NoGameServices
import net.zodac.dicefive.online.LocalOnlineSyncStatus
import net.zodac.dicefive.online.OnlineSync
import net.zodac.dicefive.online.OnlineSyncRequests
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.purchases.LocalStore
import net.zodac.dicefive.purchases.NoStore
import net.zodac.dicefive.purchases.Store
import net.zodac.dicefive.ui.style.LocalTricolourMarks
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.common.DatabaseFailurePage
import net.zodac.dicefive.ui.common.DriftState
import net.zodac.dicefive.ui.common.FramePaces
import net.zodac.dicefive.ui.common.LastMotionSettings
import net.zodac.dicefive.ui.common.LocalDriftState
import net.zodac.dicefive.ui.common.LocalFramePace
import net.zodac.dicefive.ui.common.LocalFramePaces
import net.zodac.dicefive.ui.common.LocalMotionSettings
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.LocalScreenIdle
import net.zodac.dicefive.ui.common.ScreenIdle
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.common.rememberPageCover
import net.zodac.dicefive.ui.common.wakesScreen
import net.zodac.dicefive.ui.game.LeaveGameConfirmation
import net.zodac.dicefive.ui.game.LeaveGameConfirmationDialog
import net.zodac.dicefive.ui.game.LocalLeaveGameConfirmation
import net.zodac.dicefive.ui.game.LocalDiceHaptics
import net.zodac.dicefive.ui.game.LocalSoundEffects
import net.zodac.dicefive.ui.game.rememberAppHaptics
import net.zodac.dicefive.ui.game.rememberAppSoundEffects
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * The whole app, on whatever platform hosts it: each platform's entry point builds its
 * [AppContainer] and [PlatformServices] (and, where the platform has them, its [GameServices] and [Store]) and hands them in here, and everything below is shared.
 */
@OptIn(FlowPreview::class)
@Composable
fun DiceFiveApp(
    container: AppContainer,
    platform: PlatformServices,
    gameServices: GameServices = NoGameServices,
    store: Store = NoStore,
    /** What freezes the window's infinite animations, where it's the policy its recomposer runs under. */
    screenIdle: ScreenIdle = remember { ScreenIdle() },
) {
    // Held here, above the NavHost, so the menu's drifting dice carry on unbroken across every screen off it.
    val driftState = remember { DriftState() }
    // Lifecycle-aware, so the system-settings observer behind it is only registered while the app is in front.
    val systemReduceMotion by remember(platform) { platform.reduceMotion() }.collectAsStateWithLifecycle(initialValue = false)
    // The player's own motion (the "Animations" level, or its Advanced settings): Menus & pages Off asks for the same as the
    // system's reduced motion, and the window's frames are capped at the fastest group's rate - see
    // PlatformServices.capFrameRate. Only the player's choice caps it: the system's reduced motion stops the motion,
    // not the frames.
    val savedMotion by container.settingsRepository.motionSettings.collectAsStateWithLifecycle(initialValue = AnimationLevel.default.motion)
    LaunchedEffect(platform, savedMotion) { platform.capFrameRate(savedMotion.maxFramesPerSecond) }
    // Null until read, so a saved lock isn't first undone for a frame by the default.
    val orientation by container.settingsRepository.screenOrientation.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(platform, orientation) { orientation?.let(platform::lockOrientation) }
    val motion = if (systemReduceMotion) AnimationLevel.OFF.motion else savedMotion
    val reduceMotion = !motion.menusMotion
    val framePaces = remember(motion) { FramePaces(motion) }
    SideEffect { LastMotionSettings.value = motion }
    val leaveConfirmation = remember { LeaveGameConfirmation() }
    // Achievements, scores and the cloud save are synced with the online service on signing in (and at each launch once
    // signed in), after every recorded change (one with no new unlock waits if the game screen is up), a few seconds after
    // a synced setting changes (a Styles pick), on leaving the game screen, on going to the background with something new
    // to save, and on coming back at least 60 seconds after the last sync; requests made while one runs fold into the next.
    val onlineSync = remember(container, gameServices) {
        OnlineSync(
            gameServices,
            container.achievementsRepository,
            container.onlineSettingsRepository,
            container.scoreRepository::leaderboardTotals,
            container.scoreRepository::bestUnpostedScore,
            LocalCloudSave(
                container.achievementsRepository,
                container.scoreRepository,
                container.settingsRepository,
                container.inProgressGameRepository,
                container.onlineSettingsRepository,
            ),
        )
    }
    LaunchedEffect(onlineSync) {
        // Only a sign-in's sync shows its progress on the Settings card; the ones after it are quiet. All of this runs
        // on the one main thread, so the flag needs no lock.
        var signInPending = false
        val signedIn = gameServices.state.map { it is GameServicesState.SignedIn }.distinctUntilChanged().filter { it }.map { signInPending = true }
        merge(signedIn, OnlineSyncRequests.requests).conflate().collect {
            val showProgress = signInPending
            signInPending = false
            // A sync that fails here (the scores can't be written) costs that sync: thrown on, it would take the app down,
            // and every later sync with it.
            orNullOnFailure { onlineSync.reconcile(showProgress) }
        }
    }
    LaunchedEffect(onlineSync) {
        gameServices.state.collect { if (it !is GameServicesState.SignedIn) onlineSync.forgetSignInSync() }
    }
    LaunchedEffect(container) {
        container.settingsRepository.syncedChanges.debounce(SETTINGS_SYNC_DELAY).collect { OnlineSyncRequests.request() }
    }
    // What the store says the player owns is kept on the device, which every style lock reads.
    LaunchedEffect(container, store) { container.purchasesRepository.follow(store) }
    // Only while in front of the player: pausing covers the background, the screen going off, and a system sheet on top.
    LifecycleResumeEffect(container) {
        container.playTime.resume()
        onPauseOrDispose { container.playTime.pause() }
    }
    // Put one device down and pick another up: the first saves, the second reads. A check that fails asks for the sync.
    val appSwitches = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        appSwitches.launch { if (orNullOnFailure { onlineSync.needsSyncOnLeaving() } != false) OnlineSyncRequests.request() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        appSwitches.launch { if (orNullOnFailure { onlineSync.needsSyncOnReturning() } != false) OnlineSyncRequests.request() }
    }
    // Infinite animations freeze after a minute with nobody there, and move on again at the next touch or on coming back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(screenIdle, lifecycle) { screenIdle.runTimer(lifecycle::delayWhileResumed) }
    LifecycleResumeEffect(screenIdle) {
        screenIdle.wake()
        onPauseOrDispose { }
    }
    val tricolourMarks by container.settingsRepository.tricolourMarksEnabled.collectAsStateWithLifecycle(initialValue = false)
    CompositionLocalProvider(
        LocalTricolourMarks provides tricolourMarks,
        LocalReduceMotion provides reduceMotion,
        LocalMotionSettings provides motion,
        LocalFramePaces provides framePaces,
        LocalFramePace provides framePaces.menus,
        LocalAppContainer provides container,
        LocalPlatformServices provides platform,
        LocalGameServices provides gameServices,
        LocalStore provides store,
        LocalOnlineSyncStatus provides onlineSync.status,
        LocalDriftState provides driftState,
        LocalScreenIdle provides screenIdle,
        LocalLeaveGameConfirmation provides leaveConfirmation,
    ) {
        // Inside the provider above, for the platform it creates its player from.
        CompositionLocalProvider(
            LocalSoundEffects provides rememberAppSoundEffects(container.settingsRepository.soundEnabled),
            LocalDiceHaptics provides rememberAppHaptics(container.settingsRepository.vibrationEnabled),
        ) {
            DiceFiveTheme {
                Surface(modifier = Modifier.fillMaxSize().wakesScreen(screenIdle)) {
                    // Scores that can't be opened stop the app here, saying why, rather than on the first page to read one.
                    val databaseFailure by container.databaseFailure.collectAsStateWithLifecycle()
                    databaseFailure?.let {
                        DatabaseFailurePage(details = it)
                        return@Surface
                    }
                    // Hoisted out of DiceFiveNavHost (which otherwise creates its own) so a banner
                    // long press, handled above the NavHost, can navigate the same controller the
                    // NavHost itself renders from.
                    val navController = rememberNavController()
                    // Likewise the page cover, so a banner opens its page the way the pages open each other.
                    val pageCover = rememberPageCover()
                    // Each page opened, for a crash report to say where the player was. It wakes the screen too: a screen
                    // reader's double tap opens a page with no touch for wakesScreen to see.
                    LaunchedEffect(navController) {
                        navController.currentBackStackEntryFlow.map { it.destination.route }.distinctUntilChanged()
                            .collect {
                                Breadcrumbs.record("Opened $it") // i18n: not translated - a crash report's breadcrumb
                                screenIdle.wake()
                            }
                    }
                    // What can wait for the game to end does: leaving its screen, below, asks for the sync anyway.
                    LaunchedEffect(navController) {
                        OnlineSyncRequests.outsideGameRequests.awayFrom(Screen.PLAY_GAME) { navController.currentDestination?.route }
                            .collect { OnlineSyncRequests.request() }
                    }
                    // A game left part-way through is saved as it was left, for another device's Continue.
                    LaunchedEffect(navController) {
                        navController.currentBackStackEntryFlow.map { it.destination.route }.leaving(Screen.PLAY_GAME)
                            .collect { OnlineSyncRequests.request() }
                    }
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
                            AchievementScrollRequests.request(AchievementScrollRequest(achievement.id, animate = alreadyOnAchievements))
                            if (!alreadyOnAchievements) {
                                pageCover.change(reduceMotion) { navController.navigate(Screen.ACHIEVEMENTS) }
                            }
                        },
                        onStylesSelected = { styles ->
                            val target = Screen.stylesBannerTarget(styles)
                            val alreadyThere = navController.currentDestination?.route == target
                            // Before navigating, for the same reason as the achievement request above.
                            StyleScrollRequests.request(StyleScrollRequest(styles, animate = alreadyThere))
                            if (!alreadyThere) {
                                pageCover.change(reduceMotion) {
                                    // Back to the Styles page if it's open under this page; otherwise a fresh one.
                                    if (!navController.popBackStack(Screen.STYLES, inclusive = false)) navController.navigate(Screen.STYLES)
                                    if (target != Screen.STYLES) navController.navigate(target)
                                }
                            }
                        },
                    ) {
                        DiceFiveNavHost(navController = navController, cover = pageCover)
                    }
                    LeaveGameConfirmationDialog(leaveConfirmation)
                }
            }
        }
    }
}

/** Emits each time these routes move off [route] to another. */
internal fun Flow<String?>.leaving(route: String): Flow<Unit> = flow {
    var previous: String? = null
    distinctUntilChanged().collect { current ->
        if (previous == route) emit(Unit)
        previous = current
    }
}

/** Each of these made while [current] isn't [route]. */
internal fun Flow<Unit>.awayFrom(route: String, current: () -> String?): Flow<Unit> = filter { current() != route }

/** How long synced settings are left unchanged before they're saved to the cloud: a run of Styles picks saves once. */
private val SETTINGS_SYNC_DELAY = 5.seconds
