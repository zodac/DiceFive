package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.app.AchievementEvent
import net.zodac.dicefive.app.AchievementEvents
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.achievements.resetAchievementsScrollMemory
import net.zodac.dicefive.ui.game.GameOverScreen
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.menu.MenuScreen
import net.zodac.dicefive.ui.rules.RulesScreen
import net.zodac.dicefive.ui.rules.resetRulesPageMemory
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.settings.AboutDialog
import net.zodac.dicefive.ui.settings.SettingsScreen
import net.zodac.dicefive.ui.settings.settingsViewModel
import net.zodac.dicefive.ui.setup.GameSetupScreen
import net.zodac.dicefive.ui.statistics.StatisticsScreen
import net.zodac.dicefive.ui.statistics.StatisticsViewModel
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DiceMats
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.TableBackgrounds
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.robolectric.annotation.Config

/**
 * Every screen, drawn in Spanish on a 360dp phone, has no word broken across two lines ("Usuar" / "io") - the sign of a
 * control sized for the English word. A longer language's text has to fit its box by the box growing or the text shrinking,
 * never by splitting a word. Reads each text's layout from the semantics tree; ellipsis is by design for names and isn't flagged.
 */
@Config(qualifiers = "es-w360dp-h800dp")
open class TextFitTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) = showcase.show {
        CompositionLocalProvider(
            LocalAppContainer provides inMemoryAppContainer(),
            LocalPlatformServices provides SilentPlatformServices,
        ) { DiceFiveTheme { block() } }
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

    /** What the modifiers row says with five switched on, in the language under test. */
    protected open val fiveModifiersEnabled = "5 activados"

    @Test
    fun `no screen splits a word across lines`() = uiTest {
        // Every screen's words split across lines, by screen - all of them checked before anything fails.
        val broken = mutableMapOf<String, List<String>>()
        fun check(screen: String) = brokenWords().takeIf { it.isNotEmpty() }?.let { broken[screen] = it }

        val setup = gameViewModel()
        setup.setupForm.setPlayerCount(4)
        setup.setupForm.setPlayerType(2, PlayerType.AI)
        content { GameSetupScreen(viewModel = setup, onStartGame = {}, onTutorial = {}, onBack = {}) }
        check("New game")

        // The modifiers, with every one switched on.
        val modifiers = gameViewModel()
        modifiers.setupForm.setTurnTimer(net.zodac.dicefive.model.TurnTimer.SECONDS_30)
        modifiers.setupForm.setRollsPerTurn(3)
        modifiers.setupForm.setStoredRolls(true)
        modifiers.setupForm.setUnluckyDiceEnabled(true)
        modifiers.setupForm.setExtendedScores(true)
        content { GameSetupScreen(viewModel = modifiers, onStartGame = {}, onTutorial = {}, onBack = {}) }
        compose.onNodeWithText(fiveModifiersEnabled).performClick()
        compose.waitForIdle()
        check("Modifiers")

        content { MenuScreen(hasInProgressGame = true, onContinue = {}, onNewGame = {}, onScores = {}, onStatistics = {}, onAchievements = {}, onStyles = {}, onRules = {}, onSettings = {}) }
        check("Menu")

        val settings = settingsViewModel()
        content { SettingsScreen(viewModel = settings, onBack = {}) }
        check("Settings")

        content { AboutDialog(onDismissRequest = {}) }
        check("About")

        val scores = ScoresViewModel(ScoreRepository(InMemoryScoreDao(listOf(rowOf(1, 300, GameMode.STANDARD), rowOf(2, 250, GameMode.QUICKFIRE)))))
        content { ScoresScreen(viewModel = scores, onBack = {}) }
        check("Leaderboard")

        val statistics = StatisticsViewModel(ScoreRepository(InMemoryScoreDao(emptyList())), SettingsRepository(InMemoryPreferences()))
        content { StatisticsScreen(viewModel = statistics, onBack = {}) }
        check("Statistics")

        resetAchievementsScrollMemory()
        val achievements = achievementsViewModel()
        content { AchievementsScreen(viewModel = achievements, onBack = {}) }
        check("Achievements")

        val saved = MutableStateFlow<SavedStyles?>(
            SavedStyles(DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, AchievementsState()),
        )
        val styles = stylesViewModel(saved)
        content { StylesScreen(viewModel = styles, onBack = {}) }
        compose.mainClock.advanceTimeBy(5_000)
        check("Styles")

        val game = gameViewModel()
        game.setupForm.setPlayerCount(2)
        game.setupForm.setPlayerType(2, PlayerType.AI)
        game.startGame()
        content { GameScreen(viewModel = game) }
        check("Game")

        val finished = GameEngine.newGame(
            listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Ana"), PlayerConfig(slot = 2, type = PlayerType.HUMAN, name = "Luis")),
            GameMode.STANDARD,
        )
        content { GameOverScreen(state = finished, onBackToMenu = {}, onPlayAgain = {}, onReviewScorecards = {}) }
        check("Results")

        content { AchievementBannerHost(isOnGameScreen = { false }, onAchievementSelected = {}, onStylesSelected = {}) { Box(Modifier.fillMaxSize()) } }
        AchievementEvents.emit(AchievementEvent.Unlocked(Achievement.WHERE_WERE_GOING_WE_DONT_NEED_RULES))
        compose.waitForIdle()
        check("Banner")

        assertTrue(broken.isEmpty(), "Screens with words split across lines: $broken")
    }

    @Test
    fun `every page of the rules`() = uiTest {
        resetRulesPageMemory()
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
        assertTrue(broken.isEmpty(), "Rules pages with words split across lines, or tabs cut off: $broken")
    }
}
