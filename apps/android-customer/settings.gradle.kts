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
