package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.scores.UnpostedScore
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Achievements page's and its banners' words: the count, categories, a hidden row, and what each banner says and is called. */
@RunWith(AndroidJUnit4::class)
class AchievementsTextTest {

    @get:Rule
    val compose = createComposeRule()

    private val showcase = Showcase(compose)

    /**
     * Closes the pop-up and scrolls back to the top: the screen remembers where it was scrolled to for the whole
     * process, so a test left scrolled down starts the next one there.
     */
    private fun leaveListAtTop() {
        compose.onNodeWithText("Done").performClick()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.waitForIdle()
    }

    @Test
    fun `the page is titled - counts what is unlocked - hides a description as three question marks - and each banner says what it is`() {
        val viewModel = AchievementsViewModel()
        showcase.show {
            CompositionLocalProvider(
                LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext()),
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        compose.onNodeWithText("Achievements").assertExists()
        compose.onNodeWithText("0 of ", substring = true).assertExists()
        compose.onNodeWithText(" unlocked", substring = true).assertExists()
        // A category header is read with how many of its achievements are earned (its drawn count is hidden from TalkBack so it is not said twice).
        val milestones = Achievement.entries.count { it.category == AchievementCategory.MILESTONES }
        compose.onNodeWithContentDescription("Milestones, 0 of $milestones unlocked").assertExists()
        // A hidden achievement's description is three question marks until it is earned - all in Miscellaneous, further
        // down the list: scroll to the first.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("???"))
        compose.onAllNodesWithText("???").onFirst().assertExists()

        // Each banner on a host of its own: a host shows its banners one after another.
        fun showBannerHost() = showcase.show {
            CompositionLocalProvider(LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext())) {
                DiceFiveTheme {
                    AchievementBannerHost(isOnGameScreen = { false }, onAchievementSelected = {}, onStylesSelected = {}) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        showBannerHost()
        // An unlock banner is read as the achievement unlocked and its description.
        AchievementEvents.emit(AchievementEvent.Unlocked(Achievement.THE_JOURNEY_BEGINS))
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Achievement unlocked: The Journey Begins. Start your first game")).assertExists()
        // A progress banner is read as its title and how far along.
        showBannerHost()
        AchievementEvents.emit(AchievementEvent.Progressed(Achievement.GETTING_COMFORTABLE, 4, 5))
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Getting Comfortable: 5 of 10")).assertExists()
        val config = compose.onAllNodesWithContentDescription("Getting Comfortable: 5 of 10").onFirst().fetchSemanticsNode().config
        assertEquals(true, SemanticsActions.OnClick in config)
    }

    @Test
    fun `Full Set opens a list of its numbers - 1s to 6s - the ones scored read as scored`() {
        // A 5x of 1s and 3s scored so far: bits 0 and 2.
        val store = object : AchievementStore {
            override val state = MutableStateFlow(AchievementsState(counters = mapOf(AchievementCounter.FIVE_X_NUMBERS to 0b101)))
            override suspend fun current() = state.value
            override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) = Unit
            override suspend fun resetAll() = Unit
            override suspend fun forceLock(achievement: Achievement) = Unit
        }
        val viewModel = AchievementsViewModel(achievementsRepository = store)
        showcase.show {
            CompositionLocalProvider(
                LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext()),
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Full Set"))
        // Its action is named for TalkBack. A tap shows the short tip, and a long press - or TalkBack's action - the full list.
        val row = compose.onNode(SemanticsMatcher("clicks to show the missing numbers") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Show the missing numbers" })
        row.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Numbers with no 5x scored yet: 4 left").assertExists()
        compose.onNodeWithText("Numbers with no 5x scored yet (4 left):").assertDoesNotExist()
        row.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Numbers with no 5x scored yet (4 left):").assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        row.performTouchInput { longClick() }
        compose.waitForIdle()

        compose.onNodeWithText("Numbers with no 5x scored yet (4 left):").assertExists()
        // Each number is shown as "1s" to "6s"; the scored ones are read as scored.
        for (number in listOf(1, 3)) compose.onNodeWithContentDescription("${number}s, scored").assertExists()
        for (number in listOf(2, 4, 5, 6)) compose.onNodeWithContentDescription("${number}s").assertExists()
        leaveListAtTop()
    }

    @Test
    fun `Grand Tour opens a list of every mode by name - the ones finished read as finished`() {
        val modesPlayed = listOf(GameMode.STANDARD, GameMode.STUD, GameMode.THIRD_WIND)
        val dao = object : ScoreDao by RowsDao(emptyList()) {
            override suspend fun primaryPlayerGameModeIds(): List<String> = modesPlayed.map { it.id }
            override suspend fun bestUnpostedScore(gameModeId: String, afterEpochMillis: Long, maxScore: Int): UnpostedScore? = null
        }
        val viewModel = AchievementsViewModel(scoreRepository = ScoreRepository(dao))
        showcase.show {
            CompositionLocalProvider(
                LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext()),
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Grand Tour"))
        // Its action is named for TalkBack. A tap shows the short tip, and a long press the full list.
        val row = compose.onNode(SemanticsMatcher("clicks to show the missing modes") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Show the missing modes" })
        row.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Modes with no game finished yet: 4 left").assertExists()
        compose.onNodeWithText("Modes with no game finished yet (4 left):").assertDoesNotExist()
        row.performTouchInput { longClick() }
        compose.waitForIdle()

        compose.onNodeWithText("Modes with no game finished yet (4 left):").assertExists()
        // Each mode by its whole name, the finished ones read as finished.
        for (mode in listOf("Standard", "7 Dice Stud", "Third Wind")) compose.onNodeWithContentDescription("$mode, finished").assertExists()
        for (mode in listOf("Tricolour", "Quickfire", "D20", "Hit List")) compose.onNodeWithContentDescription(mode).assertExists()
        leaveListAtTop()
    }

    @Test
    fun `a row that unlocks a style shows its tooltip on a tap - and the same words in a dialog on a long press`() {
        val viewModel = AchievementsViewModel()
        showcase.show {
            CompositionLocalProvider(
                LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext()),
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        val reward = SemanticsMatcher("clicks to show which style it unlocks") {
            it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Show which style this unlocks"
        }
        compose.onNode(hasScrollAction()).performScrollToNode(reward)
        val row = compose.onAllNodes(reward).onFirst()
        row.performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Earn this to unlock", substring = true).onFirst().assertExists()
        // No dialog yet: its OK button is not there.
        compose.onNodeWithText("OK").assertDoesNotExist()

        row.performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("OK").performClick()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.waitForIdle()
    }
}
