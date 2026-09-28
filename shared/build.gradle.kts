import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The game's platform-neutral code, as a Kotlin Multiplatform library: everything in commonMain is
// compiled once per target - Android (consumed by :app, which builds the APK) and iOS. Nothing in
// commonMain may touch Android or the JVM; the iOS targets are what enforce that, since they have
// neither. See .claude/IOS_SUPPORT.md.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // AGP 9's Android target for a KMP library - the plain com.android.library plugin can't be
    // combined with Kotlin Multiplatform any more (nor can com.android.application, which is why the
    // APK is built by the separate :app module).
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

// The same Java version :app compiles for - see the comment on javaVersion in app/build.gradle.kts.
val javaVersion: Int = Properties()
    .apply { rootProject.file("gradle/gradle-daemon-jvm.properties").inputStream().use(::load) }
    .getProperty("toolchainVersion").trim().toInt()

kotlin {
    jvmToolchain(javaVersion)

    android {
        namespace = "net.zodac.dicefive.shared"
        // In step with :app's compileSdk/minSdk (app/build.gradle.kts has the reasoning for each).
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(javaVersion.toString())
        }
        // commonTest runs on the JVM as Android host tests (no device needed).
        withHostTest {}
    }

    // Compiled on every host, so a JVM-only API slipping into commonMain fails the build here, not
    // months later on a Mac. Linking and running these needs macOS - see gradle.properties.
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        // As in :app - using a deprecated API fails the build rather than piling up as warnings.
        freeCompilerArgs.add("-Xwarning-level=DEPRECATION:error")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// CI, the dependency-update script and day-to-day habit all run `./gradlew testDebugUnitTest` (the
// name :app's unit tests have). A KMP library's JVM tests have another name, so this answers to
// that one too - and also compiles both iOS targets, the check that commonMain stays
// platform-neutral - rather than every one of those callers having to learn the new task names.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the shared module's tests on the JVM and compiles its iOS targets."
    dependsOn("testAndroidHostTest", "compileKotlinIosArm64", "compileKotlinIosSimulatorArm64", "compileTestKotlinIosSimulatorArm64")
}
