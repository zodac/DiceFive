package net.zodac.dicefive.store

import android.graphics.Bitmap
import android.graphics.Canvas
import android.annotation.SuppressLint
import android.content.Context
import android.media.SoundPool
import androidx.test.core.app.ApplicationProvider
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.game.AiTurnPlayer
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.game.toTieBreakStats
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSoundPool
import java.io.File
import kotlin.random.Random

/**
 * Shared by the store-asset generators ([StoreScreensGeneratorTest], [StoreArtGeneratorTest]): where they write, which
 * parts a run asked for, the app state they stage, and how a screen is captured. See `.claude/STORE_ASSETS.md` - the
 * script `scripts/generate-store-assets.sh` runs these and finishes what they write.
 */
internal object StoreAssets {

    /** The parts a generator test can be asked for, by `-PstoreAssetParts` (all of them when it's empty). */
    const val ICON = "icon"
    const val FEATURE = "feature"
    const val PHONE = "phone"
    const val TABLET = "tablet"
    const val VIDEO = "video"

    /** The raw renders, before the script captions, flattens and encodes them. Never inside the repo's tracked files. */
    val outDir: File
        get() = System.getProperty("dicefive.storeAssetsDir")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(repoRoot, "build/store-assets/raw")

    private val repoRoot: File
        get() {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
            return dir ?: File(".").absoluteFile
        }

    private fun wants(part: String): Boolean {
        val parts = System.getProperty("dicefive.storeAssetParts").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return parts.isEmpty() || part in parts
    }

    /**
     * A generator, not a check: a test marked [StorePart] only runs with `-PgenerateStoreAssets`, and only when its part
     * was asked for. First in a class's rule order, so a skipped run doesn't launch the app.
     */
    fun onlyWhenAsked(): TestRule = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                assumeTrue("Run scripts/generate-store-assets.sh to make the store assets", System.getProperty("dicefive.generateStoreAssets") == "true")
                val part = checkNotNull(description.getAnnotation(StorePart::class.java)) { "${description.methodName} has no @StorePart" }.value
                assumeTrue("Not asked for: $part", wants(part))
                base.evaluate()
            }
        }
    }

    fun save(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** Which part of the store assets a generator test makes - see [StoreAssets.onlyWhenAsked]. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class StorePart(val value: String)

/**
 * Deals [deal]t values to the next rolls, in order, then rolls at random again. A roll takes one `nextInt(from, until)`
 * per die it rolls (and, in a mode with coloured dice, one more for its colour), so a value per unheld die deals a
 * roll. Installed as [AppContainer.diceRandom] so the video's rolls land on what it means to show.
 */
internal class DealtRandom(private val fallback: Random = Random(5)) : Random() {
    private val dealt = ArrayDeque<Int>()

    fun deal(vararg values: Int) {
        dealt.addAll(values.toList())
    }

    override fun nextBits(bitCount: Int): Int = fallback.nextBits(bitCount)

    override fun nextInt(from: Int, until: Int): Int {
        val next = dealt.removeFirstOrNull() ?: return fallback.nextInt(from, until)
        require(next in from until until) { "Dealt $next, but this draw is from $from until $until" }
        return next
    }
}

/** The people at the table in every staged game and in the recorded history. Short, so no name is ever cut. */
internal object Cast {
    const val YOU = "Sam"
    val FRIENDS = listOf("Jo", "Alex")
    const val CPU = "Ada"
}

/** One set of Styles picks, by id - see StyleCatalog.kt. */
internal data class StylePicks(
    val dice: String = "classic_ivory",
    val cup: String = "classic_gold",
    val background: String = "classic_navy",
    val mat: String = "classic_blue",
    val frame: String = "classic_ring",
)

/** The app state behind every store screen: a believable history, most achievements earned, and a game to resume. */
internal object StoreState {

    /**
     * Hard, long-haul achievements left locked, so the Achievements screen shows some still to earn - none of them can
     * be earned by anything a generator does. Two more than that would need: with these and [FIRST_UNLOCK] locked (the
     * video's seed), 77 are unlocked, and the video's 5x makes 78 - between the Styles screen's unlock steps at 75 and 80, so it never
     * also brings a "Styles unlocked" banner. Check that still holds when achievements or style unlocks change.
     */
    private val STILL_LOCKED = setOf(
        Achievement.CENTURION, Achievement.UNTOUCHABLE, Achievement.PROFESSIONAL_ROLLER, Achievement.HAT_TRICK,
        Achievement.HIGH_ROLLER, Achievement.DICE_DEITY, Achievement.DICE_WHISPERER, Achievement.EVERYTHING_EVERYWHERE_ALL_AT_ONCE,
        Achievement.LUCKY_SEVEN, Achievement.CRITICAL_SUCCESS, Achievement.ROCK_BOTTOM, Achievement.HOW_DO_YOU_PLAY_THIS_GAME,
        Achievement.COMPLETIONIST, Achievement.ARCHIVIST, Achievement.HISTORIAN, Achievement.LOW_ROLLS,
        Achievement.COLD_DICE,
    )

    /** Left locked for the video to unlock for real, banner and all, when it scores its 5x. */
    val FIRST_UNLOCK = Achievement.FIVE_X

    private const val DAY_MILLIS = 86_400_000L

    /**
     * Resets everything, then seeds the history, achievements and [styles]. Waits until it's all on disk. [forVideo]
     * leaves [FIRST_UNLOCK] locked for the video to earn; the screenshots have it, or the last game would unlock it.
     */
    fun seed(container: AppContainer, styles: StylePicks = StylePicks(), forVideo: Boolean = false) = runBlocking {
        container.scoreRepository.resetLeaderboard()
        container.achievementsRepository.resetAll()
        container.inProgressGameRepository.clear()
        withTimeout(10_000) { container.inProgressGameRepository.hasInProgressGame.first { !it } }

        val now = System.currentTimeMillis()
        val random = Random(2026)
        // Every one the Achievements screen counts - hidden ones too, which would otherwise unlock, and banner, in the
        // middle of a scene - but the Easter eggs, which it doesn't, and those left locked on purpose.
        val earnable = Achievement.entries.filter {
            it.category != AchievementCategory.EASTER_EGGS && it !in STILL_LOCKED && !(forVideo && it == FIRST_UNLOCK)
        }
        container.achievementsRepository.record(
            earnable.mapIndexed { index, achievement -> achievement to now - (earnable.size - index) * DAY_MILLIS / 2 }.toMap(),
            emptyMap(),
        )
        recordHistory(container, now, random)

        val settings = container.settingsRepository
        settings.setPlayerName(1, Cast.YOU)
        settings.setDiceStyleId(styles.dice)
        settings.setDiceCupStyleId(styles.cup)
        settings.setTableBackgroundId(styles.background)
        settings.setDiceMatId(styles.mat)
        settings.setScoreFrameId(styles.frame)
    }

    /** Applies [styles] - for a scene that wants a different look from the one [seed] set. */
    fun applyStyles(container: AppContainer, styles: StylePicks) = runBlocking {
        val settings = container.settingsRepository
        settings.setDiceStyleId(styles.dice)
        settings.setDiceCupStyleId(styles.cup)
        settings.setTableBackgroundId(styles.background)
        settings.setDiceMatId(styles.mat)
        settings.setScoreFrameId(styles.frame)
        withTimeout(10_000) { settings.diceStyleId.first { it == styles.dice } }
    }

    /** Saves [state] as the game Continue resumes, and waits until the menu can see it. */
    fun saveGame(container: AppContainer, state: GameState) = runBlocking {
        container.inProgressGameRepository.save(state)
        // This game, not just a game: leaving one keeps it saved, and the write is queued.
        withTimeout(10_000) {
            while (container.inProgressGameRepository.load() != state) delay(20)
        }
    }

    /**
     * About two months of real games - every one played out by the CPU in full, so every score, 5x and bonus is one
     * the rules allow - recorded the way a finished game records its human players.
     */
    private suspend fun recordHistory(container: AppContainer, now: Long, random: Random) {
        val modes = listOf(GameMode.STANDARD, GameMode.STANDARD, GameMode.STANDARD, GameMode.TRICOLOUR, GameMode.QUICKFIRE, GameMode.THIRD_WIND)
        val games = 36
        for (game in 0 until games) {
            val mode = modes[random.nextInt(modes.size)]
            val friends = Cast.FRIENDS.shuffled(random).take(random.nextInt(0, 3))
            val withCpu = friends.isEmpty() || random.nextBoolean()
            val configs = buildList {
                add(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = Cast.YOU, difficulty = Difficulty.MEDIUM))
                friends.forEach { add(PlayerConfig(slot = size + 1, type = PlayerType.HUMAN, name = it, difficulty = Difficulty.MEDIUM)) }
                if (withCpu) add(PlayerConfig(slot = size + 1, type = PlayerType.AI, name = Cast.CPU, difficulty = Difficulty.MEDIUM))
            }
            val finished = playOut(GameEngine.newGame(configs, gameMode = mode, random = random), random)
            val when_ = now - (games - game) * DAY_MILLIS * 5 / 3 - random.nextLong(DAY_MILLIS / 2)
            val top = finished.players.maxOf { it.totalScore }
            finished.players.forEachIndexed { index, player ->
                if (player.type != PlayerType.HUMAN) return@forEachIndexed
                container.scoreRepository.recordScore(
                    player.name,
                    player.toTieBreakStats(),
                    won = if (finished.players.size > 1) player.totalScore == top else null,
                    isPrimaryPlayer = index == 0,
                    onLeaderboard = finished.countsOnLeaderboard,
                    gameMode = mode,
                    playedUnmodified = true,
                    timestampEpochMillis = when_,
                )
            }
        }
    }

    /** Plays every remaining turn of [state] with the CPU's own play. */
    fun playOut(state: GameState, random: Random): GameState {
        var current = state
        while (!current.isGameOver) current = AiTurnPlayer.playTurn(current, random)
        return current
    }

    /** Plays [turns] turns of [state] with the CPU's own play. */
    fun play(state: GameState, turns: Int, random: Random): GameState {
        var current = state
        repeat(turns) { current = AiTurnPlayer.playTurn(current, random) }
        return current
    }
}

/**
 * What the screen shows right now, as the phone would: the app's window, with every overlay window - the achievement
 * banner is one - drawn over it where its gravity puts it. Robolectric neither places a window by its gravity nor
 * captures an overlay window's own pixels (a root's captureToImage returns the screen under it), so each window's view
 * tree is drawn here directly, bottom-most first.
 */
internal fun ComposeTestRule.captureScreen(): Bitmap {
    waitForIdle()
    val windows = windowViews()
    val main = windows.first()
    val screen = Bitmap.createBitmap(main.width, main.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(screen)
    main.draw(canvas)
    for (overlay in windows.drop(1)) {
        if (overlay.width == 0 || overlay.height == 0 || overlay.visibility != View.VISIBLE) continue
        val gravity = (overlay.layoutParams as? WindowManager.LayoutParams)?.gravity ?: Gravity.CENTER
        val left = (screen.width - overlay.width) / 2f
        val top = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.BOTTOM -> (screen.height - overlay.height).toFloat()
            Gravity.TOP -> 0f
            else -> (screen.height - overlay.height) / 2f
        }
        canvas.save()
        canvas.translate(left, top)
        overlay.draw(canvas)
        canvas.restore()
    }
    return screen
}

/** Every window's root view, the activity's first - read from the framework's own list of them. */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
private fun windowViews(): List<View> {
    val global = Class.forName("android.view.WindowManagerGlobal")
    val instance = global.getMethod("getInstance").invoke(null)
    val field = global.getDeclaredField("mViews").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    val views = (field.get(instance) as List<View>).filter { it.isAttachedToWindow }
    val main = views.maxBy { it.width.toLong() * it.height }
    return listOf(main) + views.filter { it !== main }
}

/** How many overlay windows are up - the achievement banners' among them. */
internal fun ComposeTestRule.overlayCount(): Int = windowViews().size - 1

/**
 * Records the screen one frame at a time on the test's own clock, so every frame is exactly one frame on from the last
 * however long it took to draw: the video plays smoothly at 60fps whatever the machine. A tap draws a fading touch
 * ring, like Android's "Show taps", so a viewer can see what was pressed. Each sound the app plays is logged with its
 * frame ([SoundLog]) - the test needs [RecordingSoundPool] as a shadow for that.
 */
internal class FrameRecorder(private val compose: ComposeTestRule, private val dir: File) {
    private var frame = 0
    private val taps = mutableListOf<Pair<Offset, Int>>()

    init {
        dir.deleteRecursively()
        dir.mkdirs()
        SoundLog.file = File(dir, "sounds.txt").apply { writeText("") }
        SoundLog.frame = 0
        compose.mainClock.autoAdvance = false
    }

    /** Records [millis] of whatever the screen is doing. */
    fun hold(millis: Long) {
        repeat(((millis + FRAME_MILLIS - 1) / FRAME_MILLIS).toInt()) { step() }
    }

    /** Hands the clock back to the test, to move on to the next scene. */
    fun finish() {
        SoundLog.file = null
        compose.mainClock.autoAdvance = true
    }

    /** Taps [node] - centre of its bounds - and shows the touch. */
    fun tap(node: SemanticsNodeInteraction) {
        taps += node.fetchSemanticsNode().boundsInRoot.center to frame
        node.performClick()
    }

    private fun step() {
        SoundLog.frame = frame
        compose.mainClock.advanceTimeBy(FRAME_MILLIS)
        val screen = compose.captureScreen()
        drawTaps(screen)
        StoreAssets.save(screen, File(dir, "%05d.png".format(frame)))
        frame++
    }

    private fun drawTaps(screen: Bitmap) {
        val canvas = Canvas(screen)
        val density = screen.width / 412f
        for ((at, start) in taps) {
            val age = frame - start
            if (age !in 0 until TAP_FRAMES) continue
            val fade = 1f - age / TAP_FRAMES.toFloat()
            val radius = (22f + age * 1.2f) * density
            canvas.drawCircle(at.x, at.y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb((90 * fade).toInt(), 255, 255, 255) })
            canvas.drawCircle(
                at.x, at.y, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * density
                    color = android.graphics.Color.argb((200 * fade).toInt(), 255, 255, 255)
                },
            )
        }
    }

    private companion object {
        /** The Compose test clock's frame. The video is encoded at 60fps, so it plays 4% slower than the app - unnoticeable. */
        const val FRAME_MILLIS = 16L
        const val TAP_FRAMES = 18
    }
}

/**
 * Where [RecordingSoundPool] writes each sound the app plays while a [FrameRecorder] is running: one line per sound,
 * `<frame> <clip> <volume>`, the clip being its res/raw name. The script mixes those clips in at those frames, so the
 * video's sound is the app's own, in step with what's on screen.
 */
internal object SoundLog {
    var file: File? = null
    var frame: Int = 0

    fun played(clip: String, volume: Float) {
        file?.appendText("$frame $clip $volume\n")
    }
}

/**
 * Robolectric's SoundPool, but every clip loads at once and every play is written to [SoundLog]. Its own shadow never
 * reports a clip loaded, so the app (which waits for that before playing - see AndroidSoundPlayer) would play nothing.
 */
@Implements(SoundPool::class)
class RecordingSoundPool : ShadowSoundPool() {
    private val clipById = mutableMapOf<Int, Int>()

    @Implementation
    override fun load(context: Context, resId: Int, priority: Int): Int =
        super.load(context, resId, priority).also { clipById[it] = resId }

    @Implementation
    override fun setOnLoadCompleteListener(listener: SoundPool.OnLoadCompleteListener?) {
        super.setOnLoadCompleteListener(listener)
        clipById.values.forEach { notifyResourceLoaded(it, true) }
    }

    /** Set while a play is being passed on: one variant of `_play` calls the other, and a play is logged once. */
    private var playing = false

    @Implementation
    override fun _play(soundId: Int, leftVolume: Float, rightVolume: Float, priority: Int, loop: Int, rate: Float): Int =
        recording(soundId, leftVolume) { super._play(soundId, leftVolume, rightVolume, priority, loop, rate) }

    @Implementation
    override fun _play(soundId: Int, leftVolume: Float, rightVolume: Float, priority: Int, loop: Int, rate: Float, playerIId: Int): Int =
        recording(soundId, leftVolume) { super._play(soundId, leftVolume, rightVolume, priority, loop, rate, playerIId) }

    private fun recording(soundId: Int, volume: Float, play: () -> Int): Int {
        if (playing) return play()
        record(soundId, volume)
        playing = true
        try {
            return play()
        } finally {
            playing = false
        }
    }

    private fun record(soundId: Int, volume: Float) {
        val resId = clipById[soundId] ?: return
        SoundLog.played(ApplicationProvider.getApplicationContext<Context>().resources.getResourceEntryName(resId), volume)
    }
}
