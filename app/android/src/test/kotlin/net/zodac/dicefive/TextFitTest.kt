package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.AchievementsViewModel
import net.zodac.dicefive.ui.game.GameOverScreen
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.menu.MenuScreen
import net.zodac.dicefive.ui.rules.RulesScreen
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.settings.AboutDialog
import net.zodac.dicefive.ui.settings.SettingsScreen
import net.zodac.dicefive.ui.settings.SettingsViewModel
import net.zodac.dicefive.ui.setup.GameSetupScreen
import net.zodac.dicefive.ui.statistics.StatisticsScreen
import net.zodac.dicefive.ui.statistics.StatisticsViewModel
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.styles.StylesViewModel
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.TableBackgrounds
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen, drawn in Spanish on a 360dp phone, has no word broken across two lines ("Usuar" / "io") - the sign of a
 * control sized for the English word. A longer language's text has to fit its box by the box growing or the text shrinking,
 * never by splitting a word. Reads each text's layout from the semantics tree; ellipsis is by design for names and isn't flagged.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h800dp")
// Real (native) text measurement: the default mode measures a character as a pixel, which can break nothing.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
open class TextFitTest {

    @get:Rule
    val compose = createComposeRule()

    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext()),
                LocalPlatformServices provides SilentPlatformServices,
            ) { DiceFiveTheme { block() } }
        }
        compose.waitForIdle()
    }

    /** The texts on screen that a line break split inside a word. */
    private fun brokenWords(): List<String> {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        return nodes.flatMap { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.mapNotNull { layout ->
                val text = layout.layoutInput.text.text
                val broken = (0 until layout.lineCount - 1).any { line ->
                    val end = layout.getLineEnd(line, visibleEnd = false)
                    end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
                }
                text.takeIf { broken }
            }
        }.distinct()
    }

    /** The tab labels on screen cut short - a tab's label is all it says, so it has to fit whole. */
    private fun cutOffTabLabels(): List<String> {
        val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        val labels = compose.onAllNodes(isTab, useUnmergedTree = true).fetchSemanticsNodes().flatMap { tab ->
            generateSequence(tab.children) { level -> level.flatMap { it.children }.takeIf { it.isNotEmpty() } }.flatten()
        }.filter { SemanticsActions.GetTextLayoutResult in it.config }
        return labels.flatMap { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            // Wider than the space it was drawn in (clipped), or ellipsised. The layout's own overflow flags aren't
            // used: a tab measures its label more than once, and they came back set for labels that fit.
            layouts.filter { layout ->
                layout.multiParagraph.intrinsics.maxIntrinsicWidth > node.size.width + 1 ||
                    (0 until layout.lineCount).any { line -> layout.isLineEllipsized(line) }
            }.map { it.layoutInput.text.text }
        }.distinct()
    }

    private fun assertNoBrokenWords(screen: String) {
        val broken = brokenWords()
        assertTrue("$screen has words split across lines: $broken", broken.isEmpty())
    }

    @Test
    fun `the new game form`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(4)
        viewModel.setPlayerType(2, PlayerType.AI)
        content { GameSetupScreen(viewModel = viewModel, onStartGame = {}, onBack = {}) }
        assertNoBrokenWords("New game")
    }

    /** What the modifiers row says with five switched on, in the language under test. */
    protected open val fiveModifiersEnabled = "5 activados"

    @Test
    fun `the modifiers with every one switched on`() {
        val viewModel = GameViewModel()
        viewModel.setTurnTimer(net.zodac.dicefive.model.TurnTimer.SECONDS_30)
        viewModel.setRollsPerTurn(3)
        viewModel.setStoredRolls(true)
        viewModel.setUnluckyDiceEnabled(true)
        viewModel.setExtendedScores(true)
        content { GameSetupScreen(viewModel = viewModel, onStartGame = {}, onBack = {}) }
        compose.onNodeWithText(fiveModifiersEnabled).performClick()
        compose.waitForIdle()
        assertNoBrokenWords("Modifiers")
    }

    @Test
    fun `the main menu`() {
        content { MenuScreen(hasInProgressGame = true, onContinue = {}, onNewGame = {}, onScores = {}, onStatistics = {}, onAchievements = {}, onStyles = {}, onRules = {}, onSettings = {}) }
        assertNoBrokenWords("Menu")
    }

    @Test
    fun `settings`() {
        val viewModel = SettingsViewModel(settingsRepository = net.zodac.dicefive.data.settings.SettingsRepository(InMemoryPreferences()))
        content { SettingsScreen(viewModel = viewModel, onBack = {}) }
        assertNoBrokenWords("Settings")
    }

    @Test
    fun `the about dialog`() {
        content { AboutDialog(onDismissRequest = {}) }
        assertNoBrokenWords("About")
    }

    @Test
    fun `the leaderboard`() {
        val viewModel = ScoresViewModel(ScoreRepository(RowsDao(listOf(rowOf(1, 300, GameMode.STANDARD), rowOf(2, 250, GameMode.QUICKFIRE)))))
        content { ScoresScreen(viewModel = viewModel, onBack = {}) }
        assertNoBrokenWords("Leaderboard")
    }

    @Test
    fun `statistics`() {
        val viewModel = StatisticsViewModel()
        content { StatisticsScreen(viewModel = viewModel, onBack = {}) }
        assertNoBrokenWords("Statistics")
    }

    @Test
    fun `achievements`() {
        val viewModel = AchievementsViewModel()
        content { AchievementsScreen(viewModel = viewModel, onBack = {}) }
        assertNoBrokenWords("Achievements")
    }

    @Test
    fun `styles`() {
        val saved = MutableStateFlow<SavedStyles?>(
            SavedStyles(DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, AchievementsState()),
        )
        val viewModel = StylesViewModel(savedStyles = saved)
        content { StylesScreen(viewModel = viewModel, onBack = {}) }
        compose.mainClock.advanceTimeBy(5_000)
        assertNoBrokenWords("Styles")
    }

    @Test
    fun `every page of the rules`() {
        content { RulesScreen(onBack = {}) }
        val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        // The top row's tabs (the groups) come first, then the row of the showing group's pages.
        val groups = 3
        val broken = mutableMapOf<String, List<String>>()
        for (group in 0 until groups) {
            compose.onAllNodes(isTab)[group].performClick()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            val pages = compose.onAllNodes(isTab).fetchSemanticsNodes().size - groups
            for (page in 0 until pages) {
                compose.onAllNodes(isTab)[groups + page].performClick()
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitForIdle()
                (brokenWords() + cutOffTabLabels()).takeIf { it.isNotEmpty() }?.let { broken["$group/$page"] = it }
            }
        }
        assertTrue("Rules pages with words split across lines, or tabs cut off: $broken", broken.isEmpty())
    }

    @Test
    fun `a game in progress`() {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.startGame()
        content { GameScreen(viewModel = viewModel) }
        assertNoBrokenWords("Game")
    }

    @Test
    fun `the results page`() {
        val state = GameEngine.newGame(
            listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Ana"), PlayerConfig(slot = 2, type = PlayerType.HUMAN, name = "Luis")),
            GameMode.STANDARD,
        )
        content { GameOverScreen(state = state, onBackToMenu = {}, onPlayAgain = {}, onReviewScorecards = {}, soundEnabled = false) }
        assertNoBrokenWords("Results")
    }

    @Test
    fun `an unlock banner`() {
        content { AchievementBannerHost(isOnGameScreen = { false }, onAchievementSelected = {}, onStylesSelected = {}) { Box(Modifier.fillMaxSize()) } }
        AchievementEvents.emit(AchievementEvent.Unlocked(Achievement.NON_STANDARD_MODE))
        compose.waitForIdle()
        assertNoBrokenWords("Banner")
    }
}
