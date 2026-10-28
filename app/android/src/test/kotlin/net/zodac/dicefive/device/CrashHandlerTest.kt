package net.zodac.dicefive.device

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.CrashActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * An error nothing caught opens the crash page with everything a report needs, then ends the app's process itself,
 * without the platform's handler and its "has stopped" dialog. An error with no page - it couldn't be opened, or the app
 * isn't on screen - goes to the platform's handler.
 */
@RunWith(AndroidJUnit4::class)
class CrashHandlerTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val original = Thread.getDefaultUncaughtExceptionHandler()
    private val platformSaw = mutableListOf<Throwable>()
    private var processEnds = 0
    private var onScreen = true

    @Before
    fun installOverRecordingPlatform() {
        Thread.setDefaultUncaughtExceptionHandler { _, error -> platformSaw += error }
        install(application)
        shadowOf(application).clearNextStartedActivities()
    }

    @After
    fun restoreHandler() {
        Thread.setDefaultUncaughtExceptionHandler(original)
    }

    private fun install(context: Context) = CrashHandler.install(context, endProcess = { processEnds++ }, onScreen = { onScreen })

    private fun crash(error: Throwable, thread: Thread = Thread("game-worker")) =
        checkNotNull(Thread.getDefaultUncaughtExceptionHandler()).uncaughtException(thread, error)

    @Test
    fun `an uncaught error opens the crash page with its thread and stack trace - then ends the process, not through the platform's handler`() {
        val error = IllegalStateException("Boom")
        crash(error)

        val started = checkNotNull(shadowOf(application).nextStartedActivity)
        assertEquals(CrashActivity::class.java.name, started.component?.className)
        val report = CrashReport.from(started.getBundleExtra("net.zodac.dicefive.CRASH_REPORT"))
        assertTrue(report.app.startsWith("DiceFive "))
        assertEquals("game-worker", report.thread)
        assertEquals(error.stackTraceToString(), report.trace)
        assertEquals(1, processEnds)
        assertEquals(emptyList<Throwable>(), platformSaw)
    }

    @Test
    fun `only the first error opens the page - another ends the process with it`() {
        crash(IllegalStateException("First"))
        shadowOf(application).clearNextStartedActivities()
        crash(IllegalArgumentException("Second"))

        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(2, processEnds)
        assertEquals(emptyList<Throwable>(), platformSaw)
    }

    @Test
    fun `an error whose page can't be opened goes to the platform's handler`() {
        val noActivities = object : ContextWrapper(application) {
            override fun getApplicationContext(): Context = this

            override fun startActivity(intent: Intent?) = throw IllegalStateException("No activities")
        }
        install(noActivities)
        val error = IllegalStateException("Boom")
        crash(error)

        assertEquals(listOf<Throwable>(error), platformSaw)
        assertEquals(0, processEnds)
    }

    @Test
    fun `an error while the app isn't on screen opens no page and goes to the platform's handler`() {
        onScreen = false
        val error = IllegalStateException("Boom")
        crash(error)

        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(listOf<Throwable>(error), platformSaw)
        assertEquals(0, processEnds)
    }

    @Test
    fun `installed again it replaces itself - an error is handled once`() {
        install(application)
        crash(IllegalStateException("Boom"))

        assertEquals(1, processEnds)
        assertEquals(emptyList<Throwable>(), platformSaw)
    }

    @Test
    fun `tests run in the app's own process, not the crash page's`() {
        assertFalse(CrashHandler.isCrashProcess())
    }
}
