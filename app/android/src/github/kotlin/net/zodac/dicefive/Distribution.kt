package net.zodac.dicefive

import android.app.Application
import androidx.activity.ComponentActivity
import net.zodac.dicefive.device.AndroidProgressFiles
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.NoGameServices
import net.zodac.dicefive.platform.ProgressFiles

/** The github flavour's own part of the app, for the APK on GitHub releases: progress files, and no online service. */
internal object Distribution {

    fun initialize(application: Application) = Unit

    fun gameServices(activity: ComponentActivity): GameServices = NoGameServices

    fun progressFiles(activity: ComponentActivity): ProgressFiles = AndroidProgressFiles.attach(activity)
}
