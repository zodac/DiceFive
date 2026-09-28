package net.zodac.dicefive.ui.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString

/** The annotation tag marking a URL's span in text built by [linkifyUrls]. */
const val URL_TAG = "url"

private val URL_PATTERN = Regex("""https?://[^\s<>"()]+""")

/** Characters a URL can't usefully end in, but prose often puts straight after one. */
private const val URL_TRAILING_PUNCTUATION = ".,;:!?'"

/** [text] with every http(s) URL in it marked as a link. */
fun linkifyUrls(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in URL_PATTERN.findAll(text)) {
        val url = match.value.trimEnd { it in URL_TRAILING_PUNCTUATION }
        append(text, cursor, match.range.first)
        pushStringAnnotation(URL_TAG, url)
        append(url)
        pop()
        cursor = match.range.first + url.length
    }
    append(text, cursor, text.length)
}

/**
 * Drops whatever text is selected in one Licences dialog when a tap lands anywhere in it - the way
 * selection behaves in a browser. Each platform's licence document supplies its own (it's the one
 * that knows what "selected" means for its text view); the dialog provides it via
 * [LocalSelectionClearer] and wires the taps with [clearSelectionsOnTap].
 */
interface SelectionClearer {

    fun clearAll()

    /** Whether the tap in progress was used up dismissing a selection (and resets it) - so that
     * same tap doesn't also follow a link. */
    fun consumeDismissedTap(): Boolean
}

val LocalSelectionClearer = staticCompositionLocalOf<SelectionClearer?> { null }

/**
 * Calls [SelectionClearer.clearAll] for every tap anywhere in this element. Watches on the Initial
 * pass without consuming anything, so it runs before the tapped view sees the tap and never gets in
 * the way of what the tap was for. A drag past the touch slop (a scroll) or a long press (a new
 * selection) isn't a tap.
 */
internal fun Modifier.clearSelectionsOnTap(clearer: SelectionClearer): Modifier = this.then(
    Modifier.pointerInput(clearer) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var isTap = true
            var upAt = down.uptimeMillis
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) isTap = false
                if (!change.pressed) {
                    upAt = change.uptimeMillis
                    break
                }
            }
            if (isTap && upAt - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) clearer.clearAll()
        }
    },
)
