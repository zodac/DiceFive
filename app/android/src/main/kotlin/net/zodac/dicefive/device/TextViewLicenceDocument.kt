package net.zodac.dicefive.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.ContextMenu
import android.view.GestureDetector
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import java.util.WeakHashMap
import net.zodac.dicefive.ui.settings.LicenceScroll
import net.zodac.dicefive.ui.settings.LicenseReport
import net.zodac.dicefive.ui.settings.LocalSelectionClearer
import net.zodac.dicefive.ui.settings.SelectionClearer
import net.zodac.dicefive.ui.settings.URL_TAG
import net.zodac.dicefive.ui.settings.linkifyUrls

/**
 * [SelectionClearer] for the platform text views in one dialog: remembers each one, so a tap anywhere
 * in the dialog can drop whatever text is selected in any of them.
 */
internal class TextViewSelectionClearer : SelectionClearer {
    private val views = WeakHashMap<TextView, Unit>()

    /** Set by a tap that just dismissed a selection, so that same tap doesn't also follow a link. */
    private var tapDismissedSelection = false

    fun register(view: TextView) {
        views[view] = Unit
    }

    fun unregister(view: TextView) {
        views.remove(view)
    }

    override fun clearAll() {
        tapDismissedSelection = false
        for (view in views.keys.toList()) {
            if (view.hasSelection()) {
                tapDismissedSelection = true
                (view.text as? Spannable)?.let(Selection::removeSelection)
            }
            // Losing focus is also what closes the text action mode (the Copy / Share toolbar).
            if (view.hasFocus()) view.clearFocus()
        }
    }

    override fun consumeDismissedTap(): Boolean = tapDismissedSelection.also { tapDismissedSelection = false }
}

/** The colours and relative text sizes the document is drawn in, all as platform values. */
internal data class DocumentStyle(
    val text: Int,
    val secondaryText: Int,
    val accent: Int,
    /** Each licence card's fill. */
    val card: Int,
    /** A heading's size relative to the body text (titleMedium / bodyMedium). */
    val headingScale: Float,
    /** Secondary text's size relative to the body text (bodySmall / bodyMedium). */
    val smallScale: Float,
)

/** One card of the licence report: [key] names it (a licence's name, or [NOTICES_KEY]), and [text]
 * is everything on it. */
internal class LicenceCard(val key: String, val text: SpannableStringBuilder)

internal const val NOTICES_KEY = "Notices"

/**
 * The licence report as one piece of styled text per card: one card per licence - its heading,
 * "Show licence text" toggle, (when expanded) the full text, and every item under it - then one for
 * the notices. One text per card, rather than a view per row, because within a single TextView a
 * selection can be dragged across rows - so any run of a card, or all of it, can be copied in one go -
 * while separate TextViews keep a selection from running on into the next licence. Each licence's
 * text is folded away behind its toggle ([expanded] holds the licence names currently shown;
 * [onToggle] flips one).
 */
internal fun buildLicenceCards(
    report: LicenseReport,
    expanded: Set<String>,
    style: DocumentStyle,
    onToggle: (String) -> Unit,
): List<LicenceCard> {
    val cards = mutableListOf<LicenceCard>()
    lateinit var doc: SpannableStringBuilder

    fun appendStyled(text: CharSequence, vararg spans: Any) {
        val start = doc.length
        doc.append(text)
        for (span in spans) doc.setSpan(span, start, doc.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun appendLinked(text: AnnotatedString, vararg spans: Any) {
        val start = doc.length
        appendStyled(text.text, *spans)
        for (link in text.getStringAnnotations(URL_TAG, 0, text.length)) {
            doc.setSpan(URLSpan(link.item), start + link.start, start + link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun gap() = appendStyled("\n", RelativeSizeSpan(0.5f))

    /** Starts a new card with its heading, as titleMedium is drawn: medium weight, in the text colour. */
    fun card(key: String, title: String, subtitle: String, content: () -> Unit) {
        doc = SpannableStringBuilder()
        appendStyled(title, TypefaceSpan("sans-serif-medium"), ForegroundColorSpan(style.text), RelativeSizeSpan(style.headingScale))
        doc.append('\n')
        appendStyled(subtitle, ForegroundColorSpan(style.secondaryText), RelativeSizeSpan(style.smallScale))
        doc.append('\n')
        content()
        // No trailing newline: it would only add an empty line to the bottom of the card.
        while (doc.endsWith("\n")) doc.delete(doc.length - 1, doc.length)
        cards += LicenceCard(key, doc)
    }

    for (group in report.groups) {
        card(group.name, group.name, group.usage) {
            val isExpanded = group.name in expanded
            appendStyled(
                if (isExpanded) "Hide licence text" else "Show licence text",
                ToggleSpan(style.accent) { onToggle(group.name) },
                RelativeSizeSpan(style.smallScale),
            )
            doc.append('\n')
            if (isExpanded) {
                gap()
                appendLinked(linkifyUrls(group.text), ForegroundColorSpan(style.secondaryText), RelativeSizeSpan(style.smallScale))
                doc.append('\n')
            }
            gap()
            for (component in group.components) {
                val nameStart = doc.length
                doc.append(component.name)
                component.website?.let { website ->
                    doc.setSpan(URLSpan(website), nameStart, doc.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (component.version != null) appendStyled("  ${component.version}", ForegroundColorSpan(style.secondaryText))
                doc.append('\n')
                component.copyright?.let { copyright ->
                    appendLinked(
                        linkifyUrls(copyright),
                        ForegroundColorSpan(style.secondaryText),
                        RelativeSizeSpan(style.smallScale),
                    )
                    doc.append('\n')
                }
            }
        }
    }
    if (report.notices.isNotEmpty()) {
        card(NOTICES_KEY, "Notices", "Attribution notices shipped with the libraries above") {
            gap()
            for (notice in report.notices) {
                doc.append(notice.library).append('\n')
                appendLinked(linkifyUrls(notice.text), ForegroundColorSpan(style.secondaryText), RelativeSizeSpan(style.smallScale))
                doc.append('\n')
                gap()
            }
        }
    }
    return cards
}

/**
 * The licence report as a column of cards, each one platform TextView (all in one platform
 * ScrollView), not Compose text, because the platform already does what a licence page needs and
 * Compose can't - a long press selects (a whole URL, via smart selection) and brings up the system's
 * own Copy / Share / Select all toolbar, the selection drags across a whole card (scrolling as it
 * goes) but no further, and TalkBack sees every link. A tap on a link opens it, and on a "Show licence text" toggle flips
 * it - unless a selection was showing, in which case the tap just dismisses the selection.
 *
 * (Compose was tried first and failed on device: its LinkAnnotation opens on every press inside a
 * SelectionContainer, long press included; a custom long-press link menu raced the selection
 * gesture, which can't be pre-empted, and crashed; and a selection couldn't span rows.)
 */
@Composable
internal fun TextViewLicenceDocument(report: LicenseReport, scroll: LicenceScroll, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val clearer = LocalSelectionClearer.current
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val style = DocumentStyle(
        text = colors.onSurface.toArgb(),
        secondaryText = colors.onSurfaceVariant.toArgb(),
        accent = colors.primary.toArgb(),
        card = colors.surfaceContainerHighest.toArgb(),
        headingScale = typography.titleMedium.fontSize.value / typography.bodyMedium.fontSize.value,
        smallScale = typography.bodySmall.fontSize.value / typography.bodyMedium.fontSize.value,
    )
    val cards = remember(report, expanded, style) {
        buildLicenceCards(report, expanded.toSet(), style) { name ->
            expanded = if (name in expanded) expanded - name else expanded + name
        }
    }
    val bodySize = typography.bodyMedium.fontSize.value
    val accent = colors.primary

    AndroidView(
        // A platform ScrollView scrolls its content by drawing it offset, relying on its parent to
        // clip that to its bounds - which Compose's interop host doesn't do. Unclipped, the scrolled-
        // away text drew up over the dialog's title (except mid-overscroll, whose stretch effect
        // draws through a clipped layer).
        modifier = modifier.clipToBounds(),
        factory = { context ->
            DocumentScrollView(context).apply {
                // The dialog draws the app's own scrollbar beside the list (from LicenceScroll), so the
                // platform's stays off.
                isVerticalScrollBarEnabled = false
                val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                addView(column)
                fun publishScroll() {
                    scroll.maxPosition = (column.height - height).coerceAtLeast(0)
                    scroll.position = scrollY
                }
                setOnScrollChangeListener { _, _, _, _, _ -> publishScroll() }
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> publishScroll() }
                column.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> publishScroll() }
                // A drag on the bar comes in fractions of a pixel; keep the remainder so a slow drag still moves.
                var pending = 0f
                scroll.scrollBy = { delta ->
                    pending += delta
                    val whole = pending.toInt()
                    pending -= whole
                    if (whole != 0) scrollBy(0, whole)
                }
            }
        },
        onRelease = { scrollView ->
            val column = scrollView.getChildAt(0) as ViewGroup
            for (i in 0 until column.childCount) (clearer as? TextViewSelectionClearer)?.unregister(column.getChildAt(i) as TextView)
        },
        update = { scrollView ->
            val column = scrollView.getChildAt(0) as LinearLayout
            val density = scrollView.resources.displayMetrics.density
            // One card per licence, and the licences don't change once loaded, so this only adds them
            // the first time; after that it's only a card's text that changes.
            while (column.childCount < cards.size) {
                column.addView(
                    newCardView(column.context, uriHandler::openUri, clearer),
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
                )
            }
            while (column.childCount > cards.size) {
                (clearer as? TextViewSelectionClearer)?.unregister(column.getChildAt(column.childCount - 1) as TextView)
                column.removeViewAt(column.childCount - 1)
            }
            val scrollY = scrollView.scrollY
            var changed = false
            cards.forEachIndexed { index, card ->
                val view = column.getChildAt(index) as LinkTextView
                (view.layoutParams as LinearLayout.LayoutParams).bottomMargin = if (index == cards.lastIndex) 0 else (CARD_GAP_DP * density).toInt()
                if (view.text !== card.text) {
                    changed = true
                    view.setText(card.text, TextView.BufferType.SPANNABLE)
                    // setText leaves the cursor at 0, and a focused TextView with a cursor scrolls to it
                    // whenever it next lays out or is touched - a jump back to the top of the card.
                    Selection.removeSelection(view.text as Spannable)
                }
                view.background = GradientDrawable().apply {
                    setColor(style.card)
                    cornerRadius = CARD_CORNER_DP * density
                }
                val padding = (CARD_PADDING_DP * density).toInt()
                view.setPadding(padding, padding, padding, padding)
                view.setTextColor(style.text)
                view.setLinkTextColor(style.accent)
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, bodySize)
                view.setLineSpacing(2f * density, 1f)
                view.tintSelection(accent)
            }
            if (changed) {
                // Setting new text on a selectable TextView moves its cursor to 0 and scrolls that into
                // view, snapping the list up whenever a licence is shown or hidden. Put the scroll back
                // before the next frame is drawn, so there's no visible jump.
                scrollView.viewTreeObserver.addOnPreDrawListener(
                    object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            scrollView.viewTreeObserver.removeOnPreDrawListener(this)
                            if (scrollView.scrollY != scrollY) {
                                scrollView.scrollTo(0, scrollY)
                                return false
                            }
                            return true
                        }
                    },
                )
            }
        },
    )
}

/** Matches the M3 Card's medium shape and the 16dp padding and gaps of the cards elsewhere (About). */
private const val CARD_CORNER_DP = 12f
private const val CARD_PADDING_DP = 16f
private const val CARD_GAP_DP = 16f

/** One card's selectable text: a tap on a link opens it, on a toggle flips it - unless a selection was
 * showing (in any card), in which case the tap only dismisses it. */
private fun newCardView(context: Context, openUri: (String) -> Unit, clearer: SelectionClearer?): LinkTextView =
    LinkTextView(context).apply {
        setTextIsSelectable(true)
        (clearer as? TextViewSelectionClearer)?.register(this)
        onTap = onTap@{ event ->
            if (clearer?.consumeDismissedTap() == true || hasSelection()) return@onTap false
            when (val span = clickableSpanAt(event)) {
                null -> false
                is URLSpan -> true.also { openUri(span.url) }
                else -> true.also { span.onClick(this) }
            }
        }
    }

/**
 * A ScrollView that won't scroll just to bring a whole card on screen. When a card takes focus - as a
 * tap on a "Show licence text" toggle makes it - a plain ScrollView scrolls to show all of that card,
 * which for one taller than the screen means putting its top in view, and for a shorter one partly
 * off screen means a jump nobody asked for. A cursor or drag-handle rectangle is a line tall, so
 * scrolling while a selection is dragged is unaffected.
 */
private class DocumentScrollView(context: Context) : ScrollView(context) {
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int =
        if (rect.height() > height || isWholeCard(rect)) 0 else super.computeScrollDeltaToGetChildRectOnScreen(rect)

    /** Whether [rect] (in the scroll's content coordinates, which are the card column's) is a whole card. */
    private fun isWholeCard(rect: Rect): Boolean {
        val column = getChildAt(0) as? ViewGroup ?: return false
        return (0 until column.childCount).map(column::getChildAt).any { it.top == rect.top && it.bottom == rect.bottom }
    }
}

/** Selection highlight - and, where the platform allows (API 29+), the drag handles - in [accent],
 * rather than the window theme's default accent. */
private fun TextView.tintSelection(accent: Color) {
    highlightColor = accent.copy(alpha = 0.35f).toArgb()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        listOfNotNull(textSelectHandle, textSelectHandleLeft, textSelectHandleRight).forEach { it.mutate().setTint(accent.toArgb()) }
    }
}

/** "Show licence text" / "Hide licence text": button-coloured, not underlined like a web link. */
private class ToggleSpan(private val color: Int, private val onToggle: () -> Unit) : ClickableSpan() {
    override fun onClick(widget: View) = onToggle()

    override fun updateDrawState(paint: TextPaint) {
        paint.color = color
        paint.isUnderlineText = false
        paint.isFakeBoldText = true
    }
}

/**
 * A TextView that also reports single taps to [onTap] (which returns whether it handled one - here,
 * by following a link or flipping a toggle), and answers a long press on a link with the platform's
 * context menu - the link's address as its title, then Copy link / Copy text, as a browser does -
 * instead of selecting. Every other event still reaches the TextView itself, since that's what drives
 * selection; a handled tap goes through performClick too, so it's announced like any other click.
 */
internal class LinkTextView(context: Context) : TextView(context) {
    var onTap: (MotionEvent) -> Boolean = { false }

    /** Where the current press went down - a long click itself carries no position. */
    private var downX = 0f
    private var downY = 0f

    /** The link the context menu is being built for: its URL and its text. */
    private var menuLink: Pair<String, String>? = null

    // A bare OnGestureListener, not SimpleOnGestureListener: that one is also a double-tap listener,
    // which makes the detector swallow a second quick tap (so "Show", then straight away "Hide",
    // would do nothing). Double-tapping to select a word is left to the TextView itself.
    private val taps = GestureDetector(
        context,
        object : GestureDetector.OnGestureListener {
            override fun onSingleTapUp(event: MotionEvent): Boolean = onTap(event)

            override fun onDown(event: MotionEvent): Boolean = false

            override fun onShowPress(event: MotionEvent) = Unit

            override fun onScroll(first: MotionEvent?, event: MotionEvent, dx: Float, dy: Float): Boolean = false

            override fun onLongPress(event: MotionEvent) = Unit

            override fun onFling(first: MotionEvent?, event: MotionEvent, vx: Float, vy: Float): Boolean = false
        },
    )

    init {
        // Runs before the TextView's own long-press handling, and returning true stops it from
        // selecting - so a long press on a link gets the link menu, and anywhere else selects as usual.
        setOnLongClickListener {
            val span = clickableSpanAt(downX, downY) as? URLSpan ?: return@setOnLongClickListener false
            val spanned = text as Spanned
            menuLink = span.url to spanned.subSequence(spanned.getSpanStart(span), spanned.getSpanEnd(span)).toString()
            showContextMenu(downX, downY)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x
            downY = event.y
        }
        // The detector returns onSingleTapUp's answer on the ACTION_UP that completes a tap.
        if (taps.onTouchEvent(event) && event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onCreateContextMenu(menu: ContextMenu) {
        val (url, label) = menuLink ?: return super.onCreateContextMenu(menu)
        menuLink = null
        menu.setHeaderTitle(url)
        menu.add(Menu.NONE, COPY_LINK, Menu.NONE, "Copy link").setOnMenuItemClickListener { copy("Link", url) }
        menu.add(Menu.NONE, COPY_TEXT, Menu.NONE, "Copy text").setOnMenuItemClickListener { copy("Text", label) }
    }

    private fun copy(what: String, value: String): Boolean {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(what, value))
        // Android 13+ confirms a copy itself; before that, nothing would say it worked.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "$what copied", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    companion object {
        const val COPY_LINK = 1
        const val COPY_TEXT = 2
    }
}

/** The link or toggle under [event], or null - off the end of a line counts as off it. */
private fun TextView.clickableSpanAt(event: MotionEvent): ClickableSpan? = clickableSpanAt(event.x, event.y)

/** The link or toggle at ([viewX], [viewY]) in this view, or null - off the end of a line counts as off it. */
private fun TextView.clickableSpanAt(viewX: Float, viewY: Float): ClickableSpan? {
    val layout = layout ?: return null
    val x = viewX - totalPaddingLeft + scrollX
    val y = viewY - totalPaddingTop + scrollY
    val line = layout.getLineForVertical(y.toInt())
    if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null
    val offset = layout.getOffsetForHorizontal(line, x)
    return (text as? Spanned)?.getSpans(offset, offset, ClickableSpan::class.java)?.firstOrNull()
}
