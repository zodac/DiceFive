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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

    ScreenScaffold(title = "Styles", onBack = onBack, modifier = modifier, scrollable = false) {
        // Nothing until the saved picks have loaded, so each row can open scrolled to its real pick.
        // They're normally in already (AppContainer.savedStyles), so the page has them from its first frame.
        val picks = saved ?: return@ScreenScaffold
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

/** The Styles screen's categories, in its order. */
private enum class StyleCategory { DICE, DICE_CUP, MAT, BACKGROUND }

/** One category's card - its title and its row of tiles - as the Styles screen shows it, and as [StylesWarmUp] draws it. */
@Composable
private fun StyleCategorySection(category: StyleCategory, picks: SavedStyles, onSelect: (String) -> Unit) {
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
 * Draws the Styles screen's categories once, out of sight, so the page's code has already run by the
 * time a player opens it. Each tile's art is its own drawing code, and the first time any of it runs
 * after a launch it's slow - slow enough, run all at once, to hold the menu for several frames when
 * the page opens, release build or not. Placed on the menu (under its opaque backdrop, in a 1dp
 * clipped box, so nothing shows): it waits for the menu to settle, then composes one category a
 * frame, [width] wide so its row composes the same tiles the page would open on, and drops each
 * again - spread thin enough that the menu's own drifting dice don't skip. Compose doesn't cull what
 * a clip hides, so the art is really drawn, not just composed. Runs once per launch: later openings
 * of the menu find it already done.
 */
@Composable
fun StylesWarmUp(picks: SavedStyles?, width: Dp, modifier: Modifier = Modifier) {
    if (stylesWarmedUp || picks == null) return
    var current by remember { mutableIntStateOf(-1) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.delayWhileResumed(WARM_UP_DELAY_MILLIS)
        for (index in StyleCategory.entries.indices) {
            current = index
            // A frame to compose and draw it, then on to the next.
            withFrameNanos { }
            withFrameNanos { }
        }
        stylesWarmedUp = true
        current = -1
    }
    val category = StyleCategory.entries.getOrNull(current) ?: return
    Box(modifier = modifier.size(1.dp).clipToBounds()) {
        Box(modifier = Modifier.requiredWidth(width).wrapContentHeight(unbounded = true)) {
            StyleCategorySection(category = category, picks = picks, onSelect = {})
        }
    }
}

/** Set once [StylesWarmUp] has drawn every category, for the life of the process. */
private var stylesWarmedUp = false

// Long enough for the menu's own entrance (its buttons' fade) to be over.
private const val WARM_UP_DELAY_MILLIS = 600L

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
 * style is locked.
 *
 * A [LazyRow], not a scrolling `Row`: every tile's art is its own drawing code, some of it animated,
 * and composing every tile in every category at once made the first open after a restart hold up
 * the menu for several frames. Only the tiles on screen are composed.
 */
@Composable
private fun <T : TableArt> StyleFamilyTiles(
    catalog: StyleCatalog<T>,
    selectedId: String,
    achievements: AchievementsState,
    onSelect: (String) -> Unit,
    previewSize: DpSize,
    backgroundBrush: @Composable (T) -> Brush,
    preview: @Composable BoxScope.(T) -> Unit,
) {
    val shownSelectedId = catalog.unlockedById(selectedId, achievements).id
    val (unlocked, locked) = catalog.families.partition { it.unlock.isMet(achievements) }
    // A secret style isn't so much as hinted at until it's earned.
    val shownLocked = locked.filterNot { it.unlock.hiddenWhileLocked }

    // Opens with the current pick first in line, then centres it once its size is known.
    val pickedIndex = unlocked.indexOfFirst { it.colourOf(shownSelectedId) != null }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = pickedIndex)
    // Hidden until it has been scrolled to the current pick, so the page opens already
    // positioned rather than visibly sliding there.
    var revealed by remember { mutableStateOf(false) }
    // Faded in like the rest of the page (ScreenScaffold), not popped in a frame or two after it.
    val rowAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = PAGE_CONTENT_FADE_IN_MILLIS),
        label = "styleRowAlpha",
    )
    LaunchedEffect(Unit) {
        val layout = snapshotFlow { listState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
        val picked = layout.visibleItemsInfo.firstOrNull { it.index == pickedIndex }
        if (picked != null) {
            val viewportCentre = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            listState.scrollBy((picked.offset + picked.size / 2 - viewportCentre).toFloat())
        }
        revealed = true
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().alpha(rowAlpha),
        contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(unlocked, key = { _, family -> family.name }) { index, family ->
            StyleFamilyTile(family, achievements, shownSelectedId, onSelect, previewSize, backgroundBrush, preview, index)
        }
        itemsIndexed(shownLocked, key = { _, family -> family.name }) { index, family ->
            LockedStyleFamilyTile(family, achievements, previewSize, backgroundBrush, preview, unlocked.size + index)
        }
    }

    HorizontalScrollbar(
        listState = listState,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
    )
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
        Text(text = family.name, style = MaterialTheme.typography.labelSmall)
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
        Text(text = family.name, style = MaterialTheme.typography.labelSmall)
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
