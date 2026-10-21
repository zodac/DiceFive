package net.zodac.dicefive

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.data.scores.ModeStatistics
import net.zodac.dicefive.data.scores.PlayerStatistics
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.game.TurnTimerBadge
import net.zodac.dicefive.ui.game.UndoButton
import net.zodac.dicefive.ui.statistics.PlayerStatsCard
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a screen reader gets from the Styles, Statistics and turn-timer changes: a style tile that is
 * a selectable radio button with its pick state, a stats card whose delete is a button of its own, and a
 * timer that warns once in words rather than by colour alone.
 */
@RunWith(AndroidJUnit4::class)
class ScreenReaderSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    /** The stats card itself: the one carrying the state, not the delete button, whose label has the name in it too. */
    private fun statsCard() =
        compose.onNode(hasContentDescription("Ann", substring = true) and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))

    private val player = PlayerStatistics(
        playerName = "Ann",
        firstPlayedEpochMillis = 0L,
        gamesPlayed = 5,
        gamesWon = 3,
        gamesLost = 2,
        currentWinStreak = 1,
        bestWinStreak = 2,
        maxScore = 250,
        totalScore = 1_001,
        fiveOfAKindCount = 4,
        soloGames = 2,
        modes = listOf(
            ModeStatistics(GameMode.STANDARD, gamesPlayed = 3, gamesWon = 2, gamesLost = 1, soloGames = 0, maxScore = 250),
            ModeStatistics(GameMode.QUICKFIRE, gamesPlayed = 2, gamesWon = 0, gamesLost = 0, soloGames = 2, maxScore = 1_180),
        ),
    )

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private val showcase = Showcase(compose)

    @Test
    fun `a style tile is a radio button saying its place in the row - the picked one selected - a locked one saying how to unlock it`() {
        val viewModel = stylesViewModel()
        compose.setContent { DiceFiveTheme { StylesScreen(viewModel = viewModel, onBack = {}) } }

        val radios = compose.onAllNodes(hasRole(Role.RadioButton)).fetchSemanticsNodes()
        assertTrue(radios.isNotEmpty())
        assertTrue(radios.any { it.config.getOrNull(SemanticsProperties.Selected) == true })
        radios.forEach { assertTrue(it.config.getOrNull(SemanticsProperties.ContentDescription) != null) }
        // The badge and colour dots are folded into the tile, not stops of their own.
        assertTrue(compose.onAllNodes(hasContentDescription("Selected")).fetchSemanticsNodes().isEmpty())

        val columns = radios.map { it.config[SemanticsProperties.CollectionItemInfo].columnIndex }
        assertEquals(columns.sorted(), columns.sorted().distinct().let { columns.sorted() })

        val locked = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Locked")).fetchSemanticsNodes()
        assertTrue(locked.isNotEmpty())
        locked.forEach {
            assertEquals(Role.Button, it.config[SemanticsProperties.Role])
            assertTrue(it.config[SemanticsActions.OnClick].label!!.startsWith("Show how to unlock"))
        }
    }

    @Test
    fun `a stats card is collapsed to name and best score - its header opening Overview and By mode - each a stop or two in words`() {
        var deleted = 0
        showcase.show { DiceFiveTheme { PlayerStatsCard(player = player, onDelete = { deleted++ }) } }

        var config = statsCard().fetchSemanticsNode().config
        assertEquals("Ann. Best score 250.", config[SemanticsProperties.ContentDescription].single())
        assertEquals("Collapsed", config[SemanticsProperties.StateDescription])
        assertEquals("Show details", config[SemanticsActions.OnClick].label)
        // The best score is drawn in points; it's spoken as "Best score".
        compose.onNodeWithText("250pts", useUnmergedTree = true).assertExists()
        // The long press is gone: deleting is a button of its own, not a hidden gesture or a custom action.
        assertFalse(SemanticsActions.OnLongClick in config)
        assertFalse(SemanticsActions.CustomActions in config)

        statsCard().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        config = statsCard().fetchSemanticsNode().config
        assertEquals("Ann. Best score 250.", config[SemanticsProperties.ContentDescription].single())
        assertEquals("Expanded", config[SemanticsProperties.StateDescription])
        assertEquals("Hide details", config[SemanticsActions.OnClick].label)

        // Overview: the numbers and the won/lost/solo split, then the rest.
        compose.onNodeWithContentDescription("Played 5, won 3, lost 2. 2 solo games played. Win rate 60%. Average score 200.").assertExists()
        val details = compose.onNodeWithContentDescription("Win streak 1", substring = true).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
        assertTrue(details, details.startsWith("Win streak 1, best win streak 2. 4 5x scored. Total points scored: 1,001. First played "))

        // By mode: a stop per mode, the win rate left out with nothing but solo games; the headings and bar are silent.
        compose.onNodeWithText("By mode").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Standard. Best score 250. 3 games played. Win rate 67%. 0 solo games played.").assertExists()
        compose.onNodeWithContentDescription("Quickfire. Best score 1,180. 2 games played. 2 solo games played.").assertExists()
        compose.onAllNodesWithText("Mode").assertCountEquals(0)
        compose.onAllNodesWithText("Games").assertCountEquals(0)

        val delete = compose.onNodeWithContentDescription("Delete Ann's stats")
        assertEquals(Role.Button, delete.fetchSemanticsNode().config[SemanticsProperties.Role])
        delete.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, deleted)

        // One solo game is said as game - not games - and only solo games leave no win rate.
        showcase.show { DiceFiveTheme { PlayerStatsCard(player = player.copy(gamesWon = 0, gamesLost = 0, soloGames = 1, currentWinStreak = 0, bestWinStreak = 0), onDelete = {}) } }
        statsCard().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Played 5, won 0, lost 0. 1 solo game played. Average score 200.").assertExists()
        // The dashes in their place - the win rate and the win streak, neither won yet - are drawn, not spoken.
        compose.onAllNodesWithText("-", useUnmergedTree = true).assertCountEquals(2)
        val noStreak = compose.onNodeWithContentDescription("4 5x scored", substring = true).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
        assertTrue(noStreak, noStreak.startsWith("4 5x scored. Total points scored: 1,001. First played "))
    }

    @Test
    fun `the timer says its seconds in words - live only once it flashes - and undo is one button named by its icon`() {
        showcase.show { DiceFiveTheme { TurnTimerBadge(secondsRemaining = 12) } }
        compose.onNodeWithContentDescription("Time left: 12 seconds").assertContentDescriptionEquals("Time left: 12 seconds")
        assertFalse(SemanticsProperties.LiveRegion in compose.onNodeWithContentDescription("Time left: 12 seconds").fetchSemanticsNode().config)

        // It warns once, as a polite live region, when the flash starts.
        showcase.show { DiceFiveTheme { TurnTimerBadge(secondsRemaining = 5) } }
        val config = compose.onNodeWithContentDescription("Time running out", substring = true).fetchSemanticsNode().config
        assertEquals(androidx.compose.ui.semantics.LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])

        var undone = 0
        showcase.show { DiceFiveTheme { UndoButton(enabled = true, onClick = { undone++ }) } }
        val undo = compose.onNodeWithContentDescription("Undo")
        assertEquals(Role.Button, undo.fetchSemanticsNode().config[SemanticsProperties.Role])
        // The icon's description merges into the button: one stop, named once - not a button and a separate image.
        assertEquals(1, compose.onAllNodes(hasRole(Role.Button)).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(hasContentDescription("Undo")).fetchSemanticsNodes().size)
        undo.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, undone)
    }
}
