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
    namespace = "co.zw.nissangtr.pos"
    compileSdk = 34

    defaultConfig {
        applicationId = "co.zw.nissangtr.pos"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0-post-epic"
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
    implementation(project(":pos-api"))
    implementation(project(":feature-till"))
    implementation(project(":feature-lookup"))
    implementation(project(":feature-pay"))
    implementation(project(":feature-orders"))
    implementation(project(":feature-customer"))
    implementation(project(":sync"))
    implementation(project(":android-ui"))
    implementation(project(":qr-scanner"))
    implementation(project(":escpos-printer"))

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.core:core-ktx:1.13.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
