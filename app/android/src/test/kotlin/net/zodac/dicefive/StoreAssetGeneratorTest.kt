package net.zodac.dicefive

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.data.achievements.AchievementEvent
import net.zodac.dicefive.data.achievements.AchievementEvents
import net.zodac.dicefive.device.AndroidAppContainer
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.game.TieBreakStats
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.ScoreCategory
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Renders the Play Store listing's screenshots and icon into the root assets/ folder - a generator, not a check, so it
 * only runs when asked: `./gradlew :app:android:testDebugUnitTest --tests '*StoreAssetGeneratorTest*'
 * -PgenerateStoreAssets` (see .claude/ASSETS.md). Skipped in every other run.
 */
@RunWith(AndroidJUnit4::class)
class StoreAssetGeneratorTest {

    /** Ahead of [compose], so a skipped run doesn't launch the app first. */
    @get:Rule(order = 0)
    val onlyWhenAsked = TestRule { base, _ ->
        assumeTrue("Run with -PgenerateStoreAssets to regenerate the store assets", System.getProperty("dicefive.generateStoreAssets") == "true")
        base
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        init {
            MainActivity.useCappedFrameClock = false
        }
    }

    private fun seedCleanState(preUnlockAll: Boolean = true) {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val container = AndroidAppContainer.get(context)
            container.scoreRepository.resetLeaderboard()
            container.achievementsRepository.resetAll()
            container.inProgressGameRepository.clear()

            val now = System.currentTimeMillis()
            for (i in 1..400) {
                container.scoreRepository.recordScore(
                    playerName = "Player 1",
                    stats = TieBreakStats(if (i == 1) 312 else 310, 1, 0, null, 63, 24, 28, 35),
                    won = true,
                    isPrimaryPlayer = true,
                    timestampEpochMillis = now - 86_400_000L * 4 + i * 1000L
                )
            }
            container.scoreRepository.recordScore(
                playerName = "Player 2",
                stats = TieBreakStats(198, 0, 2, null, 45, 18, 15, 20),
                won = false,
                isPrimaryPlayer = false,
                timestampEpochMillis = now - 86_400_000L * 3
            )

            if (preUnlockAll) {
                val allUnlocked = Achievement.entries.associateWith { now - 86_400_000L }
                container.achievementsRepository.record(allUnlocked, emptyMap())
            }

            val configs = listOf(
                PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Player 1"),
                PlayerConfig(slot = 2, type = PlayerType.AI, name = "Player 2", difficulty = Difficulty.MEDIUM)
            )
            var gameState = GameEngine.newGame(configs)
            gameState = GameEngine.rollDice(gameState)
            gameState = GameEngine.commitScore(gameState, ScoreCategory.ONES)
            gameState = GameEngine.rollDice(gameState)
            gameState = GameEngine.toggleHold(gameState, 0)
            gameState = GameEngine.toggleHold(gameState, 3)
            container.inProgressGameRepository.save(gameState)
        }
    }

    private val assetsDir: File
        get() {
            var dir = File(System.getProperty("user.dir") ?: ".")
            while (true) {
                if (File(dir, "settings.gradle.kts").exists()) {
                    return File(dir, "assets")
                }
                val parent = dir.parentFile ?: break
                dir = parent
            }
            return File(System.getProperty("user.dir") ?: ".", "assets")
        }

    private fun saveScreenshot(subpath: String, filename: String) {
        try {
            val dir = File(assetsDir, "screenshots/$subpath")
            dir.mkdirs()
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, filename).outputStream().use { stream ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        } catch (_: Exception) {
            // Read-only or restricted filesystem in CI environments
        }
    }

    private fun generateAppIcon() {
        try {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val dir = assetsDir
            dir.mkdirs()

            val highResIcon = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            val iconCanvas = Canvas(highResIcon)
            iconCanvas.setDrawFilter(PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            iconCanvas.drawColor(android.graphics.Color.parseColor("#0B132B"))

            val foregroundDrawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
            if (foregroundDrawable != null) {
                val margin = 80
                foregroundDrawable.setBounds(margin, margin, 1024 - margin, 1024 - margin)
                foregroundDrawable.draw(iconCanvas)
            }
            val iconBmp = Bitmap.createScaledBitmap(highResIcon, 512, 512, true)
            File(dir, "app_icon_512.png").outputStream().use { stream ->
                iconBmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            File(dir, "feature_graphic.png").delete()
            // Remove tablet_10 if it exists
            File(dir, "screenshots/tablet_10").deleteRecursively()
        } catch (_: Exception) {
            // Read-only or restricted filesystem in CI environments
        }
    }

    private fun captureAllScreens(subpath: String) {
        // 1. Main Menu
        saveScreenshot(subpath, "main_menu.png")

        // 2. Game Board (Mid-game)
        val continueBtn = compose.onAllNodesWithText("Continue")
        if (continueBtn.fetchSemanticsNodes().isNotEmpty()) {
            continueBtn[0].performClick()
        }
        compose.waitForIdle()
        saveScreenshot(subpath, "game_board.png")

        // Back to menu
        androidx.test.espresso.Espresso.pressBack()
        if (compose.onAllNodesWithText("Leave game?").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Leave").performClick()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }

        // 3. Styles Screen (Open gallery view for 'Dice Cups')
        compose.onNodeWithText("Styles").performClick()
        compose.waitForIdle()
        // The page appears once the navigation has settled, which an idle wait doesn't always cover.
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithContentDescription("Dice Cup gallery").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Dice Cup gallery").performClick()
        compose.waitForIdle()
        saveScreenshot(subpath, "styles.png")
        androidx.test.espresso.Espresso.pressBack()

        // 4. Rules Screen
        compose.onNodeWithText("Rules").performClick()
        compose.waitForIdle()
        saveScreenshot(subpath, "rules.png")
        androidx.test.espresso.Espresso.pressBack()

        // 5. Leaderboard Screen
        compose.onNodeWithText("Leaderboard").performClick()
        compose.waitForIdle()
        saveScreenshot(subpath, "leaderboard.png")
        androidx.test.espresso.Espresso.pressBack()

        // 6. Achievements Screen (with active achievement banner)
        compose.onNodeWithText("Achievements").performClick()
        compose.waitForIdle()
        AchievementEvents.emit(AchievementEvent.Unlocked(Achievement.THE_JOURNEY_BEGINS))
        compose.waitForIdle()
        saveScreenshot(subpath, "achievements.png")
        androidx.test.espresso.Espresso.pressBack()

        // 7. Statistics Screen
        compose.onNodeWithText("Statistics").performClick()
        compose.waitForIdle()
        saveScreenshot(subpath, "statistics.png")
        androidx.test.espresso.Espresso.pressBack()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `generate phone assets`() {
        seedCleanState(preUnlockAll = true)
        generateAppIcon()
        compose.waitForIdle()
        captureAllScreens("phone")
    }

    @Test
    @Config(qualifiers = "w600dp-h960dp-mdpi")
    fun `generate 7-inch tablet assets`() {
        seedCleanState(preUnlockAll = true)
        compose.waitForIdle()
        captureAllScreens("tablet_7")
    }
}
