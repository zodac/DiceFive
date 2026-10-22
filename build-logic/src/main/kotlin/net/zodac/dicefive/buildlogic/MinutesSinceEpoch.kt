package net.zodac.dicefive.buildlogic

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * The minute a build runs, read when it runs: a value worked out while a script is evaluated would be stored in the
 * configuration cache and handed to every later build unchanged. A `-PdevStamp` debug build's versionCode.
 */
abstract class MinutesSinceEpoch : ValueSource<Int, ValueSourceParameters.None> {
    override fun obtain(): Int = (System.currentTimeMillis() / 60_000L).toInt()
}
