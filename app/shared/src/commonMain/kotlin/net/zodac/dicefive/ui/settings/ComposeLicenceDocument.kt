package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** A [SelectionClearer] with nothing to clear - each [ComposeLicenceDocument] section keeps its own selection. */
internal object NoSelectionClearer : SelectionClearer {
    override fun clearAll() = Unit

    override fun consumeDismissedTap(): Boolean = false
}

/**
 * The licence report in plain Compose: one section per licence - its name, what uses it, a "Show
 * licence text" toggle, and every item under it - with tappable links, each block of text
 * selectable on its own. What iOS shows, and what previews show; Android draws the report as one
 * platform TextView instead (see TextViewLicenceDocument in :app:android), because Compose's selection can't span
 * rows and its links misbehave inside a SelectionContainer on Android. Whether iOS needs a native text
 * view too is an open question in .claude/IOS_SUPPORT.md.
 */
@Composable
internal fun ComposeLicenceDocument(report: LicenseReport, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val links = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))

    LazyColumn(modifier = modifier) {
        items(report.groups, key = { it.name }) { group ->
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(bottom = 8.dp))
                Text(text = group.name, style = typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.primary)
                Text(text = group.usage, style = typography.bodySmall, color = colors.onSurfaceVariant)
                val isExpanded = group.name in expanded
                TextButton(onClick = { expanded = if (isExpanded) expanded - group.name else expanded + group.name }) {
                    Text(text = if (isExpanded) "Hide licence text" else "Show licence text")
                }
                SelectionContainer {
                    Column {
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
            }
        }
        if (report.notices.isNotEmpty()) {
            item(key = "notices") {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(bottom = 8.dp))
                    Text(text = "Notices", style = typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.primary)
                    Text(
                        text = "Attribution notices shipped with the libraries above",
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            items(report.notices, key = { "notice-${it.library}" }) { notice ->
                SelectionContainer {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(text = notice.library, style = typography.bodyMedium)
                        Text(text = linked(notice.text, links), style = typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
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
