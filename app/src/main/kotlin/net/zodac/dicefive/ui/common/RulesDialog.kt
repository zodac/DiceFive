package net.zodac.dicefive.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/** One page of [RulesDialog]. [paragraphs] renders as one block of body text per entry - a plain
 * list rather than a single string with embedded newlines, so a page mixing prose and a short
 * numbered list (see the joker rule) reads as separate paragraphs rather than one dense block. */
private data class RulesPage(val title: String, val paragraphs: List<String>)

/**
 * The rules explained in the player's own words, not the rulebook's - six pages, swiped rather than
 * scrolled past as one long page, so each idea (upper section, lower section, the joker rule, the
 * house rule tie-break, Tricolour) gets its own moment rather than blurring into the next.
 *
 * Kept in step with the actual rules engine: `ScoreCategory`'s fixed values (25/30/40/50/100),
 * `ScoreCalculator`'s joker rule priority, and `game/TieBreak.kt`'s criterion order (see its own
 * doc comment) - a rule change there should be echoed here.
 */
private val RULES_PAGES = listOf(
    RulesPage(
        title = "How to Play",
        paragraphs = listOf(
            "Each turn, roll five dice up to three times, choosing which to keep between rolls.",
            "Once you're happy with the roll - or you're out of rerolls - score it in any open category on your scorecard.",
            "The game ends once every category is filled. Highest total score wins.",
        ),
    ),
    RulesPage(
        title = "Upper Section",
        paragraphs = listOf(
            "Ones through Sixes: score the total of just the matching dice - three 4s in Fours scores 12.",
            "Score 63 or more across the whole section - roughly three of each number - and you earn a 35-point bonus.",
        ),
    ),
    RulesPage(
        title = "Lower Section",
        paragraphs = listOf(
            "Full House (25 points): three of one number and two of another.",
            "Small Straight (30): four numbers in a row. Large Straight (40): all five in a row.",
            "Three of a Kind / Four of a Kind: needs at least three (or four) matching dice, but scores the total of all five.",
            "Chance: no matching required at all - just the total of all five dice. The safety net for a bad roll.",
        ),
    ),
    RulesPage(
        title = "5x and the Joker Rule",
        paragraphs = listOf(
            "Five matching dice - a \"5x\" - scores 50 points.",
            "Roll another 5x after that box is already filled? It earns a 100-point bonus chip, on top of whatever category you score it in.",
            "Where a repeat 5x can go, in order:",
            "1. The matching upper box, if it's still open - five 4s must go in Fours, scored at full value.",
            "2. Otherwise, any other open box you like - Full House, Small Straight and Large Straight score their full fixed amount regardless of what the dice actually show.",
            "3. Only if nothing else is left open does it have to go in a leftover upper box for zero.",
        ),
    ),
    RulesPage(
        title = "Tie Breaks",
        paragraphs = listOf(
            "Matching the top score isn't automatically a shared win - a house rule breaks it, rewarding whoever got there with more handicaps.",
            "In order: fewest 5x, then most categories scored zero, then fewest Tricolour colour boxes scored (Tricolour games only), then the lowest upper section, Chance, Three of a Kind, then Four of a Kind.",
            "Only a tie all the way down that list still shares first place.",
        ),
    ),
    RulesPage(
        title = "Tricolour Mode",
        paragraphs = listOf(
            "Every die also lands a colour - red, yellow or blue - alongside its number.",
            "Four extra scorecard boxes: Reds, Yellows and Blues (40 points each, all five dice that colour), and Coloured House (25 points, three of one colour and two of another).",
            "Everything else - the upper section, 5x, the joker rule - plays exactly the same as the standard rules, just with more boxes to fill.",
        ),
    ),
)

/** A gap between dots roughly proportional to the dot itself, so the row reads as evenly spaced
 * rather than a cluster of large dots with barely any room between them. */
private val PAGE_INDICATOR_SPACING = 8.dp
private val PAGE_INDICATOR_SELECTED_SIZE = 10.dp
private val PAGE_INDICATOR_SIZE = 8.dp

@Composable
fun RulesDialog(onDismissRequest: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { RULES_PAGES.size })
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismissRequest,
        // The default dialog width caps out well short of CONTENT_MAX_WIDTH and can't be
        // overridden with a Modifier alone - this is what actually lets the Surface below claim
        // most of the screen for a multi-page reading surface rather than a small alert box.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f)
                .widthIn(max = CONTENT_MAX_WIDTH),
            shape = MaterialTheme.shapes.extraLarge,
            // Matches DiceFiveDialog's own container/elevation - the app's one dialog shape,
            // whether it's a plain confirmation or (as here) a paged reading surface.
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(top = 4.dp, bottom = 20.dp, start = 20.dp, end = 20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                    val rulesPage = RULES_PAGES[page]
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text(
                            text = rulesPage.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        for (paragraph in rulesPage.paragraphs) {
                            Text(
                                text = paragraph,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        enabled = pagerState.currentPage > 0,
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(PAGE_INDICATOR_SPACING), verticalAlignment = Alignment.CenterVertically) {
                        RULES_PAGES.indices.forEach { index ->
                            val selected = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .size(if (selected) PAGE_INDICATOR_SELECTED_SIZE else PAGE_INDICATOR_SIZE)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                            )
                        }
                    }

                    IconButton(
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        enabled = pagerState.currentPage < RULES_PAGES.lastIndex,
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                    }
                }
            }
        }
    }
}
