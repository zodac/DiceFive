package net.zodac.dicefive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.settings.ComponentKind
import net.zodac.dicefive.ui.settings.LicenceLabels
import net.zodac.dicefive.ui.settings.LicenseGroup
import net.zodac.dicefive.ui.settings.LicenseReport
import net.zodac.dicefive.ui.settings.LicensedComponent
import net.zodac.dicefive.ui.settings.licenceLabels
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The words of a licence list, which Android's list of views gets through [LicenceLabels]: the "Used by" line and the copy menu's text. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LicenceLabelsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun group(name: String, vararg kinds: ComponentKind) =
        LicenseGroup(name, "text", kinds.mapIndexed { i, kind -> LicensedComponent("c$i", null, kind, website = null, copyright = null) })

    private fun labelsFor(report: LicenseReport): LicenceLabels {
        var labels: LicenceLabels? by mutableStateOf(null)
        compose.setContent { labels = licenceLabels(report) }
        compose.waitForIdle()
        return labels!!
    }

    private val report = LicenseReport(
        groups = listOf(
            group("Many", *Array(84) { ComponentKind.LIBRARY }),
            group("One", ComponentKind.LIBRARY),
            group("Two kinds", ComponentKind.LIBRARY, ComponentKind.FONT),
            group("Sounds", ComponentKind.SOUND, ComponentKind.SOUND),
            group("Mixed", ComponentKind.LIBRARY, ComponentKind.LIBRARY, ComponentKind.FONT, ComponentKind.SOUND, ComponentKind.IMAGE),
        ),
        notices = emptyList(),
    )

    private fun assertUsageLines(labels: LicenceLabels) {
        assertEquals(
            listOf(
                "Used by 84 libraries",
                "Used by 1 library",
                "Used by 1 library and 1 font",
                "Used by 2 sounds",
                "Used by 2 libraries, 1 font, 1 sound and 1 image",
            ),
            report.groups.map { labels.usageOf(it) },
        )
    }

    @Test
    fun `a licence says what it is used by - each kind counted in the singular or plural it needs`() {
        assertUsageLines(labelsFor(report))
    }

    // Like the ordinals, the list follows the strings' language: no French yet, so no "et".
    @Test
    @Config(qualifiers = "fr")
    fun `on a phone in a language the app isn't in the usage line stays English`() {
        assertUsageLines(labelsFor(report))
    }

    @Test
    fun `the copy menu and its confirmations have their words`() {
        val labels = labelsFor(report)

        assertEquals(listOf("Copy link", "Copy text", "Link", "Text", "Link copied", "Text copied"), listOf(labels.copyLink, labels.copyText, labels.linkClip, labels.textClip, labels.linkCopied, labels.textCopied))
        assertEquals(listOf("Show licence text", "Hide licence text", "Notices"), listOf(labels.showText, labels.hideText, labels.noticesTitle))
    }
}
