package net.zodac.dicefive.ui.styles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import net.zodac.dicefive.ui.common.HorizontalScrollbar
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

/** Lets the picked tile tell its card where it sits (x within the row, width) so the card can scroll to it. */
private val LocalPickedTilePlaced = staticCompositionLocalOf<(Int, Int) -> Unit> { { _, _ -> } }

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
 * Mat and background are separate categories - each previews only its own brush (the mat's own
 * [DiceMat.DiceTrayDecoration] shows up on its tile too), not the two composed together, since
 * they're independently selectable rather than a single paired option. All four categories'
 * tiles are sized to fit on one screen without scrolling vertically; only the tile row within a
 * category scrolls, horizontally.
 */
@Composable
fun StylesScreen(viewModel: StylesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val diceStyleId by viewModel.diceStyleId.collectAsState()
    val diceCupStyleId by viewModel.diceCupStyleId.collectAsState()
    val tableBackgroundId by viewModel.tableBackgroundId.collectAsState()
    val diceMatId by viewModel.diceMatId.collectAsState()

    val ready by viewModel.ready.collectAsState()

    ScreenScaffold(title = "Styles", onBack = onBack, modifier = modifier, scrollable = false) {
        // Nothing until the saved picks have loaded, so each row can open scrolled to its real pick.
        if (ready) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StyleCategoryCard(title = "Dice") {
                StyleFamilyTiles(
                    catalog = DiceStyles,
                    selectedId = diceStyleId,
                    onSelect = viewModel::setDiceStyleId,
                    previewSize = DpSize(DICE_PREVIEW_SIZE, DICE_PREVIEW_SIZE),
                    backgroundBrush = { SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh) },
                ) { style ->
                    style.Die(value = 5, held = false, modifier = Modifier.size(DIE_ART_SIZE))
                }
            }

            StyleCategoryCard(title = "Dice Cup") {
                StyleFamilyTiles(
                    catalog = DiceCupStyles,
                    selectedId = diceCupStyleId,
                    onSelect = viewModel::setDiceCupStyleId,
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

            StyleCategoryCard(title = "Mat") {
                StyleFamilyTiles(
                    catalog = DiceMats,
                    selectedId = diceMatId,
                    onSelect = viewModel::setDiceMatId,
                    previewSize = DpSize(MAT_PREVIEW_WIDTH, MAT_PREVIEW_HEIGHT),
                    backgroundBrush = { mat -> mat.diceTrayBrush },
                ) { mat ->
                    mat.DiceTrayDecoration(modifier = Modifier.matchParentSize())
                }
            }

            StyleCategoryCard(title = "Background") {
                StyleFamilyTiles(
                    catalog = TableBackgrounds,
                    selectedId = tableBackgroundId,
                    onSelect = viewModel::setTableBackgroundId,
                    previewSize = DpSize(BACKGROUND_PREVIEW_WIDTH, BACKGROUND_PREVIEW_HEIGHT),
                    backgroundBrush = { background -> background.scoreAreaBrush },
                ) { background ->
                    Canvas(modifier = Modifier.matchParentSize()) { with(background) { drawScoreAreaDecoration() } }
                }
            }
        }
    }
}

/**
 * A titled group of preview tiles for one swappable category, matching Settings' Card sections.
 * The tile row scrolls horizontally and carries [HorizontalScrollbar] rather than a fixed-width
 * grid, so a category isn't stuck at whatever tile count fits one page width once more options
 * exist.
 */
@Composable
private fun StyleCategoryCard(title: String, content: @Composable RowScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 2.dp),
        )

        val scrollState = rememberScrollState()
        var viewportWidth by remember { mutableIntStateOf(0) }
        // Hidden until it has been scrolled to the current pick, so the page opens already
        // positioned rather than visibly sliding there.
        var revealed by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            withFrameNanos { }
            withFrameNanos { }
            revealed = true
        }
        val onPickedPlaced: (Int, Int) -> Unit = { x, width ->
            if (!revealed) {
                scope.launch {
                    scrollState.scrollTo((x - (viewportWidth - width) / 2).coerceAtLeast(0))
                    revealed = true
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { viewportWidth = it.width }
                .alpha(if (revealed) 1f else 0f)
                .horizontalScroll(scrollState)
                .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompositionLocalProvider(LocalPickedTilePlaced provides onPickedPlaced) { content() }
        }

        HorizontalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        )
    }
}

/** One [StyleFamilyTile] per family in [catalog], in order. */
@Composable
private fun <T : TableArt> StyleFamilyTiles(
    catalog: StyleCatalog<T>,
    selectedId: String,
    onSelect: (String) -> Unit,
    previewSize: DpSize,
    backgroundBrush: @Composable (T) -> Brush,
    preview: @Composable BoxScope.(T) -> Unit,
) {
    for (family in catalog.families) {
        StyleFamilyTile(family, selectedId, onSelect, previewSize, backgroundBrush, preview)
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
    selectedId: String,
    onSelect: (String) -> Unit,
    previewSize: DpSize,
    backgroundBrush: @Composable (T) -> Brush,
    preview: @Composable BoxScope.(T) -> Unit,
) {
    val picked = family.colourOf(selectedId)
    val shown = picked ?: family.colours.first()
    val hasColours = family.colours.size > 1
    var choosingColour by remember { mutableStateOf(false) }

    val onPickedPlaced = LocalPickedTilePlaced.current
    Column(
        modifier = Modifier.onPlaced { if (picked != null) onPickedPlaced(it.positionInParent().x.roundToInt(), it.size.width) },
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
                modifier = Modifier.combinedClickable(
                    onClick = { onSelect(shown.style.id) },
                    onLongClick = if (hasColours) ({ choosingColour = true }) else null,
                    onLongClickLabel = if (hasColours) "Choose ${family.name} colour" else null,
                ),
            ) {
                if (hasColours) {
                    ColourDots(
                        colours = family.colours,
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
                    for (colour in family.colours) {
                        StylePreview(
                            style = colour.style,
                            size = previewSize,
                            selected = colour.style.id == selectedId,
                            backgroundBrush = backgroundBrush,
                            preview = preview,
                            modifier = Modifier
                                .clickable {
                                    onSelect(colour.style.id)
                                    choosingColour = false
                                }
                                // No visible name - but a screen reader still needs to say which is which.
                                .semantics { contentDescription = "${family.name}, ${colour.name}" },
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

/** One dot per colour a style comes in, the one the tile is showing ringed in the app's gold. */
@Composable
private fun <T : TableArt> ColourDots(colours: List<StyleColour<T>>, shown: StyleColour<T>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (colour in colours) {
            val isShown = colour == shown
            Swatch(
                colour = colour.swatch,
                size = COLOUR_DOT_SIZE,
                ring = if (isShown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
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
