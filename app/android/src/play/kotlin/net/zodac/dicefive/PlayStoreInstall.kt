package net.zodac.dicefive

import android.content.Context
import android.os.Build

/** The Google Play Store app's package: an app it installed names it as its installer. */
internal const val PLAY_STORE_PACKAGE = "com.android.vending"

/** The package that installed this app (the Play Store, a file manager...), or null if Android doesn't say. */
internal fun installerPackage(context: Context): String? = runCatching {
    val packageManager = context.packageManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    } else {
        // The only way to ask before Android 11, which deprecated it for getInstallSourceInfo above.
        @Suppress("DEPRECATION")
        packageManager.getInstallerPackageName(context.packageName)
    }
}.getOrNull()
