package net.zodac.dicefive.buildlogic

import com.android.build.api.dsl.Lint
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions

/**
 * Lint's rules for every module. Deprecated and obsolete usages fail lint - and so CI, which runs it - rather than
 * piling up as warnings, as does any other warning: fix it, or suppress it where it occurs with a comment saying why.
 * The exceptions: "a newer version of a library exists" (GradleDependency, and NewerVersionAvailable, which the JVM
 * target's lint runs) and "of Gradle or AGP" (AndroidGradlePluginVersion) go off the day a release is published, failing
 * CI with no change on our side; update-dependencies.yml is what tracks new versions.
 */
internal fun Lint.strict() {
    error += setOf("Deprecated", "ObsoleteSdkInt")
    warningsAsErrors = true
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
}

/**
 * Any compiler warning fails the build (main and test code alike) rather than piling up unread - a deprecated API, an
 * unnecessary `!!`, a missing `@OptIn`. A dependency bump that deprecates something the app uses therefore breaks the
 * build until the call is migrated (the update script holds that bump back). gradle.properties'
 * org.gradle.kotlin.dsl.allWarningsAsErrors does the same for the build scripts.
 */
internal fun KotlinCommonCompilerOptions.strict() {
    allWarningsAsErrors.set(true)
}
