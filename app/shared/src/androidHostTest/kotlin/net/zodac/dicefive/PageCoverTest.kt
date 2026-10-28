package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.navigation.DiceFiveNavHost
import net.zodac.dicefive.navigation.Screen
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.common.HoldPageCover
import net.zodac.dicefive.ui.common.LocalAnimationLevel
import net.zodac.dicefive.ui.common.LocalPageCover
import net.zodac.dicefive.ui.common.PageCover
import net.zodac.dicefive.ui.common.PageCoverOverlay
import net.zodac.dicefive.ui.common.RevealedPage
import net.zodac.dicefive.ui.common.rememberPageCover
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.rules.resetRulesPageMemory
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.robolectric.shadows.ShadowLooper

/**
 * A page opens under the page cover: the page in front fades out to the backdrop, the next one is built only once that
 * is opaque, and the cover fades away once the new page has settled - or has loaded, if it holds the cover meanwhile.
 * Going back shows the page it returns to at once. The cover says nothing to TalkBack, and the game's clocks wait for it.
 */
class PageCoverTest : UiTest() {

    private lateinit var cover: PageCover

    /** Moves both clocks on a frame: the view models' coroutines run on the looper. */
    private fun frame() {
        ShadowLooper.idleMainLooper(FRAME_MILLIS, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeByFrame()
    }

    /** Page A, or page B once [page] says so, under the cover; B holds the cover while [loading]. */
    private fun twoPages(reduceMotion: Boolean = false): PagesState {
        val state = PagesState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            // Still dice, so a drawn difference on the cover is the spinner's alone.
            CompositionLocalProvider(LocalAnimationLevel provides if (reduceMotion) AnimationLevel.OFF else AnimationLevel.MEDIUM) {
                DiceFiveTheme {
                    cover = rememberPageCover()
                    Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalPageCover provides cover) {
                            val page = state.page
                            if (page == "B" && state.loading) HoldPageCover()
                            Text("Page $page")
                        }
                        PageCoverOverlay(cover)
                    }
                }
            }
        }
        compose.waitForIdle()
        return state
    }

    private class PagesState {
        var page by mutableStateOf("A")
        var loading by mutableStateOf(false)
    }

    private fun changeTo(state: PagesState, reduceMotion: Boolean = false) = compose.runOnIdle {
        cover.change(reduceMotion) {
            state.page = "B"
            Snapshot.sendApplyNotifications()
        }
    }

    @Test
    fun `the next page is opened only behind the opaque cover, which then fades away`() = uiTest {
        val state = twoPages()
        var opacityWhenOpened: Float? = null
        compose.runOnIdle {
            cover.change(reduceMotion = false) {
                opacityWhenOpened = cover.opacity
                state.page = "B"
                Snapshot.sendApplyNotifications()
            }
        }
        val opacities = (1..FRAMES).map { frame(); cover.opacity }

        assertTrue(opacities.any { it > 0f && it < 1f }, "the cover never faded: $opacities")
        assertEquals(1f, opacityWhenOpened, "page B was opened before the cover was opaque")
        assertEquals(0f, opacities.last(), "the cover never cleared: $opacities")
        assertFalse(cover.changing)
        compose.onNodeWithText("Page B").assertExists()
    }

    @Test
    fun `a page revealed in a window of its own starts under the opaque cover, which fades away once it has settled`() = uiTest {
        var revealed: PageCover? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAnimationLevel provides AnimationLevel.MEDIUM) {
                DiceFiveTheme {
                    RevealedPage {
                        revealed = LocalPageCover.current
                        Text("Crash page")
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val shown = checkNotNull(revealed)
        assertTrue(shown.changing)
        assertEquals(1f, shown.opacity, "the page was seen before its cover was up")
        val opacities = (1..FRAMES).map { frame(); shown.opacity }

        assertTrue(opacities.any { it > 0f && it < 1f }, "the cover never faded: $opacities")
        assertEquals(0f, opacities.last(), "the cover never cleared: $opacities")
        assertFalse(shown.changing)
        compose.onNodeWithText("Crash page").assertExists()
    }

    @Test
    fun `a second change asked for during one is ignored`() = uiTest {
        val state = twoPages()
        var changes = 0
        compose.runOnIdle {
            cover.change(reduceMotion = false) { changes++; state.page = "B" }
            cover.change(reduceMotion = false) { changes++ }
        }
        repeat(FRAMES) { frame() }
        compose.runOnIdle { cover.change(reduceMotion = false) { changes++ } }
        repeat(FRAMES) { frame() }
        assertEquals(2, changes)
    }

    @Test
    fun `a page that holds the cover keeps it up until loaded, with a spinner after half a second that TalkBack isn't given`() = uiTest {
        val state = twoPages()
        state.loading = true
        changeTo(state)
        // Nothing on the cover moves but the spinner: its dice hold still at this Animations level.
        val screen = { compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap -> IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) } } }

        // Faded in, and waiting well under half a second.
        repeat(FADE_OUT_FRAMES + 4) { frame() }
        assertEquals(1f, cover.opacity)
        val beforeSpinner = screen()
        repeat(SPINNER_FRAMES) { frame() }
        assertFalse(screen().contentEquals(beforeSpinner), "no spinner after half a second")
        assertTrue(
            compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "the cover's spinner reached TalkBack",
        )

        // Two seconds in, still loading: still covered.
        repeat(2_000 / FRAME_MILLIS.toInt()) { frame() }
        assertEquals(1f, cover.opacity, "the cover cleared while the page was still loading")

        compose.runOnIdle { state.loading = false }
        repeat(FRAMES) { frame() }
        assertEquals(0f, cover.opacity, "the cover didn't clear once the page had loaded")
        assertFalse(cover.changing)
    }

    @Test
    fun `under reduced motion the cover goes up and comes down without fading`() = uiTest {
        val state = twoPages(reduceMotion = true)
        changeTo(state, reduceMotion = true)
        val opacities = (1..FRAMES).map { frame(); cover.opacity }
        assertTrue(opacities.all { it == 0f || it == 1f }, "the cover faded: $opacities")
        assertTrue(1f in opacities, "the cover never went up")
        assertEquals(0f, opacities.last())
        compose.onNodeWithText("Page B").assertExists()
    }

    @Test
    fun `the turn timer doesn't count while the cover is up over the game`() = uiTest {
        val viewModel = gameViewModel()
        viewModel.setupForm.setPlayerCount(1)
        viewModel.setupForm.setTurnTimer(TurnTimer.SECONDS_30)
        viewModel.startGame()
        var holding by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme {
                    cover = rememberPageCover()
                    Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalPageCover provides cover) {
                            if (holding) HoldPageCover()
                            GameScreen(viewModel = viewModel)
                        }
                        PageCoverOverlay(cover)
                    }
                }
            }
        }
        repeat(10) { frame() }
        compose.runOnIdle { cover.change(reduceMotion = false) { holding = true } }

        // Three seconds of a page change.
        repeat(3_000 / FRAME_MILLIS.toInt()) { frame() }
        assertEquals(30, viewModel.turnSecondsRemaining.value, "the timer counted behind the cover")

        compose.runOnIdle { holding = false }
        repeat(3_000 / FRAME_MILLIS.toInt()) { frame() }
        val left = assertNotNull(viewModel.turnSecondsRemaining.value)
        assertTrue(left < 30, "the timer never started once the cover had cleared")
    }

    @Test
    fun `Continue opens the saved game with no setup page between, and going back is at once`() = uiTest {
        val container = inMemoryAppContainer()
        GameViewModel(
            container.scoreRepository,
            container.settingsRepository,
            container.inProgressGameRepository,
            InMemoryAchievementStore(),
            random = Random(1),
            localHour = { TEST_LOCAL_HOUR },
        ).run {
            setupForm.setPlayerCount(1)
            startGame()
        }
        assertNotNull(runBlocking { container.inProgressGameRepository.load() }, "no game was saved")
        lateinit var nav: NavHostController
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container, LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme {
                    nav = rememberNavController()
                    cover = rememberPageCover()
                    DiceFiveNavHost(navController = nav, cover = cover)
                }
            }
        }
        waitFor(hasText("Continue"))

        compose.onNodeWithText("Continue").performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasContentDescription("Dice cup", substring = true))
        repeat(FRAMES) { frame() }
        assertFalse(cover.changing)
        val routes = nav.currentBackStack.value.mapNotNull { it.destination.route }
        assertEquals(listOf(Screen.MENU, Screen.PLAY_GRAPH, Screen.PLAY_GAME), routes)

        // Leaving the game is going back: the menu, on the next frame, with no cover.
        compose.runOnIdle { nav.popBackStack(Screen.MENU, inclusive = false) }
        repeat(2) { frame() }
        compose.onNodeWithText("Settings").assertExists()
        assertFalse(cover.changing, "going back went through the cover")
    }

    @Test
    fun `a page asked for is dropped if going back has already left the page it was asked from`() = uiTest {
        // Rules opens on How to Play, where Start Tutorial is, not wherever an earlier test left it.
        resetRulesPageMemory()
        lateinit var nav: NavHostController
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides inMemoryAppContainer(), LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme {
                    nav = rememberNavController()
                    cover = rememberPageCover()
                    DiceFiveNavHost(navController = nav, cover = cover)
                }
            }
        }
        waitFor(hasText("Rules"))
        compose.onNodeWithText("Rules").performSemanticsAction(SemanticsActions.OnClick)
        // Not by its Back arrow: the menu's warm-ups build copies of pages with one, out of sight.
        repeat(WAIT_FRAMES) { if (nav.currentDestination?.route != Screen.RULES || cover.changing) frame() }
        assertEquals(Screen.RULES, nav.currentDestination?.route)

        // Start Tutorial asked for, then the page's back arrow tapped while Rules is still fading out: the cover takes the
        // touch.
        compose.onNodeWithText("Start Tutorial").performSemanticsAction(SemanticsActions.OnClick)
        repeat(3) { frame() }
        assertTrue(cover.changing, "Start Tutorial didn't start a change")
        compose.onNodeWithContentDescription("Back").performClick()
        frame()
        assertEquals(Screen.RULES, nav.currentDestination?.route, "a tap went through the cover")

        // The system's back can still be pressed, and the tutorial is then never opened.
        compose.runOnIdle { nav.popBackStack() }
        repeat(FRAMES) { frame() }
        assertEquals(Screen.MENU, nav.currentDestination?.route)
    }

    /** Frames until a node matching [matcher] is there, failing after a few seconds. */
    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        repeat(WAIT_FRAMES) {
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return
            frame()
        }
        error("never appeared: ${matcher.description}")
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        // The fade out, the settling and the fade in, and some.
        const val FRAMES = 60
        const val FADE_OUT_FRAMES = PageCover.FADE_OUT_MILLIS / FRAME_MILLIS.toInt()
        // Past the spinner's wait, and some.
        val SPINNER_FRAMES = (PageCover.SPINNER_DELAY_MILLIS / FRAME_MILLIS).toInt() + 8
        const val WAIT_FRAMES = 600
    }
}
