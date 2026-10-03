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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.data.achievements.UnlockedStyle
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.ui.achievements.AchievementBannerHost
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The "style unlocked" banner comes and goes by itself, so TalkBack has to be told it's there: one
 * polite live region naming every style it unlocked, with the tap that pauses it as an action.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StylesUnlockedBannerSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a styles banner is one polite live region naming every style, with a pause action`() {
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides AndroidAppContainer.get(ApplicationProvider.getApplicationContext())) {
                DiceFiveTheme {
                    AchievementBannerHost(
                        isOnGameScreen = { false },
                        onAchievementSelected = {},
                        onStylesSelected = {},
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        compose.waitForIdle()
        AchievementEvents.emit(
            AchievementEvent.StylesUnlocked(
                listOf(UnlockedStyle("Frosted", "dice"), UnlockedStyle("Velvet", "mat"), UnlockedStyle("Oak", "background")),
                30,
            ),
        )
        compose.waitForIdle()

        val announcement = "Styles unlocked: Frosted dice, Velvet mat, Oak background. Earned 30 achievements"
        compose.onNode(hasContentDescription(announcement))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(SemanticsMatcher("pauses on a tap") { it.config[SemanticsActions.OnClick].label == "Pause countdown" })
    }
}
