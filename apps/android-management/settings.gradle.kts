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
