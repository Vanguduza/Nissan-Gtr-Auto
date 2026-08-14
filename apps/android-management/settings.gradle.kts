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

// Shared GTR Material3 theme (packages/ui brand-tokens.json) — colors/fonts only.
include(":android-ui")
project(":android-ui").projectDir =
    file("../../packages/android-ui")

// Bridge-First — Phase 2 POS/warehouse scan+print; included for classpath readiness.
include(":qr-scanner")
project(":qr-scanner").projectDir =
    file("../../bridges/android/qr-scanner")
include(":escpos-printer")
project(":escpos-printer").projectDir =
    file("../../bridges/android/escpos-printer")
