import java.util.Properties

fun customerVisualProp(name: String): String {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { props.load(it) }
    return props.getProperty(name)
        ?: (project.findProperty(name) as? String)
        ?: System.getenv(name)
        ?: ""
}

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "co.zw.nissangtr.customer.visual"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        val supabaseUrl = customerVisualProp("SUPABASE_URL")
            .ifBlank { "https://bicyjghgdnzlnjqxzoud.supabase.co" }
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:rpc"))
    implementation(project(":android-ui"))

    implementation("androidx.core:core-ktx:1.13.1")
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
}
