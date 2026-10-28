package net.zodac.dicefive.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [items] one to a line, each after a round bullet, as a dialog's body text is drawn. The block takes only the width
 * it needs, so a dialog centres it, while its lines start in line with each other; a line that wraps keeps clear of
 * the bullets. The bullet is drawn, not a character, so it's the same in every language, and decorative to TalkBack,
 * which reads each line in turn.
 */
@Composable
fun BulletedList(items: List<String>, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    // The bullet sits in a box one line tall, so it's centred on a line's first row, however the line wraps. It and its
    // gap are in sp, so they grow with the text at a large font.
    val density = LocalDensity.current
    val lineHeight = with(density) { style.lineHeight.toDp() }
    val bulletSize = with(density) { BULLET_SIZE.toDp() }
    val bulletGap = with(density) { BULLET_TEXT_GAP.toDp() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BULLET_LINE_GAP)) {
        items.forEach { item ->
            Row {
                Box(modifier = Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(bulletSize).background(color, CircleShape))
                }
                Text(text = item, style = style, color = color, modifier = Modifier.padding(start = bulletGap))
            }
        }
    }
}

private val BULLET_SIZE = 5.sp
private val BULLET_TEXT_GAP = 10.sp
private val BULLET_LINE_GAP = 4.dp
