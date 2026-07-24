import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
        applicationId = "co.zw.nissangtr.management"
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(project(":core:rpc"))
    implementation(project(":feature:pos"))
    implementation(project(":feature:warehouse"))
    implementation(project(":feature:dispatch"))
    implementation(project(":feature:hr"))

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.core:core-ktx:1.13.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
