package net.zodac.dicefive

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The shared code's common source sets reach no platform API directly. Android's are caught by the compiler, as
 * `commonMain` and `commonTest` are compiled for the JVM target too, but the JDK's aren't: both targets run on a JVM,
 * so Kotlin lets common code see it, and a `java.*` import or a `System` call there would only fail once a target
 * without a JVM is added. A platform call goes behind an `expect` or `PlatformServices` instead (MULTIPLATFORM.md).
 */
class CommonCodeIsPlatformNeutralTest {

    private val commonSources = listOf(File("src/commonMain"), File("src/commonTest"))

    @Test
    fun `no common code calls the JDK or Android directly`() {
        commonSources.forEach { assertTrue(it.isDirectory, "Run from app/shared: ${it.absolutePath}") }
        val offenders = commonSources.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        val code = line.substringBefore("//")
                        if (PLATFORM_API.containsMatchIn(code)) "${file.path}:${index + 1}: ${line.trim()}" else null
                    }
                }
        }

        assertTrue(offenders.isEmpty(), "Platform APIs in common code - put them behind an expect:\n${offenders.joinToString("\n")}")
    }

    private companion object {
        /** An import from the JDK or Android, a JDK class named in full or by its usual entry points, or a JVM-only idiom. */
        val PLATFORM_API = Regex(
            listOf(
                """^import (java|javax|android|sun|org\.w3c|org\.xml|org\.junit)\.""",
                """(?<![\w.])java\.(lang|util|io|text|time|net|nio)\.""",
                """(?<![\w.])(System|Thread|Runtime)\.""",
                """String\.format\(""",
                """@Synchronized|(?<![\w.])synchronized\(""",
                """::class\.java\b""",
            ).joinToString("|"),
        )
    }
}
