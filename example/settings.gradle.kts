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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "feedbackthread-android-example"
include(":app")

// Consumes the SDK from this checkout. The project's Gradle name
// (":feedbackthread") differs from its published artifactId
// ("feedbackthread-android"), so the substitution is spelled out explicitly
// instead of relying on Gradle's automatic group/name matching.
includeBuild("..") {
    dependencySubstitution {
        substitute(module("com.feedbackthread:feedbackthread-android")).using(project(":feedbackthread"))
    }
}
