package net.zodac.dicefive.ui.styles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.game.style.StyleAccess
import net.zodac.dicefive.game.style.StyleCategory
import net.zodac.dicefive.game.style.StyleUnlock
import net.zodac.dicefive.game.style.access
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.navigation.StyleScrollRequest
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_ok
import net.zodac.dicefive.resources.styles_choose_variant_action
import net.zodac.dicefive.resources.styles_family_colour_cd
import net.zodac.dicefive.resources.styles_frame_preview_name
import net.zodac.dicefive.resources.styles_locked_spoken
import net.zodac.dicefive.resources.styles_name_with_variant
import net.zodac.dicefive.resources.styles_requirement_any
import net.zodac.dicefive.resources.styles_requirement_count
import net.zodac.dicefive.resources.styles_requirement_free
import net.zodac.dicefive.resources.styles_requirement_specific
import net.zodac.dicefive.resources.styles_select_action
import net.zodac.dicefive.resources.styles_show_unlock_action
import net.zodac.dicefive.resources.styles_unlock_any
import net.zodac.dicefive.resources.styles_unlock_count
import net.zodac.dicefive.resources.styles_unlock_free
import net.zodac.dicefive.resources.styles_unlock_specific
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import net.zodac.dicefive.ui.common.AppTooltip
import net.zodac.dicefive.ui.common.ColourDisc
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.HorizontalScrollbar
import net.zodac.dicefive.ui.common.fitFontSize
import net.zodac.dicefive.ui.common.isLargeFont
import net.zodac.dicefive.ui.common.sizeAtNormalFont
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.accessibleCombinedClickable
import net.zodac.dicefive.ui.common.ambientMotion
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.common.parseInlineMarkup
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.rememberAppTooltipState
import net.zodac.dicefive.ui.common.fillTemplate
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.common.stringsLanguage
import net.zodac.dicefive.ui.common.tooltipMarkup
import net.zodac.dicefive.ui.game.CategoryTile
import net.zodac.dicefive.ui.game.CUP_SHAKE_MILLIS
import net.zodac.dicefive.ui.icons.Symbols
import net.zodac.dicefive.ui.style.CupDice
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DiceMats
import net.zodac.dicefive.ui.style.DiceStyle
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.DieMotion
import net.zodac.dicefive.ui.style.GameVisualTheme
import net.zodac.dicefive.ui.style.LocalCupDice
import net.zodac.dicefive.ui.style.LocalDieMotion
import net.zodac.dicefive.ui.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.style.ScoreBoxes
import net.zodac.dicefive.ui.style.ScoreBoxStyle
import net.zodac.dicefive.ui.style.ScoreFrame
import net.zodac.dicefive.ui.style.ScoreFramePreviewColour
import net.zodac.dicefive.ui.style.ScoreFrames
import net.zodac.dicefive.ui.style.StyleCatalog
import net.zodac.dicefive.ui.style.StyleCatalogs
import net.zodac.dicefive.ui.style.StyleColour
import net.zodac.dicefive.ui.style.StyleFamily
import net.zodac.dicefive.ui.style.TableArt
import net.zodac.dicefive.ui.style.TableBackgrounds
import net.zodac.dicefive.ui.style.catalog
import net.zodac.dicefive.ui.style.visualTheme
import net.zodac.dicefive.ui.theme.GoldAccent

// Each category's tile size on its page, and the shape its pick's preview is scaled from on the Styles page.
private val DICE_PREVIEW_SIZE = 72.dp
private val DIE_ART_SIZE = 44.dp
private val CUP_PREVIEW_WIDTH = 72.dp
// No taller than this, so the Dice Cup page's five lines fit a 360 x 800dp phone under its status bar (96dp scrolled it).
private val CUP_PREVIEW_HEIGHT = 88.dp
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
// A box style's corner of a scorecard (BoxPreview): the frame's tile size, its boxes drawn at this fraction of a game's
// compact ones, this far apart (before scaling), and this far below the tile's top.
private val BOX_PREVIEW_WIDTH = 97.dp
private val BOX_PREVIEW_HEIGHT = 76.dp
private const val BOX_PREVIEW_SCALE = 0.62f
private val BOX_PREVIEW_GAP = 4.dp
private val BOX_PREVIEW_TOP = 5.dp
private val BOX_PREVIEW_ROWS = listOf(
    listOf(ScoreCategory.THREES, ScoreCategory.THREE_OF_A_KIND, ScoreCategory.FULL_HOUSE),
    listOf(ScoreCategory.D20_7, ScoreCategory.D20_14, ScoreCategory.CHANCE),
)
private val COLOUR_DOT_SIZE = 7.dp
private val COLOUR_DOT_SIZE_MORE_BEYOND = 4.dp
// More colours than this and the tile's dots show a window of them; the pop-up still lists every one.
internal const val MAX_COLOUR_DOTS = 3
// A locked tile's style shows through its scrim; the padlock over it is faded to match.
private const val LOCKED_SCRIM_ALPHA = 0.55f
private const val LOCKED_PADLOCK_ALPHA = 0.8f

// How long a tile a banner brought you to stays flashed gold, and how long it takes to fade in and out - as an achievement row's.
private const val STYLE_FLASH_HOLD_MILLIS = 900L
private const val STYLE_FLASH_TRANSITION_MILLIS = 400

// How strongly the gold washes over a mat or background tile, whose art would otherwise hide it.
private const val FLASH_OVER_ART_ALPHA = 0.55f

// ScreenScaffold's side margin, which the page's scrollbar sits in.
internal val PAGE_MARGIN = 20.dp

/** The Styles screen's categories, in its order. */
// The gap between tiles side by side, and between lines of them.
private val TILE_SPACING = 12.dp

/**
 * The families a category's page shows, in its order: every unlocked one, then the locked ones - less any
 * secret one, which isn't so much as hinted at until it's earned - each in the catalog's own order.
 */
private fun <T : TableArt> StyleCatalog<T>.shownFamilies(access: StyleAccess): Pair<List<StyleFamily<T>>, List<StyleFamily<T>>> {
    val (unlocked, locked) = families.partition { it.unlock.isMet(access) }
    return unlocked to locked.filterNot { it.unlock.hiddenWhileLocked }
}

/**
 * Scrolls the category page at once so a tile is centred on it ([StyleCategoryScaffold]): a banner's style on a page just
 * opened, or the pick on any page just opened. Null outside that page: a banner's tile is slid into view instead.
 */
internal fun interface StyleTileJump {
    /** Centres the tile laid out at [tile]; with [onlyIfOutOfView], only when it isn't wholly in the page's viewport. */
    fun jump(tile: LayoutCoordinates, onlyIfOutOfView: Boolean)
}

/** The category page's [StyleTileJump], or null outside it. */
internal val LocalStyleTileJump = compositionLocalOf<StyleTileJump?> { null }

/** The page's measurer for tiles' names - see [TileLabel] - or null to lay each one out afresh as a plain [Text]. */
internal val LocalTileLabelMeasurer = compositionLocalOf<TextMeasurer?> { null }

/**
 * The one measurer for tiles' names, kept for the life of the process rather than the page's, so
 * each name is laid out once - by [StylesWarmUp] on the menu, normally - and every later opening of
 * the page finds it already done. A new one only if the text would lay out differently: another
 * density (font scale included) or layout direction, or new fonts.
 */
@Composable
internal fun rememberTileLabelMeasurer(): TextMeasurer {
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
private fun TileLabel(name: String, tileWidth: Dp) {
    val measurer = LocalTileLabelMeasurer.current
    val style = tileLabelStyle(name, tileWidth)
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

/** `labelSmall`, or at a large font shrunk as far as [name] needs to stay within its tile and off its neighbours', no smaller than at 100%. */
@Composable
private fun tileLabelStyle(name: String, tileWidth: Dp): TextStyle {
    val style = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    if (!density.isLargeFont) return style
    val fitter = rememberTextMeasurer()
    val fit = remember(name, style, tileWidth, density) {
        val width = with(density) { tileWidth.roundToPx() }
        fitFontSize(style.fontSize, density.sizeAtNormalFont(style.fontSize), TILE_LABEL_FONT_STEP) { size ->
            fitter.measure(name, style.copy(fontSize = size), maxLines = 1, softWrap = false).size.width <= width
        }
    }
    return style.copy(fontSize = fit.size)
}

private val TILE_LABEL_FONT_STEP = 0.5.sp

/** How many tiles [category]'s row has. */
internal fun StyleCategory.tileCount(access: StyleAccess): Int {
    val (unlocked, locked) = catalog.shownFamilies(access)
    return unlocked.size + locked.size
}

/**
 * Every tile of one category, as its page shows them ([StyleCategoryScreen]) and [StylesWarmUp] draws them: a grid of
 * the category's styles, the pick outlined in gold. With [warmUp], just those tiles - see [StylesWarmUp].
 */
@Composable
internal fun StyleCategoryTiles(
    category: StyleCategory,
    picks: SavedStyles,
    onSelect: (String) -> Unit,
    warmUp: IntRange? = null,
    focus: StyleScrollRequest? = null,
    unlockedOnly: Boolean = false,
) {
    val access = picks.access
    when (category) {
        StyleCategory.DICE -> run {
            val roll = rememberDicePickRoll()
            StyleFamilyTiles(
                catalog = DiceStyles,
                selectedId = picks.diceStyleId,
                access = access,
                onSelect = { id ->
                    onSelect(id)
                    roll.start(id)
                },
                previewSize = DpSize(DICE_PREVIEW_SIZE, DICE_PREVIEW_SIZE),
                backgroundBrush = { SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh) },
                warmUp = warmUp,
                focus = focus,
                unlockedOnly = unlockedOnly,
            ) { style ->
                DicePreview(style, roll = roll.takeIf { it.dieId == style.id })
            }
        }

        StyleCategory.DICE_CUP -> run {
            val shake = rememberCupPickShake()
            val cupDice = CupDice(picks.visualTheme.diceStyle, GameMode.STANDARD.diceCount)
            StyleFamilyTiles(
                catalog = DiceCupStyles,
                selectedId = picks.diceCupStyleId,
                access = access,
                onSelect = { id ->
                    onSelect(id)
                    shake.start(id)
                },
                previewSize = DpSize(CUP_PREVIEW_WIDTH, CUP_PREVIEW_HEIGHT),
                backgroundBrush = { SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh) },
                warmUp = warmUp,
                focus = focus,
                unlockedOnly = unlockedOnly,
            ) { style ->
                // One cup per style, not one per tile: a tile showing another colour of the same
                // family would otherwise inherit the last one's state - the Flowerpot's plant
                // growing in from the bare pot's, the Magician's rabbit stuck at the plain hat's peek.
                key(style.id) {
                    // Forced still under reduced motion, as in a game (and off screen - see TileSlot).
                    val moving = shake.cupId == style.id && !LocalReduceMotion.current
                    CompositionLocalProvider(LocalCupDice provides cupDice) {
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
        }

        StyleCategory.MAT -> run {
            StyleFamilyTiles(
                catalog = DiceMats,
                selectedId = picks.diceMatId,
                access = access,
                onSelect = onSelect,
                previewSize = DpSize(MAT_PREVIEW_WIDTH, MAT_PREVIEW_HEIGHT),
                backgroundBrush = { mat -> mat.diceTrayBrush },
                warmUp = warmUp,
                focus = focus,
                unlockedOnly = unlockedOnly,
                flashOverArt = true,
            ) { mat ->
                mat.DiceTrayDecoration(modifier = Modifier.matchParentSize())
            }
        }

        StyleCategory.BACKGROUND -> run {
            StyleFamilyTiles(
                catalog = TableBackgrounds,
                selectedId = picks.tableBackgroundId,
                access = access,
                onSelect = onSelect,
                previewSize = DpSize(BACKGROUND_PREVIEW_WIDTH, BACKGROUND_PREVIEW_HEIGHT),
                backgroundBrush = { background -> background.scoreAreaBrush },
                warmUp = warmUp,
                focus = focus,
                unlockedOnly = unlockedOnly,
                flashOverArt = true,
            ) { background ->
                background.Animate()
                Canvas(modifier = Modifier.matchParentSize()) { with(background) { drawScoreAreaDecoration() } }
            }
        }

        StyleCategory.FRAME -> run {
            StyleFamilyTiles(
                catalog = ScoreFrames,
                selectedId = picks.scoreFrameId,
                access = access,
                onSelect = onSelect,
                previewSize = DpSize(FRAME_PREVIEW_WIDTH, FRAME_PREVIEW_HEIGHT),
                backgroundBrush = { SolidColor(MaterialTheme.colorScheme.background) },
                warmUp = warmUp,
                focus = focus,
                unlockedOnly = unlockedOnly,
            ) { frame ->
                FramePreview(frame, modifier = Modifier.align(Alignment.TopCenter))
            }
        }

        StyleCategory.BOX -> run {
            StyleFamilyTiles(
                catalog = ScoreBoxes,
                selectedId = picks.scoreBoxId,
                access = access,
                onSelect = onSelect,
                previewSize = DpSize(BOX_PREVIEW_WIDTH, BOX_PREVIEW_HEIGHT),
                backgroundBrush = { picks.visualTheme.background.scoreAreaBrush },
                warmUp = warmUp,
                focus = focus,
                unlockedOnly = unlockedOnly,
            ) { box ->
                BoxPreview(box, modifier = Modifier.align(Alignment.TopCenter))
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
 * goes on screen - the page scrolled up or down - the die goes with it, and
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
 * A box style's tile: a corner of a scorecard in it, at [BOX_PREVIEW_SCALE] of a game's compact boxes - 3s, 3x and a scored
 * Full House over D20's 7 and 14 (one in each half) and Chance - on the player's own background. Silent to a screen
 * reader: the tile says which style it is.
 */
@Composable
private fun BoxPreview(box: ScoreBoxStyle, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val scaled = remember(density) { Density(density.density * BOX_PREVIEW_SCALE, density.fontScale) }
    CompositionLocalProvider(LocalGameVisualTheme provides GameVisualTheme(box = box), LocalDensity provides scaled) {
        Column(
            modifier = modifier.padding(top = BOX_PREVIEW_TOP / BOX_PREVIEW_SCALE),
            verticalArrangement = Arrangement.spacedBy(BOX_PREVIEW_GAP),
        ) {
            for (row in BOX_PREVIEW_ROWS) {
                Row(horizontalArrangement = Arrangement.spacedBy(BOX_PREVIEW_GAP)) {
                    for (category in row) CategoryTile(category = category, highlighted = false, compact = true, scored = category == ScoreCategory.FULL_HOUSE)
                }
            }
        }
    }
}

/**
 * One [StyleFamilyTile] per family in [catalog], every one on screen at once in a grid ([GalleryGrid]) that the page
 * scrolls down through: every unlocked style first, then the locked ones, each group in the catalog's own order.
 * [selectedId] is the saved pick, shown as the default instead while its style is locked; with [unlockedOnly], the
 * locked ones aren't shown. With [warmUp], just those tiles instead, in a plain row - see [StylesWarmUp].
 *
 * Hidden by [unlockedOnly], a locked tile stays composed out of sight ([HiddenTiles]), so showing it again moves it
 * back rather than building it afresh. The styles a banner named ([focus]) are brought into view and flashed gold.
 */
@Composable
private fun <T : TableArt> StyleFamilyTiles(
    catalog: StyleCatalog<T>,
    selectedId: String,
    access: StyleAccess,
    onSelect: (String) -> Unit,
    previewSize: DpSize,
    backgroundBrush: @Composable (T) -> Brush,
    warmUp: IntRange? = null,
    focus: StyleScrollRequest? = null,
    flashOverArt: Boolean = false,
    unlockedOnly: Boolean = false,
    preview: @Composable BoxScope.(T) -> Unit,
) {
    // The styles of this category a banner asked to be shown: brought into view, and flashed for a moment.
    var flashing by remember { mutableStateOf(false) }
    val focusNames = remember(focus, catalog) { focus?.styles.orEmpty().filter { it.categoryNoun.key == catalog.noun.key }.map { it.name.key }.toSet() }
    val shownSelectedId = catalog.unlockedById(selectedId, access).id
    val (unlocked, allLocked) = catalog.shownFamilies(access)
    // The pick, put in view at once as the page opens if it's out of view - once, and not when a banner's style of this
    // page is being brought there instead.
    var revealPick by remember { mutableStateOf(warmUp == null && unlocked.none { it.name.key in focusNames }) }
    // Every tile, locked ones last: hiding them only shortens what's shown (the end of the run), so the unlocked tiles
    // keep their index and their composition.
    val allFamilies = remember(unlocked, allLocked) { unlocked + allLocked }
    val shownCount = if (unlockedOnly) unlocked.size else allFamilies.size

    // The words every tile says alike, looked up once for the page rather than once a tile.
    val words = TileWords(
        select = stringResource(Res.string.styles_select_action),
        locked = stringResource(Res.string.styles_locked_spoken),
        variantNoun = stringResource(catalog.variantNoun),
        chooseVariant = stringResource(Res.string.styles_choose_variant_action),
        familyColour = stringResource(Res.string.styles_family_colour_cd),
        showUnlock = stringResource(Res.string.styles_show_unlock_action),
        language = stringsLanguage(),
    )

    @Composable
    fun Tile(index: Int) {
        if (index < unlocked.size) {
            StyleFamilyTile(
                words = words,
                unlocked[index], access, shownSelectedId, onSelect, previewSize, backgroundBrush, preview, index,
                flashing = flashing && unlocked[index].name.key in focusNames, jumpToFlash = focus?.animate == false,
                flashOverArt = flashOverArt,
                revealIfPicked = revealPick,
                onRevealed = { revealPick = false },
            )
        } else {
            LockedStyleFamilyTile(allLocked[index - unlocked.size], access, previewSize, backgroundBrush, preview, index, words)
        }
    }

    // StylesWarmUp's pass: just these tiles, side by side, drawn once - see StylesWarmUp.
    if (warmUp != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (index in warmUp) if (index < allFamilies.size) Tile(index)
        }
        return
    }

    // As an achievement row's: the flash starts once the page is up, and holds for a moment.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(focus) {
        flashing = false
        if (focusNames.isEmpty() || unlocked.none { it.name.key in focusNames }) return@LaunchedEffect
        flashing = true
        lifecycle.delayWhileResumed(STYLE_FLASH_HOLD_MILLIS)
        flashing = false
    }

    // Each tile's composition, movable between the grid and HiddenTiles. Handed the tile to draw each time, so it draws
    // with the current pick, not the one it was first built with.
    val slots = remember(allFamilies) {
        allFamilies.map { movableContentOf { tile: @Composable () -> Unit -> TileSlot(tile) } }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = remember(maxWidth, previewSize.width, shownCount) {
            TileSpacing.of(width = maxWidth - TILES_SIDE_PADDING * 2, slot = previewSize.width, count = shownCount)
        }
        Column {
            // Read as one set of this many, whichever way the grid wraps it.
            GalleryGrid(
                slotWidth = previewSize.width,
                spacing = spacing,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = shownCount) }
                    .padding(start = TILES_SIDE_PADDING, top = TILES_END_PADDING, end = TILES_SIDE_PADDING, bottom = TILES_END_PADDING),
            ) {
                for (index in 0 until shownCount) {
                    slots[index] { Tile(index) }
                }
            }
            // The hidden tiles stay composed, out of sight (and out of reach of TalkBack), so showing them again moves
            // them back into place instead of building them - see HiddenTiles.
            if (shownCount < allFamilies.size) {
                HiddenTiles(modifier = Modifier.clearAndSetSemantics {}, tileWidth = previewSize.width) {
                    for (index in shownCount until allFamilies.size) slots[index] { Tile(index) }
                }
            }
        }
    }
}

/**
 * The words every tile of a page says alike, looked up once for the page: its tap ("Select"), a locked tile's state
 * ("Locked"), the category's word for a colour, and the templates each tile fills with its own names ([fillTemplate],
 * in the strings' [language]) - "Choose %1$s %2$s", "%1$s, %2$s" and "Show how to unlock %1$s".
 */
@Immutable
private class TileWords(
    val select: String,
    val locked: String,
    val variantNoun: String,
    private val chooseVariant: String,
    private val familyColour: String,
    private val showUnlock: String,
    private val language: String,
) {
    fun chooseVariant(family: String): String = fillTemplate(chooseVariant, arrayOf(family, variantNoun), language)

    fun familyColour(family: String, colour: String): String = fillTemplate(familyColour, arrayOf(family, colour), language)

    fun showUnlock(family: String): String = fillTemplate(showUnlock, arrayOf(family), language)
}

/**
 * How a category's tiles are spaced across a card [width] wide: each tile takes its preview's [slot] (a wider name
 * spills into the gaps either side), and the gap between them is [TILE_SPACING] where the gallery's lines have room for
 * it, or less - down to [GALLERY_MIN_TILE_SPACING] - where closing up fits another tile on each. [columns] is how many to
 * a gallery line.
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
 * Tiles kept composed but not shown: measured at a preview's [tileWidth], then placed in no room at all and clipped, so
 * nothing is drawn, none takes space, and each reads as off screen (see [TileSlot]), holding still.
 */
@Composable
private fun HiddenTiles(tileWidth: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val width = tileWidth.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = width, maxHeight = constraints.maxHeight)) }
        layout(0, 0) { placeables.forEach { it.place(0, 0) } }
    }
}

/**
 * A category's tiles in a gallery, every one laid out at once, left-aligned and spaced by [spacing]: [TileSpacing.columns]
 * to a line, each in its preview's [slotWidth] with [TileSpacing.gap] between. A name wider than its preview (at a large font, say) centres under it and
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

// The room either side of a card's tiles: what lets
// four dice or cups fit across a 360dp phone's gallery (20dp page padding each side), and still wide
// enough for the longest name to spill into.
private val TILES_SIDE_PADDING = 8.dp

// The room above a category's first line of tiles and below its last.
private val TILES_END_PADDING = 12.dp

// Between a tile's preview and its name (StyleFamilyTile's column).
private val TILE_LABEL_GAP = 4.dp

/** The smallest a category page's tiles are drawn to fit its screen ([fittedTileScale]): a cup's 88dp at 64dp. */
internal const val MIN_TILE_FIT_SCALE = 64f / 88f

// No tile smaller than this either way, the touch-target minimum: a mat's 65dp height stops it above MIN_TILE_FIT_SCALE.
private val MIN_TILE_TOUCH_SIZE = 48.dp

/** The smallest [category]'s tiles are drawn: [MIN_TILE_FIT_SCALE], or larger where that would make a tile too small to touch. */
internal fun StyleCategory.minTileFitScale(): Float =
    maxOf(MIN_TILE_FIT_SCALE, MIN_TILE_TOUCH_SIZE / minOf(previewSize.width, previewSize.height))

/**
 * The scale a category page's [count] tiles of [category] are drawn at so they fit [height] px of page, [width] px wide,
 * without scrolling, at a larger display size or font (the caller's to decide - see [StyleCategoryScaffold]): 1 where
 * they fit as they are, else the largest that fits, down to [minTileFitScale]; and 1 again where even that doesn't, the
 * page scrolling instead of every tile getting tiny. The tiles, their gaps and the card's padding scale (smaller dp); their names, [labelHeight] px a
 * line, don't (the same sp). Columns are worked out as the page's own are ([TileSpacing]), so a smaller tile can gain one.
 */
internal fun fittedTileScale(category: StyleCategory, count: Int, width: Int, height: Int, density: Float, labelHeight: Float): Float {
    if (count == 0 || height <= 0) return 1f
    val preview = category.previewSize
    fun contentHeight(scale: Float): Float {
        val dp = density * scale
        val columns = ((width - TILES_SIDE_PADDING.value * dp * 2 + GALLERY_MIN_TILE_SPACING.value * dp) /
            ((preview.width.value + GALLERY_MIN_TILE_SPACING.value) * dp)).toInt().coerceIn(1, count)
        val lines = (count + columns - 1) / columns
        // Each part rounded up to whole pixels, as the layout places them, so their rounding can't add up past the room.
        val line = ceil(preview.height.value * dp) + ceil(TILE_LABEL_GAP.value * dp) + ceil(labelHeight)
        return ceil(TILES_END_PADDING.value * dp) * 2 + lines * line + (lines - 1) * ceil(TILE_SPACING.value * dp)
    }
    if (contentHeight(1f) <= height) return 1f
    val floor = category.minTileFitScale()
    var scale = 1f
    while (scale > floor) {
        scale = maxOf(scale - TILE_FIT_STEP, floor)
        if (contentHeight(scale) <= height) return scale
    }
    return 1f
}

// How finely fittedTileScale steps down: a pixel or so of a tile at a time.
private const val TILE_FIT_STEP = 0.005f

/** One built tile on a category's page, held still - as under reduced motion - while it isn't on screen. */
@Composable
private fun TileSlot(tile: @Composable () -> Unit) {
    var onScreen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            // Clipped by the page: nothing left of it when it's scrolled out of sight.
            onScreen = coordinates.boundsInRoot().width > 0f
        },
    ) {
        CompositionLocalProvider(LocalReduceMotion provides (LocalReduceMotion.current || !onScreen)) {
            tile()
        }
    }
}

/**
 * One style: a rendered preview plus its name, ringed in gold when one of its colours is the
 * current pick. Tapping it picks the colour it's showing; long-pressing it, when it has more than
 * one colour, pops up a scrollable row of that style's colours, each as its own preview, to pick
 * one. [previewSize] is the preview's own size, which differs by category (a die's is square, a
 * cup's is tall, the mat's is wide).
 */
@Composable
private fun <T : TableArt> StyleFamilyTile(
    words: TileWords,
    family: StyleFamily<T>,
    access: StyleAccess,
    selectedId: String,
    onSelect: (String) -> Unit,
    previewSize: DpSize,
    backgroundBrush: @Composable (T) -> Brush,
    preview: @Composable BoxScope.(T) -> Unit,
    position: Int,
    flashing: Boolean = false,
    jumpToFlash: Boolean = false,
    flashOverArt: Boolean = false,
    revealIfPicked: Boolean = false,
    onRevealed: () -> Unit = {},
) {
    val colours = family.colours
    val familyName = stringResource(family.name)
    val selectLabel = words.select
    val chooseVariantLabel = words.chooseVariant(familyName)
    val picked = colours.firstOrNull { it.style.id == selectedId }
    val shown = picked ?: colours.firstOrNull { it.isAvailable(access) } ?: colours.first()
    val hasColours = colours.size > 1
    val shownDescription = if (hasColours) words.familyColour(familyName, stringResource(shown.name)) else familyName
    var choosingColour by remember { mutableStateOf(false) }
    var lockedVariantRequirement by remember { mutableStateOf<String?>(null) }

    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    // Picking a tile that's partly scrolled off the page's edge scrolls it just far enough to show whole.
    val select: (String) -> Unit = { id ->
        onSelect(id)
        scope.launch { bringIntoView.bringIntoView() }
    }
    // A banner's style, flashed: brought into view - slid there on a page that was already open, or, [jumpToFlash], on
    // one just opened, put there at once ([LocalStyleTileJump]), so the page opens already in place.
    val jump = LocalStyleTileJump.current
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    LaunchedEffect(flashing) {
        if (!flashing) return@LaunchedEffect
        // A frame on, so the tile has been laid out where it is.
        withFrameNanos { }
        val coordinates = placed[0]
        if (jumpToFlash && jump != null && coordinates != null) jump.jump(coordinates, onlyIfOutOfView = false) else bringIntoView.bringIntoView()
    }
    // The pick, on a page just opened: put in view at once if it's out of view ([LocalStyleTileJump]).
    val revealing = revealIfPicked && picked != null
    LaunchedEffect(revealing) {
        if (!revealing) return@LaunchedEffect
        // A frame on, so the tile has been laid out where it is.
        withFrameNanos { }
        placed[0]?.let { jump?.jump(it, onlyIfOutOfView = true) }
        onRevealed()
    }
    Column(
        modifier = Modifier.bringIntoViewRequester(bringIntoView).onGloballyPositioned { placed[0] = it },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TILE_LABEL_GAP),
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
                    // One radio button in a set of them: the style (and the colour showing, if it has
                    // several), whether it's the pick, and the colour chooser as an action, so the
                    // colour dots aren't a stop of their own.
                    .accessibleCombinedClickable(
                        onClick = { select(shown.style.id) },
                        onClickLabel = selectLabel,
                        onLongClick = if (hasColours) ({ choosingColour = true }) else null,
                        onLongClickLabel = if (hasColours) chooseVariantLabel else null,
                        role = Role.RadioButton,
                        clearSemantics = true,
                    ) {
                        contentDescription = shownDescription
                        selected = picked != null
                        collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = position, columnSpan = 1)
                    },
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
                        val available = colour.isAvailable(access)
                        val colourDescription = stringResource(Res.string.styles_family_colour_cd, familyName, stringResource(colour.name))
                        val achievementTitle = colour.secretAchievement?.title?.let { stringResource(it) } ?: ""
                        val variantStyleName = stringResource(Res.string.styles_name_with_variant, familyName, stringResource(colour.name))
                        val lockedRequirement = stringResource(Res.string.styles_unlock_specific, achievementTitle, variantStyleName)
                        val showUnlockLabel = stringResource(Res.string.styles_show_unlock_action, variantStyleName)
                        StylePreview(
                            style = colour.style,
                            size = previewSize,
                            selected = colour.style.id == selectedId,
                            backgroundBrush = backgroundBrush,
                            preview = preview,
                            modifier = Modifier
                                // No visible name - but a screen reader still needs to say which is which.
                                .accessibleCombinedClickable(
                                    onClick = {
                                        if (available) {
                                            select(colour.style.id)
                                            choosingColour = false
                                        } else {
                                            lockedVariantRequirement = lockedRequirement
                                        }
                                    },
                                    onClickLabel = if (available) selectLabel else showUnlockLabel,
                                    onLongClick = if (!available) ({ lockedVariantRequirement = lockedRequirement }) else null,
                                    role = Role.RadioButton,
                                    clearSemantics = true,
                                ) {
                                    contentDescription = colourDescription
                                    selected = colour.style.id == selectedId
                                },
                        ) {
                            if (!available) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = LOCKED_SCRIM_ALPHA)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Symbols.Lock,
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
        TileLabel(stringResource(family.name), previewSize.width)
    }

    if (lockedVariantRequirement != null) {
        DiceFiveDialog(
            icon = Symbols.Lock,
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
    access: StyleAccess,
    previewSize: DpSize,
    backgroundBrush: @Composable (T) -> Brush,
    preview: @Composable BoxScope.(T) -> Unit,
    position: Int,
    words: TileWords,
) {
    var showingRequirement by remember { mutableStateOf(false) }
    val tooltipState = rememberAppTooltipState()
    val scope = rememberCoroutineScope()
    val familyName = stringResource(family.name)
    val lockedText = words.locked
    val showUnlockLabel = words.showUnlock(familyName)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TILE_LABEL_GAP),
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
                    .accessibleCombinedClickable(
                        onClick = { scope.launch { tooltipState.show() } },
                        onClickLabel = showUnlockLabel,
                        onLongClick = { showingRequirement = true },
                        onLongClickLabel = showUnlockLabel,
                        role = Role.Button,
                        screenReaderClick = { showingRequirement = true },
                        clearSemantics = true,
                    ) {
                        contentDescription = familyName
                        stateDescription = lockedText
                        collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = position, columnSpan = 1)
                    },
            ) {
                // Translucent, so the style still shows through - the lock says "not yet", not "hidden".
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = LOCKED_SCRIM_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Symbols.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = LOCKED_PADLOCK_ALPHA),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        TileLabel(familyName, previewSize.width)
    }

    if (showingRequirement) {
        DiceFiveDialog(
            icon = Symbols.Lock,
            title = null,
            // Backticks mark what to highlight - the gold the rules pages use.
            message = parseInlineMarkup(
                unlockRequirement(family, access),
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
        is StyleUnlock.AnyAchievement -> stringResource(Res.string.styles_requirement_any, stringResource(unlock.achievements[0].title), stringResource(unlock.achievements[1].title))
    }

/**
 * What it takes to unlock [family], and how far along [access] is, as the locked tile's pop-up
 * says it - in [parseInlineMarkup]'s markup, with the counts and any achievement's name in backticks
 * so they're highlighted.
 */
@Composable
private fun unlockRequirement(family: StyleFamily<*>, access: StyleAccess): String =
    when (val unlock = family.unlock) {
        StyleUnlock.Free -> stringResource(Res.string.styles_unlock_free, stringResource(family.name))
        is StyleUnlock.AchievementCount ->
            pluralStringResource(Res.plurals.styles_unlock_count, unlock.count, unlock.count, stringResource(family.name), access.achievements.countedUnlocks)
        // Never a secret one: its style isn't shown until it's earned (StyleUnlock.hiddenWhileLocked).
        is StyleUnlock.SpecificAchievement -> stringResource(Res.string.styles_unlock_specific, stringResource(unlock.achievement.title), stringResource(family.name))
        is StyleUnlock.AnyAchievement ->
            stringResource(Res.string.styles_unlock_any, stringResource(unlock.achievements[0].title), stringResource(unlock.achievements[1].title), stringResource(family.name))
    }

/**
 * [style] drawn by [preview] on its [backgroundBrush] at [size], outlined in the app's gold when
 * [selected], with nothing drawn over the art. [modifier] carries the click handling; [overlay] draws on top.
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
            // tile, the backdrop's drift, the page scrolling - had every tile's art drawn over again
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
                width = if (selected) 3.dp else 1.dp,
                // outline, not outlineVariant: the border is the tile's edge, which needs 3:1 against the page.
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        preview(style)
        overlay()
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
            ColourDisc(
                colour = colour.swatch,
                size = if (moreBeyond) COLOUR_DOT_SIZE_MORE_BEYOND else COLOUR_DOT_SIZE,
                ring = if (colour == shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** The size of [this] category's tiles: the shape its pick's preview ([StylePickPreview]) is drawn in. */
internal val StyleCategory.previewSize: DpSize
    get() = when (this) {
        StyleCategory.DICE -> DpSize(DICE_PREVIEW_SIZE, DICE_PREVIEW_SIZE)
        StyleCategory.DICE_CUP -> DpSize(CUP_PREVIEW_WIDTH, CUP_PREVIEW_HEIGHT)
        StyleCategory.MAT -> DpSize(MAT_PREVIEW_WIDTH, MAT_PREVIEW_HEIGHT)
        StyleCategory.BACKGROUND -> DpSize(BACKGROUND_PREVIEW_WIDTH, BACKGROUND_PREVIEW_HEIGHT)
        StyleCategory.FRAME -> DpSize(FRAME_PREVIEW_WIDTH, FRAME_PREVIEW_HEIGHT)
        StyleCategory.BOX -> DpSize(BOX_PREVIEW_WIDTH, BOX_PREVIEW_HEIGHT)
    }

/**
 * [category]'s pick in [picks], drawn as its tile is ([previewSize]) but still: not selectable, without its badge or colour
 * dots, and its art centred in it (a frame's tab and a box's corner sit higher in a tile, clear of the dots). Silent to a
 * screen reader: the Styles page's field says what it is.
 */
@Composable
internal fun StylePickPreview(category: StyleCategory, picks: SavedStyles) {
    val access = picks.access
    val size = category.previewSize
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier = Modifier.clearAndSetSemantics {}) {
        when (category) {
            StyleCategory.DICE ->
                StylePreview(DiceStyles.unlockedById(picks.diceStyleId, access), size, selected = false, { SolidColor(surface) }, { DicePreview(it) })

            StyleCategory.DICE_CUP -> {
                val cupDice = CupDice(picks.visualTheme.diceStyle, GameMode.STANDARD.diceCount)
                StylePreview(DiceCupStyles.unlockedById(picks.diceCupStyleId, access), size, selected = false, { SolidColor(surface) }, { style ->
                    CompositionLocalProvider(LocalCupDice provides cupDice) {
                        style.Cup(
                            rolling = false,
                            tilted = false,
                            modifier = Modifier.size(width = (style.shape.gridWidth * CUP_ART_SCALE).dp, height = (style.shape.gridHeight * CUP_ART_SCALE).dp),
                        )
                    }
                })
            }

            StyleCategory.MAT ->
                StylePreview(DiceMats.unlockedById(picks.diceMatId, access), size, selected = false, { it.diceTrayBrush }, { mat ->
                    mat.DiceTrayDecoration(modifier = Modifier.matchParentSize())
                })

            StyleCategory.BACKGROUND ->
                StylePreview(TableBackgrounds.unlockedById(picks.tableBackgroundId, access), size, selected = false, { it.scoreAreaBrush }, { background ->
                    background.Animate()
                    Canvas(modifier = Modifier.matchParentSize()) { with(background) { drawScoreAreaDecoration() } }
                })

            // Each centred by half the room it keeps over itself in a tile.
            StyleCategory.FRAME ->
                StylePreview(ScoreFrames.unlockedById(picks.scoreFrameId, access), size, selected = false, { SolidColor(MaterialTheme.colorScheme.background) }, { frame ->
                    FramePreview(frame, modifier = Modifier.offset(y = -FRAME_TAB_TOP / 2))
                })

            StyleCategory.BOX ->
                StylePreview(ScoreBoxes.unlockedById(picks.scoreBoxId, access), size, selected = false, { picks.visualTheme.background.scoreAreaBrush }, { box ->
                    BoxPreview(box, modifier = Modifier.offset(y = -BOX_PREVIEW_TOP / BOX_PREVIEW_SCALE / 2))
                })
        }
    }
}

/** The name of [category]'s pick in [picks], and its colour's where its style comes in more than one. */
@Composable
internal fun stylePickNames(category: StyleCategory, picks: SavedStyles): Pair<String, String?> {
    val catalog = category.catalog
    val id = catalog.registry.unlockedId(category.pickIn(picks), picks.access)
    val family = catalog.families.firstOrNull { it.colourOf(id) != null } ?: catalog.families.first()
    val colour = family.colourOf(id)?.takeIf { family.colours.size > 1 }
    return stringResource(family.name) to colour?.let { stringResource(it.name) }
}
