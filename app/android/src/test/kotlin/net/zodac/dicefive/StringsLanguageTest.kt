package net.zodac.dicefive

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.data.scores.PlayerStatistics
import net.zodac.dicefive.ui.setup.GameSetupScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.statistics.PlayerStatsCard
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * On a phone in a language the app doesn't have, the English it falls back to is laid out, pluralised and dated as
 * English - not in the phone's language's direction, plural rules and month names (StringsLanguage, .claude/I18N.md).
 */
@RunWith(AndroidJUnit4::class)
class StringsLanguageTest {

    @get:Rule
    val compose = createComposeRule()

    private val player = PlayerStatistics(
        playerName = "Ann",
        firstPlayedEpochMillis = 0L,
        gamesPlayed = 25,
        gamesWon = 3,
        gamesLost = 1,
        currentWinStreak = 1,
        bestWinStreak = 2,
        maxScore = 250,
        totalScore = 1_001,
        fiveOfAKindCount = 4,
        soloGames = 21,
    )

    private val showcase = Showcase(compose)

    private fun layoutDirection(): LayoutDirection {
        var direction: LayoutDirection? = null
        showcase.show { DiceFiveTheme { direction = LocalLayoutDirection.current } }
        return direction!!
    }

    /** What the stats card says, opened. */
    private fun openedCard(player: PlayerStatistics): String {
        showcase.show { DiceFiveTheme { PlayerStatsCard(player = player, onLongPress = {}) } }
        compose.onNodeWithContentDescription(player.playerName, substring = true).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        return compose.onNodeWithContentDescription(player.playerName, substring = true).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
    }

    @Test
    @Config(qualifiers = "fa-w360dp-h800dp")
    fun `a Persian phone lays the English app out left to right - its back arrow on the left - and dates it with an English month`() {
        assertEquals(LayoutDirection.Ltr, layoutDirection())

        val viewModel = GameViewModel()
        showcase.show { DiceFiveTheme { GameSetupScreen(viewModel = viewModel, onStartGame = {}, onBack = {}) } }
        val back = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        assertTrue("Back arrow at $back on a screen $width wide", back.right < width / 2)

        val spoken = openedCard(player)
        assertTrue(spoken, Regex("""First played [A-Z][a-z]{2,3} \d\d, \d{4} \d\d:\d\d\.""").containsMatchIn(spoken))
    }

    @Test
    @Config(qualifiers = "iw")
    fun `a Hebrew phone lays the English app out left to right`() {
        assertEquals(LayoutDirection.Ltr, layoutDirection())
    }

    // Russian's "one" covers 21, 31...: picked by Russian's rules, the English would read "21 solo game played".
    @Test
    @Config(qualifiers = "ru")
    fun `on a Russian phone English plurals follow English's rules`() {
        val spoken = openedCard(player)
        assertTrue(spoken, spoken.contains("21 solo games played."))
        assertTrue(spoken, spoken.contains("Played 25,"))
    }

    @Test
    fun `a name in a right-to-left script is set apart inside an English sentence - one in its own direction left as it is`() {
        showcase.show { DiceFiveTheme { PlayerStatsCard(player = player.copy(playerName = "علي"), onLongPress = {}) } }
        assertEquals("Delete \u2068علي\u2069's stats", compose.onNodeWithContentDescription("علي", substring = true).fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)

        showcase.show { DiceFiveTheme { PlayerStatsCard(player = player, onLongPress = {}) } }
        assertEquals("Delete Ann's stats", compose.onNodeWithContentDescription("Ann", substring = true).fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)
    }
}
