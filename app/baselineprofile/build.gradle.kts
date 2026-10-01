import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

// Same Java version as the app - see the comment on javaVersion in app/android/build.gradle.kts.
val javaVersion: Int = Properties()
    .apply { rootProject.file("gradle/gradle-daemon-jvm.properties").inputStream().use(::load) }
    .getProperty("toolchainVersion").trim().toInt()

android {
    namespace = "net.zodac.dicefive.baselineprofile"
    // Same platform as the app (android-37.2) - see compileSdkMinor in app/android/build.gradle.kts.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 28 // Baseline Profile generation needs API 28+
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaVersion)
        targetCompatibility = JavaVersion.toVersion(javaVersion)
    }

    targetProjectPath = ":app:android"

    packaging {
        // The benchmark library's prebuilt .so files have no symbol table AGP can strip, so it warns
        // on every build and packages them as they are - the same as the app's own (see its build file).
        jniLibs {
            keepDebugSymbols += setOf("**/libbenchmarkNative.so", "**/libtracing_perfetto.so")
        }
    }
}

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion.toString())
    }
}

baselineProfile {
    // Generate on whatever device adb sees (a physical phone, or a userdebug emulator) - no managed
    // devices, since neither CI nor the sandbox has one. Run by hand: see .claude/DESIGN.md Phase 19.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
