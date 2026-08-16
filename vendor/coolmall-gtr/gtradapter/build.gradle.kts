plugins {
    alias(libs.plugins.coolmall.android.library)
    alias(libs.plugins.coolmall.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "co.zw.nissangtr.management.gtradapter"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation(libs.kotlinx.serialization.json)

    // supabase-kt 3.x — same BOM pin as apps/android-management/core/rpc
    val supabaseBom = platform("io.github.jan-tennert.supabase:bom:3.1.1")
    api(supabaseBom)
    api("io.github.jan-tennert.supabase:auth-kt")
    api("io.github.jan-tennert.supabase:postgrest-kt")
    api("io.github.jan-tennert.supabase:storage-kt")
    api("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")

    testImplementation(libs.junit)
}
