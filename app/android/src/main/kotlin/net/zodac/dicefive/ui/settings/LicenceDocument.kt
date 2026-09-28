package net.zodac.dicefive.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LineBackgroundSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.ContextMenu
import android.view.GestureDetector
import android.view.Menu
import android.view.MotionEvent
import android.view.View
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
import androidx.core.graphics.drawable.toDrawable
import java.util.WeakHashMap

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
    val divider: Int,
    /** A heading's size relative to the body text (titleMedium / bodyMedium). */
    val headingScale: Float,
    /** Secondary text's size relative to the body text (bodySmall / bodyMedium). */
    val smallScale: Float,
)

/**
 * The whole licence report as one piece of styled text: headings, rows, credits and (when expanded)
 * each licence's full text. One text, rather than a view per row, because only within a single
 * TextView can a selection be dragged across rows - so any run of it, or all of it, can be copied in
 * one go. Each licence's text is folded away behind a "Show licence text" toggle ([expanded] holds the
 * licence names currently shown; [onToggle] flips one).
 */
internal fun buildLicenceDocument(
    report: LicenseReport,
    expanded: Set<String>,
    style: DocumentStyle,
    onToggle: (String) -> Unit,
): SpannableStringBuilder {
    val doc = SpannableStringBuilder()

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

    fun divider() = appendStyled(" \n", DividerSpan(style.divider), RelativeSizeSpan(0.9f))

    fun heading(title: String, subtitle: String) {
        if (doc.isNotEmpty()) divider()
        appendStyled(title, StyleSpan(Typeface.BOLD), ForegroundColorSpan(style.accent), RelativeSizeSpan(style.headingScale))
        doc.append('\n')
        appendStyled(subtitle, ForegroundColorSpan(style.secondaryText), RelativeSizeSpan(style.smallScale))
        doc.append('\n')
    }

    for (group in report.groups) {
        heading(group.name, group.usage)
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
    if (report.notices.isNotEmpty()) {
        heading("Notices", "Attribution notices shipped with the libraries above")
        gap()
        for (notice in report.notices) {
            doc.append(notice.library).append('\n')
            appendLinked(linkifyUrls(notice.text), ForegroundColorSpan(style.secondaryText), RelativeSizeSpan(style.smallScale))
            doc.append('\n')
            gap()
        }
    }
    // No trailing newline: it would only add an empty line to the end of the scroll.
    while (doc.endsWith("\n")) doc.delete(doc.length - 1, doc.length)
    return doc
}

/**
 * The licence report, selectable end to end: one platform TextView (in a platform ScrollView), not
 * Compose text, because the platform already does what a licence page needs and Compose can't -
 * a long press selects (a whole URL, via smart selection) and brings up the system's own Copy /
 * Share / Select all toolbar, the selection drags across the whole document (scrolling as it goes),
 * and TalkBack sees every link. A tap on a link opens it, and on a "Show licence text" toggle flips
 * it - unless a selection was showing, in which case the tap just dismisses the selection.
 *
 * (Compose was tried first and failed on device: its LinkAnnotation opens on every press inside a
 * SelectionContainer, long press included; a custom long-press link menu raced the selection
 * gesture, which can't be pre-empted, and crashed; and a selection couldn't span rows.)
 */
@Composable
internal fun TextViewLicenceDocument(report: LicenseReport, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val clearer = LocalSelectionClearer.current
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val style = DocumentStyle(
        text = colors.onSurface.toArgb(),
        secondaryText = colors.onSurfaceVariant.toArgb(),
        accent = colors.primary.toArgb(),
        divider = colors.outlineVariant.toArgb(),
        headingScale = typography.titleMedium.fontSize.value / typography.bodyMedium.fontSize.value,
        smallScale = typography.bodySmall.fontSize.value / typography.bodyMedium.fontSize.value,
    )
    val document = remember(report, expanded, style) {
        buildLicenceDocument(report, expanded.toSet(), style) { name ->
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
            val text = LinkTextView(context).apply {
                setTextIsSelectable(true)
                (clearer as? TextViewSelectionClearer)?.register(this)
                onTap = onTap@{ event ->
                    if (clearer?.consumeDismissedTap() == true || hasSelection()) return@onTap false
                    when (val span = clickableSpanAt(event)) {
                        null -> false
                        is URLSpan -> true.also { uriHandler.openUri(span.url) }
                        else -> true.also { span.onClick(this) }
                    }
                }
            }
            ScrollView(context).apply {
                isVerticalScrollBarEnabled = true
                addView(text)
            }
        },
        onRelease = { scroll -> (scroll.getChildAt(0) as? TextView)?.let { (clearer as? TextViewSelectionClearer)?.unregister(it) } },
        update = { scroll ->
            val view = scroll.getChildAt(0) as LinkTextView
            view.text = document
            view.setTextColor(style.text)
            view.setLinkTextColor(style.accent)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, bodySize)
            view.setLineSpacing(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, view.resources.displayMetrics), 1f)
            view.tintSelection(accent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                scroll.verticalScrollbarThumbDrawable = accent.toArgb().toDrawable()
            }
        },
    )
}

/** Selection highlight - and, where the platform allows (API 29+), the drag handles - in [accent],
 * rather than the window theme's default accent. */
private fun TextView.tintSelection(accent: Color) {
    highlightColor = accent.copy(alpha = 0.35f).toArgb()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        listOfNotNull(textSelectHandle, textSelectHandleLeft, textSelectHandleRight).forEach { it.mutate().setTint(accent.toArgb()) }
    }
}

/** A full-width rule through the middle of its (otherwise blank) line - the text's divider. */
private class DividerSpan(private val color: Int) : LineBackgroundSpan {
    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int,
    ) {
        val oldColor = paint.color
        paint.color = color
        val middle = (top + bottom) / 2f
        canvas.drawRect(left.toFloat(), middle - 0.5f * DIVIDER_PX, right.toFloat(), middle + 0.5f * DIVIDER_PX, paint)
        paint.color = oldColor
    }

    private companion object {
        const val DIVIDER_PX = 2f
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
