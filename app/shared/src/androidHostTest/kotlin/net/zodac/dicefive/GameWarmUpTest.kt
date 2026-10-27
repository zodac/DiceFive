package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.game.GameWarmUp
import net.zodac.dicefive.ui.game.SavedGameWarmUp
import net.zodac.dicefive.ui.style.surfacesPaintedForTest
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.robolectric.shadows.ShadowLooper

/**
 * The setup screen's hidden warm-up paints the game's art at the sizes the game draws it, so a game opened after it
 * paints none of its background, mat, cup or dice faces on its first frames: each is stamped from the cache. The menu's
 * does the same for the saved game Continue opens.
 */
class GameWarmUpTest : UiTest() {

    /** Moves both clocks on a frame, and draws it. */
    private fun frame() {
        ShadowLooper.idleMainLooper(FRAME_MILLIS, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeByFrame()
        compose.onRoot().captureToImage()
    }

    @Test
    fun aGameOpenedAfterTheWarmUpPaintsNoneOfItsArt() = uiTest {
        // Art painted once and stamped after, all of it heavy: Granite's background and mat, Leather dice, a Glitter cup.
        val settings = SettingsRepository(InMemoryPreferences())
        runBlocking {
            settings.setFirstGameCompleted()
            settings.setTableBackgroundId("stone_granite")
            settings.setDiceMatId("stone_granite")
            settings.setDiceStyleId("leather_tan")
            settings.setDiceCupStyleId("glitter_pink")
        }
        val viewModel = GameViewModel(
            ScoreRepository(InMemoryScoreDao(emptyList())),
            settings,
            InProgressGameRepository(InMemoryPreferences()),
            InMemoryAchievementStore(AchievementsState(unlockedAt = Achievement.entries.associateWith { 1L })),
            random = Random(1),
            localHour = { TEST_LOCAL_HOUR },
        )
        viewModel.setupForm.setPlayerCount(1)
        compose.mainClock.autoAdvance = false
        var playing by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { Box(Modifier.fillMaxSize()) { if (playing) GameScreen(viewModel = viewModel) else GameWarmUp(viewModel) } }
            }
        }

        val beforeWarmUp = surfacesPaintedForTest()
        repeat(WARM_UP_FRAMES) { frame() }
        assertTrue(surfacesPaintedForTest() > beforeWarmUp, "the warm-up painted nothing")

        val beforeGame = surfacesPaintedForTest()
        compose.runOnIdle {
            viewModel.startGame()
            playing = true
            Snapshot.sendApplyNotifications()
        }
        repeat(GAME_FRAMES) { frame() }
        assertEquals(0, surfacesPaintedForTest() - beforeGame, "surfaces the game painted on opening")

        // The first roll's dice, tossed and come to rest, show faces already painted too.
        compose.onNodeWithContentDescription("Dice cup", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        repeat(ROLL_FRAMES) { frame() }
        assertEquals(0, surfacesPaintedForTest() - beforeGame, "surfaces the first roll painted")
    }

    @Test
    fun aContinuedGameAfterTheMenusWarmUpPaintsNoneOfItsArt() = uiTest {
        // Heavy art again, none of it the test above's, which may have run first in this JVM and left its own cached.
        val settings = SettingsRepository(InMemoryPreferences())
        runBlocking {
            settings.setFirstGameCompleted()
            settings.setTableBackgroundId("stone_slate")
            settings.setDiceMatId("stone_basalt")
            settings.setDiceStyleId("leather_oxblood")
            settings.setDiceCupStyleId("faceted_black")
        }
        val achievements = AchievementsState(unlockedAt = Achievement.entries.associateWith { 1L })
        val saved = InProgressGameRepository(InMemoryPreferences())
        fun viewModel() = GameViewModel(
            ScoreRepository(InMemoryScoreDao(emptyList())),
            settings,
            saved,
            InMemoryAchievementStore(achievements),
            random = Random(1),
            localHour = { TEST_LOCAL_HOUR },
        )
        // A game left part-way, as Continue finds it.
        viewModel().run {
            setupForm.setPlayerCount(1)
            startGame()
        }
        assertNotNull(runBlocking { saved.load() }, "no game was saved")
        val picks = SavedStyles(
            diceStyleId = "leather_oxblood",
            diceCupStyleId = "faceted_black",
            tableBackgroundId = "stone_slate",
            diceMatId = "stone_basalt",
            achievements = achievements,
        )
        val continued = viewModel()
        compose.mainClock.autoAdvance = false
        var playing by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme {
                    Box(Modifier.fillMaxSize()) {
                        if (playing) GameScreen(viewModel = continued) else SavedGameWarmUp(picks, hasInProgressGame = true, loadGame = saved::load)
                    }
                }
            }
        }

        val beforeWarmUp = surfacesPaintedForTest()
        repeat(SAVED_GAME_WARM_UP_FRAMES) { frame() }
        assertTrue(surfacesPaintedForTest() > beforeWarmUp, "the warm-up painted nothing")

        val beforeGame = surfacesPaintedForTest()
        assertTrue(runBlocking { continued.resumeGame() }, "the saved game didn't resume")
        compose.runOnIdle {
            playing = true
            Snapshot.sendApplyNotifications()
        }
        repeat(GAME_FRAMES) { frame() }
        assertEquals(0, surfacesPaintedForTest() - beforeGame, "surfaces the continued game painted on opening")
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        // Past the warm-up's 600ms wait and every pass after it.
        const val WARM_UP_FRAMES = 90
        // Past the menu's later 1200ms wait, and every pass after it.
        const val SAVED_GAME_WARM_UP_FRAMES = 130
        const val GAME_FRAMES = 10
        // The shake (420ms) and the toss (900ms), and some.
        const val ROLL_FRAMES = 100
    }
}
