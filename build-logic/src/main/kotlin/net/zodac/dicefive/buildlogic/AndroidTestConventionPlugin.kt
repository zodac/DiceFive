package net.zodac.dicefive.buildlogic

import com.android.build.api.dsl.TestExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * An Android test module (`com.android.test`, the Baseline Profile journey): the app's platform and Java version, and the
 * strict compiler and lint rules. Its minSdk is the app's; a module that needs a higher one sets it itself.
 */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.test")
        val sdk = androidSdk
        val java = javaVersion
        extensions.configure<TestExtension> {
            compileSdk = sdk.compileSdk
            compileSdkMinor = sdk.compileSdkMinor
            buildToolsVersion = sdk.buildToolsVersion
            defaultConfig {
                minSdk = sdk.minSdk
                targetSdk = sdk.targetSdk
            }
            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(java)
                targetCompatibility = JavaVersion.toVersion(java)
            }
            lint {
                strict()
            }
        }
        configureAndroidKotlin(java)
    }
}
