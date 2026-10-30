package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.app.AchievementEvent
import net.zodac.dicefive.app.AchievementEvents
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.scores.UnpostedScore
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
import net.zodac.dicefive.ui.achievements.resetAchievementsScrollMemory
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.jetbrains.compose.resources.getString

/** The Achievements page's and its banners' words: the count, categories, a hidden row, and what each banner says and is called. */
class AchievementsTextTest : UiTest() {

    private val showcase = Showcase { compose }

    /** The page remembers where it was scrolled to, and the Locked only switch, for the whole process: every test starts afresh. */
    @BeforeTest
    fun openAtTheTop() = resetAchievementsScrollMemory()

    private fun showAchievements(viewModel: AchievementsViewModel) = showcase.show {
        CompositionLocalProvider(
            LocalAppContainer provides inMemoryAppContainer(),
            LocalPlatformServices provides SilentPlatformServices,
        ) {
            DiceFiveTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
        }
    }

    @Test
    fun `the page is titled - counts what is unlocked - hides a description as three question marks - and each banner says what it is`() = uiTest {
        val viewModel = achievementsViewModel()
        showAchievements(viewModel)
        compose.onNodeWithText("Achievements").assertExists()
        compose.onNodeWithText("0 of ", substring = true).assertExists()
        compose.onNodeWithText(" earned", substring = true).assertExists()
        // A category header is read with how many of its achievements are earned (its drawn count is hidden from TalkBack so it is not said twice).
        val milestones = Achievement.entries.count { it.category == AchievementCategory.MILESTONES }
        compose.onNodeWithContentDescription("Milestones, 0 of $milestones earned").assertExists()
        // A hidden achievement's description is three question marks until it is earned - all in Miscellaneous, further
        // down the list: scroll to the first.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("???"))
        compose.onAllNodesWithText("???").onFirst().assertExists()

        // Each banner on a host of its own: a host shows its banners one after another.
        fun showBannerHost() = showcase.show {
            CompositionLocalProvider(LocalAppContainer provides inMemoryAppContainer()) {
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
    fun `Full Set opens a list of its numbers - 1s to 6s - the ones scored read as scored`() = uiTest {
        // A 5x of 1s and 3s scored so far: bits 0 and 2.
        val store = object : AchievementStore {
            override val state = MutableStateFlow(AchievementsState(counters = mapOf(AchievementCounter.FIVE_X_NUMBERS to 0b101)))
            override suspend fun current() = state.value
            override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) = Unit
            override suspend fun resetAll() = Unit
            override suspend fun forceLock(achievement: Achievement) = Unit
        }
        val viewModel = achievementsViewModel(store = store)
        showAchievements(viewModel)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Full Set"))
        // Its action is named for TalkBack. A tap shows the short tip, and a long press - or TalkBack's action - the full list.
        val row = compose.onNode(SemanticsMatcher("clicks to show the missing numbers") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Show the missing numbers" })
        row.performClick()
        compose.waitForIdle()
        // Only some modes score a 5x, so the tip and the dialog both say which, first.
        compose.onNodeWithText("Can only be earned in the 'Standard', 'Tricolour', and 'Third\u00A0Wind' modes\nNumbers with no 5x scored yet: 4 left").assertExists()
        compose.onNodeWithText("Numbers with no 5x scored yet (4 left):", substring = true).assertDoesNotExist()
        row.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Can only be earned in the 'Standard', 'Tricolour', and 'Third\u00A0Wind' modes\nNumbers with no 5x scored yet (4 left):").assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        row.performTouchInput { longClick() }
        compose.waitForIdle()

        compose.onNodeWithText("Can only be earned in the 'Standard', 'Tricolour', and 'Third\u00A0Wind' modes\nNumbers with no 5x scored yet (4 left):").assertExists()
        // Each number is shown as "1s" to "6s"; the scored ones are read as scored.
        for (number in listOf(1, 3)) compose.onNodeWithContentDescription("${number}s, scored").assertExists()
        for (number in listOf(2, 4, 5, 6)) compose.onNodeWithContentDescription("${number}s").assertExists()
    }

    @Test
    fun `Full Houses opens a list of its two hands - the scored one struck through and read as its dice scored`() = uiTest {
        // The worst hand scored so far: bit 0.
        val store = object : AchievementStore {
            override val state = MutableStateFlow(AchievementsState(counters = mapOf(AchievementCounter.FULL_HOUSES to 0b01)))
            override suspend fun current() = state.value
            override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) = Unit
            override suspend fun resetAll() = Unit
            override suspend fun forceLock(achievement: Achievement) = Unit
        }
        val viewModel = achievementsViewModel(store = store)
        showAchievements(viewModel)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Full Houses"))
        val row = compose.onNode(SemanticsMatcher("clicks to show the missing Full Houses") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Show the missing Full Houses" })
        row.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Full Houses not yet scored: 1 left").assertExists()
        row.performTouchInput { longClick() }
        compose.waitForIdle()

        compose.onNodeWithText("Full Houses not yet scored (1 left):").assertExists()
        // Each hand shown as its dice joined by dashes, and spoken as a list of them.
        compose.onNodeWithText("1-1-1-2-2").assertExists()
        compose.onNodeWithText("5-5-6-6-6").assertExists()
        compose.onNodeWithContentDescription("1, 1, 1, 2, and 2, scored").assertExists()
        compose.onNodeWithContentDescription("5, 5, 6, 6, and 6").assertExists()
    }

    @Test
    fun `Grand Tour opens a list of every mode by name - the ones finished read as finished`() = uiTest {
        val modesPlayed = listOf(GameMode.STANDARD, GameMode.STUD, GameMode.THIRD_WIND)
        val dao = object : ScoreDao by InMemoryScoreDao(emptyList()) {
            override suspend fun leaderboardTotals() = LeaderboardTotals(modesPlayed = modesPlayed.toSet())
            override suspend fun bestUnpostedScore(gameModeId: String, afterEpochMillis: Long, maxScore: Int): UnpostedScore? = null
        }
        val viewModel = achievementsViewModel(scores = ScoreRepository(dao))
        showAchievements(viewModel)
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
    }

    @Test
    fun `a row that unlocks a style shows its tooltip on a tap - and the same words in a dialog on a long press`() = uiTest {
        val viewModel = achievementsViewModel()
        showAchievements(viewModel)
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
    }

    @Test
    fun `a row deliberately kept out of some modes names the rest in a tooltip that stays until tapped again - and in a dialog on a long press`() = uiTest {
        showAchievements(achievementsViewModel())
        fun clickLabelled(label: String) = SemanticsMatcher("clicks to $label") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == label }
        val solidRound = hasText("Solid Round") and clickLabelled("Show which modes can earn this")
        compose.onNode(hasScrollAction()).performScrollToNode(solidRound)
        val row = compose.onNode(solidRound)
        row.performClick()
        compose.waitForIdle()
        val modes = "Can only be earned in the 'Standard' and 'Tricolour' modes"
        compose.onNodeWithText(modes).assertExists()
        // Still there long after, until the row is tapped again.
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText(modes).assertExists()
        row.performClick()
        compose.waitForIdle()
        compose.onNodeWithText(modes).assertDoesNotExist()
        compose.onNodeWithText("OK").assertDoesNotExist()
        row.performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText(modes).assertExists()
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()

        // One kept out of most modes names the few left: Encore is guarded in Third Wind and Stud, and D20, Hit List and
        // Quickfire have no 5x box.
        val encore = hasText("Encore") and clickLabelled("Show which modes can earn this")
        compose.onNode(hasScrollAction()).performScrollToNode(encore)
        compose.onNode(encore).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(modes).assertExists()
        compose.onNode(encore).performClick()
        compose.waitForIdle()

        // The Game Modes category names its modes in its descriptions: Nat 20's tip is only the style it unlocks.
        val nat20 = hasText("Nat 20") and clickLabelled("Show which style this unlocks")
        compose.onNode(hasScrollAction()).performScrollToNode(nat20)
        compose.onNode(nat20).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Earn this to unlock the hidden 'D20' dice colours").assertExists()
        compose.onNode(nat20).performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Where We're Going, We Don't Need Rules"))
        assertEquals(false, SemanticsActions.OnClick in compose.onNode(hasText("Where We're Going, We Don't Need Rules")).fetchSemanticsNode().config)

        // One only some modes' rules rule out (Scratched needs a 5x box) isn't limited: nothing to tap.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Scratched"))
        assertEquals(false, SemanticsActions.OnClick in compose.onNode(hasText("Scratched")).fetchSemanticsNode().config)

        // A hidden one never names its modes: Impatient has nothing else to show, so no action at all.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Impatient"))
        assertEquals(false, SemanticsActions.OnClick in compose.onNode(hasText("Impatient")).fetchSemanticsNode().config)
    }

    @Test
    fun `an earned row names no modes - and one unlocking several styles names them in one sentence, then one to a bulleted line in its dialog`() = uiTest {
        val store = InMemoryAchievementStore(AchievementsState(unlockedAt = mapOf(Achievement.SOLID_ROUND to 0L)))
        showAchievements(achievementsViewModel(store = store))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Solid Round"))
        assertEquals(false, SemanticsActions.OnClick in compose.onNode(hasText("Solid Round")).fetchSemanticsNode().config)

        val midnight = hasText("Rolling The Midnight Dice") and SemanticsMatcher("clickable") { SemanticsActions.OnClick in it.config }
        compose.onNode(hasScrollAction()).performScrollToNode(midnight)
        // Scrolled to the top, the row's middle sits under the pinned category header: touch its lower edge, clear of it.
        fun SemanticsNodeInteraction.tapBelowHeader(longPress: Boolean = false) = performTouchInput {
            val below = Offset(centerX, height - 8f)
            if (longPress) longClick(below) else click(below)
        }
        compose.onNode(midnight).tapBelowHeader()
        compose.waitForIdle()
        compose.onNodeWithText("Earn this to unlock 'Starry' dice, mat, and background").assertExists()
        compose.onNode(midnight).tapBelowHeader()
        compose.waitForIdle()
        compose.onNode(midnight).tapBelowHeader(longPress = true)
        compose.waitForIdle()
        // A heading, then each style on a bulleted line of its own, capitalised, with no "the".
        compose.onNodeWithText("Earn this to unlock:").assertExists()
        for (line in listOf("'Starry' dice style", "'Starry' mat style", "'Starry' background style")) compose.onNodeWithText(line).assertExists()
    }

    @Test
    fun `The Unearned switch is one switch - it hides what is unlocked - and is still on when the page is opened again`() = uiTest {
        val store = InMemoryAchievementStore(AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 0L)))
        val settings = SettingsRepository(InMemoryPreferences())
        showAchievements(achievementsViewModel(store = store, settings = settings))
        // Its label and switch are one target, read as "All, switch" or "Unearned, switch" and whether it's on.
        val lockedOnly = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        lockedOnly.assertIsOff()
        compose.onNodeWithText("The Journey Begins").assertExists()

        lockedOnly.performClick()
        compose.waitForIdle()
        lockedOnly.assertIsOn()
        compose.onNodeWithText("The Journey Begins").assertDoesNotExist()
        compose.onNodeWithText("Practice Makes Perfect").assertExists()

        // Back to the menu and in again: a new page, as navigation makes one, still showing the locked ones only.
        showAchievements(achievementsViewModel(store = store, settings = settings))
        lockedOnly.assertIsOn()
        compose.onNodeWithText("The Journey Begins").assertDoesNotExist()

        lockedOnly.performClick()
        compose.waitForIdle()
        lockedOnly.assertIsOff()
        compose.onNodeWithText("The Journey Begins").assertExists()
    }

    @Test
    fun `a row's title stays on one line at its own size when it fits - shrinks to the 12sp floor then wraps when it does not`() = uiTest(TestScreen(345, 800)) {
        showAchievements(achievementsViewModel())
        val short = getString(Achievement.THE_JOURNEY_BEGINS.title)
        val short14 = titleLayout(short).layoutInput
        assertEquals(1, short14.maxLines)
        assertEquals(14f, short14.style.fontSize.value, 0.01f)

        // The longest title can't fit one line of the row at 14sp, so it is stepped down - never below the app-wide 12sp floor - and wraps to two lines at most.
        val longest = getString(Achievement.WHERE_WERE_GOING_WE_DONT_NEED_RULES.title)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(longest))
        val layout = titleLayout(longest)
        assertTrue(layout.layoutInput.style.fontSize.value < 14f, "shrunk from titleSmall's 14sp")
        assertTrue(layout.layoutInput.style.fontSize.value >= 12f, "never below the 12sp floor")
        assertTrue(layout.lineCount <= 2, "at most two lines")
    }

    @Test
    fun `the earned count includes the Easter Eggs while the total leaves them out - so all earned reads more than the total`() = uiTest {
        val all = AchievementsState(unlockedAt = Achievement.entries.associateWith { 0L })
        showAchievements(achievementsViewModel(store = InMemoryAchievementStore(all)))
        val total = Achievement.entries.count { it.category != AchievementCategory.EASTER_EGGS }
        compose.onNodeWithText("${Achievement.entries.size} of $total earned").assertExists()
    }

    @Test
    fun `the Easter Eggs header is read with its earned count like every other category`() = uiTest {
        val eggs = Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS }
        val all = AchievementsState(unlockedAt = mapOf(eggs.first() to 0L))
        showAchievements(achievementsViewModel(store = InMemoryAchievementStore(all)))
        val header = hasContentDescription("Easter Eggs, 1 of ${eggs.size} earned")
        compose.onNode(hasScrollAction()).performScrollToNode(header)
        compose.onNode(header).assertExists()
    }

    private fun titleLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first()
    }

    @Test
    fun `Flipping the switch keeps the list where it was`() = uiTest {
        val store = InMemoryAchievementStore(AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 0L)))
        showAchievements(achievementsViewModel(store = store, settings = SettingsRepository(InMemoryPreferences())))
        val switch = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Grand Tour"))
        compose.waitForIdle()
        compose.onNodeWithText("Grand Tour").assertIsDisplayed()

        switch.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Grand Tour").assertIsDisplayed()

        switch.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Grand Tour").assertIsDisplayed()
    }

    @Test
    fun `Flipping the switch at the top keeps the top - no row is left behind the pinned header`() = uiTest {
        val store = InMemoryAchievementStore(AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 0L)))
        showAchievements(achievementsViewModel(store = store, settings = SettingsRepository(InMemoryPreferences())))
        val switch = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        val firstRowTop = compose.onNodeWithText("The Journey Begins").getUnclippedBoundsInRoot().top

        // The first locked row takes the first row's place, under its header: its title within a dp or two of the earned
        // row's, which sits a little differently in its card - behind the header, it was 55dp higher.
        switch.performClick()
        compose.waitForIdle()
        val lockedTop = compose.onNodeWithText("Practice Makes Perfect").getUnclippedBoundsInRoot().top
        assertTrue(abs((lockedTop - firstRowTop).value) <= 2f, "The first locked row's title is at $lockedTop, not $firstRowTop")

        switch.performClick()
        compose.waitForIdle()
        assertEquals(firstRowTop, compose.onNodeWithText("The Journey Begins").getUnclippedBoundsInRoot().top)
    }
}
