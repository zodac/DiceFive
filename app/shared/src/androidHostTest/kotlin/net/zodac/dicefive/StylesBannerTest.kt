package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.app.UnlockedStyle
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.game.style.StyleAccess
import net.zodac.dicefive.game.style.StyleCategory
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.navigation.StyleScrollRequest
import net.zodac.dicefive.navigation.StyleScrollRequests
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DiceMats
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.TableBackgrounds
import net.zodac.dicefive.ui.styles.StyleCategoryScreen
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.jetbrains.compose.resources.getString

/** What a long-pressed styles banner shows on the Styles pages: the styles it named, in view and flashed. */
class StylesBannerTest : UiTest() {

    private val showcase = Showcase { compose }
    private val everything = AchievementsState(unlockedAt = Achievement.entries.associateWith { 0L })
    private val viewModel = stylesViewModel(
        MutableStateFlow<SavedStyles?>(SavedStyles(DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, everything)),
    )

    // A dice family three fifths down its page: off screen as the page opens on this short screen, and with room below it
    // to be centred.
    private val lastDice = DiceStyles.families.filter { it.unlock.isMet(StyleAccess(everything)) }.let { it[it.size * 3 / 5] }
    private val lastDiceStyle = UnlockedStyle(lastDice.name, DiceStyles.noun)
    private val aMat = UnlockedStyle(DiceMats.families[1].name, DiceMats.noun)

    private fun showCategory(category: StyleCategory) = showcase.show {
        DiceFiveTheme { CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) { StyleCategoryScreen(viewModel, category, onBack = {}) } }
    }

    /**
     * The tile of [family name] in the middle of the page within two frames: put there at once, not slid into view (which
     * takes longer, and stops with the tile at the page's edge).
     */
    private fun assertInViewAtOnce(name: String) {
        compose.mainClock.autoAdvance = false
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val tile = compose.onNode(tiles and hasContentDescription(name, substring = true)).fetchSemanticsNode().boundsInRoot
        assertTrue(tile.center.y in screen.height * 0.45f..screen.height * 0.8f, "$name at $tile on $screen")
        compose.mainClock.autoAdvance = true
    }

    private val tiles = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun `a banner's style on a category page just opened is put in view at once - and the request is used up`() = uiTest(TestScreen(360, 560)) {
        StyleScrollRequests.consumePending()
        StyleScrollRequests.request(StyleScrollRequest(listOf(lastDiceStyle), animate = false))
        showCategory(StyleCategory.DICE)
        assertInViewAtOnce(getString(lastDice.name))
        assertEquals(null, StyleScrollRequests.pending())
    }

    @Test
    fun `the Styles page takes a banner of several categories - flashing their fields - and hands it to the category opened`() = uiTest(TestScreen(360, 560)) {
        StyleScrollRequests.consumePending()
        StyleScrollRequests.request(StyleScrollRequest(listOf(lastDiceStyle, aMat), animate = false))
        val opened = mutableListOf<StyleCategory>()
        showcase.show {
            DiceFiveTheme {
                CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) { StylesScreen(viewModel, onBack = {}, onOpenCategory = { opened += it }) }
            }
        }
        compose.mainClock.autoAdvance = false
        fun gold(title: String): Float {
            val image = compose.onNode(hasContentDescription(title) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .captureToImage().toPixelMap()
            var sum = 0f
            for (x in 0 until image.width step 4) for (y in 0 until image.height step 4) image[x, y].let { sum += it.red - it.blue }
            return sum
        }
        // Flashing: the named categories' fields washed gold - warmer than another's, and than themselves once it's over.
        repeat(20) { compose.mainClock.advanceTimeByFrame() }
        val diceFlashing = gold("Dice")
        val cupMeanwhile = gold("Dice Cup")
        compose.mainClock.advanceTimeBy(3_000)
        val diceAfter = gold("Dice")
        assertTrue(diceFlashing > diceAfter, "Dice $diceFlashing flashing, $diceAfter after")
        assertTrue(diceFlashing > cupMeanwhile, "Dice $diceFlashing, Dice Cup $cupMeanwhile")
        compose.mainClock.autoAdvance = true
        assertEquals(null, StyleScrollRequests.pending())

        // Its Dice opened: the request goes with it, its style put in view.
        compose.onNode(hasContentDescription("Dice") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .let { it.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action?.invoke() }
        assertEquals(listOf(StyleCategory.DICE), opened)
        showCategory(StyleCategory.DICE)
        assertInViewAtOnce(getString(lastDice.name))
    }
}
