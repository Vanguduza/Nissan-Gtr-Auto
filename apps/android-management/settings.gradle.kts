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
include(":feature:chat")
include(":feature:procurement")
include(":feature:credit")
include(":feature:fleet")

// Bridge-First — consume only; impl lives under bridges/
// location-tracker intentionally NOT included — driver GPS producer is
// apps/android-delivery only. Management is staff view/subscribe.
include(":qr-scanner")
project(":qr-scanner").projectDir =
    file("../../bridges/android/qr-scanner")
include(":escpos-printer")
project(":escpos-printer").projectDir =
    file("../../bridges/android/escpos-printer")
