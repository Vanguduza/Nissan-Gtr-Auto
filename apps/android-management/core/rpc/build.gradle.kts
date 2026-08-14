plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "co.zw.nissangtr.management.rpc"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // supabase-kt 3.x (auth was gotrue-kt in 2.x) — api so feature modules see Auth/SessionStatus
    val supabaseBom = platform("io.github.jan-tennert.supabase:bom:3.1.1")
    api(supabaseBom)
    api("io.github.jan-tennert.supabase:postgrest-kt")
    api("io.github.jan-tennert.supabase:auth-kt")
    api("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // H7 / B-PS-1 — PowerSync Kotlin SDK (openDatabase when POWERSYNC_URL set; Fake otherwise).
    // Pin 1.8.1: matches Kotlin 2.2.10 on this app (1.13.x needs Kotlin 2.3 metadata).
    // Secrets: local.properties / BuildConfig only — never commit. See powersync/.env.example.
    api("com.powersync:core:1.8.1")
    // Satisfies DatabaseDriverFactory → BundledSQLiteDriver classpath (PowerSync Android).
    implementation("androidx.sqlite:sqlite-bundled:2.5.0")

    testImplementation("junit:junit:4.13.2")
}
