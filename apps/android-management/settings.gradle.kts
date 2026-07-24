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

rootProject.name = "gtr-android-management"
include(":app")
include(":core:rpc")
include(":feature:auth")
include(":feature:pos")
include(":feature:warehouse")
include(":feature:dispatch")
include(":feature:hr")

// Bridge-First GPS — consume only; impl lives under bridges/
include(":location-tracker")
project(":location-tracker").projectDir =
    file("../../bridges/android/location-tracker")
