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
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SignalZero"

// Kotlin Multiplatform core. Pure domain logic lives here and compiles to
// both an Android library and a native iOS framework.
include(":shared")

// The existing Android app is left untouched under android/ while modules
// migrate one at a time. :androidApp will consume :shared and replace it —
// added here once it exists.
// include(":androidApp")
