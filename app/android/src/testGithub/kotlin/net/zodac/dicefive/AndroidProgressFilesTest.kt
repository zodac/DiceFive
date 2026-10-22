package net.zodac.dicefive

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.device.AndroidProgressFiles
import net.zodac.dicefive.platform.ProgressFileResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** [AndroidProgressFiles]' pickers, answered by hand. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidProgressFilesTest {

    @Test
    fun `a second picker while one is open fails - and the first still gets its answer`() = runTest {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val files = AndroidProgressFiles.attach(controller.get())
        controller.start().resume()
        val activity = shadowOf(controller.get())

        val first = async { files.save("progress", byteArrayOf(1)) }
        runCurrent()
        assertEquals(ProgressFileResult.Failed, files.open())
        assertFalse(first.isCompleted)

        activity.receiveResult(activity.nextStartedActivityForResult.intent, Activity.RESULT_CANCELED, null)
        assertEquals(ProgressFileResult.Cancelled, first.await())

        // Once it's answered, the next picker opens.
        val next = async { files.open() }
        runCurrent()
        assertFalse(next.isCompleted)
        activity.receiveResult(activity.nextStartedActivityForResult.intent, Activity.RESULT_CANCELED, null)
        assertEquals(ProgressFileResult.Cancelled, next.await())
    }
}
