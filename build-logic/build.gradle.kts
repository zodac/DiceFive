plugins {
    `kotlin-dsl`
}

// The Gradle plugins the conventions configure. compileOnly: each module applies them through the root build's own
// plugin declarations, so the versions come from the catalog once, and this code runs against those same classes.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

// As everywhere else - any compiler warning fails the build.
kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "dicefive.android.application"
            implementationClass = "net.zodac.dicefive.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidTest") {
            id = "dicefive.android.test"
            implementationClass = "net.zodac.dicefive.buildlogic.AndroidTestConventionPlugin"
        }
        register("kotlinMultiplatform") {
            id = "dicefive.kotlin.multiplatform"
            implementationClass = "net.zodac.dicefive.buildlogic.KotlinMultiplatformConventionPlugin"
        }
    }
}
