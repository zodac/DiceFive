package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.online.LocalGameServices
import net.zodac.dicefive.online.NoGameServices
import net.zodac.dicefive.online.OnlineAccount
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** Signed in to a service with leaderboards, recording which one is opened. */
private class LeaderboardServices : GameServices by NoGameServices {
    override val serviceName = "Google Play Games"
    override val state = MutableStateFlow<GameServicesState>(GameServicesState.SignedIn(OnlineAccount("Ann", null, "ann")))
    override val canShowLeaderboardUi = true
    val opened = mutableListOf<String?>()
    override fun showLeaderboardUi(leaderboardKey: String?) {
        opened += leaderboardKey
    }
}

/**
 * The Leaderboard's Combined / Game Mode switch: what each view shows, and what a screen reader hears for a
 * row (the mode and date a long press shows). How the cards look is for a render; this pins the structure.
 */
class ScoresScreenTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun show(rows: List<ScoreEntry>, services: GameServices = NoGameServices) {
        val viewModel = ScoresViewModel(ScoreRepository(InMemoryScoreDao(rows)))
        showcase.show {
            CompositionLocalProvider(LocalGameServices provides services) {
                DiceFiveTheme { ScoresScreen(viewModel = viewModel, onBack = {}) }
            }
        }
    }

    private val someRows = listOf(rowOf(1, 300, GameMode.STANDARD), rowOf(2, 250, GameMode.TRICOLOUR), rowOf(3, 200, GameMode.STANDARD))

    /** The mode's name as `strings.xml` has it: `Res` is internal to :app:shared, so the file is read from disk. */
    private fun modeName(mode: GameMode): String = Regex("""<string name="mode_${mode.id}">([^<]*)</string>""")
        .find(File("../shared/src/commonMain/composeResources/values/strings.xml").readText())!!.groupValues[1]

    @Test
    fun opensOnTheCombinedTableWithTheSwitchAtTheTopAndARowSpeaksItsModeAndDateWhichALongPressShows() = uiTest {
        show(someRows)
        compose.onNodeWithText("Combined").assertIsSelected()
        compose.onNodeWithText("Game Mode").assertExists()
        compose.onNodeWithText("Player1").assertExists()
        // The mode names only exist as cards in the other view.
        compose.onAllNodesWithText("7 Dice Stud").assertCountEquals(0)

        val detail = compose.onNodeWithText("Player2").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertTrue(detail!!.endsWith(", Tricolour"), detail.orEmpty())

        // A long press on a row arms its tooltip: the first row of the combined table, its date and mode.
        compose.onNodeWithText("Player1").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onAllNodesWithText("Standard", substring = true).assertCountEquals(1)
    }

    @Test
    fun gameModeShowsACardOnlyForModesWithScoresEachRowDatedAndItsPageControlsAtTheEndOfItsOwnScroll() = uiTest {
        // Quickfire never counts on the combined table, yet gets its card; Stud has no score, so no card.
        show(someRows + rowOf(4, 90, GameMode.QUICKFIRE))
        compose.onNodeWithText("Game Mode").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Standard").assertExists()
        compose.onNodeWithText("Tricolour").assertExists()
        compose.onNodeWithText(modeName(GameMode.QUICKFIRE)).assertExists()
        compose.onAllNodesWithText(modeName(GameMode.STUD)).assertCountEquals(0)
        // On its own card a row's detail is the date alone - the mode is the card's title.
        val detail = compose.onNodeWithText("Player2").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertTrue(GameMode.entries.none { detail!!.contains(modeName(it)) }, detail.orEmpty())

        show((1..SCORES_PAGE_SIZE + 5).map { rowOf(it.toLong(), it, GameMode.STANDARD) })
        compose.onNodeWithText("Game Mode").performClick()
        compose.waitForIdle()
        // Standard's card is the only one with a list, and its controls aren't in view until it scrolls to its end.
        compose.onAllNodesWithText("Page 1 of 2").assertCountEquals(0)
        compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(SCORES_PAGE_SIZE)
        compose.onNodeWithText("Page 1 of 2").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Page 2 of 2").assertExists()
    }

    @Test
    fun gameModeBuildsTheCardsOnScreenOnTheFrameItAppearsAndTheRestOverTheFramesAfter() = uiTest(TestScreen(360, 800)) {
        // Every mode with ten scores: a full card each, two or three to a screen.
        show(GameMode.entries.flatMapIndexed { m, mode -> (1..10).map { rowOf(m * 100L + it, 100 + it, mode) } })
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Game Mode").performClick()
        val first = modeName(GameMode.entries.first())
        val last = modeName(GameMode.entries.last())
        repeat(30) {
            if (compose.onAllNodesWithText(first).fetchSemanticsNodes().isEmpty()) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
        }
        compose.onNodeWithText(first).assertExists()
        compose.onAllNodesWithText(last).assertCountEquals(0)

        repeat(GameMode.entries.size) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        GameMode.entries.forEach { compose.onAllNodesWithText(modeName(it)).assertCountEquals(1) }
    }

    @Test
    fun signedInEachModeCardOpensItsGlobalLeaderboardWithoutMovingAnythingOnIt() = uiTest {
        // Signed out (or with no service): no button, and where the card's header row sits.
        show(someRows)
        compose.onNodeWithText("Game Mode").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Global leaderboard", substring = true)).assertCountEquals(0)
        val headerTop = compose.onAllNodesWithText("Player")[0].fetchSemanticsNode().boundsInRoot.top

        val services = LeaderboardServices()
        show(someRows, services)
        compose.onNodeWithText("Game Mode").performClick()
        compose.waitForIdle()
        val button = compose.onNode(hasContentDescription("Global leaderboard for Standard on Google Play Games"))
        assertEquals(Role.Button, button.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        compose.onNode(hasContentDescription("Global leaderboard for Tricolour on Google Play Games")).assertExists()
        assertEquals(headerTop, compose.onAllNodesWithText("Player")[0].fetchSemanticsNode().boundsInRoot.top)

        button.performClick()
        assertEquals(listOf<String?>("standard"), services.opened)
    }
}
