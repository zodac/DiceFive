package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.dicefive.online.GameServicesState
import net.zodac.dicefive.online.LocalGameServices
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.about_author_body
import net.zodac.dicefive.resources.about_author_heading
import net.zodac.dicefive.resources.about_author_link
import net.zodac.dicefive.resources.about_close_cd
import net.zodac.dicefive.resources.about_inspiration_body
import net.zodac.dicefive.resources.about_inspiration_heading
import net.zodac.dicefive.resources.about_inspiration_link
import net.zodac.dicefive.resources.about_privacy_body
import net.zodac.dicefive.resources.about_privacy_body_online
import net.zodac.dicefive.resources.about_privacy_heading
import net.zodac.dicefive.resources.about_privacy_link
import net.zodac.dicefive.resources.about_title
import net.zodac.dicefive.resources.app_name
import net.zodac.dicefive.ui.common.LARGE_DIALOG_MARGIN
import net.zodac.dicefive.ui.common.LargeDialog
import net.zodac.dicefive.ui.common.LargeDialogTitle
import net.zodac.dicefive.ui.common.VerticalScrollbar
import net.zodac.dicefive.ui.common.parseInlineMarkup
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.icons.Symbols
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
 * rest of the sentence. [onlineBody] replaces [body] where the device has an online game service,
 * taking its name as a second argument. */
private data class AboutSection(
    val heading: StringResource,
    val body: StringResource,
    val link: AboutLink? = null,
    val onlineBody: StringResource? = null,
)

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
        onlineBody = Res.string.about_privacy_body_online,
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
 *
 * With [debugCrashes] (a debug build), [DebugCrashes.TAP_TARGET] taps on the author's name turn on the crash mode, with a
 * toast, and each link then crashes the app its own way ([DebugCrashes.Kind], in the links' order) instead of opening.
 */
@Composable
fun AboutDialog(onDismissRequest: () -> Unit, modifier: Modifier = Modifier, debugCrashes: Boolean = false) {
    val uriHandler = LocalUriHandler.current
    var nameTaps by remember { mutableIntStateOf(0) }
    var crashMode by remember { mutableStateOf(false) }
    var crashWhileDrawn by remember { mutableStateOf(false) }
    if (crashWhileDrawn) throw DebugCrashes.error(DebugCrashes.Kind.COMPOSITION)
    val platform = if (debugCrashes) LocalPlatformServices.current else null
    val onNameTapped: (() -> Unit)? = platform?.let {
        {
            if (!crashMode && ++nameTaps >= DebugCrashes.TAP_TARGET) {
                crashMode = true
                it.showTransientMessage(DebugCrashes.ACTIVATED_MESSAGE)
            }
        }
    }
    fun crash(kind: DebugCrashes.Kind) = when (kind) {
        DebugCrashes.Kind.MAIN_THREAD -> throw DebugCrashes.error(kind)
        DebugCrashes.Kind.BACKGROUND -> DebugCrashes.crashInBackground()
        DebugCrashes.Kind.COMPOSITION -> crashWhileDrawn = true
    }
    val services = LocalGameServices.current
    // Null without a service: no Settings card, so nothing to sign in to.
    val serviceName = services.serviceName.takeIf { services.state.collectAsStateWithLifecycle().value != GameServicesState.Unavailable }

    LargeDialog(onDismissRequest = onDismissRequest, closeContentDescription = stringResource(Res.string.about_close_cd), modifier = modifier) {
        val scroll = rememberScrollState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
                LargeDialogTitle(stringResource(Res.string.about_title))
                Spacer(modifier = Modifier.height(16.dp))

                val nameStyle = SpanStyle(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ABOUT_SECTIONS.forEachIndexed { index, section ->
                        AboutSectionCard(
                            section = section,
                            serviceName = serviceName,
                            nameStyle = nameStyle,
                            onOpenLink = if (crashMode) { _ -> crash(DebugCrashes.Kind.entries[index]) } else uriHandler::openUri,
                            onNameTapped = onNameTapped.takeIf { section.heading == Res.string.about_author_heading },
                        )
                    }
                }
            }
            // In the dialog's right-hand margin, beside the cards rather than over them - as in LicensesDialog.
            VerticalScrollbar(
                scrollState = scroll,
                width = LARGE_DIALOG_MARGIN,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = LARGE_DIALOG_MARGIN),
            )
        }
    }
}

@Composable
private fun AboutSectionCard(
    section: AboutSection,
    serviceName: String?,
    nameStyle: SpanStyle,
    onOpenLink: (String) -> Unit,
    onNameTapped: (() -> Unit)? = null,
) {
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
            val appName = stringResource(Res.string.app_name)
            val body = if (section.onlineBody != null && serviceName != null) {
                stringResource(section.onlineBody, appName, serviceName)
            } else {
                stringResource(section.body, appName)
            }
            val text = parseInlineMarkup(body, nameStyle)
            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                onTextLayout = { layout = it },
                modifier = Modifier.padding(top = 4.dp).then(
                    // Raw taps, and only on a highlighted name: a debug build's crash mode, never a button for TalkBack.
                    if (onNameTapped == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(text, onNameTapped) {
                            detectTapGestures { position ->
                                val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                                if (text.spanStyles.any { it.item == nameStyle && offset in it.start..<it.end }) onNameTapped()
                            }
                        }
                    },
                ),
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
                        imageVector = Symbols.OpenInNew,
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
