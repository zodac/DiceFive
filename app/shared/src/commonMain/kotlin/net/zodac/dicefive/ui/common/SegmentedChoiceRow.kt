package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A single-choice segmented button row for a small, mutually exclusive option set that fits one
 * line - theme, player count, AI difficulty, and anything similar in future. Every option's
 * default checkmark-on-select icon is dropped: at the widths this app uses this component,
 * reserving space for that icon crowded the label out instead (worst case, "Medium" in the AI
 * difficulty row - see `.claude/UI.md`). Selection is still clear from the segment's own
 * colour/border change, same as it is for a [androidx.compose.material3.FilterChip].
 *
 * [glyph] swaps an option's text for an icon, for an option a symbol says better than a word (the
 * turn timer's "no timer"). [label] is still required for that option: it becomes the icon's
 * content description, so a screen reader announces the same thing the text would have said.
 *
 * [spokenLabel] is what a screen reader says for a text option when the drawn [label] is an abbreviation.
 *
 * [enabled] false greys out every segment and ignores taps, while still showing which one is
 * picked - for a choice the rest of the form has overridden (the turn timer under a game mode that
 * sets its own).
 *
 * [brandFont] sets the text labels in [SoraFontFamily] (bold, its only weight) - the New Game screen's player count
 * and the modifiers' values, which read as the game's own marks; a plain form choice leaves it off.
 *
 * [contentPadding] is the segment's own side padding (M3's 12dp by default): the New Game difficulty row
 * tightens it so "Medium" still fits, and sits centred, once the colour circle has taken a share of the row.
 */
@Composable
fun <T> SegmentedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = LocalTextStyle.current,
    glyph: (T) -> ImageVector? = { null },
    enabled: Boolean = true,
    contentPadding: PaddingValues = SegmentedButtonDefaults.ContentPadding,
    spokenLabel: @Composable (T) -> String = label,
    brandFont: Boolean = false,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            val optionGlyph = glyph(option)
            val spoken = spokenLabel(option)
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                contentPadding = contentPadding,
                label = {
                    if (optionGlyph != null) {
                        Icon(
                            imageVector = optionGlyph,
                            contentDescription = label(option),
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    } else {
                        // One unbroken line: a label too wide for its segment at a large font spills into the
                        // segment's own padding rather than breaking mid-word ("Medi-um").
                        Text(
                            text = label(option),
                            style = labelStyle,
                            fontFamily = if (brandFont) SoraFontFamily else null,
                            fontWeight = if (brandFont) FontWeight.Bold else null,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                            // What a screen reader says, when the drawn label is a shortened one.
                            modifier = Modifier.semantics { contentDescription = spoken },
                        )
                    }
                },
            )
        }
    }
}

/**
 * A [SegmentedChoiceRow] across its whole width whose text labels are sized together so they always match:
 * [maxFontSize] when the widest fits its segment, stepping down to [MIN_READABLE_FONT_SIZE] when it doesn't, and
 * only if even that is too wide - a narrow screen, a long translation - every option becomes its compact form at
 * that size: [compactGlyph]'s icon where it gives one (the animation level's "Off"), otherwise [compactLabel] - the
 * label's initial ("E / M / H") unless a translation needs its own, where two initials would be the same letter. A
 * screen reader still hears the full label. [labelPadding] is each segment's side padding.
 */
@Composable
fun <T> FittedSegmentedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 14.sp,
    labelPadding: Dp = 4.dp,
    compactGlyph: (T) -> ImageVector? = { null },
    compactLabel: @Composable (T) -> String = { label(it).take(1) },
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labels = options.associateWith { label(it) }
    val compactLabels = options.associateWith { compactLabel(it) }
    BoxWithConstraints(modifier = modifier) {
        val textStyle = MaterialTheme.typography.labelLarge
        val fit = remember(constraints.maxWidth, textStyle, density, labels) {
            // A segment's width less its side padding and the 1dp outline each side; the row is infinite
            // only in a measuring pass, where the largest size will do.
            val room = with(density) { (constraints.maxWidth / options.size) - (labelPadding * 2 + 2.dp).roundToPx() }
            if (constraints.maxWidth == Constraints.Infinity) {
                FontFit(maxFontSize, wraps = false)
            } else {
                FontFitCache.getOrPut(FontFitKey(options.map(labels::getValue), textStyle.copy(fontSize = maxFontSize), MIN_READABLE_FONT_SIZE, 0.5.sp, room, density.density, density.fontScale)) {
                    fitFontSize(maxFontSize, MIN_READABLE_FONT_SIZE, 0.5.sp) { size ->
                        options.all {
                            measurer.measure(text = labels.getValue(it), style = textStyle.copy(fontSize = size), maxLines = 1, softWrap = false).size.width <= room
                        }
                    }
                }
            }
        }
        SegmentedChoiceRow(
            options = options,
            selected = selected,
            onSelect = onSelect,
            label = { if (fit.wraps && compactGlyph(it) == null) compactLabels.getValue(it) else labels.getValue(it) },
            spokenLabel = { labels.getValue(it) },
            modifier = Modifier.fillMaxWidth(),
            labelStyle = textStyle.copy(fontSize = fit.size),
            glyph = { if (fit.wraps) compactGlyph(it) else null },
            contentPadding = PaddingValues(horizontal = labelPadding),
        )
    }
}
