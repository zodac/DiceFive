package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.licences_close_cd
import net.zodac.dicefive.resources.licences_intro
import net.zodac.dicefive.resources.licences_title
import net.zodac.dicefive.ui.common.CONTENT_MAX_WIDTH
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.VerticalScrollbar
import org.jetbrains.compose.resources.stringResource

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
     *
     * i18n: not translated, by decision (.claude/I18N.md, Step 3). The Android licence list is built from
     * Android views, which can't reach the string resources, and this one rarely-seen line isn't worth
     * a new platform interface. A language that wants it needs that decision revisited.
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

/** The dialog's side and bottom padding, whose right-hand side the scrollbar sits in. */
private val DIALOG_MARGIN = 20.dp

/**
 * How far the licence document is scrolled, in pixels, for the dialog's [VerticalScrollbar]: the
 * document keeps [position] and [maxPosition] up to date (both snapshot state, so the bar follows
 * them), and sets [scrollBy] to scroll itself by a pixel delta, for a drag on the bar.
 */
class LicenceScroll {
    var position by mutableIntStateOf(0)
    var maxPosition by mutableIntStateOf(0)
    var scrollBy: (Float) -> Unit = {}
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
                        .padding(top = 4.dp, bottom = DIALOG_MARGIN, start = DIALOG_MARGIN, end = DIALOG_MARGIN),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(Res.string.licences_close_cd))
                        }
                    }
                    Text(
                        text = stringResource(Res.string.licences_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(Res.string.licences_intro),
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
                                width = DIALOG_MARGIN,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = DIALOG_MARGIN),
                            )
                        }
                    }
                }
            }
        }
    }
}
