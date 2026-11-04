package net.zodac.dicefive.store

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DieMotion
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.LocalDieIndex
import net.zodac.dicefive.ui.style.LocalDieMotion
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
 * Renders the store listing's art - the hi-res icon, the feature graphic, and the promo video's closing card -
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
     * The adaptive icon - its background and foreground layers, nothing else - close to how a launcher shows it: the
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
        val bleed = size * (ADAPTIVE_CANVAS_DP - VISIBLE_DP) / VISIBLE_DP / 2
        listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach { layer ->
            val drawable = checkNotNull(ContextCompat.getDrawable(context, layer))
            drawable.setBounds(-bleed, -bleed, size + bleed, size + bleed)
            drawable.draw(canvas)
        }
        StoreAssets.save(icon, File(StoreAssets.outDir, "icon.png"))
    }

    /**
     * 1024x500, drawn at 3x. Play lays its play button over the middle when the listing has a video, and crops the
     * edges in some places, so the logo sits in the felt left of the mat and nothing that matters touches an edge.
     *
     * Also one loop of the scene moving, every [FEATURE_GIF_STRIDE]th frame into a folder, for the README's animated
     * copy: [FEATURE_LOOP_FRAMES] frames, the length of the RGB dice's colour cycle and two of the googly dice's shaking,
     * so its last frame runs on into its first.
     */
    @Test
    @StorePart(StoreAssets.FEATURE)
    @Config(qualifiers = "w1024dp-h500dp-xxhdpi")
    fun `feature graphic`() {
        val dir = File(StoreAssets.outDir, "feature-graphic-frames").apply { deleteRecursively(); mkdirs() }
        compose.mainClock.autoAdvance = false
        compose.setContent { DiceFiveTheme { BannerScene(logoDie = 56.dp, animated = true) } }
        for (frame in 1..FEATURE_LOOP_FRAMES) {
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            // The still is where the glitch die is at the worst of a burst, so it shows it glitching.
            if (frame == FEATURE_GLITCH_FRAMES) {
                StoreAssets.save(compose.onRoot().captureToImage().asAndroidBitmap(), File(StoreAssets.outDir, "feature-graphic.png"))
            }
            if (frame % FEATURE_GIF_STRIDE == 0) {
                StoreAssets.save(compose.onRoot().captureToImage().asAndroidBitmap(), File(dir, "%05d.png".format(frame / FEATURE_GIF_STRIDE)))
            }
        }
        compose.mainClock.autoAdvance = true
    }

    /**
     * The video's last card: 1920x1080, drawn at 3x - the same scene, with its logo sized to clear the mat at this
     * shape, and with the dice moving so the
     * styles that react to it (googly eyes, glitch, RGB) show. One folder of 60fps frames, like a video scene.
     */
    @Test
    @StorePart(StoreAssets.VIDEO)
    @Config(qualifiers = "w640dp-h360dp-xxhdpi")
    fun `video end card`() {
        val dir = File(StoreAssets.outDir, "video-end-card").apply { deleteRecursively(); mkdirs() }
        compose.mainClock.autoAdvance = false
        compose.setContent { DiceFiveTheme { BannerScene(logoDie = 35.dp, animated = true) } }
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

        /** One loop of the feature graphic's scene: 6s, the RGB dice's colour cycle and two [SWAY_MILLIS]. */
        const val FEATURE_LOOP_FRAMES = 375

        /** The animated feature graphic keeps one frame in this many: 48ms apart, close to a GIF's 50ms. */
        const val FEATURE_GIF_STRIDE = 3
    }
}

/**
 * The launcher icon's table ([TableBackdrop]), the menu's logo in the default dice and cup, centred in the felt left of
 * the mat - no tagline, the logo is the brand - and a handful of the unlockable dice on the mat - a taste of the Styles
 * screen. [animated] lets the googly pupils slide and the glitch and RGB dice play, the dice themselves staying put.
 */
@Composable
private fun BannerScene(logoDie: Dp, animated: Boolean = false) {
    Box(Modifier.fillMaxSize()) {
        TableBackdrop()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            Box(Modifier.size(w * (MAT_CENTRE_X - MAT_RADIUS_X), h), contentAlignment = Alignment.Center) {
                AppLogo(
                    dieSize = logoDie,
                    titleSize = (logoDie.value * 1.9f).sp,
                    diceStyle = DiceStyles.default,
                    cupStyle = DiceCupStyles.default,
                )
            }
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
                val die = h * DIE_HEIGHT_FRACTION * toss.scale
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

/**
 * The launcher icon's table (`ic_launcher_background.xml`): its lit navy felt, and its blue mat and gold ring, here
 * large enough to hold [TOSSES] and running off the banner's other three edges. The app's own backdrop stays as it is.
 */
@Composable
private fun TableBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                0f to Color(0xFF3A6FB5),
                0.55f to Color(0xFF1C3D6E),
                1f to Color(0xFF0F2A50),
                center = Offset(w * 0.42f, h * 0.10f),
                radius = w * 0.78f,
            ),
        )
        val radiusX = w * MAT_RADIUS_X
        val radiusY = h * MAT_RADIUS_Y
        val topLeft = Offset(w * MAT_CENTRE_X - radiusX, h * MAT_CENTRE_Y - radiusY)
        val mat = Size(radiusX * 2, radiusY * 2)
        drawOval(
            Brush.radialGradient(
                0f to Color(0xFF3F86D8),
                0.65f to Color(0xFF2660AA),
                1f to Color(0xFF1A4684),
                center = Offset(w * 0.79f, h * 0.42f),
                radius = maxOf(radiusX, radiusY) * 0.9f,
            ),
            topLeft,
            mat,
        )
        drawOval(Color(0xFFE6C278), topLeft, mat, style = Stroke(h * 0.008f))
    }
}

private const val MAT_CENTRE_X = 0.835f
private const val MAT_CENTRE_Y = 0.50f
private const val MAT_RADIUS_X = 0.295f
private const val MAT_RADIUS_Y = 0.62f

/** A die's height in the banner, as a fraction of the banner's. */
private const val DIE_HEIGHT_FRACTION = 0.17f

/**
 * One die on the mat: which style, which face, where (as fractions of the banner), at what turn, and how much larger
 * than the others. Every die stays inside the mat's ring at both the feature graphic's and the end card's shape.
 */
private data class Toss(val style: String, val value: Int, val x: Float, val y: Float, val angle: Float, val scale: Float = 1f)

private val TOSSES = listOf(
    Toss("cake_strawberry", 5, 0.670f, 0.27f, -14f),
    Toss("googly_ivory", 6, 0.795f, 0.15f, 11f),
    Toss("glitch_black", 4, 0.920f, 0.29f, -8f),
    Toss("rgb_wave", 3, 0.655f, 0.62f, 18f),
    Toss("christmas_baubles", 2, 0.910f, 0.58f, -20f),
    Toss("meadow_green", 6, 0.880f, 0.85f, 9f),
    Toss("world_japanese", 5, 0.730f, 0.82f, -6f),
    // A d20 fills less of its box than a square die, so it's drawn larger to look the same size. Black, as the blue one
    // is lost against the mat.
    Toss("d20_black", 6, 0.785f, 0.47f, 7f, scale = 1.25f),
)

/** One loop of the made-up shaking of the googly dice takes this long, and has [SHAKE_BEATS] beats, each moving [SHAKE_REACH] die sizes. */
private const val SWAY_MILLIS = 3_000
private const val SHAKE_BEATS = 4f
private const val SHAKE_REACH = 0.6f
