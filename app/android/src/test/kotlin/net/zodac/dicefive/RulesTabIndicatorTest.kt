package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.rules.RulesScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Rules tab row's sliding indicator sits under the selected tab in either layout direction. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class RulesTabIndicatorTest {

    @get:Rule
    val compose = createComposeRule()

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun checkIndicatorUnderEachTab() {
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) { DiceFiveTheme { RulesScreen(onBack = {}) } }
        }
        for (index in 0..2) {
            compose.onAllNodes(isTab)[index].performClick()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitForIdle()
            val tab = compose.onAllNodes(isTab)[index].fetchSemanticsNode().boundsInRoot
            val indicator = compose.onNodeWithTag("rulesTabIndicator").fetchSemanticsNode().boundsInRoot
            assertEquals("indicator $indicator vs tab $index $tab", tab.center.x, indicator.center.x, 2f)
        }
    }

    @Test
    fun `the indicator is under the selected tab left to right`() = checkIndicatorUnderEachTab()

    @Test
    @Config(qualifiers = "ar-w360dp-h800dp")
    fun `the indicator is under the selected tab right to left`() = checkIndicatorUnderEachTab()
}
