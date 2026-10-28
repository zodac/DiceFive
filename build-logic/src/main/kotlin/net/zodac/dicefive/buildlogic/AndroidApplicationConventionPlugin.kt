package net.zodac.dicefive.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * An Android app (`com.android.application`, with AGP's built-in Kotlin): the SDK levels and tools
 * (gradle/android-sdk.properties), the Java version for Java, Kotlin and the unit tests, and the strict compiler and
 * lint rules. The module itself adds what only it has - its id, flavours, signing and packaging.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.application")
        val sdk = androidSdk
        val java = javaVersion
        extensions.configure<ApplicationExtension> {
            compileSdk = sdk.compileSdk
            compileSdkMinor = sdk.compileSdkMinor
            buildToolsVersion = sdk.buildToolsVersion
            ndkVersion = sdk.ndkVersion
            defaultConfig {
                minSdk = sdk.minSdk
                targetSdk = sdk.targetSdk
            }
            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(java)
                targetCompatibility = JavaVersion.toVersion(java)
            }
            lint {
                strict(target)
            }
        }
        configureAndroidKotlin(java)
    }
}

/**
 * The toolchain (the JDK that compiles Kotlin and Java and runs the unit tests) and Kotlin's counterpart of
 * compileOptions, on [java], for a module built by AGP's built-in Kotlin. (compilerOptions replaces
 * android.kotlinOptions, which Kotlin 2.3+ rejects outright as deprecated.)
 */
internal fun Project.configureAndroidKotlin(java: Int) {
    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(java)
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(java.toString()))
            strict()
        }
    }
}
