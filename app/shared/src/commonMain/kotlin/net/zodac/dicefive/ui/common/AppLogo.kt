package net.zodac.dicefive.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.ui.game.CUP_SHAKE_MILLIS
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
 * The brand typeface: Sora (`composeResources/font/sora.ttf`), used on the logo wordmark here, on
 * [ScreenScaffold]'s page titles and every dialog title, and on the game's own marks: the scorecard
 * tiles' labels and Hit List targets, the corner badges (see `SegmentBadge`), the rolls left by the
 * cup, the scores on the player tabs and Game Over, the Leaderboard's mode-card titles, an
 * unlock banner's title, the labels (not the numbers) in the board's totals tooltip, and the
 * game mode and modifier names and values - on their picker pages and in the New Game fields that
 * open them - and the New Game screen's player count and You / User / CPU labels. This is a deliberate, narrow departure from stock M3 type ([UI.md]'s "no
 * typography overrides" rule is about the type *scale*, not a call site): these are brand marks,
 * not body text, so they earn their own face the same way the game board earns its own palette.
 *
 * Never for a player's name or anything else a player types: the font only covers ASCII and
 * Latin-1, so a name in any other script (or with an emoji) would come out in two faces.
 *
 * Not for a column of numbers either (the Leaderboard's scores, Statistics, the totals tooltip's
 * values): Sora's default digits are proportional - a "1" is narrower than a "0" - so right-aligned
 * numbers in it don't line up digit for digit. The system font's digits are all one width. Sora
 * does carry tabular digits (`fontFeatureSettings = "tnum"`) if a column ever has to be in it.
 *
 * The upstream font ships as a variable font (a 100-800 weight axis); since this app only ever
 * uses the bold instance, `sora.ttf` here is a static weight-700 instance produced with
 * `fontTools.varLib.instancer`, then subset with `fontTools.subset` (`layout_features=['*']`,
 * `name_IDs=['*']`) to U+0020-007E and U+00A0-00FF - printable ASCII plus Latin-1, for an
 * achievement title like "Déjà Vu" and the Hit List's "·" any-place mark. Variable-axis and
 * unused-script data was most of the original file's size. It is the only weight there is, so a
 * Sora call site asks for [FontWeight.Bold] - a lighter or heavier one would get Bold anyway.
 */
internal val SoraFontFamily: FontFamily
    @Composable
    get() = FontFamily(Font(Res.font.sora, weight = FontWeight.Bold))

/** Whether `sora.ttf` has a glyph for this character - printable ASCII or Latin-1, as cut (see [SoraFontFamily]). */
internal fun Char.isInSoraFont(): Boolean = this in ' '..'~' || this in '\u00A0'..'\u00FF'

/**
 * The five dice of the logo fan - the name's worth of dice - each with the tilt and the vertical
 * drop that place it on the arc. [drop] is an offset, deliberately not padding: padding is taken
 * out of the node's own size, which would leave the lifted dice rendering as squashed rectangles
 * rather than as squares sitting lower.
 */
internal data class LogoDie(val value: Int, val tilt: Float, val drop: Dp)

// The cup behind the fan, set by a tall cup: how tall it stands, and how far its middle sits above
// the fan's - so more of it, rim and all, shows above the dice than below them. Both in dice, so a
// smaller mark keeps the same proportions. A squat cup is drawn at the same scale, as the game and
// the Styles screen size every cup by its grid, and hangs from the same top line: stood on the same
// base, it would hide almost wholly behind the dice and the wordmark, only its rim showing.
private const val LOGO_CUP_HEIGHT_IN_DICE = 3.4f
private const val LOGO_CUP_RAISE_IN_DICE = 0.35f

// Tapping the fan rolls it: each die hops, spins a whole turn - alternate dice each way - and
// flicks through faces for ROLL_MILLIS, starting ROLL_STAGGER_MILLIS after the one before, then
// lands back on its own face at its own tilt. Its face changes every ROLL_FACE_MILLIS, and settles
// for the last ROLL_SETTLED_SHARE of its roll so it's seen landing on it.
private const val ROLL_MILLIS = 900f
private const val ROLL_STAGGER_MILLIS = 70f
private const val ROLL_FACE_MILLIS = 80f
private const val ROLL_SETTLED_SHARE = 0.2f
private const val ROLL_HOP_IN_DICE = 0.6f
internal val LOGO_ROLL_MILLIS = ROLL_MILLIS + ROLL_STAGGER_MILLIS * 4

/** Where one logo die is, [elapsedMillis] into a roll of the fan: turned [spinDegrees] past its tilt, [hop] dice up, showing [value]. */
internal data class LogoRollPose(val spinDegrees: Float, val hop: Float, val value: Int)

/**
 * The [index]th logo die's pose [elapsedMillis] into a roll, when its own face is [value]: its own
 * face, level and square at the start and the end, a whole turn apart; hopping, spinning and
 * showing other faces in between.
 */
internal fun logoRollPose(index: Int, value: Int, elapsedMillis: Float): LogoRollPose {
    val progress = ((elapsedMillis - index * ROLL_STAGGER_MILLIS) / ROLL_MILLIS).coerceIn(0f, 1f)
    val eased = 1f - (1f - progress).pow(3)
    val direction = if (index % 2 == 0) 1f else -1f
    val scrambling = progress > 0f && progress < 1f - ROLL_SETTLED_SHARE
    val face = if (scrambling) {
        val tick = (elapsedMillis / ROLL_FACE_MILLIS).toInt()
        // Never its own face mid-roll, so it's plain the dice really are rolling.
        (Random(tick * 31 + index).nextInt(1, 6).let { if (it >= value) it + 1 else it })
    } else {
        value
    }
    return LogoRollPose(spinDegrees = direction * 360f * eased, hop = ROLL_HOP_IN_DICE * sin(PI.toFloat() * progress), value = face)
}

/**
 * Plays one roll of a row of dice the way the logo's fan rolls: calls [onFrame] every frame with how
 * far into the roll it is, in milliseconds, up to [LOGO_ROLL_MILLIS] - each die's pose at that point
 * is [logoRollPose]'s. Shared with the Rules page's cup and dice, so both roll alike.
 */
internal suspend fun playLogoRoll(onFrame: (elapsedMillis: Float) -> Unit) {
    val start = withFrameNanos { it }
    var elapsed = 0f
    while (elapsed < LOGO_ROLL_MILLIS) {
        elapsed = withFrameNanos { (it - start) / 1_000_000f }
        onFrame(elapsed.coerceAtMost(LOGO_ROLL_MILLIS))
    }
}

/**
 * Whether a tap at ([x], [y]) - in the logo's own coordinates, its top edge being the cup's top and
 * the cup centred on [logoWidth] - lands on a cup [cupWidth] by [cupHeight] wide and tall.
 */
internal fun isOnLogoCup(x: Float, y: Float, logoWidth: Float, cupWidth: Float, cupHeight: Float): Boolean =
    y in 0f..cupHeight && x in (logoWidth - cupWidth) / 2f..(logoWidth + cupWidth) / 2f

/** The fan itself, left to right - also the order and lie of the Rules page's dice beside their cup. */
internal val LOGO_DICE = listOf(
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
 * With [shakeCupOnTap] (the main menu only), tapping the cup itself - not the dice in front of it -
 * shakes it as it shakes in a game, for [CUP_SHAKE_MILLIS] and through the same [CupCanvas] physics
 * (the wobble, and any liquid sloshing), then settles it upright; a tap while it's shaking is
 * ignored. No achievement, sound or buzz.
 *
 * [onDiceTap] is the "Not Those Dice!" easter egg - only the dice fan itself is the tap target, not
 * the wordmark below it - and tapping it rolls the fan too (see [logoRollPose]), landing back on
 * the dice it started on; a tap while they're still rolling is ignored. No ripple: at this size
 * (five dice sharing one row) a ripple reads as the whole logo flashing, not a considered tap
 * target, the same call [DiceCupPanel] makes for its cup.
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    dieSize: Dp = 34.dp,
    titleSize: TextUnit = TextUnit.Unspecified,
    diceStyle: DiceStyle = DiceStyles.default,
    cupStyle: DiceCupStyle = DiceCupStyles.default,
    pupilsFollowDevice: Boolean = false,
    shakeCupOnTap: Boolean = false,
    onDiceTap: () -> Unit = {},
) {
    // One DieMotion per die, only for a style with loose pupils and only where asked - kept for as
    // long as the style is, and fed the device's pull while this screen is in front.
    // Not under reduced motion: pupils sliding about with the device are motion too, so they stay put.
    val reduceMotion = LocalReduceMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val travel = diceStyle.pupilTravel?.takeIf { pupilsFollowDevice && !reduceMotion }
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

    // How far into a roll the fan is, or null at rest. Each die's whole turns so far, so a googly
    // die's pupils see one steady spin from roll to roll rather than a jump back by a turn.
    var rollMillis by remember { mutableStateOf<Float?>(null) }
    val turnsSoFar = remember { FloatArray(LOGO_DICE.size) }
    val scope = rememberCoroutineScope()
    val roll = {
        // No roll under reduced motion; the tap still counts (onDiceTap is called by the caller either way).
        if (rollMillis == null && !reduceMotion) {
            scope.launch {
                playLogoRoll { elapsed ->
                    rollMillis = elapsed
                    motions?.forEachIndexed { i, motion ->
                        val pose = logoRollPose(i, LOGO_DICE[i].value, elapsed)
                        motion.moveTo(Offset(0f, -pose.hop), LOGO_DICE[i].tilt + turnsSoFar[i] + pose.spinDegrees)
                    }
                }
                LOGO_DICE.indices.forEach { i -> turnsSoFar[i] += logoRollPose(i, LOGO_DICE[i].value, LOGO_ROLL_MILLIS).spinDegrees }
                rollMillis = null
            }
        }
    }

    var cupShaking by remember { mutableStateOf(false) }
    val cupTapScope = rememberCoroutineScope()

    // The cup takes no layout space (see noLayoutSpace), so it can't carry a tap target of its own: a
    // zero-size node is never hit. The whole logo listens instead, and works out whether a tap that no
    // die took (the fan's own click consumes the ones on it) landed on the cup's rectangle - the top
    // of the logo down, centred, in the cup's own proportions.
    val tallCupHeight = dieSize * LOGO_CUP_HEIGHT_IN_DICE
    val gridUnit = tallCupHeight / CupShape.TALL.gridHeight
    val cupWidth = gridUnit * cupStyle.shape.gridWidth
    val cupHeight = gridUnit * cupStyle.shape.gridHeight
    val tapModifier = if (shakeCupOnTap) {
        Modifier.pointerInput(cupWidth, cupHeight) {
            detectTapGestures { tap ->
                if (!cupShaking && !reduceMotion && isOnLogoCup(tap.x, tap.y, size.width.toFloat(), cupWidth.toPx(), cupHeight.toPx())) {
                    cupShaking = true
                    cupTapScope.launch {
                        lifecycle.delayWhileResumed(CUP_SHAKE_MILLIS)
                        cupShaking = false
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Column(modifier = modifier.then(tapModifier), horizontalAlignment = Alignment.CenterHorizontally) {
        // The cup takes no room of its own (see noLayoutSpace), so the dice and the wordmark sit
        // exactly as they would without it, its base reaching down behind the wordmark. Only the
        // part a tall cup stands above the dice is reserved, as top padding: the page scrolls, and
        // a scrolling column clips whatever is drawn outside it, which would cut off the rim. It's
        // reserved for a squat cup too, so switching cups never moves the dice or the wordmark.
        val cupRaise = dieSize * LOGO_CUP_RAISE_IN_DICE
        val cupAboveDice = tallCupHeight / 2 + cupRaise - dieSize / 2
        Box(modifier = Modifier.padding(top = cupAboveDice), contentAlignment = Alignment.Center) {
            // Standing behind the fan, in its own shape's proportions so it isn't stretched.
            // Upright, and still unless tapped (see shakeCupOnTap): never tipped, so it never lets the
            // Top Hat's rabbit out - it only peeks from a hat left tipped over - and a shake ends upright.
            cupStyle.Cup(
                rolling = cupShaking,
                tilted = false,
                modifier = Modifier
                    .noLayoutSpace()
                    .offset(y = cupHeight / 2 - dieSize / 2 - cupAboveDice)
                    .size(width = cupWidth, height = cupHeight),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                // Raw taps, not clickable: the dice are decoration, and a clickable would give TalkBack an
                // unnamed "double tap to activate" stop that does nothing a screen reader user could use.
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures {
                        roll()
                        onDiceTap()
                    }
                },
            ) {
                LOGO_DICE.forEachIndexed { i, die ->
                    val pose = rollMillis?.let { logoRollPose(i, die.value, it) }
                    CompositionLocalProvider(LocalDieMotion provides motions?.get(i)) {
                        diceStyle.Die(
                            value = pose?.value ?: die.value,
                            held = false,
                            modifier = Modifier
                                .size(dieSize)
                                .offset(y = die.drop - dieSize * (pose?.hop ?: 0f))
                                .rotate(die.tilt + (pose?.spinDegrees ?: 0f)),
                        )
                    }
                }
            }
        }

        Text(
            text = "DiceFive", // i18n: not translated - the app's name
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.displayMedium,
            fontFamily = SoraFontFamily,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
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
// The pupils' sensor is read 20 times a second, not the default 50 - they're smoothed anyway, and every reading
// wakes the app. The smoothing is stronger per reading to match, so the pupils feel just as quick:
// 1 - 0.5^(50 / 20) is what 0.5 a reading at 50 a second comes to at 20.
private const val PULL_SAMPLES_PER_SECOND = 20
private const val PULL_SMOOTHING = 0.82f

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
                Lifecycle.Event.ON_RESUME -> accelerometer.start(PULL_SAMPLES_PER_SECOND) { x, y, _ ->
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
