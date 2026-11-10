package net.zodac.dicefive.ui.styles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import net.zodac.dicefive.ui.common.isLargeFont
import androidx.compose.foundation.layout.height
import net.zodac.dicefive.ui.common.sizeAtNormalFont
import net.zodac.dicefive.ui.common.ShrinkThenWrapText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.min
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.game.style.StyleCategory
import net.zodac.dicefive.game.style.UnlockCount
import net.zodac.dicefive.game.style.access
import net.zodac.dicefive.navigation.StyleScrollRequest
import net.zodac.dicefive.navigation.StyleScrollRequests
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.purchases.LocalStore
import net.zodac.dicefive.purchases.StoreState
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.app_name
import net.zodac.dicefive.resources.common_cancel
import net.zodac.dicefive.resources.common_picker_choose_action
import net.zodac.dicefive.resources.common_picker_state_spoken
import net.zodac.dicefive.resources.filter_all
import net.zodac.dicefive.resources.styles_all_unlocked
import net.zodac.dicefive.resources.styles_category_background
import net.zodac.dicefive.resources.styles_category_box
import net.zodac.dicefive.resources.styles_category_count
import net.zodac.dicefive.resources.styles_category_dice
import net.zodac.dicefive.resources.styles_category_dice_cup
import net.zodac.dicefive.resources.styles_category_frame
import net.zodac.dicefive.resources.styles_category_mat
import net.zodac.dicefive.resources.styles_family_colour_cd
import net.zodac.dicefive.resources.styles_payment_pending
import net.zodac.dicefive.resources.styles_title
import net.zodac.dicefive.resources.styles_unlock_all
import net.zodac.dicefive.resources.styles_unlock_all_continue
import net.zodac.dicefive.resources.styles_unlock_all_dialog_message
import net.zodac.dicefive.resources.styles_unlock_all_dialog_title
import net.zodac.dicefive.resources.styles_unlock_all_failed
import net.zodac.dicefive.resources.styles_unlock_all_spoken
import net.zodac.dicefive.resources.styles_unlocked_count
import net.zodac.dicefive.resources.styles_unlocked_count_short
import net.zodac.dicefive.resources.styles_unlocked_only
import net.zodac.dicefive.resources.styles_unlocked_percent
import net.zodac.dicefive.ui.common.BACK_ARROW_OUTSET
import net.zodac.dicefive.ui.common.DIALOG_ICON_SIZE
import net.zodac.dicefive.ui.common.DiceFiveDialog
import net.zodac.dicefive.ui.common.FilterSwitchLabel
import net.zodac.dicefive.ui.common.FilterSwitchState
import net.zodac.dicefive.ui.common.FittedCountText
import net.zodac.dicefive.ui.common.HoldPageCover
import net.zodac.dicefive.ui.common.LocalLandscapePage
import net.zodac.dicefive.ui.common.LocalPageCover
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.common.VerticalScrollbar
import net.zodac.dicefive.ui.common.delayWhileResumed
import net.zodac.dicefive.ui.common.percentOf
import net.zodac.dicefive.ui.common.rememberFilterSwitchState
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.common.textScale
import net.zodac.dicefive.ui.icons.Symbols
import net.zodac.dicefive.ui.style.catalog
import net.zodac.dicefive.ui.theme.GoldAccent
import org.jetbrains.compose.resources.StringResource

/**
 * The Styles page: one field per category of table art ([StyleCategory]), each showing its pick - its preview, its
 * name and colour - with the category's unlocked count over it. A field opens that category's own page
 * ([StyleCategoryScreen]), where every style of it is shown and picked. Each field is a card titled in New Game's small
 * grey heading, with its pick's name and a gold chevron along its bottom.
 *
 * Three rows of two (two rows of three in landscape), each preview scaled to fill its field, so the page fits its
 * screen without scrolling.
 *
 * A long-pressed styles banner that named more than one category opens this page, with those categories' fields
 * flashed gold; opening one of them then flashes its styles there ([StyleScrollRequests]).
 */
@Composable
fun StylesScreen(viewModel: StylesViewModel, onBack: () -> Unit, onOpenCategory: (StyleCategory) -> Unit, modifier: Modifier = Modifier) {
    val saved by viewModel.savedStyles.collectAsStateWithLifecycle()
    // A banner's styles: read as the page first composes, so a fresh arrival opens already flashing, and passed on to the
    // category page a flashed field opens (CategoryFocus).
    var focus by remember { mutableStateOf(StyleScrollRequests.pending()) }
    LaunchedEffect(Unit) {
        StyleScrollRequests.requests.collect { request ->
            focus = request
            CategoryFocus.next = request
            StyleScrollRequests.consumePending()
        }
    }
    val platform = LocalPlatformServices.current
    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message -> platform.showTransientMessage(message) }
    }
    // The "Unlock all styles" purchase: its button, and a word when a purchase completes here.
    val store = LocalStore.current
    val storeState by store.state.collectAsStateWithLifecycle()
    val allUnlocked by rememberUpdatedState(stringResource(Res.string.styles_all_unlocked))
    val buyFailed = stringResource(Res.string.styles_unlock_all_failed)
    LaunchedEffect(store) {
        var before = store.state.value
        store.state.collect { state ->
            if (state == StoreState.Owned && (before is StoreState.ForSale || before == StoreState.Pending)) platform.showTransientMessage(allUnlocked)
            before = state
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Under the page, which is opaque: draws the category pages once while this one is open, so opening one isn't the
        // first time its tiles' art runs - see StyleCategoriesWarmUp. Never what the page cover waits on.
        CompositionLocalProvider(LocalPageCover provides null) {
            StyleCategoriesWarmUp(picks = saved, width = maxWidth)
        }
        StylesScaffold(
            picks = saved,
            focus = focus,
            onUnlockedCountTap = viewModel::onUnlockedCountTapped,
            storeState = storeState,
            onBuy = { if (!store.buy()) platform.showTransientMessage(buyFailed) },
            onBack = onBack,
            onOpenCategory = { category ->
                CategoryFocus.next = focus?.takeIf { request -> request.styles.any { it.categoryNoun.key == category.catalog.noun.key } }
                onOpenCategory(category)
            },
        )
    }
}

/**
 * The banner request the next category page opened should flash, handed on by the Styles page - whose own collector has
 * already taken it off [StyleScrollRequests]. Main thread only.
 */
private object CategoryFocus {
    var next: StyleScrollRequest? = null

    fun take(): StyleScrollRequest? = next.also { next = null }
}

/**
 * The Styles page with its picks: split from [StylesScreen], which has the view model, so [StylesWarmUp] can build the
 * real thing - [driftingDice] false there (see [ScreenScaffold]).
 */
@Composable
internal fun StylesScaffold(
    picks: SavedStyles?,
    onBack: () -> Unit,
    onOpenCategory: (StyleCategory) -> Unit,
    modifier: Modifier = Modifier,
    focus: StyleScrollRequest? = null,
    driftingDice: Boolean = true,
    onUnlockedCountTap: () -> Unit = {},
    storeState: StoreState = StoreState.Unavailable,
    onBuy: () -> Unit = {},
) {
    // Never once every style is owned (the store's word as cached, or pretended), nor where there's no store.
    val offerUnlockAll = picks != null && !picks.ownsAllStyles && storeState.offersUnlockAll
    // Just its mark, without its words, where they'd push the title off its centre or onto two lines (a narrow screen, a
    // large font): decided again for each screen size, font scale and label.
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val fontScale = LocalDensity.current.textScale
    var compactUnlockAll by remember(windowWidth, fontScale, storeState == StoreState.Pending) { mutableStateOf(false) }
    ScreenScaffold(
        title = stringResource(Res.string.styles_title),
        onBack = onBack,
        modifier = modifier,
        scrollable = false,
        driftingDice = driftingDice,
        wide = true,
        // In landscape, as on the Achievements page, the count follows the back arrow; otherwise it's a card over the fields.
        startActions = {
            if (LocalLandscapePage.current && picks != null) {
                UnlockedCountCard(picks.totalCount(), compact = true, onTap = onUnlockedCountTap, modifier = Modifier.padding(start = 8.dp))
            }
        },
        actions = {
            if (offerUnlockAll) UnlockAllStylesButton(storeState, compact = compactUnlockAll, onBuy = onBuy)
        },
        onTitleCrowded = { if (offerUnlockAll) compactUnlockAll = true },
    ) {
        // Nothing until the saved picks have loaded, so each field opens on its real pick. They're normally in already
        // (AppContainer.savedStyles), so the page has them from its first frame; if not, the page cover is kept up over it.
        StyleFields(
            picks = picks ?: return@ScreenScaffold HoldPageCover(),
            focus = focus,
            onUnlockedCountTap = onUnlockedCountTap,
            onOpenCategory = onOpenCategory,
        )
    }
}

/** Everything under the Styles page's app bar: the unlocked count (portrait), and a field per category filling the rest. */
@Composable
private fun ColumnScope.StyleFields(picks: SavedStyles, focus: StyleScrollRequest?, onUnlockedCountTap: () -> Unit, onOpenCategory: (StyleCategory) -> Unit) {
    val landscape = LocalLandscapePage.current
    if (!landscape) UnlockedCountCard(picks.totalCount(), compact = false, onTap = onUnlockedCountTap)
    val columns = if (landscape) LANDSCAPE_COLUMNS else PORTRAIT_COLUMNS
    val rows = StyleCategory.entries.chunked(columns)
    val focusNouns = remember(focus) { focus?.styles.orEmpty().map { it.categoryNoun.key }.toSet() }
    val density = LocalDensity.current
    val headingStyle = MaterialTheme.typography.titleSmall
    val measurer = rememberTextMeasurer()
    val access = picks.access
    val headings = StyleCategory.entries.associateWith { category ->
        val count = remember(access) { category.catalog.unlockCount(access) }
        stringResource(category.title) to stringResource(Res.string.styles_category_count, count.unlocked, count.total)
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
        // A row's headings stack their counts under their titles if any one of them doesn't fit on a line, so the row's
        // fields stay level.
        val headingWidth = with(density) { ((maxWidth - FIELD_SPACING * (columns - 1)) / columns - FIELD_PADDING * 2 - HEADING_PADDING * 2).roundToPx() }
        val stackedRows = rows.map { row ->
            row.any { category ->
                val (title, count) = headings.getValue(category)
                measurer.measure(title, headingStyle, maxLines = 1).size.width + measurer.measure(count, headingStyle, maxLines = 1).size.width +
                    with(density) { HEADING_GAP.roundToPx() } > headingWidth
            }
        }
        // At a large font, no field shorter than its words and a usable preview need: the page scrolls instead.
        val minRowHeight = with(density) {
            val heading = headingStyle.lineHeight.toDp() * (if (stackedRows.any { it }) 2 else 1)
            val names = headingStyle.lineHeight.toDp() + MaterialTheme.typography.bodySmall.lineHeight.toDp()
            // The names under the preview in portrait, beside it in landscape (see StyleCategoryField).
            val body = if (landscape) maxOf(names, MIN_PREVIEW_HEIGHT) else names + FIELD_GAP + MIN_PREVIEW_HEIGHT
            heading + FIELD_GAP + body + FIELD_VERTICAL_PADDING * 2
        }
        val fittedRowHeight = (maxHeight - FIELD_SPACING * (rows.size - 1)) / rows.size
        val scrolls = density.isLargeFont && fittedRowHeight < minRowHeight
        val pageScroll = rememberScrollState()
        Column(
            modifier = Modifier.fillMaxSize().then(if (scrolls) Modifier.verticalScroll(pageScroll) else Modifier),
            verticalArrangement = Arrangement.spacedBy(FIELD_SPACING),
        ) {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().then(if (scrolls) Modifier.height(minRowHeight) else Modifier.weight(1f)),
                    horizontalArrangement = Arrangement.spacedBy(FIELD_SPACING),
                ) {
                    for (category in row) {
                        StyleCategoryField(
                            category = category,
                            picks = picks,
                            heading = headings.getValue(category),
                            stackedHeading = stackedRows[index],
                            focus = focus.takeIf { category.catalog.noun.key in focusNouns },
                            onOpen = { onOpenCategory(category) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (scrolls) {
            // In the page's right-hand margin, beside the fields rather than over them - see VerticalScrollbar.
            VerticalScrollbar(scrollState = pageScroll, width = PAGE_MARGIN, modifier = Modifier.align(Alignment.TopEnd).offset(x = PAGE_MARGIN))
        }
    }
}

/** Every category's unlocked count in [this], added up, each colour counted as one - see [net.zodac.dicefive.ui.style.StyleCatalog.unlockCount]. */
@Composable
private fun SavedStyles.totalCount(): UnlockCount {
    val access = access
    return remember(access) { StyleCategory.entries.map { it.catalog.unlockCount(access) }.reduce(UnlockCount::plus) }
}

/**
 * One category on the Styles page, a card like New Game's: its name in the card's small grey heading, its unlocked count
 * at the heading's end (both gold once every one is unlocked), its pick's preview scaled to fill the room between
 * ([StylePickPreview]), and along the bottom the pick's name in the brand face with its colour under it, and the gold
 * chevron at the end, mirrored right to left. The whole card is the button.
 *
 * One button to TalkBack: "Dice, button, Ocean, Deep. 60 of 99 unlocked", with "Choose Dice" as its action; the heading
 * is cleared, as the button says it. [focus] set (a banner named this category), the card flashes gold for a moment.
 */
@Composable
private fun StyleCategoryField(
    category: StyleCategory,
    picks: SavedStyles,
    heading: Pair<String, String>,
    stackedHeading: Boolean,
    focus: StyleScrollRequest?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, countText) = heading
    val access = picks.access
    val count = remember(access) { category.catalog.unlockCount(access) }
    val (name, colour) = stylePickNames(category, picks)
    val spokenPick = colour?.let { stringResource(Res.string.styles_family_colour_cd, name, it) } ?: name
    val spokenState = stringResource(Res.string.common_picker_state_spoken, spokenPick, stringResource(Res.string.styles_unlocked_count, count.unlocked, count.total))
    val headerColour = if (count.complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val flash = rememberFieldFlash(focus)
    Card(
        modifier = modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = title
                stateDescription = spokenState
            }
            // The card's own clip is inside this modifier, so without one here the ripple would square off its corners.
            .clip(CardDefaults.shape)
            .drawWithContent {
                drawContent()
                drawRect(GoldAccent, alpha = flash() * FIELD_FLASH_ALPHA)
            }
            .clickable(onClickLabel = stringResource(Res.string.common_picker_choose_action, title), onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = FIELD_PADDING, vertical = FIELD_VERTICAL_PADDING),
            verticalArrangement = Arrangement.spacedBy(FIELD_GAP),
        ) {
            FieldHeading(
                title = title,
                count = countText,
                stacked = stackedHeading,
                colour = headerColour,
                modifier = Modifier.padding(horizontal = HEADING_PADDING).clearAndSetSemantics {},
            )
            // In portrait the names run along the bottom, under the preview; in landscape, where a card is wide and short,
            // beside it, so the preview has the card's height.
            if (LocalLandscapePage.current) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FieldPreview(category, picks, modifier = Modifier.weight(1f).fillMaxHeight())
                    Spacer(modifier = Modifier.width(FIELD_GAP))
                    FieldNames(name, colour, modifier = Modifier.weight(1f))
                }
            } else {
                FieldPreview(category, picks, modifier = Modifier.weight(1f).fillMaxWidth())
                FieldNames(name, colour, modifier = Modifier.fillMaxWidth().padding(start = HEADING_PADDING))
            }
        }
    }
}

/**
 * A field's preview of [category]'s pick, as large as fits the room it's given: from the room's start (the card's
 * start, under or beside the heading), so every preview lines up with its heading whatever its shape.
 */
@Composable
private fun FieldPreview(category: StyleCategory, picks: SavedStyles, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val size = category.previewSize
        val scale = min(maxWidth / size.width, maxHeight / size.height).coerceIn(MIN_PREVIEW_SCALE, MAX_PREVIEW_SCALE)
        val density = LocalDensity.current
        // Its art and any words in it (a frame's "You 24") scaled alike, as a picture of the tile: at a large font too, the
        // words being part of the picture.
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, fontScale = 1f)) {
            StylePickPreview(category, picks)
        }
    }
}

/**
 * A field's pick's [name] in the brand face, its [colour] under it (a line kept for it either way, so a row's previews
 * match), and the gold chevron at the end - as New Game's pickers have their choice.
 */
@Composable
private fun FieldNames(name: String, colour: String?, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            // At a large font, shrunk as far as its size at 100% to fit, rather than cut off.
            val nameStyle = MaterialTheme.typography.titleSmall.copy(fontFamily = SoraFontFamily)
            ShrinkThenWrapText(
                text = name,
                style = nameStyle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                minFontSize = LocalDensity.current.sizeAtNormalFont(nameStyle.fontSize),
                wrappedMaxLines = 1,
            )
            Text(
                text = colour.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Pointing onward, as New Game's pickers: tapped, this opens a page. Mirrored right to left.
        Icon(imageVector = Symbols.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * A field's heading: the category's [title] and its unlocked [count] at the line's end, or - [stacked], where a row's
 * headings don't all fit on one line (a large font) - the count on a line of its own under the title.
 */
@Composable
private fun FieldHeading(title: String, count: String, stacked: Boolean, colour: Color, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.titleSmall
    if (stacked) {
        Column(modifier = modifier.fillMaxWidth()) {
            // Shrunk as far as its size at 100% to fit, rather than cut off.
            ShrinkThenWrapText(text = title, style = style, color = colour, minFontSize = LocalDensity.current.sizeAtNormalFont(style.fontSize), wrappedMaxLines = 1)
            Text(text = count, style = style, color = colour, maxLines = 1)
        }
    } else {
        Row(modifier = modifier.fillMaxWidth()) {
            Text(text = title, style = style, color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(HEADING_GAP))
            Text(text = count, style = style, color = colour, maxLines = 1)
        }
    }
}

/** How strongly a [focus]ed field is washed in gold, from 0 to 1 over a moment - an achievement row's flash. */
@Composable
private fun rememberFieldFlash(focus: StyleScrollRequest?): () -> Float {
    var flashing by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(focus) {
        flashing = focus != null
        if (!flashing) return@LaunchedEffect
        lifecycle.delayWhileResumed(FIELD_FLASH_HOLD_MILLIS)
        flashing = false
    }
    val flash by animateFloatAsState(
        targetValue = if (flashing) 1f else 0f,
        animationSpec = if (LocalReduceMotion.current) snap() else tween(FIELD_FLASH_TRANSITION_MILLIS),
        label = "styleFieldFlash", // i18n: not translated - an animation label, not shown
    )
    return { flash }
}

/** [this] category's name, as its field's heading and its page's title say it. */
internal val StyleCategory.title: StringResource
    get() = when (this) {
        StyleCategory.DICE -> Res.string.styles_category_dice
        StyleCategory.DICE_CUP -> Res.string.styles_category_dice_cup
        StyleCategory.MAT -> Res.string.styles_category_mat
        StyleCategory.BACKGROUND -> Res.string.styles_category_background
        StyleCategory.FRAME -> Res.string.styles_category_frame
        StyleCategory.BOX -> Res.string.styles_category_box
    }

/**
 * One category's page, opened from its field on the Styles page: its unlocked count and the Unlocked switch, then every
 * one of its styles at once ([StyleCategoryTiles]), the pick outlined in gold, the page scrolling down through them.
 * Picking one saves it there and then, and the page stays open. A banner's styles of this category are brought into
 * view and flashed gold.
 */
@Composable
fun StyleCategoryScreen(viewModel: StylesViewModel, category: StyleCategory, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val saved by viewModel.savedStyles.collectAsStateWithLifecycle()
    // From the Styles page, or straight from a banner (when it's the only category the banner named).
    var focus by remember { mutableStateOf(CategoryFocus.take() ?: StyleScrollRequests.pending()) }
    LaunchedEffect(Unit) {
        StyleScrollRequests.requests.collect { request ->
            // Only one for this category: any other is for the page it's about to open.
            if (request.styles.none { it.categoryNoun.key == category.catalog.noun.key }) return@collect
            focus = request
            StyleScrollRequests.consumePending()
        }
    }
    // Kept across restarts, on this device only (SettingsRepository.unlockedOnlyStyles).
    val savedUnlockedOnly by viewModel.unlockedOnly.collectAsStateWithLifecycle()
    // The switch answers a tap at once, not once the setting has been written and read back.
    var tappedUnlockedOnly by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(savedUnlockedOnly) { tappedUnlockedOnly = null }
    // Left off the page once every style of it is unlocked; see rememberFilterSwitchState.
    val filterSwitch = rememberFilterSwitchState(
        allUnlocked = saved?.let { picks -> category.catalog.unlockCount(picks.access).complete },
        filterOn = tappedUnlockedOnly ?: savedUnlockedOnly ?: false,
    )
    val unlockedOnly = filterSwitch.applied(tappedUnlockedOnly ?: savedUnlockedOnly ?: false)
    StyleCategoryScaffold(
        category = category,
        picks = saved,
        focus = focus,
        unlockedOnly = unlockedOnly,
        filterSwitch = filterSwitch,
        onUnlockedOnlyChange = { tappedUnlockedOnly = it; viewModel.setUnlockedOnly(it) },
        onBack = onBack,
        onSelect = { id -> viewModel.setStyleId(category, id) },
        modifier = modifier,
    )
}

/** A category's page with its picks: split from [StyleCategoryScreen] so [StylesWarmUp] can build the real thing. */
@Composable
internal fun StyleCategoryScaffold(
    category: StyleCategory,
    picks: SavedStyles?,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    focus: StyleScrollRequest? = null,
    driftingDice: Boolean = true,
    unlockedOnly: Boolean = false,
    filterSwitch: FilterSwitchState = FilterSwitchState(shown = true, enabled = true),
    onUnlockedOnlyChange: (Boolean) -> Unit = {},
) {
    val count = picks?.let { remember(it.access) { category.catalog.unlockCount(it.access) } }
    ScreenScaffold(
        title = stringResource(category.title),
        onBack = onBack,
        modifier = modifier,
        scrollable = false,
        driftingDice = driftingDice,
        wide = true,
        // In landscape, as on the Styles page, the count follows the back arrow and the switch ends level with the
        // tiles' end edge; otherwise they're a row over the tiles.
        startActions = {
            if (LocalLandscapePage.current && count != null) UnlockedCountCard(count, compact = true, modifier = Modifier.padding(start = 8.dp))
        },
        actions = {
            if (LocalLandscapePage.current && count != null && filterSwitch.shown) {
                Box(modifier = Modifier.padding(start = 8.dp, end = BACK_ARROW_OUTSET)) { UnlockedOnlySwitch(unlockedOnly, filterSwitch.enabled, onUnlockedOnlyChange) }
            }
        },
    ) {
        val loaded = picks ?: return@ScreenScaffold HoldPageCover()
        if (!LocalLandscapePage.current && count != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnlockedCountCard(count, compact = false, modifier = Modifier.weight(1f))
                if (filterSwitch.shown) UnlockedOnlySwitch(unlockedOnly, filterSwitch.enabled, onUnlockedOnlyChange)
            }
        }
        // One text measurer for every tile's name, kept across openings - see rememberTileLabelMeasurer.
        val labelMeasurer = rememberTileLabelMeasurer()
        val pageScroll = rememberScrollState()
        CompositionLocalProvider(LocalTileLabelMeasurer provides labelMeasurer) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.verticalScroll(pageScroll)) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        StyleCategoryTiles(category = category, picks = loaded, onSelect = onSelect, focus = focus, unlockedOnly = unlockedOnly)
                    }
                }
                // In the page's right-hand margin, beside the tiles rather than over them - see VerticalScrollbar.
                VerticalScrollbar(
                    scrollState = pageScroll,
                    width = PAGE_MARGIN,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = PAGE_MARGIN),
                )
            }
        }
    }
}

/**
 * How many styles are unlocked out of how many there are ([count]), each colour counted as one - see
 * [net.zodac.dicefive.ui.style.StyleCatalog.unlockCount]. Drawn as the Achievements page's count is: a full-width card
 * over the page, or [compact] in a landscape app bar.
 *
 * [onTap] is a debug build's hidden switch ([StylesViewModel.onUnlockedCountTapped]), on the Styles page's count: a raw
 * gesture, with no ripple and nothing for TalkBack, as the Achievements page's count is, since in any other build it does
 * nothing.
 */
@Composable
private fun UnlockedCountCard(count: UnlockCount, compact: Boolean, modifier: Modifier = Modifier, onTap: (() -> Unit)? = null) {
    val tap = if (onTap == null) Modifier else Modifier.pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) }
    Card(modifier = modifier.then(tap)) {
        FittedCountText(
            full = stringResource(Res.string.styles_unlocked_count, count.unlocked, count.total),
            short = stringResource(Res.string.styles_unlocked_count_short, count.unlocked, count.total),
            percent = stringResource(Res.string.styles_unlocked_percent, percentOf(count.unlocked, count.total)),
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 12.dp).then(if (compact) Modifier else Modifier.fillMaxWidth()),
        )
    }
}

/**
 * The "Unlock all styles" purchase ([net.zodac.dicefive.purchases.ProductKey.UNLOCK_ALL_STYLES]): its mark and "Unlock All",
 * opening a dialog that says what the purchase is, and that everything it unlocks can be earned for free; its Continue
 * opens the store's own sheet, which shows the price. Usable once the store has the product ([StoreState.ForSale] with its
 * price); disabled while it's [StoreState.Loading], and while it's [StoreState.Pending], when it says "Payment pending"
 * instead. [compact], it's the mark alone. TalkBack hears "Unlock all styles", with "Payment pending" as its state.
 */
@Composable
private fun UnlockAllStylesButton(state: StoreState, compact: Boolean, onBuy: () -> Unit) {
    val pending = state == StoreState.Pending
    val spoken = stringResource(Res.string.styles_unlock_all_spoken)
    val pendingText = stringResource(Res.string.styles_payment_pending)
    var explaining by remember { mutableStateOf(false) }
    if (explaining) {
        val primary = MaterialTheme.colorScheme.primary
        DiceFiveDialog(
            icon = { UnlockAllStylesIcon(size = DIALOG_ICON_SIZE, tint = primary) },
            title = stringResource(Res.string.styles_unlock_all_dialog_title),
            message = AnnotatedString(stringResource(Res.string.styles_unlock_all_dialog_message, stringResource(Res.string.app_name))),
            confirmLabel = stringResource(Res.string.styles_unlock_all_continue),
            onConfirm = {
                explaining = false
                onBuy()
            },
            onDismissRequest = { explaining = false },
            dismissLabel = stringResource(Res.string.common_cancel),
            onDismiss = { explaining = false },
        )
    }
    FilledTonalButton(
        onClick = { explaining = true },
        enabled = state is StoreState.ForSale && state.price != null,
        contentPadding = if (compact) PaddingValues(horizontal = 12.dp) else PaddingValues(start = 8.dp, end = 12.dp),
        modifier = Modifier.semantics {
            if (pending) stateDescription = pendingText
            if (compact) contentDescription = spoken
        },
    ) {
        UnlockAllStylesIcon(size = UNLOCK_ALL_ICON_SIZE, tint = LocalContentColor.current)
        if (!compact) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (pending) pendingText else stringResource(Res.string.styles_unlock_all),
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
            )
        }
    }
}

/** Whether the store's answer offers the purchase at all: not where there's no store, and not once it's owned. */
private val StoreState.offersUnlockAll: Boolean
    get() = this is StoreState.Loading || this is StoreState.ForSale || this is StoreState.Pending

/** The purchase's mark on its button: an icon's size. */
private val UNLOCK_ALL_ICON_SIZE = 24.dp

/** The switch that hides every style not yet unlocked, drawn as the Achievements page's Locked only switch is. */
@Composable
private fun UnlockedOnlySwitch(unlockedOnly: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // One target for the label and switch, read as "All, switch, off" or "Unlocked, switch, on". No ripple, as on the Achievements page.
        modifier = Modifier.toggleable(
            value = unlockedOnly,
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Switch,
            onValueChange = onChange,
        ),
    ) {
        FilterSwitchLabel(unlockedOnly, off = stringResource(Res.string.filter_all), on = stringResource(Res.string.styles_unlocked_only))
        Switch(checked = unlockedOnly, onCheckedChange = null, enabled = enabled)
    }
}


/** Fields to a row on the Styles page: portrait, and landscape. */
private const val PORTRAIT_COLUMNS = 2
private const val LANDSCAPE_COLUMNS = 3

// Between the Styles page's fields, across and down.
private val FIELD_SPACING = 10.dp

// A field's card: its padding at the sides and top and bottom, its heading and names' further in, the gap between its
// title and count, and the gap between heading, preview and names.
private val FIELD_PADDING = 10.dp
private val FIELD_VERTICAL_PADDING = 8.dp
private val HEADING_PADDING = 4.dp
private val HEADING_GAP = 4.dp
private val FIELD_GAP = 6.dp

// The least a field's preview is given at a large font, before the page scrolls instead.
private val MIN_PREVIEW_HEIGHT = 96.dp

// A field's preview is scaled from its tile's size to fill the field, within these.
private const val MIN_PREVIEW_SCALE = 0.5f
private const val MAX_PREVIEW_SCALE = 2.5f

// A flashed field: how strongly it's washed in gold, how long that holds, and how long it takes to fade - as a tile's.
private const val FIELD_FLASH_ALPHA = 0.45f
private const val FIELD_FLASH_HOLD_MILLIS = 900L
private const val FIELD_FLASH_TRANSITION_MILLIS = 400
