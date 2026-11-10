package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.data.settings.DiceRollStyle
import net.zodac.dicefive.data.settings.FrameRate
import net.zodac.dicefive.data.settings.MotionGroup
import net.zodac.dicefive.data.settings.ScreenOrientation
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.settings.GraphicsScreen
import net.zodac.dicefive.ui.settings.GraphicsViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(360, 800)

/** The Styles card's groups, in its rows' order: the Styles page's. */
private val STYLE_ROWS = listOf(MotionGroup.DICE, MotionGroup.DICE_CUP, MotionGroup.MAT, MotionGroup.BACKGROUND, MotionGroup.SCORE_BOX)

/** The Advanced settings' row titles, top to bottom: Gameplay's and Miscellaneous' alphabetical, Styles' as the Styles page has them. */
private val ROWS = listOf(
    "Cup shake", "Dice roll", "Dice roll frame rate", "Score glow", "Scores",
    "Dice", "Dice Cup", "Mat", "Background", "Boxes",
    "Achievement banners", "Celebrations", "Menus & pages",
)

/**
 * The Graphics page: the Orientation, then the Animations level, each under a visible title, then the Advanced settings
 * under a heading of their own, in three cards (Gameplay, Styles, Miscellaneous), each closed until its title is
 * tapped: a frame rate for each group (spoken "Off", "30 frames a second", "60 frames a second", "Maximum"; the dice's
 * roll's without Off) and the dice's roll itself (Off, In place, or Tossed) - which start as the level's preset, leave
 * no level picked once changed, and go back to a level's preset when one is picked.
 */
class GraphicsScreenTest : UiTest() {

    private val repository = SettingsRepository(InMemoryPreferences())

    private fun show() {
        val viewModel = GraphicsViewModel(repository)
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides inMemoryAppContainer(), LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { GraphicsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
    }

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun hasState(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** Opens the Advanced settings' card titled [title]: its heading is one button, saying whether it's open. */
    private fun openCard(title: String) {
        compose.onNode(isHeading and hasText(title) and isButton and hasState("Collapsed")).performScrollTo().performClick()
        compose.onNode(isHeading and hasText(title) and hasState("Expanded")).assertExists()
    }

    /** Opens every Advanced settings card. */
    private fun openAdvanced() = listOf("Gameplay", "Styles", "Miscellaneous").forEach(::openCard)

    /** The groups' rate rows, top to bottom: Gameplay's (the dice's rate under their roll's style), Styles', then Miscellaneous'. */
    private val groupRows = listOf(MotionGroup.CUP_SHAKE, MotionGroup.DICE_ROLL, MotionGroup.SCORE_GLOW, MotionGroup.SCORES) + STYLE_ROWS +
        listOf(MotionGroup.BANNERS, MotionGroup.CELEBRATIONS, MotionGroup.MENUS)

    /**
     * The rows with an Off, top to bottom, after the level's: every rate row but the dice's, which has none - and the
     * dice's roll's style (null) in its place.
     */
    private val offRows = listOf(MotionGroup.CUP_SHAKE, null, MotionGroup.SCORE_GLOW, MotionGroup.SCORES) + STYLE_ROWS +
        listOf(MotionGroup.BANNERS, MotionGroup.CELEBRATIONS, MotionGroup.MENUS)

    /** [group]'s option spoken [spoken] - or, for a null [group], the dice's roll's Off. */
    private fun rate(group: MotionGroup?, spoken: String): SemanticsNodeInteraction {
        val all = compose.onAllNodes(hasContentDescription(spoken) and isSelectable())
        return if (spoken == "Off") all[offRows.indexOf(group) + 1] else all[groupRows.indexOf(group)]
    }

    private fun rollStyle(name: String) = if (name == "Off") rate(null, "Off") else compose.onNode(hasText(name) and isSelectable())

    private fun level(name: String) = compose.onAllNodes(hasText(name) and isSelectable())[0]

    @Test
    fun `orientation then animations - each titled - then the advanced settings - every group at the level's preset`() = uiTest(SCREEN) {
        show()
        compose.onNodeWithText("Graphics").assertExists()
        val orientationTop = compose.onNodeWithText("Orientation").getUnclippedBoundsInRoot().top
        val animationsTop = compose.onNodeWithText("Animations").getUnclippedBoundsInRoot().top
        assertTrue(orientationTop < animationsTop, "Orientation is above Animations")
        compose.onNode(hasText("Device") and isSelectable()).assertIsSelected()
        level("High").assertIsSelected()
        listOf("Off", "Low", "Medium").forEach { level(it).assertIsNotSelected() }
        // A heading of their own, not a button, over three cards, each closed until opened: only their headings show.
        compose.onNode(isHeading and hasText("Advanced settings") and !hasClickAction()).assertExists()
        ROWS.forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        // Each opens on its own.
        openCard("Styles")
        listOf("Dice", "Dice Cup", "Mat", "Background", "Boxes").forEach { compose.onNodeWithText(it).assertExists() }
        listOf("Cup shake", "Celebrations").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        listOf("Gameplay", "Miscellaneous").forEach(::openCard)
        ROWS.forEach { compose.onNodeWithText(it).assertExists() }
        // Top to bottom as listed.
        val rowTops = ROWS.map { compose.onNodeWithText(it).getUnclippedBoundsInRoot().top }
        assertEquals(rowTops.sorted(), rowTops, "rows in order")
        // In three cards, each under its heading: Gameplay's, the styles', then the rest.
        val top = { text: String -> compose.onNode(hasText(text)).getUnclippedBoundsInRoot().top }
        val heading = { text: String -> compose.onNode(isHeading and hasText(text)).getUnclippedBoundsInRoot().top }
        assertTrue(
            heading("Gameplay") < top("Cup shake") && top("Scores") < heading("Styles") &&
                heading("Styles") < top("Dice") && top("Boxes") < heading("Miscellaneous") && heading("Miscellaneous") < top("Achievement banners"),
            "Gameplay's rows, then Styles', then Miscellaneous'",
        )

        // At High, every group is at Max, drawn "Max" and spoken "Maximum", with the dice tossed.
        for (group in MotionGroup.entries) {
            rate(group, "Maximum").assertIsSelected()
            listOf("30 frames a second", "60 frames a second").forEach { rate(group, it).assertIsNotSelected() }
            if (group != MotionGroup.DICE_ROLL) rate(group, "Off").assertIsNotSelected()
        }
        // Drawn short, spoken in full.
        assertEquals(MotionGroup.entries.size, compose.onAllNodes(hasText("30fps") and hasContentDescription("30 frames a second") and isSelectable()).fetchSemanticsNodes().size)
        rollStyle("Tossed").assertIsSelected()
        listOf("Off", "In place").forEach { rollStyle(it).assertIsNotSelected() }
    }

    @Test
    fun `changing a group leaves no level picked - and picking a level puts every group back to its preset`() = uiTest(SCREEN) {
        show()
        openAdvanced()
        rate(MotionGroup.MAT, "30 frames a second").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(AnimationLevel.HIGH.motion.with(MotionGroup.MAT, FrameRate.FPS_30), repository.motionSettings.first())
        rate(MotionGroup.MAT, "30 frames a second").assertIsSelected()
        // No level is picked, and nothing names it.
        listOf("Off", "Low", "Medium", "High").forEach { level(it).assertIsNotSelected() }
        compose.onNodeWithText("Custom").assertDoesNotExist()

        level("Medium").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(AnimationLevel.MEDIUM.motion, repository.motionSettings.first())
        level("Medium").assertIsSelected()
        rate(MotionGroup.MAT, "Off").assertIsSelected()
        rate(MotionGroup.BANNERS, "60 frames a second").assertIsSelected()
    }

    @Test
    fun `the dice's frame rate has no off - their roll's own is - and is locked with its pick kept while the roll is off`() = uiTest(SCREEN) {
        show()
        openAdvanced()
        // Off first, as every row runs.
        rollStyle("Tossed").performScrollTo()
        val left = { name: String -> rollStyle(name).fetchSemanticsNode().boundsInRoot.left }
        assertTrue(left("Off") < left("In place") && left("In place") < left("Tossed"), "Off, In place, Tossed")
        // The Offs: the level's, every rate row's but the dice's, and the dice's roll's.
        assertEquals(1 + offRows.size, compose.onAllNodes(hasContentDescription("Off") and isSelectable()).fetchSemanticsNodes().size)
        rollStyle("In place").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(DiceRollStyle.IN_PLACE, repository.motionSettings.first().rollStyle)
        rate(MotionGroup.DICE_ROLL, "Maximum").assertIsEnabled()

        rollStyle("Off").performClick()
        compose.waitForIdle()
        assertEquals(false, repository.motionSettings.first().rollMotion)
        rate(MotionGroup.DICE_ROLL, "Maximum").assertIsNotEnabled().assertIsSelected()
        rate(MotionGroup.DICE_ROLL, "30 frames a second").assertIsNotEnabled()
        // The cup's shake is its own.
        assertEquals(true, repository.motionSettings.first().cupShakeMotion)
        rate(MotionGroup.CUP_SHAKE, "Off").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(false, repository.motionSettings.first().cupShakeMotion)
    }

    @Test
    fun `picking an orientation saves it`() = uiTest(SCREEN) {
        show()
        compose.onNode(hasText("Landscape") and isSelectable()).performClick()
        compose.waitForIdle()
        assertEquals(ScreenOrientation.LANDSCAPE, repository.screenOrientation.first())
        compose.onNode(hasText("Landscape") and isSelectable()).assertIsSelected()
    }
}
