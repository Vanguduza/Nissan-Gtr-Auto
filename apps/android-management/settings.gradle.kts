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

// Bridge-First — consume only; impl lives under bridges/
include(":location-tracker")
project(":location-tracker").projectDir =
    file("../../bridges/android/location-tracker")
include(":qr-scanner")
project(":qr-scanner").projectDir =
    file("../../bridges/android/qr-scanner")
include(":escpos-printer")
project(":escpos-printer").projectDir =
    file("../../bridges/android/escpos-printer")
