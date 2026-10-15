package net.zodac.dicefive.store

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.R
import net.zodac.dicefive.ui.common.AppLogo
import net.zodac.dicefive.ui.common.BrandBackdrop
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DieMotion
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.LocalDieIndex
import net.zodac.dicefive.ui.game.style.LocalDieMotion
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the store listing's still art - the hi-res icon, the feature graphic, and the promo video's closing card -
 * from the app's own icon and artwork, each at three times its size for the script to scale down. A generator, not a
 * check - see [StoreAssets.onlyWhenAsked] and `.claude/STORE_ASSETS.md`.
 */
@RunWith(AndroidJUnit4::class)
class StoreArtGeneratorTest {

    @get:Rule(order = 0)
    val onlyWhenAsked = StoreAssets.onlyWhenAsked()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    /**
     * The adaptive icon - its background colour and foreground, nothing else - close to how a launcher shows it: the
     * middle of its 108dp canvas fills the square - 76dp, a little more than the 72dp a launcher shows, for a margin
     * inside Play's rounded corners (the outer ring is only there for launchers to move it about in). Drawing the
     * whole canvas instead leaves the artwork a third smaller than on the phone. Play masks the square
     * to its own rounded corners and adds the shadow, so neither is drawn here.
     */
    @Test
    @StorePart(StoreAssets.ICON)
    fun `hi-res icon`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val size = ICON_PIXELS * SUPERSAMPLE
        val icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(icon)
        canvas.drawColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
        val foreground = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground))
        val bleed = size * (ADAPTIVE_CANVAS_DP - VISIBLE_DP) / VISIBLE_DP / 2
        foreground.setBounds(-bleed, -bleed, size + bleed, size + bleed)
        foreground.draw(canvas)
        StoreAssets.save(icon, File(StoreAssets.outDir, "icon.png"))
    }

    /**
     * 1024x500, drawn at 3x. Play lays its play button over the middle when the listing has a video, and crops the
     * edges in some places, so the logo sits left of centre and nothing that matters touches an edge.
     */
    @Test
    @StorePart(StoreAssets.FEATURE)
    @Config(qualifiers = "w1024dp-h500dp-xxhdpi")
    fun `feature graphic`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { DiceFiveTheme { BannerScene(logoDie = 52.dp, animated = true) } }
        // Stopped where the glitch die is at the worst of a burst, so the still shows it glitching.
        repeat(FEATURE_GLITCH_FRAMES) { compose.mainClock.advanceTimeBy(FRAME_MILLIS) }
        StoreAssets.save(compose.onRoot().captureToImage().asAndroidBitmap(), File(StoreAssets.outDir, "feature-graphic.png"))
        compose.mainClock.autoAdvance = true
    }

    /**
     * The video's last card: 1920x1080, drawn at 3x - the same scene, a little roomier, with the dice moving so the
     * styles that react to it (googly eyes, glitch, RGB) show. One folder of 60fps frames, like a video scene.
     */
    @Test
    @StorePart(StoreAssets.VIDEO)
    @Config(qualifiers = "w640dp-h360dp-xxhdpi")
    fun `video end card`() {
        val dir = File(StoreAssets.outDir, "video-end-card").apply { deleteRecursively(); mkdirs() }
        compose.mainClock.autoAdvance = false
        compose.setContent { DiceFiveTheme { BannerScene(logoDie = 40.dp, animated = true) } }
        repeat(END_CARD_FRAMES) { frame ->
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            StoreAssets.save(compose.onRoot().captureToImage().asAndroidBitmap(), File(dir, "%05d.png".format(frame)))
        }
        compose.mainClock.autoAdvance = true
    }

    private companion object {
        const val ICON_PIXELS = 512
        const val SUPERSAMPLE = 4
        const val ADAPTIVE_CANVAS_DP = 108
        const val VISIBLE_DP = 76
        const val FRAME_MILLIS = 16L
        const val END_CARD_FRAMES = 188

        /** Frames in, where the glitch die's first burst peaks (die 2 in [TOSSES], from its seeded timing). */
        const val FEATURE_GLITCH_FRAMES = 52
    }
}

/**
 * The brand backdrop, the menu's logo in the default dice and cup - no tagline, the logo is the brand - and a
 * handful of the unlockable dice tumbling across the right - a taste of the Styles screen. [animated] lets the googly
 * pupils slide and the glitch and RGB dice play, the dice themselves staying put.
 */
@Composable
private fun BannerScene(logoDie: Dp, animated: Boolean = false) {
    BrandBackdrop(showDice = true) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            AppLogo(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = w * 0.07f),
                dieSize = logoDie,
                titleSize = (logoDie.value * 1.9f).sp,
                diceStyle = DiceStyles.default,
                cupStyle = DiceCupStyles.default,
            )
            val die = h * 0.17f
            val phase = if (animated) {
                rememberInfiniteTransition(label = "bannerSway").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(SWAY_MILLIS, easing = LinearEasing)),
                    label = "bannerPhase",
                ).value
            } else {
                0f
            }
            TOSSES.forEachIndexed { i, toss ->
                val style = DiceStyles.byId(toss.style)
                val motion = remember(style) { style.pupilTravel?.let { DieMotion(seed = i, travel = it) } }
                // The dice stay where they are. Googly pupils are told the die is being shaken about - a made-up path, a different beat for each die - so they slide as they would in a moving die.
                if (motion != null) {
                    val turn = 2f * PI.toFloat() * (phase * SHAKE_BEATS + i / TOSSES.size.toFloat())
                    val home = Offset(w.value * toss.x, h.value * toss.y) / die.value
                    SideEffect { motion.moveTo(home + Offset(cos(turn), sin(turn * 1.3f)) * SHAKE_REACH, yawDegrees = 12f * sin(turn)) }
                }
                val awake = motion?.awake
                LaunchedEffect(motion, awake) {
                    if (motion != null && awake == true) motion.follow()
                }
                Box(
                    Modifier
                        .offset(x = w * toss.x - die / 2, y = h * toss.y - die / 2)
                        .size(die)
                        .rotate(toss.angle),
                ) {
                    CompositionLocalProvider(LocalDieIndex provides i, LocalDieMotion provides motion) {
                        style.Die(value = toss.value, held = false, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** One die on the right of the banner: which style, which face, where (as fractions of the banner) and at what turn. */
private data class Toss(val style: String, val value: Int, val x: Float, val y: Float, val angle: Float)

private val TOSSES = listOf(
    Toss("cake_strawberry", 5, 0.62f, 0.30f, -14f),
    Toss("googly_ivory", 6, 0.74f, 0.20f, 11f),
    Toss("glitch_black", 4, 0.87f, 0.32f, -8f),
    Toss("rgb_wave", 3, 0.66f, 0.68f, 18f),
    Toss("christmas_baubles", 2, 0.80f, 0.60f, -20f),
    Toss("meadow_green", 6, 0.92f, 0.76f, 9f),
    Toss("world_japanese", 5, 0.55f, 0.84f, -6f),
    Toss("d20_blue", 6, 0.72f, 0.44f, 7f),
)

/** One loop of the made-up shaking of the googly dice takes this long, and has [SHAKE_BEATS] beats, each moving [SHAKE_REACH] die sizes. */
private const val SWAY_MILLIS = 3_000
private const val SHAKE_BEATS = 4f
private const val SHAKE_REACH = 0.6f
