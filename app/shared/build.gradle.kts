import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The game's platform-neutral code, as a Kotlin Multiplatform library: everything in commonMain is
// compiled once per target - Android (consumed by :app:android, which builds the APK) and iOS. Nothing in
// commonMain may touch Android or the JVM; the iOS targets are what enforce that, since they have
// neither. See .claude/IOS_SUPPORT.md.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // AGP 9's Android target for a KMP library - the plain com.android.library plugin can't be
    // combined with Kotlin Multiplatform any more (nor can com.android.application, which is why the
    // APK is built by the separate :app:android module).
    alias(libs.plugins.android.kotlin.multiplatform.library)
    // Without this the KMP library plugin creates no lint tasks for the main code at all - and this
    // module is most of the app. See the lint block below.
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// The same Java version :app:android compiles for - see the comment on javaVersion in app/android/build.gradle.kts.
val javaVersion: Int = Properties()
    .apply { rootProject.file("gradle/gradle-daemon-jvm.properties").inputStream().use(::load) }
    .getProperty("toolchainVersion").trim().toInt()

// compileSdk (with its minor) and minSdk have one home, app/android/build.gradle.kts - it's where the
// reasoning for each lives, and the file the dependency-update script and CI's SDK-package script read
// and bump. This reads them from there, so the two modules can never compile against different SDKs.
val androidAppBuildScript = rootProject.file("app/android/build.gradle.kts").readText()
fun androidAppSetting(name: String, default: Int? = null): Int =
    Regex("""^\s*$name\s*=\s*(\d+)""", RegexOption.MULTILINE).find(androidAppBuildScript)?.groupValues?.get(1)?.toInt()
        ?: default
        ?: throw GradleException("app/android/build.gradle.kts has no `$name = <number>` for :app:shared to share")

kotlin {
    jvmToolchain(javaVersion)

    android {
        namespace = "net.zodac.dicefive.shared"
        compileSdk {
            version = release(androidAppSetting("compileSdk")) {
                minorApiLevel = androidAppSetting("compileSdkMinor", default = 0)
            }
        }
        minSdk = androidAppSetting("minSdk")
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(javaVersion.toString())
        }
        // commonTest runs on the JVM as Android host tests (no device needed).
        withHostTest {}
        // Compose Multiplatform resources (composeResources/) ship inside the AAR as Android assets.
        androidResources { enable = true }
        // The same rules as :app:android's lint - deprecated and obsolete usages are errors.
        lint {
            error += setOf("Deprecated", "ObsoleteSdkInt")
            warningsAsErrors = true
            disable += "GradleDependency"
        }
    }

    // Compiled on every host, so a JVM-only API slipping into commonMain fails the build here, not
    // months later on a Mac. Linking and running these needs macOS - see gradle.properties.
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        // As in :app:android - any compiler warning fails the build rather than piling up unread.
        allWarningsAsErrors = true
        // expect/actual classes are still flagged Beta; Room's multiplatform setup needs one (the
        // AppDatabaseConstructor its compiler generates), so the warning is noise here.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.aboutlibraries.core)

            // The UI. `api`, not `implementation`, for the handful :app:android's own Compose code (MainActivity
            // and the Android licence view) builds on too.
            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.foundation)
            api(libs.jetbrains.compose.ui)
            api(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(libs.jetbrains.compose.components.resources)
            // Just for Icons.AutoMirrored.Filled.Undo and friends - material3 alone only ships the
            // small default icon set.
            implementation(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.jetbrains.navigationevent.compose)
        }
        androidMain.dependencies {
            // JetBrains' navigation-compose 2.9 resolves to androidx 2.9 on Android; this keeps the
            // app on the androidx line it already shipped with (the 2.9 API the shared code compiles
            // against is a subset of it).
            implementation(libs.androidx.navigation.compose)
        }
        iosMain.dependencies {
            // Android opens the database on the framework's own SQLite; iOS has none to offer.
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            // Android's org.json is a stub on the JVM (every method throws), and AboutLibraries'
            // Android parser - behind parseLicenseReport - uses it.
            implementation(libs.org.json)
        }
    }
}

compose.resources {
    packageOfResClass = "net.zodac.dicefive.resources"
    publicResClass = false
}

// `-PregeneratePerfectPlayTable` makes StandardPerfectPlayTableTest rewrite the bundled perfect-play
// table from Standard's rules instead of checking the bundled copy still matches them - for when
// those rules change. See StandardPerfectPlayTable and .claude/DESIGN.md's Phase 23.
// `-PregenerateI18nBaseline` does the same for NoNewLiteralsTest's count of string literals left in
// ui/ (src/androidHostTest/i18n-literal-baseline.txt). See .claude/I18N.md.
tasks.withType<Test>().configureEach {
    systemProperty("dicefive.regeneratePerfectPlayTable", providers.gradleProperty("regeneratePerfectPlayTable").isPresent)
    systemProperty("dicefive.regenerateI18nBaseline", providers.gradleProperty("regenerateI18nBaseline").isPresent)
}

room {
    // Every schema version is exported here and committed, so a future migration can be tested
    // against the real shape of the version it migrates from.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Room's compiler runs once per target, generating each one's AppDatabaseConstructor.
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.androidx.room.compiler) }
}

// CI, the dependency-update script and day-to-day habit all run `./gradlew testDebugUnitTest` (the
// name :app:android's unit tests have). A KMP library's JVM tests have another name, so this answers to
// that one too - and also compiles both iOS targets, the check that commonMain stays
// platform-neutral - rather than every one of those callers having to learn the new task names.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the shared module's tests on the JVM and compiles its iOS targets."
    dependsOn("testAndroidHostTest", "compileKotlinIosArm64", "compileKotlinIosSimulatorArm64", "compileTestKotlinIosSimulatorArm64")
}
