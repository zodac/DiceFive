package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.online.LocalGameServices
import net.zodac.dicefive.online.NoGameServices
import net.zodac.dicefive.ui.settings.AboutDialog
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * Settings > "About": every title is a heading TalkBack can jump between, and each link is a button
 * that opens its page - the GitHub repo, the app that inspired DiceFive, and the privacy policy.
 */
class AboutDialogTest : UiTest() {

    private val opened = mutableListOf<String>()

    private fun showDialog(services: GameServices = NoGameServices) {
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler, LocalGameServices provides services) {
                DiceFiveTheme { AboutDialog(onDismissRequest = {}) }
            }
        }
    }

    @Test
    fun `every title is a heading - each link opens its page - and the privacy section says no user data is held`() = uiTest {
        showDialog()
        val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        for (title in listOf("About", "Author", "Inspiration", "Privacy")) {
            compose.onNodeWithText(title).performScrollTo().assert(isHeading)
        }

        val links = listOf(
            "Source code on GitHub" to "https://github.com/zodac/DiceFive",
            "Dice Me Online on Google Play" to "https://play.google.com/store/apps/details?id=com.giu.diceme",
            "Full privacy policy" to "https://zodac.github.io/DiceFive/privacy-policy",
        )
        for ((label, url) in links) {
            compose.onNodeWithText(label).performScrollTo().assert(hasClickAction()).performClick()
            assertEquals(url, opened.last())
        }

        compose.onNodeWithText("DiceFive holds no user data", substring = true).performScrollTo()
    }

    @Test
    fun `without an online service the privacy section says everything stays on this device`() = uiTest {
        showDialog()
        compose.onNodeWithText(
            "DiceFive holds no user data. Your games, scores, and settings stay on this device, and there are no ads, " +
                "analytics, or tracking.",
        ).performScrollTo()
    }

    @Test
    fun `with an online service the privacy section says the data leaves the device only once signed in`() = uiTest {
        // Signed out still says it: the sentence is about what signing in would do.
        showDialog(OnlineServices(GameServicesState.SignedOut()))
        compose.onNodeWithText(
            "DiceFive holds no user data. Your games, scores, and settings stay on this device, unless you sign in to " +
                "Google Play Games. There are no ads, analytics, or tracking.",
        ).performScrollTo()
    }
}

private class OnlineServices(state: GameServicesState) : GameServices by NoGameServices {
    override val serviceName = "Google Play Games"
    override val state: StateFlow<GameServicesState> = MutableStateFlow(state)
}
