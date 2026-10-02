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
include(":core:visual")
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
include(":feature:address")

// Shared GTR Material3 theme (packages/ui brand-tokens.json)
include(":android-ui")
project(":android-ui").projectDir =
    file("../../packages/android-ui")

// Bridge-First — review photo camera (consume only; impl under bridges/)
include(":pod-camera")
project(":pod-camera").projectDir =
    file("../../bridges/android/pod-camera")

// Bridge-First — MapLibre address pick / display (maps-nav)
include(":maps-nav")
project(":maps-nav").projectDir =
    file("../../bridges/android/maps-nav")
