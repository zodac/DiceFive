package net.zodac.dicefive.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The shared code's Kotlin Multiplatform library, with its two targets:
 * - Android (AGP's `com.android.kotlin.multiplatform.library`): the app's SDK levels, host tests on the JVM, and the
 *   strict lint rules. `com.android.lint` is applied too: without it the KMP library plugin creates no lint tasks for
 *   the main code at all.
 * - JVM, the desktop platform. No app is built for it yet; it is here so `commonMain` is compiled for a platform that
 *   isn't Android on every build, where an Android API fails to compile. `commonTest` is compiled for it too, and run
 *   on it monthly (update-dependencies.yml).
 *
 * The module sets its own namespace, dependencies and anything else only it needs.
 */
class KotlinMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        pluginManager.apply("com.android.lint")
        val sdk = androidSdk
        val java = javaVersion
        // com.android.lint's own settings, which its lint of the JVM target (lintJvm) reads; the Android target's lint
        // reads the android block's below. The same rules for both.
        extensions.configure<Lint> {
            strict(target)
        }
        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(java)

            jvm {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(java.toString()))
                }
            }

            extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                compileSdk {
                    version = release(sdk.compileSdk) {
                        minorApiLevel = sdk.compileSdkMinor
                    }
                }
                minSdk = sdk.minSdk
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(java.toString()))
                }
                // commonTest runs as Android host tests too: on the JVM, under Robolectric for the UI tests, with the
                // module's resources (Compose resources ship as Android assets).
                withHostTest {
                    isIncludeAndroidResources = true
                }
                lint {
                    strict(target)
                }
            }

            // commonTest's UI tests draw with Compose's desktop runtime on the JVM target: the build for the machine the
            // tests run on.
            sourceSets.getByName("jvmTest").dependencies {
                implementation(composeDesktopRuntime())
            }

            compilerOptions {
                strict()
                // expect/actual classes are still flagged Beta; Room's multiplatform setup needs one (the
                // AppDatabaseConstructor its compiler generates), so the warning is noise here.
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }
}

/** Compose's desktop runtime for this machine: the catalog's `jetbrains-compose-desktop-jvm`, as desktop-jvm-<os>-<arch>. */
private fun Project.composeDesktopRuntime(): String {
    val library = extensions.getByType<VersionCatalogsExtension>().named("libs")
        .findLibrary("jetbrains-compose-desktop-jvm").get().get()
    val osName = System.getProperty("os.name").lowercase()
    val os = when {
        osName.startsWith("mac") -> "macos"
        osName.startsWith("windows") -> "windows"
        else -> "linux"
    }
    val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"
    return "${library.module.group}:${library.module.name}-$os-$arch:${library.versionConstraint.requiredVersion}"
}
