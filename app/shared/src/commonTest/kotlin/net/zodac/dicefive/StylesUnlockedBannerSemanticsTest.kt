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
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import net.zodac.dicefive.app.AchievementEvent
import net.zodac.dicefive.app.AchievementEvents
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.app.StylesEarnedBy
import net.zodac.dicefive.app.UnlockedStyle
import net.zodac.dicefive.game.style.styleRewards
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.style.DiceMats
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.TableBackgrounds
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * The "style unlocked" banner comes and goes by itself, so TalkBack has to be told it's there: one
 * polite live region naming every style it unlocked, with the tap that pauses it as an action.
 */
class StylesUnlockedBannerSemanticsTest : UiTest() {

    private val showcase = Showcase { compose }

    @Test
    fun `a styles banner is one polite live region with a pause action - naming one style - two - or two and how many more`() = uiTest {
        // Three styles: one polite live region naming every style, with the tap that pauses it as an action.
        showBannerHostAndEmit(listOf(DiceStyles.unlockedStyle("Frosted"), DiceMats.unlockedStyle("Leather"), TableBackgrounds.unlockedStyle("Planks")), StylesEarnedBy.Count(30))
        compose.onNode(hasContentDescription("Styles unlocked: Frosted dice, Leather mat, Planks background. Earned 30 achievements"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(SemanticsMatcher("pauses on a tap") { it.config[SemanticsActions.OnClick].label == "Pause countdown" })
        // It names two and counts the rest.
        compose.onNodeWithText("Earned 30 achievements: 'Frosted' dice, 'Leather' mat and 1 more", useUnmergedTree = true).assertExists()

        // One style: Style Unlocked, naming it - and TalkBack hears it.
        showBannerHostAndEmit(listOf(DiceStyles.unlockedStyle("Frosted")), StylesEarnedBy.Count(23))
        compose.onNodeWithText("Style Unlocked", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Earned 23 achievements: the 'Frosted' dice style", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Style unlocked: Frosted dice. Earned 23 achievements")).assertExists()

        // Two styles: both named.
        showBannerHostAndEmit(listOf(DiceStyles.unlockedStyle("Frosted"), DiceMats.unlockedStyle("Leather")), StylesEarnedBy.Count(25))
        compose.onNodeWithText("Styles Unlocked", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Earned 25 achievements: 'Frosted' dice and 'Leather' mat", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Styles unlocked: Frosted dice, Leather mat. Earned 25 achievements")).assertExists()
    }

    @Test
    fun `a style behind one achievement names the achievement - and a hidden colour says so`() = uiTest {
        showBannerHostAndEmit(Achievement.BIG_FAN.stylesUnlocked(), StylesEarnedBy.OneAchievement(Achievement.BIG_FAN))
        compose.onNodeWithText("Styles Unlocked", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Earned 'Big Fan': 'Glitch' dice and background", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Styles unlocked: Glitch dice, Glitch background. Earned Big Fan")).assertExists()

        showBannerHostAndEmit(Achievement.ROLLING_THE_MIDNIGHT_DICE.stylesUnlocked(), StylesEarnedBy.OneAchievement(Achievement.ROLLING_THE_MIDNIGHT_DICE))
        compose.onNodeWithText("Earned 'Rolling The Midnight Dice': 'Starry' dice, mat and background", useUnmergedTree = true).assertExists()

        showBannerHostAndEmit(Achievement.LUCK_OF_THE_IRISH.stylesUnlocked(), StylesEarnedBy.OneAchievement(Achievement.LUCK_OF_THE_IRISH))
        compose.onNodeWithText("Style Unlocked", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Earned 'Luck of the Irish': the hidden 'Multicolour' dice colour", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Style unlocked: Multicolour dice. Earned Luck of the Irish")).assertExists()
    }

    private fun Achievement.stylesUnlocked(): List<UnlockedStyle> =
        styleRewards.map { UnlockedStyle(it.styleName, it.categoryNoun, it.hiddenColours) }

    /** A fresh banner host - one showing nothing yet - then the banner for [styles]. */
    private fun showBannerHostAndEmit(styles: List<UnlockedStyle>, earnedBy: StylesEarnedBy) {
        showcase.show {
            CompositionLocalProvider(LocalAppContainer provides inMemoryAppContainer()) {
                DiceFiveTheme {
                    AchievementBannerHost(isOnGameScreen = { false }, onAchievementSelected = {}, onStylesSelected = {}) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        AchievementEvents.emit(AchievementEvent.StylesUnlocked(styles, earnedBy))
        compose.waitForIdle()
    }
}
