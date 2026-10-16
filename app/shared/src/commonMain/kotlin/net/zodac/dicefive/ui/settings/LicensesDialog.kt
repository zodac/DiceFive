package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zodac.dicefive.data.JsonObject
import net.zodac.dicefive.data.JsonParseException
import net.zodac.dicefive.data.JsonString
import net.zodac.dicefive.data.parseJson
import net.zodac.dicefive.platform.ComponentKind
import net.zodac.dicefive.platform.LicenceScroll
import net.zodac.dicefive.platform.LicenseGroup
import net.zodac.dicefive.platform.LicenseReport
import net.zodac.dicefive.platform.LicensedComponent
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.LocalSelectionClearer
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.platform.ThirdPartyNotice
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.app_name
import net.zodac.dicefive.resources.licences_close_cd
import net.zodac.dicefive.resources.licences_copy_link
import net.zodac.dicefive.resources.licences_copy_text
import net.zodac.dicefive.resources.licences_hide_text
import net.zodac.dicefive.resources.licences_intro
import net.zodac.dicefive.resources.licences_link
import net.zodac.dicefive.resources.licences_link_copied
import net.zodac.dicefive.resources.licences_notices_subtitle
import net.zodac.dicefive.resources.licences_notices_title
import net.zodac.dicefive.resources.licences_show_text
import net.zodac.dicefive.resources.licences_text
import net.zodac.dicefive.resources.licences_text_copied
import net.zodac.dicefive.resources.licences_title
import net.zodac.dicefive.resources.licences_used_by
import net.zodac.dicefive.ui.common.LARGE_DIALOG_MARGIN
import net.zodac.dicefive.ui.common.LargeDialog
import net.zodac.dicefive.ui.common.LargeDialogTitle
import net.zodac.dicefive.ui.common.VerticalScrollbar
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.spokenList
import net.zodac.dicefive.ui.common.stringResource

/**
 * Every word a licence list shows besides the licences themselves, resolved from the shared string
 * resources. The Android list is built from views, which can't reach those resources, so it gets its
 * words through this instead - one set of strings for both lists, and for a translation.
 * [usageOf] is each licence's "Used by 2 libraries" line.
 */
data class LicenceLabels(
    val showText: String,
    val hideText: String,
    val noticesTitle: String,
    val noticesSubtitle: String,
    val copyLink: String,
    val copyText: String,
    /** What the clipboard calls a copied link, and a copied piece of text. */
    val linkClip: String,
    val textClip: String,
    /** The confirmation on Android 12 and older, which don't show their own. */
    val linkCopied: String,
    val textCopied: String,
    private val usage: Map<String, String>,
) {
    fun usageOf(group: LicenseGroup): String = usage.getValue(group.name)
}

/** The words for a licence list showing [report]. */
@Composable
fun licenceLabels(report: LicenseReport): LicenceLabels {
    val usage = report.groups.associate { group ->
        group.name to stringResource(
            Res.string.licences_used_by,
            spokenList(group.kindCounts.map { (kind, count) -> pluralStringResource(kind.count, count, count) }),
        )
    }
    return LicenceLabels(
        showText = stringResource(Res.string.licences_show_text),
        hideText = stringResource(Res.string.licences_hide_text),
        noticesTitle = stringResource(Res.string.licences_notices_title),
        noticesSubtitle = stringResource(Res.string.licences_notices_subtitle),
        copyLink = stringResource(Res.string.licences_copy_link),
        copyText = stringResource(Res.string.licences_copy_text),
        linkClip = stringResource(Res.string.licences_link),
        textClip = stringResource(Res.string.licences_text),
        linkCopied = stringResource(Res.string.licences_link_copied),
        textCopied = stringResource(Res.string.licences_text_copied),
        usage = usage,
    )
}

/**
 * Turns the two build-generated reports - the AboutLibraries plugin's aboutlibraries.json and
 * CollectThirdPartyNoticesTask's third_party_notices.json (both in app/android/build.gradle.kts, and read
 * through [PlatformServices.loadLicenceReports]) - into what
 * the dialog shows. Grouped by licence, most-used first, rather than one row per item each
 * repeating its licence: ninety-odd libraries share Apache-2.0, and its text only needs to be there
 * once.
 */
internal fun parseLicenseReport(librariesJson: String, noticesJson: String): LicenseReport {
    val libs = Libs.Builder().withJson(librariesJson).build()
    val groups = libs.licenses
        .map { license ->
            LicenseGroup(
                name = license.name,
                text = license.licenseContent.orEmpty().trim(),
                components = libs.libraries
                    .filter { library -> library.licenses.any { it.hash == license.hash } }
                    .map { library ->
                        LicensedComponent(
                            name = library.name,
                            version = library.artifactVersion,
                            kind = ComponentKind.fromTag(library.tag),
                            website = library.website?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                            copyright = library.description?.takeIf { it.startsWith("Copyright") },
                        )
                    }
                    .sortedBy { it.name.lowercase() },
            )
        }
        .filter { it.components.isNotEmpty() }
        .sortedWith(compareByDescending<LicenseGroup> { it.components.size }.thenBy { it.name })

    val noticesObject = parseJson(noticesJson) as? JsonObject ?: throw JsonParseException("Notices aren't a JSON object")
    val notices = noticesObject.fields
        .map { (library, text) -> ThirdPartyNotice(library = library, text = (text as? JsonString)?.value.orEmpty()) }
        .sortedBy { it.library }

    return LicenseReport(groups, notices)
}


/**
 * Settings > "Licences": every licence the app's third-party code, fonts, sounds and other assets
 * are shipped under, with its full text, and the items under each. Nothing here is written by hand
 * - see the "Open-source licenses" section of app/android/build.gradle.kts for where the list comes from and
 * how the build keeps copyleft code out of it.
 *
 * A raised reading surface rather than a DiceFiveDialog, since this is a lot of text to read, not a
 * question to answer. The list itself is the platform's [PlatformServices.LicenceDocument] -
 * one selectable text, with tappable links (on Android, the system's own Copy / Share toolbar too) -
 * and a tap anywhere drops a selection.
 */
@Composable
fun LicensesDialog(onDismissRequest: () -> Unit, modifier: Modifier = Modifier) {
    val platform = LocalPlatformServices.current
    val selectionClearer = remember { platform.createSelectionClearer() }
    // ~50KB of JSON - small, but no reason to parse it on the main thread while the dialog animates in.
    val scroll = remember { LicenceScroll() }
    val report by produceState<LicenseReport?>(initialValue = null) {
        val reports = platform.loadLicenceReports()
        value = withContext(Dispatchers.Default) { parseLicenseReport(reports.librariesJson, reports.noticesJson) }
    }

    CompositionLocalProvider(LocalSelectionClearer provides selectionClearer) {
        LargeDialog(
            onDismissRequest = onDismissRequest,
            closeContentDescription = stringResource(Res.string.licences_close_cd),
            modifier = modifier,
            contentModifier = Modifier.clearSelectionsOnTap(selectionClearer),
        ) {
            LargeDialogTitle(stringResource(Res.string.licences_title))
            Text(
                text = stringResource(Res.string.licences_intro, stringResource(Res.string.app_name)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )

            val loaded = report
            if (loaded == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    platform.LicenceDocument(report = loaded, scroll = scroll, modifier = Modifier.fillMaxSize())
                    // In the dialog's right-hand margin, beside the cards rather than over them -
                    // the same bar, placed the same way, as the Styles and Rules pages'.
                    VerticalScrollbar(
                        position = { scroll.position },
                        maxPosition = { scroll.maxPosition },
                        scrollBy = { scroll.scrollBy(it) },
                        width = LARGE_DIALOG_MARGIN,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = LARGE_DIALOG_MARGIN),
                    )
                }
            }
        }
    }
}
