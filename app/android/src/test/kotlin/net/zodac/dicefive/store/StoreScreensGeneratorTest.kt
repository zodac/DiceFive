package net.zodac.dicefive.store

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.MainActivity
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/**
 * Renders the store listing's screenshots (phone and 7-inch tablet) and the promo video's frames from the real app,
 * driven through its own screens. A generator, not a check - see [StoreAssets.onlyWhenAsked] and
 * `.claude/STORE_ASSETS.md`.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreensGeneratorTest {

    private companion object {
        const val GAME = "game"
        const val MODES = "modes"
        const val STYLES = "styles"
        const val GAME_OVER = "game-over"
        const val ACHIEVEMENTS = "achievements"
        val PHONE_SHOTS = setOf(GAME, MODES, STYLES, GAME_OVER, ACHIEVEMENTS)
        val TABLET_SHOTS = setOf(GAME, STYLES, GAME_OVER, ACHIEVEMENTS)

        init {
            // The test's own clock has to drive the app - see MainActivity.useCappedFrameClock.
            MainActivity.useCappedFrameClock = false
        }
    }

    @get:Rule(order = 0)
    val onlyWhenAsked = StoreAssets.onlyWhenAsked()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val container: AppContainer
        get() = AndroidAppContainer.get(ApplicationProvider.getApplicationContext())

    @After
    fun rollAtRandomAgain() {
        container.diceRandom = Random.Default
    }

    @Test
    @StorePart(StoreAssets.PHONE)
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `phone screenshots`() = screenshots("phone", PHONE_SHOTS)

    @Test
    @StorePart(StoreAssets.TABLET)
    @Config(qualifiers = "w600dp-h960dp-xhdpi")
    fun `7-inch tablet screenshots`() = screenshots("tablet-7", TABLET_SHOTS)

    /**
     * Play takes 2 to 8 screenshots per device type, and only considers a listing for featuring with at least 4 at
     * 1080px or more; most people see only the first two or three. So: a few strong ones, not all eight - phone gets
     * 5, the 7-inch tablet the 4 that featuring asks for. Names are listing order, and the script captions each.
     */
    private fun screenshots(device: String, shots: Set<String>) {
        val dir = File(StoreAssets.outDir, device).apply { deleteRecursively(); mkdirs() }
        var number = 0
        fun shoot(name: String) {
            settle()
            StoreAssets.save(compose.captureScreen(), File(dir, "%d-%s.png".format(++number, name)))
        }
        StoreState.seed(container)

        if (GAME in shots) {
            resume(Scenes.fourSixes())
            shoot(GAME)
            leaveGame()
        }

        if (MODES in shots) {
            StoreState.applyStyles(container, Scenes.SHOWPIECE)
            resume(Scenes.tricolour(rolled = true))
            shoot(MODES)
            leaveGame()
            StoreState.applyStyles(container, StylePicks())
        }

        if (STYLES in shots) {
            openFromMenu("Styles")
            compose.waitUntil(5_000) { exists(hasContentDescription("Dice gallery")) }
            compose.onNode(hasContentDescription("Dice gallery")).performClick()
            shoot(STYLES)
            back()
        }

        if (GAME_OVER in shots) {
            resume(Scenes.lastTurn())
            // On the frame clock from the tap, so the shot lands in the fireworks rather than after them.
            compose.mainClock.autoAdvance = false
            compose.onNode(openBox()).performClick()
            stepUntil { exists(hasText("Winner")) }
            stepFor(1_200)
            StoreAssets.save(compose.captureScreen(), File(dir, "%d-%s.png".format(++number, GAME_OVER)))
            compose.mainClock.autoAdvance = true
            back()
            menu()
        }

        if (ACHIEVEMENTS in shots) {
            openFromMenu("Achievements")
            settle()
            AchievementEvents.emit(AchievementEvent.Unlocked(StoreState.FIRST_UNLOCK))
            stepUntil { compose.overlayCount() > 0 }
            // Far enough for the banner to have slid fully in.
            stepFor(800)
            StoreAssets.save(compose.captureScreen(), File(dir, "%d-%s.png".format(++number, ACHIEVEMENTS)))
            stepUntil(15_000) { compose.overlayCount() == 0 }
            back()
        }
    }

    /**
     * The promo video's scenes, one folder of 60fps frames each, in playing order. The script lays each over its
     * caption and joins them.
     */
    @Test
    @StorePart(StoreAssets.VIDEO)
    // A little shorter than the screenshots' phone: in a 16:9 frame the screen's height is what limits it, and a tall
    // phone leaves empty space under the dice - this one fills more of the frame with the game.
    @Config(qualifiers = "w412dp-h800dp-xhdpi", shadows = [RecordingSoundPool::class])
    fun `promo video frames`() {
        val root = File(StoreAssets.outDir, "video").apply { deleteRecursively(); mkdirs() }
        StoreState.seed(container, forVideo = true)

        // Roll, hold three sixes, roll the other two into a 5x, score it - which unlocks its achievement, banner and all.
        val dealt = DealtRandom().apply {
            deal(6, 3, 6, 6, 1)
            deal(6, 6)
        }
        container.diceRandom = dealt
        resume(Scenes.fourSixes(rolled = false))
        settle()
        FrameRecorder(compose, File(root, "1-game")).apply {
            hold(700)
            tap(cup())
            hold(1_900)
            for (die in listOf(1, 3, 4)) {
                tap(compose.onNode(hasContentDescription("Die $die,", substring = true)))
                hold(260)
            }
            hold(400)
            tap(cup())
            hold(2_000)
            tap(compose.onNode(hasContentDescription("5x")))
            hold(2_400)
            finish()
        }
        container.diceRandom = Random.Default
        leaveGame()

        // A Tricolour game at a dressed-up table.
        StoreState.applyStyles(container, Scenes.SHOWPIECE)
        resume(Scenes.tricolour(rolled = false))
        settle()
        FrameRecorder(compose, File(root, "2-modes")).apply {
            hold(500)
            tap(cup())
            hold(2_600)
            finish()
        }
        leaveGame()
        StoreState.applyStyles(container, StylePicks())

        // Styles: the dice gallery, picking a few.
        openFromMenu("Styles")
        compose.waitUntil(5_000) { exists(hasContentDescription("Dice gallery")) }
        compose.onNode(hasContentDescription("Dice gallery")).performClick()
        settle()
        FrameRecorder(compose, File(root, "3-styles")).apply {
            hold(600)
            for (style in Scenes.VIDEO_DICE_PICKS) {
                // The first tile of that family - the dice gallery comes first on the page.
                tap(compose.onAllNodes(hasContentDescription("$style,", substring = true))[0])
                hold(900)
            }
            finish()
        }
        back()

        // The last box of a game Sam wins.
        resume(Scenes.lastTurn())
        settle()
        FrameRecorder(compose, File(root, "4-game-over")).apply {
            hold(600)
            tap(compose.onNode(openBox()))
            hold(3_600)
            finish()
        }
        back()
        menu()

        // An achievement unlocking.
        openFromMenu("Achievements")
        settle()
        FrameRecorder(compose, File(root, "5-achievements")).apply {
            hold(500)
            // Not the 5x again - the first scene unlocked that one for real.
            AchievementEvents.emit(AchievementEvent.Unlocked(Achievement.SCORE_300))
            hold(2_800)
            finish()
        }
    }

    // ---- Driving the app -------------------------------------------------------------------------------------------

    private fun exists(matcher: SemanticsMatcher): Boolean = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /**
     * Steps the clock a frame at a time until [done] - a new window (the banners') only appears as the clock moves on
     * under Robolectric, so a plain wait never sees it.
     */
    private fun stepUntil(timeoutMillis: Long = 5_000, done: () -> Boolean) {
        val wasAuto = compose.mainClock.autoAdvance
        compose.mainClock.autoAdvance = false
        var waited = 0L
        while (!done()) {
            check(waited < timeoutMillis) { "Still waiting after ${timeoutMillis}ms" }
            compose.mainClock.advanceTimeBy(16)
            // The framework's frames run on the looper's clock, not Compose's: a new window is attached on one.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
            waited += 16
        }
        compose.mainClock.autoAdvance = wasAuto
    }

    private fun stepFor(millis: Long) {
        val wasAuto = compose.mainClock.autoAdvance
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(millis)
        compose.mainClock.autoAdvance = wasAuto
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun menu() {
        compose.waitUntil(10_000) { exists(hasText("Settings")) && exists(hasText("Styles")) }
    }

    /** Opens a menu screen - retried, since a back press can still be settling the menu in. */
    private fun openFromMenu(name: String) {
        menu()
        compose.onNode(hasText(name)).performClick()
        compose.waitForIdle()
    }

    private fun back() {
        Espresso.pressBack()
        compose.waitForIdle()
    }

    /** Saves [state] and continues it from the menu. */
    private fun resume(state: GameState) {
        StoreState.saveGame(container, state)
        menu()
        compose.waitUntil(10_000) { exists(hasText("Continue")) }
        compose.onNode(hasText("Continue")).performClick()
        compose.waitUntil(10_000) { exists(hasContentDescription("Dice cup", substring = true)) }
    }

    private fun leaveGame() {
        Espresso.pressBack()
        compose.waitForIdle()
        if (exists(hasText("Leave"))) compose.onNode(hasText("Leave")).performClick()
        menu()
    }

    private fun cup(): SemanticsNodeInteraction = compose.onNode(hasContentDescription("Dice cup", substring = true))

    /** The one box still open on the player's card - what the staged last turn scores. */
    private fun openBox(): SemanticsMatcher = SemanticsMatcher("an open box") {
        it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Would score") == true
    }

}

/** The staged games. Every one is a real game played by the CPU to the moment shown - only the shown rolls are dealt. */
internal object Scenes {

    /** A dressed-up table, for the Tricolour scenes: all unlocked by the achievements [StoreState.seed] grants. */
    val SHOWPIECE = StylePicks(dice = "obsidian_lava", cup = "treasure_chest_oak", background = "planks_walnut", mat = "casino_green", frame = "laurel")

    /** The dice the video picks on the Styles screen, by family - the start of a tile's spoken name, "Gems, Ivory". */
    val VIDEO_DICE_PICKS = listOf("Metal", "Obsidian", "Retro", "Cake")

    private fun table(): List<PlayerConfig> = listOf(
        // Medium for all: the CPU plays every staged turn, and Hard's exhaustive search makes finding a game slow.
        PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = Cast.YOU, difficulty = Difficulty.MEDIUM),
        PlayerConfig(slot = 2, type = PlayerType.AI, name = Cast.CPU, difficulty = Difficulty.MEDIUM),
        PlayerConfig(slot = 3, type = PlayerType.HUMAN, name = Cast.FRIENDS.first(), difficulty = Difficulty.MEDIUM),
    )

    /**
     * Round 8 of a three-player game, Sam to play with Sixes, 4x and 5x still open. [rolled]: two rolls in, four
     * sixes held - the screenshot. Otherwise the turn not yet started - the video, which deals its own.
     */
    fun fourSixes(rolled: Boolean = true): GameState {
        val state = (1..500).asSequence().map { seed ->
            val random = Random(seed)
            StoreState.play(GameEngine.newGame(table(), random = random), turns = 3 * 7, random)
        }.first { state ->
            val sam = state.players[0]
            listOf(ScoreCategory.SIXES, ScoreCategory.FOUR_OF_A_KIND, ScoreCategory.FIVE_OF_A_KIND).all(sam::isOpen) &&
                sam.totalScore in 110..150
        }
        if (!rolled) return state
        val dealt = DealtRandom().apply {
            deal(6, 3, 6, 6, 1)
            deal(6, 2)
        }
        var current = GameEngine.rollDice(state, dealt)
        for (die in listOf(0, 2, 3)) current = GameEngine.toggleHold(current, die)
        current = GameEngine.rollDice(current, dealt)
        return GameEngine.toggleHold(current, 1)
    }

    /** Round 6 of Sam against the CPU in Tricolour - [rolled] once, or not yet. */
    fun tricolour(rolled: Boolean): GameState {
        val random = Random(11)
        val players = table().take(2)
        val state = StoreState.play(GameEngine.newGame(players, gameMode = GameMode.TRICOLOUR, random = random), turns = 2 * 5, random)
        return if (rolled) GameEngine.rollDice(state, DealtRandom().apply { deal(5, 0, 5, 2, 3, 1, 5, 0, 2, 1) }) else state
    }

    /** The last turn of a game Sam has already won: Jo, last to play, has rolled, with one box to fill. */
    fun lastTurn(): GameState = (1..500).asSequence().map { seed ->
        val random = Random(seed)
        val state = StoreState.play(GameEngine.newGame(table(), random = random), turns = 3 * 13 - 1, random)
        GameEngine.rollDice(state, random)
    }.first { state ->
        val (sam, cpu, jo) = state.players
        sam.totalScore >= 240 && sam.totalScore > cpu.totalScore + 20 && sam.totalScore > jo.totalScore + 60
    }
}
