plugins {
    // The app's platform and Java version, and the strict compiler and lint rules: build-logic's
    // AndroidTestConventionPlugin.
    id("dicefive.android.test")
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "net.zodac.dicefive.baselineprofile"
    defaultConfig {
        minSdk = 28 // Baseline Profile generation needs API 28+
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The journey runs the play flavour (Journeys.PACKAGE); the release workflow gives its profile to the github
        // APK too, whose code is the same less Play Games.
        missingDimensionStrategy("distribution", "play")
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

baselineProfile {
    // Generate on whatever device adb sees (a physical phone, or a userdebug emulator) - no managed
    // devices: the release workflow and the sandbox each boot an emulator of their own
    // (.github/scripts/ci_emulator.sh, sandbox/emulator.sh). See .claude/DESIGN.md Phase 19.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
