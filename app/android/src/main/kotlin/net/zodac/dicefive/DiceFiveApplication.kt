package net.zodac.dicefive

import android.app.Application
import android.content.ComponentCallbacks2
import net.zodac.dicefive.device.CrashHandler
import net.zodac.dicefive.ui.style.releaseCachedSurfaces

class DiceFiveApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The crash page's process shows an error and nothing else: none of the app is set up there, only the page's words.
        if (CrashHandler.isCrashProcess()) {
            CrashActivity.provideComposeResourcesContext(this)
            return
        }
        CrashHandler.install(this)
        Distribution.initialize(this)
    }

    // Short of memory in the background, the painted surfaces go first: they're painted again on the next draw. Not on
    // UI_HIDDEN alone, or every return to the app would repaint them.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) releaseCachedSurfaces()
    }
}
