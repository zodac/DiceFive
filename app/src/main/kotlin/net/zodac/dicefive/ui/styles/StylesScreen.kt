package net.zodac.dicefive.ui.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceStyle
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.TableBackground
import net.zodac.dicefive.ui.game.style.TableBackgrounds
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
 * Lets a player pick, rather than read, the option for each independently swappable piece of table
 * art - [DiceStyle], [DiceCupStyle] and [TableBackground]. One [Card] per category, a horizontally
 * scrolling row of preview tiles inside it; tapping a tile persists that choice via [viewModel] and
 * marks it selected, so a category isn't stuck at whatever tile count fits one page width once more
 * options are added.
 *
 * The mat/background category previews each [TableBackground.diceTrayBrush] mat (plus its own
 * [TableBackground.DiceTrayDecoration], so a flame trim shows up in the picker too) sitting on top
 * of the [TableBackground.scoreAreaBrush] page background, the same composition `GameBoard` and
 * `DiceTray` use for the real thing. The dice cup previews sit on the CURRENTLY SELECTED
 * background's felt, since that's what the cup will actually be shown against in game.
 */
@Composable
fun StylesScreen(viewModel: StylesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val diceStyleId by viewModel.diceStyleId.collectAsState()
    val diceCupStyleId by viewModel.diceCupStyleId.collectAsState()
    val tableBackgroundId by viewModel.tableBackgroundId.collectAsState()
    val selectedBackground = TableBackgrounds.byId(tableBackgroundId)

    ScreenScaffold(title = "Styles", onBack = onBack, modifier = modifier, scrollable = true) {
        StyleCategoryCard(title = "Dice") {
            for (style in DiceStyles.all) {
                StylePreviewTile(
                    label = displayName(style.id),
                    selected = style.id == diceStyleId,
                    onClick = { viewModel.setDiceStyleId(style.id) },
                    backgroundBrush = SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.size(DICE_PREVIEW_SIZE),
                ) {
                    style.Die(value = 5, held = false, modifier = Modifier.size(DIE_ART_SIZE))
                }
            }
        }

        StyleCategoryCard(title = "Dice Cup") {
            for (style in DiceCupStyles.all) {
                StylePreviewTile(
                    label = displayName(style.id),
                    selected = style.id == diceCupStyleId,
                    onClick = { viewModel.setDiceCupStyleId(style.id) },
                    backgroundBrush = selectedBackground.scoreAreaBrush,
                    modifier = Modifier.size(width = CUP_PREVIEW_WIDTH, height = CUP_PREVIEW_HEIGHT),
                ) {
                    style.Cup(
                        rolling = false,
                        tilted = false,
                        modifier = Modifier.size(width = CUP_ART_SIZE_WIDTH, height = CUP_ART_SIZE_HEIGHT),
                    )
                }
            }
        }

        StyleCategoryCard(title = "Mat & Background") {
            for (background in TableBackgrounds.all) {
                StylePreviewTile(
                    label = displayName(background.id),
                    selected = background.id == tableBackgroundId,
                    onClick = { viewModel.setTableBackgroundId(background.id) },
                    backgroundBrush = background.scoreAreaBrush,
                    modifier = Modifier.size(width = MAT_PREVIEW_WIDTH, height = MAT_PREVIEW_HEIGHT),
                ) {
                    // The dice tray "mat" as a smaller panel sitting on the felt background, the
                    // same way GameBoard stacks the two brushes in the real game.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .fillMaxHeight(0.6f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(background.diceTrayBrush),
                    ) {
                        background.DiceTrayDecoration(modifier = Modifier.matchParentSize())
                    }
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
 * current pick. Tapping it selects it. [modifier] carries the swatch's own size, which differs by
 * category (a die's is square, a cup's is tall, the mat's is wide). */
@Composable
private fun StylePreviewTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    backgroundBrush: Brush,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            modifier = modifier
                .clip(shape)
                .clickable(onClick = onClick)
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

// A preview has no host to scope a view model to - see .claude/UI.md's ViewModelConstructorInComposable gotcha.
@Suppress("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
private fun StylesScreenPreview() {
    DiceFiveTheme {
        StylesScreen(viewModel = StylesViewModel(), onBack = {})
    }
}
