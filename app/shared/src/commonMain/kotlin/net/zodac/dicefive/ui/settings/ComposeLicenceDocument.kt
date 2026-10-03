package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** A [SelectionClearer] with nothing to clear - each [ComposeLicenceDocument] section keeps its own selection. */
internal object NoSelectionClearer : SelectionClearer {
    override fun clearAll() = Unit

    override fun consumeDismissedTap(): Boolean = false
}

/**
 * The licence report in plain Compose: one card per licence - its name, what uses it, a "Show
 * licence text" toggle, and every item under it - then one for the notices, with tappable links and
 * each card's text selectable on its own (a selection doesn't run on into the next card). What iOS
 * shows, and what previews show; Android draws each card as a platform TextView instead (see
 * TextViewLicenceDocument in :app:android), because Compose's selection can't span rows and its links
 * misbehave inside a SelectionContainer on Android. Whether iOS needs a native text view too is an
 * open question in .claude/IOS_SUPPORT.md.
 */
@Composable
internal fun ComposeLicenceDocument(report: LicenseReport, scroll: LicenceScroll, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val scrollState = rememberScrollState()
    // Mirrored into the dialog's LicenceScroll, for its scrollbar.
    LaunchedEffect(scrollState) {
        scroll.scrollBy = { scrollState.dispatchRawDelta(it) }
        snapshotFlow { scrollState.value to scrollState.maxValue }.collect { (position, max) ->
            scroll.position = position
            scroll.maxPosition = max
        }
    }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val links = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))

    // A plain scrolling column, not a LazyColumn: there are only a handful of cards, and a ScrollState
    // knows its exact length, which the scrollbar needs.
    Column(modifier = modifier.verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (group in report.groups) {
            LicenceCard(title = group.name, subtitle = group.usage) {
                val isExpanded = group.name in expanded
                TextButton(onClick = { expanded = if (isExpanded) expanded - group.name else expanded + group.name }) {
                    Text(text = if (isExpanded) "Hide licence text" else "Show licence text")
                }
                if (isExpanded) {
                    Text(text = linked(group.text, links), style = typography.bodySmall, color = colors.onSurfaceVariant)
                }
                for (component in group.components) {
                    Text(text = componentLine(component, links), style = typography.bodyMedium)
                    component.copyright?.let {
                        Text(text = linked(it, links), style = typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        if (report.notices.isNotEmpty()) {
            LicenceCard(title = "Notices", subtitle = "Attribution notices shipped with the libraries above") {
                for (notice in report.notices) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(text = notice.library, style = typography.bodyMedium)
                        Text(text = linked(notice.text, links), style = typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One card of the report: a heading and subtitle, then [content], all one selection. */
@Composable
private fun LicenceCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHighest),
    ) {
        SelectionContainer {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                content()
            }
        }
    }
}

/** An item's name (a link to its website, if it has one) and version. */
private fun componentLine(component: LicensedComponent, links: TextLinkStyles): AnnotatedString = buildAnnotatedString {
    append(component.name)
    component.website?.let { addLink(LinkAnnotation.Url(it, links), 0, component.name.length) }
    component.version?.let { append("  $it") }
}

/** [text] with [linkifyUrls]'s URL annotations turned into real, tappable links. */
private fun linked(text: String, links: TextLinkStyles): AnnotatedString {
    val marked = linkifyUrls(text)
    return buildAnnotatedString {
        append(marked.text)
        for (url in marked.getStringAnnotations(URL_TAG, 0, marked.length)) {
            addLink(LinkAnnotation.Url(url.item, links), url.start, url.end)
        }
    }
}
