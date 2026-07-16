pluginManagement {
    repositories {
        google()
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

rootProject.name = "CanastaSolitaire"

include(
    ":app",
    ":core:cards",
    ":core:canasta",
    ":core:solitaire",
    ":core:protocol",
)
