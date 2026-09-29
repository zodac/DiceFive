package net.zodac.dicefive.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.sora
import net.zodac.dicefive.ui.game.style.CUP_GRID_HEIGHT
import net.zodac.dicefive.ui.game.style.CUP_GRID_WIDTH
import net.zodac.dicefive.ui.game.style.ClassicGoldDiceCupStyle
import net.zodac.dicefive.ui.game.style.IvoryDiceStyle
import org.jetbrains.compose.resources.Font

/**
 * The brand typeface: Sora (`composeResources/font/sora.ttf`), used on the logo wordmark here, on [ScreenScaffold]'s
 * page titles, and on the in-game corner badges (5x bonus count, Small/Large Straight run length -
 * see `SegmentBadge`). This is a deliberate, narrow departure from stock M3 type ([UI.md]'s "no
 * typography overrides" rule is about the type *scale*, not a call site): these are brand marks,
 * not body text, so they earn their own face the same way the game board earns its own palette.
 *
 * The upstream font ships as a variable font (a 100-800 weight axis); since this app only ever
 * uses the bold instance, `sora.ttf` here is a static weight-700 instance produced with
 * `fontTools.varLib.instancer`, then subset with `fontTools.subset` to just printable ASCII (every
 * string rendered in this face is a short English title or a digit badge) - variable-axis and
 * unused-script data was most of the original file's size.
 */
internal val SoraFontFamily: FontFamily
    @Composable
    get() = FontFamily(Font(Res.font.sora, weight = FontWeight.Bold))

/**
 * The five dice of the logo fan - the name's worth of dice - each with the tilt and the vertical
 * drop that place it on the arc. [drop] is an offset, deliberately not padding: padding is taken
 * out of the node's own size, which would leave the lifted dice rendering as squashed rectangles
 * rather than as squares sitting lower.
 */
private data class LogoDie(val value: Int, val tilt: Float, val drop: Dp)

// The cup behind the fan: how tall it stands, and how far its middle sits above the fan's - so more
// of the cup, rim and all, shows above the dice than below them. Both in dice, so a smaller mark
// keeps the same proportions.
private const val LOGO_CUP_HEIGHT_IN_DICE = 3.4f
private const val LOGO_CUP_RAISE_IN_DICE = 0.35f

private val LOGO_DICE = listOf(
    LogoDie(value = 2, tilt = -20f, drop = 8.dp),
    LogoDie(value = 4, tilt = -10f, drop = 2.dp),
    LogoDie(value = 5, tilt = 0f, drop = 0.dp),
    LogoDie(value = 3, tilt = 10f, drop = 2.dp),
    LogoDie(value = 6, tilt = 20f, drop = 8.dp),
)

/**
 * The app mark: a fan of the game's own dice, in front of the default cup, over the wordmark.
 *
 * Built from [IvoryDiceStyle] rather than an image so it costs no asset and always matches the
 * dice on the board - deliberately kept as live Compose dice rather than a drawable, unlike the
 * launcher icon (the Android app's `ic_launcher_foreground`), which is its own static artwork.
 *
 * The wordmark uses `displayMedium` from the type scale rather than a hand-set size, so it stays
 * in proportion with everything else if the scale is ever restyled; [titleSize] only exists for
 * the About page, which wants the same mark at a supporting size.
 *
 * [onDiceTap] is the "Not Those Dice!" easter egg - only the dice fan itself is the tap target, not
 * the wordmark below it. No ripple: at this size (five dice sharing one row) a ripple reads as the
 * whole logo flashing, not a considered tap target, the same call [DiceCupPanel] makes for its cup.
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    dieSize: Dp = 34.dp,
    titleSize: TextUnit = TextUnit.Unspecified,
    onDiceTap: () -> Unit = {},
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The cup takes no room of its own (see noLayoutSpace), so the dice and the wordmark sit
        // exactly as they would without it, its base reaching down behind the wordmark. Only the
        // part standing above the dice is reserved, as top padding: the page scrolls, and a
        // scrolling column clips whatever is drawn outside it, which would cut off the rim.
        val cupHeight = dieSize * LOGO_CUP_HEIGHT_IN_DICE
        val cupRaise = dieSize * LOGO_CUP_RAISE_IN_DICE
        val cupAboveDice = cupHeight / 2 + cupRaise - dieSize / 2
        Box(modifier = Modifier.padding(top = cupAboveDice), contentAlignment = Alignment.Center) {
            // The launcher icon's cup - the default Classic cup in Gold - standing behind the fan,
            // in the cup grid's own proportions so it isn't stretched. Upright and still: never
            // rolling or tipped, so it never runs the in-game shake.
            ClassicGoldDiceCupStyle.Cup(
                rolling = false,
                tilted = false,
                modifier = Modifier
                    .noLayoutSpace()
                    .offset(y = -cupRaise)
                    .size(width = cupHeight * (CUP_GRID_WIDTH / CUP_GRID_HEIGHT), height = cupHeight),
            )

            val diceInteractionSource = remember { MutableInteractionSource() }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = diceInteractionSource,
                    indication = null,
                    onClick = onDiceTap,
                ),
            ) {
                for (die in LOGO_DICE) {
                    IvoryDiceStyle.Die(
                        value = die.value,
                        held = false,
                        modifier = Modifier
                            .size(dieSize)
                            .offset(y = die.drop)
                            .rotate(die.tilt),
                    )
                }
            }
        }

        Text(
            text = "DiceFive",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.displayMedium,
            fontFamily = SoraFontFamily,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "ROLL · SCORE · REPEAT",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Measures the content at its own size but reports none, drawing it centred on the spot the parent
 * places it - so it can sit behind its siblings without the parent growing to fit it.
 */
private fun Modifier.noLayoutSpace(): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, 0) { placeable.place(-placeable.width / 2, -placeable.height / 2) }
}
