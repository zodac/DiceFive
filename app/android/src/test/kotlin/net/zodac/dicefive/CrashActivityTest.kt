package net.zodac.dicefive

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.device.CrashReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The page an error nothing caught opens ([CrashActivity]): the report it was handed, to copy or post, and a Restart
 * that opens the app again in place of the page.
 */
// A phone's size: on Robolectric's default 320x470px screen the details section has barely any room.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CrashActivityTest {

    private val report = CrashReport(
        app = "DiceFive test",
        android = "17 (API 37)",
        device = "Google Pixel",
        screen = "411x914dp, font 1.0x",
        language = "en-GB",
        thread = "main",
        lastActions = listOf("Opened play_game", "Rolled, player 1"),
        trace = "java.lang.IllegalStateException: Boom",
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val compose = AndroidComposeTestRule(ActivityScenarioRule<CrashActivity>(CrashActivity.intent(context, report))) { rule ->
        var activity: CrashActivity? = null
        rule.scenario.onActivity { activity = it }
        checkNotNull(activity)
    }

    @Test
    fun `the page shows the report it was opened with`() {
        compose.onNodeWithText("Something went wrong").assertExists()
        compose.onNodeWithText("Show details").performScrollTo().performClick()
        compose.onNodeWithText(report.text()).performScrollTo().assertExists()
    }

    @Test
    fun `Copy details puts the report on the clipboard`() {
        compose.onNodeWithText("Show details").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Copy details").performClick()
        compose.waitForIdle()
        val clip = checkNotNull(context.getSystemService(ClipboardManager::class.java).primaryClip)
        assertEquals(report.text(), clip.getItemAt(0).text.toString())
    }

    @Test
    fun `Report on GitHub opens the browser at a new issue with the report filled in`() {
        compose.onNodeWithText("Report on GitHub").performClick()
        val started = checkNotNull(shadowOf(compose.activity).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(report.issueUrl(), started.dataString)
    }

    @Test
    fun `Restart opens the app in a task of its own and closes the page`() {
        compose.onNodeWithText("Restart").performClick()
        val started = checkNotNull(shadowOf(compose.activity).nextStartedActivity)
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 && started.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertTrue(compose.activity.isFinishing)
    }

    @Test
    fun `Compose resources still keep their context where the crash process sets it`() {
        val field = Class.forName(CrashActivity.RESOURCES_CONTEXT_CLASS).getDeclaredField(CrashActivity.RESOURCES_CONTEXT_FIELD)
        assertEquals(Context::class.java, field.type)
    }
}
