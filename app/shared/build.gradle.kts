// The game's platform-neutral code, as a Kotlin Multiplatform library (consumed by :app:android, which builds the APK).
// Platform code lives in androidMain and jvmMain, behind the expect/actual and PlatformServices seams. commonMain is
// compiled for both, so an Android API there fails to compile; CommonCodeIsPlatformNeutralTest catches a JDK one, which
// both targets have. See .claude/MULTIPLATFORM.md.
plugins {
    // The Android and JVM targets, SDK levels, Java version, and the strict compiler and lint rules: build-logic's
    // KotlinMultiplatformConventionPlugin. (AGP 9 won't combine Kotlin Multiplatform with com.android.application,
    // which is why the APK is built by the separate :app:android module.)
    id("dicefive.kotlin.multiplatform")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    android {
        namespace = "net.zodac.dicefive.shared"
        // Compose Multiplatform resources (composeResources/) ship inside the AAR as Android assets.
        androidResources { enable = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.datastore.preferences.core)
            // Gzip for the cloud save (online/CloudSaveCodec). Already in the APK as DataStore's file layer.
            implementation(libs.okio)
            implementation(libs.aboutlibraries.core)

            // The UI. `api`, not `implementation`, for the handful :app:android's own Compose code (MainActivity
            // and the Android licence view) builds on too.
            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.foundation)
            api(libs.jetbrains.compose.ui)
            api(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            // api: the style catalogues and achievement banners put StringResources in their public types.
            api(libs.jetbrains.compose.components.resources)
            // Just for Icons.AutoMirrored.Filled.Undo and friends - material3 alone only ships the
            // small default icon set.
            implementation(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.jetbrains.navigationevent.compose)
        }
        jvmMain.dependencies {
            // The JVM's number, list and ordinal formatting (ui/common/Formatting.jvm.kt): the ICU Android's own
            // android.icu repackages, so both platforms format alike.
            implementation(libs.icu4j)
        }
        androidMain.dependencies {
            // JetBrains' navigation-compose 2.9 resolves to androidx 2.9 on Android; this keeps the
            // app on the androidx line it already shipped with (the 2.9 API the shared code compiles
            // against is a subset of it).
            implementation(libs.androidx.navigation.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // The shared UI's tests: runComposeUiTest, on both targets.
            implementation(libs.jetbrains.compose.ui.test)
        }
        getByName("androidHostTest").dependencies {
            // Android's org.json is a stub on the JVM (every method throws), and AboutLibraries'
            // Android parser - behind parseLicenseReport - uses it.
            implementation(libs.org.json)
            // commonTest's UI tests run under Robolectric here (see PlatformUiTest.android.kt): a ComponentActivity to host
            // them, from the test manifest.
            implementation(libs.robolectric)
            implementation(libs.androidx.junit)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.ui.test.manifest)
        }
    }
}

// Compose's multiplatform test API (ComposeUiTest, runComposeUiTest) is all still marked experimental; every UI test
// uses it.
kotlin.sourceSets.matching { it.name.endsWith("Test") }.configureEach {
    languageSettings.optIn("androidx.compose.ui.test.ExperimentalTestApi")
}

compose.resources {
    packageOfResClass = "net.zodac.dicefive.resources"
    publicResClass = false
}

// `-PregeneratePerfectPlayTable` makes StandardPerfectPlayTableTest rewrite the bundled perfect-play
// table from Standard's rules instead of checking the bundled copy still matches them - for when
// those rules change. See StandardPerfectPlayTable and .claude/DESIGN.md's Phase 23.
tasks.withType<Test>().configureEach {
    systemProperty("dicefive.regeneratePerfectPlayTable", providers.gradleProperty("regeneratePerfectPlayTable").isPresent)
}

room {
    // Every schema version is exported here and committed, so a future migration can be tested
    // against the real shape of the version it migrates from.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Room's compiler runs once per target, generating each one's AppDatabaseConstructor.
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

// CI, the dependency-update script and day-to-day habit all run `./gradlew testDebugUnitTest` (the
// name :app:android's unit tests have). A KMP library's tests have other names, so this answers to
// that one too, rather than every one of those callers having to learn the new task names.
// The tests run as Android runs them. They are compiled for the JVM target too (so commonTest stays
// common), but run there (jvmTest) only monthly, in update-dependencies.yml: no desktop app ships yet.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the shared module's tests as Android runs them, and compiles them for the JVM."
    dependsOn("testAndroidHostTest", "compileTestKotlinJvm")
}
