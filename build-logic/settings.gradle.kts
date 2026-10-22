// The build's own code: the convention plugins every module applies (Java and SDK levels, strict compiler and lint
// settings, the multiplatform targets) and the custom tasks (audio normalising, the licence checks). An included build,
// so it is compiled before the app's build scripts and shared by all of them. See .claude/MULTIPLATFORM.md.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
