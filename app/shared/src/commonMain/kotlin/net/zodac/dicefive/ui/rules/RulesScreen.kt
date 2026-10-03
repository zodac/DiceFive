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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import net.zodac.dicefive.ui.common.FooterPill
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.parseInlineMarkup
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.ui.game.style.IvoryDiceStyle
import net.zodac.dicefive.ui.game.style.palette

/** One page of [RulesScreen]. [title] heads the page itself; [tabLabel] is the shorter name its tab
 * carries, so the tab row shows more than one or two tabs at a time. [blocks] render in order: each
 * [RulesText] as one block of body text - a plain list rather than a single string with embedded
 * newlines, so a page mixing prose and a short list reads as separate paragraphs - and each
 * [RulesDice] as a small row of example dice under the text it illustrates. Text may use
 * [parseInlineMarkup]'s markers for bold, italic, underline and monospace. */
private data class RulesPage(val title: String, val tabLabel: String, val blocks: List<RulesBlock>)

private sealed interface RulesBlock

private data class RulesText(val text: String) : RulesBlock

/** One die in a [RulesDice] example: its [value], whether it [counts] towards the category being
 * shown (the rest are drawn faded), and the [colour] it rolled in Tricolour, if any. */
private data class ExampleDie(val value: Int, val counts: Boolean = true, val colour: DieColour? = null)

/** A row of five example dice illustrating the rule above it, with what they score alongside. */
private data class RulesDice(val dice: List<ExampleDie>, val score: String) : RulesBlock

private fun text(text: String) = RulesText(text)

/** Five example dice, the first [counting] of which make the category. */
private fun dice(vararg values: Int, counting: Int = values.size, score: String): RulesDice =
    RulesDice(values.mapIndexed { index, value -> ExampleDie(value, counts = index < counting) }, score)

/** Five coloured example dice for Tricolour, the first [counting] of which make the category. */
private fun colouredDice(vararg dice: Pair<Int, DieColour>, counting: Int = dice.size, score: String): RulesDice =
    RulesDice(dice.mapIndexed { index, (value, colour) -> ExampleDie(value, counts = index < counting, colour = colour) }, score)

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
        blocks = listOf(
            text("Score as many points as possible by rolling five dice, with three rolls per round."),
            text("You may keep any dice you want after a roll, then roll the remaining dice."),
            text("Once you're happy with the roll - or you've rolled three times - score it in any open category on your scorecard."),
            text("The game ends once every category is filled."),
        ),
    ),
    RulesPage(
        title = "Scoring: Upper Section",
        tabLabel = "Upper Section",
        blocks = listOf(
            text("Each *Upper Section* category, from *Ones* to *Sixes*, scores the total of the dice showing that number."),
            text("For example, rolling `5-5-5-2-1` would give a score of **15pts** in the *Fives* category."),
            dice(5, 5, 5, 2, 1, counting = 3, score = "15pts"),
            text("Score **63pts** or more across the whole section and you earn a bonus **35pts**! That's an average of three of each number."),
        ),
    ),
    RulesPage(
        title = "Scoring: Lower Section",
        tabLabel = "Lower Section",
        blocks = listOf(
            text("The *Lower Section* awards points for specific dice combinations:"),
            text("- *3x*: Total of all five dice, if at least three dice are the same"),
            dice(5, 5, 5, 2, 6, counting = 3, score = "23pts"),
            text("- *4x*: Total of all five dice, if at least four dice are the same"),
            dice(4, 4, 4, 4, 1, counting = 4, score = "17pts"),
            text("- *Full House* [25pts]: Three of one number and two of another"),
            dice(3, 3, 3, 6, 6, score = "25pts"),
            text("- *Small Straight* [30pts]: Four numbers in a row (`1-2-3-4`, `2-3-4-5`, `3-4-5-6`)"),
            dice(2, 3, 4, 5, 2, counting = 4, score = "30pts"),
            text("- *Large Straight* [40pts]: Five numbers in a row (`1-2-3-4-5`, `2-3-4-5-6`)"),
            dice(1, 2, 3, 4, 5, score = "40pts"),
            text("- *5x* [50pts]: All five dice are the same"),
            dice(6, 6, 6, 6, 6, score = "50pts"),
            text("- *Chance*: The sum of all five dice"),
            dice(2, 3, 5, 5, 6, score = "21pts"),
        ),
    ),
    RulesPage(
        title = "5x and the Joker Rule",
        tabLabel = "5x & Joker",
        blocks = listOf(
            text("If you roll five matching dice, you can score a *5x* worth **50pts**."),
            text("Roll another five matching dice after already scoring a *5x*? It earns a **100pts** bonus, *on top of* whatever category you then score those dice in."),
            text("When you score a repeat *5x*, the Joker rule decides where it can go:"),
            text("1 - The matching *Upper Section* category, if it's still open. Five `4`s must go in *Fours*, scored for **20pts**, in addition to the bonus."),
            dice(4, 4, 4, 4, 4, score = "20pts + 100pts"),
            text("2 - Otherwise, any unscored category outside the *Upper Section*, in addition to the bonus. *Full House*, *Small Straight* and *Large Straight* score their full fixed amount."),
            text("3 - If every category outside the *Upper Section* is already filled, you must score it in an unscored *Upper Section* category for **0pts** - but you still get the **100pts** bonus."),
        ),
    ),
    RulesPage(
        title = "Tie Breaks",
        tabLabel = "Tie Breaks",
        blocks = listOf(
            text("If multiple players end the game with the same score, the following checks are made in order - the first difference decides who wins the tie:"),
            text("- Fewest *5x*"),
            text("- Most categories scored zero"),
            text("- Lower *Upper Section* total"),
            text("- Lower *Chance*"),
            text("- Lower *3x*"),
            text("- Lower *4x*"),
            text("If all of these are equal, then it is a true tie."),
        ),
    ),
    RulesPage(
        title = "Mode: Tricolour",
        tabLabel = "Tricolour",
        blocks = listOf(
            text("A custom mode extending the *Standard* game mode. Every die also rolls a colour - red, yellow or blue - alongside its number."),
            text("There are four extra scoring categories:"),
            text("- *Reds* [40pts]: All five dice are red"),
            colouredDice(2 to DieColour.RED, 5 to DieColour.RED, 1 to DieColour.RED, 6 to DieColour.RED, 3 to DieColour.RED, score = "40pts"),
            text("- *Yellows* [40pts]: All five dice are yellow"),
            colouredDice(4 to DieColour.YELLOW, 4 to DieColour.YELLOW, 1 to DieColour.YELLOW, 5 to DieColour.YELLOW, 2 to DieColour.YELLOW, score = "40pts"),
            text("- *Blues* [40pts]: All five dice are blue"),
            colouredDice(6 to DieColour.BLUE, 3 to DieColour.BLUE, 3 to DieColour.BLUE, 2 to DieColour.BLUE, 5 to DieColour.BLUE, score = "40pts"),
            text("- *Coloured House* [25pts]: Three of one colour and two of another"),
            colouredDice(1 to DieColour.RED, 4 to DieColour.RED, 6 to DieColour.RED, 2 to DieColour.BLUE, 5 to DieColour.BLUE, score = "25pts"),
            text("Under the joker rule, a repeat *5x* also scores *Coloured House* at its full **25pts**. Everything else plays exactly the same as the *Standard* rules, just with more opportunities to score."),
            text("See if you can find the Easter Egg in this mode!"),
        ),
    ),
    RulesPage(
        title = "Mode: Quickfire",
        tabLabel = "Quickfire",
        blocks = listOf(
            text("A custom mode extending the *Standard* game mode. You get just **one roll** per turn - no holding dice, no rerolls - and the dice are rolled for you as your turn starts."),
            text("Every turn also has a **10 second** timer, which replaces the usual *Turn Timer* setting. If it runs out, the roll is scored in whichever open category it's worth the *least* in - the first one on the scorecard, if several tie."),
            text("Scoring, bonuses and the Joker rule are exactly the same as the *Standard* rules - you just have to take what the dice give you, and quickly!"),
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
                    for (block in rulesPage.blocks) {
                        when (block) {
                            is RulesText -> Text(
                                text = parseInlineMarkup(
                                    block.text,
                                    codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            is RulesDice -> RulesDiceRow(block, modifier = Modifier.padding(start = 12.dp, bottom = 14.dp))
                        }
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

/** The size of each die in a [RulesDiceRow] - small enough that five and a score fit beside a bullet's indent. */
private val EXAMPLE_DIE_SIZE = 28.dp

/** How faint a die that doesn't count towards the example's category is drawn. */
private const val EXAMPLE_DIE_FADED_ALPHA = 0.35f

/** Mock-up only: how a [RulesDiceRow] singles out the dice that count. */
enum class ExampleHighlight { FADE_OTHERS, UNDERLINE_COUNTED }

/** Mock-up only: which [ExampleHighlight] to draw, so both can be rendered for review. */
var exampleHighlight = ExampleHighlight.FADE_OTHERS

/**
 * Five example dice in the Classic style (each in its own colour in a Tricolour example), then what
 * they score. Dice that don't count towards the category are faded, so "three 5s, plus two others"
 * reads at a glance. TalkBack hears it as one sentence - the dice, which don't count and the score -
 * rather than five unlabelled images.
 */
@Composable
private fun RulesDiceRow(example: RulesDice, modifier: Modifier = Modifier) {
    val spoken = remember(example) { example.spokenDescription() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        for (die in example.dice) {
            val style = die.colour?.let { IvoryDiceStyle.recoloured(it.palette) } ?: IvoryDiceStyle
            val faded = exampleHighlight == ExampleHighlight.FADE_OTHERS && !die.counts
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 6.dp)) {
                style.Die(
                    value = die.value,
                    held = false,
                    modifier = Modifier.size(EXAMPLE_DIE_SIZE).graphicsLayer { alpha = if (faded) EXAMPLE_DIE_FADED_ALPHA else 1f },
                )
                if (exampleHighlight == ExampleHighlight.UNDERLINE_COUNTED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(width = EXAMPLE_DIE_SIZE - 4.dp, height = 3.dp)
                            .clip(CircleShape)
                            .background(if (die.counts) MaterialTheme.colorScheme.primary else Color.Transparent),
                    )
                }
            }
        }
        Text(
            text = "= ${example.score}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** "Example: 5, 5, 5, 2, 6. The 2 and 6 don't count. Scores 23 points." - the row read aloud. */
private fun RulesDice.spokenDescription(): String {
    fun ExampleDie.spoken() = colour?.let { "${it.name.lowercase()} $value" } ?: value.toString()
    val ignored = dice.filterNot { it.counts }.map { it.spoken() }
    val ignoredSentence = when (ignored.size) {
        0 -> ""
        1 -> " The ${ignored.single()} doesn't count."
        else -> " The ${ignored.dropLast(1).joinToString(", ")} and ${ignored.last()} don't count."
    }
    val spokenScore = score.replace("pts", " points").replace("+", "plus")
    return "Example: ${dice.joinToString(", ") { it.spoken() }}.$ignoredSentence Scores $spokenScore."
}

/** Space between the end of a page's text and the top of the footer, once scrolled to the bottom. */
private val PAGE_FOOTER_GAP = 8.dp

/**
 * "1 of 7" pinned to the bottom of the pages - where you are and how many there are, in one glance,
 * alongside the tab row's chevrons (which say only that there's more). A small gold pill drawn over the
 * pages, not a row of its own, so it costs the pages no height: longer text scrolls behind it, the
 * pill's own background keeping it readable on top. Drawn by the shared [FooterPill].
 *
 * TalkBack hears "Page 1 of 7", and as a polite live region it's announced again whenever the page
 * changes - a swipe through the pager otherwise lands on a new page without a word.
 */
@Composable
private fun PageCountFooter(page: Int, pageCount: Int, modifier: Modifier = Modifier) {
    FooterPill(
        text = "${page + 1} of $pageCount",
        modifier = modifier.semantics {
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
