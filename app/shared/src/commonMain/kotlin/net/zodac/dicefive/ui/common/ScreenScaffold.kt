package net.zodac.dicefive.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_back_cd

/** How long a page's content takes to fade in once it's opened - see [ScreenScaffold]. */
const val PAGE_CONTENT_FADE_IN_MILLIS = 100

/** Keeps a page's content from stretching into an unreadable line on a tablet or in landscape. */
val CONTENT_MAX_WIDTH = 460.dp

/** The gap between [TwoPanes]' panes. */
private val PANE_GAP = 24.dp

/** Widest a [wide][ScreenScaffold] page's content gets in landscape: two panes of [CONTENT_MAX_WIDTH] and the gap. */
val WIDE_CONTENT_MAX_WIDTH = CONTENT_MAX_WIDTH * 2 + PANE_GAP

/** Whether the page is wider than it's tall - provided by [ScreenScaffold] and [PageColumn] for the page's content. */
val LocalLandscapePage = compositionLocalOf { false }

/**
 * Two groups of a page's content: side by side, [start] then [end], each half the width, on a landscape page
 * ([LocalLandscapePage]); one above the other, as a single column, otherwise. Each pane's items are [spacing] apart, as a
 * page's are.
 */
@Composable
fun TwoPanes(
    start: @Composable ColumnScope.() -> Unit,
    end: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
) {
    if (LocalLandscapePage.current) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PANE_GAP)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing), content = start)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing), content = end)
        }
    } else {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            start()
            end()
        }
    }
}

/**
 * The shared frame for every non-menu page: the brand backdrop, an M3 top app bar carrying the
 * page name and a back affordance, and the page's own content below.
 *
 * The app bar is the standard Android answer to "how do I get out of here" - the app previously
 * relied on the system back gesture alone, with nothing on screen to say a page was a page.
 *
 * [scrollable] wraps the content in a vertical scroll; leave it false for pages that manage their
 * own scrolling (a `LazyColumn`, or anything using `Modifier.weight`).
 *
 * The app bar's title is bold, set in [SoraFontFamily] (the same face as the menu wordmark) and
 * tinted `colorScheme.primary` - the app's one brand colour, the same gold a Statistics max score
 * or a game board's "press this" uses - rather than the plain default `titleMedium` text a bare
 * `CenterAlignedTopAppBar` gives you for free. The bar itself stays transparent over the backdrop,
 * same as the rest of the app's chrome.
 *
 * [onBack] is null for a page that can't be left for now (a picker holding a value it can't keep): no back arrow is shown.
 *
 * [driftingDice] is only ever false for a copy built out of sight ahead of time (the Styles page's
 * warm-up): the drift is the app's one shared one, which a second copy would move along too.
 *
 * [horizontalPadding] is a [scrollable] page's side margin - narrower only where the page shows
 * the game board's own pieces (the scorecard review), which are sized for the game screen's margin.
 *
 * [wide] lets the content take up to [WIDE_CONTENT_MAX_WIDTH] in landscape, for a page that splits into [TwoPanes] there;
 * any other keeps [CONTENT_MAX_WIDTH], a readable line, either way.
 *
 * [actions] sit at the app bar's end - where a landscape page moves controls that would otherwise take a row of its height.
 * The whole page, app bar included, sees [LocalLandscapePage].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    driftingDice: Boolean = true,
    horizontalPadding: Dp = PAGE_HORIZONTAL_PADDING,
    wide: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BrandBackdrop(modifier = modifier, driftingDice = driftingDice) {
        LandscapePage {
            Scaffold(
                // The backdrop is already drawn behind; the Scaffold only supplies structure, insets
                // and the app bar, so it must not paint its own opaque container over the top.
                containerColor = Color.Transparent,
                topBar = { PageTopBar(title = title, onBack = onBack, actions = actions) },
            ) { innerPadding ->
                // The page's content eases in rather than appearing at once - whatever part of it is a
                // frame or two late (a loaded list, a Styles row centring on its pick) fades in with the
                // rest instead of popping in. The app bar is left to the page transition's own fade.
                // Under reduced motion it's simply there.
                val reduceMotion = LocalReduceMotion.current
                val contentAlpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
                LaunchedEffect(Unit) { contentAlpha.animateTo(1f, tween(PAGE_CONTENT_FADE_IN_MILLIS)) }
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha.value }) {
                    if (scrollable) {
                        // The content goes straight into PageColumn's viewport-tall column rather than a
                        // nested one, so a page can still use Modifier.weight to place itself vertically.
                        val layoutDirection = LocalLayoutDirection.current
                        PageColumn(
                            horizontalPadding = horizontalPadding,
                            wide = wide,
                            contentPadding = PaddingValues.Absolute(
                                left = innerPadding.calculateLeftPadding(layoutDirection),
                                top = innerPadding.calculateTopPadding(),
                                right = innerPadding.calculateRightPadding(layoutDirection),
                                bottom = innerPadding.calculateBottomPadding(),
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            content = content,
                        )
                    } else {
                        // Not scrollable: the content manages its own (a LazyColumn taking weight(1f)),
                        // which can't be nested inside an outer scroll. The Box is what centres the
                        // width-capped column - `align` here would attach to the backdrop's Box, which is
                        // no longer this node's parent, and be quietly ignored.
                        Box(
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .widthIn(max = if (LocalLandscapePage.current && wide) WIDE_CONTENT_MAX_WIDTH else CONTENT_MAX_WIDTH)
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                content = content,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** [content], told through [LocalLandscapePage] whether the room it's given is wider than it's tall. */
@Composable
fun LandscapePage(content: @Composable () -> Unit) {
    BoxWithConstraints {
        CompositionLocalProvider(LocalLandscapePage provides (maxWidth > maxHeight), content = content)
    }
}

/**
 * [ScreenScaffold]'s app bar on its own, for a page that can't take the whole scaffold but still
 * wants its title where every other page's is (the results screen, whose fireworks go between the
 * backdrop and the page). With no [onBack] there's no back arrow, and the title stays centred. [actions] go at its end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageTopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = pageTitleStyle,
                modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back_cd))
                }
            }
        },
        actions = actions,
        // Shorter in landscape, where a page's height is what's short.
        expandedHeight = if (LocalLandscapePage.current) LANDSCAPE_APP_BAR_HEIGHT else TopAppBarDefaults.TopAppBarExpandedHeight,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier,
    )
}

/** [PageTopBar]'s height on a landscape page: an icon button's. */
private val LANDSCAPE_APP_BAR_HEIGHT = 48.dp

/** A [PageColumn]'s side margin, unless the page asks for another. */
private val PAGE_HORIZONTAL_PADDING = 24.dp

/**
 * A page body that fills the screen but scrolls if it can't fit - the case on a short screen
 * (landscape, split-screen, a large display font) - with a scrollbar in its right-hand margin while it does.
 *
 * The inner column is held to at least the viewport height, so on an ordinary portrait phone
 * nothing actually scrolls and `Modifier.weight` spacers inside still have real space to divide up
 * (a weight inside an unbounded scroll would otherwise collapse to nothing).
 */
@Composable
fun PageColumn(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = PAGE_HORIZONTAL_PADDING,
    // Defaults to the system bar insets, for a page used on its own (the menu, the results
    // screen) - the app draws edge to edge, so without this its content runs under the status
    // bar's clock. A page inside a ScreenScaffold passes the Scaffold's own inner padding here
    // instead, which already accounts for both the insets and the app bar; taking the default as
    // well would inset it twice.
    contentPadding: PaddingValues = WindowInsets.systemBars.asPaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    // Up to WIDE_CONTENT_MAX_WIDTH in landscape - see ScreenScaffold's.
    wide: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val viewportHeight = maxHeight
        val landscape = maxWidth > maxHeight
        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompositionLocalProvider(LocalLandscapePage provides landscape) {
                Column(
                    modifier = Modifier
                        .widthIn(max = if (landscape && wide) WIDE_CONTENT_MAX_WIDTH else CONTENT_MAX_WIDTH)
                        .heightIn(min = viewportHeight)
                        .padding(horizontal = horizontalPadding, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = verticalArrangement,
                    content = content,
                )
            }
        }
        // In the right-hand margin while the page is too tall to show at once.
        VerticalScrollbar(scroll, width = horizontalPadding, modifier = Modifier.align(Alignment.TopEnd))
    }
}
