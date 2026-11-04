package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.ModePage
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
 * The Leaderboard's drop-down: the board it opens on, what each board shows, and what a screen reader hears for the
 * field and a row (the date a long press shows). How the boards look is for a render; this pins the structure.
 */
class ScoresScreenTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun show(
        rows: List<ScoreEntry>,
        services: GameServices = NoGameServices,
        saved: StateFlow<ModePage?> = MutableStateFlow(ModePage.STANDARD),
        onPick: (ModePage) -> Unit = {},
    ) {
        val viewModel = ScoresViewModel(ScoreRepository(InMemoryScoreDao(rows)), saved) { onPick(it) }
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

    private fun ComposeUiTest.field(value: String) = onNodeWithContentDescription("Leaderboard, $value")

    private fun ComposeUiTest.pick(name: String) {
        onNodeWithContentDescription("Leaderboard, ", substring = true).performClick()
        waitForIdle()
        onNodeWithContentDescription(name).performClick()
        waitForIdle()
    }

    @Test
    fun opensOnStandardsBoardWithTheDropDownAtTheTopAndARowSpeaksItsDateAloneWhichALongPressShows() = uiTest {
        show(someRows)
        field("Standard").assertExists()
        compose.onNodeWithText("Player1").assertExists()
        compose.onNodeWithText("Player3").assertExists()
        // Tricolour's player is on Tricolour's board, not this one.
        compose.onAllNodesWithText("Player2").assertCountEquals(0)

        val detail = compose.onNodeWithText("Player1").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertTrue(GameMode.entries.none { detail!!.contains(modeName(it)) }, detail.orEmpty())

        // A long press on a row arms its tooltip: its date, and no mode - "Standard" is still only the board's title.
        compose.onNodeWithText("Player1").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onAllNodesWithText("Standard").assertCountEquals(1)
    }

    @Test
    fun theDropDownIsOneControlWithItsValueAndStateAndItsMenuListsEveryModeThenCombinedWithTheBoardOnViewSelected() = uiTest {
        show(someRows)
        val config = field("Standard").fetchSemanticsNode().config
        assertEquals(Role.DropdownList, config.getOrNull(SemanticsProperties.Role))
        assertEquals("Collapsed", config.getOrNull(SemanticsProperties.StateDescription))

        field("Standard").performClick()
        compose.waitForIdle()
        assertEquals("Expanded", field("Standard").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        // Each mode then Combined, each one of a set the size of the menu, Standard (on view) first and selected.
        val names = GameMode.entries.map(::modeName) + "Combined"
        names.forEachIndexed { index, name ->
            val item = compose.onNodeWithContentDescription(name).fetchSemanticsNode().config
            assertEquals(Role.RadioButton, item.getOrNull(SemanticsProperties.Role), name)
            assertEquals(index == 0, item.getOrNull(SemanticsProperties.Selected), name)
            assertEquals(index, item.getOrNull(SemanticsProperties.CollectionItemInfo)!!.rowIndex, name)
        }
        assertEquals("Selected", compose.onNodeWithContentDescription("Standard").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun pickingAModeShowsOnlyItsBoardUnderItsTitleAndARowThereSpeaksItsDateAlone() = uiTest {
        val picked = mutableListOf<ModePage>()
        show(someRows + rowOf(4, 90, GameMode.QUICKFIRE), onPick = { picked += it })
        compose.pick("Tricolour")
        field("Tricolour").assertExists()
        assertEquals(listOf<ModePage>(ModePage.Mode(GameMode.TRICOLOUR)), picked)
        // Its board's title is a heading; the other modes' players aren't on it.
        compose.onNode(hasText("Tricolour") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        compose.onNodeWithText("Player2").assertExists()
        compose.onAllNodesWithText("Player1").assertCountEquals(0)
        val detail = compose.onNodeWithText("Player2").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertTrue(GameMode.entries.none { detail!!.contains(modeName(it)) }, detail.orEmpty())

        // Quickfire, off the Leaderboard's achievements, has its board too.
        compose.pick(modeName(GameMode.QUICKFIRE))
        compose.onNodeWithText("Player4").assertExists()
        // A mode with no score says so.
        compose.pick(modeName(GameMode.STUD))
        compose.onNodeWithText("No scores in ${modeName(GameMode.STUD)} yet").assertExists()
        compose.onAllNodesWithText("Player").assertCountEquals(0)
    }

    @Test
    fun combinedRanksEveryModesGamesAndARowThereSpeaksItsDateAndMode() = uiTest {
        show(someRows + rowOf(4, 900, GameMode.STUD), saved = MutableStateFlow(ModePage.Combined))
        field("Combined").assertExists()
        // Every mode's rows, Stud's 900 first.
        listOf("Player1", "Player2", "Player3", "Player4").forEach { compose.onNodeWithText(it).assertExists() }
        val detail = compose.onNodeWithText("Player4").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertTrue(detail!!.endsWith(", ${modeName(GameMode.STUD)}"), detail)
    }

    @Test
    fun aBoardPickedStartsAtItsTopWhereverTheLastBoardsTopRowIsOnIt() = uiTest {
        // Standard's best (Player100) is 16th on Combined, under 15 Stud scores, with Standard's 14 others after it: a
        // board that scrolls, and a row near enough for the list to keep its place by.
        val stud = (1..15).map { rowOf(it.toLong(), 1_000 + it, GameMode.STUD) }
        val standard = (100..114).map { rowOf(it.toLong(), 400 - it, GameMode.STANDARD) }
        show(stud + standard)
        compose.onNodeWithText("Player100").assertIsDisplayed()
        compose.pick("Combined")
        // Combined's best, at its top.
        compose.onNodeWithText("Player15").assertIsDisplayed()
    }

    @Test
    fun theBoardSavedIsTheOneItOpensOnOnceItHasLoaded() = uiTest {
        show(someRows, saved = MutableStateFlow(ModePage.Mode(GameMode.TRICOLOUR)))
        field("Tricolour").assertExists()
        compose.onNodeWithText("Player2").assertExists()
        compose.onAllNodesWithText("Player1").assertCountEquals(0)
    }

    @Test
    fun aBoardPagesWithItsControlsUnderTheCard() = uiTest {
        show((1..SCORES_PAGE_SIZE + 5).map { rowOf(it.toLong(), it, GameMode.STANDARD) }, saved = MutableStateFlow(ModePage.Mode(GameMode.STANDARD)))
        compose.onNodeWithText("Page 1 of 2").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Page 2 of 2").assertExists()
    }

    @Test
    fun signedInAModesBoardOpensItsGlobalLeaderboardWithoutMovingAnythingOnIt() = uiTest {
        val standard = MutableStateFlow<ModePage?>(ModePage.Mode(GameMode.STANDARD))
        // Signed out (or with no service): no button, and where the table's headings sit.
        show(someRows, saved = standard)
        compose.onAllNodes(hasContentDescription("Global leaderboard", substring = true)).assertCountEquals(0)
        val headerTop = compose.onAllNodesWithText("Player")[0].fetchSemanticsNode().boundsInRoot.top

        val services = LeaderboardServices()
        show(someRows, services, saved = standard)
        val button = compose.onNode(hasContentDescription("Global leaderboard for Standard on Google Play Games"))
        assertEquals(Role.Button, button.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        // Only this board's own button.
        compose.onAllNodes(hasContentDescription("Global leaderboard for Tricolour", substring = true)).assertCountEquals(0)
        assertEquals(headerTop, compose.onAllNodesWithText("Player")[0].fetchSemanticsNode().boundsInRoot.top)

        button.performClick()
        assertEquals(listOf<String?>("standard"), services.opened)
    }
}
