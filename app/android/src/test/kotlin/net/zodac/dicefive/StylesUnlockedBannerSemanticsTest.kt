package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.data.achievements.UnlockedStyle
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.TableBackgrounds
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "style unlocked" banner comes and goes by itself, so TalkBack has to be told it's there: one
 * polite live region naming every style it unlocked, with the tap that pauses it as an action.
 */
@RunWith(AndroidJUnit4::class)
class StylesUnlockedBannerSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private val showcase = Showcase(compose)

    @Test
    fun `a styles banner is one polite live region with a pause action - naming one style - two - or two and how many more`() {
        // Three styles: one polite live region naming every style, with the tap that pauses it as an action.
        showBannerHostAndEmit(listOf(DiceStyles.unlockedStyle("Frosted"), DiceMats.unlockedStyle("Leather"), TableBackgrounds.unlockedStyle("Planks")), 30)
        compose.onNode(hasContentDescription("Styles unlocked: Frosted dice, Leather mat, Planks background. Earned 30 achievements"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(SemanticsMatcher("pauses on a tap") { it.config[SemanticsActions.OnClick].label == "Pause countdown" })
        // It names two and counts the rest.
        compose.onNodeWithText("Earned 30 achievements: 'Frosted' dice, 'Leather' mat and 1 more", useUnmergedTree = true).assertExists()

        // One style: Style Unlocked, naming it - and TalkBack hears it.
        showBannerHostAndEmit(listOf(DiceStyles.unlockedStyle("Frosted")), 23)
        compose.onNodeWithText("Style Unlocked", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Earned 23 achievements: the 'Frosted' dice style", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Style unlocked: Frosted dice. Earned 23 achievements")).assertExists()

        // Two styles: both named.
        showBannerHostAndEmit(listOf(DiceStyles.unlockedStyle("Frosted"), DiceMats.unlockedStyle("Leather")), 25)
        compose.onNodeWithText("Styles Unlocked", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Earned 25 achievements: 'Frosted' dice and 'Leather' mat", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Styles unlocked: Frosted dice, Leather mat. Earned 25 achievements")).assertExists()
    }

    /** A fresh banner host - one showing nothing yet - then the banner for [styles]. */
    private fun showBannerHostAndEmit(styles: List<UnlockedStyle>, achievementCount: Int) {
        showcase.show {
            CompositionLocalProvider(LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext())) {
                DiceFiveTheme {
                    AchievementBannerHost(isOnGameScreen = { false }, onAchievementSelected = {}, onStylesSelected = {}) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        AchievementEvents.emit(AchievementEvent.StylesUnlocked(styles, achievementCount))
        compose.waitForIdle()
    }
}
