package net.zodac.dicefive.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How long a page's content takes to fade in once it's opened - see [ScreenScaffold]. */
const val PAGE_CONTENT_FADE_IN_MILLIS = 100

/** Space between a floating footer and the bottom edge, and again between it and the content above. */
private val FOOTER_GAP = 8.dp

/** Keeps a page's content from stretching into an unreadable line on a tablet or in landscape. */
val CONTENT_MAX_WIDTH = 460.dp

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
 * [footer] floats over the content, pinned to the bottom centre (a [FooterPill]): the content isn't
 * given a row for it, but is padded at its end by the footer's measured height, so a large font
 * still clears it and the last line can always scroll above it. Only for a [scrollable] page.
 *
 * The app bar's title is bold, set in [SoraFontFamily] (the same face as the menu wordmark) and
 * tinted `colorScheme.primary` - the app's one brand colour, the same gold a Statistics max score
 * or a game board's "press this" uses - rather than the plain default `titleMedium` text a bare
 * `CenterAlignedTopAppBar` gives you for free. The bar itself stays transparent over the backdrop,
 * same as the rest of the app's chrome.
 *
 * [driftingDice] is only ever false for a copy built out of sight ahead of time (the Styles page's
 * warm-up): the drift is the app's one shared one, which a second copy would move along too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    driftingDice: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    require(footer == null || scrollable) { "A footer floats over a scrollable page" }
    BrandBackdrop(modifier = modifier, driftingDice = driftingDice) {
        Scaffold(
            // The backdrop is already drawn behind; the Scaffold only supplies structure, insets
            // and the app bar, so it must not paint its own opaque container over the top.
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
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
                    var footerHeightPx by remember { mutableIntStateOf(0) }
                    val footerReserve = if (footer == null) 0.dp else with(LocalDensity.current) { footerHeightPx.toDp() } + FOOTER_GAP * 2
                    val layoutDirection = LocalLayoutDirection.current
                    PageColumn(
                        contentPadding = PaddingValues.Absolute(
                            left = innerPadding.calculateLeftPadding(layoutDirection),
                            top = innerPadding.calculateTopPadding(),
                            right = innerPadding.calculateRightPadding(layoutDirection),
                            bottom = innerPadding.calculateBottomPadding() + footerReserve,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        content = content,
                    )
                    if (footer != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = innerPadding.calculateBottomPadding() + FOOTER_GAP)
                                .onSizeChanged { footerHeightPx = it.height },
                        ) { footer() }
                    }
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
                                .widthIn(max = CONTENT_MAX_WIDTH)
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

/**
 * A page body that fills the screen but scrolls if it can't fit - the case on a short screen
 * (landscape, split-screen, a large display font).
 *
 * The inner column is held to at least the viewport height, so on an ordinary portrait phone
 * nothing actually scrolls and `Modifier.weight` spacers inside still have real space to divide up
 * (a weight inside an unbounded scroll would otherwise collapse to nothing).
 */
@Composable
fun PageColumn(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 24.dp,
    // Defaults to the system bar insets, for a page used on its own (the menu, the results
    // screen) - the app draws edge to edge, so without this its content runs under the status
    // bar's clock. A page inside a ScreenScaffold passes the Scaffold's own inner padding here
    // instead, which already accounts for both the insets and the app bar; taking the default as
    // well would inset it twice.
    contentPadding: PaddingValues = WindowInsets.systemBars.asPaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val viewportHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}
