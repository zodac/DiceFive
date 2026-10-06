package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Functions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.zodac.dicefive.ui.common.AppTooltip
import net.zodac.dicefive.ui.common.rememberAppTooltipState
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.TileIconColor
import net.zodac.dicefive.ui.theme.TileTealBorder
import net.zodac.dicefive.ui.theme.TileTealBottom
import net.zodac.dicefive.ui.theme.TileTealTop

/**
 * The scorecard's section totals - Upper, the upper bonus and Lower - behind an icon-only Σ button,
 * shown in a tooltip on a tap or a long press. They used to sit beside the cup as three lines of text,
 * which a large system font wrapped and broke; a button holds its size, and they're a
 * glance-when-needed, not something to keep in view. Styled like [UndoButton] beside it.
 *
 * The glyph turns gold once the bonus is earned, as the old Bonus line did. A tooltip isn't
 * announced, so a screen reader hears the totals as the button's state ([totalsSpokenState]) - its
 * action shows the tooltip too.
 */
@Composable
fun TotalsButton(upperTotal: Int, upperBonus: Int, lowerTotal: Int, modifier: Modifier = Modifier, minSize: Dp = 48.dp) {
    TotalsButton(
        lines = listOf(
            TotalLine("Upper", upperTotal),
            TotalLine("Bonus", upperBonus, gold = upperBonus > 0),
            TotalLine("Lower", lowerTotal),
        ),
        highlighted = upperBonus > 0,
        spokenState = totalsSpokenState(upperTotal, upperBonus, lowerTotal),
        modifier = modifier,
        minSize = minSize,
    )
}

/**
 * Hit List's totals - its targets, and the Alibi - behind the same button: a card of targets has no sections or
 * bonus to show.
 */
@Composable
fun HitListTotalsButton(targetsTotal: Int, alibiTotal: Int, modifier: Modifier = Modifier, minSize: Dp = 48.dp) {
    TotalsButton(
        lines = listOf(TotalLine("Targets", targetsTotal), TotalLine("Alibi", alibiTotal)),
        highlighted = false,
        spokenState = hitListTotalsSpokenState(targetsTotal, alibiTotal),
        modifier = modifier,
        minSize = minSize,
    )
}

/** One line of a [TotalsButton]'s tooltip: "[label]: [value]", in bold gold when [gold]. */
private class TotalLine(val label: String, val value: Int, val gold: Boolean = false)

/** The button itself: [lines] in its tooltip, its glyph gold while [highlighted], and [spokenState] what TalkBack hears. */
@Composable
private fun TotalsButton(lines: List<TotalLine>, highlighted: Boolean, spokenState: String, modifier: Modifier, minSize: Dp) {
    val tooltipState = rememberAppTooltipState()
    val scope = rememberCoroutineScope()
    val color = if (highlighted) GoldAccent else TileIconColor
    val message = remember(spokenState) {
        buildAnnotatedString {
            lines.forEachIndexed { index, line ->
                if (index > 0) append("\n")
                if (line.gold) withStyle(SpanStyle(color = GoldAccent, fontWeight = FontWeight.Bold)) { append("${line.label}: ${line.value}") } else append("${line.label}: ${line.value}")
            }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    // The tooltip's own anchor merges this button into one TalkBack node of its own, which keeps the
    // name, state and actions but not the role - so the role goes on that node too.
    AppTooltip(message = message, state = tooltipState, modifier = modifier.semantics { role = Role.Button }) {
        Box(
            modifier = Modifier
                // Before clickable, so its own click semantics don't leak through - see UI.md.
                .clearAndSetSemantics {
                    contentDescription = "Totals"
                    stateDescription = spokenState
                    role = Role.Button
                    onClick(label = "Show totals") {
                        scope.launch { tooltipState.show() }
                        true
                    }
                }
                // M3's 48dp minimum touch target, which the glyph alone (when a large font drops the label) is short of.
                .sizeIn(minWidth = minSize, minHeight = minSize)
                .clip(shape)
                .background(Brush.linearGradient(listOf(TileTealTop, TileTealBottom)))
                .border(1.dp, TileTealBorder, shape)
                .clickable(role = Role.Button) { scope.launch { tooltipState.show() } }
                .padding(horizontal = 6.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Decorative: the button's own semantics, above, name it and say the totals.
            BoardButtonIcon(imageVector = Icons.Filled.Functions, contentDescription = null, tint = color)
        }
    }
}

/** What a screen reader hears for [TotalsButton]: every total, the bonus said to be earned once it is. */
internal fun totalsSpokenState(upperTotal: Int, upperBonus: Int, lowerTotal: Int): String =
    "Upper $upperTotal, " + (if (upperBonus > 0) "bonus $upperBonus earned" else "no bonus yet") + ", lower $lowerTotal"

/** What a screen reader hears for [HitListTotalsButton]. */
internal fun hitListTotalsSpokenState(targetsTotal: Int, alibiTotal: Int): String = "Targets $targetsTotal, alibi $alibiTotal"
