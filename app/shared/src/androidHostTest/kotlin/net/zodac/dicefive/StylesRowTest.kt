package net.zodac.dicefive

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DiceMats
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.StyleAccess
import net.zodac.dicefive.ui.style.TableBackgrounds
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.jetbrains.compose.resources.getString
import androidx.compose.runtime.CompositionLocalProvider
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices

/**
 * How a Styles row behaves once it's open.
 *
 * Android only: it steps the clock through seconds of the whole Styles page, which the desktop test harness draws
 * frame by frame (past its one-minute limit there, against under one here).
 */
class StylesRowTest : UiTest() {

    private val tiles = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private val showcase = Showcase { compose }

    /** The Styles page over [saved], every row built. */
    private fun open(saved: MutableStateFlow<SavedStyles?>) {
        val viewModel = stylesViewModel(saved)
        showcase.show { DiceFiveTheme { CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) { StylesScreen(viewModel = viewModel, onBack = {}) } } }
        // Long enough for every row to build every tile.
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
    }

    @Test
    fun `picking a style keeps the row's other tiles built - and picking each cup shakes and tips it through without breaking the row`() = uiTest {
        // Everything earned, so the row has more than one style to pick between.
        val achievements = AchievementsState(unlockedAt = Achievement.entries.associateWith { 0L })
        val unlockedDice = DiceStyles.families.filter { it.unlock.isMet(StyleAccess(achievements)) }
        assertTrue(unlockedDice.size >= 2, "needs two unlocked dice styles to pick between")
        val saved = MutableStateFlow<SavedStyles?>(
            SavedStyles(unlockedDice[0].colours.first().style.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, achievements),
        )
        open(saved)
        val built = compose.onAllNodes(tiles).fetchSemanticsNodes().size
        // Frame by frame from here, so a row that dropped its tiles and built them again would be caught partway.
        compose.mainClock.autoAdvance = false
        saved.value = saved.value!!.copy(diceStyleId = unlockedDice[1].colours.first().style.id)
        compose.mainClock.advanceTimeByFrame()
        val after = compose.onAllNodes(tiles).fetchSemanticsNodes()
        assertEquals(built, after.size)
        val selected = after.filter { it.config.getOrNull(SemanticsProperties.Selected) == true }.map { it.config[SemanticsProperties.ContentDescription].single() }
        assertTrue(selected.any { it.startsWith(getString(unlockedDice[1].name)) }, selected.toString())
        compose.mainClock.autoAdvance = true

        // Every cup, the chest included: its shake starts painting its hoard, which Robolectric can't - see TreasureChestCup.
        open(MutableStateFlow(SavedStyles(DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, achievements)))
        val allBuilt = compose.onAllNodes(tiles).fetchSemanticsNodes().size
        for (cup in DiceCupStyles.families.filter { it.unlock.isMet(StyleAccess(achievements)) }) {
            val name = getString(cup.name)
            val tile = compose.onAllNodes(tiles and SemanticsMatcher("starts with $name") { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.singleOrNull()?.startsWith(name) == true
            }).onFirst()
            tile.performSemanticsAction(SemanticsActions.OnClick)
            // Partway through the shake (420ms), into the tip, then past its standing back up (1.5s later).
            compose.mainClock.advanceTimeBy(200)
            compose.mainClock.advanceTimeBy(600)
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitForIdle()
        }
        assertEquals(allBuilt, compose.onAllNodes(tiles).fetchSemanticsNodes().size)
    }
}
