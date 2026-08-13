import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun localProp(name: String): String {
    val local = Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { local.load(it) }
    }
    return local.getProperty(name)
        ?: (project.findProperty(name) as? String)
        ?: ""
}

android {
    namespace = "co.zw.nissangtr.management"
    compileSdk = 34

    defaultConfig {
        // Flavors override applicationId (L1: tablet ≠ phone).
        applicationId = "co.zw.nissangtr.management"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-scaffold"
        // Placeholders — set via local.properties / CI; never commit real keys.
        buildConfigField("String", "SUPABASE_URL", "\"${localProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("SUPABASE_ANON_KEY")}\"")
        buildConfigField(
            "boolean",
            "RPC_FORCE_FAKE",
            localProp("rpc.forceFake").equals("true", ignoreCase = true).toString(),
        )
        buildConfigField(
            "String",
            "DELIVERY_SUPPORT_PHONE",
            "\"${localProp("DELIVERY_SUPPORT_PHONE")}\"",
        )
        // PowerSync (H7) — names from powersync/.env.example; never commit real values.
        buildConfigField("String", "POWERSYNC_URL", "\"${localProp("POWERSYNC_URL")}\"")
        buildConfigField(
            "String",
            "POWERSYNC_PUBLIC_KEY",
            "\"${localProp("POWERSYNC_PUBLIC_KEY")}\"",
        )
        buildConfigField(
            "String",
            "POWERSYNC_PROJECT_ID",
            "\"${localProp("POWERSYNC_PROJECT_ID")}\"",
        )
    }

    flavorDimensions += "formFactor"
    productFlavors {
        create("phone") {
            dimension = "formFactor"
            // Portable management — keep historic package id (no DO / Lock Task).
            applicationId = "co.zw.nissangtr.management"
            buildConfigField("boolean", "IS_TABLET_KIOSK", "false")
            resValue("string", "app_name", "GTR Management")
        }
        create("tablet") {
            dimension = "formFactor"
            applicationId = "co.zw.nissangtr.management.tablet"
            buildConfigField("boolean", "IS_TABLET_KIOSK", "true")
            resValue("string", "app_name", "GTR POS Kiosk")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:rpc"))
    implementation(project(":android-ui"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:kiosk"))
    implementation(project(":feature:pos"))
    implementation(project(":feature:warehouse"))
    implementation(project(":feature:dispatch"))
    implementation(project(":feature:hr"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:procurement"))
    implementation(project(":feature:credit"))
    implementation(project(":feature:fleet"))
    implementation(project(":qr-scanner"))
    implementation(project(":escpos-printer"))
    implementation(project(":biometric-photo"))

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
