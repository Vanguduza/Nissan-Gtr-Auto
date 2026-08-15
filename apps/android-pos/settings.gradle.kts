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

rootProject.name = "gtr-android-pos"
include(":app")
include(":pos-api")
include(":feature-till")
include(":feature-lookup")
include(":feature-pay")
include(":feature-orders")
include(":feature-customer")
include(":sync")

// Shared GTR Material3 theme (packages/ui brand-tokens.json)
include(":android-ui")
project(":android-ui").projectDir =
    file("../../packages/android-ui")

// Bridge-First hardware (CameraX QR + Bluetooth ESC/POS + cash-drawer kick)
include(":qr-scanner")
project(":qr-scanner").projectDir =
    file("../../bridges/android/qr-scanner")
include(":escpos-printer")
project(":escpos-printer").projectDir =
    file("../../bridges/android/escpos-printer")
