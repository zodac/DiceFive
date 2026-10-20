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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.game.GameWarmUp
import net.zodac.dicefive.ui.style.surfacesPaintedForTest
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/**
 * The setup screen's hidden warm-up paints the game's art at the sizes the game draws it, so a game opened after it
 * paints none of its background, mat, cup or dice faces on its first frames: each is stamped from the cache.
 */
@RunWith(AndroidJUnit4::class)
class GameWarmUpTest {

    @get:Rule
    val compose = createComposeRule()

    /** Moves both clocks on a frame, and draws it. */
    private fun frame() {
        ShadowLooper.idleMainLooper(FRAME_MILLIS, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeByFrame()
        compose.onRoot().captureToImage()
    }

    @Test
    fun aGameOpenedAfterTheWarmUpPaintsNoneOfItsArt() {
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
            ScoreRepository(RowsDao(emptyList())),
            settings,
            InProgressGameRepository(InMemoryPreferences()),
            InMemoryAchievementStore(AchievementsState(unlockedAt = Achievement.entries.associateWith { 1L })),
            random = Random(1),
            localHour = { TEST_LOCAL_HOUR },
        )
        viewModel.setPlayerCount(1)
        compose.mainClock.autoAdvance = false
        var playing by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { Box(Modifier.fillMaxSize()) { if (playing) GameScreen(viewModel = viewModel) else GameWarmUp(viewModel) } }
            }
        }

        val beforeWarmUp = surfacesPaintedForTest()
        repeat(WARM_UP_FRAMES) { frame() }
        assertTrue("the warm-up painted nothing", surfacesPaintedForTest() > beforeWarmUp)

        val beforeGame = surfacesPaintedForTest()
        compose.runOnIdle {
            viewModel.startGame()
            playing = true
            Snapshot.sendApplyNotifications()
        }
        repeat(GAME_FRAMES) { frame() }
        assertEquals("surfaces the game painted on opening", 0, surfacesPaintedForTest() - beforeGame)

        // The first roll's dice, tossed and come to rest, show faces already painted too.
        compose.onNodeWithContentDescription("Dice cup", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        repeat(ROLL_FRAMES) { frame() }
        assertEquals("surfaces the first roll painted", 0, surfacesPaintedForTest() - beforeGame)
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        // Past the warm-up's 600ms wait and every pass after it.
        const val WARM_UP_FRAMES = 90
        const val GAME_FRAMES = 10
        // The shake (420ms) and the toss (900ms), and some.
        const val ROLL_FRAMES = 100
    }
}
