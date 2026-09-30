package net.zodac.dicefive.ui.rules

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.parseInlineMarkup

/** One page of [RulesScreen]. [title] heads the page itself; [tabLabel] is the shorter name its tab
 * carries, so the tab row shows more than one or two tabs at a time. [paragraphs] renders as one
 * block of body text per entry - a plain list rather than a single string with embedded newlines,
 * so a page mixing prose and a short numbered list (see the joker rule) reads as separate paragraphs
 * rather than one dense block. Each paragraph may use [parseInlineMarkup]'s markers for bold,
 * italic, underline and monospace. */
private data class RulesPage(val title: String, val tabLabel: String, val paragraphs: List<String>)

/**
 * The rules explained in the player's own words, not the rulebook's - one page per idea, swiped
 * (or picked from the tab row) rather than scrolled past as one long page, so each (upper section,
 * lower section, the joker rule, the house rule tie-break, and one page per non-Standard game mode)
 * gets its own moment rather than blurring into the next.
 *
 * Kept in step with the actual rules engine: `ScoreCategory`'s fixed values (25/30/40/50/100),
 * `ScoreCalculator`'s joker rule priority, `game/TieBreak.kt`'s criterion order (see its own
 * doc comment), and each `GameMode`'s rolls, dice and timer - a rule change there should be
 * echoed here.
 */
private val RULES_PAGES = listOf(
    RulesPage(
        title = "How to Play DiceFive",
        tabLabel = "How to Play",
        paragraphs = listOf(
            "Score as many points as possible by rolling five dice, with three rolls per round.",
            "You may keep any dice you want after a roll, then roll the remaining dice.",
            "Once you're happy with the roll - or you've rolled three times - score it in any open category on your scorecard.",
            "The game ends once every category is filled.",
        ),
    ),
    RulesPage(
        title = "Scoring: Upper Section",
        tabLabel = "Upper Section",
        paragraphs = listOf(
            "Each *Upper Section* category, from *Ones* to *Sixes*, scores the total of the dice showing that number.",
            "For example, rolling `5-5-5-2-1` would give a score of **15pts** in the *Fives* category.",
            "Score **63pts** or more across the whole section and you earn a bonus **35pts**! That's an average of three of each number.",
        ),
    ),
    RulesPage(
        title = "Scoring: Lower Section",
        tabLabel = "Lower Section",
        paragraphs = listOf(
            "The *Lower Section* awards points for specific dice combinations:",
            "- *3x*: Total of all five dice, if at least three dice are the same",
            "- *4x*: Total of all five dice, if at least four dice are the same",
            "- *Full House* [25pts]: Three of one number and two of another",
            "- *Small Straight* [30pts]: Four numbers in a row (`1-2-3-4`, `2-3-4-5`, `3-4-5-6`)",
            "- *Large Straight* [40pts]: Five numbers in a row (`1-2-3-4-5`, `2-3-4-5-6`)",
            "- *5x* [50pts]: All five dice are the same",
            "- *Chance*: The sum of all five dice",
        ),
    ),
    RulesPage(
        title = "5x and the Joker Rule",
        tabLabel = "5x & Joker",
        paragraphs = listOf(
            "If you roll five matching dice, you can score a *5x* worth **50pts**.",
            "Roll another five matching dice after already scoring a *5x*? It earns a **100pts** bonus, *on top of* whatever category you then score those dice in.",
            "When you score a repeat *5x*, the Joker rule decides where it can go:",
            "1 - The matching *Upper Section* category, if it's still open. Five `4`s must go in *Fours*, scored for **20pts**, in addition to the bonus.",
            "2 - Otherwise, any unscored category outside the *Upper Section*, in addition to the bonus. *Full House*, *Small Straight* and *Large Straight* score their full fixed amount.",
            "3 - If every category outside the *Upper Section* is already filled, you must score it in an unscored *Upper Section* category for **0pts** - but you still get the **100pts** bonus.",
        ),
    ),
    RulesPage(
        title = "Tie Breaks",
        tabLabel = "Tie Breaks",
        paragraphs = listOf(
            "If multiple players end the game with the same score, the following checks are made in order - the first difference decides who wins the tie:",
            "- Fewest *5x*",
            "- Most categories scored zero",
            "- Lower *Upper Section* total",
            "- Lower *Chance*",
            "- Lower *3x*",
            "- Lower *4x*",
            "If all of these are equal, then it is a true tie.",
        ),
    ),
    RulesPage(
        title = "Mode: Tricolour",
        tabLabel = "Tricolour",
        paragraphs = listOf(
            "A custom mode extending the *Standard* game mode. Every die also rolls a colour - red, yellow or blue - alongside its number.",
            "There are four extra scoring categories:",
            "- *Reds* [40pts]: All five dice are red",
            "- *Yellows* [40pts]: All five dice are yellow",
            "- *Blues* [40pts]: All five dice are blue",
            "- *Coloured House* [25pts]: Three of one colour and two of another",
            "Under the joker rule, a repeat *5x* also scores *Coloured House* at its full **25pts**. Everything else plays exactly the same as the *Standard* rules, just with more opportunities to score.",
            "See if you can find the Easter Egg in this mode!",
        ),
    ),
    RulesPage(
        title = "Mode: Quickfire",
        tabLabel = "Quickfire",
        paragraphs = listOf(
            "A custom mode extending the *Standard* game mode. You get just **one roll** per turn - no holding dice, no rerolls - and the dice are rolled for you as your turn starts.",
            "Every turn also has a **10 second** timer, which replaces the usual *Turn Timer* setting. If it runs out, the roll is scored in whichever open category it's worth the *least* in - the first one on the scorecard, if several tie.",
            "Scoring, bonuses and the Joker rule are exactly the same as the *Standard* rules - you just have to take what the dice give you, and quickly!",
        ),
    ),
)

/** How far in from each end of the tab row its tabs are hidden outright while there are more to
 * scroll to: the chevron's glyph (24dp, centred in its 48dp button, so ending 36dp in) plus a small
 * gap, so no label is ever drawn underneath it. */
private val TAB_EDGE_CLEAR = 40.dp

/** Past [TAB_EDGE_CLEAR], how far the tabs take to fade back in to full strength. */
private val TAB_EDGE_FADE = 24.dp

/** How much scrolling is left before an end's chevron and fade start easing away - so they fade out
 * as the last tab comes into view rather than switching off at the very end. */
private val TAB_EDGE_EASE = 48.dp

/** How much of the tab row's visible width a chevron tap scrolls by - less than all of it, so the
 * tab that was cut off at the edge is still in view afterwards, now whole. */
private const val TAB_CHEVRON_SCROLL_FRACTION = 0.6f

/**
 * The Rules page: a tab per [RulesPage] over a [HorizontalPager] of them. The tabs scroll, since the
 * list grows by a page with every game mode - a row of dots stopped saying where you were, or
 * letting you get to the last page, once there were more than a handful.
 *
 * A scrolling tab row gives no sign on its own that there's more of it: on a phone the first three
 * tabs can end right at the edge, so the row looks like all there is. A fade alone didn't fix that -
 * it only shows when a label happens to be under it, and with the selected tab centred the next one
 * can start just past the edge, leaving the fade over empty space. So each end with tabs beyond it
 * gets a [TabScrollChevron], which is there whatever the labels' widths, over a [fadeOffscreenEdges]
 * fade that keeps a cut-off label from running into it. The row and its tabs carry collection
 * semantics so TalkBack says "Tab, 1 of 7", the spoken form of the same hint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { RULES_PAGES.size })
    val coroutineScope = rememberCoroutineScope()
    val tabScrollState = rememberScrollState()

    ScreenScaffold(title = "Rules", onBack = onBack, modifier = modifier) {
        // A real Box, so the chevrons' align lands on their actual parent (see UI.md's gotchas).
        Box(modifier = Modifier.fillMaxWidth()) {
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                scrollState = tabScrollState,
                // Transparent over the backdrop, like the app bar above it, and flush with the page text.
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .fadeOffscreenEdges(tabScrollState, clearWidth = TAB_EDGE_CLEAR, fadeWidth = TAB_EDGE_FADE, easeDistance = TAB_EDGE_EASE)
                    .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = RULES_PAGES.size) },
            ) {
                RULES_PAGES.forEachIndexed { index, rulesPage ->
                    Tab(
                        selected = index == pagerState.currentPage,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(text = rulesPage.tabLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.semantics {
                            collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1)
                        },
                    )
                }
            }
            TabScrollChevron(scrollState = tabScrollState, forward = false, modifier = Modifier.align(Alignment.CenterStart))
            TabScrollChevron(scrollState = tabScrollState, forward = true, modifier = Modifier.align(Alignment.CenterEnd))
        }

        // The footer floats over the pages rather than taking a row of its own: pinned to the bottom,
        // with the page text scrolling behind it, and each page padded by the footer's measured height
        // (so a large font still clears it) plus a gap, so the last line can always scroll above it.
        var footerHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val pageBottomPadding = with(density) { footerHeightPx.toDp() } + PAGE_FOOTER_GAP
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val rulesPage = RULES_PAGES[page]
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = pageBottomPadding)) {
                    Text(
                        text = rulesPage.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    for (paragraph in rulesPage.paragraphs) {
                        Text(
                            text = parseInlineMarkup(
                                paragraph,
                                codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                }
            }

            PageCountFooter(
                page = pagerState.currentPage,
                pageCount = RULES_PAGES.size,
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { footerHeightPx = it.height },
            )
        }
    }
}

/** Space between the end of a page's text and the top of the footer, once scrolled to the bottom. */
private val PAGE_FOOTER_GAP = 8.dp

/**
 * "1 of 7" pinned to the bottom of the pages - where you are and how many there are, in one glance,
 * alongside the tab row's chevrons (which say only that there's more). A small gold pill drawn over the
 * pages, not a row of its own, so it costs the pages no height: longer text scrolls behind it, the
 * pill's own background keeping it readable on top. A plain background rather than a `Surface`,
 * which would swallow touches and stop a scroll that starts on the pill.
 *
 * TalkBack hears "Page 1 of 7", and as a polite live region it's announced again whenever the page
 * changes - a swipe through the pager otherwise lands on a new page without a word.
 */
@Composable
private fun PageCountFooter(page: Int, pageCount: Int, modifier: Modifier = Modifier) {
    Text(
        text = "${page + 1} of $pageCount",
        style = MaterialTheme.typography.labelMedium,
        // The brand gold, as a filled button wears it: primary behind onPrimary, the pair the palette
        // guarantees contrast for (see UI.md's "Colour" - gold as a fill matches an existing use).
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics {
                contentDescription = "Page ${page + 1} of $pageCount"
                liveRegion = LiveRegionMode.Polite
            },
    )
}

/**
 * Hides a horizontally scrolling row's content at whichever end has more beyond it: fully across
 * [clearWidth], where that end's [TabScrollChevron] sits, so its glyph is never drawn over a label,
 * then fading back in over [fadeWidth]. Both deepen with how far there is left to scroll (up to
 * [easeDistance]), so they ease away as the last tab comes fully into view instead of vanishing at
 * the end. Drawn only: nothing is hidden from touch or from TalkBack.
 */
private fun Modifier.fadeOffscreenEdges(scrollState: ScrollState, clearWidth: Dp, fadeWidth: Dp, easeDistance: Dp): Modifier = this
    // Offscreen so the DstIn masks below cut into the row's own pixels, not the backdrop behind it.
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val width = (clearWidth + fadeWidth).toPx().coerceAtMost(size.width / 2)
        if (width <= 0f) return@drawWithContent
        // Where along the mask the clear zone gives way to the fade, as a fraction of it.
        val clearFraction = (clearWidth.toPx() / width).coerceIn(0f, 1f)
        val ease = easeDistance.toPx()
        val startStrength = scrollState.edgeStrength(forward = false, over = ease)
        val endStrength = scrollState.edgeStrength(forward = true, over = ease)
        if (startStrength > 0f) {
            val edge = Color.Black.copy(alpha = 1f - startStrength)
            drawRect(
                brush = Brush.horizontalGradient(0f to edge, clearFraction to edge, 1f to Color.Black, startX = 0f, endX = width),
                size = Size(width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (endStrength > 0f) {
            val edge = Color.Black.copy(alpha = 1f - endStrength)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    (1f - clearFraction) to edge,
                    1f to edge,
                    startX = size.width - width,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - width, 0f),
                size = Size(width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * How much more there is to scroll towards one end, from 0 (none) to 1 ([over] or more) - so an edge
 * hint deepens with the distance left rather than switching on and off, and eases away as the last
 * tab comes fully into view instead of vanishing at the end.
 */
private fun ScrollState.edgeStrength(forward: Boolean, over: Float): Float {
    val remaining = if (forward) maxValue - value else value
    return (remaining / over).coerceIn(0f, 1f)
}

/**
 * A chevron at one end of the tab row while there are more tabs that way - the hint that there's
 * more, which the fade can't give by itself when no label happens to be under it. A tap scrolls the
 * row that way rather than changing page: it points at more tabs, not at the next page.
 *
 * Kept out of TalkBack (`clearAndSetSemantics`): the tabs it scrolls to are TalkBack stops already,
 * each announced with its position and the count ("Tab, 5 of 7"), so it would only add a stop that
 * says nothing new.
 */
@Composable
private fun TabScrollChevron(scrollState: ScrollState, forward: Boolean, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val shown by remember(scrollState, forward) {
        derivedStateOf { if (forward) scrollState.canScrollForward else scrollState.canScrollBackward }
    }
    // Not composed at all when there's nothing that way, so it never sits over a tab taking its taps.
    if (!shown) return

    IconButton(
        onClick = {
            val distance = scrollState.viewportSize * TAB_CHEVRON_SCROLL_FRACTION
            coroutineScope.launch { scrollState.animateScrollBy(if (forward) distance else -distance) }
        },
        modifier = modifier
            .graphicsLayer { alpha = scrollState.edgeStrength(forward, over = TAB_EDGE_EASE.toPx()) }
            .clearAndSetSemantics {},
    ) {
        Icon(
            imageVector = if (forward) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
