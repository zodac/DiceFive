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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/** One page of [RulesDialog]. [paragraphs] renders as one block of body text per entry - a plain
 * list rather than a single string with embedded newlines, so a page mixing prose and a short
 * numbered list (see the joker rule) reads as separate paragraphs rather than one dense block.
 * Each paragraph may use [parseInlineMarkup]'s markers for bold, italic, underline and monospace. */
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
        title = "How to Play DiceFive",
        paragraphs = listOf(
            "Score as many points as possible by rolling five dice, with three rolls per round.",
            "You may keep any dice you want after a roll, then roll the remaining dice.",
            "Once you're happy with the roll - or you've rolled three times - score it in any open category on your scorecard.",
            "The game ends once every category is filled.",
        ),
    ),
    RulesPage(
        title = "Scoring: Upper Section",
        paragraphs = listOf(
            "Each *Upper Section* category, from *Ones* to *Sixes*, scores the total of the dice showing that number.",
            "For example, rolling `5-5-5-2-1` would give a score of **15pts** in the *Fives* category.",
            "Score **63pts** or more across the whole section and you earn a bonus **35pts**! That's an average of three of each number.",
        ),
    ),
    RulesPage(
        title = "Scoring: Lower Section",
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
