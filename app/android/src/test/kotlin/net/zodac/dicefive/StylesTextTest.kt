package net.zodac.dicefive

import net.zodac.dicefive.data.settings.SettingsRepository
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DiceMats
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.ScoreFrames
import net.zodac.dicefive.ui.style.TableBackgrounds
import net.zodac.dicefive.ui.style.UnlockCount
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isHeading
import org.junit.Assert.assertTrue
import net.zodac.dicefive.ui.styles.StylesScreen
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.performClick
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Styles screen's words: its title and categories, and how a style - locked, or in several colours - is named and what a tap is called. */
@RunWith(AndroidJUnit4::class)
class StylesTextTest {

    @get:Rule
    val compose = createComposeRule()

    private val showcase = Showcase(compose)

    // Unlocked only is stored by the settings, so each test starts from a new repository with it off.
    private val settings = SettingsRepository(InMemoryPreferences())

    private fun show(achievements: AchievementsState) {
        val saved = MutableStateFlow<SavedStyles?>(
            SavedStyles(DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, achievements),
        )
        val viewModel = stylesViewModel(saved, settings)
        showcase.show { DiceFiveTheme { StylesScreen(viewModel = viewModel, onBack = {}) } }
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
    }

    @Test
    fun `the page is titled Styles with a card and gallery switch a category - a locked style and one in several colours named and their taps`() {
        show(AchievementsState())
        compose.onNodeWithText("Styles").assertExists()
        listOf("Dice", "Dice Cup", "Mat", "Background", "Frame").forEach { compose.onNodeWithContentDescription("$it gallery").assertExists() }
        // A locked style says Locked - and its tap is called Show how to unlock.
        val frosted = compose.onAllNodesWithContentDescription("Frosted").onFirst().fetchSemanticsNode().config
        assertEquals("Locked", frosted[SemanticsProperties.StateDescription])
        assertEquals("Show how to unlock Frosted", frosted[SemanticsActions.OnClick].label)

        // A style in several colours names the colour showing - and a long press is called Choose colour.
        show(AchievementsState(unlockedAt = Achievement.entries.associateWith { 0L }))
        val metal = compose.onNode(hasContentDescription("Metal, Gold")).fetchSemanticsNode().config
        assertEquals("Select", metal[SemanticsActions.OnClick].label)
        assertEquals("Choose Metal colour", metal[SemanticsActions.OnLongClick].label)
    }

    @Test
    fun `a locked style tile shows the unlock requirement dialog with resolved style name`() {
        show(AchievementsState())
        val metal = compose.onAllNodesWithContentDescription("Metal").onFirst()
        metal.performScrollTo()
        metal.performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Earn 20 achievements to unlock Metal. You've earned 0 so far.").assertExists()
    }

    @Test
    fun `the page counts every colour unlocked - and each category's heading says its own count - drawn as a fraction TalkBack doesn't read`() {
        val achievements = AchievementsState(unlockedAt = mapOf(Achievement.NAT_20 to 0L))
        show(achievements)
        val counts = listOf(
            "Dice" to DiceStyles, "Dice Cup" to DiceCupStyles, "Mat" to DiceMats, "Background" to TableBackgrounds, "Frame" to ScoreFrames,
        ).map { (title, catalog) -> title to catalog.unlockCount(achievements) }
        val total = counts.map { it.second }.reduce(UnlockCount::plus)
        compose.onNodeWithText("${total.unlocked} of ${total.total} unlocked").assertExists()

        val headings = compose.onAllNodes(isHeading()).fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.single() }
        for ((title, count) in counts) {
            assertTrue("$title: $headings", "$title, ${count.unlocked} of ${count.total} unlocked" in headings)
            compose.onNodeWithText("${count.unlocked}/${count.total}").assertDoesNotExist()
        }
    }

    @Test
    fun `Unlocked only is one switch - on it hides every locked style but keeps the unlocked ones - and stays on when the page is opened again`() {
        show(AchievementsState())
        val switch = compose.onNodeWithText("Unlocked only").fetchSemanticsNode().config
        assertEquals(Role.Switch, switch[SemanticsProperties.Role])
        assertEquals(ToggleableState.Off, switch[SemanticsProperties.ToggleableState])
        compose.onAllNodesWithContentDescription("Frosted").onFirst().assertExists()

        compose.onNodeWithText("Unlocked only").performClick()
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertEquals(ToggleableState.On, compose.onNodeWithText("Unlocked only").fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
        assertEquals(0, compose.onAllNodesWithContentDescription("Frosted").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasStateDescription("Locked")).fetchSemanticsNodes().size)
        compose.onAllNodesWithContentDescription("Classic", substring = true).onFirst().assertExists()

        show(AchievementsState())
        assertEquals(ToggleableState.On, compose.onNodeWithText("Unlocked only").fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
        assertEquals(0, compose.onAllNodesWithContentDescription("Frosted").fetchSemanticsNodes().size)
    }
}
