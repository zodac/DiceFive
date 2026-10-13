package net.zodac.dicefive.store

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asAndroidBitmap
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
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

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
    fun `feature graphic`() = render("feature-graphic.png") { BannerScene(logoDie = 52.dp) }

    /** The video's last card: 1920x1080, drawn at 3x - the same scene, a little roomier. */
    @Test
    @StorePart(StoreAssets.VIDEO)
    @Config(qualifiers = "w640dp-h360dp-xxhdpi")
    fun `video end card`() = render("video-end-card.png") { BannerScene(logoDie = 40.dp) }

    private fun render(name: String, content: @Composable () -> Unit) {
        compose.setContent { DiceFiveTheme { content() } }
        compose.waitForIdle()
        StoreAssets.save(compose.onRoot().captureToImage().asAndroidBitmap(), File(StoreAssets.outDir, name))
    }

    private companion object {
        const val ICON_PIXELS = 512
        const val SUPERSAMPLE = 4
        const val ADAPTIVE_CANVAS_DP = 108
        const val VISIBLE_DP = 76
    }
}

/**
 * The brand backdrop, the menu's logo in the default dice and cup - no tagline, the logo is the brand - and a
 * handful of the unlockable dice tumbling across the right - a taste of the Styles screen.
 */
@Composable
private fun BannerScene(logoDie: Dp) {
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
            for (toss in TOSSES) {
                Box(
                    Modifier
                        .offset(x = w * toss.x - die / 2, y = h * toss.y - die / 2)
                        .size(die)
                        .rotate(toss.angle),
                ) {
                    DiceStyles.byId(toss.style).Die(value = toss.value, held = false, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** One die on the right of the banner: which style, which face, where (as fractions of the banner) and at what turn. */
private data class Toss(val style: String, val value: Int, val x: Float, val y: Float, val angle: Float)

private val TOSSES = listOf(
    Toss("gems_ivory", 5, 0.62f, 0.30f, -14f),
    Toss("wood_walnut", 6, 0.74f, 0.20f, 11f),
    Toss("retro_amber", 4, 0.87f, 0.32f, -8f),
    Toss("obsidian_lava", 3, 0.66f, 0.68f, 18f),
    Toss("googly_ivory", 2, 0.80f, 0.60f, -20f),
    Toss("marble_white", 6, 0.92f, 0.76f, 9f),
    Toss("cake_pink", 1, 0.55f, 0.84f, -6f),
)
