package net.zodac.dicefive.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.data.settings.DiceRollStyle
import net.zodac.dicefive.data.settings.FrameRate
import net.zodac.dicefive.data.settings.MotionGroup
import net.zodac.dicefive.data.settings.MotionSettings
import net.zodac.dicefive.data.settings.ScreenOrientation
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_dropdown_collapsed
import net.zodac.dicefive.resources.common_dropdown_expanded
import net.zodac.dicefive.resources.settings_advanced
import net.zodac.dicefive.resources.settings_advanced_gameplay
import net.zodac.dicefive.resources.settings_advanced_misc
import net.zodac.dicefive.resources.settings_animations
import net.zodac.dicefive.resources.settings_animations_high
import net.zodac.dicefive.resources.settings_animations_high_short
import net.zodac.dicefive.resources.settings_animations_low
import net.zodac.dicefive.resources.settings_animations_low_short
import net.zodac.dicefive.resources.settings_animations_medium
import net.zodac.dicefive.resources.settings_animations_medium_short
import net.zodac.dicefive.resources.settings_animations_off
import net.zodac.dicefive.resources.settings_frame_rate
import net.zodac.dicefive.resources.settings_frame_rate_max
import net.zodac.dicefive.resources.settings_frame_rate_max_spoken
import net.zodac.dicefive.resources.settings_frame_rate_spoken
import net.zodac.dicefive.resources.settings_graphics
import net.zodac.dicefive.resources.settings_motion_banners
import net.zodac.dicefive.resources.settings_motion_celebrations
import net.zodac.dicefive.resources.settings_motion_cup_shake
import net.zodac.dicefive.resources.settings_motion_dice_roll
import net.zodac.dicefive.resources.settings_motion_dice_roll_rate
import net.zodac.dicefive.resources.settings_motion_menus
import net.zodac.dicefive.resources.settings_motion_score_glow
import net.zodac.dicefive.resources.settings_motion_scores
import net.zodac.dicefive.resources.settings_orientation
import net.zodac.dicefive.resources.settings_orientation_device
import net.zodac.dicefive.resources.settings_orientation_landscape
import net.zodac.dicefive.resources.settings_orientation_portrait
import net.zodac.dicefive.resources.settings_roll_in_place
import net.zodac.dicefive.resources.settings_roll_tossed
import net.zodac.dicefive.resources.styles_category_background
import net.zodac.dicefive.resources.styles_category_box
import net.zodac.dicefive.resources.styles_category_dice
import net.zodac.dicefive.resources.styles_category_dice_cup
import net.zodac.dicefive.resources.styles_category_mat
import net.zodac.dicefive.resources.styles_title
import net.zodac.dicefive.ui.common.FittedSegmentedChoiceRow
import net.zodac.dicefive.ui.common.HoldPageCover
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.common.TwoPanes
import net.zodac.dicefive.ui.common.localised
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.stringResource
import net.zodac.dicefive.ui.icons.Symbols

/**
 * One pick-one setting under a title: its icon and title, then a segmented row of its [options] across the card
 * ([FittedSegmentedChoiceRow]). TalkBack hears the title, then each option as a selectable button ("Medium, selected"). With [enabled] false the row is greyed and
 * takes no taps, still showing its pick.
 */
@Composable
private fun <T> TitledChoice(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    compactLabel: @Composable (T) -> String = optionLabel,
    compactGlyph: (T) -> ImageVector? = { null },
    spokenLabel: @Composable (T) -> String = optionLabel,
    enabled: Boolean = true,
) {
    val colours = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = colours.onSurfaceVariant.copy(alpha = if (enabled) 1f else DISABLED_ALPHA))
            SettingLabel(title, modifier = Modifier.weight(1f), color = if (enabled) colours.onSurface else colours.onSurface.copy(alpha = DISABLED_ALPHA))
        }
        FittedSegmentedChoiceRow(
            options = options,
            selected = selected,
            onSelect = onSelect,
            label = optionLabel,
            compactLabel = compactLabel,
            compactGlyph = compactGlyph,
            spokenLabel = spokenLabel,
            enabled = enabled,
        )
    }
}

/** M3's disabled content alpha, for a title whose row is disabled. */
private const val DISABLED_ALPHA = 0.38f

@Composable
private fun animationLevelLabel(level: AnimationLevel?): String = stringResource(
    when (level) {
        AnimationLevel.HIGH -> Res.string.settings_animations_high
        AnimationLevel.MEDIUM -> Res.string.settings_animations_medium
        AnimationLevel.LOW -> Res.string.settings_animations_low
        AnimationLevel.OFF, null -> Res.string.settings_animations_off
    },
)

/** The level's initial for a narrow screen - its own string, as a translation's initials can collide. Off is a glyph. */
@Composable
private fun animationLevelShortLabel(level: AnimationLevel?): String = when (level) {
    AnimationLevel.HIGH -> stringResource(Res.string.settings_animations_high_short)
    AnimationLevel.MEDIUM -> stringResource(Res.string.settings_animations_medium_short)
    AnimationLevel.LOW -> stringResource(Res.string.settings_animations_low_short)
    AnimationLevel.OFF, null -> animationLevelLabel(level)
}

@Composable
private fun orientationLabel(orientation: ScreenOrientation): String = stringResource(
    when (orientation) {
        ScreenOrientation.DEVICE -> Res.string.settings_orientation_device
        ScreenOrientation.PORTRAIT -> Res.string.settings_orientation_portrait
        ScreenOrientation.LANDSCAPE -> Res.string.settings_orientation_landscape
    },
)

/** Each orientation as a phone turned that way (or turning), for a narrow screen: an initial would mean nothing. */
private fun orientationGlyph(orientation: ScreenOrientation): ImageVector = when (orientation) {
    ScreenOrientation.DEVICE -> Symbols.ScreenRotation
    ScreenOrientation.PORTRAIT -> Symbols.StayCurrentPortrait
    ScreenOrientation.LANDSCAPE -> Symbols.StayCurrentLandscape
}

/** A frame rate as drawn: "Off", "30fps", "60fps", "Max". */
@Composable
private fun frameRateLabel(rate: FrameRate): String = when (rate) {
    FrameRate.OFF -> stringResource(Res.string.settings_animations_off)
    FrameRate.MAX -> stringResource(Res.string.settings_frame_rate_max)
    FrameRate.FPS_30, FrameRate.FPS_60 -> stringResource(Res.string.settings_frame_rate, checkNotNull(rate.framesPerSecond))
}

/** A frame rate for a narrow screen: the number alone, Off as a glyph. */
@Composable
private fun frameRateShortLabel(rate: FrameRate): String = when (rate) {
    FrameRate.FPS_30, FrameRate.FPS_60 -> checkNotNull(rate.framesPerSecond).localised()
    FrameRate.OFF, FrameRate.MAX -> frameRateLabel(rate)
}

/** A frame rate as a screen reader says it: "30 frames a second", "Maximum". */
@Composable
private fun frameRateSpoken(rate: FrameRate): String = when (rate) {
    FrameRate.FPS_30, FrameRate.FPS_60 -> checkNotNull(rate.framesPerSecond).let { pluralStringResource(Res.plurals.settings_frame_rate_spoken, it, it) }
    FrameRate.MAX -> stringResource(Res.string.settings_frame_rate_max_spoken)
    FrameRate.OFF -> frameRateLabel(rate)
}

@Composable
private fun rollStyleLabel(style: DiceRollStyle): String = stringResource(
    when (style) {
        DiceRollStyle.TOSSED -> Res.string.settings_roll_tossed
        DiceRollStyle.IN_PLACE -> Res.string.settings_roll_in_place
        DiceRollStyle.OFF -> Res.string.settings_animations_off
    },
)

/** Each [MotionGroup]'s row in the Advanced settings: its icon and name - a style's as the Styles page names it. */
private val MotionGroup.icon: ImageVector
    get() = when (this) {
        MotionGroup.DICE -> Symbols.Casino
        MotionGroup.DICE_CUP -> Symbols.LocalCafe
        MotionGroup.MAT -> Symbols.Texture
        MotionGroup.BACKGROUND -> Symbols.Wallpaper
        MotionGroup.SCORE_BOX -> Symbols.GridView
        MotionGroup.MENUS -> Symbols.SportsEsports
        MotionGroup.BANNERS -> Symbols.MilitaryTech
        MotionGroup.SCORES -> Symbols.Numbers
        MotionGroup.CELEBRATIONS -> Symbols.Stars
        MotionGroup.SCORE_GLOW -> Symbols.Star
        MotionGroup.CUP_SHAKE -> Symbols.SyncAlt
        MotionGroup.DICE_ROLL -> Symbols.Speed
    }

@Composable
private fun MotionGroup.title(): String = stringResource(
    when (this) {
        MotionGroup.DICE -> Res.string.styles_category_dice
        MotionGroup.DICE_CUP -> Res.string.styles_category_dice_cup
        MotionGroup.MAT -> Res.string.styles_category_mat
        MotionGroup.BACKGROUND -> Res.string.styles_category_background
        MotionGroup.SCORE_BOX -> Res.string.styles_category_box
        MotionGroup.MENUS -> Res.string.settings_motion_menus
        MotionGroup.BANNERS -> Res.string.settings_motion_banners
        MotionGroup.SCORES -> Res.string.settings_motion_scores
        MotionGroup.CELEBRATIONS -> Res.string.settings_motion_celebrations
        MotionGroup.SCORE_GLOW -> Res.string.settings_motion_score_glow
        MotionGroup.CUP_SHAKE -> Res.string.settings_motion_cup_shake
        MotionGroup.DICE_ROLL -> Res.string.settings_motion_dice_roll_rate
    },
)

/**
 * The Graphics page, opened from Settings: the Orientation and the Animations level, each under its title, then the
 * Advanced settings, closed until opened - a frame rate for each [MotionGroup] (Off, 30fps, 60fps or Max; the dice's
 * roll's without Off) and the dice's roll (Off, In place or Tossed). The Advanced settings start as the level's preset; changing one leaves no level picked, and picking a level
 * puts them all back to its preset.
 */
@Composable
fun GraphicsScreen(viewModel: GraphicsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val loaded by viewModel.settings.collectAsStateWithLifecycle()
    ScreenScaffold(
        title = stringResource(Res.string.settings_graphics),
        onBack = onBack,
        modifier = modifier,
        scrollable = true,
        wide = true,
    ) {
        // Nothing but the title bar until the saved settings are back, as Settings does.
        val settings = loaded ?: return@ScreenScaffold HoldPageCover()
        val level = settings.level
        val motion = settings.motion
        val choicesCard = @Composable {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    TitledChoice(
                        icon = Symbols.ScreenRotation,
                        title = stringResource(Res.string.settings_orientation),
                        options = ScreenOrientation.entries,
                        selected = settings.orientation,
                        onSelect = viewModel::setScreenOrientation,
                        optionLabel = { orientationLabel(it) },
                        compactGlyph = ::orientationGlyph,
                    )
                    TitledChoice<AnimationLevel?>(
                        icon = Symbols.AutoAwesome,
                        title = stringResource(Res.string.settings_animations),
                        // Least to most, Off first: from the start edge, so left to right here and mirrored in a right-to-left language.
                        options = AnimationLevel.entries.reversed(),
                        // None picked while the Advanced settings match no level: no word for it, by the maintainer's choice.
                        selected = level,
                        onSelect = { it?.let(viewModel::setAnimationLevel) },
                        optionLabel = { animationLevelLabel(it) },
                        compactLabel = { animationLevelShortLabel(it) },
                        // "Off" as a symbol when there's no room for words: an initial would be "O", or a translation's own.
                        compactGlyph = { if (it == AnimationLevel.OFF) Symbols.MotionPhotosOff else null },
                    )
                }
            }
        }
        // Side by side in landscape: the titled choices, then the Advanced settings.
        TwoPanes(start = { choicesCard() }, end = { AdvancedSettings(motion, viewModel) })
    }
}

/** How long an Advanced settings card takes to open or close, as a modifier's settings do (none under reduced motion). */
private const val ADVANCED_OPEN_MILLIS = 220

/**
 * The Advanced settings: their title (a heading, in the page's text colour), then three cards, each closed each time
 * the page opens (kept open across a rotation). Gameplay, what moves on the game screen: Cup shake, the Dice roll
 * (Off, In place, or Tossed) and, under it, its frame rate, which has no Off (the roll's own is its off) and is locked,
 * its pick kept, while the roll is off, then Score glow and Scores. Styles: each style's own decoration
 * ([MotionGroup.decoration]), in the Styles page's order. Miscellaneous: Achievement banners (on every screen),
 * Celebrations (the results), and Menus & pages. Gameplay's and Miscellaneous' rows are in their names' English
 * alphabetical order.
 */
@Composable
private fun AdvancedSettings(motion: MotionSettings, viewModel: GraphicsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.settings_advanced),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
        )
        CollapsibleCard(stringResource(Res.string.settings_advanced_gameplay)) {
            FrameRateChoice(MotionGroup.CUP_SHAKE, FrameRate.entries, motion, viewModel)
            // Least to most, Off first, as every row runs.
            TitledChoice(
                icon = Symbols.Casino,
                title = stringResource(Res.string.settings_motion_dice_roll),
                options = DiceRollStyle.entries,
                selected = motion.rollStyle,
                onSelect = viewModel::setRollStyle,
                optionLabel = { rollStyleLabel(it) },
                compactGlyph = { if (it == DiceRollStyle.OFF) Symbols.MotionPhotosOff else null },
            )
            FrameRateChoice(MotionGroup.DICE_ROLL, FrameRate.entries - FrameRate.OFF, motion, viewModel, enabled = motion.rollStyle != DiceRollStyle.OFF)
            for (group in listOf(MotionGroup.SCORE_GLOW, MotionGroup.SCORES)) {
                FrameRateChoice(group, FrameRate.entries, motion, viewModel)
            }
        }
        CollapsibleCard(stringResource(Res.string.styles_title)) {
            // In the Styles page's order, not alphabetical.
            for (group in MotionGroup.decoration) FrameRateChoice(group, FrameRate.entries, motion, viewModel)
        }
        CollapsibleCard(stringResource(Res.string.settings_advanced_misc)) {
            for (group in listOf(MotionGroup.BANNERS, MotionGroup.CELEBRATIONS, MotionGroup.MENUS)) {
                FrameRateChoice(group, FrameRate.entries, motion, viewModel)
            }
        }
    }
}

/**
 * A card of [rows] under its [title], closed until its title is tapped. The title row is the card's full width and
 * runs to its top edge, so its press ripple fills the whole header: a heading with an arrow at its end pointing down
 * (closed) or up (open). TalkBack hears "Gameplay, Collapsed, heading, button".
 */
@Composable
private fun CollapsibleCard(title: String, rows: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current
    val state = stringResource(if (expanded) Res.string.common_dropdown_expanded else Res.string.common_dropdown_collapsed)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = COLLAPSIBLE_HEADER_HEIGHT)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics {
                    heading()
                    stateDescription = state
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // In the page's text colour, as the Advanced settings heading is: not gold.
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Symbols.KeyboardArrowUp else Symbols.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduceMotion) EnterTransition.None else expandVertically(tween(ADVANCED_OPEN_MILLIS)),
            exit = if (reduceMotion) ExitTransition.None else shrinkVertically(tween(ADVANCED_OPEN_MILLIS)),
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) { rows() }
        }
    }
}

/** A closed card's height: its title row alone, as tall as a settings row with room round its title. */
private val COLLAPSIBLE_HEADER_HEIGHT = 56.dp

/** [group]'s row: its icon and name over [options], [group]'s rate picked. */
@Composable
private fun FrameRateChoice(group: MotionGroup, options: List<FrameRate>, motion: MotionSettings, viewModel: GraphicsViewModel, enabled: Boolean = true) {
    TitledChoice(
        icon = group.icon,
        title = group.title(),
        options = options,
        selected = motion.rate(group),
        onSelect = { viewModel.setFrameRate(group, it) },
        optionLabel = { frameRateLabel(it) },
        compactLabel = { frameRateShortLabel(it) },
        compactGlyph = { if (it == FrameRate.OFF) Symbols.MotionPhotosOff else null },
        spokenLabel = { frameRateSpoken(it) },
        enabled = enabled,
    )
}
