package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mikepenz.aboutlibraries.Libs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zodac.dicefive.data.JsonObject
import net.zodac.dicefive.data.JsonParseException
import net.zodac.dicefive.data.JsonString
import net.zodac.dicefive.data.parseJson
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.ui.common.CONTENT_MAX_WIDTH
import net.zodac.dicefive.ui.common.SoraFontFamily

/**
 * What a licensed item is, so a licence's heading can say "Used by 4 sounds" rather than calling a
 * recording a library. Everything the plugin discovers from Gradle is a [LIBRARY]; a hand-written
 * app/licensing/libraries/ entry for an asset says which kind it is in its `tag` field.
 */
enum class ComponentKind(val tag: String?, val singular: String, val plural: String) {
    LIBRARY(tag = null, singular = "library", plural = "libraries"),
    FONT(tag = "font", singular = "font", plural = "fonts"),
    SOUND(tag = "sound", singular = "sound", plural = "sounds"),
    IMAGE(tag = "image", singular = "image", plural = "images"),
    ;

    companion object {
        fun fromTag(tag: String?): ComponentKind = entries.firstOrNull { it.tag == tag } ?: LIBRARY
    }
}

/** One licensed item as the dialog shows it. [copyright] is only set where the licence requires the
 * notice (or a credit) itself to be reproduced - see app/licensing/README.md. */
data class LicensedComponent(
    val name: String,
    val version: String?,
    val kind: ComponentKind,
    val website: String?,
    val copyright: String?,
)

/** One licence, its full text shown once, and every item the app ships under it. */
data class LicenseGroup(val name: String, val text: String, val components: List<LicensedComponent>) {
    /**
     * "Used by 84 libraries", "Used by 1 library and 1 font", "Used by 2 libraries, 1 font and
     * 1 sound" - one licence can cover any mix of kinds; each is counted, most common first.
     */
    val usage: String
        get() {
            val counts = components.groupingBy { it.kind }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<ComponentKind, Int>> { it.value }.thenBy { it.key.ordinal })
                .map { (kind, count) -> "$count ${if (count == 1) kind.singular else kind.plural}" }
            val list = if (counts.size <= 1) counts.joinToString() else counts.dropLast(1).joinToString() + " and " + counts.last()
            return "Used by $list"
        }
}

/** A dependency's Apache-2.0 NOTICE file, which must be passed on alongside the licence itself. */
data class ThirdPartyNotice(val library: String, val text: String)

data class LicenseReport(val groups: List<LicenseGroup>, val notices: List<ThirdPartyNotice>)

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
 * Same raised reading surface as RulesDialog, for the same reason: this is a lot of text to read,
 * not a question to answer. The list itself is the platform's [PlatformServices.LicenceDocument] -
 * one selectable text, with tappable links (on Android, the system's own Copy / Share toolbar too) -
 * and a tap anywhere drops a selection.
 */
@Composable
fun LicensesDialog(onDismissRequest: () -> Unit, modifier: Modifier = Modifier) {
    val platform = LocalPlatformServices.current
    val selectionClearer = remember { platform.createSelectionClearer() }
    // ~50KB of JSON - small, but no reason to parse it on the main thread while the dialog animates in.
    val report by produceState<LicenseReport?>(initialValue = null) {
        val reports = platform.loadLicenceReports()
        value = withContext(Dispatchers.Default) { parseLicenseReport(reports.librariesJson, reports.noticesJson) }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f)
                .widthIn(max = CONTENT_MAX_WIDTH),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            CompositionLocalProvider(LocalSelectionClearer provides selectionClearer) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clearSelectionsOnTap(selectionClearer)
                        .padding(top = 4.dp, bottom = 20.dp, start = 20.dp, end = 20.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                    Text(
                        text = "Licences",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "DiceFive is built with the open-source software, fonts and sounds below, each used " +
                            "under the licence it's listed with.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )

                    val loaded = report
                    if (loaded == null) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        platform.LicenceDocument(report = loaded, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}
