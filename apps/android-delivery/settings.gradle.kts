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

rootProject.name = "gtr-android-delivery"
include(":app")
include(":core:rpc")
include(":core:design")
include(":feature:auth")
include(":feature:jobs")
include(":feature:tracking")
include(":feature:pod")

// Driver visual system (Slopes-style, light + dark) lives in :core:design.

// Bridge-First — consume only; impl lives under bridges/
include(":location-tracker")
project(":location-tracker").projectDir =
    file("../../bridges/android/location-tracker")
include(":pod-camera")
project(":pod-camera").projectDir =
    file("../../bridges/android/pod-camera")
include(":pod-signature")
project(":pod-signature").projectDir =
    file("../../bridges/android/pod-signature")
include(":maps-nav")
project(":maps-nav").projectDir =
    file("../../bridges/android/maps-nav")
include(":card-terminal")
project(":card-terminal").projectDir =
    file("../../bridges/android/card-terminal")
