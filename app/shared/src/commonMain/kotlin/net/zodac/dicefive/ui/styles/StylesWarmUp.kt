package net.zodac.dicefive.ui.styles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.first
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.game.style.StyleCategory
import net.zodac.dicefive.game.style.access
import net.zodac.dicefive.ui.common.OffscreenWarmUp
import net.zodac.dicefive.ui.common.textScale

/** What a page measured: its cards' whole height and the room it has for them, in px. */
internal class PageMeasure(val content: Int, val viewport: Int)

/** The screen a [PageFit] was worked out for: the window, its density and font scale. */
internal data class PageFitKey(val window: IntSize, val density: Float, val fontScale: Float)

/**
 * How much the cards are scaled down on the screen [key] they were measured on, so the page fits without
 * scrolling there. [StylesWarmUp] measures the page out of sight, after it has drawn the tiles once, and
 * saves this with the Styles picks ([SavedStyles.stylesPageFit]), so it's known from the first frame of
 * every later launch and opening the page costs nothing extra. [scale] is 1 for a page that fits as it is,
 * or one that would need shrinking below [MIN_PAGE_FIT_SCALE]: a phone that much shorter, or a large font,
 * still scrolls, rather than every tile getting tiny - so only a page a little too tall for its screen is
 * touched. Not measured yet (a fresh install, opened within moments of launching) or measured for another
 * screen (a resized window, a new font scale), the page is full size.
 */
internal data class PageFit(val key: PageFitKey, val scale: Float) {
    /** As it's saved: "1080x2400:2.9375:1.0=0.757". */
    fun encode(): String = "${key.window.width}x${key.window.height}:${key.density}:${key.fontScale}=$scale"

    companion object {
        fun decode(saved: String?): PageFit? = runCatching {
            val (screen, scale) = saved!!.split("=")
            val (window, density, fontScale) = screen.split(":")
            val (width, height) = window.split("x")
            PageFit(PageFitKey(IntSize(width.toInt(), height.toInt()), density.toFloat(), fontScale.toFloat()), scale.toFloat())
        }.getOrNull()
    }
}

// No smaller than this, or the page is left full size and scrolls. Low enough for the maintainer's own
// phone (~369 x 816dp), where the page needs about 77% with the unlocked count over it, and a 360 x 800dp one (about 75%).
private const val MIN_PAGE_FIT_SCALE = 0.7f
// A hair under the exact fit, so a pixel's rounding doesn't leave it scrolling by one.
private const val PAGE_FIT_SLACK = 0.995f

/**
 * The scale that fits a page measured at [full] size and at [smallest] ([MIN_PAGE_FIT_SCALE]) - see
 * [PageFit]. The text keeps its size, so the height isn't proportional to the scale: the two measures
 * split it into the part that scales and the part that doesn't.
 */
internal fun fittedPageScale(full: Int, smallest: Int, viewport: Int): Float {
    if (full <= viewport || smallest > viewport) return 1f
    val scaling = (full - smallest) / (1f - MIN_PAGE_FIT_SCALE)
    val fixed = full - scaling
    return ((viewport - fixed) / scaling * PAGE_FIT_SLACK).coerceIn(MIN_PAGE_FIT_SCALE, 1f)
}

/**
 * First measures the Styles page out of sight, without drawing it, and saves the size that fits it to its
 * screen ([PageFit]) - first, so a page opened soon after a fresh install is already sized.
 *
 * Then draws every tile of the Styles screen once, out of sight, so the page's code has already run by the
 * time a player opens it or scrolls a row. Each tile's art is its own drawing code, and the first time
 * any of it runs after a launch it's slow (loading a mat's texture, painting a marble die, compiling
 * a D20's drawing) - slow enough, run all at once, to hold the menu for several frames when the page
 * opens, and to make a row's first scroll stutter as each tile appeared. Placed on the menu (under
 * its opaque backdrop, in a 1dp clipped box, so nothing shows): it waits for the menu to settle, then
 * draws [WARM_UP_TILES_PER_PASS] tiles at a time, a category at a time, and drops them again - spread
 * thin enough that the menu's own drifting dice don't skip. Compose doesn't cull what a clip hides,
 * so the art is really drawn, not just composed. Runs once per launch: later openings of the menu
 * find it already done. [width] is unused by the tiles themselves; it keeps the box as wide as the page.
 *
 * Then the page around the tiles, which is its own first-time code - each category's card as the page
 * has it (its title and gallery toggle, its scrolling row, placeholders and scrollbar), one a pass,
 * and last the whole screen, app bar and backdrop too ([StylesScaffold]) - so a first open after a
 * launch doesn't load all of that at once on its opening frame (it was most of that frame).
 */
@Composable
fun StylesWarmUp(picks: SavedStyles?, width: Dp, modifier: Modifier = Modifier, onPageFit: (String) -> Unit = {}) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val fitKey = PageFitKey(window, density.density, density.textScale)
    // Done - unless the screen has changed since the page was measured for it (a resized window, a new
    // font scale), when it's measured again, though nothing else is redone.
    if (picks == null || (stylesWarmedUp && PageFit.decode(picks.stylesPageFit)?.key == fitKey)) return
    // As big as the page would be, so it lays out as it would there.
    val page = DpSize(width, with(density) { window.height.toDp() })
    OffscreenWarmUp<WarmUpPass>(
        key = fitKey,
        delayMillis = WARM_UP_DELAY_MILLIS,
        pageSize = page,
        modifier = modifier,
        run = {
            // First the whole page measures itself, so it can open already sized to fit (PageFit): full size,
            // then - only if it doesn't fit - at the smallest it may be drawn. Every launch, and before the tiles,
            // so the saved fit follows any change to the page and is in place within moments of the menu settling.
            // Laid out but not drawn, so its tiles' first-time drawing is still spread over the passes below.
            suspend fun measured(at: Float): PageMeasure? {
                pageMeasure = null
                current = WarmUpPass.Page(at, draw = false)
                repeat(WARM_UP_MEASURE_FRAMES) {
                    withFrameNanos { }
                    pageMeasure?.let { return it }
                }
                return null
            }
            val full = measured(1f)
            val smallest = when {
                full == null || full.content <= full.viewport -> full?.content
                else -> measured(MIN_PAGE_FIT_SCALE)?.content
            }
            // Full size where it couldn't be measured, so as not to try again for this screen. Saved only when it's
            // changed: normally it's the same each launch, but a new version of the page can change its height.
            val fit = PageFit(fitKey, if (full == null || smallest == null) 1f else fittedPageScale(full.content, smallest, full.viewport))
            if (PageFit.decode(picks.stylesPageFit) != fit) onPageFit(fit.encode())
            if (!stylesWarmedUp) {
                for (category in StyleCategory.entries) {
                    for (first in 0 until category.tileCount(picks.access) step WARM_UP_TILES_PER_PASS) {
                        draw(WarmUpPass.Tiles(category, first until first + WARM_UP_TILES_PER_PASS))
                    }
                }
                for (category in StyleCategory.entries) draw(WarmUpPass.Card(category))
                // The screen's frame on its own first, then with the page in it: each its own first-time code.
                draw(WarmUpPass.Frame)
                draw(WarmUpPass.Page(fit.scale, draw = true))
                stylesWarmedUp = true
            }
        },
    ) { pass ->
        val drawn = if (pass is WarmUpPass.Page && !pass.draw) Modifier.drawWithContent { } else Modifier
        Column(modifier = Modifier.fillMaxSize().then(drawn)) {
            // The page's own measurer, so the names laid out here are the ones the page draws.
            CompositionLocalProvider(LocalTileLabelMeasurer provides rememberTileLabelMeasurer()) {
                when (pass) {
                    is WarmUpPass.Tiles -> StyleCategorySection(category = pass.category, picks = picks, onSelect = {}, warmUp = pass.tiles)
                    is WarmUpPass.Card -> StyleCategorySection(category = pass.category, picks = picks, onSelect = {})
                    WarmUpPass.Frame -> StylesScaffold(picks = null, focus = null, onBack = {}, onSelect = { {} }, driftingDice = false)
                    is WarmUpPass.Page -> StylesScaffold(
                        picks = picks,
                        focus = null,
                        onBack = {},
                        onSelect = { {} },
                        driftingDice = false,
                        pageScale = pass.scale,
                        onPageMeasured = { pageMeasure = it },
                    )
                }
            }
        }
    }
}

/** One step of [StylesWarmUp]: some of a category's tiles, a category's whole card, the screen's empty frame, or the whole screen. */
private sealed interface WarmUpPass {
    data class Tiles(val category: StyleCategory, val tiles: IntRange) : WarmUpPass
    data class Card(val category: StyleCategory) : WarmUpPass
    data object Frame : WarmUpPass
    /** The whole screen, its cards at [scale] - see [PageFit] - and only laid out, to be measured, unless [draw]. */
    data class Page(val scale: Float, val draw: Boolean) : WarmUpPass
}

// The last measure a warm-up page reported, if any - see StylesWarmUp. Main thread only.
private var pageMeasure: PageMeasure? = null

// How many frames a measured warm-up page has to lay itself out and report, before it's given up on.
private const val WARM_UP_MEASURE_FRAMES = 10

/** Set once [StylesWarmUp] has drawn every category, for the life of the process. */
private var stylesWarmedUp = false

// Long enough for the menu's own entrance (its buttons' fade) to be over.
private const val WARM_UP_DELAY_MILLIS = 600L

// How many tiles StylesWarmUp draws at once - few enough that a pass fits beside the menu's own frame.
private const val WARM_UP_TILES_PER_PASS = 3
