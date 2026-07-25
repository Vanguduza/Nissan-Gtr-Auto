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

rootProject.name = "gtr-android-bridges"
include(":location-tracker")
project(":location-tracker").projectDir = file("location-tracker")
include(":qr-scanner")
project(":qr-scanner").projectDir = file("qr-scanner")
include(":escpos-printer")
project(":escpos-printer").projectDir = file("escpos-printer")
include(":pod-camera")
project(":pod-camera").projectDir = file("pod-camera")
include(":pod-signature")
project(":pod-signature").projectDir = file("pod-signature")
