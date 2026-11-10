package net.zodac.dicefive.ui.styles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.game.style.StyleCategory
import net.zodac.dicefive.game.style.access
import net.zodac.dicefive.ui.common.OffscreenWarmUp

/**
 * Draws the Styles page once, out of sight, so its code - the app bar, the fields, each pick's preview - has already run
 * by the time a player opens it: the first time any of it runs after a launch it's slow (loading a mat's texture, painting
 * a marble die, compiling a D20's drawing), slow enough to hold the page's opening for several frames. Placed on the menu
 * (under its opaque backdrop, in a 1dp clipped box, so nothing shows), it waits for the menu to settle, then draws the
 * page's empty frame and then the whole page, a pass at a time. Compose doesn't cull what a clip hides, so the art is
 * really drawn, not just composed. Runs once per launch. [width] keeps the box as wide as the page.
 *
 * The category pages aren't drawn here, but on the Styles page once it's open ([StyleCategoriesWarmUp]).
 */
@Composable
fun StylesWarmUp(picks: SavedStyles?, width: Dp, modifier: Modifier = Modifier) {
    if (picks == null || stylesWarmedUp) return
    OffscreenWarmUp<StylesPass>(
        key = Unit,
        delayMillis = WARM_UP_DELAY_MILLIS,
        pageSize = pageSize(width),
        modifier = modifier,
        run = {
            for (pass in StylesPass.entries) draw(pass)
            stylesWarmedUp = true
        },
    ) { pass ->
        Column(modifier = Modifier.fillMaxSize()) {
            when (pass) {
                StylesPass.FRAME -> StylesScaffold(picks = null, onBack = {}, onOpenCategory = {}, driftingDice = false)
                StylesPass.PAGE -> StylesScaffold(picks = picks, onBack = {}, onOpenCategory = {}, driftingDice = false)
            }
        }
    }
}

/** [StylesWarmUp]'s passes: the page's empty frame on its own, then the whole page - each its own first-time code. */
private enum class StylesPass { FRAME, PAGE }

/**
 * Draws every category's tiles once, out of sight, while the Styles page is open, so opening one isn't the first time
 * its tiles' art runs (see [StylesWarmUp]): [WARM_UP_TILES_PER_PASS] at a time, a category at a time, then one
 * category's whole page ([StyleCategoryScaffold]) for the page's own code - every page's is the same, and each whole page
 * a pass cost a heavy frame on the Styles page. Placed under the Styles page, which is opaque, in a 1dp clipped box.
 *
 * Once per launch, but not from the start each time: a player who opens a category, or leaves the page, before it's done
 * stops it, and the next time the Styles page is open it carries on from the pass it had reached ([categoryWarmUpDone]).
 */
@Composable
internal fun StyleCategoriesWarmUp(picks: SavedStyles?, width: Dp, modifier: Modifier = Modifier) {
    if (picks == null) return
    val passes = remember(picks.access) { categoryWarmUpPasses(picks) }
    if (categoryWarmUpDone >= passes.size) return
    OffscreenWarmUp<CategoryPass>(
        key = passes,
        delayMillis = WARM_UP_DELAY_MILLIS,
        pageSize = pageSize(width),
        modifier = modifier,
        run = {
            while (categoryWarmUpDone < passes.size) {
                draw(passes[categoryWarmUpDone])
                categoryWarmUpDone++
            }
        },
    ) { pass ->
        Column(modifier = Modifier.fillMaxSize()) {
            // The pages' own measurer, so the names laid out here are the ones the pages draw.
            CompositionLocalProvider(LocalTileLabelMeasurer provides rememberTileLabelMeasurer()) {
                when (pass) {
                    is CategoryPass.Tiles -> StyleCategoryTiles(category = pass.category, picks = picks, onSelect = {}, warmUp = pass.tiles)
                    is CategoryPass.Page -> StyleCategoryScaffold(category = pass.category, picks = picks, onBack = {}, onSelect = {}, driftingDice = false)
                }
            }
        }
    }
}

/** [StyleCategoriesWarmUp]'s passes, in order: every category's tiles a few at a time, then the first category's whole page. */
internal fun categoryWarmUpPasses(picks: SavedStyles): List<CategoryPass> = buildList {
    for (category in StyleCategory.entries) {
        for (first in 0 until category.tileCount(picks.access) step WARM_UP_TILES_PER_PASS) {
            add(CategoryPass.Tiles(category, first until first + WARM_UP_TILES_PER_PASS))
        }
    }
    add(CategoryPass.Page(StyleCategory.entries.first()))
}

/** One step of [StyleCategoriesWarmUp]: some of a category's tiles, or a category's whole page. */
internal sealed interface CategoryPass {
    data class Tiles(val category: StyleCategory, val tiles: IntRange) : CategoryPass
    data class Page(val category: StyleCategory) : CategoryPass
}

/** As big as a page would be, so it lays out as it would there. */
@Composable
private fun pageSize(width: Dp): DpSize {
    val window = LocalWindowInfo.current.containerSize
    return DpSize(width, with(LocalDensity.current) { window.height.toDp() })
}

/** Set once [StylesWarmUp] has drawn the Styles page, for the life of the process. */
private var stylesWarmedUp = false

/**
 * How many of [categoryWarmUpPasses] [StyleCategoriesWarmUp] has drawn, for the life of the process: where it carries
 * on from. Main thread only.
 */
internal var categoryWarmUpDone = 0

// Long enough for the page under it to have opened and settled (the menu's buttons' fade, the Styles page's fade-in).
private const val WARM_UP_DELAY_MILLIS = 600L

// How many tiles StyleCategoriesWarmUp draws at once - few enough that a pass fits beside the page's own frame.
private const val WARM_UP_TILES_PER_PASS = 3
