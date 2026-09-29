package net.zodac.dicefive

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real app, launched through [MainActivity] under Robolectric - the sandbox has no device or
 * emulator. Everything the shared module leaves to the platform meets here: the composition locals
 * MainActivity provides, Compose Multiplatform resources (the Sora font, the vector icons) packaged
 * from :app:shared, navigation, the storage behind AndroidAppContainer, and system back reaching the
 * shared BackHandler through the Activity.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivitySmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `a game can be started from the menu and system back asks before leaving it`() {
        compose.onNodeWithText("DiceFive").assertExists()

        // A game left in progress by an earlier test run splits Play into New Game and Continue.
        val play = if (compose.onAllNodesWithText("Play").fetchSemanticsNodes().isNotEmpty()) "Play" else "New Game"
        compose.onNodeWithText(play).performClick()
        compose.onNodeWithText("Start Game").performClick()
        compose.waitForIdle()

        Espresso.pressBack()
        compose.onNodeWithText("Leave game?").assertExists()

        compose.onNodeWithText("Leave").performClick()
        // Left, not finished: the game is saved, so the menu offers to continue it.
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty() }
    }
}
