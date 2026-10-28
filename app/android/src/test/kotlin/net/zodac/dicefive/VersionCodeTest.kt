package net.zodac.dicefive

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The versionCode build-logic's `AppVersion` packs from the repo root's VERSION file. */
class VersionCodeTest {

    private val version = File("../../VERSION").readText().trim()

    @Test
    fun `the versionCode packs VERSION as three digits each for minor and patch`() {
        // A -PdevStamp build takes the minute it was built instead (its name is <version>-dev<minute>).
        assumeTrue(BuildConfig.VERSION_NAME == "$version-dev")
        val (major, minor, patch) = version.split(".").map(String::toInt)

        assertEquals(major * 1_000_000 + minor * 1_000 + patch, BuildConfig.VERSION_CODE)
    }

    @Test
    fun `the versionCode is above every code the two-digit packing gave`() {
        assumeTrue(BuildConfig.VERSION_NAME == "$version-dev")

        // 1.2.1, the last version released with major*10000 + minor*100 + patch.
        assertTrue(BuildConfig.VERSION_CODE > 10_201)
    }
}
