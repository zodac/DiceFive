package net.zodac.dicefive

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
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
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.TableBackgrounds
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.styles.StylesViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import androidx.compose.ui.test.longClick
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

    private fun show(achievements: AchievementsState) {
        val saved = MutableStateFlow<SavedStyles?>(
            SavedStyles(DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, achievements),
        )
        val viewModel = StylesViewModel(savedStyles = saved)
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
        val frosted = compose.onAllNodesWithContentDescription("Frosted").onFirst()
        frosted.performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Earn 23 achievements to unlock Frosted. You've earned 0 so far.").assertExists()
    }
}
