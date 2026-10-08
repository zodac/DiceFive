package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.about_author_body
import net.zodac.dicefive.resources.app_name
import net.zodac.dicefive.resources.about_author_heading
import net.zodac.dicefive.resources.about_author_link
import net.zodac.dicefive.resources.about_close_cd
import net.zodac.dicefive.resources.about_inspiration_body
import net.zodac.dicefive.resources.about_inspiration_heading
import net.zodac.dicefive.resources.about_inspiration_link
import net.zodac.dicefive.resources.about_privacy_body
import net.zodac.dicefive.resources.about_privacy_heading
import net.zodac.dicefive.resources.about_privacy_link
import net.zodac.dicefive.resources.about_title
import net.zodac.dicefive.ui.common.CONTENT_MAX_WIDTH
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.parseInlineMarkup
import net.zodac.dicefive.ui.common.stringResource
import org.jetbrains.compose.resources.StringResource

private const val GITHUB_REPO_URL = "https://github.com/zodac/DiceFive"
private const val DICE_ME_ONLINE_URL = "https://play.google.com/store/apps/details?id=com.giu.diceme"

/** Published by GitHub Pages from `docs/privacy-policy.md` - the same URL the Play Store listing links to. */
private const val PRIVACY_POLICY_URL = "https://zodac.github.io/DiceFive/privacy-policy"

/** A button under a section's body that opens [url] in the browser. */
private data class AboutLink(val label: StringResource, val url: String)

/** One About section: a heading, its body text and an optional link. Kept as data rather than
 * inlined composables so the list itself documents what the page says, in order, without reading
 * the layout code. Names in [body] go between backticks, which draws them a shade brighter than the
 * rest of the sentence. */
private data class AboutSection(val heading: StringResource, val body: StringResource, val link: AboutLink? = null)

private val ABOUT_SECTIONS = listOf(
    AboutSection(
        heading = Res.string.about_author_heading,
        body = Res.string.about_author_body,
        link = AboutLink(Res.string.about_author_link, GITHUB_REPO_URL),
    ),
    AboutSection(
        heading = Res.string.about_inspiration_heading,
        body = Res.string.about_inspiration_body,
        link = AboutLink(Res.string.about_inspiration_link, DICE_ME_ONLINE_URL),
    ),
    AboutSection(
        heading = Res.string.about_privacy_heading,
        body = Res.string.about_privacy_body,
        link = AboutLink(Res.string.about_privacy_link, PRIVACY_POLICY_URL),
    ),
)

/**
 * Settings > "About": who made the app and where its source lives, the other app that inspired it,
 * and the privacy policy - kept separate from [LicensesDialog], which is specifically the open-source
 * licences the build ships under, not people or products. Same raised reading surface as
 * [LicensesDialog], for the same reason: a short scroll of prose, not a question to answer.
 *
 * Each section is its own card, so the three read as separate groups. Gold is kept for the page
 * title and the links, the only things here that respond to a tap. Headings are `onSurface`, body
 * text `onSurfaceVariant`, and names are just a brighter, medium-weight version of the body text,
 * so headings, highlights and links no longer all share one colour.
 */
@Composable
fun AboutDialog(onDismissRequest: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current

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
            Column(modifier = Modifier.fillMaxSize().padding(top = 4.dp, bottom = 20.dp, start = 20.dp, end = 20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(Res.string.about_close_cd))
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text(
                        text = stringResource(Res.string.about_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val nameStyle = SpanStyle(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (section in ABOUT_SECTIONS) {
                            AboutSectionCard(
                                section = section,
                                nameStyle = nameStyle,
                                onOpenLink = uriHandler::openUri,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSectionCard(section: AboutSection, nameStyle: SpanStyle, onOpenLink: (String) -> Unit) {
    // One step above the dialog's surfaceContainerHigh, so the card reads as raised on it.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        // A link's 40dp button already carries ~10dp of space under its label, so the card's own
        // bottom padding shrinks to match, keeping the gap under the last line the same as above it.
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (section.link == null) 16.dp else 6.dp)) {
            Text(
                text = stringResource(section.heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = parseInlineMarkup(stringResource(section.body, stringResource(Res.string.app_name)), nameStyle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            section.link?.let { link ->
                // Pulled left by the button's own start padding, so its icon lines up with the
                // text above rather than sitting indented; the touch target is unchanged.
                TextButton(
                    onClick = { onOpenLink(link.url) },
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .offset(x = -ButtonDefaults.TextButtonWithIconContentPadding.calculateStartPadding(LayoutDirection.Ltr)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(link.label))
                }
            }
        }
    }
}
