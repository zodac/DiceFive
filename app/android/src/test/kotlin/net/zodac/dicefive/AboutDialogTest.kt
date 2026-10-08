package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.settings.AboutDialog
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings > "About": every title is a heading TalkBack can jump between, and each link is a button
 * that opens its page - the GitHub repo, the app that inspired DiceFive, and the privacy policy.
 */
@RunWith(AndroidJUnit4::class)
class AboutDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun showDialog() {
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                DiceFiveTheme { AboutDialog(onDismissRequest = {}) }
            }
        }
    }

    @Test
    fun `every title is a heading - each link opens its page - and the privacy section says no user data is held`() {
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
}
