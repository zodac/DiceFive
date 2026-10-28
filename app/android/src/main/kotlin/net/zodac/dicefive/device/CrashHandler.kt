package net.zodac.dicefive.device

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import kotlin.system.exitProcess
import net.zodac.dicefive.CrashActivity

/**
 * What an error nothing caught does: [CrashActivity] shows its [CrashReport], in a process of its own ([PROCESS_SUFFIX], the
 * manifest's), and the app's own process ends - logged, but not handed to the platform's own handler, which would put
 * Android's "has stopped" dialog over the page (on some phones at the first crash), and report it to Android and Play's
 * crash reports. An error with no page goes to the platform's handler, as before there was a page, so none goes
 * unshown and unreported: one whose page couldn't be opened, and one while the app isn't on screen, where Android
 * quietly refuses to open a page (and shows no dialog either).
 */
internal object CrashHandler {

    /** The crash page's process: the app's own name with this on the end. */
    const val PROCESS_SUFFIX = ":crash"

    private const val TAG = "DiceFive"

    /** The exit code the app's process ends with once the page is open. */
    private const val EXIT_CODE = 10

    /**
     * Installs the handler, in place of one installed before (a test's earlier Application), never on top of it.
     * [endProcess] ends the app's process once the page is open, and [onScreen] says whether the app is on screen: a
     * test's own, not to end its JVM.
     */
    fun install(context: Context, endProcess: () -> Unit = ::endThisProcess, onScreen: () -> Boolean = ::isOnScreen) {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        val platform = if (current is Handler) current.platform else current
        Thread.setDefaultUncaughtExceptionHandler(Handler(context.applicationContext, platform, endProcess, onScreen))
    }

    /** Whether the app has a page the player can see, from which Android lets it open another. */
    private fun isOnScreen(): Boolean =
        RunningAppProcessInfo().also(ActivityManager::getMyMemoryState).importance <= RunningAppProcessInfo.IMPORTANCE_VISIBLE

    private fun endThisProcess() {
        Process.killProcess(Process.myPid())
        exitProcess(EXIT_CODE)
    }

    private class Handler(
        private val appContext: Context,
        val platform: Thread.UncaughtExceptionHandler?,
        private val endProcess: () -> Unit,
        private val onScreen: () -> Boolean,
    ) : Thread.UncaughtExceptionHandler {
        private var tried = false
        private var opened = false

        // One error at a time: another thread's waits for the first's page to open, then ends with it.
        @Synchronized
        override fun uncaughtException(thread: Thread, error: Throwable) {
            if (!tried) {
                tried = true
                // Whatever goes wrong opening the page (out of memory, for one), the error still goes somewhere.
                opened = runCatching {
                    onScreen() && run { appContext.startActivity(CrashActivity.intent(appContext, CrashReport.of(appContext, thread, error))); true }
                }.getOrDefault(false)
            }
            // No platform handler only on a plain JVM (a Robolectric test): print it, as the JVM itself would.
            when {
                platform == null -> error.printStackTrace()
                opened -> {
                    Log.e(TAG, "FATAL EXCEPTION: ${thread.name}, shown on the crash page", error)
                    endProcess()
                }
                else -> platform.uncaughtException(thread, error)
            }
        }
    }

    /** Whether this is the crash page's process, where none of the rest of the app is set up. */
    fun isCrashProcess(): Boolean = processName().endsWith(PROCESS_SUFFIX)

    private fun processName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().substringBefore('\u0000') }.getOrDefault("")
        }
}
