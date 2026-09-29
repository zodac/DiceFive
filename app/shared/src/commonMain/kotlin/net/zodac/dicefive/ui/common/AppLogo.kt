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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.sora
import net.zodac.dicefive.ui.game.style.CupShape
import net.zodac.dicefive.ui.game.style.DiceCupStyle
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceStyle
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.DieMotion
import net.zodac.dicefive.ui.game.style.LocalDieMotion
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

// The cup behind the fan, set by a tall cup: how tall it stands, and how far its middle sits above
// the fan's - so more of it, rim and all, shows above the dice than below them. Both in dice, so a
// smaller mark keeps the same proportions. A squat cup is drawn at the same scale, as the game and
// the Styles screen size every cup by its grid, and hangs from the same top line: stood on the same
// base, it would hide almost wholly behind the dice and the wordmark, only its rim showing.
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
 * The app mark: a fan of the game's own dice, in front of a cup, over the wordmark.
 *
 * Drawn with [diceStyle] and [cupStyle] - the player's own picks, on the menu - rather than an
 * image, so it costs no asset and always matches the dice and cup on the board. Deliberately live
 * Compose art rather than a drawable, unlike the launcher icon (the Android app's
 * `ic_launcher_foreground`), which is its own static artwork of the defaults.
 *
 * The wordmark uses `displayMedium` from the type scale rather than a hand-set size, so it stays
 * in proportion with everything else if the scale is ever restyled; [titleSize] only exists for
 * the About page, which wants the same mark at a supporting size.
 *
 * With [pupilsFollowDevice] (the main menu only), a [diceStyle] with loose pupils - the googly
 * eyes, and nothing else - has them slide about with the phone's tilt and shake, through the same
 * [DieMotion] the dice tray moves them with. Every other style, and every other screen, is unaffected.
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
    diceStyle: DiceStyle = DiceStyles.default,
    cupStyle: DiceCupStyle = DiceCupStyles.default,
    pupilsFollowDevice: Boolean = false,
    onDiceTap: () -> Unit = {},
) {
    // One DieMotion per die, only for a style with loose pupils and only where asked - kept for as
    // long as the style is, and fed the device's pull while this screen is in front.
    val travel = diceStyle.pupilTravel?.takeIf { pupilsFollowDevice }
    val motions = travel?.let {
        remember(it) { LOGO_DICE.mapIndexed { i, die -> DieMotion(seed = i, travel = it).apply { moveTo(Offset.Zero, die.tilt) } } }
    }
    if (motions != null) {
        DevicePullEffect { gees -> motions.forEach { it.feel(gees) } }
        for (motion in motions) {
            // Acts on the value it was keyed on, not a fresh read: the sensor can wake the pupils in
            // the very frame this first launches, and an effect that started following on that fresh
            // read would be cancelled by the recomposition for it - and follow() going back to sleep
            // as it's cancelled would leave nothing following at all.
            val awake = motion.awake
            LaunchedEffect(motion, awake) {
                if (awake) motion.follow()
            }
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The cup takes no room of its own (see noLayoutSpace), so the dice and the wordmark sit
        // exactly as they would without it, its base reaching down behind the wordmark. Only the
        // part a tall cup stands above the dice is reserved, as top padding: the page scrolls, and
        // a scrolling column clips whatever is drawn outside it, which would cut off the rim. It's
        // reserved for a squat cup too, so switching cups never moves the dice or the wordmark.
        val tallCupHeight = dieSize * LOGO_CUP_HEIGHT_IN_DICE
        val cupRaise = dieSize * LOGO_CUP_RAISE_IN_DICE
        val cupAboveDice = tallCupHeight / 2 + cupRaise - dieSize / 2
        val gridUnit = tallCupHeight / CupShape.TALL.gridHeight
        val cupHeight = gridUnit * cupStyle.shape.gridHeight
        Box(modifier = Modifier.padding(top = cupAboveDice), contentAlignment = Alignment.Center) {
            // Standing behind the fan, in its own shape's proportions so it isn't stretched.
            // Upright and still: never rolling or tipped, so it never runs the in-game shake (or
            // lets the Top Hat's rabbit out - it only peeks from a hat left tipped over).
            cupStyle.Cup(
                rolling = false,
                tilted = false,
                modifier = Modifier
                    .noLayoutSpace()
                    .offset(y = cupHeight / 2 - dieSize / 2 - cupAboveDice)
                    .size(width = gridUnit * cupStyle.shape.gridWidth, height = cupHeight),
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
                LOGO_DICE.forEachIndexed { i, die ->
                    CompositionLocalProvider(LocalDieMotion provides motions?.get(i)) {
                        diceStyle.Die(
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

// Standard gravity, for turning the accelerometer's m/s² into g; and how much of each new reading the
// pull takes on, smoothing out the sensor's own jitter without dulling a real shake.
private const val STANDARD_GRAVITY = 9.81f
private const val PULL_SMOOTHING = 0.5f

/**
 * Reports the pull the device puts on anything loose on screen, in g (x right, y down): gravity down
 * whichever way the phone is tipped, and against however it's being moved - the reverse of what the
 * [Accelerometer][net.zodac.dicefive.platform.Accelerometer] reads. Listening only while this screen
 * is resumed, as ShakeDetectorEffect does, so a backgrounded app's sensor isn't left running.
 */
@Composable
private fun DevicePullEffect(onPull: (Offset) -> Unit) {
    val platform = LocalPlatformServices.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPull = rememberUpdatedState(onPull)
    val accelerometer = remember { platform.createAccelerometer() } ?: return

    DisposableEffect(lifecycleOwner, accelerometer) {
        var pull: Offset? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> accelerometer.start { x, y, _ ->
                    // Up the screen is -y on it, so the reading's y keeps its sign and x flips.
                    val reading = Offset(-x, y) / STANDARD_GRAVITY
                    val smoothed = pull?.let { it + (reading - it) * PULL_SMOOTHING } ?: reading
                    pull = smoothed
                    currentOnPull.value(smoothed)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    accelerometer.stop()
                    pull = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            accelerometer.stop()
        }
    }
}
