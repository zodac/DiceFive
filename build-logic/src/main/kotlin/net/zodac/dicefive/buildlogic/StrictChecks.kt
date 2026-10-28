package net.zodac.dicefive.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions

/**
 * Lint's rules for every module: code-quality-config/android/lint.xml, where each is explained. Any warning fails lint -
 * and so CI, which runs it - and deprecated and obsolete usages are errors outright. A module's own lint.xml (per-path
 * exceptions) applies before it.
 */
internal fun Lint.strict(project: Project) {
    lintConfig = project.rootProject.file("code-quality-config/android/lint.xml")
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
