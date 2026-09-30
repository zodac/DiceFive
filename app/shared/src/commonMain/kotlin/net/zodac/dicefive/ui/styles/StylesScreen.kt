package net.zodac.dicefive.ui.styles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.game.style.DieMotion
import net.zodac.dicefive.ui.game.style.LocalDieMotion
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.runtime.key
import androidx.compose.runtime.State
import kotlinx.coroutines.launch
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.HorizontalScrollbar
import net.zodac.dicefive.ui.common.PAGE_CONTENT_FADE_IN_MILLIS
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.common.parseInlineMarkup
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.game.style.DiceCupStyle
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceMat
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyle
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.StyleCatalog
import net.zodac.dicefive.ui.game.style.StyleColour
import net.zodac.dicefive.ui.game.style.StyleFamily
import net.zodac.dicefive.ui.game.style.StyleUnlock
import net.zodac.dicefive.ui.game.style.TableArt
import net.zodac.dicefive.ui.game.style.TableBackground
import net.zodac.dicefive.ui.game.style.TableBackgrounds
import net.zodac.dicefive.ui.theme.DiceFiveTheme

// Kept small enough that all four categories (Dice, Dice Cup, Mat, Background) fit on one screen
// without needing to scroll - see StylesScreen's doc comment.
private val DICE_PREVIEW_SIZE = 72.dp
private val DIE_ART_SIZE = 44.dp
private val CUP_PREVIEW_WIDTH = 72.dp
private val CUP_PREVIEW_HEIGHT = 96.dp
// The cup previews at this fraction of their in-game size, whatever their shape (tall: 42 x 60dp).
private const val CUP_ART_SCALE = 60f / 84f
private val MAT_PREVIEW_WIDTH = 108.dp
private val MAT_PREVIEW_HEIGHT = 72.dp
private val BACKGROUND_PREVIEW_WIDTH = 108.dp
private val BACKGROUND_PREVIEW_HEIGHT = 72.dp
private val COLOUR_DOT_SIZE = 7.dp
private val COLOUR_DOT_SIZE_MORE_BEYOND = 4.dp
// More colours than this and the tile's dots show a window of them; the pop-up still lists every one.
internal const val MAX_COLOUR_DOTS = 3
// A locked tile's style shows through its scrim; the padlock over it is faded to match.
private const val LOCKED_SCRIM_ALPHA = 0.55f
private const val LOCKED_PADLOCK_ALPHA = 0.8f

/**
 * Lets a player pick, rather than read, the option for each independently swappable piece of table
 * art - [DiceStyle], [DiceCupStyle], [DiceMat] and [TableBackground]. One [Card] per category, a
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
 * they're independently selectable rather than a single paired option. All four categories'
 * tiles are sized to fit on one screen without scrolling vertically, and the tile row within a
 * category scrolls horizontally; on a screen too short for all four (a small phone, a large font)
 * the page scrolls vertically too.
 */
@Composable
fun StylesScreen(viewModel: StylesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val saved by viewModel.savedStyles.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Styles",
        onBack = onBack,
        modifier = modifier,
        scrollable = false,
    ) {
        // Nothing until the saved picks have loaded, so each row can open scrolled to its real pick.
        // They're normally in already (AppContainer.savedStyles), so the page has them from its first frame.
        val picks = saved ?: return@ScreenScaffold
        // One text measurer for every tile's name on the page, whose cache keeps each name's layout
        // while the page is open - see TileLabel.
        val labelMeasurer = rememberTextMeasurer(cacheSize = TILE_LABEL_CACHE_SIZE)
        CompositionLocalProvider(LocalTileLabelMeasurer provides labelMeasurer) {
            // Sized to fit one screen, but free to scroll when it can't - a small phone, or a large
            // font - rather than cutting the last category off out of reach.
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (category in StyleCategory.entries) {
                    StyleCategorySection(
                        category = category,
                        picks = picks,
                        onSelect = when (category) {
                            StyleCategory.DICE -> viewModel::setDiceStyleId
                            StyleCategory.DICE_CUP -> viewModel::setDiceCupStyleId
                            StyleCategory.MAT -> viewModel::setDiceMatId
                            StyleCategory.BACKGROUND -> viewModel::setTableBackgroundId
                        },
                    )
                }
            }
        }
    }
}

/** The Styles screen's categories, in its order. */
private enum class StyleCategory { DICE, DICE_CUP, MAT, BACKGROUND }

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

// Every tile's name on the page, with room to spare.
private const val TILE_LABEL_CACHE_SIZE = 64

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
    }
    val (unlocked, locked) = catalog.shownFamilies(achievements)
    return unlocked.size + locked.size
}

/** One category's card - its title and its row of tiles - as the Styles screen shows it, and as [StylesWarmUp] draws it. */
@Composable
private fun StyleCategorySection(category: StyleCategory, picks: SavedStyles, onSelect: (String) -> Unit, warmUp: IntRange? = null) {
    val achievements = picks.achievements
    when (category) {
        StyleCategory.DICE -> StyleCategoryCard(title = "Dice") {
            StyleFamilyTiles(
                catalog = DiceStyles,
                selectedId = picks.diceStyleId,
                achievements = achievements,
                onSelect = onSelect,
                previewSize = DpSize(DICE_PREVIEW_SIZE, DICE_PREVIEW_SIZE),
                backgroundBrush = { SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh) },
                warmUp = warmUp,
                buildStagger = category.ordinal,
            ) { style ->
                DicePreview(style)
            }
        }

        StyleCategory.DICE_CUP -> StyleCategoryCard(title = "Dice Cup") {
            StyleFamilyTiles(
                catalog = DiceCupStyles,
                selectedId = picks.diceCupStyleId,
                achievements = achievements,
                onSelect = onSelect,
                previewSize = DpSize(CUP_PREVIEW_WIDTH, CUP_PREVIEW_HEIGHT),
                backgroundBrush = { SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh) },
                warmUp = warmUp,
                buildStagger = category.ordinal,
            ) { style ->
                style.Cup(
                    rolling = false,
                    tilted = false,
                    modifier = Modifier.size(
                        width = (style.shape.gridWidth * CUP_ART_SCALE).dp,
                        height = (style.shape.gridHeight * CUP_ART_SCALE).dp,
                    ),
                )
            }
        }

        StyleCategory.MAT -> StyleCategoryCard(title = "Mat") {
            StyleFamilyTiles(
                catalog = DiceMats,
                selectedId = picks.diceMatId,
                achievements = achievements,
                onSelect = onSelect,
                previewSize = DpSize(MAT_PREVIEW_WIDTH, MAT_PREVIEW_HEIGHT),
                backgroundBrush = { mat -> mat.diceTrayBrush },
                warmUp = warmUp,
                buildStagger = category.ordinal,
            ) { mat ->
                mat.DiceTrayDecoration(modifier = Modifier.matchParentSize())
            }
        }

        StyleCategory.BACKGROUND -> StyleCategoryCard(title = "Background") {
            StyleFamilyTiles(
                catalog = TableBackgrounds,
                selectedId = picks.tableBackgroundId,
                achievements = achievements,
                onSelect = onSelect,
                previewSize = DpSize(BACKGROUND_PREVIEW_WIDTH, BACKGROUND_PREVIEW_HEIGHT),
                backgroundBrush = { background -> background.scoreAreaBrush },
                warmUp = warmUp,
                buildStagger = category.ordinal,
            ) { background ->
                background.Animate()
                Canvas(modifier = Modifier.matchParentSize()) { with(background) { drawScoreAreaDecoration() } }
            }
        }
    }
}

/**
 * A dice tile's die. One with something loose on its faces (googly eyes - see
 * [DiceStyle.pupilTravel]) is moved by the page, as the tray moves it in a game: wherever its tile
 * goes on screen - its row scrolled left or right, or the page up or down - the die goes with it, and
 * its pupils are thrown about and settle. Not under reduced motion, where they sit where they settled.
 */
@Composable
private fun DicePreview(style: DiceStyle) {
    val travel = style.pupilTravel?.takeIf { !LocalReduceMotion.current }
    if (travel == null) {
        style.Die(value = 5, held = false, modifier = Modifier.size(DIE_ART_SIZE))
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
            value = 5,
            held = false,
            // Where the die is, in its own sizes, as DieMotion measures it.
            modifier = Modifier.size(DIE_ART_SIZE).onGloballyPositioned { motion.moveTo(it.positionInRoot() / dieSizePx, 0f) },
        )
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
 */
@Composable
fun StylesWarmUp(picks: SavedStyles?, width: Dp, modifier: Modifier = Modifier) {
    if (stylesWarmedUp || picks == null) return
    var current by remember { mutableStateOf<Pair<StyleCategory, IntRange>?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.delayWhileResumed(WARM_UP_DELAY_MILLIS)
        for (category in StyleCategory.entries) {
            for (first in 0 until category.tileCount(picks.achievements) step WARM_UP_TILES_PER_PASS) {
                current = category to (first until first + WARM_UP_TILES_PER_PASS)
                // A frame to compose and draw them, then on to the next.
                withFrameNanos { }
                withFrameNanos { }
            }
        }
        stylesWarmedUp = true
        current = null
    }
    val (category, tiles) = current ?: return
    Box(modifier = modifier.size(1.dp).clipToBounds()) {
        Box(modifier = Modifier.requiredWidth(width).wrapContentHeight(unbounded = true)) {
            // Its own measurer, so the labels are drawn by the same code the page's are.
            CompositionLocalProvider(LocalTileLabelMeasurer provides rememberTextMeasurer(cacheSize = TILE_LABEL_CACHE_SIZE)) {
                StyleCategorySection(category = category, picks = picks, onSelect = {}, warmUp = tiles)
            }
        }
    }
}

/** Set once [StylesWarmUp] has drawn every category, for the life of the process. */
private var stylesWarmedUp = false

// Long enough for the menu's own entrance (its buttons' fade) to be over.
private const val WARM_UP_DELAY_MILLIS = 600L

// How many tiles StylesWarmUp draws at once - few enough that a pass fits beside the menu's own frame.
private const val WARM_UP_TILES_PER_PASS = 3

/** A titled group of preview tiles for one swappable category, matching Settings' Card sections. */
@Composable
private fun StyleCategoryCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 2.dp).semantics { heading() },
        )
        content()
    }
}

/**
 * One [StyleFamilyTile] per family in [catalog], in a horizontally scrolling row with a
 * [HorizontalScrollbar] under it: every unlocked style first, then the locked ones, each group in
 * the catalog's own order. [selectedId] is the saved pick, shown as the default instead while its
 * style is locked. With [warmUp], just those tiles instead, in a plain row - see [StylesWarmUp].
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
    warmUp: IntRange? = null,
    buildStagger: Int = 0,
    preview: @Composable BoxScope.(T) -> Unit,
) {
    val shownSelectedId = catalog.unlockedById(selectedId, achievements).id
    val (unlocked, shownLocked) = catalog.shownFamilies(achievements)

    @Composable
    fun Tile(index: Int) {
        if (index < unlocked.size) {
            StyleFamilyTile(unlocked[index], achievements, shownSelectedId, onSelect, previewSize, backgroundBrush, preview, index)
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
    // Opens with the current pick in the middle.
    val pickedIndex = unlocked.indexOfFirst { it.colourOf(shownSelectedId) != null }.coerceAtLeast(0)
    val scrollState = rememberScrollState()

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
    val reach = previewSize.width + TILE_SPACING
    val windowWidth = LocalWindowInfo.current.containerSize.width
    // The pick and as many either side as can show beside it, centred in the row.
    val openingTiles = with(LocalDensity.current) { ceil(windowWidth / 2f / reach.toPx()).toInt() } * 2 + 1
    val built = remember(buildOrder) {
        val openedWith = buildOrder.take(openingTiles).toSet()
        List(families.size) { index -> mutableStateOf(index in openedWith) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(buildOrder) {
        lifecycle.delayWhileResumed(PAGE_CONTENT_FADE_IN_MILLIS.toLong())
        repeat(buildStagger) { withFrameNanos { } }
        for (index in buildOrder) {
            if (built[index].value) continue
            built[index].value = true
            repeat(TILE_BUILD_FRAMES) { withFrameNanos { } }
        }
    }

    // Hidden until it has been scrolled to the current pick, so the page opens already
    // positioned rather than visibly sliding there.
    var revealed by remember { mutableStateOf(false) }
    // Faded in like the rest of the page (ScreenScaffold), not popped in a frame or two after it.
    val rowAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = PAGE_CONTENT_FADE_IN_MILLIS),
        label = "styleRowAlpha",
    )
    val rowStartPaddingPx = with(LocalDensity.current) { ROW_PADDING.roundToPx() }
    // Where the pick sits in the row, once it's been laid out: its left edge and width.
    val pickedAt = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(Unit) {
        val (left, width) = snapshotFlow { pickedAt.value }.filterNotNull().first()
        val viewport = snapshotFlow { scrollState.viewportSize }.first { it > 0 }
        // Its place in the row is measured inside the row's padding, which scrolls with it.
        scrollState.scrollTo(rowStartPaddingPx + left + width / 2 - viewport / 2)
        revealed = true
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            // Its place in a row of this many, as a lazy row would say it.
            .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = families.size) }
            .horizontalScroll(scrollState)
            .padding(horizontal = ROW_PADDING, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
    ) {
        families.forEachIndexed { index, family ->
            key(family.name) {
                TileSlot(
                    built = built[index],
                    onPlaced = if (index == pickedIndex) ({ left, width -> pickedAt.value = left to width }) else null,
                    placeholder = { TilePlaceholder(family.name, previewSize) },
                ) {
                    Tile(index)
                }
            }
        }
    }

    HorizontalScrollbar(
        scrollState = scrollState,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
    )
}

// The gap before a row's first tile and after its last.
private val ROW_PADDING = 12.dp

// How many frames apart a row builds its tiles after the page opens - see StyleFamilyTiles.
private const val TILE_BUILD_FRAMES = 4

/**
 * One place in a Styles row: the [tile] once it's [built], and the [placeholder] until then. Reads
 * [built] itself, so a tile being built recomposes only its own place, not the row. The tile is held
 * still - as under reduced motion - while it isn't on screen. [onPlaced] hears where it sits in the
 * row and how wide it is.
 */
@Composable
private fun TileSlot(
    built: State<Boolean>,
    onPlaced: ((left: Int, width: Int) -> Unit)?,
    placeholder: @Composable () -> Unit,
    tile: @Composable () -> Unit,
) {
    var onScreen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            // Clipped by the row: nothing left of it when it's scrolled out of sight.
            onScreen = coordinates.boundsInRoot().width > 0f
            onPlaced?.invoke(coordinates.positionInParent().x.toInt(), coordinates.size.width)
        },
    ) {
        if (!built.value) {
            placeholder()
            return@Box
        }
        CompositionLocalProvider(LocalReduceMotion provides (LocalReduceMotion.current || !onScreen)) {
            tile()
        }
    }
}

/**
 * A tile not built yet (see StyleFamilyTiles): an empty tile of the same size, with its name, so the
 * row is laid out as it will be. Silent to a screen reader, which finds the tile itself once it's built.
 */
@Composable
private fun TilePlaceholder(name: String, previewSize: DpSize) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(previewSize)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        )
        TileLabel(name)
    }
}

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
) {
    val colours = family.availableColours(achievements)
    val picked = colours.firstOrNull { it.style.id == selectedId }
    val shown = picked ?: colours.first()
    val hasColours = colours.size > 1
    var choosingColour by remember { mutableStateOf(false) }

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
                modifier = Modifier
                    // One radio button in a row of them: the style (and the colour showing, if it has
                    // several), whether it's the pick, and the colour chooser as an action, so neither
                    // the check badge nor the colour dots is a stop of its own. Ahead of the click handling,
                    // not behind it: semantics cleared only apply to what sits further in.
                    .clearAndSetSemantics {
                        contentDescription = if (hasColours) "${family.name}, ${shown.name}" else family.name
                        role = Role.RadioButton
                        selected = picked != null
                        collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = position, columnSpan = 1)
                        onClick(label = "Select") {
                            select(shown.style.id)
                            true
                        }
                        if (hasColours) {
                            onLongClick(label = "Choose ${family.name} colour") {
                                choosingColour = true
                                true
                            }
                        }
                    }
                    .combinedClickable(
                        onClick = { select(shown.style.id) },
                        onLongClick = if (hasColours) ({ choosingColour = true }) else null,
                        onLongClickLabel = if (hasColours) "Choose ${family.name} colour" else null,
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
                        StylePreview(
                            style = colour.style,
                            size = previewSize,
                            selected = colour.style.id == selectedId,
                            backgroundBrush = backgroundBrush,
                            preview = preview,
                            modifier = Modifier
                                // No visible name - but a screen reader still needs to say which is which.
                                .clearAndSetSemantics {
                                    contentDescription = "${family.name}, ${colour.name}"
                                    role = Role.RadioButton
                                    selected = colour.style.id == selectedId
                                    onClick(label = "Select") {
                                        select(colour.style.id)
                                        choosingColour = false
                                        true
                                    }
                                }
                                .selectable(selected = colour.style.id == selectedId, role = Role.RadioButton) {
                                    select(colour.style.id)
                                    choosingColour = false
                                },
                        )
                    }
                }
                HorizontalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
                )
            }
        }
        TileLabel(family.name)
    }
}

/**
 * A style that hasn't been unlocked yet: its tile in its first colour, faded under a padlock, so
 * it can't be picked. Long-pressing it pops up what it takes to unlock - [unlockRequirement].
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StylePreview(
            style = family.colours.first().style,
            size = previewSize,
            selected = false,
            backgroundBrush = backgroundBrush,
            preview = preview,
            modifier = Modifier
                // A tap does nothing on screen, but a screen reader has no long press to fall back on:
                // its one action is the long press's, so double-tapping says how to unlock it.
                .clearAndSetSemantics {
                    contentDescription = family.name
                    stateDescription = "Locked"
                    role = Role.Button
                    collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = position, columnSpan = 1)
                    onClick(label = "Show how to unlock ${family.name}") {
                        showingRequirement = true
                        true
                    }
                }
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showingRequirement = true },
                    onLongClickLabel = "Show how to unlock ${family.name}",
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
        TileLabel(family.name)
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
            confirmLabel = "OK",
            onConfirm = { showingRequirement = false },
            onDismissRequest = { showingRequirement = false },
        )
    }
}

/**
 * What it takes to unlock [family], and how far along [achievements] is, as the locked tile's pop-up
 * says it - in [parseInlineMarkup]'s markup, with the counts and any achievement's name in backticks
 * so they're highlighted.
 */
private fun unlockRequirement(family: StyleFamily<*>, achievements: AchievementsState): String =
    when (val unlock = family.unlock) {
        StyleUnlock.Free -> "${family.name} is always available."
        is StyleUnlock.AchievementCount -> {
            val plural = if (unlock.count == 1) "achievement" else "achievements"
            "Earn `${unlock.count}` $plural to unlock ${family.name}. You've earned `${achievements.countedUnlocks}` so far."
        }
        // Never a secret one: its style isn't shown until it's earned (StyleUnlock.hiddenWhileLocked).
        is StyleUnlock.SpecificAchievement -> "Earn `${unlock.achievement.title}` to unlock ${family.name}."
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
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(16.dp)
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
            contentDescription = "Selected",
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
