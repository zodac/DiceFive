package net.zodac.dicefive.buildlogic

import java.util.Properties
import org.gradle.api.Project

private fun Project.rootProperties(path: String): Properties =
    Properties().apply { rootProject.file(path).inputStream().use(::load) }

private fun Properties.required(path: String, name: String): String =
    getProperty(name)?.trim()?.takeIf { it.isNotEmpty() } ?: error("$path has no `$name=`")

/**
 * The Java version everything builds with: `toolchainVersion` in gradle/gradle-daemon-jvm.properties (written by
 * `./gradlew updateDaemonJvm --jvm-version=N --jvm-vendor=ADOPTIUM`). Gradle runs itself on that JDK, compiles and
 * tests with it, and the code is compiled for it - and it downloads that JDK wherever it is missing (the foojay
 * resolver in settings.gradle.kts), so this sandbox, CI and Android Studio all get the same one. The sandbox's JDK
 * follows it as a head start (the update script's consistency check fails its run if they drift), and CI's setup-java
 * reads it from that file at run time.
 */
val Project.javaVersion: Int
    get() {
        val path = "gradle/gradle-daemon-jvm.properties"
        return rootProperties(path).required(path, "toolchainVersion").toInt()
    }

/** The Android SDK levels and tools, from gradle/android-sdk.properties - where each is explained. */
class AndroidSdk(
    val compileSdk: Int,
    val compileSdkMinor: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val buildToolsVersion: String,
    val ndkVersion: String,
)

val Project.androidSdk: AndroidSdk
    get() {
        val path = "gradle/android-sdk.properties"
        val properties = rootProperties(path)
        fun value(name: String) = properties.required(path, name)
        return AndroidSdk(
            compileSdk = value("compileSdk").toInt(),
            compileSdkMinor = value("compileSdkMinor").toInt(),
            minSdk = value("minSdk").toInt(),
            targetSdk = value("targetSdk").toInt(),
            buildToolsVersion = value("buildToolsVersion"),
            ndkVersion = value("ndkVersion"),
        )
    }

/**
 * The app's version, from the repo root's VERSION file - bump that to release a new one. [code] packs it as
 * MMmmpp: two digits each for minor and patch, so a part of 100 or more would collide with the next minor or major.
 */
class AppVersion(val name: String) {
    val code: Int = name.split(".").map { it.toInt() }.let { (major, minor, patch) ->
        require(minor < 100 && patch < 100) { "VERSION $name: minor and patch must each be below 100" }
        major * 10_000 + minor * 100 + patch
    }
}

val Project.appVersion: AppVersion
    get() = AppVersion(rootProject.file("VERSION").readText().trim())
