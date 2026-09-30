package net.zodac.dicefive.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A small gold pill of text, drawn over a page's content and pinned to its bottom - the Rules page's
 * "1 of 7" and the Settings page's version. The brand gold as a filled button wears it: primary behind
 * onPrimary, the pair the palette guarantees contrast for (see UI.md's "Colour"). A plain background
 * rather than a `Surface`, which would swallow touches and stop a scroll that starts on the pill.
 *
 * Any semantics (a spoken form, a live region) go on [modifier].
 */
@Composable
fun FooterPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
