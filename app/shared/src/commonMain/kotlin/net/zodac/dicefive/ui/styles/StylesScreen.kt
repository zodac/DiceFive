package net.zodac.dicefive.ui.styles

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.game.style.StyleAccess
import net.zodac.dicefive.game.style.StyleCategory
import net.zodac.dicefive.game.style.StyleUnlock
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
import net.zodac.dicefive.resources.filter_all
import net.zodac.dicefive.resources.styles_all_unlocked
import net.zodac.dicefive.resources.styles_payment_pending
import net.zodac.dicefive.resources.styles_title
import net.zodac.dicefive.resources.styles_unlock_all
import net.zodac.dicefive.resources.styles_unlock_all_continue
import net.zodac.dicefive.resources.styles_unlock_all_dialog_message
import net.zodac.dicefive.resources.styles_unlock_all_dialog_title
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
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.VerticalScrollbar
import net.zodac.dicefive.ui.common.percentOf
import net.zodac.dicefive.ui.common.rememberFilterSwitchState
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.style.DiceCupStyle
import net.zodac.dicefive.ui.style.DiceMat
import net.zodac.dicefive.ui.style.DiceStyle
import net.zodac.dicefive.ui.style.ScoreFrame
import net.zodac.dicefive.ui.style.StyleCatalog
import net.zodac.dicefive.ui.style.StyleFamily
import net.zodac.dicefive.ui.style.TableBackground
import net.zodac.dicefive.ui.style.catalog

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
    // Kept across restarts, on this device only (SettingsRepository.unlockedOnlyStyles).
    val savedUnlockedOnly by viewModel.unlockedOnly.collectAsStateWithLifecycle()
    // The switch answers a tap at once, not once the setting has been written and read back.
    var tappedUnlockedOnly by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(savedUnlockedOnly) { tappedUnlockedOnly = null }
    // Left off the page once every style is unlocked; see rememberFilterSwitchState.
    val filterSwitch = rememberFilterSwitchState(
        allUnlocked = saved?.let { picks -> StyleCategory.entries.map { it.catalog.unlockCount(picks.access) }.reduce(UnlockCount::plus).let { it.unlocked >= it.total } },
        filterOn = tappedUnlockedOnly ?: savedUnlockedOnly ?: false,
    )
    val unlockedOnly = filterSwitch.applied(tappedUnlockedOnly ?: savedUnlockedOnly ?: false)
    val platform = LocalPlatformServices.current
    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message -> platform.showTransientMessage(message) }
    }
    // The "Unlock all styles" purchase: its button, and a word when a purchase completes here.
    val store = LocalStore.current
    val storeState by store.state.collectAsStateWithLifecycle()
    val allUnlocked by rememberUpdatedState(stringResource(Res.string.styles_all_unlocked))
    LaunchedEffect(store) {
        var before = store.state.value
        store.state.collect { state ->
            if (state == StoreState.Owned && (before is StoreState.ForSale || before == StoreState.Pending)) platform.showTransientMessage(allUnlocked)
            before = state
        }
    }
    StylesScaffold(
        picks = saved,
        focus = focus,
        unlockedOnly = unlockedOnly,
        filterSwitch = filterSwitch,
        onUnlockedOnlyChange = { tappedUnlockedOnly = it; viewModel.setUnlockedOnly(it) },
        onUnlockedCountTap = viewModel::onUnlockedCountTapped,
        storeState = storeState,
        onBuy = store::buy,
        onBack = onBack,
        onSelect = { category -> { id -> viewModel.setStyleId(category, id) } },
        modifier = modifier,
    )
}

/**
 * The Styles screen with its picks: split from [StylesScreen], which has the view model, so
 * [StylesWarmUp] can build the real thing - [driftingDice] false there (see [ScreenScaffold]).
 */
@Composable
internal fun StylesScaffold(
    picks: SavedStyles?,
    focus: StyleScrollRequest?,
    onBack: () -> Unit,
    onSelect: (StyleCategory) -> (String) -> Unit,
    modifier: Modifier = Modifier,
    driftingDice: Boolean = true,
    unlockedOnly: Boolean = false,
    filterSwitch: FilterSwitchState = FilterSwitchState(shown = true, enabled = true),
    onUnlockedOnlyChange: (Boolean) -> Unit = {},
    onUnlockedCountTap: () -> Unit = {},
    storeState: StoreState = StoreState.Unavailable,
    onBuy: () -> Unit = {},
    pageScale: Float? = null,
    onPageMeasured: ((PageMeasure) -> Unit)? = null,
) {
    // Never once every style is owned (the store's word as cached, or pretended), nor where there's no store.
    val offerUnlockAll = picks != null && !picks.ownsAllStyles && storeState.offersUnlockAll
    // Just its mark, without its words, where they'd push the title off its centre or onto two lines (a narrow screen, a
    // large font): decided again for each screen size, font scale and label.
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val fontScale = LocalDensity.current.fontScale
    var compactUnlockAll by remember(windowWidth, fontScale, storeState == StoreState.Pending) { mutableStateOf(false) }
    ScreenScaffold(
        title = stringResource(Res.string.styles_title),
        onBack = onBack,
        modifier = modifier,
        scrollable = false,
        driftingDice = driftingDice,
        wide = true,
        // In landscape, as on the Achievements page, the count follows the back arrow and the switch ends level with the cards'
        // end edge, either side of the title; otherwise they're a row over the cards (StylesPage).
        startActions = {
            if (LocalLandscapePage.current && picks != null) StylesUnlockedCount(picks.access, compact = true, onTap = onUnlockedCountTap, modifier = Modifier.padding(start = 8.dp))
        },
        actions = {
            if (offerUnlockAll) UnlockAllStylesButton(storeState, compact = compactUnlockAll, onBuy = onBuy)
            if (LocalLandscapePage.current && picks != null && filterSwitch.shown) {
                Box(modifier = Modifier.padding(start = 8.dp, end = BACK_ARROW_OUTSET)) { UnlockedOnlySwitch(unlockedOnly, filterSwitch.enabled, onUnlockedOnlyChange) }
            }
        },
        onTitleCrowded = { if (offerUnlockAll) compactUnlockAll = true },
    ) {
        // Nothing until the saved picks have loaded, so each row can open scrolled to its real pick.
        // They're normally in already (AppContainer.savedStyles), so the page has them from its first frame; if
        // not, the page cover is kept up over it meanwhile.
        StylesPage(
            picks = picks ?: return@ScreenScaffold HoldPageCover(),
            focus = focus,
            onSelect = onSelect,
            unlockedOnly = unlockedOnly,
            filterSwitch = filterSwitch,
            onUnlockedOnlyChange = onUnlockedOnlyChange,
            onUnlockedCountTap = onUnlockedCountTap,
            scale = pageScale,
            onMeasured = onPageMeasured,
        )
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
    unlockedOnly: Boolean,
    filterSwitch: FilterSwitchState,
    onUnlockedOnlyChange: (Boolean) -> Unit,
    onUnlockedCountTap: () -> Unit,
    scale: Float? = null,
    onMeasured: ((PageMeasure) -> Unit)? = null,
) {
    // One text measurer for every tile's name, kept across openings - see rememberTileLabelMeasurer.
    val labelMeasurer = rememberTileLabelMeasurer()
    CompositionLocalProvider(LocalTileLabelMeasurer provides labelMeasurer) {
        // Sized to fit one screen, but free to scroll when it can't - a small phone, a large font,
        // an open gallery - rather than cutting the last category off out of reach.
        val pageScroll = rememberScrollState()
        val columns = if (LocalLandscapePage.current) LANDSCAPE_COLUMNS else 1
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
        if (!LocalLandscapePage.current) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StylesUnlockedCount(picks.access, compact = false, onTap = onUnlockedCountTap, modifier = Modifier.weight(1f))
                if (filterSwitch.shown) UnlockedOnlySwitch(unlockedOnly, filterSwitch.enabled, onUnlockedOnlyChange)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            CompositionLocalProvider(LocalDensity provides cardsDensity) {
                Column(modifier = Modifier.verticalScroll(pageScroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Two categories to a row in landscape, one otherwise; a row's last place left empty so its card keeps the
                    // others' width.
                    for (row in StyleCategory.entries.chunked(columns)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (category in row) {
                                Box(modifier = Modifier.weight(1f)) {
                                    StyleCategorySection(category = category, picks = picks, onSelect = onSelect(category), focus = focus, unlockedOnly = unlockedOnly)
                                }
                            }
                            repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
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

/**
 * How many styles are unlocked, out of how many the page offers, each colour counted as one - see [StyleCatalog.unlockCount].
 * Drawn as the Achievements page's count is: a full-width card over the cards, or [compact] in a landscape app bar.
 *
 * [onTap] is a debug build's hidden switch ([StylesViewModel.onUnlockedCountTapped]): a raw gesture, with no ripple and nothing
 * for TalkBack, as the Achievements page's count is, since in any other build it does nothing.
 */
@Composable
private fun StylesUnlockedCount(access: StyleAccess, compact: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val count = remember(access) { StyleCategory.entries.map { it.catalog.unlockCount(access) }.reduce(UnlockCount::plus) }
    Card(modifier = modifier.pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) }) {
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

/** Categories to a row in landscape. */
private const val LANDSCAPE_COLUMNS = 2
