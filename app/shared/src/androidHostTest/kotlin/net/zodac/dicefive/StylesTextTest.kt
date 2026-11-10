package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.style.StyleAccess
import net.zodac.dicefive.game.style.UnlockCount
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DiceMats
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.ScoreBoxes
import net.zodac.dicefive.ui.style.ScoreFrames
import net.zodac.dicefive.ui.style.TableBackgrounds
import net.zodac.dicefive.ui.styles.StylesScreen
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.SemanticsMatcher
import net.zodac.dicefive.game.style.StyleCategory
import net.zodac.dicefive.ui.styles.StyleCategoryScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * The Styles pages' words: the Styles page's title, its fields and what TalkBack says of each; a category page's title,
 * and how a style - locked, or in several colours - is named and what a tap is called.
 *
 * Android only: it steps the clock through seconds of whole pages, which the desktop test harness draws frame by frame.
 */
class StylesTextTest : UiTest() {

    private val showcase = Showcase { compose }

    // Unlocked only is stored by the settings, so each test starts from a new repository with it off.
    private val settings = SettingsRepository(InMemoryPreferences())

    private fun viewModel(achievements: AchievementsState) = stylesViewModel(
        MutableStateFlow<SavedStyles?>(SavedStyles(DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, achievements)),
        settings,
    )

    /** The Styles page, reporting each field opened to [opened]. */
    private fun showStyles(achievements: AchievementsState, opened: MutableList<StyleCategory> = mutableListOf()) {
        val viewModel = viewModel(achievements)
        showcase.show {
            DiceFiveTheme {
                CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) { StylesScreen(viewModel = viewModel, onBack = {}, onOpenCategory = { opened += it }) }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    /** [category]'s page. */
    private fun showCategory(achievements: AchievementsState, category: StyleCategory = StyleCategory.DICE) {
        val viewModel = viewModel(achievements)
        showcase.show {
            DiceFiveTheme { CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) { StyleCategoryScreen(viewModel = viewModel, category = category, onBack = {}) } }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    @Test
    fun `the Styles page is titled Styles - each category one button saying its pick and count - and a tap opens its page`() = uiTest {
        val achievements = AchievementsState(unlockedAt = mapOf(Achievement.NAT_20 to 0L))
        val opened = mutableListOf<StyleCategory>()
        showStyles(achievements, opened)
        compose.onNodeWithText("Styles").assertExists()
        val count = { catalog: net.zodac.dicefive.ui.style.StyleCatalog<*> -> catalog.unlockCount(StyleAccess(achievements)).let { "${it.unlocked} of ${it.total} unlocked" } }
        val expected = listOf(
            Triple("Dice", "Classic, Ivory. ${count(DiceStyles)}", StyleCategory.DICE),
            Triple("Dice Cup", "Classic, Gold. ${count(DiceCupStyles)}", StyleCategory.DICE_CUP),
            Triple("Mat", "Classic, Blue. ${count(DiceMats)}", StyleCategory.MAT),
            Triple("Background", "Classic, Navy. ${count(TableBackgrounds)}", StyleCategory.BACKGROUND),
            Triple("Frame", "Classic. ${count(ScoreFrames)}", StyleCategory.FRAME),
            Triple("Boxes", "Classic. ${count(ScoreBoxes)}", StyleCategory.BOX),
        )
        val buttons = compose.onAllNodes(hasRole(Role.Button) and hasClickLabelStartingChoose()).fetchSemanticsNodes()
            .associate { it.config[SemanticsProperties.ContentDescription].single() to it.config }
        for ((title, state, category) in expected) {
            val button = buttons.getValue(title)
            assertEquals(state, button[SemanticsProperties.StateDescription], title)
            assertEquals("Choose $title", button[SemanticsActions.OnClick].label)
            compose.onNode(hasRole(Role.Button) and hasContentDescription(title)).performClick()
            assertEquals(category, opened.last())
        }
        // The heading over each field is the button's to say, not a stop of its own.
        compose.onNodeWithText("Dice Cup").assertDoesNotExist()
        assertEquals(expected.map { it.third }, opened)
    }

    @Test
    fun `the Styles page counts every colour unlocked - each field's count drawn as a fraction`() = uiTest {
        val achievements = AchievementsState(unlockedAt = mapOf(Achievement.NAT_20 to 0L))
        showStyles(achievements)
        val counts = listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds, ScoreFrames, ScoreBoxes).map { it.unlockCount(StyleAccess(achievements)) }
        val total = counts.reduce(UnlockCount::plus)
        compose.onNodeWithText("${total.unlocked} of ${total.total} unlocked").assertExists()
        for (count in counts) compose.onAllNodesWithText("${count.unlocked}/${count.total}", useUnmergedTree = true).onFirst().assertExists()
    }

    @Test
    fun `a category page is titled with its category - a locked style and one in several colours named and their taps`() = uiTest {
        showCategory(AchievementsState())
        compose.onNodeWithText("Dice").assertExists()
        compose.onNodeWithText("${DiceStyles.unlockCount(StyleAccess(AchievementsState())).let { "${it.unlocked} of ${it.total}" }} unlocked").assertExists()
        // A locked style says Locked - and its tap is called Show how to unlock.
        val frosted = compose.onAllNodesWithContentDescription("Frosted").onFirst().fetchSemanticsNode().config
        assertEquals("Locked", frosted[SemanticsProperties.StateDescription])
        assertEquals("Show how to unlock Frosted", frosted[SemanticsActions.OnClick].label)

        // A style in several colours names the colour showing - and a long press is called Choose colour.
        showCategory(AchievementsState(unlockedAt = Achievement.entries.associateWith { 0L }))
        val metal = compose.onNode(hasContentDescription("Metal, Gold")).fetchSemanticsNode().config
        assertEquals("Select", metal[SemanticsActions.OnClick].label)
        assertEquals("Choose Metal colour", metal[SemanticsActions.OnLongClick].label)
    }

    @Test
    fun `a locked style tile shows the unlock requirement dialog with resolved style name`() = uiTest {
        showCategory(AchievementsState())
        val metal = compose.onAllNodesWithContentDescription("Metal").onFirst()
        metal.performScrollTo()
        metal.performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Earn 20 achievements to unlock Metal. You've earned 0 so far.").assertExists()
    }

    @Test
    fun `The Unlocked switch is one switch - on it hides every locked style but keeps the unlocked ones - and stays on when the page is opened again`() = uiTest {
        showCategory(AchievementsState())
        // The label is "All" while it's off and "Unlocked" while it's on.
        val switch = compose.onNodeWithText("All").fetchSemanticsNode().config
        assertEquals(Role.Switch, switch[SemanticsProperties.Role])
        assertEquals(ToggleableState.Off, switch[SemanticsProperties.ToggleableState])
        compose.onAllNodesWithContentDescription("Frosted").onFirst().assertExists()

        val widthOff = compose.onNodeWithText("All").fetchSemanticsNode().size.width
        compose.onNodeWithText("All").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(ToggleableState.On, compose.onNodeWithText("Unlocked").fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
        // The label holds the longer word's width either way, so nothing beside it moves.
        assertEquals(widthOff, compose.onNodeWithText("Unlocked").fetchSemanticsNode().size.width)
        assertEquals(0, compose.onAllNodesWithContentDescription("Frosted").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasStateDescription("Locked")).fetchSemanticsNodes().size)
        compose.onAllNodesWithContentDescription("Classic", substring = true).onFirst().assertExists()

        // Another category's page too, as the switch is one setting for every page.
        showCategory(AchievementsState(), StyleCategory.MAT)
        assertEquals(ToggleableState.On, compose.onNodeWithText("Unlocked").fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
        assertEquals(0, compose.onAllNodes(hasStateDescription("Locked")).fetchSemanticsNodes().size)
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    // A field's button: its tap is called "Choose ...".
    private fun hasClickLabelStartingChoose() = SemanticsMatcher("tap called Choose") { it.config.getOrNull(SemanticsActions.OnClick)?.label?.startsWith("Choose ") == true }
}
