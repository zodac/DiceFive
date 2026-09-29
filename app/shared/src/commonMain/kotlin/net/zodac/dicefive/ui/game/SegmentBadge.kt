package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.common.SoraFontFamily
import net.zodac.dicefive.ui.theme.TileBadgeBackground

/**
 * A small circular badge showing 1-2 digits - used for both the Small/Large Straight run-length
 * badge and the 5x bonus-count badge, so the two look like one shared design instead of a
 * hand-drawn digit next to a system font one. Set in [SoraFontFamily], the same brand face as the
 * logo wordmark and page titles - not a plain M3 [Text], because a badge this small next to the
 * category's own bold glyph read as an afterthought in the ambient font; not hand-drawn segments
 * either (an earlier version of this composable rendered digits as seven-segment "LCD" rectangles
 * to sidestep pulling in a font at all), because 0/1/7 - the only digits that never light the
 * middle segment - always read as visibly broken or short next to the rest.
 */
@Composable
fun SegmentBadge(count: Int, color: Color, modifier: Modifier = Modifier) {
    val digits = count.coerceIn(0, 99).toString()
    BoxWithConstraints(
        modifier = modifier
            .clip(CircleShape)
            .background(TileBadgeBackground)
            .border(1.dp, color.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Fraction of the badge's own width, not a fixed sp value - the badge is itself sized as
        // a fraction of its tile (see BADGE_SIZE_FRACTION in CategoryIcon.kt), so the digit has to
        // scale with it too. Two digits get a smaller fraction than one so the pair still clears
        // the circle's edges instead of being clipped.
        val sizeFraction = if (digits.length == 1) 0.62f else 0.46f
        val fontSize = with(LocalDensity.current) { (maxWidth * sizeFraction).toSp() }
        Text(
            text = digits,
            color = color,
            style = TextStyle(
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
