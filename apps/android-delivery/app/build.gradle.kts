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
    namespace = "co.zw.nissangtr.delivery"
    compileSdk = 34

    defaultConfig {
        applicationId = "co.zw.nissangtr.delivery"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.1"
        // Placeholders — set via local.properties / CI; never commit real keys.
        buildConfigField("String", "SUPABASE_URL", "\"${localProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "SUPPORT_PHONE", "\"${localProp("SUPPORT_PHONE")}\"")
        // Keyless routing (OSRM-compatible). ROUTING_BASE_URL is the full `/route/v1/driving` root;
        // OSRM_URL (host only, self-hosted satellite) is accepted too. Blank → public OSRM router.
        val osrmHost = localProp("OSRM_URL").trim().trimEnd('/')
        val routingBaseUrl = localProp("ROUTING_BASE_URL").trim().ifBlank {
            when {
                osrmHost.isBlank() -> "https://router.project-osrm.org/route/v1/driving"
                osrmHost.contains("/route/") -> osrmHost
                else -> "$osrmHost/route/v1/driving"
            }
        }
        buildConfigField("String", "ROUTING_BASE_URL", "\"$routingBaseUrl\"")
        // Optional self-hosted MapLibre style (infra/satellites/maptiles/, tileserver-gl).
        // Blank → keyless OpenFreeMap styles from the maps-nav bridge. Never bundle MBTiles in the APK.
        buildConfigField(
            "String",
            "MAPLIBRE_STYLE_URL",
            "\"${localProp("MAPLIBRE_STYLE_URL")}\"",
        )
        buildConfigField(
            "boolean",
            "RPC_FORCE_FAKE",
            localProp("rpc.forceFake").equals("true", ignoreCase = true).toString(),
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
    implementation(project(":feature:jobs"))
    implementation(project(":feature:tracking"))
    implementation(project(":feature:pod"))
    implementation(project(":location-tracker"))
    implementation(project(":pod-camera"))
    implementation(project(":pod-signature"))
    implementation(project(":maps-nav"))

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.core:core-ktx:1.13.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
