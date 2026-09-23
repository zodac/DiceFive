package net.zodac.dicefive.ui.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.common.HorizontalScrollbar
import net.zodac.dicefive.ui.common.ScreenScaffold
import net.zodac.dicefive.ui.game.style.DiceCupStyle
import net.zodac.dicefive.ui.game.style.DiceStyle
import net.zodac.dicefive.ui.game.style.GameVisualTheme
import net.zodac.dicefive.ui.game.style.TableBackground
import net.zodac.dicefive.ui.theme.DiceFiveTheme

private val DICE_PREVIEW_SIZE = 96.dp
private val DIE_ART_SIZE = 60.dp
private val CUP_PREVIEW_WIDTH = 96.dp
private val CUP_PREVIEW_HEIGHT = 132.dp
private val CUP_ART_SIZE_WIDTH = 58.dp
private val CUP_ART_SIZE_HEIGHT = 84.dp
private val MAT_PREVIEW_WIDTH = 200.dp
private val MAT_PREVIEW_HEIGHT = 116.dp

/**
 * Lets a player see, rather than read, the options for each independently swappable piece of table
 * art - [DiceStyle], [DiceCupStyle] and [TableBackground] (see [GameVisualTheme]). Only the shipped
 * defaults exist today, each shown as the current pick; the shape of the page - one [Card] per
 * category, a row of preview tiles inside it - is what makes adding a second option later just
 * another tile, not a redesign.
 *
 * The mat/background category previews the [TableBackground.diceTrayBrush] mat sitting on top of
 * the [TableBackground.scoreAreaBrush] page background, the same composition [GameBoard] and
 * [DiceTray] use for the real thing.
 */
@Composable
fun StylesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val visualTheme = GameVisualTheme()

    ScreenScaffold(title = "Styles", onBack = onBack, modifier = modifier, scrollable = true) {
        StyleCategoryCard(title = "Dice") {
            StylePreviewTile(
                label = displayName(visualTheme.diceStyle.id),
                selected = true,
                backgroundBrush = SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.size(DICE_PREVIEW_SIZE),
            ) {
                visualTheme.diceStyle.Die(value = 5, held = false, modifier = Modifier.size(DIE_ART_SIZE))
            }
        }

        StyleCategoryCard(title = "Dice Cup") {
            StylePreviewTile(
                label = displayName(visualTheme.diceCupStyle.id),
                selected = true,
                backgroundBrush = visualTheme.background.scoreAreaBrush,
                modifier = Modifier.size(width = CUP_PREVIEW_WIDTH, height = CUP_PREVIEW_HEIGHT),
            ) {
                visualTheme.diceCupStyle.Cup(
                    rolling = false,
                    tilted = false,
                    modifier = Modifier.size(width = CUP_ART_SIZE_WIDTH, height = CUP_ART_SIZE_HEIGHT),
                )
            }
        }

        StyleCategoryCard(title = "Mat & Background") {
            StylePreviewTile(
                label = displayName(visualTheme.background.id),
                selected = true,
                backgroundBrush = visualTheme.background.scoreAreaBrush,
                modifier = Modifier.size(width = MAT_PREVIEW_WIDTH, height = MAT_PREVIEW_HEIGHT),
            ) {
                // The dice tray "mat" as a smaller panel sitting on the felt background, the same
                // way GameBoard stacks the two brushes in the real game.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .fillMaxHeight(0.6f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(visualTheme.background.diceTrayBrush),
                )
            }
        }
    }
}

/**
 * A titled group of preview tiles for one swappable category, matching Settings' Card sections.
 * The tile row scrolls horizontally and carries [HorizontalScrollbar] rather than a fixed-width
 * grid, so a category isn't stuck at whatever tile count fits one page width once more options
 * exist - today's single tile per category just means the bar has nothing to show yet.
 */
@Composable
private fun StyleCategoryCard(title: String, content: @Composable RowScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
        )

        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }

        HorizontalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }
}

/** One style option: a rendered preview swatch plus its name, with a check badge when it's the
 * current pick. [modifier] carries the swatch's own size, which differs by category (a die's is
 * square, a cup's is tall, the mat's is wide). */
@Composable
private fun StylePreviewTile(
    label: String,
    selected: Boolean,
    backgroundBrush: Brush,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            modifier = modifier
                .clip(shape)
                .background(backgroundBrush)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
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
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

/** "midnight_felt" -> "Midnight Felt". Style ids are lower_snake_case by convention. */
private fun displayName(id: String): String =
    id.split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

@Preview(showBackground = true)
@Composable
private fun StylesScreenPreview() {
    DiceFiveTheme {
        StylesScreen(onBack = {})
    }
}
