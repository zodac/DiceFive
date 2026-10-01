package net.zodac.dicefive

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** How a Styles row behaves once it's open. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StylesRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val tiles = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun `picking a style keeps the row's other tiles built`() {
        // Everything earned, so the row has more than one style to pick between.
        val achievements = AchievementsState(unlockedAt = Achievement.entries.associateWith { 0L })
        val unlockedDice = DiceStyles.families.filter { it.unlock.isMet(achievements) }
        assertTrue("needs two unlocked dice styles to pick between", unlockedDice.size >= 2)
        val saved = MutableStateFlow<SavedStyles?>(
            SavedStyles(unlockedDice[0].colours.first().style.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, achievements),
        )
        val viewModel = StylesViewModel(savedStyles = saved)
        compose.setContent { DiceFiveTheme { StylesScreen(viewModel = viewModel, onBack = {}) } }
        // Long enough for every row to build every tile.
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        val built = compose.onAllNodes(tiles).fetchSemanticsNodes().size

        // Frame by frame from here, so a row that dropped its tiles and built them again would be caught partway.
        compose.mainClock.autoAdvance = false
        val newPick = unlockedDice[1].colours.first().style.id
        saved.value = saved.value!!.copy(diceStyleId = newPick)
        compose.mainClock.advanceTimeByFrame()

        val after = compose.onAllNodes(tiles).fetchSemanticsNodes()
        assertEquals(built, after.size)
        val selected = after.filter { it.config.getOrNull(SemanticsProperties.Selected) == true }
            .map { it.config[SemanticsProperties.ContentDescription].single() }
        assertTrue(selected.toString(), selected.any { it.startsWith(unlockedDice[1].name) })
    }
}
