package net.zodac.dicefive.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The shared code's Kotlin Multiplatform library, with Android (AGP's `com.android.kotlin.multiplatform.library`) its
 * one target: the app's SDK levels, host tests on the JVM, and the strict lint rules. `com.android.lint` is applied too:
 * without it the KMP library plugin creates no lint tasks for the main code at all.
 *
 * With one target, the compiler lets `commonMain` see Android's APIs; `CommonCodeIsPlatformNeutralTest` keeps them out,
 * so another target can be added back without moving code (MULTIPLATFORM.md).
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
        // com.android.lint's own settings, which its lint of the common code (lintJvm, a target or not) reads; the
        // Android target's lint reads the android block's below. The same rules for both.
        extensions.configure<Lint> {
            strict(target)
        }
        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(java)

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
                // commonTest runs as Android host tests: on the JVM, under Robolectric for the UI tests, with the
                // module's resources (Compose resources ship as Android assets).
                withHostTest {
                    isIncludeAndroidResources = true
                }
                lint {
                    strict(target)
                }
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
