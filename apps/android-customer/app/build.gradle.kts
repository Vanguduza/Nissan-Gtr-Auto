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
    namespace = "co.zw.nissangtr.customer"
    compileSdk = 34

    defaultConfig {
        applicationId = "co.zw.nissangtr.customer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-scaffold"
        // Placeholders — set via local.properties / CI; never commit real keys.
        // Names align with root `.env.example` (and web `NEXT_PUBLIC_SUPABASE_*`).
        buildConfigField("String", "SUPABASE_URL", "\"${localProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("SUPABASE_ANON_KEY")}\"")
        buildConfigField(
            "boolean",
            "RPC_FORCE_FAKE",
            localProp("rpc.forceFake").equals("true", ignoreCase = true).toString(),
        )
        // Optional digits-only WhatsApp CTA. Blank disables the CTA; never invent a production number.
        buildConfigField(
            "String",
            "WHATSAPP_E164",
            "\"${localProp("WHATSAPP_E164")}\"",
        )
        // Maps are keyless via the shared MapLibre + OpenFreeMap/OSM bridge.
        // Google Sign-In — Web OAuth client ID as Credential Manager serverClientId.
        // Prefer GOOGLE_WEB_CLIENT_ID; GOOGLE_SERVER_CLIENT_ID accepted as alias.
        // Android OAuth client (package + SHA-1) is required in Google Cloud but is NOT
        // embedded here — only the Web client ID goes to BuildConfig. Never commit secrets.
        val googleWebClientId = localProp("GOOGLE_WEB_CLIENT_ID")
            .ifBlank { localProp("GOOGLE_SERVER_CLIENT_ID") }
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        manifestPlaceholders["GOOGLE_WEB_CLIENT_ID"] = googleWebClientId
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val verifyCanonicalCustomerLineage by tasks.registering(Exec::class) {
    group = "verification"
    description = "Refuse customer APK/AAB builds from stale or unreconciled repository lineage."
    val repoRoot = rootProject.projectDir.parentFile.parentFile
    workingDir(repoRoot)
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    if (isWindows) {
        commandLine("py", "-3", "scripts/project_truth_guard.py", "release-check", "--app", "customer-android")
    } else {
        commandLine("python3", "scripts/project_truth_guard.py", "release-check", "--app", "customer-android")
    }
}

tasks.configureEach {
    if (name.startsWith("assemble", ignoreCase = true) || name.startsWith("bundle", ignoreCase = true)) {
        dependsOn(verifyCanonicalCustomerLineage)
    }
}

dependencies {
    implementation(project(":core:rpc"))
    implementation(project(":android-ui"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:cart"))
    implementation(project(":feature:orders"))
    implementation(project(":feature:garage"))
    implementation(project(":feature:pay"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:track"))
    implementation(project(":feature:wishlist"))
    implementation(project(":feature:compare"))
    implementation(project(":feature:reviews"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:address"))
    implementation(project(":pod-camera"))
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
