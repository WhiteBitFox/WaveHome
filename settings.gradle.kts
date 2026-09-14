pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        //For Notify library
        maven { url = uri("https://jitpack.io") }
        //For Home
        //maven { url = uri("https://maven.pkg.dev/google-home-sdk/maven") }

    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://jitpack.io")}
        // Google Home SDK repository
        //maven { url = uri("https://maven.pkg.dev/google-home-sdk/maven") }
    }
}

rootProject.name = "WaveHome"
include(":app")
