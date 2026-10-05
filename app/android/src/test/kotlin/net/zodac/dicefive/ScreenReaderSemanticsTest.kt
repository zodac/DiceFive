package net.zodac.dicefive

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.data.scores.PlayerStatistics
import net.zodac.dicefive.ui.game.TurnTimerBadge
import net.zodac.dicefive.ui.game.UndoButton
import net.zodac.dicefive.ui.statistics.PlayerStatsCard
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.styles.StylesViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader gets from the Styles, Statistics and turn-timer changes: a style tile that is
 * a selectable radio button with its pick state, a stats card whose only action is the delete, and a
 * timer that warns once in words rather than by colour alone.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScreenReaderSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

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
    )

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun `a style tile is a radio button and the picked one is selected`() {
        val viewModel = StylesViewModel()
        compose.setContent { DiceFiveTheme { StylesScreen(viewModel = viewModel, onBack = {}) } }

        val radios = compose.onAllNodes(hasRole(Role.RadioButton)).fetchSemanticsNodes()
        assertTrue(radios.isNotEmpty())
        assertTrue(radios.any { it.config.getOrNull(SemanticsProperties.Selected) == true })
        radios.forEach { assertTrue(it.config.getOrNull(SemanticsProperties.ContentDescription) != null) }
        // The badge and colour dots are folded into the tile, not stops of their own.
        assertTrue(compose.onAllNodes(hasContentDescription("Selected")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a style tile says its place in the row`() {
        val viewModel = StylesViewModel()
        compose.setContent { DiceFiveTheme { StylesScreen(viewModel = viewModel, onBack = {}) } }

        val columns = compose.onAllNodes(hasRole(Role.RadioButton)).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.CollectionItemInfo].columnIndex }
        assertTrue(columns.isNotEmpty())
        assertEquals(columns.sorted(), columns.sorted().distinct().let { columns.sorted() })
    }

    @Test
    fun `a locked style says it is locked and its action says how to unlock it`() {
        val viewModel = StylesViewModel()
        compose.setContent { DiceFiveTheme { StylesScreen(viewModel = viewModel, onBack = {}) } }

        val locked = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Locked"))
            .fetchSemanticsNodes()
        assertTrue(locked.isNotEmpty())
        locked.forEach {
            assertEquals(Role.Button, it.config[SemanticsProperties.Role])
            assertTrue(it.config[SemanticsActions.OnClick].label!!.startsWith("Show how to unlock"))
        }
    }

    @Test
    fun `a stats card is collapsed to name and best score and its tap opens it`() {
        var deleted = 0
        compose.setContent { DiceFiveTheme { PlayerStatsCard(player = player, onLongPress = { deleted++ }) } }

        val node = compose.onNodeWithContentDescription("Ann", substring = true)
        var config = node.fetchSemanticsNode().config
        assertEquals("Ann. Best score 250.", config[SemanticsProperties.ContentDescription].single())
        assertEquals("Collapsed", config[SemanticsProperties.StateDescription])
        assertEquals("Show details", config[SemanticsActions.OnClick].label)
        assertEquals("Delete Ann's stats", config[SemanticsActions.OnLongClick].label)

        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        config = compose.onNodeWithContentDescription("Ann", substring = true).fetchSemanticsNode().config
        val spoken = config[SemanticsProperties.ContentDescription].single()
        assertEquals("Expanded", config[SemanticsProperties.StateDescription])
        assertEquals("Hide details", config[SemanticsActions.OnClick].label)
        assertTrue(spoken.contains("Played 5, won 3, lost 2"))
        assertTrue(spoken.contains("Total score 1,001. Average score 200."))
        assertTrue(spoken.contains("4 5x scored. 2 solo games played."))

        node.performSemanticsAction(SemanticsActions.OnLongClick)
        config[SemanticsActions.CustomActions].single().action()
        assertEquals(2, deleted)
    }

    @Test
    fun `the timer says its seconds in words and is not live while there is time`() {
        compose.setContent { DiceFiveTheme { TurnTimerBadge(secondsRemaining = 12) } }

        compose.onNodeWithContentDescription("Time left: 12 seconds").assertContentDescriptionEquals("Time left: 12 seconds")
        assertFalse(
            SemanticsProperties.LiveRegion in compose.onNodeWithContentDescription("Time left: 12 seconds").fetchSemanticsNode().config,
        )
    }

    @Test
    fun `the timer warns once as a polite live region when the flash starts`() {
        compose.setContent { DiceFiveTheme { TurnTimerBadge(secondsRemaining = 5) } }

        val config = compose.onNodeWithContentDescription("Time running out", substring = true).fetchSemanticsNode().config
        assertEquals(androidx.compose.ui.semantics.LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
    }

    @Test
    fun `undo is one button named by its label - the icon is not a stop of its own`() {
        var undone = 0
        compose.setContent { DiceFiveTheme { UndoButton(enabled = true, onClick = { undone++ }) } }

        val undo = compose.onNodeWithText("Undo")
        assertEquals(Role.Button, undo.fetchSemanticsNode().config[SemanticsProperties.Role])
        // Text and icon are one merged node: the icon has no description that could add a second stop.
        assertEquals(1, compose.onAllNodes(hasRole(Role.Button)).fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodes(hasContentDescription("", substring = true)).fetchSemanticsNodes().isEmpty())
        undo.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, undone)
    }
}
