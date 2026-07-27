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

rootProject.name = "gtr-android-customer"
include(":app")
include(":core:rpc")
include(":feature:auth")
include(":feature:cart")
include(":feature:orders")
include(":feature:garage")
include(":feature:pay")
include(":feature:chat")
include(":feature:track")
include(":feature:wishlist")
include(":feature:compare")
include(":feature:reviews")
include(":feature:catalog")

// Bridge-First — review photo camera (consume only; impl under bridges/)
include(":pod-camera")
project(":pod-camera").projectDir =
    file("../../bridges/android/pod-camera")
