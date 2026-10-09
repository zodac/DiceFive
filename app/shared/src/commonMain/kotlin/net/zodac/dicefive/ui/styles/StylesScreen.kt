package net.zodac.dicefive.ui.styles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.GridView as GridViewOutlined
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.achievements.StyleScrollRequest
import net.zodac.dicefive.data.achievements.StyleScrollRequests
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_ok
import net.zodac.dicefive.resources.style_variant_colour
import net.zodac.dicefive.resources.styles_category_background
import net.zodac.dicefive.resources.styles_category_dice
import net.zodac.dicefive.resources.styles_category_dice_cup
import net.zodac.dicefive.resources.styles_category_frame
import net.zodac.dicefive.resources.styles_category_mat
import net.zodac.dicefive.resources.styles_choose_variant_action
import net.zodac.dicefive.resources.styles_family_colour_cd
import net.zodac.dicefive.resources.styles_frame_preview_name
import net.zodac.dicefive.resources.styles_gallery_all_action
import net.zodac.dicefive.resources.styles_gallery_cd
import net.zodac.dicefive.resources.styles_gallery_row_action
import net.zodac.dicefive.resources.styles_locked_spoken
import net.zodac.dicefive.resources.styles_requirement_count
import net.zodac.dicefive.resources.styles_requirement_free
import net.zodac.dicefive.resources.styles_requirement_specific
import net.zodac.dicefive.resources.styles_select_action
import net.zodac.dicefive.resources.styles_selected_cd
import net.zodac.dicefive.resources.styles_show_unlock_action
import net.zodac.dicefive.resources.styles_title
import net.zodac.dicefive.resources.styles_unlock_count
import net.zodac.dicefive.resources.styles_unlock_free
import net.zodac.dicefive.resources.styles_unlock_specific
import net.zodac.dicefive.ui.common.AppTooltip
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.HorizontalScrollbar
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.PAGE_CONTENT_FADE_IN_MILLIS
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.VerticalScrollbar
import net.zodac.dicefive.ui.common.ambientMotion
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.common.parseInlineMarkup
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.rememberAppTooltipState
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.common.tooltipMarkup
import net.zodac.dicefive.ui.game.CUP_SHAKE_MILLIS
import net.zodac.dicefive.ui.game.style.DiceCupStyle
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceMat
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyle
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.DieMotion
import net.zodac.dicefive.ui.game.style.LocalDieMotion
import net.zodac.dicefive.ui.game.style.ScoreFrame
import net.zodac.dicefive.ui.game.style.ScoreFramePreviewColour
import net.zodac.dicefive.ui.game.style.ScoreFrames
import net.zodac.dicefive.ui.game.style.StyleCatalog
import net.zodac.dicefive.ui.game.style.StyleCatalogs
import net.zodac.dicefive.ui.game.style.StyleColour
import net.zodac.dicefive.ui.game.style.StyleFamily
import net.zodac.dicefive.ui.game.style.StyleUnlock
import net.zodac.dicefive.ui.game.style.TableArt
import net.zodac.dicefive.ui.game.style.TableBackground
import net.zodac.dicefive.ui.game.style.TableBackgrounds
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import net.zodac.dicefive.ui.theme.GoldAccent
import org.jetbrains.compose.resources.StringResource

// Kept small enough that the categories (Dice, Dice Cup, Mat, Background, Frame) fit on one screen
// without needing to scroll - see StylesScreen's doc comment.
private val DICE_PREVIEW_SIZE = 72.dp
private val DIE_ART_SIZE = 44.dp
private val CUP_PREVIEW_WIDTH = 72.dp
private val CUP_PREVIEW_HEIGHT = 96.dp
// The cup previews at this fraction of their in-game size, whatever their shape (tall: 42 x 60dp).
private const val CUP_ART_SCALE = 60f / 84f
// The dice tray's wide shape. Small enough for three across a gallery on a ~376dp phone (3 x 97dp
// in ~320dp, with 12dp gaps); 108 x 72 only fit two.
private val MAT_PREVIEW_WIDTH = 97.dp
private val MAT_PREVIEW_HEIGHT = 65.dp
// The score board's shape - about as tall as it is wide (380dp tall, the screen's width less its
// padding), a little taller than wide on most phones - not the mat's: it's what a background fills.
private val BACKGROUND_PREVIEW_WIDTH = 72.dp
private val BACKGROUND_PREVIEW_HEIGHT = 80.dp
// A frame round a player's tab, as on the game screen's page background: the mat's width, so three fit
// across a gallery, and tall enough for a tab-sized frame above the variant dots.
private val FRAME_PREVIEW_WIDTH = 97.dp
private val FRAME_PREVIEW_HEIGHT = 76.dp
// The tab drawn inside it - about a four-player game's - and how far it sits below the tile's top.
private val FRAME_TAB_WIDTH = 84.dp
private val FRAME_TAB_HEIGHT = 56.dp
private val FRAME_TAB_TOP = 5.dp
private val COLOUR_DOT_SIZE = 7.dp
private val COLOUR_DOT_SIZE_MORE_BEYOND = 4.dp
// More colours than this and the tile's dots show a window of them; the pop-up still lists every one.
internal const val MAX_COLOUR_DOTS = 3
// A locked tile's style shows through its scrim; the padlock over it is faded to match.
private const val LOCKED_SCRIM_ALPHA = 0.55f
private const val LOCKED_PADLOCK_ALPHA = 0.8f

/**
 * Lets a player pick, rather than read, the option for each independently swappable piece of table
 * art - [DiceStyle], [DiceCupStyle], [DiceMat], [TableBackground] and [ScoreFrame]. One [Card] per category, a
 * horizontally scrolling row of preview tiles inside it, so a category isn't stuck at whatever tile
 * count fits one page width once more options are added.
 *
 * Each tile is one [StyleFamily] - a shape or pattern - rather than one colour of it: tapping it
 * picks that style, and long-pressing a style that comes in more than one colour pops up a
 * scrollable row of previews, one per colour, to pick from. A style's tile shows the colour picked for it, or its first colour if it isn't the current
 * pick; a row of colour dots along its bottom edge is the cue that it has more than one.
 *
 * A style that hasn't been unlocked yet (see [StyleUnlock]) is covered by a padlock and can't be
 * picked; long-pressing it explains what unlocks it. A secret one ([StyleUnlock.hiddenWhileLocked])
 * isn't shown at all until it's unlocked. A saved pick whose style is locked shows the
 * category's default as picked instead, since that's what the game draws in its place.
 *
 * Mat and background are separate categories - each previews only its own brush (the mat's own
 * [DiceMat.DiceTrayDecoration] shows up on its tile too), not the two composed together, since
 * they're independently selectable rather than a single paired option. All the categories'
 * tiles are sized to fit on one screen without scrolling vertically, and the tile row within a
 * category scrolls horizontally; on a screen too short for all four (a small phone, a large font)
 * the page scrolls vertically too.
 *
 * Each category's title has a small gallery toggle at its end: on, that card shows every one of its
 * tiles at once, full size, in a grid the page scrolls down through
 * - for seeing a whole category without scrolling sideways through it.
 */
@Composable
fun StylesScreen(viewModel: StylesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val saved by viewModel.savedStyles.collectAsStateWithLifecycle()
    // A long-pressed styles banner (see StyleScrollRequests) asks for its styles' rows to scroll to them and
    // flash them gold. Read here as the screen first composes, so a fresh arrival opens already in place.
    var focus by remember { mutableStateOf(StyleScrollRequests.pending()) }
    LaunchedEffect(Unit) {
        StyleScrollRequests.requests.collect { request ->
            focus = request
            StyleScrollRequests.consumePending()
        }
    }
    StylesScaffold(
        picks = saved,
        focus = focus,
        onBack = onBack,
        onSelect = { category ->
            when (category) {
                StyleCategory.DICE -> viewModel::setDiceStyleId
                StyleCategory.DICE_CUP -> viewModel::setDiceCupStyleId
                StyleCategory.MAT -> viewModel::setDiceMatId
                StyleCategory.BACKGROUND -> viewModel::setTableBackgroundId
                StyleCategory.FRAME -> viewModel::setScoreFrameId
            }
        },
        modifier = modifier,
    )
}

/**
 * The Styles screen with its picks: split from [StylesScreen], which has the view model, so
 * [StylesWarmUp] can build the real thing - [driftingDice] false there (see [ScreenScaffold]).
 */
@Composable
private fun StylesScaffold(
    picks: SavedStyles?,
    focus: StyleScrollRequest?,
    onBack: () -> Unit,
    onSelect: (StyleCategory) -> (String) -> Unit,
    modifier: Modifier = Modifier,
    driftingDice: Boolean = true,
    pageScale: Float? = null,
    onPageMeasured: ((PageMeasure) -> Unit)? = null,
) {
    ScreenScaffold(
        title = stringResource(Res.string.styles_title),
        onBack = onBack,
        modifier = modifier,
        scrollable = false,
        driftingDice = driftingDice,
    ) {
        // Nothing until the saved picks have loaded, so each row can open scrolled to its real pick.
        // They're normally in already (AppContainer.savedStyles), so the page has them from its first frame.
        StylesPage(picks = picks ?: return@ScreenScaffold, focus = focus, onSelect = onSelect, scale = pageScale, onMeasured = onPageMeasured)
    }
}

/**
 * Everything under the Styles screen's app bar: a card per category, the page scrolling when they don't fit, and its scrollbar.
 * The cards are drawn at the size [StylesWarmUp] worked out and saved for this screen ([PageFit]) - or at [scale], for the warm-up's own
 * copies, which report their height through [onMeasured].
 */
@Composable
private fun ColumnScope.StylesPage(
    picks: SavedStyles,
    focus: StyleScrollRequest?,
    onSelect: (StyleCategory) -> (String) -> Unit,
    scale: Float? = null,
    onMeasured: ((PageMeasure) -> Unit)? = null,
) {
    // One text measurer for every tile's name, kept across openings - see rememberTileLabelMeasurer.
    val labelMeasurer = rememberTileLabelMeasurer()
    CompositionLocalProvider(LocalTileLabelMeasurer provides labelMeasurer) {
        // Sized to fit one screen, but free to scroll when it can't - a small phone, a large font,
        // an open gallery - rather than cutting the last category off out of reach.
        val pageScroll = rememberScrollState()
        val density = LocalDensity.current
        val fitKey = PageFitKey(LocalWindowInfo.current.containerSize, density.density, density.fontScale)
        // Read once: the size the page opens at is the size it keeps, even if a warm-up finishes meanwhile.
        val cardsScale = scale ?: remember(fitKey) { PageFit.decode(picks.stylesPageFit)?.takeIf { it.key == fitKey }?.scale ?: 1f }
        // Smaller dp for the cards, the same sp: tiles, art and gaps shrink, but no text does.
        val cardsDensity = remember(density, cardsScale) {
            if (cardsScale == 1f) density else Density(density.density * cardsScale, density.fontScale / cardsScale)
        }
        if (onMeasured != null) {
            LaunchedEffect(fitKey, cardsScale) {
                // Once it's laid out at this scale - a frame on, so not the size it was before - and has a
                // viewport and an end to its scroll.
                withFrameNanos { }
                val (overflow, viewport) = snapshotFlow { pageScroll.maxValue to pageScroll.viewportSize }
                    .first { (overflow, viewport) -> viewport > 0 && overflow != Int.MAX_VALUE }
                onMeasured(PageMeasure(content = overflow + viewport, viewport = viewport))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            CompositionLocalProvider(LocalDensity provides cardsDensity) {
                Column(modifier = Modifier.verticalScroll(pageScroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (category in StyleCategory.entries) {
                        StyleCategorySection(category = category, picks = picks, onSelect = onSelect(category), focus = focus)
                    }
                }
            }
            // In the page's right-hand margin, beside the cards rather than over them - see VerticalScrollbar.
            VerticalScrollbar(
                scrollState = pageScroll,
                width = PAGE_MARGIN,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = PAGE_MARGIN),
            )
        }
    }
}

/** What a page measured: its cards' whole height and the room it has for them, in px. */
private class PageMeasure(val content: Int, val viewport: Int)

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
// phone (~369 x 816dp), where the page needs about 79%.
private const val MIN_PAGE_FIT_SCALE = 0.75f
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

// How long a tile a banner brought you to stays flashed gold, and how long it takes to fade in and out - as an achievement row's.
private const val STYLE_FLASH_HOLD_MILLIS = 900L
private const val STYLE_FLASH_TRANSITION_MILLIS = 400

// How strongly the gold washes over a mat or background tile, whose art would otherwise hide it.
private const val FLASH_OVER_ART_ALPHA = 0.55f

// ScreenScaffold's side margin, which the page's scrollbar sits in.
private val PAGE_MARGIN = 20.dp

/** The Styles screen's categories, in its order. */
private enum class StyleCategory { DICE, DICE_CUP, MAT, BACKGROUND, FRAME }

// The gap between tiles in a row.
private val TILE_SPACING = 12.dp

/**
 * The families a row shows, in its order: every unlocked one, then the locked ones - less any
 * secret one, which isn't so much as hinted at until it's earned - each in the catalog's own order.
 */
private fun <T : TableArt> StyleCatalog<T>.shownFamilies(achievements: AchievementsState): Pair<List<StyleFamily<T>>, List<StyleFamily<T>>> {
    val (unlocked, locked) = families.partition { it.unlock.isMet(achievements) }
    return unlocked to locked.filterNot { it.unlock.hiddenWhileLocked }
}

/** The page's measurer for tiles' names - see [TileLabel] - or null to lay each one out afresh as a plain [Text]. */
private val LocalTileLabelMeasurer = compositionLocalOf<TextMeasurer?> { null }

/**
 * The one measurer for tiles' names, kept for the life of the process rather than the page's, so
 * each name is laid out once - by [StylesWarmUp] on the menu, normally - and every later opening of
 * the page finds it already done. A new one only if the text would lay out differently: another
 * density (font scale included) or layout direction, or new fonts.
 */
@Composable
private fun rememberTileLabelMeasurer(): TextMeasurer {
    val key = TileLabelMeasurerKey(LocalFontFamilyResolver.current, LocalDensity.current, LocalLayoutDirection.current)
    val kept = tileLabelMeasurer
    if (kept != null && kept.first == key) return kept.second
    return TextMeasurer(key.fontFamilyResolver, key.density, key.layoutDirection, TILE_LABEL_CACHE_SIZE)
        .also { tileLabelMeasurer = key to it }
}

private data class TileLabelMeasurerKey(val fontFamilyResolver: FontFamily.Resolver, val density: Density, val layoutDirection: LayoutDirection)

// Only ever used on the main thread.
private var tileLabelMeasurer: Pair<TileLabelMeasurerKey, TextMeasurer>? = null

// Every tile's name on the page, however many styles there are, with room to spare.
private val TILE_LABEL_CACHE_SIZE: Int by lazy { StyleCatalogs.sumOf { it.families.size } + 16 }

/**
 * A tile's [name] under its preview, drawn exactly as a `labelSmall` [Text] would draw it and read by
 * a screen reader exactly as one would be. Laying out even a word of text was about a third of what a
 * tile cost to build, so with [LocalTileLabelMeasurer] each name's layout is made once for the page and
 * just drawn after that. See .claude/BENCHMARKS.md.
 */
@Composable
private fun TileLabel(name: String) {
    val measurer = LocalTileLabelMeasurer.current
    val style = MaterialTheme.typography.labelSmall
    if (measurer == null) {
        Text(text = name, style = style)
        return
    }
    // As Text colours it: the style's own colour, or the content colour where it has none.
    val colour = style.color.takeOrElse { LocalContentColor.current }
    val layout = measurer.measure(text = name, style = style.copy(color = colour))
    val size = with(LocalDensity.current) { DpSize(layout.size.width.toDp(), layout.size.height.toDp()) }
    Canvas(
        modifier = Modifier
            .size(size)
            // What a Text would say: its words, as a text node of their own.
            .semantics { text = AnnotatedString(name) },
    ) {
        drawText(layout)
    }
}

/** How many tiles [category]'s row has. */
private fun StyleCategory.tileCount(achievements: AchievementsState): Int {
    val catalog = when (this) {
        StyleCategory.DICE -> DiceStyles
        StyleCategory.DICE_CUP -> DiceCupStyles
        StyleCategory.MAT -> DiceMats
        StyleCategory.BACKGROUND -> TableBackgrounds
        StyleCategory.FRAME -> ScoreFrames
    }
    val (unlocked, locked) = catalog.shownFamilies(achievements)
    return unlocked.size + locked.size
}

/**
 * One category's card - its title and its tiles - as the Styles screen shows it, and as [StylesWarmUp]
 * draws it. Its tiles are a scrolling row, or every one of them at once in a grid while its title's
 * gallery toggle is on.
 */
@Composable
private fun StyleCategorySection(
    category: StyleCategory,
    picks: SavedStyles,
    onSelect: (String) -> Unit,
    warmUp: IntRange? = null,
    focus: StyleScrollRequest? = null,
) {
    val achievements = picks.achievements
    var gallery by rememberSaveable { mutableStateOf(false) }
    @Composable
    fun CategoryCard(title: String, content: @Composable ColumnScope.() -> Unit) =
        StyleCategoryCard(title = title, gallery = gallery, onGalleryChange = { gallery = it }, content = content)
    when (category) {
        StyleCategory.DICE -> CategoryCard(title = stringResource(Res.string.styles_category_dice)) {
            val roll = rememberDicePickRoll()
            StyleFamilyTiles(
                catalog = DiceStyles,
                selectedId = picks.diceStyleId,
                achievements = achievements,
                onSelect = { id ->
                    onSelect(id)
                    roll.start(id)
                },
                previewSize = DpSize(DICE_PREVIEW_SIZE, DICE_PREVIEW_SIZE),
                backgroundBrush = { SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh) },
                gallery = gallery,
                warmUp = warmUp,
                buildStagger = category.ordinal,
                focus = focus,
            ) { style ->
                DicePreview(style, roll = roll.takeIf { it.dieId == style.id })
            }
        }

        StyleCategory.DICE_CUP -> CategoryCard(title = stringResource(Res.string.styles_category_dice_cup)) {
            val shake = rememberCupPickShake()
            StyleFamilyTiles(
                catalog = DiceCupStyles,
                selectedId = picks.diceCupStyleId,
                achievements = achievements,
                onSelect = { id ->
                    onSelect(id)
                    shake.start(id)
                },
                previewSize = DpSize(CUP_PREVIEW_WIDTH, CUP_PREVIEW_HEIGHT),
                backgroundBrush = { SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh) },
                gallery = gallery,
                warmUp = warmUp,
                buildStagger = category.ordinal,
                focus = focus,
            ) { style ->
                // One cup per style, not one per tile: a tile showing another colour of the same
                // family would otherwise inherit the last one's state - the Flowerpot's plant
                // growing in from the bare pot's, the Magician's rabbit stuck at the plain hat's peek.
                key(style.id) {
                    // Forced still under reduced motion, as in a game (and off screen - see TileSlot).
                    val moving = shake.cupId == style.id && !LocalReduceMotion.current
                    style.Cup(
                        rolling = moving && shake.shaking,
                        tilted = moving && !shake.shaking,
                        modifier = Modifier.size(
                            width = (style.shape.gridWidth * CUP_ART_SCALE).dp,
                            height = (style.shape.gridHeight * CUP_ART_SCALE).dp,
                        ),
                    )
                }
            }
        }

        StyleCategory.MAT -> CategoryCard(title = stringResource(Res.string.styles_category_mat)) {
            StyleFamilyTiles(
                catalog = DiceMats,
                selectedId = picks.diceMatId,
                achievements = achievements,
                onSelect = onSelect,
                previewSize = DpSize(MAT_PREVIEW_WIDTH, MAT_PREVIEW_HEIGHT),
                backgroundBrush = { mat -> mat.diceTrayBrush },
                gallery = gallery,
                warmUp = warmUp,
                buildStagger = category.ordinal,
                focus = focus,
                flashOverArt = true,
            ) { mat ->
                mat.DiceTrayDecoration(modifier = Modifier.matchParentSize())
            }
        }

        StyleCategory.BACKGROUND -> CategoryCard(title = stringResource(Res.string.styles_category_background)) {
            StyleFamilyTiles(
                catalog = TableBackgrounds,
                selectedId = picks.tableBackgroundId,
                achievements = achievements,
                onSelect = onSelect,
                previewSize = DpSize(BACKGROUND_PREVIEW_WIDTH, BACKGROUND_PREVIEW_HEIGHT),
                backgroundBrush = { background -> background.scoreAreaBrush },
                gallery = gallery,
                warmUp = warmUp,
                buildStagger = category.ordinal,
                focus = focus,
                flashOverArt = true,
            ) { background ->
                background.Animate()
                Canvas(modifier = Modifier.matchParentSize()) { with(background) { drawScoreAreaDecoration() } }
            }
        }

        StyleCategory.FRAME -> CategoryCard(title = stringResource(Res.string.styles_category_frame)) {
            StyleFamilyTiles(
                catalog = ScoreFrames,
                selectedId = picks.scoreFrameId,
                achievements = achievements,
                onSelect = onSelect,
                previewSize = DpSize(FRAME_PREVIEW_WIDTH, FRAME_PREVIEW_HEIGHT),
                backgroundBrush = { SolidColor(MaterialTheme.colorScheme.background) },
                gallery = gallery,
                warmUp = warmUp,
                buildStagger = category.ordinal,
                focus = focus,
            ) { frame ->
                FramePreview(frame, modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/**
 * Which cup on the Styles screen is rolling, if any: the one just picked, shown doing what it does in a
 * game so a player sees how it moves before playing with it - shaken for [CUP_SHAKE_MILLIS] ([shaking]),
 * then tipped over as if pouring the dice out, left lying there for [PICKED_CUP_TIPPED_MILLIS] (long
 * enough for a chest's lid to fly open and settle), and stood back up. A new pick (or the same one
 * again) starts it afresh; none at all under reduced motion. No sound or buzz.
 */
@Stable
private class CupPickShake(private val scope: CoroutineScope, private val lifecycle: Lifecycle, private val reduceMotion: Boolean) {
    /** The cup rolling, or null when none is. */
    var cupId by mutableStateOf<String?>(null)
        private set

    /** Whether [cupId] is still being shaken; once not, it's tipped over. */
    var shaking by mutableStateOf(false)
        private set
    private var job: Job? = null

    fun start(id: String) {
        if (reduceMotion) return
        job?.cancel()
        cupId = id
        shaking = true
        job = scope.launch {
            lifecycle.delayWhileResumed(CUP_SHAKE_MILLIS)
            shaking = false
            lifecycle.delayWhileResumed(PICKED_CUP_TIPPED_MILLIS)
            cupId = null
        }
    }
}

// How long a cup picked on the Styles screen lies tipped over after its shake before standing back up.
private const val PICKED_CUP_TIPPED_MILLIS = 1_500L

@Composable
private fun rememberCupPickShake(): CupPickShake {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduceMotion = LocalReduceMotion.current
    return remember(scope, lifecycle, reduceMotion) { CupPickShake(scope, lifecycle, reduceMotion) }
}

/**
 * Which die on the Styles screen is rolling, if any: the one just picked, tumbled in place through
 * all six faces ([DIE_PICK_ROLL_FACES]), each for [DIE_PICK_ROLL_FACE_MILLIS], to land back on the 5
 * its tile shows - so a player sees the style's every face before playing with it. A new
 * pick (or the same one again) starts it afresh; none at all under reduced motion. No sound or buzz.
 */
@Stable
private class DicePickRoll(private val scope: CoroutineScope, private val reduceMotion: Boolean) {
    /** The die rolling, or null when none is. */
    var dieId by mutableStateOf<String?>(null)
        private set

    /** How far through the roll it is: 0 at rest, then one more for each face it turns over to, to [DIE_PICK_ROLL_FACES]'s size. */
    val progress = Animatable(0f)
    private var job: Job? = null

    fun start(id: String) {
        if (reduceMotion) return
        job?.cancel()
        dieId = id
        job = scope.launch {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = DIE_PICK_ROLL_FACES.size.toFloat(),
                animationSpec = tween(durationMillis = DIE_PICK_ROLL_MILLIS, easing = LinearEasing),
            )
            dieId = null
        }
    }

    /** The face showing [progress] of the way through: the tile's 5, then each in turn, turning over halfway between. */
    fun face(): Int = DIE_PICK_ROLL_FACES.getOrNull(progress.value.roundToInt() - 1) ?: DICE_TILE_FACE
}

// The face a dice tile shows.
private const val DICE_TILE_FACE = 5

/**
 * The faces a picked die turns over to, from the 5 its tile shows: every face once, each beside the
 * one before it on a real die - never its opposite, which a tumbling die can't go straight to (opposite
 * faces add up to 7) - and back to the 5 to finish. See DicePickRollTest.
 */
internal val DIE_PICK_ROLL_FACES = listOf(3, 1, 2, 6, 4, 5)

// How long each face of a picked die's roll shows, every one the same - an easing that started fast
// and slowed to land flashed the first faces past (the 1 hardly showed) - and so the whole roll.
private const val DIE_PICK_ROLL_FACE_MILLIS = 130
private val DIE_PICK_ROLL_MILLIS = DIE_PICK_ROLL_FACE_MILLIS * DIE_PICK_ROLL_FACES.size

// A rolling die turns once round as it goes (so a number or word finishes upright), and lifts by this much at
// its height - no more, or a die turned corner-on (44dp across, 62dp corner to corner) reaches its 72dp tile's edges.
private const val DIE_PICK_ROLL_TURN_DEGREES = 360f
private const val DIE_PICK_ROLL_LIFT = 0.08f

@Composable
private fun rememberDicePickRoll(): DicePickRoll {
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    return remember(scope, reduceMotion) { DicePickRoll(scope, reduceMotion) }
}

/**
 * Turns and lifts a die [roll] of the way through its roll, in the draw phase, so each frame is a
 * repaint, not a recomposition. Nothing while it isn't rolling.
 */
private fun Modifier.rolling(roll: DicePickRoll?): Modifier =
    if (roll == null) this else graphicsLayer {
        // Its own layer, so art that blends onto what's under it (a die's grain) blends onto the die
        // alone - turned, its square layer would otherwise take the tile's corners with it.
        compositingStrategy = CompositingStrategy.Offscreen
        val through = roll.progress.value / DIE_PICK_ROLL_FACES.size
        rotationZ = through * DIE_PICK_ROLL_TURN_DEGREES
        val lift = 1f + DIE_PICK_ROLL_LIFT * sin(PI.toFloat() * through)
        scaleX = lift
        scaleY = lift
    }

/**
 * A dice tile's die - rolling, while it's the one just picked ([roll], see [DicePickRoll]). One with something loose on its faces (googly eyes - see
 * [DiceStyle.pupilTravel]) is moved by the page, as the tray moves it in a game: wherever its tile
 * goes on screen - its row scrolled left or right, or the page up or down - the die goes with it, and
 * its pupils are thrown about and settle. Not under reduced motion, where they sit where they settled.
 */
@Composable
private fun DicePreview(style: DiceStyle, roll: DicePickRoll? = null) {
    // Recomposed only as the face changes; the turning and lifting are drawn (Modifier.rolling).
    val face by remember(roll) { derivedStateOf { roll?.face() ?: DICE_TILE_FACE } }
    val travel = style.pupilTravel?.takeIf { ambientMotion }
    if (travel == null) {
        style.Die(value = face, held = false, modifier = Modifier.size(DIE_ART_SIZE).rolling(roll))
        return
    }
    // Seeded as LocalDieIndex (0 here), so the pupils start where the die would draw them unmoved.
    val motion = remember(travel) { DieMotion(seed = 0, travel = travel) }
    // Acts on the value it was keyed on - see AppLogo's googly dice for why.
    val awake = motion.awake
    LaunchedEffect(motion, awake) {
        if (awake) motion.follow()
    }
    val dieSizePx = with(LocalDensity.current) { DIE_ART_SIZE.toPx() }
    CompositionLocalProvider(LocalDieMotion provides motion) {
        style.Die(
            value = face,
            held = false,
            // Where the die is, in its own sizes, as DieMotion measures it.
            modifier = Modifier.size(DIE_ART_SIZE).onGloballyPositioned { motion.moveTo(it.positionInRoot() / dieSizePx, 0f) }.rolling(roll),
        )
    }
}

/**
 * A frame's tile: a player's tab - a name and a score - framed as it would be on their turn, in player 1's
 * default colour ([ScoreFramePreviewColour]); in a game each player's frame takes their own. Silent to a
 * screen reader: the tile says which frame it is.
 */
@Composable
private fun FramePreview(frame: ScoreFrame, modifier: Modifier = Modifier) {
    val colour = ScoreFramePreviewColour
    Column(
        modifier = modifier
            .padding(top = FRAME_TAB_TOP)
            .size(FRAME_TAB_WIDTH, FRAME_TAB_HEIGHT)
            .drawBehind { with(frame) { drawFrame(colour) } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(Res.string.styles_frame_preview_name), color = colour, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text(text = "24", color = colour, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}

/**
 * Draws every tile of the Styles screen once, out of sight, so the page's code has already run by the
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
    val fitKey = PageFitKey(LocalWindowInfo.current.containerSize, density.density, density.fontScale)
    // Done - unless the screen has changed since the page was measured for it (a resized window, a new
    // font scale), when it's measured again, though nothing else is redone.
    if (picks == null || (stylesWarmedUp && PageFit.decode(picks.stylesPageFit)?.key == fitKey)) return
    var current by remember { mutableStateOf<WarmUpPass?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(fitKey) {
        lifecycle.delayWhileResumed(WARM_UP_DELAY_MILLIS)
        suspend fun pass(next: WarmUpPass) {
            current = next
            // A frame to compose and draw it, then on to the next.
            withFrameNanos { }
            withFrameNanos { }
        }
        if (!stylesWarmedUp) {
            for (category in StyleCategory.entries) {
                for (first in 0 until category.tileCount(picks.achievements) step WARM_UP_TILES_PER_PASS) {
                    pass(WarmUpPass.Tiles(category, first until first + WARM_UP_TILES_PER_PASS))
                }
            }
            for (category in StyleCategory.entries) pass(WarmUpPass.Card(category))
            // The screen's frame on its own first, then with the page in it: each its own first-time code.
            pass(WarmUpPass.Frame)
        }
        // The whole page also measures itself, so it can open already sized to fit (PageFit): full size,
        // then - only if it doesn't fit - at the smallest it may be drawn. Every launch, after the tiles,
        // so the saved fit follows any change to the page.
        suspend fun measured(at: Float): PageMeasure? {
            pageMeasure = null
            current = WarmUpPass.Page(at)
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
        stylesWarmedUp = true
        // Full size where it couldn't be measured, so as not to try again for this screen. Saved only when it's
        // changed: normally it's the same each launch, but a new version of the page can change its height.
        val fit = PageFit(fitKey, if (full == null || smallest == null) 1f else fittedPageScale(full.content, smallest, full.viewport))
        if (PageFit.decode(picks.stylesPageFit) != fit) onPageFit(fit.encode())
        current = null
    }
    val pass = current ?: return
    Box(modifier = modifier.size(1.dp).clipToBounds()) {
        // As big as the page would be, so it lays out as it would there.
        val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        Column(modifier = Modifier.requiredWidth(width).requiredHeight(height)) {
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
    /** The whole screen, its cards at [scale] - see [PageFit]. */
    data class Page(val scale: Float) : WarmUpPass
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

/**
 * A titled group of preview tiles for one swappable category, matching Settings' Card sections. The
 * title's [GalleryToggle] switches [gallery] - every tile at once, rather than a scrolling row.
 */
@Composable
private fun StyleCategoryCard(title: String, gallery: Boolean, onGalleryChange: (Boolean) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        // The title's line, with the toggle centred on it.
        Row(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .semantics { heading() },
            )
            GalleryToggle(title = title, gallery = gallery, onGalleryChange = onGalleryChange)
        }
        content()
    }
}

/**
 * The small grid icon at the end of a category's title: on, the category shows every tile at once.
 * Outlined and quiet while off, filled in the app's gold while on. No taller than the title's own line,
 * so adding it didn't move anything; a touch near it still lands on it (Compose widens a small target's
 * hit area to the minimum touch size). A switch to a screen reader - "Dice gallery, on".
 */
@Composable
private fun GalleryToggle(title: String, gallery: Boolean, onGalleryChange: (Boolean) -> Unit) {
    val galleryName = stringResource(Res.string.styles_gallery_cd, title)
    val galleryAction = stringResource(if (gallery) Res.string.styles_gallery_row_action else Res.string.styles_gallery_all_action, title)
    // Its room in the title's line: no taller than the icon, so the header is no taller for it.
    Box(modifier = Modifier.padding(end = 2.dp).size(width = GALLERY_TOGGLE_SIZE, height = GALLERY_ICON_SIZE)) {
        // The control itself, a square centred on the icon - so the press highlight is too - and
        // let run past the title's line above and below.
        Box(
            modifier = Modifier
                .requiredSize(GALLERY_TOGGLE_SIZE)
                // The icon is the whole of it, named here rather than on the icon so it's one stop.
                .clearAndSetSemantics {
                    contentDescription = galleryName
                    role = Role.Switch
                    toggleableState = ToggleableState(gallery)
                    onClick(label = galleryAction) {
                        onGalleryChange(!gallery)
                        true
                    }
                }
                .toggleable(
                    value = gallery,
                    interactionSource = null,
                    indication = ripple(bounded = false, radius = GALLERY_HIGHLIGHT_RADIUS),
                    role = Role.Switch,
                    onValueChange = onGalleryChange,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (gallery) Icons.Filled.GridView else Icons.Outlined.GridViewOutlined,
                contentDescription = null,
                tint = if (gallery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(GALLERY_ICON_SIZE),
            )
        }
    }
}

// The toggle's icon - no taller than the title's 20sp line, so the header is no taller for it.
private val GALLERY_ICON_SIZE = 20.dp
// The control - what a touch lands on - a square this size, centred on the icon.
private val GALLERY_TOGGLE_SIZE = 40.dp
// The press highlight's radius, centred on the icon: smaller than the control, as the first row of
// tiles starts 18dp below the icon's centre (half the title's 20dp line, then 2 + 6dp of padding).
private val GALLERY_HIGHLIGHT_RADIUS = 16.dp

/**
 * One [StyleFamilyTile] per family in [catalog], in a horizontally scrolling row with a
 * [HorizontalScrollbar] under it: every unlocked style first, then the locked ones, each group in
 * the catalog's own order. [selectedId] is the saved pick, shown as the default instead while its
 * style is locked. With [warmUp], just those tiles instead, in a plain row - see [StylesWarmUp].
 *
 * With [gallery], the same tiles wrapped onto as many lines as they need instead, every one on screen at once and the page
 * scrolling down through them. They're moved between the two, not built again: switching costs only
 * the tiles not built yet (all of them are, on the first switch), and keeps what each was doing - a
 * cup mid-shake, an open colour pop-up - and where the row was scrolled to.
 *
 * Every tile is built and kept, but not all at once when the page opens - composing every tile in
 * every category in one go held up the page's first open for several frames. See the build order
 * below, and .claude/BENCHMARKS.md for why this isn't a lazy row.
 */
@Composable
private fun <T : TableArt> StyleFamilyTiles(
    catalog: StyleCatalog<T>,
    selectedId: String,
    achievements: AchievementsState,
    onSelect: (String) -> Unit,
    previewSize: DpSize,
    backgroundBrush: @Composable (T) -> Brush,
    gallery: Boolean = false,
    warmUp: IntRange? = null,
    buildStagger: Int = 0,
    focus: StyleScrollRequest? = null,
    flashOverArt: Boolean = false,
    preview: @Composable BoxScope.(T) -> Unit,
) {
    // The styles of this category a banner asked to be shown: scrolled to, and flashed for a moment.
    var flashing by remember { mutableStateOf(false) }
    val focusNames = remember(focus, catalog) { focus?.styles.orEmpty().filter { it.categoryNoun.key == catalog.noun.key }.map { it.name.key }.toSet() }
    val shownSelectedId = catalog.unlockedById(selectedId, achievements).id
    val (unlocked, shownLocked) = catalog.shownFamilies(achievements)

    @Composable
    fun Tile(index: Int) {
        if (index < unlocked.size) {
            StyleFamilyTile(
                unlocked[index], achievements, shownSelectedId, onSelect, previewSize, backgroundBrush, preview, index,
                flashing = flashing && unlocked[index].name.key in focusNames, flashOverArt = flashOverArt, variantNoun = catalog.variantNoun,
            )
        } else {
            LockedStyleFamilyTile(shownLocked[index - unlocked.size], achievements, previewSize, backgroundBrush, preview, index)
        }
    }

    // StylesWarmUp's pass: just these tiles, side by side, drawn once - see StylesWarmUp.
    if (warmUp != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (index in warmUp) if (index < unlocked.size + shownLocked.size) Tile(index)
        }
        return
    }

    val families = remember(unlocked, shownLocked) { unlocked + shownLocked }
    // Opens with the current pick in the middle. Only the pick the row opened with: the build below
    // is keyed on it, so following a later pick would throw away every other tile and build them all
    // again, flashing the whole row each time a new style was picked.
    // Or, arriving from a styles banner, with the style it unlocked there instead.
    val pickedIndex = remember(families.size) {
        unlocked.indexOfFirst { it.name.key in focusNames }.takeIf { it >= 0 }
            ?: unlocked.indexOfFirst { it.colourOf(shownSelectedId) != null }.coerceAtLeast(0)
    }
    val scrollState = rememberScrollState()

    // The card's width, for spacing the tiles: the row and the gallery space them alike (TileSpacing).
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = remember(maxWidth, previewSize.width, families.size) {
            TileSpacing.of(width = maxWidth - TILES_SIDE_PADDING * 2, slot = previewSize.width, count = families.size)
        }
        Column {

            // Every tile in the row is built, and kept - a plain scrolling row, not a lazy one, so scrolling,
            // however fast, never has to build a tile on the frame it appears, and one scrolled past and back
            // isn't built again. But not all at once: the ones on screen as the page opens are built straight
            // away, and the rest a tile at a time once the page has faded in, nearest the pick first - each
            // shown until then as an empty tile of the same size, so nothing moves as they fill in. Each row
            // starts [buildStagger] frames after the first, so no two rows build on the same frame. (A lazy
            // row's cache window was meant to do this, but built nothing ahead of time: every tile was still
            // built on the frame it scrolled in.) What isn't on screen is held still (see TileSlot), so a
            // tile costs nothing once built.
            val buildOrder = remember(families.size, pickedIndex) { families.indices.sortedBy { abs(it - pickedIndex) } }
            val reach = previewSize.width + spacing.gap
            val windowWidth = LocalWindowInfo.current.containerSize.width
            // The pick and as many either side as can show beside it, centred in the row.
            val openingTiles = with(LocalDensity.current) { ceil(windowWidth / 2f / reach.toPx()).toInt() } * 2 + 1
            // Built nearest the pick first, the built tiles are always one unbroken run - [firstBuilt] to
            // [lastBuilt] - with the unbuilt ones either side of it. Each unbuilt side is one PlaceholderRun,
            // not a placeholder per tile, so opening the page costs the same however many tiles the row has.
            //
            // Even the tiles on screen aren't all built on the page's first frame: that one frame, long enough
            // to stall everything else on screen (the backdrop's drifting dice), is what made opening the page
            // feel slow. It builds just the pick; each frame after adds the next tile either side, until the
            // row is full across the screen - only then is it shown (see revealed below).
            //
            // Under reduced motion, though, there's no fade to hide that build behind, and the row would sit empty
            // for those frames before popping in: the tiles on screen are all built on the first frame instead, so the
            // page opens complete - one slower frame, with nothing else moving for it to stall.
            val reduceMotion = LocalReduceMotion.current
            val opening = remember(buildOrder) { buildOrder.take(openingTiles) }
            var firstBuilt by remember(buildOrder) { mutableIntStateOf(if (reduceMotion) opening.min() else pickedIndex) }
            var lastBuilt by remember(buildOrder) { mutableIntStateOf(if (reduceMotion) opening.max() else pickedIndex) }
            val openingBuilt = remember(buildOrder) { derivedStateOf { lastBuilt - firstBuilt + 1 >= min(openingTiles, families.size) } }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(buildOrder) {
                fun buildNext(index: Int) {
                    if (index < firstBuilt) firstBuilt = index else if (index > lastBuilt) lastBuilt = index
                }
                val rest = buildOrder.drop(openingTiles)
                // The pick is built already; then a tile either side of what's built, a frame at a time (all
                // already built under reduced motion).
                for (pair in opening.drop(1).chunked(2).takeUnless { reduceMotion }.orEmpty()) {
                    withFrameNanos { }
                    pair.forEach(::buildNext)
                }
                if (!reduceMotion) lifecycle.delayWhileResumed(PAGE_CONTENT_FADE_IN_MILLIS.toLong())
                repeat(buildStagger) { withFrameNanos { } }
                for (index in rest) {
                    buildNext(index)
                    repeat(TILE_BUILD_FRAMES) { withFrameNanos { } }
                }
            }

            // The gallery shows every tile, so it builds every one not built yet, all at once.
            LaunchedEffect(gallery) {
                if (gallery) {
                    firstBuilt = 0
                    lastBuilt = families.lastIndex
                }
            }

            // Hidden until it has been scrolled to the current pick, so the page opens already
            // positioned rather than visibly sliding there.
            var revealed by remember { mutableStateOf(false) }
            // Faded in like the rest of the page (ScreenScaffold), not popped in a frame or two after it.
            val rowAlpha by animateFloatAsState(
                targetValue = if (revealed) 1f else 0f,
                animationSpec = if (reduceMotion) snap() else tween(durationMillis = PAGE_CONTENT_FADE_IN_MILLIS),
                label = "styleRowAlpha", // i18n: not translated - an animation label, not shown
            )
            // Where a tile's centre is in the row: every tile takes the same slot, built or not, so it's
            // known before any of them is laid out.
            val density = LocalDensity.current
            fun centrePx(index: Int): Int = with(density) {
                (TILES_SIDE_PADDING + (previewSize.width + spacing.gap) * index + previewSize.width / 2).roundToPx()
            }
            val pickedCentrePx = centrePx(pickedIndex)
            // While the gallery's open, the row (out of sight, its scroll kept) follows the pick, so closing
            // the gallery shows the row centred on it - as the page opens - rather than where it was left.
            val currentIndex = unlocked.indexOfFirst { it.colourOf(shownSelectedId) != null }
            LaunchedEffect(gallery, currentIndex) {
                val viewport = scrollState.viewportSize
                // Not before the row has opened on its pick (see below), which it then does anyway.
                if (gallery && currentIndex >= 0 && viewport > 0) scrollState.scrollTo(centrePx(currentIndex) - viewport / 2)
            }
            LaunchedEffect(Unit) {
                snapshotFlow { openingBuilt.value }.first { it }
                val viewport = snapshotFlow { scrollState.viewportSize }.first { it > 0 }
                scrollState.scrollTo(pickedCentrePx - viewport / 2)
                revealed = true
            }
            // A banner asking for a style while the page is already open: slide the row to it.
            // Then, as an achievement row does, the flash starts once it's there and holds for a moment.
            LaunchedEffect(focus) {
                flashing = false
                val index = unlocked.indexOfFirst { it.name.key in focusNames }
                if (index < 0 || focus == null) return@LaunchedEffect
                snapshotFlow { revealed }.first { it }
                val target = centrePx(index) - scrollState.viewportSize / 2
                if (focus.animate && !reduceMotion) scrollState.animateScrollTo(target) else scrollState.scrollTo(target)
                flashing = true
                lifecycle.delayWhileResumed(STYLE_FLASH_HOLD_MILLIS)
                flashing = false
            }

            // Each tile's composition, movable between the row and the gallery - see the doc comment. Handed
            // the tile to draw each time, so it draws with the current pick, not the one it was first built with.
            val slots = remember(families) {
                families.map { movableContentOf { tile: @Composable () -> Unit -> TileSlot(tile) } }
            }

            if (gallery) {
                // Read as the row is - its place in a set of this many - whichever way the grid wraps it.
                GalleryGrid(
                    slotWidth = previewSize.width,
                    spacing = spacing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = families.size) }
                        .padding(start = TILES_SIDE_PADDING, top = 6.dp, end = TILES_SIDE_PADDING, bottom = 12.dp),
                ) {
                    for (index in families.indices) {
                        slots[index] { Tile(index) }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(rowAlpha)
                        // Its place in a row of this many, as a lazy row would say it.
                        .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = families.size) }
                        .horizontalScroll(scrollState)
                        .padding(horizontal = TILES_SIDE_PADDING, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing.gap),
                ) {
                    if (firstBuilt > 0) PlaceholderRun(firstBuilt, previewSize, spacing.gap)
                    for (index in firstBuilt..lastBuilt) {
                        // Each tile in its preview's width, as in the gallery: a wider name spills into the gaps.
                        Box(modifier = Modifier.width(previewSize.width).wrapContentWidth(unbounded = true)) {
                            slots[index] { Tile(index) }
                        }
                    }
                    if (lastBuilt < families.lastIndex) PlaceholderRun(families.lastIndex - lastBuilt, previewSize, spacing.gap)
                }

                HorizontalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier.padding(start = TILES_SIDE_PADDING, end = TILES_SIDE_PADDING, bottom = 10.dp),
                )
            }
        }
    }
}

/**
 * How a category's tiles are spaced across a card [width] wide, in its row and its gallery alike, so the
 * two match: each tile takes its preview's [slot] (a wider name spills into the gaps either side), and
 * the gap between them is [TILE_SPACING] where the gallery's lines have room for it, or less - down to
 * [GALLERY_MIN_TILE_SPACING] - where closing up fits another tile on each. [columns] is how many to a
 * gallery line.
 */
@Immutable
internal data class TileSpacing(val columns: Int, val gap: Dp) {
    companion object {
        fun of(width: Dp, slot: Dp, count: Int): TileSpacing {
            val columns = ((width + GALLERY_MIN_TILE_SPACING) / (slot + GALLERY_MIN_TILE_SPACING)).toInt().coerceIn(1, count.coerceAtLeast(1))
            val gap = if (columns > 1) minOf(TILE_SPACING, (width - slot * columns) / (columns - 1)) else TILE_SPACING
            return TileSpacing(columns, gap)
        }
    }
}

/**
 * A category's tiles in a gallery, every one laid out at once, left-aligned like the row and spaced as
 * it is ([spacing]): [TileSpacing.columns] to a line, each in its preview's [slotWidth] with
 * [TileSpacing.gap] between. A name wider than its preview (at a large font, say) centres under it and
 * spills a little into the gaps, rather than pushing the line's last tile onto the next. Lines are as
 * tall as their tallest tile, [TILE_SPACING] apart.
 */
@Composable
private fun GalleryGrid(slotWidth: Dp, spacing: TileSpacing, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        if (placeables.isEmpty()) return@Layout layout(width, 0) {}
        val slot = slotWidth.toPx()
        val step = slot + spacing.gap.toPx()
        val lineGap = TILE_SPACING.roundToPx()
        val lines = placeables.chunked(spacing.columns)
        val lineHeights = lines.map { line -> line.maxOf { it.height } }
        layout(width, lineHeights.sum() + lineGap * (lines.size - 1)) {
            var y = 0
            lines.forEachIndexed { l, line ->
                line.forEachIndexed { c, tile -> tile.place(x = (c * step + (slot - tile.width) / 2).roundToInt(), y = y) }
                y += lineHeights[l] + lineGap
            }
        }
    }
}

// The least room between tiles side by side in a gallery, when closing up gains a column: four dice or
// cups across a 360dp phone need 4 x 72dp in the ~304dp its card leaves inside TILES_SIDE_PADDING - gaps of 5dp.
private val GALLERY_MIN_TILE_SPACING = 4.dp

// The room either side of a card's tiles, row or gallery alike (and the row's scrollbar): what lets
// four dice or cups fit across a 360dp phone's gallery (20dp page padding each side), and still wide
// enough for the longest name to spill into.
private val TILES_SIDE_PADDING = 8.dp

// How many frames apart a row builds its tiles after the page opens - see StyleFamilyTiles.
private const val TILE_BUILD_FRAMES = 4

/** One built tile in a Styles row or gallery, held still - as under reduced motion - while it isn't on screen. */
@Composable
private fun TileSlot(tile: @Composable () -> Unit) {
    var onScreen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            // Clipped by the row or the page: nothing left of it when it's scrolled out of sight.
            onScreen = coordinates.boundsInRoot().width > 0f
        },
    ) {
        CompositionLocalProvider(LocalReduceMotion provides (LocalReduceMotion.current || !onScreen)) {
            tile()
        }
    }
}

/**
 * [count] tiles not built yet (see StyleFamilyTiles), side by side: each an empty tile of the same
 * size, drawn in one go and taking exactly the room the tiles will, so nothing moves as they fill in.
 * No names - laying those out is most of what a tile costs, and this is what keeps the page's
 * opening frame from growing with the number of styles. Silent to a screen reader, which finds each
 * tile once it's built.
 */
@Composable
private fun PlaceholderRun(count: Int, previewSize: DpSize, gap: Dp) {
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .clearAndSetSemantics {}
            .size(width = previewSize.width * count + gap * (count - 1), height = previewSize.height),
    ) {
        val tile = Size(previewSize.width.toPx(), previewSize.height.toPx())
        val step = (previewSize.width + gap).toPx()
        val corner = CornerRadius(PLACEHOLDER_CORNER.toPx())
        val border = 1.dp.toPx()
        repeat(count) { i ->
            val left = i * step
            drawRoundRect(fill, topLeft = Offset(left, 0f), size = tile, cornerRadius = corner)
            drawRoundRect(
                outline,
                topLeft = Offset(left + border / 2, border / 2),
                size = Size(tile.width - border, tile.height - border),
                cornerRadius = corner,
                style = Stroke(border),
            )
        }
    }
}

private val PLACEHOLDER_CORNER = 16.dp

/**
 * One style: a rendered preview plus its name, with a check badge when one of its colours is the
 * current pick. Tapping it picks the colour it's showing; long-pressing it, when it has more than
 * one colour, pops up a scrollable row of that style's colours, each as its own preview, to pick
 * one. [previewSize] is the preview's own size, which differs by category (a die's is square, a
 * cup's is tall, the mat's is wide).
 */
@Composable
private fun <T : TableArt> StyleFamilyTile(
    family: StyleFamily<T>,
    achievements: AchievementsState,
    selectedId: String,
    onSelect: (String) -> Unit,
    previewSize: DpSize,
    backgroundBrush: @Composable (T) -> Brush,
    preview: @Composable BoxScope.(T) -> Unit,
    position: Int,
    flashing: Boolean = false,
    flashOverArt: Boolean = false,
    variantNoun: StringResource = Res.string.style_variant_colour,
) {
    val colours = family.colours
    val familyName = stringResource(family.name)
    val selectLabel = stringResource(Res.string.styles_select_action)
    val chooseVariantLabel = stringResource(Res.string.styles_choose_variant_action, familyName, stringResource(variantNoun))
    val picked = colours.firstOrNull { it.style.id == selectedId }
    val shown = picked ?: colours.firstOrNull { it.isAvailable(achievements) } ?: colours.first()
    val hasColours = colours.size > 1
    val shownDescription = if (hasColours) stringResource(Res.string.styles_family_colour_cd, familyName, stringResource(shown.name)) else familyName
    var choosingColour by remember { mutableStateOf(false) }
    var lockedVariantRequirement by remember { mutableStateOf<String?>(null) }

    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    // Picking a tile that's partly scrolled off the card's edge scrolls it just far enough to show whole.
    val select: (String) -> Unit = { id ->
        onSelect(id)
        scope.launch { bringIntoView.bringIntoView() }
    }
    Column(
        modifier = Modifier.bringIntoViewRequester(bringIntoView),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The pop-up's anchor: DropdownMenu positions itself against its parent, so the preview and
        // the pop-up share this Box rather than the pop-up hanging off the label below.
        Box {
            StylePreview(
                style = shown.style,
                size = previewSize,
                selected = picked != null,
                backgroundBrush = backgroundBrush,
                preview = preview,
                flashing = flashing,
                flashOverArt = flashOverArt,
                modifier = Modifier
                    // One radio button in a row of them: the style (and the colour showing, if it has
                    // several), whether it's the pick, and the colour chooser as an action, so neither
                    // the check badge nor the colour dots is a stop of its own. Ahead of the click handling,
                    // not behind it: semantics cleared only apply to what sits further in.
                    .clearAndSetSemantics {
                        contentDescription = shownDescription
                        role = Role.RadioButton
                        selected = picked != null
                        collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = position, columnSpan = 1)
                        onClick(label = selectLabel) {
                            select(shown.style.id)
                            true
                        }
                        if (hasColours) {
                            onLongClick(label = chooseVariantLabel) {
                                choosingColour = true
                                true
                            }
                        }
                    }
                    .combinedClickable(
                        onClick = { select(shown.style.id) },
                        onLongClick = if (hasColours) ({ choosingColour = true }) else null,
                        onLongClickLabel = if (hasColours) chooseVariantLabel else null,
                    ),
            ) {
                if (hasColours) {
                    ColourDots(
                        colours = colours,
                        shown = shown,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
                    )
                }
            }

            // DropdownMenu for its anchoring, surface and dismiss handling, holding a row of previews
            // rather than text items: a colour is picked by how it looks, and the style's name is
            // already on the tile. The row scrolls sideways once a style has more colours than fit.
            DropdownMenu(expanded = choosingColour, onDismissRequest = { choosingColour = false }) {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (colour in colours) {
                        val available = colour.isAvailable(achievements)
                        val colourDescription = stringResource(Res.string.styles_family_colour_cd, familyName, stringResource(colour.name))
                        val achievementTitle = colour.secretAchievement?.title?.let { stringResource(it) } ?: ""
                        val lockedRequirement = stringResource(Res.string.styles_unlock_specific, achievementTitle, familyName)
                        StylePreview(
                            style = colour.style,
                            size = previewSize,
                            selected = colour.style.id == selectedId,
                            backgroundBrush = backgroundBrush,
                            preview = preview,
                            modifier = Modifier
                                // No visible name - but a screen reader still needs to say which is which.
                                .clearAndSetSemantics {
                                    contentDescription = colourDescription
                                    role = Role.RadioButton
                                    selected = colour.style.id == selectedId
                                    onClick(label = selectLabel) {
                                        if (available) {
                                            select(colour.style.id)
                                            choosingColour = false
                                        }
                                        true
                                    }
                                }
                                .combinedClickable(
                                    onClick = {
                                        if (available) {
                                            select(colour.style.id)
                                            choosingColour = false
                                        } else {
                                            lockedVariantRequirement = lockedRequirement
                                        }
                                    },
                                    onLongClick = if (!available) {
                                        { lockedVariantRequirement = lockedRequirement }
                                    } else null,
                                ),
                        ) {
                            if (!available) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = LOCKED_SCRIM_ALPHA)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = LOCKED_PADLOCK_ALPHA),
                                        modifier = Modifier.size(previewSize.width / 3),
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
                )
            }
        }
        TileLabel(stringResource(family.name))
    }

    if (lockedVariantRequirement != null) {
        DiceFiveDialog(
            icon = Icons.Filled.Lock,
            title = null,
            message = parseInlineMarkup(
                lockedVariantRequirement!!,
                codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary),
            ),
            confirmLabel = stringResource(Res.string.common_ok),
            onConfirm = { lockedVariantRequirement = null },
            onDismissRequest = { lockedVariantRequirement = null },
        )
    }
}

/**
 * A style that hasn't been unlocked yet: its tile in its first colour, faded under a padlock, so
 * it can't be picked. Tapping it shows a tooltip with the core requirement ([shortRequirement]);
 * long-pressing it opens a dialog with more ([unlockRequirement]).
 */
@Composable
private fun <T : TableArt> LockedStyleFamilyTile(
    family: StyleFamily<T>,
    achievements: AchievementsState,
    previewSize: DpSize,
    backgroundBrush: @Composable (T) -> Brush,
    preview: @Composable BoxScope.(T) -> Unit,
    position: Int,
) {
    var showingRequirement by remember { mutableStateOf(false) }
    val tooltipState = rememberAppTooltipState()
    val scope = rememberCoroutineScope()
    val familyName = stringResource(family.name)
    val lockedText = stringResource(Res.string.styles_locked_spoken)
    val showUnlockLabel = stringResource(Res.string.styles_show_unlock_action, familyName)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Not the tooltip's own long press (enabled = false): that opens the dialog, and a tap shows the tooltip.
        AppTooltip(message = tooltipMarkup(shortRequirement(family)), state = tooltipState, enabled = false) {
            StylePreview(
                style = family.colours.first().style,
                size = previewSize,
                selected = false,
                backgroundBrush = backgroundBrush,
                preview = preview,
                modifier = Modifier
                    // A tap shows the short requirement and a long press the dialog. A tooltip isn't announced, so
                    // a screen reader's one action is the dialog, which says the same and more.
                    .clearAndSetSemantics {
                        contentDescription = familyName
                        stateDescription = lockedText
                        role = Role.Button
                        collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = position, columnSpan = 1)
                        onClick(label = showUnlockLabel) {
                            showingRequirement = true
                            true
                        }
                    }
                    .combinedClickable(
                        onClick = { scope.launch { tooltipState.show() } },
                        onLongClick = { showingRequirement = true },
                        onLongClickLabel = showUnlockLabel,
                    ),
            ) {
                // Translucent, so the style still shows through - the lock says "not yet", not "hidden".
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = LOCKED_SCRIM_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = LOCKED_PADLOCK_ALPHA),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        TileLabel(stringResource(family.name))
    }

    if (showingRequirement) {
        DiceFiveDialog(
            icon = Icons.Filled.Lock,
            title = null,
            // Backticks mark what to highlight - the gold the rules pages use.
            message = parseInlineMarkup(
                unlockRequirement(family, achievements),
                codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary),
            ),
            confirmLabel = stringResource(Res.string.common_ok),
            onConfirm = { showingRequirement = false },
            onDismissRequest = { showingRequirement = false },
        )
    }
}

/** Just the core requirement for [family], for the tooltip a tap on its locked tile shows - [tooltipMarkup]'s markup. */
@Composable
private fun shortRequirement(family: StyleFamily<*>): String =
    when (val unlock = family.unlock) {
        StyleUnlock.Free -> stringResource(Res.string.styles_requirement_free)
        is StyleUnlock.AchievementCount -> pluralStringResource(Res.plurals.styles_requirement_count, unlock.count, unlock.count)
        is StyleUnlock.SpecificAchievement -> stringResource(Res.string.styles_requirement_specific, stringResource(unlock.achievement.title))
    }

/**
 * What it takes to unlock [family], and how far along [achievements] is, as the locked tile's pop-up
 * says it - in [parseInlineMarkup]'s markup, with the counts and any achievement's name in backticks
 * so they're highlighted.
 */
@Composable
private fun unlockRequirement(family: StyleFamily<*>, achievements: AchievementsState): String =
    when (val unlock = family.unlock) {
        StyleUnlock.Free -> stringResource(Res.string.styles_unlock_free, family.name)
        is StyleUnlock.AchievementCount ->
            pluralStringResource(Res.plurals.styles_unlock_count, unlock.count, unlock.count, family.name, achievements.countedUnlocks)
        // Never a secret one: its style isn't shown until it's earned (StyleUnlock.hiddenWhileLocked).
        is StyleUnlock.SpecificAchievement -> stringResource(Res.string.styles_unlock_specific, stringResource(unlock.achievement.title), family.name)
    }

/**
 * [style] drawn by [preview] on its [backgroundBrush] at [size], outlined in the app's gold with a
 * check badge when [selected]. [modifier] carries the click handling; [overlay] draws on top.
 */
@Composable
private fun <T : TableArt> StylePreview(
    style: T,
    size: DpSize,
    selected: Boolean,
    backgroundBrush: @Composable (T) -> Brush,
    preview: @Composable BoxScope.(T) -> Unit,
    modifier: Modifier = Modifier,
    flashing: Boolean = false,
    flashOverArt: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(16.dp)
    // The gold flash of a tile a banner brought you to, like an achievement row's: behind the art (the
    // die sits on it), or - for a mat or background, which is the whole tile - washed over it.
    val flash by animateFloatAsState(
        targetValue = if (flashing) 1f else 0f,
        animationSpec = if (LocalReduceMotion.current) snap() else tween(STYLE_FLASH_TRANSITION_MILLIS),
        label = "styleTileFlash", // i18n: not translated - an animation label, not shown
    )
    Box(
        modifier = Modifier
            .size(size)
            // Drawn into a layer of its own, which the device keeps and puts back on screen as it is
            // until the tile itself changes. Without it, anything on the page moving - an animated
            // tile, the backdrop's drift, a row scrolling - had every tile's art drawn over again
            // each frame, more than a frame's work on a phone; now a still tile costs next to nothing
            // and scrolling just moves the layers. See .claude/BENCHMARKS.md.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .clip(shape)
            .then(modifier)
            .background(backgroundBrush(style))
            .drawWithContent {
                if (!flashOverArt) drawRect(GoldAccent, alpha = flash)
                drawContent()
                if (flashOverArt) drawRect(GoldAccent, alpha = flash * FLASH_OVER_ART_ALPHA)
            }
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        preview(style)
        if (selected) {
            SelectedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        overlay()
    }
}

@Composable
private fun SelectedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = stringResource(Res.string.styles_selected_cd),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Which of [count] colours' dots to show when the tile has room for [MAX_COLOUR_DOTS]: all of them,
 * or a window of that many that always includes [shownIndex] - the shown colour is in the middle
 * where it can be, and at an end where it's the first or last colour.
 */
internal fun colourDotRange(count: Int, shownIndex: Int): IntRange {
    if (count <= MAX_COLOUR_DOTS) return 0 until count
    val start = (shownIndex - MAX_COLOUR_DOTS / 2).coerceIn(0, count - MAX_COLOUR_DOTS)
    return start until start + MAX_COLOUR_DOTS
}

/**
 * One dot per colour a style comes in, the one the tile is showing ringed in the app's gold. A style
 * with more colours than fit shows a window of them around the shown one - see [colourDotRange] -
 * and the window's end dots shrink on any side that has more colours beyond it.
 */
@Composable
private fun <T : TableArt> ColourDots(colours: List<StyleColour<T>>, shown: StyleColour<T>, modifier: Modifier = Modifier) {
    val range = colourDotRange(colours.size, colours.indexOf(shown))
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (index in range) {
            val colour = colours[index]
            val moreBeyond = (index == range.first && index > 0) || (index == range.last && index < colours.lastIndex)
            Swatch(
                colour = colour.swatch,
                size = if (moreBeyond) COLOUR_DOT_SIZE_MORE_BEYOND else COLOUR_DOT_SIZE,
                ring = if (colour == shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun Swatch(colour: Color, size: Dp, ring: Color) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colour)
            .border(1.dp, ring, CircleShape),
    )
}

// A preview has no host to scope a view model to - see .claude/UI.md's ViewModelConstructorInComposable gotcha.
@Suppress("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
private fun StylesScreenPreview() {
    DiceFiveTheme {
        StylesScreen(viewModel = StylesViewModel(), onBack = {})
    }
}
